package jforex;

import java.util.Properties;

import jforex.TradeStateController.TradeState;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.Momentum.MACD_STATE;
import jforex.techanalysis.Trend;
import jforex.utils.FXUtils;
import jforex.utils.Logger;
import com.dukascopy.api.*;
import com.dukascopy.api.IIndicators.AppliedPrice;

public class LongMR_EURUSD extends AbstractSinglePairSingleStrategy implements
		IStrategy {
	public LongMR_EURUSD(Properties p) {
		super(p);
	}

	public void onStart(IContext context) throws JFException {
		onStartExec(context);
	}

	public void onAccount(IAccount account) throws JFException {
	}

	public void onMessage(IMessage message) throws JFException {
		switch (tradeState.getState()) {
		case SCANNING_TA_SIGNALS:
			if (message.getType().equals(IMessage.Type.ORDER_SUBMIT_OK)) {
				positionOrder.setStopLossPrice(protectiveStop - safeZone
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
						(protectiveStop - positionOrder.getOpenPrice()) * 10000,
						0, 0);
				// TODO: find a way to calculate realistic commisions, in the
				// worst case by replicating table from Dukascopy site
				// SEE ITesterClient.getCommissions !!
				dbLogRecordEntry(message, positionOrder);
			} else if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK)) {
				// order canceled
				tradeState.stateTransition(TradeState.SCANNING_TA_SIGNALS,
						message.getCreationTime());
			}
			break;
		case POSITION_OPENED:
			if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK)) {
				log.printAction(
						Logger.logTags.ORDER.toString(),
						positionOrder.getLabel(),
						FXUtils.getFormatedTimeGMT(message.getCreationTime()),
						Logger.logTags.EXIT_STOP_LOSS.toString(),
						positionOrder.getClosePrice(),
						(protectiveStop - positionOrder.getOpenPrice()) * 10000,
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

				dbLogRecordExit(message, positionOrder,
						Logger.logTags.EXIT_STOP_LOSS);
				positionExitCleanUp();
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
						(protectiveStop - positionOrder.getOpenPrice()) * 10000,
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
						(protectiveStop - positionOrder.getOpenPrice()) * 10000,
						positionOrder.getProfitLossInPips(), 0);
			} else if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK)) {
				tradeState.stateTransition(TradeState.SCANNING_TA_SIGNALS,
						message.getCreationTime());
				log.printAction(
						Logger.logTags.ORDER.toString(),
						positionOrder.getLabel(),
						FXUtils.getFormatedTimeGMT(message.getCreationTime()),
						Logger.logTags.EXIT_TRAILING_STOP.toString(),
						positionOrder.getClosePrice(),
						(protectiveStop - positionOrder.getOpenPrice()) * 10000,
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
				dbLogRecordExit(message, positionOrder,
						Logger.logTags.EXIT_TRAILING_STOP);
				positionExitCleanUp();
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

	public void onStop() throws JFException {
		onStopExec();
	}

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
			// if (!breakEvenSet && positionOrder.getProfitLossInPips() >=
			// BREAK_EVEN_PROFIT_THRESHOLD)
			// {
			// protectiveStop = positionOrder.getOpenPrice();
			// lastStopType = LastStopType.BREAKEVEN;
			// positionOrder.setStopLossPrice(protectiveStop);
			// // Subsequent protective stop changes must check that they don't
			// move this down
			// breakEvenSet = true;
			// }
			// PROTECT_PROFIT_THRESHOLD_OFFSET = 1.5 *
			// indicators.atr(instrument, selectedPeriod, OfferSide.BID, 14,
			// Filter.WEEKENDS, 1, tick.getTime(), 0)[0];
			if (!protectProfitSet
					&& positionOrder.getProfitLossInPips() >= PROTECT_PROFIT_THRESHOLD) {
				protectiveStop = tick.getBid()
						- PROTECT_PROFIT_THRESHOLD_OFFSET / 10000;
				lastStopType = LastStopType.PROFIT_PROTECTION;
				positionOrder.setStopLossPrice(protectiveStop);
				// Subsequent protective stop changes must check that they don't
				// move this down
				protectProfitSet = true;
			} else if (trailProfit
					&& protectProfitSet
					&& tick.getBid() > positionOrder.getStopLossPrice()
							+ PROTECT_PROFIT_THRESHOLD_OFFSET / 10000
					&& tick.getBid() - PROTECT_PROFIT_THRESHOLD_OFFSET / 10000 > protectiveStop) {
				protectiveStop = tick.getBid()
						- PROTECT_PROFIT_THRESHOLD_OFFSET / 10000;
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
					(protectiveStop - positionOrder.getOpenPrice()) * 10000,
					positionOrder.getProfitLossInPips(), 0);
			tradeStats.updateMaxPnL(positionOrder.getProfitLossInPips());
			break;
		case EXIT_ORDER_TRAILING:
			log.printAction(
					Logger.logTags.ORDER.toString(),
					positionOrder.getLabel(),
					FXUtils.getFormatedTimeGMT(tick.getTime()),
					Logger.logTags.PROFIT_REPORT.toString(),
					vola.getATR(instrument, selectedPeriod, OfferSide.BID,
							tick.getTime(), 14) * 10000,
					(protectiveStop - positionOrder.getOpenPrice()) * 10000,
					positionOrder.getProfitLossInPips(), 0);
			tradeStats.updateMaxPnL(positionOrder.getProfitLossInPips());
			break;
		case POSITION_CLOSED:
			break;
		case ENTRY_ORDER_CANCELLED:
			break;
		default:
			return;
		}
	}

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
			IBar triggerLowBar = null;
			if ((triggerLowBar = entrySignalFound(instrument, period, askBar,
					bidBar)) != null) {
				placeBuyOrder(instrument, period, askBar, bidBar);
				// Careful, placeOrder will trigger onMessage call !
				log.printAction(
						Logger.logTags.ORDER.toString(),
						positionOrder.getLabel(),
						FXUtils.getFormatedBarTime(bidBar),
						Logger.logTags.ENTRY_FOUND.toString() + " BUY STOP",
						positionOrder.getOpenPrice(),
						(protectiveStop - positionOrder.getOpenPrice()) * 10000,
						0, 0);
				// TODO: convert 10'000 to proper pips value
				printStatsValues(Logger.logTags.ENTRY_STATS, instrument,
						period, bidBar.getTime(), bidBar.getHigh(),
						triggerLowBar.getTime(), triggerLowBar.getLow(), 1000,
						true); // with header
				// Time of last higherTimeFrame bar needed
				long timeOfPrevBarHigherTimeFrame = history
						.getPreviousBarStart(higherTimeFrame, bidBar.getTime());
				printStatsValues(Logger.logTags.ENTRY_STATS, instrument,
						higherTimeFrame, timeOfPrevBarHigherTimeFrame,
						bidBar.getHigh(), timeOfPrevBarHigherTimeFrame,
						triggerLowBar.getLow(), 720, false); // no header
			}
			break;
		case ENTRY_ORDER_WAITING:
			// TODO: also search for entry price improvement when waiting for
			// entry !
			if (tradeTrigger.longEntryCancel(protectiveStop, instrument,
					period, OfferSide.BID, bidBar.getTime())) {
				positionOrder.close();
				// Careful, positionOrder.close will trigger onMessage call !
				log.printAction(
						Logger.logTags.ORDER.toString(),
						positionOrder.getLabel(),
						FXUtils.getFormatedBarTime(bidBar),
						Logger.logTags.ENTRY_CANCELED.toString(),
						positionOrder.getOpenPrice(),
						(positionOrder.getOpenPrice() - protectiveStop) * 10000,
						0, 0);
				// TODO: find a way to calculate realistic commissions, in the
				// worst case by replicating table from Dukascopy site
			}
			break;
		case POSITION_OPENED:
			if (raiseProtectiveStopSignalFound(instrument, period, askBar,
					bidBar)) {
				// update protective stop
				positionOrder.setStopLossPrice(protectiveStop);
			}
			if (startT1BLSignalFound(instrument, period, askBar, bidBar)) {
				tradeState.stateTransition(TradeState.EXIT_ORDER_TRAILING,
						bidBar.getTime());
				protectiveStop = bidBar.getLow() - safeZone / 10000;
				lastStopType = LastStopType.TRAILING;
				positionOrder.setStopLossPrice(protectiveStop);
				// Careful, positionOrder.close will trigger onMessage call !
				log.printAction(
						Logger.logTags.ORDER.toString(),
						positionOrder.getLabel(),
						FXUtils.getFormatedBarTime(bidBar),
						Logger.logTags.TRAILING_STOP_UPDATED.toString(),
						positionOrder.getStopLossPrice(),
						(protectiveStop - positionOrder.getOpenPrice()) * 10000,
						positionOrder.getProfitLossInPips(), 0);
			}
			break;
		case EXIT_ORDER_TRAILING:
			// But it also mustn't lower the protective stop !
			if (bidBar.getLow() - safeZone / 10000 > protectiveStop) {
				protectiveStop = bidBar.getLow() - safeZone / 10000;
				lastStopType = LastStopType.TRAILING;
				positionOrder.setStopLossPrice(protectiveStop);
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

	private IBar entrySignalFound(Instrument instrument, Period period,
			IBar askBar, IBar bidBar) throws JFException {
		if (!check4hTimeFrame(instrument, bidBar))
			return null;

		if (channelPosition.priceChannelPos(instrument, period, OfferSide.BID,
				bidBar.getTime(), bidBar.getLow()) > 20.0)
			return null;

		IBar triggerLowBar = tradeTrigger.bullishReversalCandlePatternBar(
				instrument, period, OfferSide.BID, bidBar.getTime());
		if (triggerLowBar == null)
			return null;

		protectiveStop = triggerLowBar.getLow()
				- 2
				* vola.getATR(instrument, period, OfferSide.BID,
						bidBar.getTime(), 14);
		return triggerLowBar;
	}

	/**
	 * @param lookBack
	 *            - statistical sample size
	 * @param entryPrice
	 * @return true if all the conditions according to 4h timeframe are OK,
	 *         false if no valid signal
	 */
	private boolean check4hTimeFrame(Instrument instrument, IBar bidBar)
			throws JFException {
		Trend.TREND_STATE trend4h = trendDetector.getTrendState(instrument,
				higherTimeFrame, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime());
		double trend4hStat = trendDetector.getMAsMaxDiffStDevPos(instrument,
				higherTimeFrame, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime(), FXUtils.QUARTER_WORTH_OF_4h_BARS);
		if (!((trend4h.equals(Trend.TREND_STATE.DOWN_MILD)
				|| trend4h.equals(Trend.TREND_STATE.DOWN_STRONG) || trend4h
					.equals(Trend.TREND_STATE.FRESH_DOWN)) || trend4hStat <= -1.0))
			return false;

		Momentum.MACD_STATE macd4h = momentum.getMACDState(instrument,
				higherTimeFrame, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime());
		if (macd4h.equals(Momentum.MACD_STATE.FALLING_BOTH_ABOVE_0))
			return false;
		Momentum.MACD_H_STATE macdH4h = momentum.getMACDHistogramState(
				instrument, higherTimeFrame, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime());
		if (!(macdH4h.toString().startsWith("RAISING")
				|| macdH4h.toString().startsWith("TICKED_UP") || macdH4h
					.equals(Momentum.MACD_H_STATE.TICKED_DOWN_ABOVE_ZERO)))
			return false;

		if (context.getIndicators().rsi(instrument, higherTimeFrame,
				OfferSide.BID, AppliedPrice.CLOSE, 14, Filter.WEEKENDS, 1,
				bidBar.getTime(), 0)[0] > 70.0)
			return false;

		Momentum.STOCH_STATE stoch4h = momentum.getStochState(instrument,
				higherTimeFrame, OfferSide.BID, bidBar.getTime());
		double[] stochValues4h = momentum.getStochs(instrument,
				higherTimeFrame, OfferSide.BID, bidBar.getTime());
		if (stoch4h.equals(Momentum.STOCH_STATE.FALLING_IN_MIDDLE)
				|| (stoch4h.equals(Momentum.STOCH_STATE.OVERSOLD_BOTH) && stochValues4h[0] < stochValues4h[1]))
			return false;

		return true;
	}

	/**
	 * There is only one reason to raise protective stop on finished bar
	 * (various profit protections are done in onTick) 1. bullish reversal
	 * pattern with low in 30' channel
	 */
	private boolean raiseProtectiveStopSignalFound(Instrument instrument,
			Period period, IBar askBar, IBar bidBar) throws JFException {
		if (momentum.getMACDState(instrument, period, OfferSide.BID,
				AppliedPrice.CLOSE, bidBar.getTime()).equals(
				MACD_STATE.FALLING_BOTH_ABOVE_0)
				|| channelPosition.priceChannelPos(instrument, period,
						OfferSide.BID, bidBar.getTime(), bidBar.getLow()) > 20.0)
			return false;

		IBar triggerLowBar = tradeTrigger.bullishReversalCandlePatternBar(
				instrument, period, OfferSide.BID, bidBar.getTime());
		if (triggerLowBar == null)
			return false;

		double newStopCandidate = triggerLowBar.getLow()
				- 2
				* vola.getATR(instrument, period, OfferSide.BID,
						bidBar.getTime(), 14);

		if (newStopCandidate > protectiveStop) {
			protectiveStop = newStopCandidate - safeZone / 10000;
			lastStopType = LastStopType.BULLISH_TRIGGER_LOW_IN_CHANNEL;
			return true;
		}
		return false;
	}

	private boolean startT1BLSignalFound(Instrument instrument, Period period,
			IBar askBar, IBar bidBar) throws JFException {
		return context.getIndicators().rsi(instrument, higherTimeFrame,
				OfferSide.BID, AppliedPrice.CLOSE, 14, Filter.WEEKENDS, 1,
				bidBar.getTime(), 0)[0] > 70.0;
	}

	@Override
	protected String getReportFileName() {
		return getStrategyName() + "_";
	}

	@Override
	protected String getStrategyName() {
		return "LongMR_EURUSD";
	}

}