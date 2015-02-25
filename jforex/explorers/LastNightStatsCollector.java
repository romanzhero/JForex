package jforex.explorers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import jforex.BasicTAStrategy;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.SRLevel;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Trendline;
import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;
import jforex.utils.Logger;

import com.dukascopy.api.Configurable;
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

public class LastNightStatsCollector extends BasicTAStrategy implements IStrategy {
	
	Logger reportLogger = null;
	protected Map<String, String> nextSetups = new HashMap<String, String>();
	
	protected static final String
		TREND_ID_30MIN = "TREND_ID_30MIN",
		TREND_ID_4H = "TREND_ID_4H",
		TREND_STRENGTH_30MIN = "TREND_STRENGTH_30MIN",
		TREND_STRENGTH_4H = "TREND_STRENGTH_4H";
	
	@Configurable("Period")
	public Period basicTimeFrame = Period.THIRTY_MINS;	
	
	public LastNightStatsCollector(Properties p, Logger reportLogger) {
		super(p);
		this.reportLogger = reportLogger;
		String nextSetupsStr = p.getProperty("nextSetups");
		if (nextSetupsStr != null) {
			// format should be: nextSetups=<pair>:<setup description>;<pair>:<setup description>;...;<pair>:<setup description>
			StringTokenizer st = new StringTokenizer(nextSetupsStr, ";");
			while (st.hasMoreTokens()) {
				String nextSetup = st.nextToken();
				StringTokenizer internal = new StringTokenizer(nextSetup, ":");
				nextSetups.put(internal.nextToken(), internal.nextToken());
			}
		}
	}

