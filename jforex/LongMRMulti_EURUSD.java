package jforex;

import java.util.Properties;

import jforex.TradeStateController.TradeState;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Momentum.MACD_STATE;
import jforex.utils.FXUtils;
import jforex.utils.log.Logger;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.IIndicators.AppliedPrice;

public class LongMRMulti_EURUSD extends AbstractMultiPositionStrategy implements
		IStrategy {

	public Instrument selectedInstrument = Instrument.EURUSD;

	public Period selectedPeriod = Period.THIRTY_MINS;
	public Period higherTimeFrame = Period.FOUR_HOURS;

	public LongMRMulti_EURUSD(Properties props) {
		super(props);
	}

	@Override
	public void onStart(IContext context) throws JFException {
		onStartExec(context);
	}

	@Override
	public void onStop() throws JFException {
		onStopExec();
	}

	@Override
	public void onAccount(IAccount account) throws JFException {
		// TODO Auto-generated method stub

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
		case POSITION_OPENED_AND_ENTRY_ORDER_WAITING:
		case POSITION_OPENED_MAX_REACHED:
			if (!protectProfitSet
					&& getPositionOrder().getProfitLossInPips() >= PROTECT_PROFIT_THRESHOLD) {
				protectiveStop = tick.getBid()
						- PROTECT_PROFIT_THRESHOLD_OFFSET / 10000;
				lastStopType = LastStopType.PROFIT_PROTECTION;
				setStopLossPrice(protectiveStop);
				// Subsequent protective stop changes must check that they don't
				// move this down
				protectProfitSet = true;
			} else if (trailProfit
					&& protectProfitSet
					&& tick.getBid() > getPositionOrder().getStopLossPrice()
							+ PROTECT_PROFIT_THRESHOLD_OFFSET / 10000
					&& tick.getBid() - PROTECT_PROFIT_THRESHOLD_OFFSET / 10000 > protectiveStop) {
				protectiveStop = tick.getBid()
						- PROTECT_PROFIT_THRESHOLD_OFFSET / 10000;
				lastStopType = LastStopType.PROFIT_PROTECTION;
				setStopLossPrice(protectiveStop);
			}
			log.printAction(
					Logger.logTags.ORDER.toString(),
					getPositionOrder().getLabel(),
					FXUtils.getFormatedTimeGMT(tick.getTime()),
					Logger.logTags.PROFIT_REPORT.toString(),
					vola.getATR(instrument, selectedPeriod, OfferSide.BID,
							tick.getTime(), 14) * 10000,
					(protectiveStop - getPositionOrder().getOpenPrice()) * 10000,
					getPositionOrder().getProfitLossInPips(), 0);
			tradeStats.updateMaxPnL(getPositionOrder().getProfitLossInPips());
			break;
		case EXIT_ORDER_TRAILING:
			log.printAction(
					Logger.logTags.ORDER.toString(),
					getPositionOrder().getLabel(),
					FXUtils.getFormatedTimeGMT(tick.getTime()),
					Logger.logTags.PROFIT_REPORT.toString(),
					vola.getATR(instrument, selectedPeriod, OfferSide.BID,
							tick.getTime(), 14) * 10000,
					(protectiveStop - getPositionOrder().getOpenPrice()) * 10000,
					getPositionOrder().getProfitLossInPips(), 0);
			tradeStats.updateMaxPnL(getPositionOrder().getProfitLossInPips());
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
				waitingOrder
						.setStopLossPrice(protectiveStop - safeZone / 10000);
				tradeState.stateTransition(TradeState.ENTRY_ORDER_WAITING,
						message.getCreationTime());
			}
			break;
		case ENTRY_ORDER_WAITING:
			if (message.getType().equals(IMessage.Type.ORDER_FILL_OK)) {
				processOrderFill(message);
			} else if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK)) {
				// order canceled
				tradeState.stateTransition(TradeState.SCANNING_TA_SIGNALS,
						message.getCreationTime());
			}
			break;
		case POSITION_OPENED:
			if (message.getType().equals(IMessage.Type.ORDER_SUBMIT_OK)) {
				waitingOrder
						.setStopLossPrice(protectiveStop - safeZone / 10000);
				tradeState.stateTransition(
						TradeState.POSITION_OPENED_AND_ENTRY_ORDER_WAITING,
						message.getCreationTime());
			}
			// message will be send for all the open orders !
			else if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK)) {
				processPositionsClose(message, false);
			} else if (message.getType().equals(IMessage.Type.ORDER_CHANGED_OK)) {
				String msgSuffix = new String(" (" + lastStopType.toString()
						+ ")");
				log.printAction(
						Logger.logTags.ORDER.toString(),
						message.getOrder().getLabel(),
						FXUtils.getFormatedTimeGMT(message.getCreationTime()),
						Logger.logTags.STOP_UPDATED.toString() + msgSuffix,
						message.getOrder().getStopLossPrice(),
						(protectiveStop - message.getOrder().getOpenPrice()) * 10000,
						message.getOrder().getProfitLossInPips(), 0);
			}
			break;
		case POSITION_OPENED_MAX_REACHED:
			// message will be send for all the open orders !
			if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK)) {
				processPositionsClose(message, false);
			} else if (message.getType().equals(IMessage.Type.ORDER_CHANGED_OK)) {
				String msgSuffix = new String(" (" + lastStopType.toString()
						+ ")");
				log.printAction(
						Logger.logTags.ORDER.toString(),
						message.getOrder().getLabel(),
						FXUtils.getFormatedTimeGMT(message.getCreationTime()),
						Logger.logTags.STOP_UPDATED.toString() + msgSuffix,
						message.getOrder().getStopLossPrice(),
						(protectiveStop - message.getOrder().getOpenPrice()) * 10000,
						message.getOrder().getProfitLossInPips(), 0);
			}
			break;
		case POSITION_OPENED_AND_ENTRY_ORDER_WAITING:
			if (message.getType().equals(IMessage.Type.ORDER_FILL_OK)) {
				processOrderFill(message);
			}
			// message will be send for all the open orders !
			else if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK)) {
				if (!message.getOrder().equals(waitingOrder)) {
					processPositionsClose(message, false);
					waitingOrder.close();
				} else
					// order canceled
					tradeState.stateTransition(TradeState.POSITION_OPENED,
							message.getCreationTime());
			} else if (message.getType().equals(IMessage.Type.ORDER_CHANGED_OK)) {
				String msgSuffix = new String(" (" + lastStopType.toString()
						+ ")");
				log.printAction(
						Logger.logTags.ORDER.toString(),
						message.getOrder().getLabel(),
						FXUtils.getFormatedTimeGMT(message.getCreationTime()),
						Logger.logTags.STOP_UPDATED.toString() + msgSuffix,
						message.getOrder().getStopLossPrice(),
						(protectiveStop - message.getOrder().getOpenPrice()) * 10000,
						message.getOrder().getProfitLossInPips(), 0);
			}
			break;
		case EXIT_ORDER_TRAILING:
			if (message.getType().equals(IMessage.Type.ORDER_CHANGED_OK)) {
				log.printAction(
						Logger.logTags.ORDER.toString(),
						message.getOrder().getLabel(),
						FXUtils.getFormatedTimeGMT(message.getCreationTime()),
						Logger.logTags.TRAILING_STOP_UPDATED.toString(),
						message.getOrder().getStopLossPrice(),
						(protectiveStop - message.getOrder().getOpenPrice()) * 10000,
						message.getOrder().getProfitLossInPips(), 0);
			} else if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK)) {
				processPositionsClose(message, true);
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
			checkForNewSignal(instrument, period, askBar, bidBar);
			break;
		case ENTRY_ORDER_WAITING:
			checkForCancelEntryOrder(instrument, period, bidBar);
			break;
		case POSITION_OPENED:
			if (raiseProtectiveStopSignalFound(instrument, period, askBar,
					bidBar)) {
				// update protective stop
				setStopLossPrice(protectiveStop);
			}
			if (startT1BLSignalFound(instrument, period, askBar, bidBar)) {
				tradeState.stateTransition(TradeState.EXIT_ORDER_TRAILING,
						bidBar.getTime());
				protectiveStop = bidBar.getLow() - safeZone / 10000;
				lastStopType = LastStopType.TRAILING;
				setStopLossPrice(protectiveStop);
				// Careful, positionOrder.close will trigger onMessage call !
				log.printAction(
						Logger.logTags.ORDER.toString(),
						getPositionOrder().getLabel(),
						FXUtils.getFormatedBarTime(bidBar),
						Logger.logTags.TRAILING_STOP_UPDATED.toString(),
						getPositionOrder().getStopLossPrice(),
						(protectiveStop - getPositionOrder().getOpenPrice()) * 10000,
						getPositionOrder().getProfitLossInPips(), 0);
			}
			if (tradingAllowed(bidBar.getTime()))
				checkForNextSignal(instrument, period, askBar, bidBar);
			break;
		case POSITION_OPENED_AND_ENTRY_ORDER_WAITING:
			checkForCancelEntryOrder(instrument, period, bidBar);
			if (raiseProtectiveStopSignalFound(instrument, period, askBar,
					bidBar)) {
				// update protective stop
				setStopLossPrice(protectiveStop);
			}
			if (startT1BLSignalFound(instrument, period, askBar, bidBar)) {
				tradeState.stateTransition(TradeState.EXIT_ORDER_TRAILING,
						bidBar.getTime());
				protectiveStop = bidBar.getLow() - safeZone / 10000;
				lastStopType = LastStopType.TRAILING;
				setStopLossPrice(protectiveStop);
				// Careful, positionOrder.close will trigger onMessage call !
				log.printAction(
						Logger.logTags.ORDER.toString(),
						getPositionOrder().getLabel(),
						FXUtils.getFormatedBarTime(bidBar),
						Logger.logTags.TRAILING_STOP_UPDATED.toString(),
						getPositionOrder().getStopLossPrice(),
						(protectiveStop - getPositionOrder().getOpenPrice()) * 10000,
						getPositionOrder().getProfitLossInPips(), 0);
			}
			break;
		case POSITION_OPENED_MAX_REACHED:
			if (raiseProtectiveStopSignalFound(instrument, period, askBar,
					bidBar)) {
				// update protective stop
				setStopLossPrice(protectiveStop);
			}
			if (startT1BLSignalFound(instrument, period, askBar, bidBar)) {
				tradeState.stateTransition(TradeState.EXIT_ORDER_TRAILING,
						bidBar.getTime());
				protectiveStop = bidBar.getLow() - safeZone / 10000;
				lastStopType = LastStopType.TRAILING;
				setStopLossPrice(protectiveStop);
				// Careful, positionOrder.close will trigger onMessage call !
				log.printAction(
						Logger.logTags.ORDER.toString(),
						getPositionOrder().getLabel(),
						FXUtils.getFormatedBarTime(bidBar),
						Logger.logTags.TRAILING_STOP_UPDATED.toString(),
						getPositionOrder().getStopLossPrice(),
						(protectiveStop - getPositionOrder().getOpenPrice()) * 10000,
						getPositionOrder().getProfitLossInPips(), 0);
			}
			break;
		case EXIT_ORDER_TRAILING:
			// But it also mustn't lower the protective stop !
			if (bidBar.getLow() - safeZone / 10000 > protectiveStop) {
				protectiveStop = bidBar.getLow() - safeZone / 10000;
				lastStopType = LastStopType.TRAILING;
				setStopLossPrice(protectiveStop);
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

	protected void checkForCancelEntryOrder(Instrument instrument,
			Period period, IBar bidBar) throws JFException {
		// TODO: also search for entry price improvement when waiting for entry
		// !
		if (tradeTrigger.longEntryCancel(protectiveStop, instrument, period,
				OfferSide.BID, bidBar.getTime())) {
			waitingOrder.close();
			// Careful, waitingOrder.close will trigger onMessage call !
			log.printAction(Logger.logTags.ORDER.toString(),
					waitingOrder.getLabel(),
					FXUtils.getFormatedBarTime(bidBar),
					Logger.logTags.ENTRY_CANCELED.toString(),
					waitingOrder.getOpenPrice(),
					(waitingOrder.getOpenPrice() - protectiveStop) * 10000, 0,
					0);
			// TODO: find a way to calculate realistic commissions, in the worst
			// case by replicating table from Dukascopy site
		}
	}

	protected void checkForNewSignal(Instrument instrument, Period period,
			IBar askBar, IBar bidBar) throws JFException {
		IBar triggerLowBar = null;
		if ((triggerLowBar = entrySignalFound(instrument, period, askBar,
				bidBar, true)) != null) {
			placeBuyOrder(instrument, period, askBar, bidBar);
			// Careful, placeOrder will trigger onMessage call !
			log.printAction(Logger.logTags.ORDER.toString(),
					waitingOrder.getLabel(),
					FXUtils.getFormatedBarTime(bidBar),
					Logger.logTags.ENTRY_FOUND.toString() + " BUY STOP",
					waitingOrder.getOpenPrice(),
					(protectiveStop - waitingOrder.getOpenPrice()) * 10000, 0,
					0);
			// TODO: convert 10'000 to proper pips value
			printStatsValues(Logger.logTags.ENTRY_STATS, instrument, period,
					bidBar.getTime(), bidBar.getHigh(),
					triggerLowBar.getTime(), triggerLowBar.getLow(), 1000, true); // with
																					// header
			// Time of last higherTimeFrame bar needed
			long timeOfPrevBarHigherTimeFrame = history.getPreviousBarStart(
					higherTimeFrame, bidBar.getTime());
			printStatsValues(Logger.logTags.ENTRY_STATS, instrument,
					higherTimeFrame, timeOfPrevBarHigherTimeFrame,
					bidBar.getHigh(), timeOfPrevBarHigherTimeFrame,
					triggerLowBar.getLow(), 720, false); // no header
		}
	}

	private void checkForNextSignal(Instrument instrument, Period period,
			IBar askBar, IBar bidBar) throws JFException {
		if (bidBar.getTime() < timeOfLastTrade + INTERVAL_BETWEEN_TRADES)
			return;

		IBar triggerLowBar = null;
		if ((triggerLowBar = entrySignalFound(instrument, period, askBar,
				bidBar, false)) != null) {
			placeBuyOrder(instrument, period, askBar, bidBar);
			// Careful, placeOrder will trigger onMessage call !
			log.printAction(Logger.logTags.ORDER.toString(),
					waitingOrder.getLabel(),
					FXUtils.getFormatedBarTime(bidBar),
					Logger.logTags.ENTRY_FOUND.toString() + " BUY STOP",
					waitingOrder.getOpenPrice(),
					(protectiveStop - waitingOrder.getOpenPrice()) * 10000, 0,
					0);
			// TODO: convert 10'000 to proper pips value
			printStatsValues(Logger.logTags.ENTRY_STATS, instrument, period,
					bidBar.getTime(), bidBar.getHigh(),
					triggerLowBar.getTime(), triggerLowBar.getLow(), 1000, true); // with
																					// header
			// Time of last higherTimeFrame bar needed
			long timeOfPrevBarHigherTimeFrame = history.getPreviousBarStart(
					higherTimeFrame, bidBar.getTime());
			printStatsValues(Logger.logTags.ENTRY_STATS, instrument,
					higherTimeFrame, timeOfPrevBarHigherTimeFrame,
					bidBar.getHigh(), timeOfPrevBarHigherTimeFrame,
					triggerLowBar.getLow(), 720, false); // no header
		}
	}

	protected void processPositionsClose(IMessage message, boolean trailing)
			throws JFException {
		log.printAction(Logger.logTags.ORDER.toString(), message.getOrder()
				.getLabel(), FXUtils.getFormatedTimeGMT(message
				.getCreationTime()),
				trailing ? Logger.logTags.EXIT_TRAILING_STOP.toString()
						: Logger.logTags.EXIT_STOP_LOSS.toString(), message
						.getOrder().getClosePrice(), (protectiveStop - message
						.getOrder().getOpenPrice()) * 10000, message.getOrder()
						.getProfitLossInPips(), 0);
		// TODO: find a way to calculate realistic commissions, in the worst
		// case by replicating table from Dukascopy site
		long timeOfPrevBar = history.getPreviousBarStart(selectedPeriod,
				message.getCreationTime());
		printStatsValues(Logger.logTags.EXIT_STATS, message.getOrder()
				.getInstrument(), selectedPeriod, timeOfPrevBar, message
				.getOrder().getStopLossPrice(), timeOfPrevBar, message
				.getOrder().getStopLossPrice(), 1000, true); // with header
		// Time of last higherTimeFrame bar needed
		long timeOfPrevBarHigherTimeFrame = history.getPreviousBarStart(
				higherTimeFrame, message.getCreationTime());
		printStatsValues(Logger.logTags.EXIT_STATS, message.getOrder()
				.getInstrument(), higherTimeFrame,
				timeOfPrevBarHigherTimeFrame, message.getOrder()
						.getStopLossPrice(), timeOfPrevBarHigherTimeFrame,
				message.getOrder().getStopLossPrice(), 720, false); // no header

		dbLogRecordExit(message, message.getOrder(),
				trailing ? Logger.logTags.EXIT_TRAILING_STOP
						: Logger.logTags.EXIT_STOP_LOSS);
		orders.remove(message.getOrder());
		if (orders.size() == 0) {
			protectProfitSet = false;
			timeOfLastTrade = 0;
			tradeState.stateTransition(TradeState.SCANNING_TA_SIGNALS,
					message.getCreationTime());
			lastStopType = LastStopType.NONE;
			tradeStats.reset();
		}
	}

	@Override
	protected Instrument getSelectedInstrument() {
		return selectedInstrument;
	}

	@Override
	protected IOrder getPositionOrder() {
		return orders.size() == 0 ? waitingOrder : orders.get(0);
	}

	@Override
	protected String getStrategyName() {
		return "LongMRMulti";
	}

	@Override
	protected String getReportFileName() {
		return getStrategyName() + "_";
	}

	private IBar entrySignalFound(Instrument instrument, Period period,
			IBar askBar, IBar bidBar, boolean firstEntry) throws JFException {
		if (!check1dTimeFrame(instrument, bidBar))
			return null;

		if (!check4hTimeFrame(instrument, bidBar, firstEntry))
			return null;

		if (channelPosition.priceChannelPos(instrument, period, OfferSide.BID,
				bidBar.getTime(), bidBar.getLow()) > 20.0)
			return null;

		if (tradeTrigger.bearishReversalCandlePattern(instrument, period,
				OfferSide.BID, bidBar.getTime()) != Double.MAX_VALUE)
			return null;

		IBar triggerLowBar = tradeTrigger.bullishReversalCandlePatternBar(
				instrument, period, OfferSide.BID, bidBar.getTime());
		if (triggerLowBar == null)
			return null;

		if (firstEntry)
			protectiveStop = triggerLowBar.getLow()
					- 2
					* vola.getATR(instrument, period, OfferSide.BID,
							bidBar.getTime(), 14);
		return triggerLowBar;
	}

	private boolean check1dTimeFrame(Instrument instrument, IBar bidBar)
			throws JFException {
		long prevDayTime = history.getPreviousBarStart(
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		Trend.TREND_STATE trend1d = trendDetector.getTrendState(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				AppliedPrice.CLOSE, prevDayTime);
		double trend1dStat = trendDetector.getMAsMaxDiffStDevPos(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				AppliedPrice.CLOSE, prevDayTime, FXUtils.YEAR_WORTH_OF_1d_BARS);
		Momentum.MACD_STATE macd1d = momentum.getMACDState(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				AppliedPrice.CLOSE, prevDayTime);
		Momentum.MACD_H_STATE macdH1d = momentum.getMACDHistogramState(
				instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				AppliedPrice.CLOSE, prevDayTime);
		Momentum.STOCH_STATE stoch1d = momentum.getStochState(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prevDayTime);
		double[] stochValues1d = momentum.getStochs(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prevDayTime);

		// 1d uptrend reversal going on, no long trades
		if (trend1dStat > 0.5
				&& trend1d.equals(Trend.TREND_STATE.UP_STRONG)
				&& macd1d.equals(Momentum.MACD_STATE.FALLING_BOTH_ABOVE_0)
				&& macdH1d.equals(Momentum.MACD_H_STATE.FALLING_BELOW_0)
				&& (stoch1d.equals(Momentum.STOCH_STATE.BEARISH_FALLING_IN_MIDDLE)
						|| stoch1d.equals(Momentum.STOCH_STATE.BEARISH_OVERSOLD_FAST) || (stoch1d
						.equals(Momentum.STOCH_STATE.BEARISH_OVERSOLD_BOTH) && stochValues1d[0] < stochValues1d[1])))
			return false;

		return true;
	}

	/**
	 * @param firstEntry
	 * @return true if all the conditions according to 4h timeframe are OK,
	 *         false if no valid signal
	 */
	private boolean check4hTimeFrame(Instrument instrument, IBar bidBar,
			boolean firstEntry) throws JFException {
		if (firstEntry) {
			if (channelPosition.priceChannelPos(instrument, higherTimeFrame,
					OfferSide.BID, bidBar.getTime(), bidBar.getLow()) > 60.0)
				return false;

			Trend.TREND_STATE trend4h = trendDetector.getTrendState(instrument,
					higherTimeFrame, OfferSide.BID, AppliedPrice.CLOSE,
					bidBar.getTime());
			double trend4hStat = trendDetector.getMAsMaxDiffStDevPos(
					instrument, higherTimeFrame, OfferSide.BID,
					AppliedPrice.CLOSE, bidBar.getTime(),
					FXUtils.QUARTER_WORTH_OF_4h_BARS);
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
					instrument, higherTimeFrame, OfferSide.BID,
					AppliedPrice.CLOSE, bidBar.getTime());
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
			if (stoch4h.equals(Momentum.STOCH_STATE.BEARISH_FALLING_IN_MIDDLE)
					|| (stoch4h.equals(Momentum.STOCH_STATE.BEARISH_OVERSOLD_BOTH) && stochValues4h[0] < stochValues4h[1]))
				return false;

			return true;
		} else {
			Momentum.MACD_STATE macd4h = momentum.getMACDState(instrument,
					higherTimeFrame, OfferSide.BID, AppliedPrice.CLOSE,
					bidBar.getTime());
			if (macd4h.toString().startsWith("RAISING"))
				return true;
			else
				return false;
		}
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

}
