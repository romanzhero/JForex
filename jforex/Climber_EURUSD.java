package jforex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import jforex.TradeStateController.TradeState;
import jforex.filters.FilterFactory;
import jforex.filters.IFilter;
import jforex.utils.FXUtils;
import jforex.utils.Logger;
import jforex.utils.FXUtils.EntryFilters;

import com.dukascopy.api.*;
import com.dukascopy.api.IIndicators.AppliedPrice;

public class Climber_EURUSD extends AbstractSinglePairSingleStrategy implements
		IStrategy {
	public Climber_EURUSD(Properties p) {
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
			// // But it also mustn't lower the protective stop !
			// if (bidBar.getLow() - safeZone / 10000 > protectiveStop) {
			protectiveStop = bidBar.getLow() - safeZone / 10000;
			lastStopType = LastStopType.TRAILING;
			positionOrder.setStopLossPrice(protectiveStop);
			// }
			break;
		case POSITION_CLOSED:
			break;
		case ENTRY_ORDER_CANCELLED:
			break;
		default:
			return;
		}
		// plus drawing stuff
		// IChart chart = context.getChart(instrument);
		// chart.repaint();
	}

	private IBar entrySignalFound(Instrument instrument, Period period,
			IBar askBar, IBar bidBar) throws JFException {
		// basic setup definition first
		double MAsMaxDiffStDevPos = trendDetector.getUptrendMAsMaxDifStDevPos(
				instrument, period, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime(), 1000);
		if (MAsMaxDiffStDevPos == -1000
				|| MAsMaxDiffStDevPos < trendStDevDefinitionMin
				|| MAsMaxDiffStDevPos > trendStDevDefinitionMax)
			return null;
		IBar triggerLowBar = tradeTrigger.bullishReversalCandlePatternBar(
				instrument, period, OfferSide.BID, bidBar.getTime());
		if (triggerLowBar == null)
			return null;
		double protectiveStopCandidate = triggerLowBar.getLow();

		// IFilter channelPosFilter = null;
		// if (allFilters.containsKey(FilterFactory.FILTER_PREFIX +
		// "30min_ChannelPos")) {
		// channelPosFilter = allFilters.get(FilterFactory.FILTER_PREFIX +
		// "30min_ChannelPos");
		// double[] entryHigh = {bidBar.getHigh()};
		// channelPosFilter.setAuxParams(entryHigh);
		// }

		// IFilter condChannelPosMAsPositionFilter = null;
		// if (allFilters.containsKey(FilterFactory.FILTER_CONDITIONAL_PREFIX +
		// "4h_ChannelPos_MAsPosition_4h")) {
		// condChannelPosMAsPositionFilter =
		// allFilters.get(FilterFactory.FILTER_CONDITIONAL_PREFIX +
		// "4h_ChannelPos_MAsPosition_4h");
		// double[] entryPriceArray = {bidBar.getHigh()};
		// condChannelPosMAsPositionFilter.setAuxParams(entryPriceArray);
		// }

		Collection<IFilter> filters = allFilters.values();
		for (IFilter f : filters) {
			if (f.getName().equals(
					FilterFactory.FILTER_PREFIX + "30min_EntryChannelPos")) {
				double[] entryHigh = { bidBar.getTime(), bidBar.getHigh() };
				f.setAuxParams(entryHigh);
			}
			if (f.getName().equals(
					FilterFactory.FILTER_PREFIX + "30min_StopChannelPos")) {
				double[] stopLow = { triggerLowBar.getTime(),
						triggerLowBar.getLow() };
				f.setAuxParams(stopLow);
			}
			if (f.getName().equals(
					FilterFactory.FILTER_CONDITIONAL_PREFIX
							+ "4h_ChannelPos_MAsPosition_4h")) {
				double[] entryPriceArray = { bidBar.getTime(), bidBar.getHigh() };
				f.setAuxParams(entryPriceArray);
			}
			if (f.getName().equals(
					FilterFactory.FILTER_CONDITIONAL_PREFIX
							+ "30min_ChannelPos_ADX_4h")) {
				double[] triggerLowBarTime = { triggerLowBar.getTime(),
						triggerLowBar.getLow() };
				f.setAuxParams(triggerLowBarTime);
			}
			boolean passed = f.check(instrument, OfferSide.BID,
					AppliedPrice.CLOSE, bidBar.getTime());
			if (!passed) {
				if (reportFilters)
					log.print(f.explain(bidBar.getTime()));
				if (useFilters)
					return null;
			}
		}
		// managed to pass all the filters
		protectiveStop = protectiveStopCandidate;
		return triggerLowBar;
	}

	/**
	 * @param lookBack
	 *            - statistical sample size
	 * @param entryPrice
	 * @return true if all the conditions according to 4h timeframe are OK,
	 *         false if no valid signal
	 */
	private boolean check4hTimeFrame(Instrument instrument, Period period,
			OfferSide bid, AppliedPrice close, long time, int lookBack,
			double entryPrice) throws JFException {
		IFilter adxMAsFilter = null, uptrendMAsDistanceFilter = null, channelPosFilter = null, stochsDiffFilter = null, macdSignalFilter = null, macdHFilter = null, downtrendMAsDistanceFilter = null, stochsSlowFilter = null, stochsFastFilter = null, adxDiPlusMAsFilter = null;
		List<IFilter> allFilters = new ArrayList<IFilter>();

		if (filtersConfiguration
				.containsKey(FilterFactory.FILTER_CONDITIONAL_PREFIX
						+ "4h_ADX_MAsPosition_4h")) {
			adxMAsFilter = FilterFactory
					.createFilter(
							FilterFactory.FILTER_CONDITIONAL_PREFIX
									+ "4h_ADX_MAsPosition_4h",
							filtersConfiguration
									.getProperty(FilterFactory.FILTER_CONDITIONAL_PREFIX
											+ "4h_ADX_MAsPosition_4h"),
							indicators, history);
			allFilters.add(adxMAsFilter);
		}
		if (filtersConfiguration
				.containsKey(FilterFactory.FILTER_CONDITIONAL_PREFIX
						+ "4h_UptrendMAsDistance_MAsPosition_4h")) {
			uptrendMAsDistanceFilter = FilterFactory
					.createFilter(
							FilterFactory.FILTER_CONDITIONAL_PREFIX
									+ "4h_UptrendMAsDistance_MAsPosition_4h",
							filtersConfiguration
									.getProperty(FilterFactory.FILTER_CONDITIONAL_PREFIX
											+ "4h_UptrendMAsDistance_MAsPosition_4h"),
							indicators, history);
			allFilters.add(uptrendMAsDistanceFilter);
		}
		if (filtersConfiguration
				.containsKey(FilterFactory.FILTER_CONDITIONAL_PREFIX
						+ "4h_ChannelPos_MAsPosition_4h")) {
			channelPosFilter = FilterFactory
					.createFilter(
							FilterFactory.FILTER_CONDITIONAL_PREFIX
									+ "4h_ChannelPos_MAsPosition_4h",
							filtersConfiguration
									.getProperty(FilterFactory.FILTER_CONDITIONAL_PREFIX
											+ "4h_ChannelPos_MAsPosition_4h"),
							indicators, history);
			double[] entryPriceArray = new double[1];
			entryPriceArray[0] = entryPrice;
			channelPosFilter.setAuxParams(entryPriceArray);
			allFilters.add(channelPosFilter);
		}
		if (filtersConfiguration
				.containsKey(FilterFactory.FILTER_CONDITIONAL_PREFIX
						+ "4h_StochsDiff_MAsPosition_4h")) {
			stochsDiffFilter = FilterFactory
					.createFilter(
							FilterFactory.FILTER_CONDITIONAL_PREFIX
									+ "4h_StochsDiff_MAsPosition_4h",
							filtersConfiguration
									.getProperty(FilterFactory.FILTER_CONDITIONAL_PREFIX
											+ "4h_StochsDiff_MAsPosition_4h"),
							indicators, history);
			allFilters.add(stochsDiffFilter);
		}
		if (filtersConfiguration
				.containsKey(FilterFactory.FILTER_CONDITIONAL_PREFIX
						+ "4h_MACDSignal_MAsPosition_4h")) {
			macdSignalFilter = FilterFactory
					.createFilter(
							FilterFactory.FILTER_CONDITIONAL_PREFIX
									+ "4h_MACDSignal_MAsPosition_4h",
							filtersConfiguration
									.getProperty(FilterFactory.FILTER_CONDITIONAL_PREFIX
											+ "4h_MACDSignal_MAsPosition_4h"),
							indicators, history);
			allFilters.add(macdSignalFilter);
		}
		if (filtersConfiguration
				.containsKey(FilterFactory.FILTER_CONDITIONAL_PREFIX
						+ "4h_MACDHistogram_MAsPosition_4h")) {
			macdHFilter = FilterFactory
					.createFilter(
							FilterFactory.FILTER_CONDITIONAL_PREFIX
									+ "4h_MACDHistogram_MAsPosition_4h",
							filtersConfiguration
									.getProperty(FilterFactory.FILTER_CONDITIONAL_PREFIX
											+ "4h_MACDHistogram_MAsPosition_4h"),
							indicators, history);
			allFilters.add(macdHFilter);
		}
		if (filtersConfiguration
				.containsKey(FilterFactory.FILTER_CONDITIONAL_PREFIX
						+ "4h_DowntrendMAsDistance_MAsPosition_4h")) {
			downtrendMAsDistanceFilter = FilterFactory
					.createFilter(
							FilterFactory.FILTER_CONDITIONAL_PREFIX
									+ "4h_DowntrendMAsDistance_MAsPosition_4h",
							filtersConfiguration
									.getProperty(FilterFactory.FILTER_CONDITIONAL_PREFIX
											+ "4h_DowntrendMAsDistance_MAsPosition_4h"),
							indicators, history);
			allFilters.add(downtrendMAsDistanceFilter);
		}
		if (filtersConfiguration
				.containsKey(FilterFactory.FILTER_CONDITIONAL_PREFIX
						+ "4h_StochSlow_MAsPosition_4h")) {
			stochsSlowFilter = FilterFactory
					.createFilter(
							FilterFactory.FILTER_CONDITIONAL_PREFIX
									+ "4h_StochSlow_MAsPosition_4h",
							filtersConfiguration
									.getProperty(FilterFactory.FILTER_CONDITIONAL_PREFIX
											+ "4h_StochSlow_MAsPosition_4h"),
							indicators, history);
			allFilters.add(stochsSlowFilter);
		}
		if (filtersConfiguration
				.containsKey(FilterFactory.FILTER_CONDITIONAL_PREFIX
						+ "4h_StochFast_MAsPosition_4h")) {
			stochsFastFilter = FilterFactory
					.createFilter(
							FilterFactory.FILTER_CONDITIONAL_PREFIX
									+ "4h_StochFast_MAsPosition_4h",
							filtersConfiguration
									.getProperty(FilterFactory.FILTER_CONDITIONAL_PREFIX
											+ "4h_StochFast_MAsPosition_4h"),
							indicators, history);
			allFilters.add(stochsFastFilter);
		}
		if (filtersConfiguration
				.containsKey(FilterFactory.FILTER_CONDITIONAL_PREFIX
						+ "4h_ADXDiPlus_MAsPosition_4h")) {
			adxDiPlusMAsFilter = FilterFactory
					.createFilter(
							FilterFactory.FILTER_CONDITIONAL_PREFIX
									+ "4h_ADXDiPlus_MAsPosition_4h",
							filtersConfiguration
									.getProperty(FilterFactory.FILTER_CONDITIONAL_PREFIX
											+ "4h_ADXDiPlus_MAsPosition_4h"),
							indicators, history);
			allFilters.add(adxDiPlusMAsFilter);
		}

		for (IFilter f : allFilters) {
			if (!f.check(instrument, bid, close, time))
				return false;
		}

		// if (trendDetector.getUptrendMAsMaxDifStDevPos(instrument, period,
		// OfferSide.BID, AppliedPrice.CLOSE, timeOfPrevBarHigherTimeFrame,
		// lookBack) != -1000) {
		// // uptrend:
		// // ADX >= 10.4 <= 49.6
		// // Strength <= 1.74
		// // entryChannelPos >= 48 <= 93.2
		// // StocsDiff >= -9.5 <= 10.4
		// // MACD_Signal >= -0.00257 <= 0.01555
		// // MACD_H >= -0.00103
		// ///if (adxs4h[0] < 10.35 || adxs4h[0] > 49.54)
		// if (!adxMAsFilter.check(instrument, bid, close,
		// timeOfPrevBarHigherTimeFrame))
		// return false;
		// //if (trendStDevPos > 1.735)
		// if (!uptrendMAsDistanceFilter.check(instrument, bid, close,
		// timeOfPrevBarHigherTimeFrame))
		// return false;
		// //if (triggerBar4hChannelPos < 47.5 || triggerBar4hChannelPos >
		// 93.15)
		// if (!channelPosFilter.check(instrument, bid, close,
		// timeOfPrevBarHigherTimeFrame))
		// return false;
		// //if (stochs4hDiff < -9.55 || stochs4hDiff > 10.34)
		// if (!stochsDiffFilter.check(instrument, bid, close,
		// timeOfPrevBarHigherTimeFrame))
		// return false;
		// //if (macds4h[Momentum.MACD_Signal] < -0.002575 ||
		// macds4h[Momentum.MACD_Signal] > 0.015545)
		// if (!macdSignalFilter.check(instrument, bid, close,
		// timeOfPrevBarHigherTimeFrame))
		// return false;
		// //if (macds4h[Momentum.MACD_H] < -0.001035)
		// if (!macdHFilter.check(instrument, bid, close,
		// timeOfPrevBarHigherTimeFrame))
		// return false;
		// }
		// else if (trendDetector.getDowntrendMAsMaxDifStDevPos(instrument,
		// period, OfferSide.BID, AppliedPrice.CLOSE,
		// timeOfPrevBarHigherTimeFrame, lookBack) != -1000) {
		// // downtrend
		// // Strength >= -1.28
		// // ADX >= 15.9 <= 36.6
		// // StochDiff >= -10.6 <= 16.5
		// // MACD_H >= 0.00021
		// // StochSlow >= 49.1
		// // StochFast >= 56.7
		// // MACD_Signal >= -0.00763
		// //if (trendStDevPos < -1.285)
		// if (!downtrendMAsDistanceFilter.check(instrument, bid, close,
		// timeOfPrevBarHigherTimeFrame))
		// return false;
		// //if (adxs4h[0] < 15.85 || adxs4h[0] > 36.55)
		// if (!adxMAsFilter.check(instrument, bid, close,
		// timeOfPrevBarHigherTimeFrame))
		// return false;
		// //if (stochs4hDiff < -10.75 || stochs4hDiff > 16.55)
		// if (!stochsDiffFilter.check(instrument, bid, close,
		// timeOfPrevBarHigherTimeFrame))
		// return false;
		// //if (macds4h[Momentum.MACD_H] < 0.000205)
		// if (!macdHFilter.check(instrument, bid, close,
		// timeOfPrevBarHigherTimeFrame))
		// return false;
		// //if (stochs4h[1] < 49.05)
		// if (!stochsSlowFilter.check(instrument, bid, close,
		// timeOfPrevBarHigherTimeFrame))
		// return false;
		// //if (stochs4h[0] < 56.65)
		// if (!stochsFastFilter.check(instrument, bid, close,
		// timeOfPrevBarHigherTimeFrame))
		// return false;
		// //if (macds4h[Momentum.MACD_Signal] < -0.007635)
		// if (!macdSignalFilter.check(instrument, bid, close,
		// timeOfPrevBarHigherTimeFrame))
		// return false;
		// }
		// else {
		// // flat
		// // entryChannelPos <= 78.4
		// // ADX <= 33.3
		// // DI_PLUS >= 19.7
		// //trendStDevPos = trendDetector.getMAsMaxDifStDevPos(instrument,
		// period, OfferSide.BID, AppliedPrice.CLOSE,
		// timeOfPrevBarHigherTimeFrame, lookBack);
		// //if (triggerBar4hChannelPos > 78.35)
		// if (!channelPosFilter.check(instrument, bid, close,
		// timeOfPrevBarHigherTimeFrame))
		// return false;
		// //if (adxs4h[0] > 33.25)
		// if (!adxMAsFilter.check(instrument, bid, close,
		// timeOfPrevBarHigherTimeFrame))
		// return false;
		// //if (adxs4h[1] < 19.65)
		// if (!adxDiPlusMAsFilter.check(instrument, bid, close,
		// timeOfPrevBarHigherTimeFrame))
		// return false;
		// }
		return true;
	}

	/**
	 * There are two reasons to raise protective stop on finished bar (various
	 * profit protections are done in onTick) 1. bearish reversal pattern
	 * happened with pivot high above very high in BBands channel (95%) 2.
	 * bullish reversal pattern with pivot dipping into EMA9, but only if this
	 * ensures profit above 20 pips (TODO: experiment with ATR and other
	 * flexible methods)
	 */
	private boolean raiseProtectiveStopSignalFound(Instrument instrument,
			Period period, IBar askBar, IBar bidBar) throws JFException {
		double newStopCandidate = bearishReversalHighInChannel(instrument,
				period, askBar, bidBar);
		if (newStopCandidate != Double.MAX_VALUE
				&& newStopCandidate > protectiveStop) {
			// TODO: make configurable and test ! if current profit is still
			// below predefined profit protection level, just set to breakeven
			// if(newStopCandidate < positionOrder.getOpenPrice() +
			// BREAK_EVEN_PROFIT_THRESHOLD / 10000)
			// protectiveStop = positionOrder.getOpenPrice() - safeZone / 10000;
			// else
			protectiveStop = newStopCandidate - safeZone / 10000;
			lastStopType = LastStopType.BEARISH_TRIGGER_HIGH_IN_CHANNEL;
			return true;
		}

		newStopCandidate = emaBullishDip(instrument, period, askBar, bidBar, 9);
		if (newStopCandidate != Double.MIN_VALUE
				&& newStopCandidate > protectiveStop
				// no use of EMA dip before breakeven
				&& newStopCandidate > positionOrder.getOpenPrice()) {
			// TODO: make configurable and test ! if current profit is still
			// below predefined profit protection level, just set to breakeven
			// if(newStopCandidate < positionOrder.getOpenPrice() +
			// BREAK_EVEN_PROFIT_THRESHOLD / 10000)
			// protectiveStop = positionOrder.getOpenPrice() - safeZone / 10000;
			// else
			protectiveStop = newStopCandidate - safeZone / 10000;
			lastStopType = LastStopType.EMA9;
			return true;
		}
		return false;
	}

	private double bearishReversalHighInChannel(Instrument instrument,
			Period period, IBar askBar, IBar bidBar) throws JFException {
		// double newStopCandidate =
		// tradeTrigger.bearishReversalCandlePattern(instrument, period,
		// OfferSide.BID, bidBar.getTime());
		// if (newStopCandidate == Double.MAX_VALUE || newStopCandidate <=
		// protectiveStop) // avoid lowering existing protective stops
		// return Double.MAX_VALUE;
		//
		// if (channelPosition.bullishTriggerChannelStats(instrument, period,
		// OfferSide.BID, bidBar.getTime())[0] > 95.0)
		// return newStopCandidate;
		// else
		// return Double.MAX_VALUE;

		double newStopCandidate = bidBar.getLow();
		if (tradeTrigger.bearishReversalCandlePattern(instrument, period,
				OfferSide.BID, bidBar.getTime()) == Double.MAX_VALUE
				|| newStopCandidate <= protectiveStop) // avoid lowering
														// existing protective
														// stops
			return Double.MAX_VALUE;

		if (channelPosition.bullishTriggerChannelStats(instrument, period,
				OfferSide.BID, bidBar.getTime())[0] > 95.0)
			return newStopCandidate;
		else
			return Double.MAX_VALUE;
	}

	private double emaBullishDip(Instrument instrument, Period period,
			IBar askBar, IBar bidBar, int lookback) throws JFException {
		double newStopCandidate = tradeTrigger.bullishReversalCandlePattern(
				instrument, period, OfferSide.BID, bidBar.getTime());
		if (newStopCandidate == Double.MIN_VALUE)
			return Double.MIN_VALUE;

		// Careful, we want ema9 and ma20 not of the last bar but one before -
		// therefore 2 candles before and index 1
		double ema9 = indicators.ema(instrument, period, OfferSide.BID,
				AppliedPrice.CLOSE, lookback, Filter.WEEKENDS, 2,
				bidBar.getTime(), 0)[1];
		double ma20 = indicators
				.sma(instrument, period, OfferSide.BID, AppliedPrice.CLOSE, 20,
						Filter.WEEKENDS, 2, bidBar.getTime(), 0)[1];

		if (newStopCandidate > protectiveStop // avoid lowering existing
												// protective stops
				// && newStopCandidate > positionOrder.getOpenPrice() // only
				// after breakeven
				&& ema9 > ma20 && newStopCandidate < ema9) {
			return newStopCandidate;
		} else
			return Double.MIN_VALUE;
	}

	private boolean startT1BLSignalFound(Instrument instrument, Period period,
			IBar askBar, IBar bidBar) throws JFException {
		return tradeTrigger.startT1BL(positionOrder, instrument, period,
				OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime());
	}

	@Override
	protected String getReportFileName() {
		return getStrategyName() + "_";
	}

	@Override
	protected String getStrategyName() {
		return "Climber_EURUSD";
	}

	private boolean entrySignalFoundOldV1(Instrument instrument, Period period,
			IBar askBar, IBar bidBar) throws JFException {
		// basic setup definition first
		double MAsMaxDiffStDevPos = trendDetector.getUptrendMAsMaxDifStDevPos(
				instrument, period, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime(), 1000);
		if (MAsMaxDiffStDevPos == -1000
				|| MAsMaxDiffStDevPos < trendStDevDefinitionMin
				|| MAsMaxDiffStDevPos > trendStDevDefinitionMax)
			return false;
		double protectiveStopCandidate = tradeTrigger
				.bullishReversalCandlePattern(instrument, period,
						OfferSide.BID, bidBar.getTime());
		if (protectiveStopCandidate == Double.MIN_VALUE)
			return false;

		// 4h Signal filtering first
		// Time of last higherTimeFrame bar needed
		long timeOfPrevBarHigherTimeFrame = history.getPreviousBarStart(
				higherTimeFrame, bidBar.getTime());
		if (entryFilters.contains(EntryFilters.FILTER_4H)
				&& !check4hTimeFrame(instrument, higherTimeFrame,
						OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime(), // timeOfPrevBarHigherTimeFrame,
						720, bidBar.getHigh()))
			return false;

		// Signal filtering first
		if (entryFilters.contains(EntryFilters.FILTER_MOMENTUM)) {
			if (momentum.momentumDown(instrument, period, OfferSide.BID,
					AppliedPrice.CLOSE, bidBar.getTime()))
				return false;

			IFilter adxFilter = null, diPlusOnADXsPositionFilter = null;
			if (filtersConfiguration.containsKey(FilterFactory.FILTER_PREFIX
					+ "30min_ADX")) {
				adxFilter = FilterFactory.createFilter(
						FilterFactory.FILTER_PREFIX + "30min_ADX",
						filtersConfiguration
								.getProperty(FilterFactory.FILTER_PREFIX
										+ "30min_ADX"), indicators, history);
				// allFilters.add(adxFilter);
			}
			if (filtersConfiguration
					.containsKey(FilterFactory.FILTER_CONDITIONAL_PREFIX
							+ "30min_ADXDiPlus_ADXsPosition_30min")) {
				diPlusOnADXsPositionFilter = FilterFactory
						.createFilter(
								FilterFactory.FILTER_CONDITIONAL_PREFIX
										+ "30min_ADXDiPlus_ADXsPosition_30min",
								filtersConfiguration
										.getProperty(FilterFactory.FILTER_CONDITIONAL_PREFIX
												+ "30min_ADXDiPlus_ADXsPosition_30min"),
								indicators, history);
				// allFilters.add(diPlusOnADXsPositionFilter);
			}
			if (!adxFilter.check(instrument, OfferSide.BID, AppliedPrice.CLOSE,
					bidBar.getTime())
					|| !diPlusOnADXsPositionFilter
							.check(instrument, OfferSide.BID,
									AppliedPrice.CLOSE, bidBar.getTime()))
				return false;
		}

		double[] last2BarsChannelPos = channelPosition
				.bullishTriggerChannelStats(instrument, period, OfferSide.BID,
						bidBar.getTime());
		if (entryFilters.contains(EntryFilters.FILTER_ENTRY_BAR_HIGH)) {
			IFilter channelPosFilter = null;
			if (filtersConfiguration.containsKey(FilterFactory.FILTER_PREFIX
					+ "30min_ChannelPos")) {
				channelPosFilter = FilterFactory.createFilter(
						FilterFactory.FILTER_PREFIX + "30min_ChannelPos",
						filtersConfiguration
								.getProperty(FilterFactory.FILTER_PREFIX
										+ "30min_ChannelPos"), indicators,
						history);
				double[] entryHigh = { bidBar.getHigh() };
				channelPosFilter.setAuxParams(entryHigh);
				// allFilters.add(channelPosFilter);
			}
			// double maxChannelPosHigh = 71.2;
			// if (last2BarsChannelPos[0] > maxChannelPosHigh)
			if (!channelPosFilter.check(instrument, OfferSide.BID,
					AppliedPrice.CLOSE, bidBar.getTime()))
				return false;
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

		// TODO: IMPORTANT ! This is NOT valid for 1-bar triggers ! These need
		// low of the LAST bar, not one before !
		double minChannelPosLow = Double.MAX_VALUE;
		if (entryFilters.contains(EntryFilters.FILTER_ENTRY_BAR_LOW)) {
			IFilter channelPosADXFilter = null;
			if (filtersConfiguration
					.containsKey(FilterFactory.FILTER_CONDITIONAL_PREFIX
							+ "30min_ChannelPos_ADX_4h")) {
				channelPosADXFilter = FilterFactory
						.createFilter(
								FilterFactory.FILTER_CONDITIONAL_PREFIX
										+ "30min_ChannelPos_ADX_4h",
								filtersConfiguration
										.getProperty(FilterFactory.FILTER_CONDITIONAL_PREFIX
												+ "30min_ChannelPos_ADX_4h"),
								indicators, history);
				List<IBar> bars = history.getBars(instrument, period,
						OfferSide.BID, Filter.WEEKENDS, 2, bidBar.getTime(), 0);
				double[] prevBarLow = { bars.get(0).getLow() };
				channelPosADXFilter.setAuxParams(prevBarLow);
				// allFilters.add(channelPosADXFilter);
			}
			minChannelPosLow = 51.0;
			double[] adx4h = trendDetector.getADXs(instrument, higherTimeFrame,
					OfferSide.BID, timeOfPrevBarHigherTimeFrame);
			if (adx4h[0] > 40.0)
				minChannelPosLow = 0.0;
			else
				minChannelPosLow = 11.9;
			// if (last2BarsChannelPos[1] >= minChannelPosLow)
			if (!channelPosADXFilter.check(instrument, OfferSide.BID,
					AppliedPrice.CLOSE, bidBar.getTime()))
				return false;
		}

		// managed to pass all the filters
		protectiveStop = protectiveStopCandidate;
		return true;
	}

}