	@Override
	public void onStart(IContext context) throws JFException {
		super.onStartExec(context);
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException { }

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException 
	{	
		if (!period.equals(Period.FOUR_HOURS)) {
            return;
        }
		log.print("Entered onBar AFTER filtering for timeframe " + period.toString() + " and bidBar time of " + FXUtils.getFormatedBarTime(bidBar));
        
        // Attention: 4h indicators are all calculated for 7 out 8 30min candles as INCOMPLETE bars ! On purpose - immediate reaction wanted, not up to 3,5 hours later !         
        
        long prevDayTime = history.getPreviousBarStart(Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
        long prev4hTime = bidBar.getTime();

        double ATR30min = vola.getATR(instrument, basicTimeFrame, OfferSide.BID, bidBar.getTime(), 14) * Math.pow(10, instrument.getPipScale());
        double ATR4h = vola.getATR(instrument, Period.FOUR_HOURS, OfferSide.BID, bidBar.getTime(), 14) * Math.pow(10, instrument.getPipScale());
        double ATR1d = vola.getATR(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prevDayTime, 14) * Math.pow(10, instrument.getPipScale());
        double bBandsSqueeze30min = vola.getBBandsSqueeze(instrument, basicTimeFrame, OfferSide.BID, bidBar.getTime(), 20);
        double bBandsSqueeze4h = vola.getBBandsSqueeze(instrument, Period.FOUR_HOURS, OfferSide.BID, prev4hTime, 20);

        Trend.TREND_STATE trendID30min = trendDetector.getTrendState(instrument, basicTimeFrame, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime());
        Trend.TREND_STATE trendID4h = trendDetector.getTrendState(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime());
        Trend.TREND_STATE trendID1d = trendDetector.getTrendState(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, AppliedPrice.CLOSE, prevDayTime);

        List<FlexLogEntry> logLine = new ArrayList<FlexLogEntry>();
		
		logLine.add(new FlexLogEntry("Pair", instrument.toString()));
		//TODO: remove after understanding for which period is the method called first, 30min or 4h when 4h is full
		logLine.add(new FlexLogEntry("Bars period", period.toString()));
		logLine.add(new FlexLogEntry("Time 30min", FXUtils.getFormatedTimeCET(bidBar.getTime())));

		logLine.add(new FlexLogEntry("TrendId30min", trendID30min.toString()));
		logLine.add(new FlexLogEntry("TrendId4h", trendID4h.toString()));
		double trendStrength30min = trendDetector.getMAsMaxDiffStDevPos(instrument, basicTimeFrame, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime(), FXUtils.MONTH_WORTH_OF_30min_BARS); 
		double trendStrength4h = trendDetector.getMAsMaxDiffStDevPos(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime(), FXUtils.QUARTER_WORTH_OF_4h_BARS);
		String trendIdOverlapStr = null;
		if (trendStrength30min > -0.7 && trendStrength4h > -0.7)
			trendIdOverlapStr = new String("(" + trendID30min.toString() + ")");
		else if (trendStrength30min < -0.7 && trendStrength4h < -0.7)
			trendIdOverlapStr = new String("(FLAT)");
		
		logLine.add(new FlexLogEntry("TrendIdOverlap", 
				trendID30min.equals(trendID4h) 
				&& ((trendStrength30min > -0.7 && trendStrength4h > -0.7)
					|| (trendStrength30min < -0.7 && trendStrength4h < -0.7)) ? "YES " + trendIdOverlapStr: "no"));
		logLine.add(new FlexLogEntry("TrendId1d", trendID1d.toString()));
		// trend regime
		addFlatStats(instrument, bidBar, logLine);
		// momentum
		addMACDStats(instrument, bidBar, logLine);
		// OS/OB
		addRSIStats(instrument, bidBar, logLine);
		addStochStats(instrument, bidBar, logLine);
		// channel position
		addChannelPosStats(instrument, bidBar, logLine);
		// candles
		addCandles(instrument, bidBar, period, logLine);
		
		logLine.add(new FlexLogEntry("ATR30min", new Double(ATR30min), FXUtils.df1));
		logLine.add(new FlexLogEntry("ATR30min + 20%", new Double(ATR30min * 1.2), FXUtils.df1));
		logLine.add(new FlexLogEntry("ATR4h", new Double(ATR4h), FXUtils.df1));
		logLine.add(new FlexLogEntry("ATR1d", new Double(ATR1d), FXUtils.df1));
		logLine.add(new FlexLogEntry("volatility4h", new Double(bBandsSqueeze4h), FXUtils.df1));
		logLine.add(new FlexLogEntry("volatility30min", new Double(bBandsSqueeze30min), FXUtils.df1));
		
		addADXStats(instrument, bidBar, period, logLine);
		addCCIStats(instrument, bidBar, period, logLine);
		
		printOvernightReport(instrument, period, bidBar, logLine);
	}

	private void printOvernightReport(Instrument instrument, Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine) throws JFException {
		String 
			mailBody = null;
		if (pPeriod.equals(Period.FOUR_HOURS)) {
			mailBody = createReportBody4h(instrument, pPeriod, bidBar, logLine);
		}
		else
			return;
		
		reportLogger.print(mailBody);
	}

	protected String checkTALevels(Instrument instrument, Period pPeriod, IBar bidBar, String candleSignals) throws JFException {
		String taLevelsHit = new String();
		IBar barToCheck = bidBar;
		List<SRLevel> MAsToCheck = getMAsValues(instrument, bidBar);
		// check for srLevels crossed
		if (candleSignals.contains("_2_BARS") || candleSignals.contains("_3_BARS")) {
			// get previous bar
			barToCheck = history.getBar(instrument, pPeriod, OfferSide.BID, 2);
		}
		for (SRLevel levelsToCheck : srLevels) {
			if (levelsToCheck.hit(barToCheck, 10 / Math.pow(10, instrument.getPipScale()))) {
				if (taLevelsHit.length() == 0)
					taLevelsHit += "S/R level " + levelsToCheck + " hit !";
				else
					taLevelsHit += ", S/R level " + levelsToCheck + " hit !";
			}
		}
		for (SRLevel levelsToCheck : MAsToCheck) {
			if (levelsToCheck.hit(barToCheck, 5 / Math.pow(10, instrument.getPipScale()))) {
				if (taLevelsHit.length() == 0)
					taLevelsHit += "moving average " + levelsToCheck + " hit !";
				else
					taLevelsHit += ", moving average " + levelsToCheck + " hit !";
			}
		}
		
		for (Trendline trendLineToCheck : trendlinesToCheck) {
			if (trendLineToCheck.hit(barToCheck, 10 / Math.pow(10, instrument.getPipScale()))) {
				if (taLevelsHit.length() == 0)
					taLevelsHit += "trendline hit: " + trendLineToCheck.getName() + " !";
				else
					taLevelsHit += ", trendline hit: " + trendLineToCheck.getName() + " !";
			}				
		}
		if (taLevelsHit.length() > 0)
			taLevelsHit = " and " + taLevelsHit;
		return taLevelsHit;
	}

	private List<SRLevel> getMAsValues(Instrument instrument, IBar bidBar) throws JFException {
		List<SRLevel> result = new ArrayList<SRLevel>();

		result.add(new SRLevel("MA50 30min", indicators.sma(instrument, Period.THIRTY_MINS, OfferSide.BID, AppliedPrice.CLOSE, 50, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]));
		result.add(new SRLevel("MA100 30min", indicators.sma(instrument, Period.THIRTY_MINS, OfferSide.BID, AppliedPrice.CLOSE, 100, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]));
		result.add(new SRLevel("MA200 30min", indicators.sma(instrument, Period.THIRTY_MINS, OfferSide.BID, AppliedPrice.CLOSE, 200, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]));

		result.add(new SRLevel("MA20 4h", indicators.sma(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, 20, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]));
		result.add(new SRLevel("MA50 4h", indicators.sma(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, 50, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]));
		result.add(new SRLevel("MA100 4h", indicators.sma(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, 100, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]));
		result.add(new SRLevel("MA200 4h", indicators.sma(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, 200, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]));

		result.add(new SRLevel("MA20 1d", indicators.sma(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, AppliedPrice.CLOSE, 20, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]));
		result.add(new SRLevel("MA50 1d", indicators.sma(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, AppliedPrice.CLOSE, 50, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]));
		result.add(new SRLevel("MA100 1d", indicators.sma(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, AppliedPrice.CLOSE, 100, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]));
		result.add(new SRLevel("MA200 1d", indicators.sma(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, AppliedPrice.CLOSE, 200, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]));

		return result;
	}

	protected void addMACDStats(Instrument instrument, IBar bidBar, List<FlexLogEntry> logLine) throws JFException {
		Momentum.MACD_STATE macd30minState = momentum.getMACDState(instrument, basicTimeFrame, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime());
		Momentum.MACD_STATE macd4hState = momentum.getMACDState(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime());
		Momentum.MACD_H_STATE macdH30minState = momentum.getMACDHistogramState(instrument, basicTimeFrame, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime());
		Momentum.MACD_H_STATE macdH4hState = momentum.getMACDHistogramState(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime());
        long prevDayTime = history.getPreviousBarStart(Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		Momentum.MACD_STATE macd1dState = momentum.getMACDState(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, AppliedPrice.CLOSE, prevDayTime);
		Momentum.MACD_H_STATE macdH1dState = momentum.getMACDHistogramState(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, AppliedPrice.CLOSE, prevDayTime);
		
		logLine.add(new FlexLogEntry("MACDState30min", macd30minState.toString()));
		logLine.add(new FlexLogEntry("MACDState4h", macd4hState.toString()));
		logLine.add(new FlexLogEntry("MACDStateOverlap", macd30minState.equals(macd4hState) ? "YES (" + macd30minState.toString() + ")": "no"));
		logLine.add(new FlexLogEntry("MACDHState30min", macdH30minState.toString()));
		logLine.add(new FlexLogEntry("MACDHState4h", macdH4hState.toString()));
		logLine.add(new FlexLogEntry("MACDHStateOverlap", macdH30minState.equals(macdH4hState) ? "YES (" + macdH30minState.toString() + ")": "no"));
		logLine.add(new FlexLogEntry("MACDState1d", macd1dState.toString()));
		logLine.add(new FlexLogEntry("MACDHState1d", macdH1dState.toString()));

		Momentum.MACD_CROSS macd30minCross = momentum.getMACDCross(instrument, basicTimeFrame, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime());
		Momentum.MACD_CROSS macd4hCross = momentum.getMACDCross(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime());
		logLine.add(new FlexLogEntry("MACDCross30min", macd30minCross.toString()));
		logLine.add(new FlexLogEntry("MACDCross4h", macd4hCross.toString()));
		Momentum.MACD_CROSS macd1dCross = momentum.getMACDCross(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, AppliedPrice.CLOSE, prevDayTime);
		logLine.add(new FlexLogEntry("MACDCross1d", macd1dCross.toString()));
		
		double MACD30minStDevPos = momentum.getMACDStDevPos(instrument, basicTimeFrame, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime(), FXUtils.MONTH_WORTH_OF_30min_BARS);
		logLine.add(new FlexLogEntry("MACDStDevPos30min", new Double(MACD30minStDevPos), FXUtils.df1));
		logLine.add(new FlexLogEntry("MACDAbs30min", new Double(momentum.MACD(instrument, basicTimeFrame, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime())), FXUtils.df5));

		double MACD4hStDevPos = momentum.getMACDStDevPos(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime(), FXUtils.QUARTER_WORTH_OF_4h_BARS);
		logLine.add(new FlexLogEntry("MACDStDevPos4h", new Double(MACD4hStDevPos), FXUtils.df1));
		logLine.add(new FlexLogEntry("MACDAbs4h", new Double(momentum.MACD(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime())), FXUtils.df5));

		double MACD1dStDevPos = momentum.getMACDStDevPos(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, AppliedPrice.CLOSE, prevDayTime, FXUtils.YEAR_WORTH_OF_1d_BARS);
		logLine.add(new FlexLogEntry("MACDStDevPos1d", new Double(MACD1dStDevPos), FXUtils.df1));
		logLine.add(new FlexLogEntry("MACDAbs1d", new Double(momentum.MACD(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, AppliedPrice.CLOSE, prevDayTime)), FXUtils.df5));

		double MACD_H30minStDevPos = momentum.getMACD_HStDevPos(instrument, basicTimeFrame, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime(), FXUtils.MONTH_WORTH_OF_30min_BARS);
		logLine.add(new FlexLogEntry("MACD_HStDevPos30min", new Double(MACD_H30minStDevPos), FXUtils.df1));
		logLine.add(new FlexLogEntry("MACD_HAbs30min", new Double(momentum.MACDHistogram(instrument, basicTimeFrame, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime())), FXUtils.df5));

		double MACD_H4hStDevPos = momentum.getMACD_HStDevPos(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime(), FXUtils.QUARTER_WORTH_OF_4h_BARS);
		logLine.add(new FlexLogEntry("MACD_HStDevPos4h", new Double(MACD_H4hStDevPos), FXUtils.df1));
		logLine.add(new FlexLogEntry("MACD_HAbs4h", new Double(momentum.MACDHistogram(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime())), FXUtils.df5));

		double MACD_H1dStDevPos = momentum.getMACD_HStDevPos(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, AppliedPrice.CLOSE, prevDayTime, FXUtils.YEAR_WORTH_OF_1d_BARS);
		logLine.add(new FlexLogEntry("MACD_HStDevPos1d", new Double(MACD_H1dStDevPos), FXUtils.df1));
		logLine.add(new FlexLogEntry("MACD_HAbs1d", new Double(momentum.MACDHistogram(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, AppliedPrice.CLOSE, prevDayTime)), FXUtils.df5));
	}

	protected void addStochStats(Instrument instrument, IBar bidBar, List<FlexLogEntry> logLine) throws JFException {
		Momentum.STOCH_STATE stoch30minState = momentum.getStochState(instrument, basicTimeFrame, OfferSide.BID, bidBar.getTime());
		Momentum.STOCH_STATE stoch4hState = momentum.getStochState(instrument, Period.FOUR_HOURS, OfferSide.BID, bidBar.getTime());

		logLine.add(new FlexLogEntry("StochState30min", stoch30minState.toString()));
		logLine.add(new FlexLogEntry("StochState4h", stoch4hState.toString()));
		logLine.add(new FlexLogEntry("StochStateOverlap", stoch30minState.equals(stoch4hState) ? "YES (" + stoch30minState.toString() + ")": "no"));
		double[] stochs30min = momentum.getStochs(instrument, basicTimeFrame, OfferSide.BID, bidBar.getTime());
		logLine.add(new FlexLogEntry("StochFast30min", stochs30min[0], FXUtils.df1));
		logLine.add(new FlexLogEntry("StochSlow30min", stochs30min[1], FXUtils.df1));
		double[] stochs4h = momentum.getStochs(instrument, Period.FOUR_HOURS, OfferSide.BID, bidBar.getTime());
		logLine.add(new FlexLogEntry("StochFast4h", stochs4h[0], FXUtils.df1));
		logLine.add(new FlexLogEntry("StochSlow4h", stochs4h[1], FXUtils.df1));

		long prevDayTime = history.getPreviousBarStart(Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		Momentum.STOCH_STATE stoch1dState = momentum.getStochState(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prevDayTime);
		logLine.add(new FlexLogEntry("StochState1d", stoch1dState.toString()));
		double[] stochs1d = momentum.getStochs(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prevDayTime);
		logLine.add(new FlexLogEntry("StochFast1d", stochs1d[0], FXUtils.df1));
		logLine.add(new FlexLogEntry("StochSlow1d", stochs1d[1], FXUtils.df1));

	}

	protected void addRSIStats(Instrument instrument, IBar bidBar, List<FlexLogEntry> logLine) throws JFException {
		double rsi30min = momentum.getRSI(instrument, basicTimeFrame, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime());
		logLine.add(new FlexLogEntry("RSI30min", new Double(rsi30min), FXUtils.df1));

		double rsi4h = momentum.getRSI(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime());
		logLine.add(new FlexLogEntry("RSI4h", new Double(rsi4h), FXUtils.df1));
		logLine.add(new FlexLogEntry("RSIState4h", new String(momentum.getRSIState(instrument, Period.FOUR_HOURS, OfferSide.BID, bidBar.getTime()).toString())));
		
		logLine.add(new FlexLogEntry("RSIOverlap", (rsi30min <= 33 && rsi4h <= 33) || (rsi30min >= 67 && rsi4h >= 67)? "YES (" + FXUtils.df1.format(rsi30min) + "/" + FXUtils.df1.format(rsi4h) + ")": "no"));
		
		long prevDayTime = history.getPreviousBarStart(Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		double rsi1d = momentum.getRSI(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, AppliedPrice.CLOSE, prevDayTime);
		logLine.add(new FlexLogEntry("RSI1d", new Double(rsi1d), FXUtils.df1));

	}

	protected void addFlatStats(Instrument instrument, IBar bidBar, List<FlexLogEntry> logLine) throws JFException {
		double trendStrength30min = trendDetector.getMAsMaxDiffStDevPos(instrument, basicTimeFrame, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime(), FXUtils.MONTH_WORTH_OF_30min_BARS); 
		logLine.add(new FlexLogEntry("MAsDistance30min", new Double(trendStrength30min), FXUtils.df1));
		trendStrength30min = trendDetector.getAbsMAsDifference(instrument, basicTimeFrame, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime()); 
		logLine.add(new FlexLogEntry("AbsMAsDistance30min", new Double(trendStrength30min), FXUtils.df1));
		
		double trendStrength4h = trendDetector.getMAsMaxDiffStDevPos(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime(), FXUtils.QUARTER_WORTH_OF_4h_BARS); 
		logLine.add(new FlexLogEntry("MAsDistance4h", new Double(trendStrength4h), FXUtils.df1));
		trendStrength4h = trendDetector.getAbsMAsDifference(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime()); 
		logLine.add(new FlexLogEntry("AbsMAsDistance4h", new Double(trendStrength4h), FXUtils.df1));
		logLine.add(new FlexLogEntry("MAsDistanceOverlap", trendStrength30min <= -0.7 && trendStrength4h <= -0.7 ? "YES (FLAT)" : "no"));

		long prevDayTime = history.getPreviousBarStart(Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		double trendStrength1d = trendDetector.getMAsMaxDiffStDevPos(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, AppliedPrice.CLOSE, prevDayTime, FXUtils.YEAR_WORTH_OF_1d_BARS); 
		logLine.add(new FlexLogEntry("MAsDistance1d", new Double(trendStrength1d), FXUtils.df1));
		trendStrength1d = trendDetector.getAbsMAsDifference(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, AppliedPrice.CLOSE, prevDayTime); 
		logLine.add(new FlexLogEntry("AbsMAsDistance1d", new Double(trendStrength1d), FXUtils.df1));

	}

	protected void addChannelPosStats(Instrument instrument, IBar bidBar, List<FlexLogEntry> logLine) throws JFException {
        double lowChannelPos30min = 0, lowChannelPosPrevBar30min = 0;
        lowChannelPos30min = channelPosition.priceChannelPos(instrument, basicTimeFrame, OfferSide.BID, bidBar.getTime(), bidBar.getLow());
		logLine.add(new FlexLogEntry("barLowChannelPos30min", new Double(lowChannelPos30min), FXUtils.df1));
        lowChannelPosPrevBar30min = channelPosition.bullishTriggerChannelStats(instrument, basicTimeFrame, OfferSide.BID, bidBar.getTime())[1];
		logLine.add(new FlexLogEntry("bullishTriggerLowChannelPos30min", new Double(lowChannelPosPrevBar30min), FXUtils.df1));

        double highChannelPos30min = 0, highChannelPosPrevBar30min = 0;
        highChannelPos30min = channelPosition.priceChannelPos(instrument, basicTimeFrame, OfferSide.BID, bidBar.getTime(), bidBar.getHigh());
		logLine.add(new FlexLogEntry("barHighChannelPos30min", new Double(highChannelPos30min), FXUtils.df1));
        highChannelPosPrevBar30min = channelPosition.bearishTriggerChannelStats(instrument, basicTimeFrame, OfferSide.BID, bidBar.getTime())[1];
		logLine.add(new FlexLogEntry("bearishTriggerHighChannelPos30min", new Double(highChannelPosPrevBar30min), FXUtils.df1));

		int barsAbove = channelPosition.consequitiveBarsAbove(instrument, basicTimeFrame, OfferSide.BID, bidBar.getTime(), 5);
		logLine.add(new FlexLogEntry("barsAboveChannel30min", new Integer(barsAbove), FXUtils.if3));
		int barsBelow = channelPosition.consequitiveBarsBelow(instrument, basicTimeFrame, OfferSide.BID, bidBar.getTime(), 5);
		logLine.add(new FlexLogEntry("barsBelowChannel30min", new Integer(barsBelow), FXUtils.if3));
		
        double lowChannelPos4h = 0, highChannelPos4h = 0;
        lowChannelPos4h = channelPosition.priceChannelPos(instrument, Period.FOUR_HOURS, OfferSide.BID, bidBar.getTime(), bidBar.getLow());
		logLine.add(new FlexLogEntry("barLowChannelPos4h", new Double(lowChannelPos4h), FXUtils.df1));
        highChannelPos4h = channelPosition.priceChannelPos(instrument, Period.FOUR_HOURS, OfferSide.BID, bidBar.getTime(), bidBar.getHigh());
		logLine.add(new FlexLogEntry("barHighChannelPos4h", new Double(highChannelPos4h), FXUtils.df1));

		logLine.add(new FlexLogEntry("barLowChannelPosOverlap", lowChannelPos30min <= 30 && lowChannelPos4h <= 30 ? "YES (" + FXUtils.df1.format(lowChannelPos30min) + "%/" + FXUtils.df1.format(lowChannelPos4h) + "%)": "no"));
		logLine.add(new FlexLogEntry("barHighChannelPosOverlap", highChannelPos30min >= 70 && highChannelPos4h >= 70 ? "YES (" + FXUtils.df1.format(highChannelPos30min) + "%/" + FXUtils.df1.format(highChannelPos4h) + "%)": "no"));

		long prev4hTime = history.getPreviousBarStart(Period.FOUR_HOURS, bidBar.getTime());
		barsAbove = channelPosition.consequitiveBarsAbove(instrument, Period.FOUR_HOURS, OfferSide.BID, prev4hTime, 5);
		logLine.add(new FlexLogEntry("barsAboveChannel4h", new Integer(barsAbove), FXUtils.if3));
		barsBelow = channelPosition.consequitiveBarsBelow(instrument, Period.FOUR_HOURS, OfferSide.BID, prev4hTime, 5);
		logLine.add(new FlexLogEntry("barsBelowChannel4h", new Integer(barsBelow), FXUtils.if3));
		double[] bBandsRaw4h = channelPosition.getRawBBandsData(instrument, Period.FOUR_HOURS, OfferSide.BID, prev4hTime);
		logLine.add(new FlexLogEntry("bBandsWidth4h", new Double((bBandsRaw4h[0] - bBandsRaw4h[2]) * Math.pow(10, instrument.getPipScale())), FXUtils.df1));
		
		long prevDayTime = history.getPreviousBarStart(Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
        double lowChannelPos1d = 0, highChannelPos1d = 0;
        lowChannelPos1d = channelPosition.priceChannelPos(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prevDayTime, bidBar.getLow());
		logLine.add(new FlexLogEntry("barLowChannelPos1d", new Double(lowChannelPos1d), FXUtils.df1));
        highChannelPos1d = channelPosition.priceChannelPos(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prevDayTime, bidBar.getHigh());
		logLine.add(new FlexLogEntry("barHighChannelPos1d", new Double(highChannelPos1d), FXUtils.df1));

		barsAbove = channelPosition.consequitiveBarsAbove(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prevDayTime, 5);
		logLine.add(new FlexLogEntry("barsAboveChannel1d", new Integer(barsAbove), FXUtils.if3));
		barsBelow = channelPosition.consequitiveBarsBelow(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prevDayTime, 5);
		logLine.add(new FlexLogEntry("barsBelowChannel1d", new Integer(barsBelow), FXUtils.if3));
		
		double[] bBandsRaw = channelPosition.getRawBBandsData(instrument, basicTimeFrame, OfferSide.BID, bidBar.getTime());
		logLine.add(new FlexLogEntry("barLow30min", new Double(bidBar.getLow()), FXUtils.df5));
		logLine.add(new FlexLogEntry("barHigh30min", new Double(bidBar.getHigh()), FXUtils.df5));
		logLine.add(new FlexLogEntry("bBandsTopBarHighDiff30min", new Double((bidBar.getHigh() - bBandsRaw[0]) * Math.pow(10, instrument.getPipScale())), FXUtils.df1));
		logLine.add(new FlexLogEntry("bBandsBottomBarLow30min", new Double((bBandsRaw[2] - bidBar.getLow()) * Math.pow(10, instrument.getPipScale())), FXUtils.df1));
		logLine.add(new FlexLogEntry("bBandsTopBarHighDiff30min", new Double((bidBar.getHigh() - bBandsRaw[0]) * Math.pow(10, instrument.getPipScale())), FXUtils.df1));
		logLine.add(new FlexLogEntry("bBandsWidth30min", new Double((bBandsRaw[0] - bBandsRaw[2]) * Math.pow(10, instrument.getPipScale())), FXUtils.df1));
	}

	protected void addCandles(Instrument instrument, IBar bidBar, Period period, List<FlexLogEntry> logLine) throws JFException {
        double bar30minStat = tradeTrigger.barLengthStatPos(instrument, basicTimeFrame, OfferSide.BID, bidBar, FXUtils.MONTH_WORTH_OF_30min_BARS);
        logLine.add(new FlexLogEntry("barStat30min", new Double(bar30minStat), FXUtils.df1));
        
        double bar30minOverlap = tradeTrigger.previousBarOverlap(instrument, basicTimeFrame, OfferSide.BID, bidBar.getTime());
        logLine.add(new FlexLogEntry("prevBarOverlap30min", new Double(bar30minOverlap), FXUtils.df1));
        
		String candleTrigger30minStr = new String();
		TradeTrigger.TriggerDesc 
			bullishTriggerDesc = null,
			bearishTriggerDesc = null;
        if ((bullishTriggerDesc = tradeTrigger.bullishReversalCandlePatternDesc(instrument, basicTimeFrame, OfferSide.BID, bidBar.getTime())) != null) {
        	candleTrigger30minStr += bullishTriggerDesc.type.toString();
            logLine.add(new FlexLogEntry("bullishCandleTriggerChannelPos30min", new Double(bullishTriggerDesc.channelPosition), FXUtils.df1));
            logLine.add(new FlexLogEntry("bullishPivotLevel30min", new Double(bullishTriggerDesc.pivotLevel), FXUtils.df5));
            logLine.add(new FlexLogEntry("bullishCandleTriggerKChannelPos30min", new Double(bullishTriggerDesc.keltnerChannelPosition), FXUtils.df1));
        }
        else {
            logLine.add(new FlexLogEntry("bullishCandleTriggerChannelPos30min", new Double(0), FXUtils.df1));
            logLine.add(new FlexLogEntry("bullishPivotLevel30min", new Double(0), FXUtils.df5));
            logLine.add(new FlexLogEntry("bullishCandleTriggerKChannelPos30min", new Double(0), FXUtils.df1));
        }
        if ((bearishTriggerDesc = tradeTrigger.bearishReversalCandlePatternDesc(instrument, basicTimeFrame, OfferSide.BID, bidBar.getTime())) != null) {
        	if (candleTrigger30minStr.length() > 0)
            	candleTrigger30minStr += " AND " + bearishTriggerDesc.type.toString();
        	else 
            	candleTrigger30minStr += bearishTriggerDesc.type.toString();
            logLine.add(new FlexLogEntry("bearishCandleTriggerChannelPos30min", new Double(bearishTriggerDesc.channelPosition), FXUtils.df1));
            logLine.add(new FlexLogEntry("bearishPivotLevel30min", new Double(bearishTriggerDesc.pivotLevel), FXUtils.df5));
            logLine.add(new FlexLogEntry("bearishCandleTriggerKChannelPos30min", new Double(bearishTriggerDesc.keltnerChannelPosition), FXUtils.df1));
        }
        else {
            logLine.add(new FlexLogEntry("bearishCandleTriggerChannelPos30min", new Double(0), FXUtils.df1));
            logLine.add(new FlexLogEntry("bearishPivotLevel30min", new Double(0), FXUtils.df5));
            logLine.add(new FlexLogEntry("bearishCandleTriggerKChannelPos30min", new Double(0), FXUtils.df1));
        }

        logLine.add(new FlexLogEntry("CandleTrigger30min", candleTrigger30minStr.length() > 0 ? candleTrigger30minStr : "none"));
		
        double bar4hStat = 0.0;
        String candleTrigger4hStr = new String();
        TradeTrigger.TriggerDesc  
	    	bearishCandleTriggerDesc4h = null, 
	    	bullishCandleTriggerDesc4h = null;        
        if (period.equals(Period.FOUR_HOURS)) {
        	bullishCandleTriggerDesc4h = tradeTrigger.bullishReversalCandlePatternDesc(instrument, Period.FOUR_HOURS, OfferSide.BID, bidBar.getTime());
            if (bullishCandleTriggerDesc4h != null) { 
            	candleTrigger4hStr += bullishCandleTriggerDesc4h.type.toString();
                logLine.add(new FlexLogEntry("bullishCandleTriggerChannelPos4h", new Double(bullishCandleTriggerDesc4h.channelPosition), FXUtils.df1));
                logLine.add(new FlexLogEntry("bullishPivotLevel4h", new Double(bullishCandleTriggerDesc4h.pivotLevel), FXUtils.df5));
            }
            else {
                logLine.add(new FlexLogEntry("bullishCandleTriggerChannelPos4h", new Double(0), FXUtils.df1));
                logLine.add(new FlexLogEntry("bullishPivotLevel4h", new Double(0), FXUtils.df5));
            }
            if ((bearishCandleTriggerDesc4h = tradeTrigger.bearishReversalCandlePatternDesc(instrument, Period.FOUR_HOURS, OfferSide.BID, bidBar.getTime())) != null) {
            	if (candleTrigger4hStr.length() > 0)
                	candleTrigger4hStr += " AND " + bearishCandleTriggerDesc4h.type.toString();
            	else 
                	candleTrigger4hStr += bearishCandleTriggerDesc4h.type.toString();
                logLine.add(new FlexLogEntry("bearishCandleTriggerChannelPos4h", new Double(bearishCandleTriggerDesc4h.channelPosition), FXUtils.df1));
                logLine.add(new FlexLogEntry("bearishPivotLevel4h", new Double(bearishCandleTriggerDesc4h.pivotLevel), FXUtils.df5));
            }
            else {
                logLine.add(new FlexLogEntry("bearishCandleTriggerChannelPos4h", new Double(0), FXUtils.df1));
                logLine.add(new FlexLogEntry("bearishPivotLevel4h", new Double(0), FXUtils.df5));
            }
            
            bar4hStat = tradeTrigger.barLengthStatPos(instrument, Period.FOUR_HOURS, OfferSide.BID, bidBar, FXUtils.QUARTER_WORTH_OF_4h_BARS);
            logLine.add(new FlexLogEntry("CandleTrigger4h", candleTrigger4hStr.length() > 0 ? candleTrigger4hStr : "none"));
            logLine.add(new FlexLogEntry("barStat4h", new Double(bar4hStat), FXUtils.df1));
        }
        else {
    		logLine.add(new FlexLogEntry("CandleTrigger4h", "n/a"));
            logLine.add(new FlexLogEntry("barStat4h", ""));
        }
	}

	private void addADXStats(Instrument instrument, IBar bidBar, Period period,	List<FlexLogEntry> logLine) throws JFException {
		logLine.add(new FlexLogEntry("ADX30min", new Double(momentum.getADXs(instrument, basicTimeFrame, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime())[0]), FXUtils.df1));
		logLine.add(new FlexLogEntry("DI_PLUS30min", new Double(indicators.plusDi(instrument, basicTimeFrame, OfferSide.BID, 14, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]), FXUtils.df1));
		logLine.add(new FlexLogEntry("DI_MINUS30min", new Double(indicators.minusDi(instrument, basicTimeFrame, OfferSide.BID, 14, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]), FXUtils.df1));
	
		logLine.add(new FlexLogEntry("ADX4h", new Double(momentum.getADXs(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime())[0]), FXUtils.df1));
		logLine.add(new FlexLogEntry("DI_PLUS4h", new Double(indicators.plusDi(instrument, Period.FOUR_HOURS, OfferSide.BID, 14, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]), FXUtils.df1));
		logLine.add(new FlexLogEntry("DI_MINUS4h", new Double(indicators.minusDi(instrument, Period.FOUR_HOURS, OfferSide.BID, 14, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]), FXUtils.df1));
		
		long prevDayTime = history.getPreviousBarStart(Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		logLine.add(new FlexLogEntry("ADX1d", new Double(momentum.getADXs(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, AppliedPrice.CLOSE, prevDayTime)[0]), FXUtils.df1));
		logLine.add(new FlexLogEntry("DI_PLUS1d", new Double(indicators.plusDi(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, 14, Filter.WEEKENDS, 1, prevDayTime, 0)[0]), FXUtils.df1));
		logLine.add(new FlexLogEntry("DI_MINUS1d", new Double(indicators.minusDi(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, 14, Filter.WEEKENDS, 1, prevDayTime, 0)[0]), FXUtils.df1));
		
	}

	private void addCCIStats(Instrument instrument, IBar bidBar, Period period,	List<FlexLogEntry> logLine) throws JFException {
		logLine.add(new FlexLogEntry("CCI30min", new Double(indicators.cci(instrument, basicTimeFrame, OfferSide.BID, 14, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]), FXUtils.df1));
		logLine.add(new FlexLogEntry("CCI4h", new Double(indicators.cci(instrument, Period.FOUR_HOURS, OfferSide.BID, 14, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]), FXUtils.df1));
		
		long prevDayTime = history.getPreviousBarStart(Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		logLine.add(new FlexLogEntry("CCI1d", new Double(indicators.cci(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, 14, Filter.WEEKENDS, 1, prevDayTime, 0)[0]), FXUtils.df1));
		
		logLine.add(new FlexLogEntry("CCIState4h", new String(momentum.getCCIState(instrument, Period.FOUR_HOURS, OfferSide.BID, bidBar.getTime()).toString())));
		
	}
	
	@Override
	public void onMessage(IMessage message) throws JFException { }

	@Override
	public void onAccount(IAccount account) throws JFException { }

	@Override
	public void onStop() throws JFException {
		log.close();
	}

	@Override
	protected String getStrategyName() {
		return "OvernightStatsCollector";
	}

	@Override
	protected String getReportFileName() {
		return "OvernightStatsCollector_";
	}

	protected String createReportBody4h(Instrument instrument, Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine) throws JFException {
		
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}
		String mailBody = new String();
		
		mailBody = "\n-------------------------\nReport for " 
			+ instrument.toString() + ", " 
			+ FXUtils.getFormatedTimeCET(bidBar.getTime()) 
			+ " CET (time frame: " + pPeriod.toString() 
			+ ")\n-------------------------\n\n";
		
		if (nextSetups.containsKey(instrument.toString())) {
			mailBody += "Next recommended setup: " + nextSetups.get(instrument.toString()) + "\n\n";
		}
	
		double roc1 = indicators.roc(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, 1, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0];
		mailBody += "4h price change: " + FXUtils.df1.format(roc1 * 100) + " pips\n";
		
		String valueToShow = mailStringsMap.get("CandleTrigger4h");
		String candles = new String();
		if (valueToShow != null && !valueToShow.toUpperCase().equals("NONE") && !valueToShow.toLowerCase().equals("n/a")) {			
			candles = "4h: " + valueToShow + "\n(bar StDev size: " + mailStringsMap.get("barStat4h") + ", ";
			if (valueToShow.contains("BULLISH")) {
				candles += "pivot bar low 4h channel position: " + mailStringsMap.get("bullishCandleTriggerChannelPos4h") + "%";
	
				double  
					barHigh = bidBar.getHigh(),
					barLow = bidBar.getLow(),
					barHalf = barLow + (barHigh - barLow) / 2;

				Double 
					atr4hPlus20Perc = (Double)mailValuesMap.get("ATR30min + 20%").getValue(),
					bullishPivotLevel = (Double)mailValuesMap.get("bullishPivotLevel4h").getValue(),
					aggressiveSL = new Double(bullishPivotLevel.doubleValue() - atr4hPlus20Perc.doubleValue() / Math.pow(10, instrument.getPipScale())),
					riskStandard = new Double((barHigh - bullishPivotLevel) * Math.pow(10, instrument.getPipScale())),
					riskAggressive = new Double((barHigh - aggressiveSL) * Math.pow(10, instrument.getPipScale())),
					riskStandard2 = new Double((barHalf - bullishPivotLevel) * Math.pow(10, instrument.getPipScale())),
					riskAggressive2 = new Double((barHalf - aggressiveSL) * Math.pow(10, instrument.getPipScale()));
				candles += "\nBUY STP: " + FXUtils.df5.format(barHigh) 
					+ " (risks " + FXUtils.df1.format(riskStandard.doubleValue()) + "/" + FXUtils.df1.format(riskAggressive.doubleValue()) 
					+ ")\nBUY LMT: " + FXUtils.df5.format(barHalf)  
					+ " (risks " + FXUtils.df1.format(riskStandard2.doubleValue()) + "/" + FXUtils.df1.format(riskAggressive2.doubleValue()) 
					+ ")\nSL: " + mailStringsMap.get("bullishPivotLevel4h")
					+ " (aggressive: " + FXUtils.df5.format(aggressiveSL.doubleValue()) + ")";
			}
			if (valueToShow.contains(" AND "))
				candles += "\n\n";
			if (valueToShow.contains("BEARISH")) {
				candles += "pivot bar high 4h channel position: " + mailStringsMap.get("bearishCandleTriggerChannelPos4h") + "%";
	
				double  
					barHigh = bidBar.getHigh(),
					barLow = bidBar.getLow(),
					barHalf = barLow + (barHigh - barLow) / 2;

				Double 
					atr30minPlus20Perc = (Double)mailValuesMap.get("ATR30min + 20%").getValue(),
					bearishPivotLevel = (Double)mailValuesMap.get("bearishPivotLevel4h").getValue(),
					aggressiveSL = new Double(bearishPivotLevel.doubleValue() + atr30minPlus20Perc.doubleValue() / Math.pow(10, instrument.getPipScale())),
					riskStandard = new Double((bearishPivotLevel - barLow) * Math.pow(10, instrument.getPipScale())),
					riskAggressive = new Double((aggressiveSL - barLow) * Math.pow(10, instrument.getPipScale())),
					riskStandard2 = new Double((bearishPivotLevel - barHalf) * Math.pow(10, instrument.getPipScale())),
					riskAggressive2 = new Double((aggressiveSL - barHalf) * Math.pow(10, instrument.getPipScale()));
				candles += "\nSELL STP: " + FXUtils.df5.format(barLow)
					+ " (risks " + FXUtils.df1.format(riskStandard.doubleValue()) + "/" + FXUtils.df1.format(riskAggressive.doubleValue()) 
					+ ")\nSELL LMT: " + FXUtils.df5.format(barLow + (barHigh - barLow) / 2)  
					+ " (risks " + FXUtils.df1.format(riskStandard2.doubleValue()) + "/" + FXUtils.df1.format(riskAggressive2.doubleValue()) 
					+ ")\nSL: " + mailStringsMap.get("bearishPivotLevel4h") 
					+ " (aggressive: " + FXUtils.df5.format(aggressiveSL.doubleValue()) + ")";
			}
			candles += "\n" + checkTALevels(instrument, pPeriod, bidBar, valueToShow) + "\n";			
						
			Double trendStrength4h = (Double)mailValuesMap.get("MAsDistance4h").getValue();
			if (trendStrength4h.doubleValue() < -0.7) 
				candles += "\n\n4h trend: FLAT (Strength: " + mailStringsMap.get("MAsDistance4h") + "); TrendID: " + mailStringsMap.get("TrendId4h");  			
			else
				candles += "\n\n4h trend: " + mailStringsMap.get("TrendId4h") + " (" + mailStringsMap.get("MAsDistance4h") + ")";
	
			candles += "\n4h momentum: MACDState " + mailStringsMap.get("MACDState4h")  
					+  " / MACDHState " + mailStringsMap.get("MACDHState4h")  
					+ " / StochState " + mailStringsMap.get("StochState4h");
			if (mailStringsMap.get("StochState4h").contains("OVER")) {
				Double stochFast4h = (Double)mailValuesMap.get("StochFast4h").getValue();
				Double stochSlow4h = (Double)mailValuesMap.get("StochSlow4h").getValue();
				String stochsToShow = FXUtils.df1.format(stochFast4h) + "/" + FXUtils.df1.format(stochSlow4h);
				if (stochFast4h > stochSlow4h)
					candles  += " and RAISING (" + stochsToShow + " --> difference " + FXUtils.df1.format(stochFast4h - stochSlow4h) + ")";
				else 
					candles += " and FALLING (" + stochsToShow + " --> difference " + FXUtils.df1.format(stochFast4h - stochSlow4h) + ")";
			}
			
			candles += "\nvolatility4h (BBands squeeze): " + mailStringsMap.get("volatility4h") + "%\n";
			candles += "volatility30min (BBands squeeze): " + mailStringsMap.get("volatility30min") + "%\n";
		}
		else { 
			mailBody += "\n" + checkTALevels(instrument, pPeriod, bidBar, "BULLISH_1_BAR") + "\n";			
		}

		if (candles.length() > 0) 
			mailBody += "\nCandles:\n" + candles;
		
		// other COMBINED signals
		if (((Double)mailValuesMap.get("barLowChannelPos4h").getValue()).doubleValue() < 50.0)
		{
			boolean additionalSignal = false;
			if (mailStringsMap.get("MACDHState4h").equals("TICKED_UP_BELOW_ZERO")) {
				mailBody += "\nAdditional signals: MACDHistogram 4h " + mailStringsMap.get("MACDHState4h");
				additionalSignal = true;
			}
			if (momentum.getStochCross(instrument, pPeriod, OfferSide.BID, bidBar.getTime()).toString().contains("BULLISH_CROSS")) {
				mailBody += "\nAdditional signals: Stoch 4h BULLISH CROSS from oversold";
				additionalSignal = true;				
			}
			if (momentum.getRSIState(instrument, pPeriod, OfferSide.BID, bidBar.getTime()).toString().contains("TICKED_UP_FROM_OVERSOLD")) {
				mailBody += "\nAdditional signals: RSI 4h TICKED UP from oversold";
				additionalSignal = true;				
			}
			if (additionalSignal) {					
				mailBody += "\nand bar low 4h channelPos " + mailStringsMap.get("barLowChannelPos4h") + "%\n";
				IBar prevBar = history.getBar(instrument, pPeriod, OfferSide.BID, 2);
	
				double  
					barHigh = bidBar.getHigh(),
					barLow = bidBar.getLow(),
					barHalf = barLow + (barHigh - barLow) / 2;
	
				Double 
					atr30minPlus20Perc = (Double)mailValuesMap.get("ATR30min + 20%").getValue(),
					bullishPivotLevel = new Double(barLow < prevBar.getLow() ? barLow : prevBar.getLow()),
					aggressiveSL = new Double(bullishPivotLevel.doubleValue() - atr30minPlus20Perc.doubleValue() / Math.pow(10, instrument.getPipScale())),
					riskStandard = new Double((barHigh - bullishPivotLevel) * Math.pow(10, instrument.getPipScale())),
					riskAggressive = new Double((barHigh - aggressiveSL) * Math.pow(10, instrument.getPipScale())),
					riskStandard2 = new Double((barHalf - bullishPivotLevel) * Math.pow(10, instrument.getPipScale())),
					riskAggressive2 = new Double((barHalf - aggressiveSL) * Math.pow(10, instrument.getPipScale()));
				mailBody += "\nBUY STP: " + FXUtils.df5.format(barHigh)
					+ " (risks " + FXUtils.df1.format(riskStandard.doubleValue()) + "/" + FXUtils.df1.format(riskAggressive.doubleValue()) 
					+ ")\nBUY LMT: " + FXUtils.df5.format(barHalf)  
					+ " (risks " + FXUtils.df1.format(riskStandard2.doubleValue()) + "/" + FXUtils.df1.format(riskAggressive2.doubleValue()) 
					+ ")\nSL: " + FXUtils.df5.format(bullishPivotLevel)
					+ " (aggressive: " + FXUtils.df5.format(aggressiveSL.doubleValue()) + ")\n";
			}
		}
		else if (((Double)mailValuesMap.get("barHighChannelPos4h").getValue()).doubleValue() > 50.0)
		{
			boolean additionalSignal = false;
			if (mailStringsMap.get("MACDHState4h").equals("TICKED_DOWN_ABOVE_ZERO")) {
				mailBody += "\nAdditional signals: MACDHistogram 4h " + mailStringsMap.get("MACDHState4h");
				additionalSignal = true;
			}
			if (momentum.getStochCross(instrument, pPeriod, OfferSide.BID, bidBar.getTime()).toString().contains("BEARISH_CROSS")) {
				mailBody += "\nAdditional signals: Stoch 4h BEARISH CROSS from overbought";
				additionalSignal = true;				
			}
			if (momentum.getRSIState(instrument, pPeriod, OfferSide.BID, bidBar.getTime()).toString().equals("TICKED_DOWN_FROM_OVERBOUGHT")) {
				mailBody += "\nAdditional signals: RSI 4h TICKED DOWN from overbought";
				additionalSignal = true;				
			}
			
			if (additionalSignal) {
				mailBody += "\nand bar high 4h channelPos " + mailStringsMap.get("barHighChannelPos4h")  + "%\n";
				IBar prevBar = history.getBar(instrument, pPeriod, OfferSide.BID, 2);
				double  
					barHigh = bidBar.getHigh(),
					barLow = bidBar.getLow(),
					barHalf = barLow + (barHigh - barLow) / 2;
				Double
					atr30minPlus20Perc = (Double)mailValuesMap.get("ATR30min + 20%").getValue(),
					bearishPivotLevel = new Double(barHigh > prevBar.getHigh() ? barHigh : prevBar.getHigh()),
					aggressiveSL = new Double(bearishPivotLevel.doubleValue() + atr30minPlus20Perc.doubleValue() / Math.pow(10, instrument.getPipScale())),
					riskStandard = new Double((bearishPivotLevel - barLow) * Math.pow(10, instrument.getPipScale())),
					riskAggressive = new Double((aggressiveSL - barLow) * Math.pow(10, instrument.getPipScale())),
					riskStandard2 = new Double((bearishPivotLevel - barHalf) * Math.pow(10, instrument.getPipScale())),
					riskAggressive2 = new Double((aggressiveSL - barHalf) * Math.pow(10, instrument.getPipScale()));
				mailBody += "\nSELL STP: " + FXUtils.df5.format(barLow) 
					+ " (risks " + FXUtils.df1.format(riskStandard.doubleValue()) + "/" + FXUtils.df1.format(riskAggressive.doubleValue()) 
					+ ")\nSELL LMT: " + FXUtils.df5.format(barLow + (barHigh - barLow) / 2)  
					+ " (risks " + FXUtils.df1.format(riskStandard2.doubleValue()) + "/" + FXUtils.df1.format(riskAggressive2.doubleValue()) 
					+ ")\nSL: " + FXUtils.df5.format(bearishPivotLevel) 
					+ " (aggressive: " + FXUtils.df5.format(aggressiveSL.doubleValue()) + ")\n";
			}
		}
		
		if (!mailStringsMap.get("MACDCross4h").equals("NONE")) {
			mailBody += "\nMACD4h cross ! " + mailStringsMap.get("MACDCross4h");
			mailBody += "\n\n";
		}
			
		// 4h regime
		mailBody += "\n\n-------------------------\n4h TIMEFRAME EVENTS:\n-------------------------\n";
			
		if (mailStringsMap.get("RSIState4h").contains("TICKED_UP_FROM") || mailStringsMap.get("RSIState4h").contains("TICKED_DOWN_FROM"))
			mailBody += "\nRSI4h: " + mailStringsMap.get("RSI4h") + " (" + mailStringsMap.get("RSIState4h") + ")"; 
		if (mailStringsMap.get("CCIState4h").contains("TICKED_UP_FROM") || mailStringsMap.get("CCIState4h").contains("TICKED_DOWN_FROM"))
			mailBody += "\nCCI4h: " + mailStringsMap.get("CCI4h") + " (" + mailStringsMap.get("CCIState4h") + ")";
			
		return mailBody;
	}
}
