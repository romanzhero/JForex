package jforex.techanalysis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jforex.utils.FXUtils;
import jforex.utils.log.FlexLogEntry;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.IIndicators.AppliedPrice;

public class TAEventsSource {
	protected IHistory history;
	protected IIndicators indicators;

	protected Trend trendDetector;
	protected Channel channelPosition;
	protected Momentum momentum;
	protected Volatility vola;
	protected TradeTrigger tradeTrigger;

	public TAEventsSource(IHistory history, IIndicators indicators) {
		super();
		this.history = history;
		this.indicators = indicators;

		trendDetector = new Trend(indicators);
		channelPosition = new Channel(history, indicators);
		tradeTrigger = new TradeTrigger(indicators, history, null);
		momentum = new Momentum(history, indicators);
		vola = new Volatility(indicators);
	}

	public void addADXStats(Instrument instrument, IBar bidBar, Period period,
			List<FlexLogEntry> logLine) throws JFException {
		logLine.add(new FlexLogEntry("ADX30min", new Double(momentum.getADXs(
				instrument, period, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime())[0]), FXUtils.df1));
		logLine.add(new FlexLogEntry("DI_PLUS30min", new Double(indicators
				.plusDi(instrument, period, OfferSide.BID, 14, Filter.WEEKENDS,
						1, bidBar.getTime(), 0)[0]), FXUtils.df1));
		logLine.add(new FlexLogEntry("DI_MINUS30min", new Double(indicators
				.minusDi(instrument, period, OfferSide.BID, 14,
						Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]),
				FXUtils.df1));

		logLine.add(new FlexLogEntry("ADX4h", new Double(momentum.getADXs(
				instrument, Period.FOUR_HOURS, OfferSide.BID,
				AppliedPrice.CLOSE, bidBar.getTime())[0]), FXUtils.df1));
		logLine.add(new FlexLogEntry("DI_PLUS4h", new Double(indicators.plusDi(
				instrument, Period.FOUR_HOURS, OfferSide.BID, 14,
				Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]), FXUtils.df1));
		logLine.add(new FlexLogEntry("DI_MINUS4h", new Double(indicators
				.minusDi(instrument, Period.FOUR_HOURS, OfferSide.BID, 14,
						Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]),
				FXUtils.df1));

		long prevDayTime = history.getPreviousBarStart(
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		logLine.add(new FlexLogEntry("ADX1d", new Double(momentum.getADXs(
				instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				AppliedPrice.CLOSE, prevDayTime)[0]), FXUtils.df1));
		logLine.add(new FlexLogEntry("DI_PLUS1d", new Double(indicators.plusDi(
				instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, 14,
				Filter.WEEKENDS, 1, prevDayTime, 0)[0]), FXUtils.df1));
		logLine.add(new FlexLogEntry("DI_MINUS1d",
				new Double(indicators.minusDi(instrument,
						Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, 14,
						Filter.WEEKENDS, 1, prevDayTime, 0)[0]), FXUtils.df1));

	}

	public void addCCIStats(Instrument instrument, IBar bidBar, Period period,
			List<FlexLogEntry> logLine) throws JFException {
		logLine.add(new FlexLogEntry("CCI30min", new Double(indicators.cci(
				instrument, period, OfferSide.BID, 14, Filter.WEEKENDS, 1,
				bidBar.getTime(), 0)[0]), FXUtils.df1));
		logLine.add(new FlexLogEntry("CCI4h", new Double(indicators.cci(
				instrument, Period.FOUR_HOURS, OfferSide.BID, 14,
				Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]), FXUtils.df1));

		long prevDayTime = history.getPreviousBarStart(
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		logLine.add(new FlexLogEntry("CCI1d", new Double(indicators.cci(
				instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, 14,
				Filter.WEEKENDS, 1, prevDayTime, 0)[0]), FXUtils.df1));

		logLine.add(new FlexLogEntry("CCIState30min", new String(momentum
				.getCCIState(instrument, Period.THIRTY_MINS, OfferSide.BID,
						bidBar.getTime()).toString())));
		logLine.add(new FlexLogEntry("CCIState4h", new String(momentum
				.getCCIState(instrument, Period.FOUR_HOURS, OfferSide.BID,
						bidBar.getTime()).toString())));
		logLine.add(new FlexLogEntry("CCIState1d", new String(momentum
				.getCCIState(instrument, Period.DAILY_SUNDAY_IN_MONDAY,
						OfferSide.BID, prevDayTime).toString())));
	}

	public void addSetups(Instrument instrument, IBar bidBar, Period period,
			List<FlexLogEntry> logLine) throws JFException {
		if (!period.equals(Period.FOUR_HOURS))
			return;

		String setups = new String();
		if (potential4hBullishEntrySignal(instrument, period, bidBar, logLine))
			setups += "bullish4h";
		if (potential4hBearishEntrySignal(instrument, period, bidBar, logLine)) {
			if (setups.length() > 0)
				setups += " AND ";
			setups += "bearish4h";
		}
		if (setups.length() > 0)
			logLine.add(new FlexLogEntry("setup4h", setups));
	}

	public void addCandlesOneTimeFrame(Instrument instrument, IBar bidBar, Period period, List<FlexLogEntry> logLine) throws JFException {
		logLine.add(new FlexLogEntry("barOpen"
				+ FXUtils.timeFrameNamesMap.get(period.toString()), new Double(bidBar.getOpen()), FXUtils.df5));
		logLine.add(new FlexLogEntry("barClose"
				+ FXUtils.timeFrameNamesMap.get(period.toString()), new Double(bidBar.getClose()), FXUtils.df5));
		logLine.add(new FlexLogEntry("barLow"
				+ FXUtils.timeFrameNamesMap.get(period.toString()), new Double(bidBar.getLow()), FXUtils.df5));
		logLine.add(new FlexLogEntry("barHigh"
				+ FXUtils.timeFrameNamesMap.get(period.toString()), new Double(bidBar.getHigh()), FXUtils.df5));

		if (bidBar.getHigh() != bidBar.getLow()) {
			logLine.add(new FlexLogEntry("upperHandlePerc"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(tradeTrigger.barsUpperHandlePerc(bidBar)), FXUtils.df1));
			int sign = bidBar.getClose() > bidBar.getOpen() ? 1 : -1;
			logLine.add(new FlexLogEntry("barBodyPerc"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(sign * tradeTrigger.barsBodyPerc(bidBar)), FXUtils.df1));
			logLine.add(new FlexLogEntry("lowerHandlePerc"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(tradeTrigger.barsLowerHandlePerc(bidBar)), FXUtils.df1));
		}

		double barStat = tradeTrigger.barLengthStatPos(instrument, period,
				OfferSide.BID, bidBar, FXUtils.MONTH_WORTH_OF_30min_BARS);
		logLine.add(new FlexLogEntry("barStat"
				+ FXUtils.timeFrameNamesMap.get(period.toString()), new Double(barStat), FXUtils.df1));
		barStat = tradeTrigger.avgBarLength(instrument, period, OfferSide.BID,
				bidBar, FXUtils.MONTH_WORTH_OF_30min_BARS);
		logLine.add(new FlexLogEntry("bar"
				+ FXUtils.timeFrameNamesMap.get(period.toString()) + "AvgSize",
				new Double(barStat), FXUtils.df1));

		double barOverlap = tradeTrigger.previousBarOverlap(instrument, period,
				OfferSide.BID, bidBar.getTime());
		logLine.add(new FlexLogEntry("prevBarOverlap"
				+ FXUtils.timeFrameNamesMap.get(period.toString()), new Double(barOverlap), FXUtils.df1));

		String candleTriggerStr = new String();
		TradeTrigger.TriggerDesc bullishTriggerDesc = null, bearishTriggerDesc = null;
		if ((bullishTriggerDesc = tradeTrigger.bullishReversalCandlePatternDesc(instrument, period,
						OfferSide.BID, bidBar.getTime())) != null) {
			candleTriggerStr += bullishTriggerDesc.type.toString();
			logLine.add(new FlexLogEntry("bullishCandleTriggerChannelPos"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(bullishTriggerDesc.channelPosition), FXUtils.df1));
			logLine.add(new FlexLogEntry("bullishPivotLevel"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(bullishTriggerDesc.pivotLevel), FXUtils.df5));
			logLine.add(new FlexLogEntry("bullishCandleTriggerKChannelPos"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(bullishTriggerDesc.keltnerChannelPosition),
					FXUtils.df1));

			logLine.add(new FlexLogEntry("bullishTriggerCombinedUHPerc"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(bullishTriggerDesc.combinedUpperHandlePerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry("bullishTriggerCombinedBodyPerc"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(bullishTriggerDesc.combinedRealBodyPerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry("bullishTriggerCombinedLHPerc"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(bullishTriggerDesc.combinedLowerHandlePerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry(
					"bullishTriggerCombinedBodyDirection"
							+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new String(
							bullishTriggerDesc.combinedRealBodyDirection ? "BULLISH"
									: "BEARISH")));
			double reversalSizeStat = tradeTrigger.barLengthStatPos(instrument,
					period, OfferSide.BID, bidBar,
					bullishTriggerDesc.getCombinedHigh(),
					bullishTriggerDesc.getCombinedLow(),
					FXUtils.MONTH_WORTH_OF_30min_BARS);
			logLine.add(new FlexLogEntry("bullishTriggerCombinedSizeStat"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(reversalSizeStat), FXUtils.df1));
		} else {
			logLine.add(new FlexLogEntry("bullishCandleTriggerChannelPos"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(0), FXUtils.df1));
			logLine.add(new FlexLogEntry("bullishPivotLevel"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(0), FXUtils.df5));
			logLine.add(new FlexLogEntry("bullishCandleTriggerKChannelPos"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(0), FXUtils.df1));
		}
		if ((bearishTriggerDesc = tradeTrigger.bearishReversalCandlePatternDesc(instrument, period,
						OfferSide.BID, bidBar.getTime())) != null) {
			if (candleTriggerStr.length() > 0)
				candleTriggerStr += " AND "	+ bearishTriggerDesc.type.toString();
			else
				candleTriggerStr += bearishTriggerDesc.type.toString();
			logLine.add(new FlexLogEntry("bearishCandleTriggerChannelPos"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(bearishTriggerDesc.channelPosition), FXUtils.df1));
			logLine.add(new FlexLogEntry("bearishPivotLevel"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(bearishTriggerDesc.pivotLevel), FXUtils.df5));
			logLine.add(new FlexLogEntry("bearishCandleTriggerKChannelPos"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(bearishTriggerDesc.keltnerChannelPosition),
					FXUtils.df1));

			logLine.add(new FlexLogEntry("bearishTriggerCombinedUHPerc"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(bearishTriggerDesc.combinedUpperHandlePerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry("bearishTriggerCombinedBodyPerc"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(bearishTriggerDesc.combinedRealBodyPerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry("bearishTriggerCombinedLHPerc"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(bearishTriggerDesc.combinedLowerHandlePerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry(
					"bearishTriggerCombinedBodyDirection"
							+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new String(
							bearishTriggerDesc.combinedRealBodyDirection ? "BULLISH"
									: "BEARISH")));
			double reversalSizeStat = tradeTrigger.barLengthStatPos(instrument,
					period, OfferSide.BID, bidBar,
					bearishTriggerDesc.getCombinedHigh(),
					bearishTriggerDesc.getCombinedLow(),
					FXUtils.MONTH_WORTH_OF_30min_BARS);
			logLine.add(new FlexLogEntry("bearishTriggerCombinedSizeStat"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(reversalSizeStat), FXUtils.df1));
		} else {
			logLine.add(new FlexLogEntry("bearishCandleTriggerChannelPos"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(0), FXUtils.df1));
			logLine.add(new FlexLogEntry("bearishPivotLevel"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(0), FXUtils.df5));
			logLine.add(new FlexLogEntry("bearishCandleTriggerKChannelPos"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(0), FXUtils.df1));
		}
		if (candleTriggerStr.length() == 0) {
			logLine.add(new FlexLogEntry("bullishTriggerCombinedUHPerc"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(0), FXUtils.df1));
			logLine.add(new FlexLogEntry("bullishTriggerCombinedBodyPerc"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(0), FXUtils.df1));
			logLine.add(new FlexLogEntry("bullishTriggerCombinedLHPerc"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(0), FXUtils.df1));
			logLine.add(new FlexLogEntry("bullishTriggerCombinedSizeStat"
					+ FXUtils.timeFrameNamesMap.get(period.toString()),
					new Double(0), FXUtils.df1));
		}
		logLine.add(new FlexLogEntry("CandleTrigger"
				+ FXUtils.timeFrameNamesMap.get(period.toString()),
				candleTriggerStr.length() > 0 ? candleTriggerStr : "none"));

	}

	public void addChannelPosStatsOneTimeFrame(Instrument instrument,
			Period basicTimeFrame, IBar bidBar, List<FlexLogEntry> logLine)
			throws JFException {
		double lowChannelPos = 0, lowChannelPosPrevBar = 0;
		lowChannelPos = channelPosition.priceChannelPos(instrument,
				basicTimeFrame, OfferSide.BID, bidBar.getTime(),
				bidBar.getLow());
		logLine.add(new FlexLogEntry("barLowChannelPos"
				+ FXUtils.timeFrameNamesMap.get(basicTimeFrame.toString()),
				new Double(lowChannelPos), FXUtils.df1));
		lowChannelPosPrevBar = channelPosition.bullishTriggerChannelStats(
				instrument, basicTimeFrame, OfferSide.BID, bidBar.getTime())[1];
		logLine.add(new FlexLogEntry("bullishTriggerLowChannelPos"
				+ FXUtils.timeFrameNamesMap.get(basicTimeFrame.toString()),
				new Double(lowChannelPosPrevBar), FXUtils.df1));

		double highChannelPos = 0, highChannelPosPrevBar = 0;
		highChannelPos = channelPosition.priceChannelPos(instrument,
				basicTimeFrame, OfferSide.BID, bidBar.getTime(),
				bidBar.getHigh());
		logLine.add(new FlexLogEntry("barHighChannelPos"
				+ FXUtils.timeFrameNamesMap.get(basicTimeFrame.toString()),
				new Double(highChannelPos), FXUtils.df1));
		highChannelPosPrevBar = channelPosition.bearishTriggerChannelStats(
				instrument, basicTimeFrame, OfferSide.BID, bidBar.getTime())[1];
		logLine.add(new FlexLogEntry("bearishTriggerHighChannelPos"
				+ FXUtils.timeFrameNamesMap.get(basicTimeFrame.toString()),
				new Double(highChannelPosPrevBar), FXUtils.df1));

		int barsAbove = channelPosition.consequitiveBarsAbove(instrument,
				basicTimeFrame, OfferSide.BID, bidBar.getTime(), 10);
		logLine.add(new FlexLogEntry("barsAboveChannel"
				+ FXUtils.timeFrameNamesMap.get(basicTimeFrame.toString()),
				new Integer(barsAbove), FXUtils.if3));
		int barsBelow = channelPosition.consequitiveBarsBelow(instrument,
				basicTimeFrame, OfferSide.BID, bidBar.getTime(), 10);
		logLine.add(new FlexLogEntry("barsBelowChannel"
				+ FXUtils.timeFrameNamesMap.get(basicTimeFrame.toString()),
				new Integer(barsBelow), FXUtils.if3));

		double[] bBandsRaw = channelPosition.getRawBBandsData(instrument,
				basicTimeFrame, OfferSide.BID, bidBar.getTime());

		logLine.add(new FlexLogEntry("bBandsWidth"
				+ FXUtils.timeFrameNamesMap.get(basicTimeFrame.toString()),
				new Double((bBandsRaw[0] - bBandsRaw[2])
						* Math.pow(10, instrument.getPipScale())), FXUtils.df1));
		logLine.add(new FlexLogEntry("bBandsTop"
				+ FXUtils.timeFrameNamesMap.get(basicTimeFrame.toString()),
				new Double(bBandsRaw[0]), FXUtils.df5));
		logLine.add(new FlexLogEntry("bBandsBottom"
				+ FXUtils.timeFrameNamesMap.get(basicTimeFrame.toString()),
				new Double(bBandsRaw[2]), FXUtils.df5));
	}

	public void addFlatStatsOneTimeFrame(Instrument instrument, IBar bidBar,
			Period basicTimeFrame, List<FlexLogEntry> logLine)
			throws JFException {
		double trendStrength = trendDetector.getMAsMaxDiffStDevPos(instrument,
				basicTimeFrame, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime(), FXUtils.MONTH_WORTH_OF_30min_BARS);
		logLine.add(new FlexLogEntry("MAsDistance"
				+ FXUtils.timeFrameNamesMap.get(basicTimeFrame.toString()),
				new Double(trendStrength), FXUtils.df1));
		logLine.add(new FlexLogEntry(
				"IsMA200Highest"
						+ FXUtils.timeFrameNamesMap.get(basicTimeFrame
								.toString()),
				trendDetector.isMA200Highest(instrument, basicTimeFrame,
						OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime()) ? "yes"
						: "no"));
		logLine.add(new FlexLogEntry(
				"IsMA200Lowest"
						+ FXUtils.timeFrameNamesMap.get(basicTimeFrame
								.toString()),
				trendDetector.isMA200Lowest(instrument, basicTimeFrame,
						OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime()) ? "yes"
						: "no"));
	}

	public void addMACDStats(Instrument instrument, IBar bidBar,
			List<FlexLogEntry> logLine) throws JFException {
		Momentum.MACD_STATE macd30minState = momentum.getMACDState(instrument,
				Period.THIRTY_MINS, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime());
		Momentum.MACD_STATE macd4hState = momentum.getMACDState(instrument,
				Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime());
		Momentum.MACD_H_STATE macdH30minState = momentum.getMACDHistogramState(
				instrument, Period.THIRTY_MINS, OfferSide.BID,
				AppliedPrice.CLOSE, bidBar.getTime());
		Momentum.MACD_H_STATE macdH4hState = momentum.getMACDHistogramState(
				instrument, Period.FOUR_HOURS, OfferSide.BID,
				AppliedPrice.CLOSE, bidBar.getTime());
		long prevDayTime = history.getPreviousBarStart(
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		Momentum.MACD_STATE macd1dState = momentum.getMACDState(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				AppliedPrice.CLOSE, prevDayTime);
		Momentum.MACD_H_STATE macdH1dState = momentum.getMACDHistogramState(
				instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				AppliedPrice.CLOSE, prevDayTime);

		logLine.add(new FlexLogEntry("MACDState30min", macd30minState
				.toString()));
		logLine.add(new FlexLogEntry("MACDState4h", macd4hState.toString()));
		logLine.add(new FlexLogEntry("MACDStateOverlap", macd30minState
				.equals(macd4hState) ? "YES (" + macd30minState.toString()
				+ ")" : "no"));
		logLine.add(new FlexLogEntry("MACDHState30min", macdH30minState
				.toString()));
		logLine.add(new FlexLogEntry("MACDHState4h", macdH4hState.toString()));
		logLine.add(new FlexLogEntry("MACDHStateOverlap", macdH30minState
				.equals(macdH4hState) ? "YES (" + macdH30minState.toString()
				+ ")" : "no"));
		logLine.add(new FlexLogEntry("MACDState1d", macd1dState.toString()));
		logLine.add(new FlexLogEntry("MACDHState1d", macdH1dState.toString()));

		Momentum.MACD_CROSS macd30minCross = momentum.getMACDCross(instrument,
				Period.THIRTY_MINS, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime());
		Momentum.MACD_CROSS macd4hCross = momentum.getMACDCross(instrument,
				Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime());
		logLine.add(new FlexLogEntry("MACDCross30min", macd30minCross
				.toString()));
		logLine.add(new FlexLogEntry("MACDCross4h", macd4hCross.toString()));
		Momentum.MACD_CROSS macd1dCross = momentum.getMACDCross(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				AppliedPrice.CLOSE, prevDayTime);
		logLine.add(new FlexLogEntry("MACDCross1d", macd1dCross.toString()));

		double MACD30minStDevPos = momentum.getMACDStDevPos(instrument,
				Period.THIRTY_MINS, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime(), FXUtils.MONTH_WORTH_OF_30min_BARS);
		logLine.add(new FlexLogEntry("MACDStDevPos30min", new Double(
				MACD30minStDevPos), FXUtils.df1));

		double MACD4hStDevPos = momentum.getMACDStDevPos(instrument,
				Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime(), FXUtils.QUARTER_WORTH_OF_4h_BARS);
		logLine.add(new FlexLogEntry("MACDStDevPos4h", new Double(
				MACD4hStDevPos), FXUtils.df1));

		double MACD1dStDevPos = momentum.getMACDStDevPos(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				AppliedPrice.CLOSE, prevDayTime, FXUtils.YEAR_WORTH_OF_1d_BARS);
		logLine.add(new FlexLogEntry("MACDStDevPos1d", new Double(
				MACD1dStDevPos), FXUtils.df1));

		double MACD_H30minStDevPos = momentum.getMACD_HStDevPos(instrument,
				Period.THIRTY_MINS, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime(), FXUtils.MONTH_WORTH_OF_30min_BARS);
		logLine.add(new FlexLogEntry("MACD_HStDevPos30min", new Double(
				MACD_H30minStDevPos), FXUtils.df1));

		double MACD_H4hStDevPos = momentum.getMACD_HStDevPos(instrument,
				Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime(), FXUtils.QUARTER_WORTH_OF_4h_BARS);
		logLine.add(new FlexLogEntry("MACD_HStDevPos4h", new Double(
				MACD_H4hStDevPos), FXUtils.df1));

		double MACD_H1dStDevPos = momentum.getMACD_HStDevPos(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				AppliedPrice.CLOSE, prevDayTime, FXUtils.YEAR_WORTH_OF_1d_BARS);
		logLine.add(new FlexLogEntry("MACD_HStDevPos1d", new Double(
				MACD_H1dStDevPos), FXUtils.df1));
	}

	public void addRSIStats(Instrument instrument, IBar bidBar,
			List<FlexLogEntry> logLine) throws JFException {
		double rsi30min = momentum.getRSI(instrument, Period.THIRTY_MINS,
				OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime());
		logLine.add(new FlexLogEntry("RSI30min", new Double(rsi30min),
				FXUtils.df1));
		logLine.add(new FlexLogEntry("RSIState30min", new String(momentum
				.getRSIState(instrument, Period.THIRTY_MINS, OfferSide.BID,
						bidBar.getTime()).toString())));

		double rsi4h = momentum.getRSI(instrument, Period.FOUR_HOURS,
				OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime());
		logLine.add(new FlexLogEntry("RSI4h", new Double(rsi4h), FXUtils.df1));
		logLine.add(new FlexLogEntry("RSIState4h", new String(momentum
				.getRSIState(instrument, Period.FOUR_HOURS, OfferSide.BID,
						bidBar.getTime()).toString())));

		logLine.add(new FlexLogEntry("RSIOverlap",
				(rsi30min <= 33 && rsi4h <= 33)
						|| (rsi30min >= 67 && rsi4h >= 67) ? "YES ("
						+ FXUtils.df1.format(rsi30min) + "/"
						+ FXUtils.df1.format(rsi4h) + ")" : "no"));

		long prevDayTime = history.getPreviousBarStart(
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		double rsi1d = momentum.getRSI(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				AppliedPrice.CLOSE, prevDayTime);
		logLine.add(new FlexLogEntry("RSI1d", new Double(rsi1d), FXUtils.df1));
		logLine.add(new FlexLogEntry("RSIState1d", new String(momentum
				.getRSIState(instrument, Period.DAILY_SUNDAY_IN_MONDAY,
						OfferSide.BID, prevDayTime).toString())));
	}

	public void addStochStats(Instrument instrument, IBar bidBar,
			List<FlexLogEntry> logLine) throws JFException {
		Momentum.STOCH_STATE stoch30minState = momentum
				.getStochState(instrument, Period.THIRTY_MINS, OfferSide.BID,
						bidBar.getTime());
		Momentum.STOCH_STATE stoch4hState = momentum.getStochState(instrument,
				Period.FOUR_HOURS, OfferSide.BID, bidBar.getTime());

		logLine.add(new FlexLogEntry("StochState30min", stoch30minState
				.toString()));
		logLine.add(new FlexLogEntry("StochState4h", stoch4hState.toString()));
		logLine.add(new FlexLogEntry("StochStateOverlap", stoch30minState
				.equals(stoch4hState) ? "YES (" + stoch30minState.toString()
				+ ")" : "no"));

		logLine.add(new FlexLogEntry("stochsCross30min", momentum
				.getStochCross(instrument, Period.THIRTY_MINS, OfferSide.BID,
						bidBar.getTime()).toString()));
		long prev4hTime = history.getPreviousBarStart(Period.FOUR_HOURS,
				bidBar.getTime());
		logLine.add(new FlexLogEntry("stochsCross4h", momentum.getStochCross(
				instrument, Period.FOUR_HOURS, OfferSide.BID, prev4hTime)
				.toString()));

		double[] stochs30min = momentum.getStochs(instrument,
				Period.THIRTY_MINS, OfferSide.BID, bidBar.getTime());
		logLine.add(new FlexLogEntry("StochFast30min", stochs30min[0],
				FXUtils.df1));
		logLine.add(new FlexLogEntry("StochSlow30min", stochs30min[1],
				FXUtils.df1));
		double[] stochs4h = momentum.getStochs(instrument, Period.FOUR_HOURS,
				OfferSide.BID, bidBar.getTime());
		logLine.add(new FlexLogEntry("StochFast4h", stochs4h[0], FXUtils.df1));
		logLine.add(new FlexLogEntry("StochSlow4h", stochs4h[1], FXUtils.df1));

		long prevDayTime = history.getPreviousBarStart(
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		Momentum.STOCH_STATE stoch1dState = momentum.getStochState(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prevDayTime);
		logLine.add(new FlexLogEntry("StochState1d", stoch1dState.toString()));
		double[] stochs1d = momentum.getStochs(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prevDayTime);
		logLine.add(new FlexLogEntry("StochFast1d", stochs1d[0], FXUtils.df1));
		logLine.add(new FlexLogEntry("StochSlow1d", stochs1d[1], FXUtils.df1));
		logLine.add(new FlexLogEntry("stochsCross1d", momentum.getStochCross(
				instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				prevDayTime).toString()));
	}

	public void collectAllStats(Instrument instrument, Period period,
			IBar bidBar, List<FlexLogEntry> logLine) throws JFException {
		long prevDayTime = history.getPreviousBarStart(
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		long prev4hTime = history.getPreviousBarStart(Period.FOUR_HOURS,
				bidBar.getTime());

		double ATR30min = vola.getATR(instrument, Period.THIRTY_MINS,
				OfferSide.BID, bidBar.getTime(), 14)
				* Math.pow(10, instrument.getPipScale()), ATR4h = vola.getATR(
				instrument, Period.FOUR_HOURS, OfferSide.BID, bidBar.getTime(),
				14)
				* Math.pow(10, instrument.getPipScale()), ATR1d = vola.getATR(
				instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				prevDayTime, 14)
				* Math.pow(10, instrument.getPipScale()), bBandsSqueeze30min = vola
				.getBBandsSqueeze(instrument, Period.THIRTY_MINS,
						OfferSide.BID, bidBar.getTime(), 20), bBandsSqueeze4h = vola
				.getBBandsSqueeze(instrument, Period.FOUR_HOURS, OfferSide.BID,
						prev4hTime, 20), bBandsSqueeze1d = vola
				.getBBandsSqueeze(instrument, Period.DAILY_SUNDAY_IN_MONDAY,
						OfferSide.BID, prevDayTime, 20);

		Trend.TREND_STATE trendID30min = trendDetector.getTrendState(
				instrument, Period.THIRTY_MINS, OfferSide.BID,
				AppliedPrice.CLOSE, bidBar.getTime());
		Trend.TREND_STATE trendID4h = trendDetector.getTrendState(instrument,
				Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime());
		Trend.TREND_STATE trendID1d = trendDetector.getTrendState(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				AppliedPrice.CLOSE, prevDayTime);

		logLine.add(new FlexLogEntry("Pair", instrument.toString()));
		// TODO: remove after understanding for which period is the method
		// called first, 30min or 4h when 4h is full
		logLine.add(new FlexLogEntry("Bars period", period.toString()));
		logLine.add(new FlexLogEntry("BarStartTime", FXUtils
				.getFormatedTimeCET(bidBar.getTime())));

		logLine.add(new FlexLogEntry("TrendId30min", trendID30min.toString()));
		logLine.add(new FlexLogEntry("TrendId4h", trendID4h.toString()));
		double trendStrength30min = trendDetector.getMAsMaxDiffStDevPos(
				instrument, Period.THIRTY_MINS, OfferSide.BID,
				AppliedPrice.CLOSE, bidBar.getTime(),
				FXUtils.MONTH_WORTH_OF_30min_BARS);
		double trendStrength4h = trendDetector.getMAsMaxDiffStDevPos(
				instrument, Period.FOUR_HOURS, OfferSide.BID,
				AppliedPrice.CLOSE, bidBar.getTime(),
				FXUtils.QUARTER_WORTH_OF_4h_BARS);
		String trendIdOverlapStr = null;
		if (trendStrength30min > -0.7 && trendStrength4h > -0.7)
			trendIdOverlapStr = new String("(" + trendID30min.toString() + ")");
		else if (trendStrength30min < -0.7 && trendStrength4h < -0.7)
			trendIdOverlapStr = new String("(FLAT)");

		logLine.add(new FlexLogEntry(
				"TrendIdOverlap",
				trendID30min.equals(trendID4h)
						&& ((trendStrength30min > -0.7 && trendStrength4h > -0.7) || (trendStrength30min < -0.7 && trendStrength4h < -0.7)) ? "YES "
						+ trendIdOverlapStr
						: "no"));

		logLine.add(new FlexLogEntry("TrendId1d", trendID1d.toString()));
		// trend regime
		addFlatStats(instrument, bidBar, period, logLine);
		// momentum
		addMACDStats(instrument, bidBar, logLine);
		// OS/OB
		addRSIStats(instrument, bidBar, logLine);
		addStochStats(instrument, bidBar, logLine);
		// channel position
		addChannelPosStats(instrument, period, bidBar, logLine);
		// candles
		addCandles(instrument, bidBar, period, logLine);
		// setups can be called only at the end when all the stats are collected
		// !
		addSetups(instrument, bidBar, period, logLine);

		logLine.add(new FlexLogEntry("ATR30min", new Double(ATR30min),
				FXUtils.df1));
		logLine.add(new FlexLogEntry("ATR30min + 20%", new Double(
				ATR30min * 1.2), FXUtils.df1));
		logLine.add(new FlexLogEntry("ATR4h", new Double(ATR4h), FXUtils.df1));
		logLine.add(new FlexLogEntry("ATR1d", new Double(ATR1d), FXUtils.df1));
		logLine.add(new FlexLogEntry("volatility1d",
				new Double(bBandsSqueeze1d), FXUtils.df1));
		logLine.add(new FlexLogEntry("volatility4h",
				new Double(bBandsSqueeze4h), FXUtils.df1));
		logLine.add(new FlexLogEntry("volatility30min", new Double(
				bBandsSqueeze30min), FXUtils.df1));

		addADXStats(instrument, bidBar, period, logLine);
		addCCIStats(instrument, bidBar, period, logLine);

		Trend.ICHI_CLOUD_CROSS ichi_1d = trendDetector.isIchiCloudCross(
				history, instrument, Period.DAILY_SUNDAY_IN_MONDAY,
				OfferSide.BID, AppliedPrice.CLOSE, prevDayTime), ichi_4h = trendDetector
				.isIchiCloudCross(history, instrument, Period.FOUR_HOURS,
						OfferSide.BID, AppliedPrice.CLOSE, period
								.equals(Period.FOUR_HOURS) ? bidBar.getTime()
								: prev4hTime), ichi_30min = trendDetector
				.isIchiCloudCross(history, instrument, Period.THIRTY_MINS,
						OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime());
		logLine.add(new FlexLogEntry("IchiCloudCross1d", ichi_1d.toString()));
		logLine.add(new FlexLogEntry("IchiCloudCross4h", ichi_4h.toString()));
		logLine.add(new FlexLogEntry("IchiCloudCross30min", ichi_30min
				.toString()));

		double ichiCloudDist1d = trendDetector.ichiCloudDistCalc(history,
				instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				bidBar, prevDayTime, FXUtils.YEAR_WORTH_OF_1d_BARS), ichiCloudDist4h = trendDetector
				.ichiCloudDistCalc(history, instrument, Period.FOUR_HOURS,
						OfferSide.BID, bidBar,
						period.equals(Period.FOUR_HOURS) ? bidBar.getTime()
								: prev4hTime, FXUtils.YEAR_WORTH_OF_4H_BARS), ichiCloudDist30min = trendDetector
				.ichiCloudDistCalc(history, instrument, Period.THIRTY_MINS,
						OfferSide.BID, bidBar, bidBar.getTime(),
						FXUtils.MONTH_WORTH_OF_30min_BARS);
		logLine.add(new FlexLogEntry("IchiCloudDist1d", new Double(
				ichiCloudDist1d), FXUtils.df2));
		logLine.add(new FlexLogEntry("IchiCloudDist4h", new Double(
				ichiCloudDist4h), FXUtils.df2));
		logLine.add(new FlexLogEntry("IchiCloudDist30min", new Double(
				ichiCloudDist30min), FXUtils.df2));

		/*
		 * Trend.IchiDesc ichiDesc_1d = trendDetector.getIchiCloud(history,
		 * instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
		 * AppliedPrice.CLOSE, prevDayTime), ichiDesc_4h =
		 * trendDetector.getIchiCloud(history, instrument, Period.FOUR_HOURS,
		 * OfferSide.BID, AppliedPrice.CLOSE, prev4hTime), ichiDesc_30min =
		 * trendDetector.getIchiCloud(history, instrument, Period.THIRTY_MINS,
		 * OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime());
		 * 
		 * logLine.add(new FlexLogEntry("IchiCloudWidth1d", new
		 * Double(ichiDesc_1d.widthPips), FXUtils.df1)); logLine.add(new
		 * FlexLogEntry("IchiCloudWidthToATR1d", new
		 * Double(ichiDesc_1d.widthToATR), FXUtils.df1)); logLine.add(new
		 * FlexLogEntry("IchiCloudBullish1d", ichiDesc_1d.isBullishCloud ? "yes"
		 * : "no")); logLine.add(new FlexLogEntry("IchiCloudBullishLines1d",
		 * ichiDesc_1d.isBullishTenkanLine ? "yes" : "no")); logLine.add(new
		 * FlexLogEntry("IchiCloudBottomDirection1d",
		 * ichiDesc_1d.bottomBorderDirection)); logLine.add(new
		 * FlexLogEntry("IchiCloudTopDirection1d",
		 * ichiDesc_1d.topBorderDirection));
		 * 
		 * logLine.add(new FlexLogEntry("IchiCloudWidth4h", new
		 * Double(ichiDesc_4h.widthPips), FXUtils.df1)); logLine.add(new
		 * FlexLogEntry("IchiCloudWidthToATR4h", new
		 * Double(ichiDesc_4h.widthToATR), FXUtils.df1)); logLine.add(new
		 * FlexLogEntry("IchiCloudBullish4h", ichiDesc_4h.isBullishCloud ? "yes"
		 * : "no")); logLine.add(new FlexLogEntry("IchiCloudBullishLines4h",
		 * ichiDesc_4h.isBullishTenkanLine ? "yes" : "no")); logLine.add(new
		 * FlexLogEntry("IchiCloudBottomDirection4h",
		 * ichiDesc_4h.bottomBorderDirection)); logLine.add(new
		 * FlexLogEntry("IchiCloudTopDirection4h",
		 * ichiDesc_4h.topBorderDirection));
		 * 
		 * logLine.add(new FlexLogEntry("IchiCloudWidth30min", new
		 * Double(ichiDesc_30min.widthPips), FXUtils.df1)); logLine.add(new
		 * FlexLogEntry("IchiCloudWidthToATR30min", new
		 * Double(ichiDesc_30min.widthToATR), FXUtils.df1)); logLine.add(new
		 * FlexLogEntry("IchiCloudBullish30min", ichiDesc_30min.isBullishCloud ?
		 * "yes" : "no")); logLine.add(new
		 * FlexLogEntry("IchiCloudBullishLines30min",
		 * ichiDesc_30min.isBullishTenkanLine ? "yes" : "no")); logLine.add(new
		 * FlexLogEntry("IchiCloudBottomDirection30min",
		 * ichiDesc_30min.bottomBorderDirection)); logLine.add(new
		 * FlexLogEntry("IchiCloudTopDirection30min",
		 * ichiDesc_30min.topBorderDirection));
		 */
	}

	public String checkStrong4hFirstEntrySignals(Instrument instrument,
			Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine)
			throws JFException {

		if (!pPeriod.equals(Period.FOUR_HOURS))
			return "";

		// good signals to start FIRST position are when 4h candle triggers
		// occur on extremes of 4h channel in following situations:
		// BULLISH:
		// 1. 1d flat regime, on 1d channel bottom
		// 2. 1d strong uptrend, on at least 1d channel half (4h candle pivot
		// must be below 50% 1d channel)
		// BEARISH:
		// 3. 1d flat regime, on 1d channel top
		// 4. 1d strong downtrend, on at least 1d channel half (4h candle pivot
		// must be above 50% 1d channel)
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}

		String result = new String("");
		if (strong4hBullishFirstEntrySignal(instrument, pPeriod, bidBar,
				logLine)) {
			result = "\nATTENTION: very strong bullish 4h first entry signal !!! Study Elliot Wave count and risk parameters carefully and open LONG position if everything OK !\n";
		} else if (strong4hBearishFirstEntrySignal(instrument, pPeriod, bidBar,
				logLine)) {
			result = "\nATTENTION: very strong bearish 4h first entry signal !!! Study Elliot Wave count and risk parameters carefully and open SHORT position if everything OK !\n";
		}

		return result;
	}

	public boolean strong4hBullishFirstEntrySignal(Instrument instrument,
			Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine)
			throws JFException {

		if (!pPeriod.equals(Period.FOUR_HOURS))
			return false;

		// good signals to start FIRST position are when 4h candle triggers
		// occur on extremes of 4h channel in following situations:
		// BULLISH:
		// 1. 1d flat regime, on 1d channel bottom
		// 2. 1d strong uptrend, on at least 1d channel half (4h candle pivot
		// must be below 50% 1d channel)
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}
		if (mailValuesMap.get("bullishCandleTriggerChannelPos4h") == null)
			return false;

		// now first check NO-GO ZONES !
		// 1. bearish reversal ongoing: clear uptrend but all 3 momentums
		// falling, both MACDs above zero
		// 2. bearish breakout ongoing: strong 4h downtrend, 1d weak but
		// ACCELLERATING downtrend (from flat)
		if (bullishEntryNoGoZone(instrument, pPeriod, bidBar, logLine))
			return false;

		String[] parsedCandles = tradeTrigger
				.parseCandleTriggers(mailStringsMap.get("CandleTrigger4h"));
		String bullishCandles = parsedCandles[0];

		if (bullishCandles == null)
			return false;

		String[] parsedCandles1d = tradeTrigger
				.parseCandleTriggers(mailStringsMap.get("CandleTrigger1d"));
		String bullishCandles1d = parsedCandles1d[0];

		double candleTriggerChannelPos = mailValuesMap.get(
				"bullishCandleTriggerChannelPos4h").getDoubleValue(), candleTriggerChannelPos1d = mailValuesMap
				.get("bullishCandleTriggerChannelPos1d").getDoubleValue(), pivotLevel4h = mailValuesMap
				.get("bullishPivotLevel4h").getDoubleValue();
		Double trendStrength1d = (Double) mailValuesMap.get("MAsDistance1d")
				.getValue();
		// flat 1d regime
		long prev1dBarTime = history.getPreviousBarStart(
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		double channelPos1d = tradeTrigger.priceChannelPos(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prev1dBarTime,
				pivotLevel4h, 0)[0], keltnerPos1d = tradeTrigger
				.priceKeltnerChannelPos(instrument,
						Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
						prev1dBarTime, pivotLevel4h, 0);
		// OK to enter position #1 at 4h channel half in case of 1d bullish
		// triggers:
		// 1: 1d flat and bullish candle trigger on channel bottom
		// 2: 1d uptrend and bullish candle trigger on channel half
		if (bullishCandles1d != null
				&& ((trendStrength1d.doubleValue() < -0.7 && candleTriggerChannelPos1d < 3.0)
						|| (trendStrength1d.doubleValue() >= -0.7
								&& (mailStringsMap.get("TrendId1d").equals(
										"UP_STRONG") || mailStringsMap.get(
										"TrendId1d").equals("UP_MILD")) && candleTriggerChannelPos1d < 50.0) || (trendStrength1d
						.doubleValue() < -0.7
						&& mailStringsMap.get("TrendId1d").equals("UP_STRONG")
						&& trendDetector.isMA200Lowest(instrument, pPeriod,
								OfferSide.BID, AppliedPrice.CLOSE,
								prev1dBarTime) && candleTriggerChannelPos1d < 50.0))) {
			if (candleTriggerChannelPos < 50.0)
				return true;
		}

		// in all other cases 4h candle trigger pivot must be on 4 channel
		// bottom
		if (candleTriggerChannelPos < 3.0) {
			if (trendStrength1d.doubleValue() < -0.7) {
				if (channelPos1d < 3.0
						|| keltnerPos1d < 3.0
						|| (mailStringsMap.get("TrendId1d").equals("UP_STRONG")
								&& trendDetector.isMA200Lowest(instrument,
										pPeriod, OfferSide.BID,
										AppliedPrice.CLOSE, prev1dBarTime) && channelPos1d < 53.0)) {
					return true;
				}
				// TODO: probably need to check also UP_MILD with 1d MA200 the
				// lowest
			} else if (mailStringsMap.get("TrendId1d").equals("UP_STRONG")) {
				if (channelPos1d < 53.0) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean potential4hBullishEntrySignal(Instrument instrument,
			Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine)
			throws JFException {

		if (!pPeriod.equals(Period.FOUR_HOURS))
			return false;

		// good signals to start FIRST position are when 4h candle triggers
		// occur on extremes of 4h channel in following situations:
		// BULLISH:
		// 1. 1d flat regime, on 1d channel bottom
		// 2. 1d strong uptrend, on at least 1d channel half (4h candle pivot
		// must be below 50% 1d channel)
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}
		if (mailValuesMap.get("bullishCandleTriggerChannelPos4h") == null)
			return false;

		String[] parsedCandles = tradeTrigger
				.parseCandleTriggers(mailStringsMap.get("CandleTrigger4h"));
		String bullishCandles = parsedCandles[0];

		if (bullishCandles == null)
			return false;

		String[] parsedCandles1d = tradeTrigger
				.parseCandleTriggers(mailStringsMap.get("CandleTrigger1d"));
		String bullishCandles1d = parsedCandles1d[0];

		double candleTriggerChannelPos = mailValuesMap.get(
				"bullishCandleTriggerChannelPos4h").getDoubleValue(), candleTriggerChannelPos1d = mailValuesMap
				.get("bullishCandleTriggerChannelPos1d").getDoubleValue(), pivotLevel4h = mailValuesMap
				.get("bullishPivotLevel4h").getDoubleValue();
		Double trendStrength1d = (Double) mailValuesMap.get("MAsDistance1d")
				.getValue();
		// flat 1d regime
		long prev1dBarTime = history.getPreviousBarStart(
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		double channelPos1d = tradeTrigger.priceChannelPos(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prev1dBarTime,
				pivotLevel4h, 0)[0], keltnerPos1d = tradeTrigger
				.priceKeltnerChannelPos(instrument,
						Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
						prev1dBarTime, pivotLevel4h, 0);
		// OK to enter position #1 at 4h channel half in case of 1d bullish
		// triggers:
		// 1: 1d flat and bullish candle trigger on channel bottom
		// 2: 1d uptrend and bullish candle trigger on channel half
		if (bullishCandles1d != null
				&& ((trendStrength1d.doubleValue() < -0.7 && candleTriggerChannelPos1d < 3.0)
						|| (trendStrength1d.doubleValue() >= -0.7
								&& (mailStringsMap.get("TrendId1d").equals(
										"UP_STRONG") || mailStringsMap.get(
										"TrendId1d").equals("UP_MILD")) && candleTriggerChannelPos1d < 50.0) || (trendStrength1d
						.doubleValue() < -0.7
						&& mailStringsMap.get("TrendId1d").equals("UP_STRONG")
						&& trendDetector.isMA200Lowest(instrument, pPeriod,
								OfferSide.BID, AppliedPrice.CLOSE,
								prev1dBarTime) && candleTriggerChannelPos1d < 50.0))) {
			if (candleTriggerChannelPos < 50.0)
				return true;
		}

		// in all other cases 4h candle trigger pivot must be on 4 channel
		// bottom
		if (trendStrength1d.doubleValue() < -0.7) {
			if (candleTriggerChannelPos < 3.0
					|| channelPos1d < 3.0
					|| keltnerPos1d < 3.0
					|| (mailStringsMap.get("TrendId1d").equals("UP_STRONG")
							&& trendDetector.isMA200Lowest(instrument, pPeriod,
									OfferSide.BID, AppliedPrice.CLOSE,
									prev1dBarTime) && channelPos1d < 53.0)) {
				return true;
			}
			// TODO: probably need to check also UP_MILD with 1d MA200 the
			// lowest
		} else if (mailStringsMap.get("TrendId1d").equals("UP_STRONG")) {
			if ((candleTriggerChannelPos < 3.0 && channelPos1d < 53.0)
					|| channelPos1d < 3.0) {
				return true;
			}
		}
		return false;
	}

	public boolean bearishEntryNoGoZone(Instrument instrument, Period pPeriod,
			IBar bidBar, List<FlexLogEntry> logLine) throws JFException {
		if (!pPeriod.equals(Period.FOUR_HOURS))
			return false;

		// 1. bullish reversal ongoing: clear downtrend but all 3 momentums
		// raising, both MACDs below zero
		// 2. bullish breakout ongoing: strong 4h uptrend, 1d weak but
		// ACCELLERATING uptrend (from flat)
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}

		String[] parsedCandles = tradeTrigger
				.parseCandleTriggers(mailStringsMap.get("CandleTrigger1d"));
		String bullishCandles = parsedCandles[0];
		IBar last1dBar = history.getBar(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, 1);
		double trendStrength1d = mailValuesMap.get("MAsDistance1d")
				.getDoubleValue(), stochFast1d = mailValuesMap.get(
				"StochFast1d").getDoubleValue(), stochSlow1d = mailValuesMap
				.get("StochSlow1d").getDoubleValue();
		if (mailStringsMap.get("TrendId1d").equals("DOWN_STRONG")
				&& trendStrength1d > -0.2
				&& mailStringsMap.get("MACDState1d").equals(
						"RAISING_BOTH_BELOW_0")
				&& (mailStringsMap.get("MACDHState1d").startsWith("RAISING") || mailStringsMap
						.get("MACDHState1d").startsWith("TICKED_UP"))
				&& (mailStringsMap.get("StochState1d").equals(
						"RAISING_IN_MIDDLE")
						|| mailStringsMap.get("StochState1d").equals(
								"OVERSOLD_SLOW")
						|| mailStringsMap.get("StochState1d").equals(
								"OVERBOUGHT_FAST") || (mailStringsMap.get(
						"StochState1d").equals("OVERBOUGHT_BOTH") && stochFast1d > stochSlow1d))
				&& (bullishCandles != null || (mailValuesMap.get("barStat1d")
						.getDoubleValue() > -0.2
						&& tradeTrigger.barsBodyPerc(last1dBar) > 60.0 && last1dBar
						.getClose() > last1dBar.getOpen())))
			return true;

		double trendStrength4h = mailValuesMap.get("MAsDistance4h")
				.getDoubleValue();

		if (mailStringsMap.get("TrendId4h").equals("UP_STRONG")
				&& trendStrength4h > -0.2
				&& mailStringsMap.get("IsMA200Lowest1d").equals("yes")
				&& (mailStringsMap.get("TrendId1d").equals("UP_STRONG") || mailStringsMap
						.get("TrendId1d").equals("FRESH_UP")))
			return true;

		return false;
	}

	public boolean bullishEntryNoGoZone(Instrument instrument, Period pPeriod,
			IBar bidBar, List<FlexLogEntry> logLine) throws JFException {
		if (!pPeriod.equals(Period.FOUR_HOURS))
			return false;

		// 1. bearish reversal ongoing: clear uptrend but all 3 momentums
		// falling, both MACDs above zero
		// 2. bearish breakout ongoing: strong 4h downtrend, 1d weak but
		// ACCELLERATING downtrend (from flat)
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}

		double trendStrength1d = mailValuesMap.get("MAsDistance1d")
				.getDoubleValue(), stochFast1d = mailValuesMap.get(
				"StochFast1d").getDoubleValue(), stochSlow1d = mailValuesMap
				.get("StochSlow1d").getDoubleValue();
		String[] parsedCandles = tradeTrigger
				.parseCandleTriggers(mailStringsMap.get("CandleTrigger1d"));
		String bearishCandles = parsedCandles[1];
		// General 1d candles try also Sunday as separate date. Make sure
		// DAILY_SUNDAY_IN_MONDAY
		IBar last1dBar = history.getBar(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, 1);
		if (mailStringsMap.get("TrendId1d").equals("UP_STRONG")
				&& trendStrength1d > -0.2
				&& mailStringsMap.get("MACDState1d").equals(
						"FALLING_BOTH_ABOVE_0")
				&& (mailStringsMap.get("MACDHState1d").startsWith("FALLING") || mailStringsMap
						.get("MACDHState1d").startsWith("TICKED_DOWN"))
				&& (mailStringsMap.get("StochState1d").equals(
						"FALLING_IN_MIDDLE")
						|| mailStringsMap.get("StochState1d").equals(
								"OVERBOUGHT_SLOW")
						|| mailStringsMap.get("StochState1d").equals(
								"OVERSOLD_FAST") || (mailStringsMap.get(
						"StochState1d").equals("OVERSOLD_BOTH") && stochFast1d < stochSlow1d))
				&& (bearishCandles != null || (mailValuesMap.get("barStat1d")
						.getDoubleValue() > -0.2
						&& tradeTrigger.barsBodyPerc(last1dBar) > 60.0 && last1dBar
						.getClose() < last1dBar.getOpen())))
			return true;

		double trendStrength4h = mailValuesMap.get("MAsDistance4h")
				.getDoubleValue();
		// bearish reversal momentum must be confirmed with a bearish candle(s)
		if (mailStringsMap.get("TrendId4h").equals("DOWN_STRONG")
				&& trendStrength4h > -0.2
				&& mailStringsMap.get("IsMA200Highest1d").equals("yes")
				&& (mailStringsMap.get("TrendId1d").equals("DOWN_STRONG") || mailStringsMap
						.get("TrendId1d").equals("FRESH_DOWN")))
			return true;

		return false;
	}

	public boolean strong4hBullishNextEntrySignal(Instrument instrument,
			Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine)
			throws JFException {

		if (!pPeriod.equals(Period.FOUR_HOURS))
			return false;

		// assumption is that good signal to start FIRST position already
		// happened. Therefore any candle trigger
		// below 4h channel half is OK as aggressive next entry !
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}
		if (mailValuesMap.get("bullishCandleTriggerChannelPos4h") == null)
			return false;

		String[] parsedCandles = tradeTrigger
				.parseCandleTriggers(mailStringsMap.get("CandleTrigger4h"));
		String bullishCandles = parsedCandles[0];

		double candleTriggerChannelPos = mailValuesMap.get(
				"bullishCandleTriggerChannelPos4h").getDoubleValue();
		return bullishCandles != null && candleTriggerChannelPos < 52.0;
	}

	public boolean bullishExitSignalFirst(Instrument instrument,
			Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine)
			throws JFException {

		if (!pPeriod.equals(Period.FOUR_HOURS))
			return false;

		// exit signal is 1d bearish candles trigger piercing 1d channel top,
		// including simple 1-bar with 60% bearish body
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}

		if (mailValuesMap.get("bearishCandleTriggerChannelPos1d") == null)
			return false;

		String[] parsedCandles = tradeTrigger
				.parseCandleTriggers(mailStringsMap.get("CandleTrigger1d"));
		String bearishCandles = parsedCandles[1];

		double candleTriggerChannelPos = mailValuesMap.get(
				"bearishCandleTriggerChannelPos1d").getDoubleValue();
		IBar last1dBar = history.getBar(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, 1);
		if (bearishCandles != null && bearishCandles.equals("BEARISH_3_BARS")
				&& last1dBar.getClose() > last1dBar.getOpen())
			return false;

		long prev1dBarTime = history.getPreviousBarStart(
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		double last1dBarLowChannelPos = channelPosition.priceChannelPos(
				instrument, pPeriod, OfferSide.BID, prev1dBarTime,
				last1dBar.getHigh());
		return (bearishCandles != null && candleTriggerChannelPos > 97.0)
				|| (last1dBarLowChannelPos > 90.0
						&& mailValuesMap.get("barStat1d").getDoubleValue() > -0.2
						&& tradeTrigger.barsBodyPerc(last1dBar) > 60.0 && last1dBar
						.getClose() < last1dBar.getOpen());
	}

	public boolean bullishExitSignalSecond(Instrument instrument,
			Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine)
			throws JFException {

		if (!pPeriod.equals(Period.FOUR_HOURS))
			return false;

		// exit signal is 1d bearish candles trigger piercing 1d channel top,
		// including simple 1-bar with 60% bearish body
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}

		String[] parsedCandles = tradeTrigger
				.parseCandleTriggers(mailStringsMap.get("CandleTrigger4h"));
		String bearishCandles = parsedCandles[1];
		if (bearishCandles == null)
			return false;

		long prev1dBarTime = history.getPreviousBarStart(
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		double pivotLevel4h = mailValuesMap.get("bearishPivotLevel4h")
				.getDoubleValue(), channelPos1d = tradeTrigger.priceChannelPos(
				instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				prev1dBarTime, pivotLevel4h, 0)[0], keltnerPos1d = tradeTrigger
				.priceKeltnerChannelPos(instrument,
						Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
						prev1dBarTime, pivotLevel4h, 0), candleTriggerChannelPos4h = mailValuesMap
				.get("bearishCandleTriggerChannelPos4h").getDoubleValue(), candleTriggerKChannelPos4h = mailValuesMap
				.get("bearishCandleTriggerKChannelPos4h").getDoubleValue(), rsi1d = mailValuesMap
				.get("RSI1d").getDoubleValue(), rsi4h = mailValuesMap.get(
				"RSI4h").getDoubleValue();

		return (bearishCandles != null
				&& (candleTriggerChannelPos4h > 95.0 || candleTriggerKChannelPos4h > 95.0)
				&& (channelPos1d > 95.0 || keltnerPos1d > 95.0)
				&& rsi1d >= 68.0 && rsi4h >= 68.0);
	}

	public boolean bearishExitSignalSecond(Instrument instrument,
			Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine)
			throws JFException {

		if (!pPeriod.equals(Period.FOUR_HOURS))
			return false;

		// exit signal is 1d bearish candles trigger piercing 1d channel top,
		// including simple 1-bar with 60% bearish body
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}

		String[] parsedCandles = tradeTrigger
				.parseCandleTriggers(mailStringsMap.get("CandleTrigger4h"));
		String bullishCandles = parsedCandles[0];
		if (bullishCandles == null)
			return false;

		long prev1dBarTime = history.getPreviousBarStart(
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		double pivotLevel4h = mailValuesMap.get("bullishPivotLevel4h")
				.getDoubleValue(), channelPos1d = tradeTrigger.priceChannelPos(
				instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				prev1dBarTime, pivotLevel4h, 0)[0], keltnerPos1d = tradeTrigger
				.priceKeltnerChannelPos(instrument,
						Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
						prev1dBarTime, pivotLevel4h, 0), candleTriggerChannelPos4h = mailValuesMap
				.get("bullishCandleTriggerChannelPos4h").getDoubleValue(), candleTriggerKChannelPos4h = mailValuesMap
				.get("bullishCandleTriggerKChannelPos4h").getDoubleValue(), rsi1d = mailValuesMap
				.get("RSI1d").getDoubleValue(), rsi4h = mailValuesMap.get(
				"RSI4h").getDoubleValue();

		return (bullishCandles != null
				&& (candleTriggerChannelPos4h < 5.0 || candleTriggerKChannelPos4h < 5.0)
				&& (channelPos1d < 5.0 || keltnerPos1d < 5.0) && rsi1d <= 32.0 && rsi4h <= 32.0);
	}

	public boolean strong4hBearishFirstEntrySignal(Instrument instrument,
			Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine)
			throws JFException {

		if (!pPeriod.equals(Period.FOUR_HOURS))
			return false;

		// BEARISH:
		// 3. 1d flat regime, on 1d channel top
		// 4. 1d strong downtrend, on at least 1d channel half (4h candle pivot
		// must be above 50% 1d channel)
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}
		if (mailValuesMap.get("bearishCandleTriggerChannelPos4h") == null)
			return false;

		// now first check NO-GO ZONES !
		if (bearishEntryNoGoZone(instrument, pPeriod, bidBar, logLine))
			return false;

		String[] parsedCandles = tradeTrigger
				.parseCandleTriggers(mailStringsMap.get("CandleTrigger4h"));
		String bearishCandles = parsedCandles[1];
		if (bearishCandles == null)
			return false;

		String[] parsedCandles1d = tradeTrigger
				.parseCandleTriggers(mailStringsMap.get("CandleTrigger1d"));
		String bearishCandles1d = parsedCandles1d[1];
		double pivotLevel4h = mailValuesMap.get("bearishPivotLevel4h")
				.getDoubleValue(), candleTriggerChannelPos1d = mailValuesMap
				.get("bearishCandleTriggerChannelPos1d").getDoubleValue();
		Double trendStrength1d = (Double) mailValuesMap.get("MAsDistance1d")
				.getValue();
		// flat 1d regime
		long prev1dBarTime = history.getPreviousBarStart(
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		double channelPos1d = tradeTrigger.priceChannelPos(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prev1dBarTime,
				pivotLevel4h, 0)[0], keltnerPos1d = tradeTrigger
				.priceKeltnerChannelPos(instrument,
						Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
						prev1dBarTime, pivotLevel4h, 0), candleTriggerChannelPos = mailValuesMap
				.get("bearishCandleTriggerChannelPos4h").getDoubleValue();

		if (bearishCandles1d != null
				&& ((trendStrength1d.doubleValue() < -0.7 && candleTriggerChannelPos1d > 97.0)
						|| (trendStrength1d.doubleValue() >= -0.7
								&& (mailStringsMap.get("TrendId1d").equals(
										"DOWN_STRONG") || mailStringsMap.get(
										"TrendId1d").equals("DOWN_MILD")) && candleTriggerChannelPos1d > 50.0) || (trendStrength1d
						.doubleValue() < -0.7
						&& mailStringsMap.get("TrendId1d")
								.equals("DOWN_STRONG")
						&& trendDetector.isMA200Highest(instrument, pPeriod,
								OfferSide.BID, AppliedPrice.CLOSE,
								prev1dBarTime) && candleTriggerChannelPos1d > 50.0))) {
			if (candleTriggerChannelPos > 50.0)
				return true;
		}

		if (candleTriggerChannelPos > 97.0) {
			if (trendStrength1d.doubleValue() < -0.7) {
				if (channelPos1d > 97.0
						|| keltnerPos1d > 97.0
						|| (mailStringsMap.get("TrendId1d").equals(
								"DOWN_STRONG")
								&& trendDetector.isMA200Highest(instrument,
										pPeriod, OfferSide.BID,
										AppliedPrice.CLOSE, prev1dBarTime) && channelPos1d > 47.0)) {
					return true;
				}
			} else if (mailStringsMap.get("TrendId1d").equals("DOWN_STRONG")) {
				if (channelPos1d > 47.0) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean potential4hBearishEntrySignal(Instrument instrument,
			Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine)
			throws JFException {

		if (!pPeriod.equals(Period.FOUR_HOURS))
			return false;

		// BEARISH:
		// 3. 1d flat regime, on 1d channel top
		// 4. 1d strong downtrend, on at least 1d channel half (4h candle pivot
		// must be above 50% 1d channel)
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}
		if (mailValuesMap.get("bearishCandleTriggerChannelPos4h") == null)
			return false;

		String[] parsedCandles = tradeTrigger
				.parseCandleTriggers(mailStringsMap.get("CandleTrigger4h"));
		String bearishCandles = parsedCandles[1];
		if (bearishCandles == null)
			return false;

		String[] parsedCandles1d = tradeTrigger
				.parseCandleTriggers(mailStringsMap.get("CandleTrigger1d"));
		String bearishCandles1d = parsedCandles1d[1];
		double pivotLevel4h = mailValuesMap.get("bearishPivotLevel4h")
				.getDoubleValue(), candleTriggerChannelPos1d = mailValuesMap
				.get("bearishCandleTriggerChannelPos1d").getDoubleValue();
		Double trendStrength1d = (Double) mailValuesMap.get("MAsDistance1d")
				.getValue();
		// flat 1d regime
		long prev1dBarTime = history.getPreviousBarStart(
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		double channelPos1d = tradeTrigger.priceChannelPos(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prev1dBarTime,
				pivotLevel4h, 0)[0], keltnerPos1d = tradeTrigger
				.priceKeltnerChannelPos(instrument,
						Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
						prev1dBarTime, pivotLevel4h, 0), candleTriggerChannelPos = mailValuesMap
				.get("bearishCandleTriggerChannelPos4h").getDoubleValue();

		if (bearishCandles1d != null
				&& ((trendStrength1d.doubleValue() < -0.7 && candleTriggerChannelPos1d > 97.0)
						|| (trendStrength1d.doubleValue() >= -0.7
								&& (mailStringsMap.get("TrendId1d").equals(
										"DOWN_STRONG") || mailStringsMap.get(
										"TrendId1d").equals("DOWN_MILD")) && candleTriggerChannelPos1d > 50.0) || (trendStrength1d
						.doubleValue() < -0.7
						&& mailStringsMap.get("TrendId1d")
								.equals("DOWN_STRONG")
						&& trendDetector.isMA200Highest(instrument, pPeriod,
								OfferSide.BID, AppliedPrice.CLOSE,
								prev1dBarTime) && candleTriggerChannelPos1d > 50.0))) {
			if (candleTriggerChannelPos > 50.0)
				return true;
		}

		if (trendStrength1d.doubleValue() < -0.7) {
			if (candleTriggerChannelPos > 97.0
					|| channelPos1d > 97.0
					|| keltnerPos1d > 97.0
					|| (mailStringsMap.get("TrendId1d").equals("DOWN_STRONG")
							&& trendDetector.isMA200Highest(instrument,
									pPeriod, OfferSide.BID, AppliedPrice.CLOSE,
									prev1dBarTime) && channelPos1d > 47.0)) {
				return true;
			}
		} else if (mailStringsMap.get("TrendId1d").equals("DOWN_STRONG")) {
			if ((candleTriggerChannelPos > 97.0 && channelPos1d > 47.0)
					|| channelPos1d > 97.0) {
				return true;
			}
		}
		return false;
	}

	public boolean strong4hBearishNextEntrySignal(Instrument instrument,
			Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine)
			throws JFException {

		if (!pPeriod.equals(Period.FOUR_HOURS))
			return false;

		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}
		if (mailValuesMap.get("bearishCandleTriggerChannelPos4h") == null)
			return false;

		String[] parsedCandles = tradeTrigger
				.parseCandleTriggers(mailStringsMap.get("CandleTrigger4h"));
		String bearishCandles = parsedCandles[1];

		double candleTriggerChannelPos = mailValuesMap.get(
				"bearishCandleTriggerChannelPos4h").getDoubleValue();
		return bearishCandles != null && candleTriggerChannelPos > 48.0;
	}

	public boolean bearishExitSignalFirst(Instrument instrument, Period period,
			IBar bidBar, List<FlexLogEntry> logLine) throws JFException {
		if (!period.equals(Period.FOUR_HOURS))
			return false;

		// exit signal is 1d bearish candles trigger piercing 1d channel top,
		// including simple 1-bar with 60% bearish body
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}

		if (mailValuesMap.get("bullishCandleTriggerChannelPos1d") == null)
			return false;

		String[] parsedCandles = tradeTrigger
				.parseCandleTriggers(mailStringsMap.get("CandleTrigger1d"));
		String bullishCandles = parsedCandles[0];

		double candleTriggerChannelPos = mailValuesMap.get(
				"bullishCandleTriggerChannelPos1d").getDoubleValue();
		IBar last1dBar = history.getBar(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, 1);
		if (bullishCandles != null && bullishCandles.equals("BULLISH_3_BARS")
				&& last1dBar.getClose() < last1dBar.getOpen())
			return false;

		long prev1dBarTime = history.getPreviousBarStart(
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		double last1dBarLowChannelPos = channelPosition.priceChannelPos(
				instrument, period, OfferSide.BID, prev1dBarTime,
				last1dBar.getLow());
		return (bullishCandles != null && candleTriggerChannelPos < 3.0)
				|| (last1dBarLowChannelPos < 10.0
						&& mailValuesMap.get("barStat1d").getDoubleValue() > -0.2
						&& tradeTrigger.barsBodyPerc(last1dBar) > 60.0 && last1dBar
						.getClose() > last1dBar.getOpen());
	}

	public boolean any4hBearishTrigger(Instrument instrument, Period period,
			IBar bidBar, List<FlexLogEntry> logLine) {
		if (!period.equals(Period.FOUR_HOURS))
			return false;

		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}
		if (mailValuesMap.get("bearishCandleTriggerChannelPos4h") == null)
			return false;

		String[] parsedCandles = tradeTrigger
				.parseCandleTriggers(mailStringsMap.get("CandleTrigger4h"));
		String bearishCandles = parsedCandles[1];

		return bearishCandles != null;
	}

	public boolean any4hBullishTrigger(Instrument instrument, Period period,
			IBar bidBar, List<FlexLogEntry> logLine) {
		if (!period.equals(Period.FOUR_HOURS))
			return false;

		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}
		if (mailValuesMap.get("bullishCandleTriggerChannelPos4h") == null)
			return false;

		String[] parsedCandles = tradeTrigger
				.parseCandleTriggers(mailStringsMap.get("CandleTrigger4h"));
		String bullishCandles = parsedCandles[0];

		return bullishCandles != null;
	}

	/**
	 * Collects stats for one single timeframe as needed for OneTouchTrading
	 * 
	 * @param instrument
	 * @param period
	 * @param bidBar
	 * @param logLine
	 *            - results are put into this list
	 * @throws JFException
	 */
	public void collectOneTimeFrameStats(Instrument instrument, Period period,
			IBar bidBar, List<FlexLogEntry> logLine) throws JFException {
		double ATR = vola.getATR(instrument, period, OfferSide.BID,
				bidBar.getTime(), 14)
				* Math.pow(10, instrument.getPipScale()), bBandsSqueeze = vola
				.getBBandsSqueeze(instrument, period, OfferSide.BID,
						bidBar.getTime(), 20);

		Trend.TREND_STATE trendID = trendDetector.getTrendState(instrument,
				period, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime());

		logLine.add(new FlexLogEntry("Pair", instrument.toString()));
		logLine.add(new FlexLogEntry("Bars period", period.toString()));
		logLine.add(new FlexLogEntry("BarStartTime", FXUtils
				.getFormatedTimeCET(bidBar.getTime())));

		logLine.add(new FlexLogEntry("TrendId"
				+ FXUtils.timeFrameNamesMap.get(period.toString()), trendID
				.toString()));
		// trend regime
		addFlatStatsOneTimeFrame(instrument, bidBar, period, logLine);
		// channel position
		addChannelPosStatsOneTimeFrame(instrument, period, bidBar, logLine);
		// candles
		addCandlesOneTimeFrame(instrument, bidBar, period, logLine);

		logLine.add(new FlexLogEntry("ATR"
				+ FXUtils.timeFrameNamesMap.get(period.toString()), new Double(
				ATR), FXUtils.df1));
		logLine.add(new FlexLogEntry("ATR"
				+ FXUtils.timeFrameNamesMap.get(period.toString()) + " 20%",
				new Double(ATR * 1.2), FXUtils.df1));
		logLine.add(new FlexLogEntry("volatility"
				+ FXUtils.timeFrameNamesMap.get(period.toString()), new Double(
				bBandsSqueeze), FXUtils.df1));

		Trend.ICHI_CLOUD_CROSS ichi = trendDetector.isIchiCloudCross(history,
				instrument, period, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime());
		logLine.add(new FlexLogEntry("IchiCloudCross"
				+ FXUtils.timeFrameNamesMap.get(period.toString()), ichi
				.toString()));

		double ichiCloudDist = trendDetector.ichiCloudDistCalc(history,
				instrument, period, OfferSide.BID, bidBar, bidBar.getTime(),
				FXUtils.timeFrameStatsMap.get(period.toString()));
		logLine.add(new FlexLogEntry("IchiCloudDist"
				+ FXUtils.timeFrameNamesMap.get(period.toString()), new Double(
				ichiCloudDist), FXUtils.df2));

		Trend.IchiDesc ichiDesc = trendDetector.getIchi(history, instrument,
				period, OfferSide.BID, bidBar.getTime());
		logLine.add(new FlexLogEntry("IchiCloudWidth"
				+ FXUtils.timeFrameNamesMap.get(period.toString()), new Double(
				ichiDesc.widthPips), FXUtils.df1));
		logLine.add(new FlexLogEntry("IchiCloudWidthToATR"
				+ FXUtils.timeFrameNamesMap.get(period.toString()), new Double(
				ichiDesc.widthToATR), FXUtils.df1));
		logLine.add(new FlexLogEntry("IchiCloudBullish"
				+ FXUtils.timeFrameNamesMap.get(period.toString()),
				ichiDesc.isBullishCloud ? "yes" : "no"));
		logLine.add(new FlexLogEntry("IchiCloudBullishLines"
				+ FXUtils.timeFrameNamesMap.get(period.toString()),
				ichiDesc.isBullishTenkanLine ? "yes" : "no"));
		logLine.add(new FlexLogEntry("IchiCloudBottomDirection"
				+ FXUtils.timeFrameNamesMap.get(period.toString()),
				ichiDesc.bottomBorderDirection));
		logLine.add(new FlexLogEntry("IchiCloudTopDirection"
				+ FXUtils.timeFrameNamesMap.get(period.toString()),
				ichiDesc.topBorderDirection));
	}

	public void addFlatStats(Instrument instrument, IBar bidBar,
			Period basicTimeFrame, List<FlexLogEntry> logLine)
			throws JFException {
		double trendStrength30min = trendDetector.getMAsMaxDiffStDevPos(
				instrument, basicTimeFrame, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime(), FXUtils.MONTH_WORTH_OF_30min_BARS);
		logLine.add(new FlexLogEntry("MAsDistance30min", new Double(
				trendStrength30min), FXUtils.df1));
		trendStrength30min = trendDetector.getAbsMAsDifference(instrument,
				basicTimeFrame, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime());
		logLine.add(new FlexLogEntry("IsMA200Highest30min", trendDetector
				.isMA200Highest(instrument, basicTimeFrame, OfferSide.BID,
						AppliedPrice.CLOSE, bidBar.getTime()) ? "yes" : "no"));
		logLine.add(new FlexLogEntry("IsMA200Lowest30min", trendDetector
				.isMA200Lowest(instrument, basicTimeFrame, OfferSide.BID,
						AppliedPrice.CLOSE, bidBar.getTime()) ? "yes" : "no"));

		double trendStrength4h = trendDetector.getMAsMaxDiffStDevPos(
				instrument, Period.FOUR_HOURS, OfferSide.BID,
				AppliedPrice.CLOSE, bidBar.getTime(),
				FXUtils.QUARTER_WORTH_OF_4h_BARS);
		logLine.add(new FlexLogEntry("MAsDistance4h", new Double(
				trendStrength4h), FXUtils.df1));
		trendStrength4h = trendDetector.getAbsMAsDifference(instrument,
				Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime());
		logLine.add(new FlexLogEntry(
				"MAsDistanceOverlap",
				trendStrength30min <= -0.7 && trendStrength4h <= -0.7 ? "YES (FLAT)"
						: "no"));
		logLine.add(new FlexLogEntry("IsMA200Highest4h", trendDetector
				.isMA200Highest(instrument, Period.FOUR_HOURS, OfferSide.BID,
						AppliedPrice.CLOSE, bidBar.getTime()) ? "yes" : "no"));
		logLine.add(new FlexLogEntry("IsMA200Lowest4h", trendDetector
				.isMA200Lowest(instrument, Period.FOUR_HOURS, OfferSide.BID,
						AppliedPrice.CLOSE, bidBar.getTime()) ? "yes" : "no"));

		long prevDayTime = history.getPreviousBarStart(
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		double trendStrength1d = trendDetector.getMAsMaxDiffStDevPos(
				instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				AppliedPrice.CLOSE, prevDayTime, FXUtils.YEAR_WORTH_OF_1d_BARS);
		logLine.add(new FlexLogEntry("MAsDistance1d", new Double(
				trendStrength1d), FXUtils.df1));
		trendStrength1d = trendDetector.getAbsMAsDifference(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				AppliedPrice.CLOSE, prevDayTime);
		logLine.add(new FlexLogEntry("IsMA200Highest1d", trendDetector
				.isMA200Highest(instrument, Period.DAILY_SUNDAY_IN_MONDAY,
						OfferSide.BID, AppliedPrice.CLOSE, prevDayTime) ? "yes"
				: "no"));
		logLine.add(new FlexLogEntry("IsMA200Lowest1d", trendDetector
				.isMA200Lowest(instrument, Period.DAILY_SUNDAY_IN_MONDAY,
						OfferSide.BID, AppliedPrice.CLOSE, prevDayTime) ? "yes"
				: "no"));

	}

	// TODO: this works fully correctly only when called for 30min bar !!! Check
	// what happens for 4h bar and generalize !!!!
	public void addChannelPosStats(Instrument instrument,
			Period basicTimeFrame, IBar bidBar, List<FlexLogEntry> logLine)
			throws JFException {
		double lowChannelPos30min = 0, lowChannelPosPrevBar30min = 0;
		lowChannelPos30min = channelPosition.priceChannelPos(instrument,
				basicTimeFrame, OfferSide.BID, bidBar.getTime(),
				bidBar.getLow());
		logLine.add(new FlexLogEntry("barLowChannelPos30min", new Double(
				lowChannelPos30min), FXUtils.df1));
		// lowChannelPosPrevBar30min =
		// channelPosition.bullishTriggerChannelStats(instrument,
		// basicTimeFrame, OfferSide.BID, bidBar.getTime())[1];
		// logLine.add(new FlexLogEntry("bullishTriggerLowChannelPos30min", new
		// Double(lowChannelPosPrevBar30min), FXUtils.df1));

		double highChannelPos30min = 0, highChannelPosPrevBar30min = 0;
		highChannelPos30min = channelPosition.priceChannelPos(instrument,
				basicTimeFrame, OfferSide.BID, bidBar.getTime(),
				bidBar.getHigh());
		logLine.add(new FlexLogEntry("barHighChannelPos30min", new Double(
				highChannelPos30min), FXUtils.df1));
		// highChannelPosPrevBar30min =
		// channelPosition.bearishTriggerChannelStats(instrument,
		// basicTimeFrame, OfferSide.BID, bidBar.getTime())[1];
		// logLine.add(new FlexLogEntry("bearishTriggerHighChannelPos30min", new
		// Double(highChannelPosPrevBar30min), FXUtils.df1));

		int barsAbove = channelPosition.consequitiveBarsAbove(instrument,
				basicTimeFrame, OfferSide.BID, bidBar.getTime(), 10);
		logLine.add(new FlexLogEntry("barsAboveChannel30min", new Integer(
				barsAbove), FXUtils.if3));
		int barsBelow = channelPosition.consequitiveBarsBelow(instrument,
				basicTimeFrame, OfferSide.BID, bidBar.getTime(), 10);
		logLine.add(new FlexLogEntry("barsBelowChannel30min", new Integer(
				barsBelow), FXUtils.if3));

		double lowChannelPos4h = 0, highChannelPos4h = 0;
		lowChannelPos4h = channelPosition.priceChannelPos(instrument,
				Period.FOUR_HOURS, OfferSide.BID, bidBar.getTime(),
				bidBar.getLow());
		logLine.add(new FlexLogEntry("barLowChannelPos4h", new Double(
				lowChannelPos4h), FXUtils.df1));
		highChannelPos4h = channelPosition.priceChannelPos(instrument,
				Period.FOUR_HOURS, OfferSide.BID, bidBar.getTime(),
				bidBar.getHigh());
		logLine.add(new FlexLogEntry("barHighChannelPos4h", new Double(
				highChannelPos4h), FXUtils.df1));

		logLine.add(new FlexLogEntry("barLowChannelPosOverlap",
				lowChannelPos30min <= 30 && lowChannelPos4h <= 30 ? "YES ("
						+ FXUtils.df1.format(lowChannelPos30min) + "%/"
						+ FXUtils.df1.format(lowChannelPos4h) + "%)" : "no"));
		logLine.add(new FlexLogEntry("barHighChannelPosOverlap",
				highChannelPos30min >= 70 && highChannelPos4h >= 70 ? "YES ("
						+ FXUtils.df1.format(highChannelPos30min) + "%/"
						+ FXUtils.df1.format(highChannelPos4h) + "%)" : "no"));

		long prev4hTime = history.getPreviousBarStart(Period.FOUR_HOURS,
				bidBar.getTime());
		barsAbove = channelPosition.consequitiveBarsAbove(instrument,
				Period.FOUR_HOURS, OfferSide.BID, prev4hTime, 10);
		logLine.add(new FlexLogEntry("barsAboveChannel4h", new Integer(
				barsAbove), FXUtils.if3));
		barsBelow = channelPosition.consequitiveBarsBelow(instrument,
				Period.FOUR_HOURS, OfferSide.BID, prev4hTime, 10);
		logLine.add(new FlexLogEntry("barsBelowChannel4h", new Integer(
				barsBelow), FXUtils.if3));
		double[] bBandsRaw4h = channelPosition.getRawBBandsData(instrument,
				Period.FOUR_HOURS, OfferSide.BID, prev4hTime);
		logLine.add(new FlexLogEntry("bBandsWidth4h", new Double(
				(bBandsRaw4h[0] - bBandsRaw4h[2])
						* Math.pow(10, instrument.getPipScale())), FXUtils.df1));
		logLine.add(new FlexLogEntry("bBandsTop4h", new Double(bBandsRaw4h[0]),
				FXUtils.df5));
		logLine.add(new FlexLogEntry("bBandsBottom4h", new Double(
				bBandsRaw4h[2]), FXUtils.df5));

		long prevDayTime = history.getPreviousBarStart(
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		double lowChannelPos1d = 0, highChannelPos1d = 0;
		lowChannelPos1d = channelPosition.priceChannelPos(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prevDayTime,
				bidBar.getLow());
		logLine.add(new FlexLogEntry("barLowChannelPos1d", new Double(
				lowChannelPos1d), FXUtils.df1));
		highChannelPos1d = channelPosition.priceChannelPos(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prevDayTime,
				bidBar.getHigh());
		logLine.add(new FlexLogEntry("barHighChannelPos1d", new Double(
				highChannelPos1d), FXUtils.df1));

		barsAbove = channelPosition.consequitiveBarsAbove(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prevDayTime, 10);
		logLine.add(new FlexLogEntry("barsAboveChannel1d", new Integer(
				barsAbove), FXUtils.if3));
		barsBelow = channelPosition.consequitiveBarsBelow(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prevDayTime, 10);
		logLine.add(new FlexLogEntry("barsBelowChannel1d", new Integer(
				barsBelow), FXUtils.if3));

		double[] bBandsRaw1d = channelPosition.getRawBBandsData(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prevDayTime);
		logLine.add(new FlexLogEntry("bBandsWidth1d", new Double(
				(bBandsRaw1d[0] - bBandsRaw1d[2])
						* Math.pow(10, instrument.getPipScale())), FXUtils.df1));
		logLine.add(new FlexLogEntry("bBandsTop1d", new Double(bBandsRaw1d[0]),
				FXUtils.df5));
		logLine.add(new FlexLogEntry("bBandsBottom1d", new Double(
				bBandsRaw1d[2]), FXUtils.df5));
		// TODO: check how to handle...
		// logLine.add(new FlexLogEntry("bBandsTopBarHighDiff1d", new
		// Double((bBandsRaw1d[0] - bidBar.getHigh()) * Math.pow(10,
		// instrument.getPipScale())), FXUtils.df1));
		// logLine.add(new FlexLogEntry("bBandsBottomBarLow1d", new
		// Double((bidBar.getLow() - bBandsRaw1d[2]) * Math.pow(10,
		// instrument.getPipScale())), FXUtils.df1));

		double[] bBandsRaw = channelPosition.getRawBBandsData(instrument,
				basicTimeFrame, OfferSide.BID, bidBar.getTime());

		logLine.add(new FlexLogEntry("bBandsWidth30min", new Double(
				(bBandsRaw[0] - bBandsRaw[2])
						* Math.pow(10, instrument.getPipScale())), FXUtils.df1));
		logLine.add(new FlexLogEntry("bBandsTop30min",
				new Double(bBandsRaw[0]), FXUtils.df5));
		logLine.add(new FlexLogEntry("bBandsBottom30min", new Double(
				bBandsRaw[2]), FXUtils.df5));

		// TODO: check how to handle...
		// logLine.add(new FlexLogEntry("bBandsTopBarHighDiff30min", new
		// Double((bidBar.getHigh() - bBandsRaw[0]) * Math.pow(10,
		// instrument.getPipScale())), FXUtils.df1));
		// logLine.add(new FlexLogEntry("bBandsBottomBarLow30min", new
		// Double((bBandsRaw[2] - bidBar.getLow()) * Math.pow(10,
		// instrument.getPipScale())), FXUtils.df1));
	}

	public void addCandles(Instrument instrument, IBar bidBar, Period period,
			List<FlexLogEntry> logLine) throws JFException {
		logLine.add(new FlexLogEntry("barOpen30min", new Double(bidBar
				.getOpen()), FXUtils.df5));
		logLine.add(new FlexLogEntry("barClose30min", new Double(bidBar
				.getClose()), FXUtils.df5));
		logLine.add(new FlexLogEntry("barLow30min",
				new Double(bidBar.getLow()), FXUtils.df5));
		logLine.add(new FlexLogEntry("barHigh30min", new Double(bidBar
				.getHigh()), FXUtils.df5));

		if (bidBar.getHigh() != bidBar.getLow()) {
			logLine.add(new FlexLogEntry("upperHandlePerc30min", new Double(
					tradeTrigger.barsUpperHandlePerc(bidBar)), FXUtils.df1));
			int sign = bidBar.getClose() > bidBar.getOpen() ? 1 : -1;
			logLine.add(new FlexLogEntry("barBodyPerc30min", new Double(sign
					* tradeTrigger.barsBodyPerc(bidBar)), FXUtils.df1));
			logLine.add(new FlexLogEntry("lowerHandlePerc30min", new Double(
					tradeTrigger.barsLowerHandlePerc(bidBar)), FXUtils.df1));
		}

		double bar30minStat = tradeTrigger.barLengthStatPos(instrument, period,
				OfferSide.BID, bidBar, FXUtils.MONTH_WORTH_OF_30min_BARS);
		logLine.add(new FlexLogEntry("barStat30min", new Double(bar30minStat),
				FXUtils.df1));
		bar30minStat = tradeTrigger.avgBarLength(instrument, period,
				OfferSide.BID, bidBar, FXUtils.MONTH_WORTH_OF_30min_BARS);
		logLine.add(new FlexLogEntry("bar30minAvgSize",
				new Double(bar30minStat), FXUtils.df1));

		double bar30minOverlap = tradeTrigger.previousBarOverlap(instrument,
				period, OfferSide.BID, bidBar.getTime());
		logLine.add(new FlexLogEntry("prevBarOverlap30min", new Double(
				bar30minOverlap), FXUtils.df1));

		String candleTrigger30minStr = new String();
		TradeTrigger.TriggerDesc bullishTriggerDesc = null, bearishTriggerDesc = null;
		if ((bullishTriggerDesc = tradeTrigger
				.bullishReversalCandlePatternDesc(instrument, period,
						OfferSide.BID, bidBar.getTime())) != null) {
			candleTrigger30minStr += bullishTriggerDesc.type.toString();
			logLine.add(new FlexLogEntry("bullishCandleTriggerChannelPos30min",
					new Double(bullishTriggerDesc.channelPosition), FXUtils.df1));
			logLine.add(new FlexLogEntry("bullishPivotLevel30min", new Double(
					bullishTriggerDesc.pivotLevel), FXUtils.df5));
			logLine.add(new FlexLogEntry(
					"bullishCandleTriggerKChannelPos30min", new Double(
							bullishTriggerDesc.keltnerChannelPosition),
					FXUtils.df1));

			double channelPos4h = tradeTrigger.priceChannelPos(instrument,
					Period.FOUR_HOURS, OfferSide.BID, bidBar.getTime(),
					bullishTriggerDesc.pivotLevel, 0)[0], keltnerPos4h = tradeTrigger
					.priceKeltnerChannelPos(instrument, Period.FOUR_HOURS,
							OfferSide.BID, bidBar.getTime(),
							bullishTriggerDesc.pivotLevel, 0);
			logLine.add(new FlexLogEntry(
					"bullishPivotLevelHigherTFChannelPos30min", new Double(
							channelPos4h), FXUtils.df1));
			logLine.add(new FlexLogEntry(
					"bullishPivotLevelHigherTFKChannelPos30min", new Double(
							keltnerPos4h), FXUtils.df1));

			logLine.add(new FlexLogEntry("bullishTriggerCombinedUHPerc30min",
					new Double(bullishTriggerDesc.combinedUpperHandlePerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry("bullishTriggerCombinedBodyPerc30min",
					new Double(bullishTriggerDesc.combinedRealBodyPerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry("bullishTriggerCombinedLHPerc30min",
					new Double(bullishTriggerDesc.combinedLowerHandlePerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry(
					"bullishTriggerCombinedBodyDirection30min",
					new String(
							bullishTriggerDesc.combinedRealBodyDirection ? "BULLISH"
									: "BEARISH")));
			double reversalSizeStat = tradeTrigger.barLengthStatPos(instrument,
					period, OfferSide.BID, bidBar,
					bullishTriggerDesc.getCombinedHigh(),
					bullishTriggerDesc.getCombinedLow(),
					FXUtils.MONTH_WORTH_OF_30min_BARS);
			logLine.add(new FlexLogEntry("bullishTriggerCombinedSizeStat30min",
					new Double(reversalSizeStat), FXUtils.df1));
		} else {
			logLine.add(new FlexLogEntry("bullishCandleTriggerChannelPos30min",
					new Double(0), FXUtils.df1));
			logLine.add(new FlexLogEntry("bullishPivotLevel30min",
					new Double(0), FXUtils.df5));
			logLine.add(new FlexLogEntry(
					"bullishCandleTriggerKChannelPos30min", new Double(0),
					FXUtils.df1));
		}
		if ((bearishTriggerDesc = tradeTrigger
				.bearishReversalCandlePatternDesc(instrument, period,
						OfferSide.BID, bidBar.getTime())) != null) {
			if (candleTrigger30minStr.length() > 0)
				candleTrigger30minStr += " AND "
						+ bearishTriggerDesc.type.toString();
			else
				candleTrigger30minStr += bearishTriggerDesc.type.toString();
			logLine.add(new FlexLogEntry("bearishCandleTriggerChannelPos30min",
					new Double(bearishTriggerDesc.channelPosition), FXUtils.df1));
			logLine.add(new FlexLogEntry("bearishPivotLevel30min", new Double(
					bearishTriggerDesc.pivotLevel), FXUtils.df5));
			logLine.add(new FlexLogEntry(
					"bearishCandleTriggerKChannelPos30min", new Double(
							bearishTriggerDesc.keltnerChannelPosition),
					FXUtils.df1));

			double channelPos4h = tradeTrigger.priceChannelPos(instrument,
					Period.FOUR_HOURS, OfferSide.BID, bidBar.getTime(),
					bearishTriggerDesc.pivotLevel, 0)[0], keltnerPos4h = tradeTrigger
					.priceKeltnerChannelPos(instrument, Period.FOUR_HOURS,
							OfferSide.BID, bidBar.getTime(),
							bearishTriggerDesc.pivotLevel, 0);
			logLine.add(new FlexLogEntry(
					"bearishPivotLevelHigherTFChannelPos30min", new Double(
							channelPos4h), FXUtils.df1));
			logLine.add(new FlexLogEntry(
					"bearishPivotLevelHigherTFKChannelPos30min", new Double(
							keltnerPos4h), FXUtils.df1));

			logLine.add(new FlexLogEntry("bearishTriggerCombinedUHPerc30min",
					new Double(bearishTriggerDesc.combinedUpperHandlePerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry("bearishTriggerCombinedBodyPerc30min",
					new Double(bearishTriggerDesc.combinedRealBodyPerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry("bearishTriggerCombinedLHPerc30min",
					new Double(bearishTriggerDesc.combinedLowerHandlePerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry(
					"bearishTriggerCombinedBodyDirection30min",
					new String(
							bearishTriggerDesc.combinedRealBodyDirection ? "BULLISH"
									: "BEARISH")));
			double reversalSizeStat = tradeTrigger.barLengthStatPos(instrument,
					period, OfferSide.BID, bidBar,
					bearishTriggerDesc.getCombinedHigh(),
					bearishTriggerDesc.getCombinedLow(),
					FXUtils.MONTH_WORTH_OF_30min_BARS);
			logLine.add(new FlexLogEntry("bearishTriggerCombinedSizeStat30min",
					new Double(reversalSizeStat), FXUtils.df1));
		} else {
			logLine.add(new FlexLogEntry("bearishCandleTriggerChannelPos30min",
					new Double(0), FXUtils.df1));
			logLine.add(new FlexLogEntry("bearishPivotLevel30min",
					new Double(0), FXUtils.df5));
			logLine.add(new FlexLogEntry(
					"bearishCandleTriggerKChannelPos30min", new Double(0),
					FXUtils.df1));
		}
		if (candleTrigger30minStr.length() == 0) {
			logLine.add(new FlexLogEntry("bullishTriggerCombinedUHPerc30min",
					new Double(0), FXUtils.df1));
			logLine.add(new FlexLogEntry("bullishTriggerCombinedBodyPerc30min",
					new Double(0), FXUtils.df1));
			logLine.add(new FlexLogEntry("bullishTriggerCombinedLHPerc30min",
					new Double(0), FXUtils.df1));
			logLine.add(new FlexLogEntry("bullishTriggerCombinedSizeStat30min",
					new Double(0), FXUtils.df1));
		}
		logLine.add(new FlexLogEntry("CandleTrigger30min",
				candleTrigger30minStr.length() > 0 ? candleTrigger30minStr
						: "none"));

		double bar4hStat = 0.0;
		String candleTrigger4hStr = new String();
		TradeTrigger.TriggerDesc bearishCandleTriggerDesc4h = null, bullishCandleTriggerDesc4h = null;
		long bar4hTimeToUse = 0;
		if (period.equals(Period.FOUR_HOURS)) {
			bar4hTimeToUse = bidBar.getTime();

			logLine.add(new FlexLogEntry("barOpen4h", new Double(bidBar
					.getOpen()), FXUtils.df5));
			logLine.add(new FlexLogEntry("barClose4h", new Double(bidBar
					.getClose()), FXUtils.df5));
			logLine.add(new FlexLogEntry("barLow4h",
					new Double(bidBar.getLow()), FXUtils.df5));
			logLine.add(new FlexLogEntry("barHigh4h", new Double(bidBar
					.getHigh()), FXUtils.df5));

			if (bidBar.getHigh() != bidBar.getLow()) {
				logLine.add(new FlexLogEntry("upperHandlePerc4h", new Double(
						tradeTrigger.barsUpperHandlePerc(bidBar)), FXUtils.df1));
				int sign = bidBar.getClose() > bidBar.getOpen() ? 1 : -1;
				logLine.add(new FlexLogEntry("barBodyPerc4h", new Double(sign
						* tradeTrigger.barsBodyPerc(bidBar)), FXUtils.df1));
				logLine.add(new FlexLogEntry("lowerHandlePerc4h", new Double(
						tradeTrigger.barsLowerHandlePerc(bidBar)), FXUtils.df1));
			}
			bar4hStat = tradeTrigger.barLengthStatPos(instrument,
					Period.FOUR_HOURS, OfferSide.BID, bidBar,
					FXUtils.QUARTER_WORTH_OF_4h_BARS);
			logLine.add(new FlexLogEntry("barStat4h", new Double(bar4hStat),
					FXUtils.df1));
		} else {
			bar4hTimeToUse = history.getPreviousBarStart(Period.FOUR_HOURS,
					bidBar.getTime());
		}

		bullishCandleTriggerDesc4h = tradeTrigger
				.bullishReversalCandlePatternDesc(instrument,
						Period.FOUR_HOURS, OfferSide.BID, bar4hTimeToUse);
		if (bullishCandleTriggerDesc4h != null) {
			candleTrigger4hStr += bullishCandleTriggerDesc4h.type.toString();
			logLine.add(new FlexLogEntry("bullishCandleTriggerChannelPos4h",
					new Double(bullishCandleTriggerDesc4h.channelPosition),
					FXUtils.df1));
			logLine.add(new FlexLogEntry("bullishPivotLevel4h", new Double(
					bullishCandleTriggerDesc4h.pivotLevel), FXUtils.df5));
			logLine.add(new FlexLogEntry("bullishCandleTriggerKChannelPos4h",
					new Double(
							bullishCandleTriggerDesc4h.keltnerChannelPosition),
					FXUtils.df1));

			long prev1dBarTime = history.getPreviousBarStart(
					Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
			double channelPos1d = tradeTrigger.priceChannelPos(instrument,
					Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
					prev1dBarTime, bullishCandleTriggerDesc4h.pivotLevel, 0)[0], keltnerPos1d = tradeTrigger
					.priceKeltnerChannelPos(instrument,
							Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
							prev1dBarTime,
							bullishCandleTriggerDesc4h.pivotLevel, 0);
			logLine.add(new FlexLogEntry(
					"bullishPivotLevelHigherTFChannelPos4h", new Double(
							channelPos1d), FXUtils.df1));
			logLine.add(new FlexLogEntry(
					"bullishPivotLevelHigherTFKChannelPos4h", new Double(
							keltnerPos1d), FXUtils.df1));

			logLine.add(new FlexLogEntry(
					"bullishTriggerCombinedUHPerc4h",
					new Double(
							bullishCandleTriggerDesc4h.combinedUpperHandlePerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry(
					"bullishTriggerCombinedBodyPerc4h",
					new Double(bullishCandleTriggerDesc4h.combinedRealBodyPerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry(
					"bullishTriggerCombinedLHPerc4h",
					new Double(
							bullishCandleTriggerDesc4h.combinedLowerHandlePerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry(
					"bullishTriggerCombinedBodyDirection4h",
					new String(
							bullishCandleTriggerDesc4h.combinedRealBodyDirection ? "BULLISH"
									: "BEARISH")));
			double reversalSizeStat = tradeTrigger.barLengthStatPos(instrument,
					period, OfferSide.BID, bidBar,
					bullishCandleTriggerDesc4h.getCombinedHigh(),
					bullishCandleTriggerDesc4h.getCombinedLow(),
					FXUtils.QUARTER_WORTH_OF_4h_BARS);
			logLine.add(new FlexLogEntry("bullishTriggerCombinedSizeStat4h",
					new Double(reversalSizeStat), FXUtils.df1));

			double barHigh = bidBar.getHigh(), barLow = bidBar.getLow(), barHalf = barLow
					+ (barHigh - barLow) / 2, atr4h = vola.getATR(instrument,
					Period.FOUR_HOURS, OfferSide.BID, bar4hTimeToUse, 14), bullishPivotLevel = bullishCandleTriggerDesc4h.pivotLevel, aggressiveSL = bullishPivotLevel
					- atr4h / Math.pow(10, instrument.getPipScale()), riskStandard = (bidBar
					.getHigh() - bullishPivotLevel)
					* Math.pow(10, instrument.getPipScale()), riskAggressive = (bidBar
					.getHigh() - aggressiveSL)
					* Math.pow(10, instrument.getPipScale()), riskStandard2 = (barHalf - bullishPivotLevel)
					* Math.pow(10, instrument.getPipScale()), riskAggressive2 = (barHalf - aggressiveSL)
					* Math.pow(10, instrument.getPipScale());
			logLine.add(new FlexLogEntry("riskBullishStandard4h", new Double(
					riskStandard), FXUtils.df1));
			logLine.add(new FlexLogEntry("riskBullishAggresive4h", new Double(
					riskAggressive), FXUtils.df1));
		} else {
			logLine.add(new FlexLogEntry("bullishCandleTriggerChannelPos4h",
					new Double(0), FXUtils.df1));
			logLine.add(new FlexLogEntry("bullishPivotLevel4h", new Double(0),
					FXUtils.df5));
		}
		if ((bearishCandleTriggerDesc4h = tradeTrigger
				.bearishReversalCandlePatternDesc(instrument,
						Period.FOUR_HOURS, OfferSide.BID, bar4hTimeToUse)) != null) {
			if (candleTrigger4hStr.length() > 0)
				candleTrigger4hStr += " AND "
						+ bearishCandleTriggerDesc4h.type.toString();
			else
				candleTrigger4hStr += bearishCandleTriggerDesc4h.type
						.toString();
			logLine.add(new FlexLogEntry("bearishCandleTriggerChannelPos4h",
					new Double(bearishCandleTriggerDesc4h.channelPosition),
					FXUtils.df1));
			logLine.add(new FlexLogEntry("bearishPivotLevel4h", new Double(
					bearishCandleTriggerDesc4h.pivotLevel), FXUtils.df5));
			logLine.add(new FlexLogEntry("bearishCandleTriggerKChannelPos4h",
					new Double(
							bearishCandleTriggerDesc4h.keltnerChannelPosition),
					FXUtils.df1));

			long prev1dBarTime = history.getPreviousBarStart(
					Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
			double channelPos1d = tradeTrigger.priceChannelPos(instrument,
					Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
					prev1dBarTime, bearishCandleTriggerDesc4h.pivotLevel, 0)[0], keltnerPos1d = tradeTrigger
					.priceKeltnerChannelPos(instrument,
							Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
							prev1dBarTime,
							bearishCandleTriggerDesc4h.pivotLevel, 0);
			logLine.add(new FlexLogEntry(
					"bearishPivotLevelHigherTFChannelPos4h", new Double(
							channelPos1d), FXUtils.df1));
			logLine.add(new FlexLogEntry(
					"bearishPivotLevelHigherTFKChannelPos4h", new Double(
							keltnerPos1d), FXUtils.df1));

			logLine.add(new FlexLogEntry(
					"bearishTriggerCombinedUHPerc4h",
					new Double(
							bearishCandleTriggerDesc4h.combinedUpperHandlePerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry(
					"bearishTriggerCombinedBodyPerc4h",
					new Double(bearishCandleTriggerDesc4h.combinedRealBodyPerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry(
					"bearishTriggerCombinedLHPerc4h",
					new Double(
							bearishCandleTriggerDesc4h.combinedLowerHandlePerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry(
					"bearishTriggerCombinedBodyDirection4h",
					new String(
							bearishCandleTriggerDesc4h.combinedRealBodyDirection ? "BULLISH"
									: "BEARISH")));
			double reversalSizeStat = tradeTrigger.barLengthStatPos(instrument,
					period, OfferSide.BID, bidBar,
					bearishCandleTriggerDesc4h.getCombinedHigh(),
					bearishCandleTriggerDesc4h.getCombinedLow(),
					FXUtils.QUARTER_WORTH_OF_4h_BARS);
			logLine.add(new FlexLogEntry("bearishTriggerCombinedSizeStat4h",
					new Double(reversalSizeStat), FXUtils.df1));

			double barHigh = bidBar.getHigh(), barLow = bidBar.getLow(), barHalf = barLow
					+ (barHigh - barLow) / 2, atr4h = vola.getATR(instrument,
					Period.FOUR_HOURS, OfferSide.BID, bar4hTimeToUse, 14), bearishPivotLevel = bearishCandleTriggerDesc4h.pivotLevel, aggressiveSL = bearishPivotLevel
					+ atr4h / Math.pow(10, instrument.getPipScale()), riskStandard = (bearishPivotLevel - barLow)
					* Math.pow(10, instrument.getPipScale()), riskAggressive = (aggressiveSL - barLow)
					* Math.pow(10, instrument.getPipScale()), riskStandard2 = (bearishPivotLevel - barHalf)
					* Math.pow(10, instrument.getPipScale()), riskAggressive2 = (aggressiveSL - barHalf)
					* Math.pow(10, instrument.getPipScale());
			logLine.add(new FlexLogEntry("riskBearishStandard4h", new Double(
					riskStandard), FXUtils.df1));
			logLine.add(new FlexLogEntry("riskBearishAggresive4h", new Double(
					riskAggressive), FXUtils.df1));
		} else {
			logLine.add(new FlexLogEntry("bearishCandleTriggerChannelPos4h",
					new Double(0), FXUtils.df1));
			logLine.add(new FlexLogEntry("bearishPivotLevel4h", new Double(0),
					FXUtils.df5));
		}
		logLine.add(new FlexLogEntry("CandleTrigger4h", candleTrigger4hStr
				.length() > 0 ? candleTrigger4hStr : "none"));

		double bar1dStat = 0.0;
		String candleTrigger1dStr = new String();
		TradeTrigger.TriggerDesc bearishCandleTriggerDesc1d = null, bullishCandleTriggerDesc1d = null;
		long prev1dBarTime = history.getPreviousBarStart(
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		// problem when previous day was a bank holidays - WEEKENDS filter can't
		// detect that and a flat is returned
		// for the time being avoid calculating candle stats in such cases -
		// long-term need to find the previous non-flat day i.e. working day
		IBar last1dBar = history.getBars(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, Filter.WEEKENDS,
				1, prev1dBarTime, 0).get(0);

		logLine.add(new FlexLogEntry("barOpen1d", new Double(last1dBar
				.getOpen()), FXUtils.df5));
		logLine.add(new FlexLogEntry("barClose1d", new Double(last1dBar
				.getClose()), FXUtils.df5));
		logLine.add(new FlexLogEntry("barLow1d",
				new Double(last1dBar.getLow()), FXUtils.df5));
		logLine.add(new FlexLogEntry("barHigh1d", new Double(last1dBar
				.getHigh()), FXUtils.df5));

		if (last1dBar.getHigh() != last1dBar.getLow()) {
			logLine.add(new FlexLogEntry("upperHandlePerc1d", new Double(
					tradeTrigger.barsUpperHandlePerc(last1dBar)), FXUtils.df1));
			int sign = last1dBar.getClose() > last1dBar.getOpen() ? 1 : -1;
			logLine.add(new FlexLogEntry("barBodyPerc1d", new Double(sign
					* tradeTrigger.barsBodyPerc(last1dBar)), FXUtils.df1));
			logLine.add(new FlexLogEntry("lowerHandlePerc1d", new Double(
					tradeTrigger.barsLowerHandlePerc(last1dBar)), FXUtils.df1));
		}

		boolean bullishSundayCandle1d = false;
		bullishCandleTriggerDesc1d = tradeTrigger
				.bullishReversalCandlePatternDesc(instrument,
						Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
						prev1dBarTime);
		if (bullishCandleTriggerDesc1d == null) {
			bullishCandleTriggerDesc1d = tradeTrigger
					.bullishReversalCandlePatternDesc(instrument, Period.DAILY,
							OfferSide.BID, prev1dBarTime);
			bullishSundayCandle1d = true;
		}
		if (bullishCandleTriggerDesc1d != null) {
			candleTrigger1dStr += bullishCandleTriggerDesc1d.type.toString();
			logLine.add(new FlexLogEntry("bullishCandleTriggerChannelPos1d",
					new Double(bullishCandleTriggerDesc1d.channelPosition),
					FXUtils.df1));
			logLine.add(new FlexLogEntry("bullishCandleTriggerKChannelPos1d",
					new Double(
							bullishCandleTriggerDesc1d.keltnerChannelPosition),
					FXUtils.df1));
			logLine.add(new FlexLogEntry("bullishPivotLevel1d", new Double(
					bullishCandleTriggerDesc1d.pivotLevel), FXUtils.df5));

			logLine.add(new FlexLogEntry("bullishSundayCandle1d",
					bullishSundayCandle1d ? "yes" : "no"));

			logLine.add(new FlexLogEntry(
					"bullishTriggerCombinedUHPerc1d",
					new Double(
							bullishCandleTriggerDesc1d.combinedUpperHandlePerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry(
					"bullishTriggerCombinedBodyPerc1d",
					new Double(bullishCandleTriggerDesc1d.combinedRealBodyPerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry(
					"bullishTriggerCombinedLHPerc1d",
					new Double(
							bullishCandleTriggerDesc1d.combinedLowerHandlePerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry(
					"bullishTriggerCombinedBodyDirection1d",
					new String(
							bullishCandleTriggerDesc1d.combinedRealBodyDirection ? "BULLISH"
									: "BEARISH")));
			double reversalSizeStat = tradeTrigger.barLengthStatPos(instrument,
					Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, last1dBar,
					bullishCandleTriggerDesc1d.getCombinedHigh(),
					bullishCandleTriggerDesc1d.getCombinedLow(),
					FXUtils.YEAR_WORTH_OF_1d_BARS);
			logLine.add(new FlexLogEntry("bullishTriggerCombinedSizeStat1d",
					new Double(reversalSizeStat), FXUtils.df1));
		} else {
			logLine.add(new FlexLogEntry("bullishCandleTriggerChannelPos1d",
					new Double(0), FXUtils.df1));
			logLine.add(new FlexLogEntry("bullishPivotLevel1d", new Double(0),
					FXUtils.df5));
		}

		boolean bearishSundayCandle1d = false;
		if ((bearishCandleTriggerDesc1d = tradeTrigger
				.bearishReversalCandlePatternDesc(instrument,
						Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
						prev1dBarTime)) == null) {
			bearishCandleTriggerDesc1d = tradeTrigger
					.bearishReversalCandlePatternDesc(instrument, Period.DAILY,
							OfferSide.BID, prev1dBarTime);
			bearishSundayCandle1d = true;
		}
		if (bearishCandleTriggerDesc1d != null) {
			if (candleTrigger1dStr.length() > 0)
				candleTrigger1dStr += " AND "
						+ bearishCandleTriggerDesc1d.type.toString();
			else
				candleTrigger1dStr += bearishCandleTriggerDesc1d.type
						.toString();
			logLine.add(new FlexLogEntry("bearishCandleTriggerChannelPos1d",
					new Double(bearishCandleTriggerDesc1d.channelPosition),
					FXUtils.df1));
			logLine.add(new FlexLogEntry("bearishCandleTriggerKChannelPos1d",
					new Double(
							bearishCandleTriggerDesc1d.keltnerChannelPosition),
					FXUtils.df1));
			logLine.add(new FlexLogEntry("bearishPivotLevel1d", new Double(
					bearishCandleTriggerDesc1d.pivotLevel), FXUtils.df5));

			logLine.add(new FlexLogEntry("bearishSundayCandle1d",
					bearishSundayCandle1d ? "yes" : "no"));

			logLine.add(new FlexLogEntry(
					"bearishTriggerCombinedUHPerc1d",
					new Double(
							bearishCandleTriggerDesc1d.combinedUpperHandlePerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry(
					"bearishTriggerCombinedBodyPerc1d",
					new Double(bearishCandleTriggerDesc1d.combinedRealBodyPerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry(
					"bearishTriggerCombinedLHPerc1d",
					new Double(
							bearishCandleTriggerDesc1d.combinedLowerHandlePerc),
					FXUtils.df1));
			logLine.add(new FlexLogEntry(
					"bearishTriggerCombinedBodyDirection1d",
					new String(
							bearishCandleTriggerDesc1d.combinedRealBodyDirection ? "BULLISH"
									: "BEARISH")));
			double reversalSizeStat = tradeTrigger.barLengthStatPos(instrument,
					Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, last1dBar,
					bearishCandleTriggerDesc1d.getCombinedHigh(),
					bearishCandleTriggerDesc1d.getCombinedLow(),
					FXUtils.YEAR_WORTH_OF_1d_BARS);
			logLine.add(new FlexLogEntry("bearishTriggerCombinedSizeStat1d",
					new Double(reversalSizeStat), FXUtils.df1));
		} else {
			logLine.add(new FlexLogEntry("bearishCandleTriggerChannelPos1d",
					new Double(0), FXUtils.df1));
			logLine.add(new FlexLogEntry("bearishPivotLevel1d", new Double(0),
					FXUtils.df5));
		}

		bar1dStat = tradeTrigger.barLengthStatPos(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, last1dBar,
				FXUtils.YEAR_WORTH_OF_1d_BARS);
		logLine.add(new FlexLogEntry("CandleTrigger1d", candleTrigger1dStr
				.length() > 0 ? candleTrigger1dStr : "none"));
		logLine.add(new FlexLogEntry("barStat1d", new Double(bar1dStat),
				FXUtils.df1));
	}
}
