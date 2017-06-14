package jforex.techanalysis;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.stat.ranking.NaturalRanking;

import jforex.utils.FXUtils;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.IIndicators.MaType;

public class Trend {

	public enum TREND_STATE {
		UP_STRONG, UP_MILD, FRESH_DOWN, FRESH_UP, DOWN_MILD, DOWN_STRONG, NONE // shouldn't happen, for debugging
	}

	public enum ICHI_CLOUD_CROSS {
		BULLISH, BEARISH, NONE
	}

	public enum FLAT_REGIME_CAUSE {
		MAs_WITHIN_CHANNEL, MAs_CLOSE, NONE
	}

	public class IchiDesc {
		public double 
			widthPips, widthToATR, widthRel,
			cloudTop, cloudBottom, prevCloudTop, prevCloudBottom,
			fastLine, slowLine;
		public boolean isBullishSlowLineCross = false,
				isBearishSlowLineCross = false,

				// needed for cases when lines turn adverse (to trade) AFTER
				// cross of slow line
				isCloseAboveSlowLine = false,

				isBullishCloudCross = false, isBearishCloudCross = false,

				isBullishCloud,

				isBullishTenkanLine;
		public String slowLineState, fastLineState, bottomBorderDirection,
				topBorderDirection;

		public String linesState() {
			if (slowLineState.equals("FLAT") && fastLineState.equals("FLAT"))
				return "FLAT";
			else if ((slowLineState.equals("FALLING")
					|| slowLineState.equals("TICKED_DOWN") || slowLineState
						.equals("FLAT"))
					&& !fastLineState.equals("RAISING")
					&& !fastLineState.equals("TICKED_UP"))
				return "BEARISH";
			else if ((slowLineState.equals("RAISING")
					|| slowLineState.equals("TICKED_UP") || slowLineState
						.equals("FLAT"))
					&& !fastLineState.equals("FALLING")
					&& !fastLineState.equals("TICKED_DOWN"))
				return "BULLISH";
			else
				return "CONTRADICTORY";
		}
	}

	protected int TENKAN = 0, KIJUN = 1, SENOKU_A = 3, SENOKU_B = 4,
			CLOUD_A = 5, CLOUD_B = 6, tenkan = 9, kijun = 26, senkou = 52;

	private IIndicators indicators;

	public Trend(IIndicators pIndicators) {
		this.indicators = pIndicators;
	}

