package jforex;

import java.util.Properties;

import jforex.TradeStateController.TradeState;
import jforex.utils.FXUtils;
import jforex.utils.FXUtils.EntryFilters;
import jforex.utils.log.Logger;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.IIndicators.AppliedPrice;

public class DownHill_EURUSD extends AbstractSinglePairSingleStrategy implements
		IStrategy {

	public DownHill_EURUSD(Properties p) {
		super(p);
	}

	@Override
	public void onStart(IContext context) throws JFException {
		onStartExec(context);
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException {
		if (!instrument.equals(selectedInstrument)) {
			return;
		}
		if (Math.abs(tick.getBid() - lastRecordedTick) * 10000 < 5)
			return;
		lastRecordedTick = tick.getBid();
		switch (tradeState.getState()) {
		case SCANNING_TA_SIGNALS:
			break;
		case ENTRY_ORDER_WAITING:
			break;
		case POSITION_OPENED:
			// check profit. If certain threshold reached, move protective stop
			// to breakeven or bit more
			if (!breakEvenSet
					&& positionOrder.getProfitLossInPips() >= BREAK_EVEN_PROFIT_THRESHOLD) {
				protectiveStop = positionOrder.getOpenPrice();
				lastStopType = LastStopType.BREAKEVEN;
				positionOrder.setStopLossPrice(protectiveStop);
				// Subsequent protective stop changes must check that they don't
				// move this down
				breakEvenSet = true;
			}
			// PROTECT_PROFIT_THRESHOLD_OFFSET = 1.5 *
			// indicators.atr(instrument, selectedPeriod, OfferSide.BID, 14,
			// Filter.WEEKENDS, 1, tick.getTime(), 0)[0];
			if (!protectProfitSet
					&& positionOrder.getProfitLossInPips() >= PROTECT_PROFIT_THRESHOLD) {
				protectiveStop = tick.getBid()
						+ PROTECT_PROFIT_THRESHOLD_OFFSET / 10000;
				lastStopType = LastStopType.PROFIT_PROTECTION;
				positionOrder.setStopLossPrice(protectiveStop);
				// Subsequent protective stop changes must check that they don't
				// move this down
				protectProfitSet = true;
			} else if (trailProfit
					&& protectProfitSet
					&& tick.getBid() < positionOrder.getStopLossPrice()
							- PROTECT_PROFIT_THRESHOLD_OFFSET / 10000
					&& tick.getBid() + PROTECT_PROFIT_THRESHOLD_OFFSET / 10000 < protectiveStop) {
				protectiveStop = tick.getBid()
						+ PROTECT_PROFIT_THRESHOLD_OFFSET / 10000;
				lastStopType = LastStopType.PROFIT_PROTECTION;
				positionOrder.setStopLossPrice(protectiveStop);
			}
			log.printAction(
					Logger.logTags.ORDER.toString(),
					positionOrder.getLabel(),
					FXUtils.getFormatedTimeGMT(tick.getTime()),
					Logger.logTags.PROFIT_REPORT.toString(),
					vola.getATR(instrument, selectedPeriod, OfferSide.BID,
							tick.getTime(), 14) * 10000,
					(positionOrder.getOpenPrice() - protectiveStop) * 10000,
					positionOrder.getProfitLossInPips(), 0);
			break;
		case EXIT_ORDER_TRAILING:
			log.printAction(
					Logger.logTags.ORDER.toString(),
					positionOrder.getLabel(),
					FXUtils.getFormatedTimeGMT(tick.getTime()),
					Logger.logTags.PROFIT_REPORT.toString(),
					vola.getATR(instrument, selectedPeriod, OfferSide.BID,
							tick.getTime(), 14) * 10000,
					(positionOrder.getOpenPrice() - protectiveStop) * 10000,
					positionOrder.getProfitLossInPips(), 0);
			break;
		case POSITION_CLOSED:
			break;
		case ENTRY_ORDER_CANCELLED:
			break;
		default:
			return;
		}
	}

	@Override
	public void onMessage(IMessage message) throws JFException {
		switch (tradeState.getState()) {
		case SCANNING_TA_SIGNALS:
			if (message.getType().equals(IMessage.Type.ORDER_SUBMIT_OK)) {
				positionOrder.setStopLossPrice(protectiveStop + safeZone
						/ 10000);
				tradeState.stateTransition(TradeState.ENTRY_ORDER_WAITING,
						message.getCreationTime());
			}
			break;
		case ENTRY_ORDER_WAITING:
			if (message.getType().equals(IMessage.Type.ORDER_FILL_OK)) {
				tradeState.stateTransition(TradeState.POSITION_OPENED,
						message.getCreationTime());
				log.printAction(
						Logger.logTags.ORDER.toString(),
						positionOrder.getLabel(),
						FXUtils.getFormatedTimeGMT(message.getCreationTime()),
						Logger.logTags.ENTRY_FILLED.toString(),
						positionOrder.getOpenPrice(),
						(positionOrder.getOpenPrice() - protectiveStop) * 10000,
						0, 0);
				// TODO: find a way to calculate realistic commisions, in the
				// worst case by replicating table from Dukascopy site
				// SEE ITesterClient.getCommissions !!

			} else if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK)) {
				// order canceled
				tradeState.stateTransition(TradeState.SCANNING_TA_SIGNALS,
						message.getCreationTime());
			}
			break;
		case POSITION_OPENED:
			if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK)) {
				positionExitCleanUp();

				log.printAction(
						Logger.logTags.ORDER.toString(),
						positionOrder.getLabel(),
						FXUtils.getFormatedTimeGMT(message.getCreationTime()),
						Logger.logTags.EXIT_STOP_LOSS.toString(),
						positionOrder.getClosePrice(),
						(positionOrder.getOpenPrice() - protectiveStop) * 10000,
						positionOrder.getProfitLossInPips(), 0);
				// TODO: find a way to calculate realistic commissions, in the
				// worst case by replicating table from Dukascopy site
				long timeOfPrevBar = history.getPreviousBarStart(
						selectedPeriod, message.getCreationTime());
				printStatsValues(Logger.logTags.EXIT_STATS, message.getOrder()
						.getInstrument(), selectedPeriod, timeOfPrevBar,
						message.getOrder().getStopLossPrice(), timeOfPrevBar,
						message.getOrder().getStopLossPrice(), 1000, true); // with
																			// header
				// Time of last higherTimeFrame bar needed
				long timeOfPrevBarHigherTimeFrame = history
						.getPreviousBarStart(higherTimeFrame,
								message.getCreationTime());
				printStatsValues(Logger.logTags.EXIT_STATS, message.getOrder()
						.getInstrument(), higherTimeFrame,
						timeOfPrevBarHigherTimeFrame, message.getOrder()
								.getStopLossPrice(),
						timeOfPrevBarHigherTimeFrame, message.getOrder()
								.getStopLossPrice(), 720, false); // no header

				tradeState.stateTransition(TradeState.SCANNING_TA_SIGNALS,
						message.getCreationTime());
			} else if (message.getType().equals(IMessage.Type.ORDER_CHANGED_OK)) {
				String msgSuffix = new String(" (" + lastStopType.toString()
						+ ")");
				log.printAction(
						Logger.logTags.ORDER.toString(),
						positionOrder.getLabel(),
						FXUtils.getFormatedTimeGMT(message.getCreationTime()),
						Logger.logTags.STOP_UPDATED.toString() + msgSuffix,
						positionOrder.getStopLossPrice(),
						(positionOrder.getOpenPrice() - protectiveStop) * 10000,
						positionOrder.getProfitLossInPips(), 0);
			}
			break;
		case EXIT_ORDER_TRAILING:
			if (message.getType().equals(IMessage.Type.ORDER_CHANGED_OK)) {
				log.printAction(
						Logger.logTags.ORDER.toString(),
						positionOrder.getLabel(),
						FXUtils.getFormatedTimeGMT(message.getCreationTime()),
						Logger.logTags.TRAILING_STOP_UPDATED.toString(),
						positionOrder.getStopLossPrice(),
						(positionOrder.getOpenPrice() - protectiveStop) * 10000,
						positionOrder.getProfitLossInPips(), 0);
			} else if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK)) {
				positionExitCleanUp();
				tradeState.stateTransition(TradeState.SCANNING_TA_SIGNALS,
						message.getCreationTime());
				log.printAction(
						Logger.logTags.ORDER.toString(),
						positionOrder.getLabel(),
						FXUtils.getFormatedTimeGMT(message.getCreationTime()),
						Logger.logTags.EXIT_TRAILING_STOP.toString(),
						positionOrder.getClosePrice(),
						(positionOrder.getOpenPrice() - protectiveStop) * 10000,
						positionOrder.getProfitLossInPips(), 0);
				// TODO: find a way to calculate realistic commissions, in the
				// worst case by replicating table from Dukascopy site

				long timeOfPrevBar = history.getPreviousBarStart(
						selectedPeriod, message.getCreationTime());
				printStatsValues(Logger.logTags.EXIT_STATS, message.getOrder()
						.getInstrument(), selectedPeriod, timeOfPrevBar,
						message.getOrder().getStopLossPrice(), timeOfPrevBar,
						message.getOrder().getStopLossPrice(), 1000, true); // with
																			// header
				// Time of last higherTimeFrame bar needed
				long timeOfPrevBarHigherTimeFrame = history
						.getPreviousBarStart(higherTimeFrame,
								message.getCreationTime());
				printStatsValues(Logger.logTags.EXIT_STATS, message.getOrder()
						.getInstrument(), higherTimeFrame,
						timeOfPrevBarHigherTimeFrame, message.getOrder()
								.getStopLossPrice(),
						timeOfPrevBarHigherTimeFrame, message.getOrder()
								.getStopLossPrice(), 720, false); // no header
			}
			break;
		case POSITION_CLOSED:
			break;
		case ENTRY_ORDER_CANCELLED:
			break;
		default:
			return;
		}
	}

	protected void positionExitCleanUp() {
		breakEvenSet = false;
		protectProfitSet = false;
		tradeTrigger.resetT1BH();
		lastStopType = LastStopType.NONE;
	}

	@Override
	public void onAccount(IAccount account) throws JFException {
		// TODO Auto-generated method stub

	}

	@Override
	public void onStop() throws JFException {
		onStopExec();
	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar,
			IBar bidBar) throws JFException {
		if (!instrument.equals(selectedInstrument)
				|| !period.equals(selectedPeriod)) {
			return;
		}

		switch (tradeState.getState()) {
		case SCANNING_TA_SIGNALS:
			if (!tradingAllowed(bidBar.getTime()))
				return;
			if (entrySignalFound(instrument, period, askBar, bidBar)) {
				placeSellOrder(instrument, period, askBar, bidBar);
				// Careful, placeOrder will trigger onMessage call !
				log.printAction(
						Logger.logTags.ORDER.toString(),
						positionOrder.getLabel(),
						FXUtils.getFormatedBarTime(bidBar),
						Logger.logTags.ENTRY_FOUND.toString() + " SELL STOP",
						positionOrder.getOpenPrice(),
						(positionOrder.getOpenPrice() - protectiveStop) * 10000,
						0, 0);
				// TODO: convert 10'000 to proper pips value
				// TODO: adapt bar% to correct values
				printStatsValues(Logger.logTags.ENTRY_STATS, instrument,
						period, bidBar.getTime(), bidBar.getHigh(),
						bidBar.getTime(), bidBar.getLow(), 1000, true); // with
																		// header
				// Time of last higherTimeFrame bar needed
				long timeOfPrevBarHigherTimeFrame = history
						.getPreviousBarStart(higherTimeFrame, bidBar.getTime());
				printStatsValues(Logger.logTags.ENTRY_STATS, instrument,
						higherTimeFrame, timeOfPrevBarHigherTimeFrame,
						bidBar.getHigh(), timeOfPrevBarHigherTimeFrame,
						bidBar.getLow(), 720, false); // no header
			}
			break;
		case ENTRY_ORDER_WAITING:
			// TODO: also search for entry price improvement when waiting for
			// entry !
			if (tradeTrigger.shortEntryCancel(protectiveStop, instrument,
					period, OfferSide.BID, bidBar.getTime())) {
				positionOrder.close();
				// Careful, positionOrder.close will trigger onMessage call !
				log.printAction(
						Logger.logTags.ORDER.toString(),
						positionOrder.getLabel(),
						FXUtils.getFormatedBarTime(bidBar),
						Logger.logTags.ENTRY_CANCELED.toString(),
						positionOrder.getOpenPrice(),
						(protectiveStop - positionOrder.getOpenPrice()) * 10000,
						0, 0);
				// TODO: find a way to calculate realistic commissions, in the
				// worst case by replicating table from Dukascopy site
			}
			break;
		case POSITION_OPENED:
			if (lowerProtectiveStopSignalFound(instrument, period, askBar,
					bidBar)) {
				// update protective stop
				positionOrder.setStopLossPrice(protectiveStop);
			}
			if (startT1BHSignalFound(instrument, period, askBar, bidBar)) {
				tradeState.stateTransition(TradeState.EXIT_ORDER_TRAILING,
						bidBar.getTime());
				lastStopType = LastStopType.TRAILING;
				protectiveStop = bidBar.getHigh() + safeZone / 10000;
				positionOrder.setStopLossPrice(protectiveStop);
				// Careful, positionOrder.close will trigger onMessage call !
				log.printAction(
						Logger.logTags.ORDER.toString(),
						positionOrder.getLabel(),
						FXUtils.getFormatedBarTime(bidBar),
						Logger.logTags.TRAILING_STOP_UPDATED.toString(),
						positionOrder.getStopLossPrice(),
						(positionOrder.getOpenPrice() - protectiveStop) * 10000,
						positionOrder.getProfitLossInPips(), 0);
			}
			break;
		case EXIT_ORDER_TRAILING:
			protectiveStop = bidBar.getHigh() + safeZone / 10000;
			lastStopType = LastStopType.TRAILING;
			positionOrder.setStopLossPrice(protectiveStop);
			break;
		case POSITION_CLOSED:
			break;
		case ENTRY_ORDER_CANCELLED:
			break;
		default:
			return;
		}
	}

	private boolean startT1BHSignalFound(Instrument instrument, Period period,
			IBar askBar, IBar bidBar) throws JFException {
		return tradeTrigger.startT1BH(positionOrder, instrument, period,
				OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime());
	}

	/**
	 * There are two reasons to lower protective stop on finished bar (various
	 * profit protections are done in onTick) 1. bullish reversal pattern
	 * happened with pivot low below very low in BBands channel (5%) 2. bearish
	 * reversal pattern with pivot piercing EMA9, but only if this ensures
	 * profit above 20 pips (TODO: experiment with ATR and other flexible
	 * methods)
	 */
	private boolean lowerProtectiveStopSignalFound(Instrument instrument,
			Period period, IBar askBar, IBar bidBar) throws JFException {
		double newStopCandidate = bullishReversalLowInChannel(instrument,
				period, askBar, bidBar);
		if (newStopCandidate != Double.MIN_VALUE
				&& newStopCandidate < protectiveStop) {
			// if current profit is still below predefined profit protection
			// level, just set to breakeven
			// if(newStopCandidate > positionOrder.getOpenPrice() -
			// BREAK_EVEN_PROFIT_THRESHOLD / 10000)
			// protectiveStop = positionOrder.getOpenPrice() + safeZone / 10000;
			// else
			protectiveStop = newStopCandidate + safeZone / 10000;
			lastStopType = LastStopType.BULLISH_TRIGGER_LOW_IN_CHANNEL;
			return true;
		}

		newStopCandidate = emaBearishDip(instrument, period, askBar, bidBar, 9);
		if (newStopCandidate != Double.MAX_VALUE
				&& newStopCandidate < protectiveStop
				// no use of EMA dip before breakeven
				&& newStopCandidate < positionOrder.getOpenPrice()) {
			// if current profit is still above predefined profit protection
			// level, just set to breakeven
			// if(newStopCandidate > positionOrder.getOpenPrice() -
			// BREAK_EVEN_PROFIT_THRESHOLD / 10000)
			// protectiveStop = positionOrder.getOpenPrice() + safeZone / 10000;
			// else
			protectiveStop = newStopCandidate + safeZone / 10000;
			lastStopType = LastStopType.EMA9;
			return true;
		}
		return false;
	}

	private double emaBearishDip(Instrument instrument, Period period,
			IBar askBar, IBar bidBar, int lookback) throws JFException {
		double newStopCandidate = tradeTrigger.bearishReversalCandlePattern(
				instrument, period, OfferSide.BID, bidBar.getTime());
		if (newStopCandidate == Double.MAX_VALUE)
			return Double.MAX_VALUE;

		// Careful, we want ema9 and ma20 not of the last bar but one before -
		// therefore 2 candles before and index 1
		double ema9 = indicators.ema(instrument, period, OfferSide.BID,
				AppliedPrice.CLOSE, lookback, Filter.WEEKENDS, 2,
				bidBar.getTime(), 0)[1];
		double ma20 = indicators
				.sma(instrument, period, OfferSide.BID, AppliedPrice.CLOSE, 20,
						Filter.WEEKENDS, 2, bidBar.getTime(), 0)[1];

		if (newStopCandidate < protectiveStop // avoid raising existing
												// protective stops
				// && newStopCandidate > positionOrder.getOpenPrice() // only
				// after breakeven
				&& ema9 < ma20 && newStopCandidate > ema9) {
			return newStopCandidate;
		} else
			return Double.MAX_VALUE;
	}

	private double bullishReversalLowInChannel(Instrument instrument,
			Period period, IBar askBar, IBar bidBar) throws JFException {
		double newStopCandidate = bidBar.getHigh();
		if (channelPosition.priceChannelPos(instrument, period, OfferSide.BID,
				bidBar.getTime(), bidBar.getLow()) >= 5.0
				|| newStopCandidate >= protectiveStop) // avoid raising existing
														// protective stops
			return Double.MIN_VALUE;

		if (tradeTrigger.bullishReversalCandlePattern(instrument, period,
				OfferSide.BID, bidBar.getTime()) == Double.MIN_VALUE)
			return Double.MIN_VALUE;
		else
			return newStopCandidate;
	}

	private boolean entrySignalFound(Instrument instrument, Period period,
			IBar askBar, IBar bidBar) throws JFException {
		// basic setup definition first
		double MAsMaxDiffStDevPos = trendDetector
				.getDowntrendMAsMaxDifStDevPos(instrument, period,
						OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime(),
						1000);
		if (MAsMaxDiffStDevPos == -1000
				|| MAsMaxDiffStDevPos < trendStDevDefinitionMin
				|| MAsMaxDiffStDevPos > trendStDevDefinitionMax)
			return false;

		// 4h Signal filtering first
		// Time of last higherTimeFrame bar needed
		long timeOfPrevBarHigherTimeFrame = history.getPreviousBarStart(
				higherTimeFrame, bidBar.getTime());
		if (entryFilters.contains(EntryFilters.FILTER_4H)
				&& !check4hTimeFrame(instrument, higherTimeFrame,
						OfferSide.BID, AppliedPrice.CLOSE,
						timeOfPrevBarHigherTimeFrame, 720, bidBar.getHigh()))
			return false;

		// Signal filtering first
		double[] last2BarsChannelPos = channelPosition
				.bearishTriggerChannelStats(instrument, period, OfferSide.BID,
						bidBar.getTime());

		if (entryFilters.contains(EntryFilters.FILTER_MOMENTUM)) {
			if (momentum.momentumUp(instrument, period, OfferSide.BID,
					AppliedPrice.CLOSE, bidBar.getTime(), last2BarsChannelPos))
				return false;

			// double[] adx = trendDetector.getADXs(instrument, period,
			// OfferSide.BID, bidBar.getTime());
			// if (adx[0] > 47.5 ||
			// (adx[1] < adx[2] && adx[1] < adx[0] && adx[1] < 8.4))
			// return false;
		}

		if (entryFilters.contains(EntryFilters.FILTER_ENTRY_BAR_HIGH)) {
			// double maxChannelPosHigh = 71.2;
			// if (last2BarsChannelPos[0] < maxChannelPosHigh)
			// return false;
		}

		// get higher timeframe ADX to require piercing lower BBand in late
		// uptrend (4h ADX > 40)
		// Time of last higherTimeFrame bar needed
		// trendDetector.isStrongUpTrendBy3MAs(instrument, higherTimeFrame,
		// OfferSide.BID, AppliedPrice.CLOSE, timeOfHigherTimeFrameBar);
		// if (trendDetector.getMAsMaxDifStDevPos(instrument, higherTimeFrame,
		// OfferSide.BID, AppliedPrice.CLOSE, timeOfHigherTimeFrameBar) > 2.0)
		// // 4h uptrend too strong and probably too old
		// return false;

		double minChannelPosLow = Double.MIN_VALUE;
		if (entryFilters.contains(EntryFilters.FILTER_ENTRY_BAR_LOW)) {
			minChannelPosLow = 51.0;
			// double[] adx4h = trendDetector.getADXs(instrument,
			// higherTimeFrame, OfferSide.BID, timeOfHigherTimeFrameBar);
			// if (adx4h[0] > 40.0)
			// minChannelPosLow = 0.0;
			// else
			// minChannelPosLow = 11.9;
		}

		return last2BarsChannelPos[0] > minChannelPosLow
				&& (protectiveStop = tradeTrigger.bearishReversalCandlePattern(
						instrument, period, OfferSide.BID, bidBar.getTime())) != Double.MAX_VALUE;
	}

	private boolean check4hTimeFrame(Instrument instrument,
			Period higherTimeFrame, OfferSide bid, AppliedPrice close,
			long timeOfPrevBarHigherTimeFrame, int i, double high) {
		return true;
	}

	@Override
	protected String getReportFileName() {
		return getStrategyName() + "_";
	}

	@Override
	protected String getStrategyName() {
		return "Downhill_EURUSD";
	}

}