	// current trend definition with Up = MA50 > MA100 && MA20 > MA100 and Down
	// = MA50 < MA100 && MA20 < MA100
	// probably too simplistic, especially in 4h uptrend clearly also when MA100
	// > MA50;
	// TODO: better study role of MA200, it's position versus all others...
	public boolean isStrongUpTrendBy3MAs(Instrument instrument, Period pPeriod,
			OfferSide side, IIndicators.AppliedPrice appliedPrice, long time,
			int lookback, double stDevFilter) throws JFException {
		double MA20 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				20, Filter.WEEKENDS, 1, time, 0)[0];
		double MA50 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				50, Filter.WEEKENDS, 1, time, 0)[0];
		double MA100 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				100, Filter.WEEKENDS, 1, time, 0)[0];

		if (MA50 > MA100 && MA20 > MA100) {
			// check that MAs difference is WELL within first stdev i.e. greater
			// then mean - 0.7 stdev
			// (subject to stat analysis)
			return getUptrendMAsMaxDifStDevPos(instrument, pPeriod, side,
					appliedPrice, time, lookback) > stDevFilter;
		} else {
			return false;
		}

	}

	/**
	 * @return 1 - strong downtrend, MAs in descending order from slowest to
	 *         fastest 2 - weak downtrend, top-bottom order of MAs MA100, MA20,
	 *         MA50, 3 - downtrend / flat, top-bottom order of MAs MA20, MA100,
	 *         MA50 4 - uptrend / flat, top-bottom order of MAs MA50, MA100,
	 *         MA20 5 - weak uptrend, top-bottom order of MAs MA50, MA20, MA100
	 *         6 - strong uptrend, top-bottom order of MAs MA20, MA50, MA100
	 * @throws JFException
	 */
	public int getTrendId(Instrument instrument, Period pPeriod,
			OfferSide side, IIndicators.AppliedPrice appliedPrice, long time)
			throws JFException {
		double MA20 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				20, Filter.WEEKENDS, 1, time, 0)[0];
		double MA50 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				50, Filter.WEEKENDS, 1, time, 0)[0];
		double MA100 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				100, Filter.WEEKENDS, 1, time, 0)[0];

		if (MA20 > MA50 && MA20 > MA100 && MA50 > MA100)
			return 6;
		else if (MA20 <= MA50 && MA20 > MA100 && MA50 > MA100)
			return 5;
		else if (MA20 <= MA100 && MA50 > MA100)
			return 4;
		else if (MA20 > MA100 && MA100 > MA50)
			return 3;
		else if (MA20 < MA100 && MA20 > MA50 && MA100 > MA50)
			return 2;
		else if (MA20 < MA100 && MA20 < MA50 && MA100 > MA50)
			return 1;
		else
			return 0;
	}

	/**
	 * @return 1 - strong downtrend, MAs in descending order from slowest to
	 *         fastest 2 - weak downtrend, top-bottom order of MAs MA100, MA20,
	 *         MA50, 3 - downtrend / flat, top-bottom order of MAs MA20, MA100,
	 *         MA50 4 - uptrend / flat, top-bottom order of MAs MA50, MA100,
	 *         MA20 5 - weak uptrend, top-bottom order of MAs MA50, MA20, MA100
	 *         6 - strong uptrend, top-bottom order of MAs MA20, MA50, MA100
	 * @throws JFException
	 */
	public TREND_STATE getTrendState(Instrument instrument, Period pPeriod, Filter filter, OfferSide side, IIndicators.AppliedPrice appliedPrice, long time) throws JFException {
		double MA20 = indicators.sma(instrument, pPeriod, side, appliedPrice, 20, filter, 1, time, 0)[0];
		double MA50 = indicators.sma(instrument, pPeriod, side, appliedPrice, 50, filter, 1, time, 0)[0];
		double MA100 = indicators.sma(instrument, pPeriod, side, appliedPrice, 100, filter, 1, time, 0)[0];

		if (MA20 > MA50 && MA20 > MA100 && MA50 > MA100)
			return TREND_STATE.UP_STRONG;
		else if (MA20 <= MA50 && MA20 > MA100 && MA50 > MA100)
			return TREND_STATE.UP_MILD;
		else if (MA20 <= MA100 && MA50 > MA100)
			return TREND_STATE.FRESH_DOWN;
		else if (MA20 > MA100 && MA100 > MA50)
			return TREND_STATE.FRESH_UP;
		else if (MA20 < MA100 && MA20 > MA50 && MA100 > MA50)
			return TREND_STATE.DOWN_MILD;
		else if (MA20 < MA100 && MA20 < MA50 && MA100 > MA50)
			return TREND_STATE.DOWN_STRONG;
		else
			return TREND_STATE.NONE;
	}

	public boolean isMA200Lowest(Instrument instrument, Period pPeriod,	Filter filter, OfferSide side, IIndicators.AppliedPrice appliedPrice, long time)
			throws JFException {
		double MA20 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				20, filter, 1, time, 0)[0];
		double MA50 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				50, filter, 1, time, 0)[0];
		double MA100 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				100, filter, 1, time, 0)[0];
		double MA200 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				200, filter, 1, time, 0)[0];

		return MA200 < MA20 && MA200 < MA50 && MA200 < MA100;
	}
	
	public boolean isMA200Lowest(Instrument instrument, Period pPeriod,	OfferSide side, IIndicators.AppliedPrice appliedPrice, long time) throws JFException {
		return isMA200Lowest(instrument, pPeriod, Filter.WEEKENDS, side, appliedPrice, time);
	}


	public boolean isMA200Highest(Instrument instrument, Period pPeriod, Filter filter,	OfferSide side, IIndicators.AppliedPrice appliedPrice, long time) throws JFException {
		double MA20 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				20, filter, 1, time, 0)[0];
		double MA50 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				50, filter, 1, time, 0)[0];
		double MA100 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				100, filter, 1, time, 0)[0];
		double MA200 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				200, filter, 1, time, 0)[0];

		return MA200 > MA20 && MA200 > MA50 && MA200 > MA100;
	}
	
	public boolean isMA200Highest(Instrument instrument, Period pPeriod, OfferSide side, IIndicators.AppliedPrice appliedPrice, long time) throws JFException {
		return isMA200Highest(instrument, pPeriod, Filter.WEEKENDS, side, appliedPrice, time);
	}


	public boolean isBarHighAboveAllMAs(Instrument instrument, Period pPeriod,
			OfferSide side, IIndicators.AppliedPrice appliedPrice, IBar bar)
			throws JFException {
		double MA20 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				20, Filter.WEEKENDS, 1, bar.getTime(), 0)[0];
		double MA50 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				50, Filter.WEEKENDS, 1, bar.getTime(), 0)[0];
		double MA100 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				100, Filter.WEEKENDS, 1, bar.getTime(), 0)[0];

		return bar.getHigh() > MA20 && bar.getHigh() > MA50
				&& bar.getHigh() > MA100;
	}

	public boolean isBarLowBelowAllMAs(Instrument instrument, Period pPeriod,
			OfferSide side, IIndicators.AppliedPrice appliedPrice, IBar bar)
			throws JFException {
		double MA20 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				20, Filter.WEEKENDS, 1, bar.getTime(), 0)[0];
		double MA50 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				50, Filter.WEEKENDS, 1, bar.getTime(), 0)[0];
		double MA100 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				100, Filter.WEEKENDS, 1, bar.getTime(), 0)[0];

		return bar.getLow() < MA20 && bar.getLow() < MA50
				&& bar.getLow() < MA100;
	}

	/*
	 * Algorhytm: 1. fetch three MAs for desired lookback period (for 30' 1000
	 * is a month) 2. iterate over all three arrays at the same time 3. for each
	 * index value: 3.1 check if MAs in uptrend position. If not skip loop 3.2
	 * find greatest difference between values 3.3 put it into result list 4.
	 * convert list to array (less elements then lookback period !) 5. calculate
	 * stdev using result array as input
	 * 
	 * First return element is mean and 2nd stdev MAs should be put in a queue
	 * so for each bar only ONE more set of values are fetched, not each time
	 * all 1'000 ! Just first time at start !
	 */
	private double[] uptrendMAsDifferenceStDevStats(Instrument instrument,
			Period pPeriod, OfferSide side,
			IIndicators.AppliedPrice appliedPrice, long time, int lookBack)
			throws JFException {
		double[] result = getRawUptrendMAsDifferences(instrument, pPeriod,
				side, appliedPrice, time, lookBack);
		return FXUtils.sdFast(result);
	}

	protected double[] getRawUptrendMAsDifferences(Instrument instrument, Period pPeriod, Filter filter, OfferSide side, IIndicators.AppliedPrice appliedPrice, long time, int lookBack) throws JFException {
		double[] MAs20 = indicators.sma(instrument, pPeriod, side, appliedPrice, 20, filter, lookBack, time, 0);
		double[] MAs50 = indicators.sma(instrument, pPeriod, side, appliedPrice, 50, filter, lookBack, time, 0);
		double[] MAs100 = indicators.sma(instrument, pPeriod, side,	appliedPrice, 100, filter, lookBack, time, 0);

		List<Double> upTrendValues = new ArrayList<Double>();
		for (int i = 0; i < MAs20.length; i++) {
			if (MAs50[i] > MAs100[i] && MAs20[i] > MAs100[i]) {
				upTrendValues.add(new Double(maxMAsDifference(MAs20[i],	MAs50[i], MAs100[i])));
			}
		}
		double[] result = new double[upTrendValues.size()];
		int pos = 0;
		for (Double d : upTrendValues) {
			result[pos++] = d.doubleValue();
		}
		return result;
	}
	
	protected double[] getRawUptrendMAsDifferences(Instrument instrument, Period pPeriod, OfferSide side, IIndicators.AppliedPrice appliedPrice, long time, int lookBack) throws JFException {
		return getRawUptrendMAsDifferences(instrument, pPeriod, Filter.WEEKENDS, side, appliedPrice, time, lookBack);
	}


	public double getUptrendMAsMaxDifStDevPos(Instrument instrument,
			Period pPeriod, OfferSide side,
			IIndicators.AppliedPrice appliedPrice, long time, int lookback)
			throws JFException {
		double MA20 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				20, Filter.WEEKENDS, 1, time, 0)[0];
		double MA50 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				50, Filter.WEEKENDS, 1, time, 0)[0];
		double MA100 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				100, Filter.WEEKENDS, 1, time, 0)[0];

		if (MA50 > MA100 && MA20 > MA100) {
			double[] stDevStats = uptrendMAsDifferenceStDevStats(instrument,
					pPeriod, side, appliedPrice, time, lookback);
			return (maxMAsDifference(MA20, MA50, MA100) - stDevStats[0])
					/ stDevStats[1];
		} else {
			return -1000.0;
		}
	}

	public double maxMAsDifference(double MA20, double MA50, double MA100) {
		double maxMA = 0, minMA = 0;
		// find max
		if (MA20 > MA50 && MA20 > MA100)
			maxMA = MA20;
		else if (MA50 > MA100)
			maxMA = MA50;
		else
			maxMA = MA100;
		// find min
		if (MA20 < MA50 && MA20 < MA100)
			minMA = MA20;
		else if (MA50 < MA100)
			minMA = MA50;
		else
			minMA = MA100;
		return maxMA - minMA;
	}

	public boolean isStrongDownTrendBy3MAs(Instrument instrument,
			Period pPeriod, OfferSide side,
			IIndicators.AppliedPrice appliedPrice, long time, int lookback,
			double stDevFilter) throws JFException {
		double MA20 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				20, Filter.WEEKENDS, 1, time, 0)[0];
		double MA50 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				50, Filter.WEEKENDS, 1, time, 0)[0];
		double MA100 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				100, Filter.WEEKENDS, 1, time, 0)[0];

		if (MA50 < MA100 && MA20 < MA100) {
			return getDowntrendMAsMaxDifStDevPos(instrument, pPeriod, side,
					appliedPrice, time, lookback) > stDevFilter;
		} else {
			return false;
		}

	}

	/*
	 * Algorhytm: 1. fetch three MAs for desired lookback period (for 30' 1000
	 * is a month) 2. iterate over all three arrays at the same time 3. for each
	 * index value: 3.1 check if MAs in uptrend position. If not skip loop 3.2
	 * find greatest difference between values 3.3 put it into result list 4.
	 * convert list to array (less elements then lookback period !) 5. calculate
	 * stdev using result array as input
	 * 
	 * First return element is mean and 2nd stdev MAs should be put in a queue
	 * so for each bar only ONE more set of values are fetched, not each time
	 * all 1'000 ! Just first time at start !
	 */
	private double[] downtrendMAsDifferenceStDevStats(Instrument instrument,
			Period pPeriod, OfferSide side,
			IIndicators.AppliedPrice appliedPrice, long time, int lookBack)
			throws JFException {
		double[] result = getRawDowntrendMAsDifferences(instrument, pPeriod,
				side, appliedPrice, time, lookBack);
		return FXUtils.sdFast(result);
	}

	protected double[] getRawDowntrendMAsDifferences(Instrument instrument,	Period pPeriod, Filter filter, OfferSide side,	IIndicators.AppliedPrice appliedPrice, long time, int lookBack)	throws JFException {
		double[] MAs20 = indicators.sma(instrument, pPeriod, side,
				appliedPrice, 20, filter, lookBack, time, 0);
		double[] MAs50 = indicators.sma(instrument, pPeriod, side,
				appliedPrice, 50, filter, lookBack, time, 0);
		double[] MAs100 = indicators.sma(instrument, pPeriod, side,
				appliedPrice, 100, filter, lookBack, time, 0);

		List<Double> downTrendValues = new ArrayList<Double>();
		for (int i = 0; i < MAs20.length; i++) {
			if (MAs50[i] < MAs100[i] && MAs20[i] < MAs100[i]) {
				downTrendValues.add(new Double(maxMAsDifference(MAs20[i],
						MAs50[i], MAs100[i])));
			}
		}
		double[] result = new double[downTrendValues.size()];
		int pos = 0;
		for (Double d : downTrendValues) {
			result[pos++] = d.doubleValue();
		}
		return result;
	}
	
	protected double[] getRawDowntrendMAsDifferences(Instrument instrument,	Period pPeriod, OfferSide side,	IIndicators.AppliedPrice appliedPrice, long time, int lookBack)	throws JFException {
		return getRawDowntrendMAsDifferences(instrument, pPeriod, Filter.WEEKENDS, side, appliedPrice, time, lookBack);
	}

	public double getDowntrendMAsMaxDifStDevPos(Instrument instrument,
			Period pPeriod, OfferSide side,
			IIndicators.AppliedPrice appliedPrice, long time, int lookback)
			throws JFException {
		double MA20 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				20, Filter.WEEKENDS, 1, time, 0)[0];
		double MA50 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				50, Filter.WEEKENDS, 1, time, 0)[0];
		double MA100 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				100, Filter.WEEKENDS, 1, time, 0)[0];

		if (MA50 < MA100 && MA20 < MA100) {
			double[] stDevStats = downtrendMAsDifferenceStDevStats(instrument,
					pPeriod, side, appliedPrice, time, lookback);
			return (maxMAsDifference(MA20, MA50, MA100) - stDevStats[0])
					/ stDevStats[1];
		} else {
			return -1000.0;
		}
	}

	/**
	 * calculates mean and StDev for ALL max MAs difference over the lookback
	 * period regardless of the trend
	 * 
	 * @return
	 */
	public double[] MAsDifferenceStDevStats(Instrument instrument,
			Period pPeriod, OfferSide side,
			IIndicators.AppliedPrice appliedPrice, long time, int lookBack)
			throws JFException {
		double[] result = getRawMAsDifferences(instrument, pPeriod, side,
				appliedPrice, time, lookBack);
		return FXUtils.sdFast(result);
	}

	protected double[] getRawMAsDifferences(Instrument instrument, Period pPeriod, Filter filter, OfferSide side, IIndicators.AppliedPrice appliedPrice, long time, int lookBack) throws JFException {
		double[] MAs20 = indicators.sma(instrument, pPeriod, side, appliedPrice, 20, filter, lookBack, time, 0);
		double[] MAs50 = indicators.sma(instrument, pPeriod, side, appliedPrice, 50, filter, lookBack, time, 0);
		double[] MAs100 = indicators.sma(instrument, pPeriod, side,	appliedPrice, 100, filter, lookBack, time, 0);

		double[] result = new double[MAs20.length];
		for (int i = 0; i < MAs20.length; i++) {
			result[i] = maxMAsDifference(MAs20[i], MAs50[i], MAs100[i]);
		}
		return result;
	}
	
	protected double[] getMA200MA100TrendDifferences(Instrument instrument, Period pPeriod, Filter filter, OfferSide side, IIndicators.AppliedPrice appliedPrice, long time, int lookBack) throws JFException {
//		double[] MAs20 = indicators.sma(instrument, pPeriod, side, appliedPrice, 20, filter, lookBack, time, 0);
//		double[] MAs50 = indicators.sma(instrument, pPeriod, side, appliedPrice, 50, filter, lookBack, time, 0);
		double[] MAs100 = indicators.sma(instrument, pPeriod, side,	appliedPrice, 100, filter, lookBack, time, 0);
		double[] MAs200 = indicators.sma(instrument, pPeriod, side, appliedPrice, 200, filter, lookBack, time, 0);

		double[] result = new double[MAs200.length];
		for (int i = 0; i < MAs200.length; i++) {
//			// valid only for clear trends, OK if MA20 below MA50
//			if ((MAs20[i] > MAs100[i] && MAs50[i] > MAs100[i] && MAs100[i] > MAs200[i])
//				|| (MAs20[i] < MAs100[i] && MAs50[i] < MAs100[i] && MAs100[i] < MAs200[i]))
			result[i] = Math.abs(MAs200[i] - MAs100[i]);
		}
		return result;
	}
	
	protected double[] getRawMAsDifferences(Instrument instrument, Period pPeriod, OfferSide side, IIndicators.AppliedPrice appliedPrice, long time, int lookBack) throws JFException {
		return getRawMAsDifferences(instrument, pPeriod, Filter.WEEKENDS, side, appliedPrice, time, lookBack);
	}


	public double getMAsMaxDiffStDevPos(Instrument instrument, Period pPeriod,
			OfferSide side, IIndicators.AppliedPrice appliedPrice, long time,
			int lookback) throws JFException {
		double MA20 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				20, Filter.WEEKENDS, 1, time, 0)[0];
		double MA50 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				50, Filter.WEEKENDS, 1, time, 0)[0];
		double MA100 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				100, Filter.WEEKENDS, 1, time, 0)[0];

		double[] stDevStats = MAsDifferenceStDevStats(instrument, pPeriod,
				side, appliedPrice, time, lookback);
		return (maxMAsDifference(MA20, MA50, MA100) - stDevStats[0])
				/ stDevStats[1];
	}

	public double getMAsMaxDiffPercentile(Instrument instrument, Period pPeriod, Filter filter, OfferSide side,	IIndicators.AppliedPrice appliedPrice, long time, int lookback)	throws JFException {
		double[] rawData = getRawMAsDifferences(instrument, pPeriod, filter, side, appliedPrice, time, lookback);
		double[] rank = new NaturalRanking().rank(rawData);

		// the last in rawData should be the latest bar. Rank 1 means it is the
		// biggest etc. Percentile is simply rank / array size * 100
		return rank[rank.length - 1] / rank.length * 100.0;
	}
	
	public double getMA200MA100TrendDiffPercentile(Instrument instrument, Period pPeriod, Filter filter, OfferSide side, IIndicators.AppliedPrice appliedPrice, long time, int lookback) throws JFException {
		double[] MAs20 = indicators.sma(instrument, pPeriod, side, appliedPrice, 20, filter, 1, time, 0);
		double[] MAs50 = indicators.sma(instrument, pPeriod, side, appliedPrice, 50, filter, 1, time, 0);
		double[] MAs100 = indicators.sma(instrument, pPeriod, side,	appliedPrice, 100, filter, 1, time, 0);
		double[] MAs200 = indicators.sma(instrument, pPeriod, side, appliedPrice, 200, filter, 1, time, 0);

//		// valid only for clear trends, OK if MA20 below MA50
//		if (!(MAs20[0] > MAs100[0] && MAs50[0] > MAs100[0] && MAs100[0] > MAs200[0])
//			&& !(MAs20[0] < MAs100[0] && MAs50[0] < MAs100[0] && MAs100[0] < MAs200[0]))
//			return -1;
		
		double[] rawData = getMA200MA100TrendDifferences(instrument, pPeriod, filter, side, appliedPrice, time, lookback);
		double[] rank = new NaturalRanking().rank(rawData);

		// the last in rawData should be the latest bar. Rank 1 means it is the
		// biggest etc. Percentile is simply rank / array size * 100
		return rank[rank.length - 1] / rank.length * 100.0;
	}

	
	public double getMAsMaxDiffPercentile(Instrument instrument, Period pPeriod, OfferSide side,	IIndicators.AppliedPrice appliedPrice, long time, int lookback)	throws JFException {
		return getMAsMaxDiffPercentile(instrument, pPeriod, Filter.WEEKENDS, side, appliedPrice, time, lookback);
	}


	public double getUptrendMAsMaxDiffPercentile(Instrument instrument,	Period pPeriod, Filter filter, OfferSide side,	IIndicators.AppliedPrice appliedPrice, long time, int lookback)	throws JFException {
		double[] rawData = getRawUptrendMAsDifferences(instrument, pPeriod, filter,	side, appliedPrice, time, lookback);
		double[] rank = new NaturalRanking().rank(rawData);

		// the last in rawData should be the latest bar. Rank 1 means it is the
		// biggest etc. Percentile is simply rank / array size * 100
		return rank[rank.length - 1] / rank.length * 100.0;
	}
	
	public double getUptrendMAsMaxDiffPercentile(Instrument instrument,	Period pPeriod, OfferSide side,	IIndicators.AppliedPrice appliedPrice, long time, int lookback)	throws JFException {
		return getUptrendMAsMaxDiffPercentile(instrument, pPeriod, Filter.WEEKENDS, side, appliedPrice, time, lookback);
	}

	public double getDowntrendMAsMaxDiffPercentile(Instrument instrument, Period pPeriod, Filter filter, OfferSide side, IIndicators.AppliedPrice appliedPrice, long time, int lookback) throws JFException {
		double[] rawData = getRawDowntrendMAsDifferences(instrument, pPeriod, filter, side, appliedPrice, time, lookback);
		double[] rank = new NaturalRanking().rank(rawData);

		// the last in rawData should be the latest bar. Rank 1 means it is the
		// biggest etc. Percentile is simply rank / array size * 100
		return rank[rank.length - 1] / rank.length * 100.0;
	}
	
	public double getDowntrendMAsMaxDiffPercentile(Instrument instrument, Period pPeriod, OfferSide side, IIndicators.AppliedPrice appliedPrice, long time, int lookback) throws JFException {
		return getDowntrendMAsMaxDiffPercentile(instrument, pPeriod, Filter.WEEKENDS, side, appliedPrice, time, lookback);
	}

	public double[] getADXs(Instrument instrument, Period pPeriod,
			OfferSide side, long time) throws JFException {
		double[] result = new double[3];
		result[0] = indicators.adx(instrument, pPeriod, side, 14,
				Filter.WEEKENDS, 1, time, 0)[0];
		result[1] = indicators.plusDi(instrument, pPeriod, side, 14,
				Filter.WEEKENDS, 1, time, 0)[0];
		result[2] = indicators.minusDi(instrument, pPeriod, side, 14,
				Filter.WEEKENDS, 1, time, 0)[0];

		return result;
	}

	/**
	 * current max MAs difference in pips
	 * 
	 * @return
	 */
	public double getAbsMAsDifference(Instrument instrument, Period pPeriod,
			OfferSide side, IIndicators.AppliedPrice appliedPrice, long time)
			throws JFException {
		double MA20 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				20, Filter.WEEKENDS, 1, time, 0)[0];
		double MA50 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				50, Filter.WEEKENDS, 1, time, 0)[0];
		double MA100 = indicators.sma(instrument, pPeriod, side, appliedPrice,
				100, Filter.WEEKENDS, 1, time, 0)[0];

		return maxMAsDifference(MA20, MA50, MA100)
				* Math.pow(10, instrument.getPipScale());
	}

	public ICHI_CLOUD_CROSS isIchiCloudCross(IHistory history,
			Instrument instrument, Period pPeriod, OfferSide side,
			IIndicators.AppliedPrice appliedPrice, long time)
			throws JFException {
		double[][]
		// used for cloud borders, therefore moved kijun periods in past ! Cloud
		// is drawn in future !
		i_sh = indicators.ichimoku(instrument, pPeriod, side, tenkan, kijun,
				senkou, Filter.WEEKENDS, 1 + kijun, time, 0), i_sh_1 = indicators
				.ichimoku(instrument, pPeriod, side, tenkan, kijun, senkou,
						Filter.WEEKENDS, 2 + kijun, time, 0);

		double
		// must be rounded to 0.1 pips since used as SL !
		i_cloudTop = Math.round(Math.max(i_sh[SENOKU_A][0], i_sh[SENOKU_B][0])
				* Math.pow(10, instrument.getPipScale())), i_cloudBottom = Math
				.round(Math.min(i_sh[SENOKU_A][0], i_sh[SENOKU_B][0])
						* Math.pow(10, instrument.getPipScale())), i_cloudTop_1 = Math
				.round(Math.max(i_sh_1[SENOKU_A][0], i_sh_1[SENOKU_B][0])
						* Math.pow(10, instrument.getPipScale())), i_cloudBottom_1 = Math
				.round(Math.min(i_sh_1[SENOKU_A][0], i_sh_1[SENOKU_B][0])
						* Math.pow(10, instrument.getPipScale()));

		double cloudTop = i_cloudTop / Math.pow(10, instrument.getPipScale()), cloudBottom = i_cloudBottom
				/ Math.pow(10, instrument.getPipScale()), cloudTop_1 = i_cloudTop_1
				/ Math.pow(10, instrument.getPipScale()), cloudBottom_1 = i_cloudBottom_1
				/ Math.pow(10, instrument.getPipScale());

		List<IBar> last2bars = history.getBars(instrument, pPeriod, side,
				Filter.WEEKENDS, 2, time, 0);
		IBar bar = last2bars.get(1);
		IBar bar_1 = last2bars.get(0);

		if (bar_1.getClose() >= cloudBottom_1 && bar.getClose() < cloudBottom) {
			return ICHI_CLOUD_CROSS.BEARISH;
		}

		if (bar_1.getClose() <= cloudTop_1 && bar.getClose() > cloudTop) {
			return ICHI_CLOUD_CROSS.BULLISH;
		}

		return ICHI_CLOUD_CROSS.NONE;
	}

	public boolean isIchiLongCancel(IHistory history, Instrument instrument,
			Period pPeriod, OfferSide side,
			IIndicators.AppliedPrice appliedPrice, long time)
			throws JFException {
		double[][]
		// used for cloud borders, therefore moved kijun periods in past ! Cloud
		// is drawn in future !
		i_sh = indicators.ichimoku(instrument, pPeriod, side, tenkan, kijun,
				senkou, Filter.WEEKENDS, 1 + kijun, time, 0);

		double
		// must be rounded to 0.1 pips since used as SL !
		i_cloudTop = Math.round(Math.max(i_sh[SENOKU_A][0], i_sh[SENOKU_B][0])
				* Math.pow(10, instrument.getPipScale()));

		double cloudTop = i_cloudTop / Math.pow(10, instrument.getPipScale());

		IBar bar = history.getBars(instrument, pPeriod, side, Filter.WEEKENDS,
				1, time, 0).get(0);

		return bar.getClose() < cloudTop;
	}

	public boolean isIchiShortCancel(IHistory history, Instrument instrument,
			Period pPeriod, OfferSide side,
			IIndicators.AppliedPrice appliedPrice, long time)
			throws JFException {
		double[][]
		// used for cloud borders, therefore moved kijun periods in past ! Cloud
		// is drawn in future !
		i_sh = indicators.ichimoku(instrument, pPeriod, side, tenkan, kijun,
				senkou, Filter.WEEKENDS, 1 + kijun, time, 0);

		double
		// must be rounded to 0.1 pips since used as SL !
		i_cloudBottom = Math.round(Math.min(i_sh[SENOKU_A][0],
				i_sh[SENOKU_B][0]) * Math.pow(10, instrument.getPipScale()));

		double cloudBottom = i_cloudBottom
				/ Math.pow(10, instrument.getPipScale());

		IBar bar = history.getBars(instrument, pPeriod, side, Filter.WEEKENDS,
				1, time, 0).get(0);

		return bar.getClose() > cloudBottom;
	}

	public IchiDesc getIchi(IHistory history, Instrument instrument, Period pPeriod, OfferSide side, long time) throws JFException 
	{ 
		return getIchi(history, instrument, pPeriod, side, Filter.WEEKENDS, time);
	}

	public IchiDesc getIchi(IHistory history, Instrument instrument, Period pPeriod, OfferSide side, Filter filter, long time) throws JFException {
		double[][]
		// used for cloud borders, therefore moved kijun periods in past ! Cloud
		// is drawn in future !
		i_sh = indicators.ichimoku(instrument, pPeriod, side, tenkan, kijun, senkou, filter, kijun, time, 0), 
		i_sh_1 = indicators.ichimoku(instrument, pPeriod, side, tenkan, kijun, senkou, filter, 1 + kijun, time, 0), 
		i_sh_2 = indicators.ichimoku(instrument, pPeriod, side, tenkan, kijun, senkou, filter, 2 + kijun, time, 0),
		/*
		i_sh = indicators.ichimoku(instrument, pPeriod, side, tenkan, kijun, senkou, filter, 1 + kijun, time, 0), 
		i_sh_1 = indicators.ichimoku(instrument, pPeriod, side, tenkan, kijun, senkou, filter, 2 + kijun, time, 0), 
		i_sh_2 = indicators.ichimoku(instrument, pPeriod, side, tenkan, kijun, senkou, filter, 3 + kijun, time, 0),
		*/
		// lines are normally drawn...
		lines = indicators.ichimoku(instrument, pPeriod, side, tenkan, kijun, senkou, filter, 3, time, 0);

		double
			// must be rounded to 0.1 pips since used as SL !
			i_cloudTop = Math.max(i_sh[SENOKU_A][0], i_sh[SENOKU_B][0]), 
			i_cloudBottom = Math.min(i_sh[SENOKU_A][0], i_sh[SENOKU_B][0]), 
			i_cloudTop_1 = Math.max(i_sh_1[SENOKU_A][0], i_sh_1[SENOKU_B][0]), 
			i_cloudBottom_1 = Math.min(i_sh_1[SENOKU_A][0], i_sh_1[SENOKU_B][0]), 
			i_cloudTop_2 = Math.max(i_sh_2[SENOKU_A][0], i_sh_1[SENOKU_B][0]), 
			i_cloudBottom_2 = Math.min(i_sh_2[SENOKU_A][0], i_sh_1[SENOKU_B][0]), 
			i_kumo_A = i_sh[CLOUD_A][0]	* Math.pow(10, instrument.getPipScale()), 
			i_kumo_B = i_sh[CLOUD_B][0];

		double 
			cloudTop = i_cloudTop, 
			cloudBottom = i_cloudBottom, 
			cloudWidth = cloudTop - cloudBottom, 
			cloudTop_1 = i_cloudTop_1, 
			cloudBottom_1 = i_cloudBottom_1, 
			kumo_A = i_kumo_A , kumo_B = i_kumo_B,

			fastLine = lines[TENKAN][2], 
			prevFastLine = lines[TENKAN][1], 
			firstFastLine = lines[TENKAN][0],
			slowLine = lines[KIJUN][2], 
			prevSlowLine = lines[KIJUN][1], 
			firstSlowLine = lines[KIJUN][0],
	
			ATR = indicators.atr(instrument, pPeriod, side, 14, filter, 1, time, 0)[0];

		IchiDesc res = new IchiDesc();
		res.cloudBottom = cloudBottom;
		res.cloudTop = cloudTop;
		res.prevCloudBottom = i_cloudBottom_1;
		res.prevCloudTop = i_cloudTop_1;
		res.fastLine = fastLine;
		res.slowLine = slowLine;

		if (fastLine > prevFastLine && prevFastLine > firstFastLine)
			res.fastLineState = new String("RAISING");
		else if (fastLine > prevFastLine && prevFastLine <= firstFastLine)
			res.fastLineState = new String("TICKED_UP");
		else if (fastLine == prevFastLine && prevFastLine == firstFastLine)
			res.fastLineState = new String("FLAT");
		else if (fastLine < prevFastLine && prevFastLine < firstFastLine)
			res.fastLineState = new String("FALLING");
		else if (fastLine < prevFastLine && prevFastLine >= firstFastLine)
			res.fastLineState = new String("TICKED_DOWN");
		else
			res.fastLineState = new String("UNKNOWN");

		if (slowLine > prevSlowLine && prevSlowLine > firstSlowLine)
			res.slowLineState = new String("RAISING");
		else if (slowLine > prevSlowLine && prevSlowLine <= firstSlowLine)
			res.slowLineState = new String("TICKED_UP");
		else if (slowLine == prevSlowLine && prevSlowLine == firstSlowLine)
			res.slowLineState = new String("FLAT");
		else if (slowLine < prevSlowLine && prevSlowLine < firstSlowLine)
			res.slowLineState = new String("FALLING");
		else if (slowLine < prevSlowLine && prevSlowLine >= firstSlowLine)
			res.slowLineState = new String("TICKED_DOWN");
		else
			res.slowLineState = new String("UNKNOWN");

		if (i_cloudTop == i_cloudTop_1 && i_cloudTop_1 == i_cloudTop_2)
			res.topBorderDirection = new String("flat");
		else if ((i_cloudTop > i_cloudTop_1 && i_cloudTop_1 >= i_cloudTop_2)
				|| (i_cloudTop >= i_cloudTop_1 && i_cloudTop_1 > i_cloudTop_2))
			res.topBorderDirection = new String("raising");
		else if ((i_cloudTop < i_cloudTop_1 && i_cloudTop_1 <= i_cloudTop_2)
				|| (i_cloudTop <= i_cloudTop_1 && i_cloudTop_1 < i_cloudTop_2))
			res.topBorderDirection = new String("falling");
		else if (i_cloudTop > i_cloudTop_1 && i_cloudTop_1 < i_cloudTop_2)
			res.topBorderDirection = new String("ticked_up");
		else if (i_cloudTop < i_cloudTop_1 && i_cloudTop_1 > i_cloudTop_2)
			res.topBorderDirection = new String("ticked_down");
		else
			res.topBorderDirection = new String("I do not know ?");

		if (i_cloudBottom == i_cloudBottom_1
				&& i_cloudBottom_1 == i_cloudBottom_2)
			res.bottomBorderDirection = new String("flat");
		else if ((i_cloudBottom > i_cloudBottom_1 && i_cloudBottom_1 >= i_cloudBottom_2)
				|| (i_cloudBottom >= i_cloudBottom_1 && i_cloudBottom_1 > i_cloudBottom_2))
			res.bottomBorderDirection = new String("raising");
		else if ((i_cloudBottom < i_cloudBottom_1 && i_cloudBottom_1 <= i_cloudBottom_2)
				|| (i_cloudBottom <= i_cloudBottom_1 && i_cloudBottom_1 < i_cloudBottom_2))
			res.bottomBorderDirection = new String("falling");
		else if (i_cloudBottom > i_cloudBottom_1
				&& i_cloudBottom_1 < i_cloudBottom_2)
			res.bottomBorderDirection = new String("ticked_up");
		else if (i_cloudBottom < i_cloudBottom_1
				&& i_cloudBottom_1 > i_cloudBottom_2)
			res.bottomBorderDirection = new String("ticked_down");
		else
			res.bottomBorderDirection = new String("I do not know ?");
		
		List<IBar> last2bars = history.getBars(instrument, pPeriod, side, filter, 2, time, 0);
		IBar bar = last2bars.get(1);
		IBar prevBar = last2bars.get(0);

		if (prevBar.getClose() >= cloudBottom_1 && bar.getClose() < cloudBottom) {
			res.isBearishCloudCross = true;
		} else
			res.isBearishCloudCross = false;

		if (prevBar.getClose() <= cloudTop_1 && bar.getClose() > cloudTop) {
			res.isBullishCloudCross = true;
		} else
			res.isBullishCloudCross = false;

		if (prevBar.getClose() < prevSlowLine && bar.getClose() >= slowLine)
			res.isBullishSlowLineCross = true;
		else
			res.isBullishSlowLineCross = false;
		if (prevBar.getClose() > prevSlowLine && bar.getClose() <= slowLine)
			res.isBearishSlowLineCross = true;
		else
			res.isBearishSlowLineCross = false;

		if (bar.getClose() > slowLine)
			res.isCloseAboveSlowLine = true;
		else
			res.isCloseAboveSlowLine = false;

		res.widthPips = cloudWidth * Math.pow(10, instrument.getPipScale());
		res.widthToATR = res.widthPips / ATR;
		// res.widthRel =
		res.isBullishCloud = i_sh[SENOKU_A][0] > i_sh[SENOKU_B][0];
		res.isBullishTenkanLine = fastLine > slowLine;
		return res;
	}

	public double ichiCloudDistCalc(IHistory history, Instrument instrument,
			Period pPeriod, OfferSide side, IBar bar, long time,
			int lookBackPeriod) throws JFException {
		double[][]
		// used for cloud borders, therefore moved kijun periods in past ! Cloud
		// is drawn in future !
		i_sh = indicators.ichimoku(instrument, pPeriod, side, tenkan, kijun,
				senkou, Filter.WEEKENDS, lookBackPeriod + kijun, time, 0);
		double[] atr_hist = indicators.atr(instrument, pPeriod, side, 14,
				Filter.WEEKENDS, lookBackPeriod, time, 0);

		List<IBar> bars = history.getBars(instrument, pPeriod, side,
				Filter.WEEKENDS, lookBackPeriod, time, 0);
		double[] distances = new double[lookBackPeriod];
		int relevantBars = 0;

		for (int i = 0; i < bars.size(); i++) {

			IBar currBar = bars.get(i);
			double i_cloudTop = Math.max(i_sh[SENOKU_A][i], i_sh[SENOKU_B][i]), i_cloudBottom = Math
					.min(i_sh[SENOKU_A][i], i_sh[SENOKU_B][i]);
			// relevant are only bars with at least one extreme outside cloud
			if (currBar.getLow() > i_cloudTop
					|| (currBar.getHigh() > i_cloudTop
							&& currBar.getLow() < i_cloudTop && currBar
							.getLow() >= i_cloudBottom)
					|| (currBar.getHigh() > i_cloudTop
							&& currBar.getLow() < i_cloudBottom && currBar
							.getClose() > i_cloudTop)) {
				distances[relevantBars++] = (currBar.getHigh() - i_cloudTop)
						/ atr_hist[i];
			} else if (currBar.getHigh() < i_cloudBottom
					|| (currBar.getLow() < i_cloudBottom
							&& currBar.getHigh() > i_cloudBottom && currBar
							.getHigh() <= i_cloudTop)
					|| (currBar.getLow() < i_cloudBottom
							&& currBar.getHigh() > i_cloudTop && currBar
							.getClose() < i_cloudBottom)) {
				distances[relevantBars++] = (i_cloudBottom - currBar.getLow())
						/ atr_hist[i];
			}
		}

		double[][]
		// used for cloud borders, therefore moved kijun periods in past ! Cloud
		// is drawn in future !
		i_sh_now = indicators.ichimoku(instrument, pPeriod, side, tenkan,
				kijun, senkou, Filter.WEEKENDS, 1 + kijun, time, 0);
		double atr = indicators.atr(instrument, pPeriod, side, 14,
				Filter.WEEKENDS, 1, time, 0)[0], currDistance = 0.0, currDistanceAbs = 0.0, i_cloudTop_now = Math
				.max(i_sh_now[SENOKU_A][0], i_sh_now[SENOKU_B][0]), i_cloudBottom_now = Math
				.min(i_sh_now[SENOKU_A][0], i_sh_now[SENOKU_B][0]);
		if (bar.getLow() > i_cloudTop_now
				|| (bar.getHigh() > i_cloudTop_now
						&& bar.getLow() < i_cloudTop_now && bar.getLow() >= i_cloudBottom_now)
				|| (bar.getHigh() > i_cloudTop_now
						&& bar.getLow() < i_cloudBottom_now && bar.getClose() > i_cloudTop_now)) {
			currDistanceAbs = bar.getHigh() - i_cloudTop_now;
			currDistance = currDistanceAbs / atr;
		} else if (bar.getHigh() < i_cloudBottom_now
				|| (bar.getLow() < i_cloudBottom_now
						&& bar.getHigh() > i_cloudBottom_now && bar.getHigh() <= i_cloudTop_now)
				|| (bar.getLow() < i_cloudBottom_now
						&& bar.getHigh() > i_cloudTop_now && bar.getClose() < i_cloudBottom_now)) {
			currDistanceAbs = i_cloudBottom_now - bar.getLow();
			currDistance = currDistanceAbs / atr;
		}

		double[] nonEmptyDistances = new double[relevantBars];
		for (int i = 0; i < relevantBars; i++)
			nonEmptyDistances[i] = distances[i];

		double[] stDevRes = FXUtils.sdFast(nonEmptyDistances);
		return (currDistance - stDevRes[0]) / stDevRes[1];
	}

	public FLAT_REGIME_CAUSE isFlatRegime(Instrument instrument, Period pPeriod, OfferSide side, IIndicators.AppliedPrice appliedPrice, Filter filter, long time, int lookBack, double MAsDistancePercThreshold) throws JFException {
		double ma20 = indicators.sma(instrument, pPeriod, side, appliedPrice, 20, filter, 1, time, 0)[0], 
				ma50 = indicators.sma(instrument, pPeriod, side, appliedPrice, 50, filter, 1, time, 0)[0], 
				ma100 = indicators.sma(instrument, pPeriod, side, appliedPrice, 100, filter, 1,	time, 0)[0],
				ma200 = indicators.sma(instrument, pPeriod, side, appliedPrice, 200, filter, 1,	time, 0)[0];
		double[][] bBands = indicators.bbands(instrument, pPeriod, side, appliedPrice, 20, 2.0, 2.0, MaType.SMA, filter, 1, time, 0);
		double bBandsTop = bBands[0][0], bBandsBottom = bBands[2][0];

		// all MAs within channel > FLAT !
		if ((bBandsTop > ma20 && bBandsTop > ma50 && bBandsTop > ma100 && bBandsBottom < ma20 && bBandsBottom < ma50 && bBandsBottom < ma100)
			|| ((bBandsTop > ma200 && bBandsBottom < ma200) && ((bBandsTop > ma100 && bBandsBottom < ma100) || (bBandsTop > ma50 && bBandsBottom < ma50))))  
			return FLAT_REGIME_CAUSE.MAs_WITHIN_CHANNEL;

		// MAs very tight together
		if (getMAsMaxDiffPercentile(instrument, pPeriod, side, appliedPrice, time, lookBack) < MAsDistancePercThreshold)
			return FLAT_REGIME_CAUSE.MAs_CLOSE;

		// TODO: third criteria should be contradictory position of basic 3 MAs
		// against MA200. For example MA200 highest but any of three uptrends.
		// Study !!!
		return FLAT_REGIME_CAUSE.NONE;
	}

	public TREND_STATE getTrendState(Instrument instrument, Period period, OfferSide bid, AppliedPrice close, long time) throws JFException {
		return getTrendState(instrument, period, Filter.WEEKENDS, bid, close, time);
	}

}