package jforex.techanalysis;

import java.text.DecimalFormat;
import java.util.List;

import jforex.utils.FXUtils;
import jforex.utils.Logger;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.IIndicators.MaType;

public class TradeTrigger {

	/**
	 * 
	 */
	private IIndicators indicators;
	private Logger log;
	private IHistory history;
	private boolean stochsHeavilyOverboughtTriggered;
	private boolean stochsHeavilyOversoldTriggered;

	public enum TriggerType {
		NONE, BULLISH_1_BAR, BULLISH_2_BARS, BULLISH_3_BARS, BEARISH_1_BAR, BEARISH_2_BARS, BEARISH_3_BARS
	}

	public class TriggerDesc {
		public TriggerType type;
		public double pivotLevel,
				srLevel, // support-resistance defined by this candle pattern
				reversalSize, channelPosition, bBandsTop,
				bBandsBottom,
				bBandsWidth, // bBands values at signal time
				keltnerChannelPosition,
				// for 2- and 3-bar triggers - total values when combined into 1
				// bar
				combinedUpperHandlePerc, combinedRealBodyPerc,
				combinedLowerHandlePerc;
		public boolean combinedRealBodyDirection; // true bullish, false bearish

		protected int numberOfBars = 0;
		protected IBar patternBars[] = null;

		public TriggerDesc(TriggerType type, int noOfBars, IBar[] patternBars,
				double pivotLevel, double srLevel, double channelPosition,
				double bBandsTop, double bBandsBottom, double bBandsWidth,
				double keltnerChannelPosition, double pCombinedUHPerc,
				double pCombinedRealBodyPerc, double pCombinedLHPerc,
				boolean pCombinedRealBodyDirection) {
			super();
			this.numberOfBars = noOfBars;
			this.patternBars = patternBars;
			this.type = type;
			this.pivotLevel = pivotLevel;
			this.srLevel = srLevel;
			this.channelPosition = channelPosition;
			this.bBandsBottom = bBandsBottom;
			this.bBandsTop = bBandsTop;
			this.bBandsWidth = bBandsWidth;
			this.keltnerChannelPosition = keltnerChannelPosition;
			combinedUpperHandlePerc = pCombinedUHPerc;
			combinedRealBodyPerc = pCombinedRealBodyPerc;
			combinedLowerHandlePerc = pCombinedLHPerc;
			combinedRealBodyDirection = pCombinedRealBodyDirection;

			this.reversalSize = calcReversalSize();
		}

		private double calcReversalSize() {
			if (numberOfBars == 1) {
				return patternBars[0].getHigh() - patternBars[0].getLow();
			}
			if (numberOfBars == 2) {
				double patternHigh = Math.max(patternBars[0].getHigh(),
						patternBars[1].getHigh()), patternLow = Math.min(
						patternBars[0].getLow(), patternBars[1].getLow());
				return patternHigh - patternLow;
			}
			double patternHigh = Math.max(patternBars[0].getHigh(), Math.max(
					patternBars[1].getHigh(), patternBars[2].getHigh())), patternLow = Math
					.min(patternBars[0].getLow(),
							Math.min(patternBars[1].getLow(),
									patternBars[2].getLow()));
			return patternHigh - patternLow;
		}

		public double getReversalSize() {
			return reversalSize;
		}

		public IBar getLastBar() {
			if (numberOfBars == 1)
				return patternBars[0];
			else if (numberOfBars == 2)
				return patternBars[1];
			return patternBars[2];
		}

		public double getCombinedHigh() {
			if (patternBars == null)
				return Double.NEGATIVE_INFINITY;

			if (numberOfBars == 1)
				return patternBars[0].getHigh();
			else if (numberOfBars == 2)
				return Math.max(patternBars[0].getHigh(),
						patternBars[1].getHigh());
			return Math.max(Math.max(patternBars[0].getHigh(),
					patternBars[1].getHigh()), patternBars[2].getHigh());
		}

		public double getCombinedLow() {
			if (patternBars == null)
				return Double.POSITIVE_INFINITY;

			if (numberOfBars == 1)
				return patternBars[0].getLow();
			else if (numberOfBars == 2)
				return Math.min(patternBars[0].getLow(),
						patternBars[1].getLow());
			return Math.min(
					Math.min(patternBars[0].getLow(), patternBars[1].getLow()),
					patternBars[2].getLow());
		}
	}

	private TriggerType lastBullishTrigger;
	private TriggerType lastBearishTrigger;

	public TradeTrigger(IIndicators pIndicators, IHistory pHistory, Logger pLog) {
		this.indicators = pIndicators;
		this.history = pHistory;
		this.log = pLog;
		resetT1BL();
		resetT1BH();
	}

	public void resetT1BL() {
		this.stochsHeavilyOverboughtTriggered = false;
	}

	public void resetT1BH() {
		this.stochsHeavilyOversoldTriggered = false;
	}

	/**
	 * most generic bullish trigger, check for any of the 1-, 2- or 3-candles
	 * bullish reversal patterns
	 * 
	 * @return low of pivot bar if condition found (to serve as protective stop)
	 *         otherwise -1
	 */
	public double bullishReversalCandlePattern(Instrument instrument,
			Period pPeriod, OfferSide side, long time) throws JFException {
		lastBullishTrigger = TriggerType.NONE;
		double pivotLow = Double.MIN_VALUE;
		if ((pivotLow = this.oneBarBullishTrigger(instrument, pPeriod, side,
				time)) != Double.MIN_VALUE) {
			lastBullishTrigger = TriggerType.BULLISH_1_BAR;
			return pivotLow;
		} else if ((pivotLow = this.twoBarsBullishTrigger(instrument, pPeriod,
				side, time)) != Double.MIN_VALUE) {
			lastBullishTrigger = TriggerType.BULLISH_2_BARS;
			return pivotLow;
		}
		if ((pivotLow = this.bullishPivotalLowsTrigger(instrument, pPeriod,
				side, time)) != Double.MIN_VALUE) {
			lastBullishTrigger = TriggerType.BULLISH_3_BARS;
			return pivotLow;
		} else
			return Double.MIN_VALUE;
	}

	public IBar bullishReversalCandlePatternBar(Instrument instrument,	Period pPeriod, OfferSide side, long time) throws JFException {
		lastBullishTrigger = TriggerType.NONE;
		IBar pivotLowBar = null;
		if ((pivotLowBar = this.oneBarBullishTriggerBar(instrument, pPeriod, side, time)) != null) {
			lastBullishTrigger = TriggerType.BULLISH_1_BAR;
			return pivotLowBar;
		} else if ((pivotLowBar = this.twoBarsBullishTriggerBar(instrument,
				pPeriod, side, time)) != null) {
			lastBullishTrigger = TriggerType.BULLISH_2_BARS;
			return pivotLowBar;
		} else if ((pivotLowBar = this.bullishPivotalLowsTriggerBar(instrument,
				pPeriod, side, time)) != null) {
			lastBullishTrigger = TriggerType.BULLISH_3_BARS;
			return pivotLowBar;
		} else
			return null;
	}

	public TriggerDesc bullishReversalCandlePatternDesc(Instrument instrument,	Period pPeriod, OfferSide side, long time) throws JFException {
		return bullishReversalCandlePatternDesc(instrument, pPeriod, Filter.WEEKENDS, side, time);
	}

	/**
	 * S/R level for bullish 3-bar is middle of middle bar's LOWER handle
	 */
	private double getBullishPivotalThreeBarsSRLevel(IBar middleBar) {
		double realBodyBottom = Math.min(middleBar.getOpen(),	middleBar.getClose());
		return middleBar.getLow() + (realBodyBottom - middleBar.getLow()) / 2.0;
	}

	/**
	 * support/resistance level is the same both for bullish and bearish pivot
	 * triangle - simply the middle bar middle
	 */
	private double getPivotalThreeBarsSRLevel(Instrument instrument, Period pPeriod, OfferSide side, long time) throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side, Filter.WEEKENDS, 2, time, 0);
		IBar prevBar = bars.get(0);
		return prevBar.getLow() + (prevBar.getHigh() - prevBar.getLow()) / 2;
	}

	private double getLowerHandleMiddle(IBar bar) {
		double realBodyBottom = Math.min(bar.getOpen(), bar.getClose());
		return bar.getLow() + (realBodyBottom - bar.getLow()) / 2;
	}

	public double priceKeltnerChannelPos(Instrument instrument, Period pPeriod,	OfferSide side, long time, double price, int backBars)	throws JFException {
		return priceKeltnerChannelPos(instrument, pPeriod, Filter.WEEKENDS, side, time, price, backBars);
	}
	
	public double priceKeltnerChannelPos(Instrument instrument, Period pPeriod,	Filter filter, OfferSide side, long time, double price, int backBars)	throws JFException {
		double[] last2ATRs = indicators.atr(instrument, pPeriod, side, 14, filter, 2, time, 0);
		double[] last2MAs = indicators.sma(instrument, pPeriod, side, AppliedPrice.CLOSE, 20, filter, 2, time, 0);
		double keltnerBandsWidth = (last2MAs[backBars] + 2.0 * last2ATRs[backBars])	- (last2MAs[backBars] - 2.0 * last2ATRs[backBars]);
		double keltnerBandsBottom = last2MAs[backBars] - 2.0 * last2ATRs[backBars];
		return (price - keltnerBandsBottom) / keltnerBandsWidth * 100;
	}


	/**
	 * @return low of pivot bar if condition found (to serve as protective stop)
	 *         otherwise -1
	 */
	public double bullishPivotalLowsTrigger(Instrument instrument, Period pPeriod, OfferSide side, long time) throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side,Filter.WEEKENDS, 3, time, 0);
		IBar currBar = bars.get(2);
		IBar prevBar1 = bars.get(1);
		IBar prevBar2 = bars.get(0);

		if (currBar.getLow() > prevBar1.getLow()
				&& prevBar1.getLow() <= prevBar2.getLow()) {
			return prevBar1.getLow();
		} else {
			return Double.MIN_VALUE;
		}
	}

	public IBar bullishPivotalLowsTriggerBar(Instrument instrument,	Period pPeriod, OfferSide side, long time) throws JFException {
		return bullishPivotalLowsTriggerBar(instrument, pPeriod, Filter.WEEKENDS, side, time);
	}
	
	public IBar bullishPivotalLowsTriggerBar(Instrument instrument,	Period pPeriod, Filter filter, OfferSide side, long time) throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side, filter, 3, time, 0);
		IBar currBar = bars.get(2);
		IBar prevBar1 = bars.get(1);
		IBar prevBar2 = bars.get(0);

		if (currBar.getLow() > prevBar1.getLow() && prevBar1.getLow() <= prevBar2.getLow()) {
			return prevBar1;
		} else {
			return null;
		}
	}

	// bar length must be at least average
	public double oneBarBullishTrigger(Instrument instrument, Period pPeriod,
			OfferSide side, long time) throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side,
				Filter.WEEKENDS, 1, time, 0);
		IBar currBar = bars.get(0);
		if (barLengthStatPos(instrument, pPeriod, side, currBar, 1000) < -0.1)
			return Double.MIN_VALUE;

		double barLength = currBar.getHigh() - currBar.getLow();
		double realBodyLow = Math.min(currBar.getOpen(), currBar.getClose());
		double lowerWick = realBodyLow - currBar.getLow();

		if (lowerWick / barLength >= 0.6) {
			return currBar.getLow();
		} else {
			return Double.MIN_VALUE;
		}
	}

	// bar length must be at least average
	public IBar oneBarBullishTriggerBar(Instrument instrument, Period pPeriod,	OfferSide side, long time) throws JFException {
		return oneBarBullishTriggerBar(instrument, pPeriod, Filter.WEEKENDS, side, time);
	}
	
	public IBar oneBarBullishTriggerBar(Instrument instrument, Period pPeriod, Filter filter, OfferSide side, long time) throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side, filter, 1, time, 0);
		IBar currBar = bars.get(0);
		if (barLengthStatPos(instrument, pPeriod, side, currBar, 1000) <= 0.0)
			return null;

		double 
			barLength = currBar.getHigh() - currBar.getLow(), 
			realBodyLow = Math.min(currBar.getOpen(), currBar.getClose()), 
			lowerWick = realBodyLow	- currBar.getLow();

		if (lowerWick / barLength >= 0.6) {
			return currBar;
		} else {
			return null;
		}
	}

	public IBar bullishMaribouzuBar(Instrument instrument, Period pPeriod,
			OfferSide side, long time) throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side,
				Filter.WEEKENDS, 1, time, 0);
		IBar currBar = bars.get(0);
		if (barLengthStatPos(instrument, pPeriod, side, currBar, 1000) <= 0.0)
			return null;

		double barLength = currBar.getHigh() - currBar.getLow();

		if (currBar.getClose() > currBar.getOpen()
				&& (currBar.getClose() - currBar.getOpen()) / barLength >= 0.8) {
			return currBar;
		} else {
			return null;
		}
	}
	
	public IBar bullishMaribouzuBar(Instrument instrument, Period pPeriod, Filter filter, OfferSide side, long time) throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side, filter, 1, time, 0);
		IBar currBar = bars.get(0);
		if (barLengthStatPos(instrument, pPeriod, filter, side, currBar, 1000) <= 0.0)
			return null;

		double barLength = currBar.getHigh() - currBar.getLow();

		if (currBar.getClose() > currBar.getOpen() && (currBar.getClose() - currBar.getOpen()) / barLength >= 0.8) {
			return currBar;
		} else {
			return null;
		}
	}


	public double oneBarBearishTrigger(Instrument instrument, Period pPeriod, Filter filter, OfferSide side, long time) throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side, filter, 1, time, 0);
		IBar currBar = bars.get(0);
		// bar length must be at least average
		if (barLengthStatPos(instrument, pPeriod, filter, side, currBar, 1000) <= 0.0)
			return Double.MAX_VALUE;

		double barLength = currBar.getHigh() - currBar.getLow(), realBodyHigh = Math
				.max(currBar.getOpen(), currBar.getClose()), upperWick = currBar
				.getHigh() - realBodyHigh;

		if (upperWick / barLength >= 0.6) {
			return currBar.getHigh();
		} else {
			return Double.MAX_VALUE;
		}
	}

	public double oneBarBearishTrigger(Instrument instrument, Period pPeriod, OfferSide side, long time) throws JFException {
		return oneBarBearishTrigger(instrument, pPeriod, Filter.WEEKENDS, side, time);
	}
	
	public double bearishMaribouzuBar(Instrument instrument, Period pPeriod, Filter filter, OfferSide side, long time) throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side, filter, 1, time, 0);
		IBar currBar = bars.get(0);
		// bar length must be at least average
		if (barLengthStatPos(instrument, pPeriod, filter, side, currBar, 1000) <= 0.0)
			return Double.MAX_VALUE;

		double barLength = currBar.getHigh() - currBar.getLow();

		if (currBar.getClose() < currBar.getOpen()
				&& (currBar.getOpen() - currBar.getClose()) / barLength >= 0.8) {
			return currBar.getHigh();
		} else {
			return Double.MAX_VALUE;
		}
	}
	
	public double bearishMaribouzuBar(Instrument instrument, Period pPeriod, OfferSide side, long time) throws JFException {
		return bearishMaribouzuBar(instrument, pPeriod, Filter.WEEKENDS, side, time);
	}


	// 2nd bar body length must be at least average
	public double twoBarsBullishTrigger(Instrument instrument, Period pPeriod,
			OfferSide side, long time) throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side,
				Filter.WEEKENDS, 2, time, 0);
		IBar prevBar = bars.get(0);
		IBar currBar = bars.get(1);

		// bullish inside bar (previous very big and RED, current bar very
		// small)
		if (barBodyLengthStatPos(instrument, pPeriod, side, currBar, 1000) <= -1.0
				&& barBodyLengthStatPos(instrument, pPeriod, side, prevBar,
						1000) >= 1.0
				&& prevBar.getClose() < prevBar.getOpen() // careful, avoid
															// riksha man !
															// Previous bar
															// should have
															// significant body
															// size relative to
															// length !
				&& barsBodyPerc(prevBar) >= 60.0
				&& prevBar.getHigh() >= currBar.getHigh()
				&& prevBar.getLow() <= currBar.getLow())
			return prevBar.getLow();

		if (barBodyLengthStatPos(instrument, pPeriod, side, currBar, 1000) <= 0.0)
			return Double.MIN_VALUE;

		// first check strict engulfing possibility
		int[] engulfing = indicators.cdlEngulfing(instrument, pPeriod, side,
				Filter.WEEKENDS, 1, time, 0);
		if (engulfing != null && engulfing[0] == 100)
			return prevBar.getLow() < currBar.getLow() ? prevBar.getLow()
					: currBar.getLow();

		// 2nd bar of the combination must be green
		if (currBar.getClose() < currBar.getOpen())
			return Double.MIN_VALUE;

		if (bodiesOverlapPerc(prevBar, currBar) >= 0.8)
			// careful: pivot bar is the one with deeper low, not necessarily
			// previous !
			if (prevBar.getLow() < currBar.getLow())
				return prevBar.getLow();
			else
				return currBar.getLow();
		else
			return Double.MIN_VALUE;
	}

	public String bullishCandleDescription(Instrument instrument,
			Period pPeriod, OfferSide side, long time) throws JFException {
		// first check strict engulfing possibility
		int[] engulfing = indicators.cdlEngulfing(instrument, pPeriod, side,
				Filter.WEEKENDS, 1, time, 0);
		if (engulfing != null && engulfing[0] == 100)
			return new String("Bullish engulfing");

		int[] abandonedBaby = indicators.cdlAbandonedBaby(instrument, pPeriod,
				side, 60.0, Filter.WEEKENDS, 1, time, 0);
		if (abandonedBaby != null && abandonedBaby[0] == 100)
			return new String("Bullish abandoned baby");

		return null;
	}

	public double barsBodyPerc(IBar bar) {
		double totalBarSize = bar.getHigh() - bar.getLow();
		double bodySize = Math.abs(bar.getClose() - bar.getOpen());

		return bodySize / totalBarSize * 100.0;
	}

	public double barsUpperHandlePerc(IBar bar) {
		double totalBarSize = bar.getHigh() - bar.getLow();
		double upperHandleSize = bar.getHigh()
				- Math.max(bar.getClose(), bar.getOpen());

		return upperHandleSize / totalBarSize * 100.0;
	}

	public double barsLowerHandlePerc(IBar bar) {
		double totalBarSize = bar.getHigh() - bar.getLow();
		double lowerHandleSize = Math.min(bar.getClose(), bar.getOpen())
				- bar.getLow();

		return lowerHandleSize / totalBarSize * 100.0;
	}

	public IBar twoBarsBullishTriggerBar(Instrument instrument, Period pPeriod,	OfferSide side, long time) throws JFException {
		return twoBarsBullishTriggerBar(instrument, pPeriod, Filter.WEEKENDS, side, time);
	}
	
	public IBar twoBarsBullishTriggerBar(Instrument instrument, Period pPeriod,	Filter filter, OfferSide side, long time) throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side, filter, 2, time, 0);
		IBar prevBar = bars.get(0);
		IBar currBar = bars.get(1);

		// bullish inside bar (previous very big and RED, current bar very
		// small)
		if (barBodyLengthStatPos(instrument, pPeriod, filter, side, currBar, 1000) <= -1.0
			&& barBodyLengthStatPos(instrument, pPeriod, filter, side, prevBar,	1000) >= 1.0
				&& prevBar.getClose() < prevBar.getOpen() // careful, avoid riksha man ! Previous bar should have significant body size relative to length !
				&& barsBodyPerc(prevBar) >= 60.0
				&& prevBar.getHigh() >= currBar.getHigh()
				&& prevBar.getLow() <= currBar.getLow())
			return prevBar;

		if (barBodyLengthStatPos(instrument, pPeriod, filter, side, currBar, 1000) <= 0.0)
			return null;

		// first check strict engulfing possibility
		int[] engulfing = indicators.cdlEngulfing(instrument, pPeriod, side, filter, 1, time, 0);
		if (engulfing != null && engulfing[0] == 100)
			return prevBar.getLow() < currBar.getLow() ? prevBar : currBar;

		// 2nd bar of the combination must be green
		if (currBar.getClose() < currBar.getOpen())
			return null;

		if (bodiesOverlapPerc(prevBar, currBar) >= 0.8) {
			// careful: pivot bar is the one with deeper low, not necessarily
			// previous !
			if (prevBar.getLow() < currBar.getLow())
				return prevBar;
			else
				return currBar;
		} else
			return null;
	}
	

	// 2nd bar body length must be at least average
	public double twoBarsBearishTrigger(Instrument instrument, Period pPeriod, Filter filter, OfferSide side, long time) throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side, filter, 2, time, 0);
		IBar prevBar = bars.get(0);
		IBar currBar = bars.get(1);

		// bearish inside bar: previous bar huge and green, this bar small
		if (barBodyLengthStatPos(instrument, pPeriod, filter, side, currBar, 1000) <= -1.0
			&& barBodyLengthStatPos(instrument, pPeriod, filter, side, prevBar,	1000) >= 1.0
				&& prevBar.getClose() > prevBar.getOpen() // careful, avoid
															// riksha man !
															// Previous bar
															// should have
															// significant body
															// size relative to
															// length !
				&& barsBodyPerc(prevBar) >= 60.0
				&& prevBar.getHigh() >= currBar.getHigh()
				&& prevBar.getLow() <= currBar.getLow())
			return prevBar.getHigh();

		if (barBodyLengthStatPos(instrument, pPeriod, filter, side, currBar, 1000) <= 0.0)
			return Double.MAX_VALUE;

		// first check strict engulfing possibility
		int[] engulfing = indicators.cdlEngulfing(instrument, pPeriod, side, filter, 1, time, 0);
		if (engulfing != null && engulfing[0] == -100)
			return prevBar.getHigh() > currBar.getHigh() ? prevBar.getHigh() : currBar.getHigh();

		// 2nd bar of the combination must be red
		if (currBar.getClose() > currBar.getOpen())
			return Double.MAX_VALUE;

		if (bodiesOverlapPerc(prevBar, currBar) >= 0.8) {
			// careful: pivot bar is the one with higher high, not necessarily
			// the previous one !
			if (prevBar.getHigh() > currBar.getHigh())
				return prevBar.getHigh();
			else
				return currBar.getHigh();
		} else
			return Double.MAX_VALUE;
	}
	
	public double twoBarsBearishTrigger(Instrument instrument, Period pPeriod, OfferSide side, long time) throws JFException {
		return twoBarsBearishTrigger(instrument, pPeriod, Filter.WEEKENDS, side, time);
	}


	private double bodiesOverlapPerc(IBar prevBar, IBar currBar) {
		// Bar1 is the left (earlier) and Bar2 the right (newer) bar
		double bar1RealBodyLow = Math
				.min(prevBar.getOpen(), prevBar.getClose());
		double bar1RealBodyHigh = Math.max(prevBar.getOpen(),
				prevBar.getClose());
		double bar1RealBodyLength = bar1RealBodyHigh - bar1RealBodyLow;

		double bar2RealBodyLow = Math
				.min(currBar.getOpen(), currBar.getClose());
		double bar2RealBodyHigh = Math.max(currBar.getOpen(),
				currBar.getClose());

		double bodiesOverlapPerc = 0;

		if (bar2RealBodyHigh >= bar1RealBodyHigh)
			bodiesOverlapPerc = (bar1RealBodyHigh - bar2RealBodyLow)
					/ bar1RealBodyLength;
		else
			bodiesOverlapPerc = (bar2RealBodyHigh - bar1RealBodyLow)
					/ bar1RealBodyLength;
		return bodiesOverlapPerc;
	}

	/**
	 * Two criteria to cancel a pending buy entry order: 1. low of the last bar
	 * was below "bottom" of the initial signal candle(s) 2. bearish pivotal
	 * highs but only if also two lower lows in its last two candles
	 * 
	 * @param initialStopLoss
	 * @param instrument
	 * @param pPeriod
	 * @param side
	 * @param shift
	 * @return
	 * @throws JFException
	 */
	public boolean longEntryCancel(double initialStopLoss,
			Instrument instrument, Period pPeriod, OfferSide side, long time)
			throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side,
				Filter.WEEKENDS, 3, time, 0);
		IBar currBar = bars.get(2);

		if (currBar.getLow() < initialStopLoss)
			return true;
		IBar prevBar1 = bars.get(1);
		IBar prevBar2 = bars.get(0);

		return currBar.getHigh() < prevBar1.getHigh()
				&& prevBar1.getHigh() > prevBar2.getHigh()
				&& currBar.getLow() < prevBar1.getLow();
	}

	public double triggerFoundBearishPivotalHighs(Instrument instrument, Period pPeriod, Filter filter, OfferSide side, long time) throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side, filter, 3, time, 0);
		IBar currBar = bars.get(2);
		IBar prevBar1 = bars.get(1);
		IBar prevBar2 = bars.get(0);

		if (currBar.getHigh() < prevBar1.getHigh()
				&& prevBar1.getHigh() > prevBar2.getHigh()) {
			return prevBar1.getHigh();
		} else {
			return Double.MAX_VALUE;
		}
	}
	
	public double triggerFoundBearishPivotalHighs(Instrument instrument, Period pPeriod, OfferSide side, long time) throws JFException {
		return triggerFoundBearishPivotalHighs(instrument, pPeriod, Filter.WEEKENDS, side, time);
	}


	public boolean startT1BL(IOrder positionOrder, Instrument instrument,
			Period pPeriod, OfferSide side,
			IIndicators.AppliedPrice appliedPrice, long time)
			throws JFException {
		// Logic if following:
		// As long as high of the bar is ABOVE top of BBands, don't start
		// trailing
		// If both Stochs go (even ONCE !) above 90 or ADX go above 40, start
		// trailing on FIRST bar which high is NOT above BBands anymore
		// BUT: independent of these two, as soon as there's a bar with LOW
		// above BBands top start trailing !
		double[][] bBands = indicators.bbands(instrument, pPeriod, side,
				AppliedPrice.CLOSE, 20, 2.0, 2.0, MaType.SMA, Filter.WEEKENDS,
				1, time, 0);
		double bBandsTop = bBands[0][0];
		List<IBar> bars = history.getBars(instrument, pPeriod, side,
				Filter.WEEKENDS, 1, time, 0);
		IBar prevBar = bars.get(0);
		if (prevBar.getLow() > bBandsTop) {
			return true;
		}
		if (!stochsHeavilyOverboughtTriggered) {
			int fastKPeriod = 14;
			int slowKPeriod = 3;
			MaType slowKMaType = MaType.SMA;
			int slowDPeriod = 3;
			MaType slowDMaType = MaType.SMA;
			double[][] stoch2 = indicators.stoch(instrument, pPeriod, side,
					fastKPeriod, slowKPeriod, slowKMaType, slowDPeriod,
					slowDMaType, Filter.WEEKENDS, 1, time, 0);
			double fastStoch = stoch2[0][0];
			double slowStoch = stoch2[1][0];

			this.stochsHeavilyOverboughtTriggered = fastStoch >= 89.0
					&& slowStoch >= 89.0;
			if (this.stochsHeavilyOverboughtTriggered) {
				DecimalFormat df1 = new DecimalFormat("#.#");
				log.printAction(Logger.logTags.ORDER.toString(), positionOrder
						.getLabel(), FXUtils.getFormatedTimeGMT(time),
						Logger.logTags.TRAILING_CONDITION_FOUND.toString()
								+ ": StochFast = " + df1.format(fastStoch)
								+ ", StochSlow = " + df1.format(slowStoch),
						positionOrder.getClosePrice(), (positionOrder
								.getStopLossPrice() - positionOrder
								.getOpenPrice()) * 10000, positionOrder
								.getProfitLossInPips(), 0);
			}
		}
		// Now check whether last bar high was above top BBands
		if (stochsHeavilyOverboughtTriggered) {
			if (prevBar.getHigh() < bBandsTop) {
				DecimalFormat df1 = new DecimalFormat("#.#####");
				log.printAction(
						Logger.logTags.ORDER.toString(),
						positionOrder.getLabel(),
						FXUtils.getFormatedTimeGMT(time),
						Logger.logTags.TRAILING_CONDITION_FOUND.toString()
								+ ": bar's high below BBands top ("
								+ df1.format(prevBar.getHigh()) + " / "
								+ df1.format(bBandsTop) + ")", positionOrder
								.getClosePrice(), (positionOrder
								.getStopLossPrice() - positionOrder
								.getOpenPrice()) * 10000, positionOrder
								.getProfitLossInPips(), 0);
			}
			return prevBar.getHigh() < bBandsTop;
		} else
			return false;
	}

	public double bearishReversalCandlePattern(Instrument instrument,
			Period pPeriod, OfferSide side, long time) throws JFException {
		double pivotHigh = Double.MAX_VALUE;
		lastBearishTrigger = TriggerType.NONE;

		if ((pivotHigh = this.oneBarBearishTrigger(instrument, pPeriod, side,
				time)) != Double.MAX_VALUE) {
			lastBearishTrigger = TriggerType.BEARISH_1_BAR;
			return pivotHigh;
		} else if ((pivotHigh = this.twoBarsBearishTrigger(instrument, pPeriod,
				side, time)) != Double.MAX_VALUE) {
			lastBearishTrigger = TriggerType.BEARISH_2_BARS;
			return pivotHigh;

		} else if ((pivotHigh = this.triggerFoundBearishPivotalHighs(
				instrument, pPeriod, side, time)) != Double.MAX_VALUE) {
			lastBearishTrigger = TriggerType.BEARISH_3_BARS;
			return pivotHigh;
		}
		return Double.MAX_VALUE;
	}

	public TriggerDesc bearishReversalCandlePatternDesc(Instrument instrument, Period pPeriod, OfferSide side, long time) throws JFException {
		return bearishReversalCandlePatternDesc(instrument, pPeriod, Filter.WEEKENDS, side, time);
	}

	private double getUpperHandleMiddle(IBar bar) {
		double realBodyTop = Math.max(bar.getOpen(), bar.getClose());
		return realBodyTop + (bar.getHigh() - realBodyTop) / 2;
	}

	public double barLengthStatPos(Instrument instrument, Period pPeriod, OfferSide side, IBar forBar, int lookBack) throws JFException {
		return barLengthStatPos(instrument, pPeriod, Filter.WEEKENDS, side, forBar, lookBack);
	}

	public double barLengthStatPos(Instrument instrument, Period pPeriod, Filter filter, OfferSide side, IBar forBar, int lookBack) throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side, filter, lookBack - 1, forBar.getTime(), 0);
		// include current bar in calc !
		bars.add(forBar);
		double[] barLengths = new double[bars.size()];
		int i = 0;
		for (IBar currBar : bars) {
			barLengths[i++] = currBar.getHigh() - currBar.getLow();
		}
		double[] stDevStats = FXUtils.sdFast(barLengths);
		return ((forBar.getHigh() - forBar.getLow()) - stDevStats[0])
				/ stDevStats[1];
	}
	
	public double barLengthStatPos(Instrument instrument, Period pPeriod, OfferSide side, IBar forBar, double combinedHigh, double combinedLow, int lookBack) throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side, Filter.WEEKENDS, lookBack - 1, forBar.getTime(), 0);
		// include current bar in calc !
		bars.add(forBar);
		double[] barLengths = new double[bars.size()];
		int i = 0;
		for (IBar currBar : bars) {
			barLengths[i++] = currBar.getHigh() - currBar.getLow();
		}
		double[] stDevStats = FXUtils.sdFast(barLengths);
		return ((combinedHigh - combinedLow) - stDevStats[0]) / stDevStats[1];
	}

	/**
	 * @return in pips
	 */
	public double avgBarLength(Instrument instrument, Period pPeriod,
			OfferSide side, IBar forBar, int lookBack) throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side,
				Filter.WEEKENDS, lookBack - 1, forBar.getTime(), 0);
		// include current bar in calc !
		bars.add(forBar);
		if (bars.size() == 0)
			return 0.0;

		double barLengths = 0.0;
		for (IBar currBar : bars) {
			barLengths += currBar.getHigh() - currBar.getLow();
		}
		return (barLengths / bars.size())
				* Math.pow(10, instrument.getPipScale());
	}

	public double barBodyLengthStatPos(Instrument instrument, Period pPeriod,	OfferSide side, IBar forBar, int lookBack) throws JFException {
		return barBodyLengthStatPos(instrument, pPeriod, Filter.WEEKENDS, side, forBar, lookBack);
	}
	
	public double barBodyLengthStatPos(Instrument instrument, Period pPeriod, Filter filter, OfferSide side, IBar forBar, int lookBack) throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side, filter, lookBack - 1, forBar.getTime(), 0);
		// include current bar in calc !
		bars.add(forBar);
		double[] barBodyLengths = new double[bars.size()];
		int i = 0;
		for (IBar currBar : bars) {
			barBodyLengths[i++] = Math.max(currBar.getOpen(),
					currBar.getClose())
					- Math.min(currBar.getOpen(), currBar.getClose());
		}
		double[] stDevStats = FXUtils.sdFast(barBodyLengths);
		double forBbarBodyLength = Math
				.max(forBar.getOpen(), forBar.getClose())
				- Math.min(forBar.getOpen(), forBar.getClose());
		return (forBbarBodyLength - stDevStats[0]) / stDevStats[1];
	}


	public double avgHandleLength(Instrument instrument, Period pPeriod,
			OfferSide side, IBar forBar, int lookBack) throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side,
				Filter.WEEKENDS, lookBack - 1, forBar.getTime(), 0);
		// include current bar in calc !
		bars.add(forBar);
		double[] handleLengths = new double[2 * bars.size()];
		int i = 0;
		for (IBar currBar : bars) {
			double bodyTop = Math.max(currBar.getOpen(), currBar.getClose()), bodyBottom = Math
					.min(currBar.getOpen(), currBar.getClose());
			handleLengths[i++] = currBar.getHigh() - bodyTop;
			handleLengths[i++] = bodyBottom - currBar.getLow();
		}
		double[] stDevStats = FXUtils.sdFast(handleLengths);
		return stDevStats[0];
	}

	/**
	 * Two criteria to cancel a pending sell entry order: 1. high of the last
	 * bar was above "top" of the initial signal candle(s) 2. bullish pivotal
	 * lows but only if also two higher highs in its last two candles
	 * 
	 * @param initialStopLoss
	 * @param instrument
	 * @param pPeriod
	 * @param side
	 * @param shift
	 * @return
	 * @throws JFException
	 */
	public boolean shortEntryCancel(double initialStopLoss,
			Instrument instrument, Period pPeriod, OfferSide side, long time)
			throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side,
				Filter.WEEKENDS, 3, time, 0);
		IBar currBar = bars.get(2);

		if (currBar.getHigh() > initialStopLoss)
			return true;

		IBar prevBar1 = bars.get(1);
		IBar prevBar2 = bars.get(0);

		return currBar.getLow() > prevBar1.getLow()
				&& prevBar1.getLow() < prevBar2.getLow()
				&& currBar.getHigh() > prevBar1.getHigh();
	}

	public boolean startT1BH(IOrder positionOrder, Instrument instrument,
			Period pPeriod, OfferSide side, AppliedPrice close, long time)
			throws JFException {
		// Logic if following:
		// As long as low of the bar is BELOW bottom of BBands, don't start
		// trailing
		// If both Stochs go (even ONCE !) below 10 or ADX go above 40, start
		// trailing on FIRST bar which low is NOT below BBands anymore
		// BUT: independent of these two, as soon as there's a bar with HIGH
		// below BBands top start trailing !
		double[][] bBands = indicators.bbands(instrument, pPeriod, side,
				AppliedPrice.CLOSE, 20, 2.0, 2.0, MaType.SMA, Filter.WEEKENDS,
				1, time, 0);
		double bBandsBottom = bBands[Channel.BOTTOM][0];
		List<IBar> bars = history.getBars(instrument, pPeriod, side,
				Filter.WEEKENDS, 1, time, 0);
		IBar prevBar = bars.get(0);

		if (prevBar.getHigh() < bBandsBottom) {
			return true;
		}
		if (!stochsHeavilyOversoldTriggered) {
			int fastKPeriod = 14;
			int slowKPeriod = 3;
			MaType slowKMaType = MaType.SMA;
			int slowDPeriod = 3;
			MaType slowDMaType = MaType.SMA;
			double[][] stoch2 = indicators.stoch(instrument, pPeriod, side,
					fastKPeriod, slowKPeriod, slowKMaType, slowDPeriod,
					slowDMaType, Filter.WEEKENDS, 1, time, 0);
			double fastStoch = stoch2[0][0];
			double slowStoch = stoch2[1][0];

			this.stochsHeavilyOversoldTriggered = fastStoch <= 11.0
					&& slowStoch <= 11.0;
			if (this.stochsHeavilyOversoldTriggered) {
				DecimalFormat df1 = new DecimalFormat("#.#");
				log.printAction(Logger.logTags.ORDER.toString(), positionOrder
						.getLabel(), FXUtils.getFormatedTimeGMT(time),
						Logger.logTags.TRAILING_CONDITION_FOUND.toString()
								+ ": StochFast = " + df1.format(fastStoch)
								+ ", StochSlow = " + df1.format(slowStoch),
						positionOrder.getClosePrice(), (positionOrder
								.getOpenPrice() - positionOrder
								.getStopLossPrice()) * 10000, positionOrder
								.getProfitLossInPips(), 0);
			}
		}
		// Now check whether last bar low was below BBands bottom
		if (stochsHeavilyOversoldTriggered) {
			if (prevBar.getLow() > bBandsBottom) {
				DecimalFormat df1 = new DecimalFormat("#.#####");
				log.printAction(
						Logger.logTags.ORDER.toString(),
						positionOrder.getLabel(),
						FXUtils.getFormatedTimeGMT(time),
						Logger.logTags.TRAILING_CONDITION_FOUND.toString()
								+ ": bar's low above BBands bottom ("
								+ df1.format(prevBar.getLow()) + " / "
								+ df1.format(bBandsBottom) + ")", positionOrder
								.getClosePrice(), (positionOrder
								.getStopLossPrice() - positionOrder
								.getOpenPrice()) * 10000, positionOrder
								.getProfitLossInPips(), 0);
			}
			return prevBar.getLow() > bBandsBottom;
		} else
			return false;
	}

	public TriggerType getLastBullishTrigger() {
		return lastBullishTrigger;
	}

	public TriggerType getLastBearishTrigger() {
		return lastBearishTrigger;
	}

	public double previousBarOverlap(Instrument instrument, Period pPeriod,
			OfferSide side, long time) throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side,
				Filter.WEEKENDS, 2, time, 0);
		IBar prevBar = bars.get(0);
		IBar currBar = bars.get(1);
		double prevBarHeight = prevBar.getHigh() - prevBar.getLow();

		if (currBar.getHigh() > prevBar.getHigh()) {
			if (currBar.getLow() >= prevBar.getHigh())
				return 0.0;
			else if (currBar.getLow() > prevBar.getLow())
				return (prevBar.getHigh() - currBar.getLow()) / prevBarHeight
						* 100.0;
			else
				return 100.0;

		} else if (currBar.getHigh() <= prevBar.getLow())
			return 0.0;
		else if (currBar.getLow() < prevBar.getLow())
			return (currBar.getHigh() - prevBar.getLow()) / prevBarHeight
					* 100.0;
		else
			return (currBar.getHigh() - currBar.getLow()) / prevBarHeight
					* 100.0;

	}

	public double[] priceChannelPos(Instrument instrument, Period pPeriod, OfferSide side, long time, double price, int backBars) throws JFException {
		return priceChannelPos(instrument, pPeriod, Filter.WEEKENDS, side, time, price, backBars);
	}

	public double[] priceChannelPos(Instrument instrument, Period pPeriod, Filter filter, OfferSide side, long time, double price, int backBars) throws JFException {
		double[][] bBands = indicators.bbands(instrument, pPeriod, side, AppliedPrice.CLOSE, 20, 2.0, 2.0, MaType.SMA, filter,	2, time, 0);
		double bBandsWidth = bBands[0][1 - backBars] - bBands[2][1 - backBars];
		double bBandsBottom = bBands[2][1 - backBars];
		double[] results = new double[4];
		results[0] = (price - bBandsBottom) / bBandsWidth * 100;
		results[1] = bBands[0][1- backBars];
		results[2] = bBandsBottom;
		results[3] = bBandsWidth;
		return results;
	}
	
	public String[] parseCandleTriggers(String valueToShow) {
		String bullishCandles = null, bearishCandles = null;
		String[] result = new String[2];
		result[0] = null;
		result[1] = null;

		if (valueToShow != null && !valueToShow.toUpperCase().equals("NONE")
				&& !valueToShow.toLowerCase().equals("n/a")) {
			int positionOfAnd = valueToShow.indexOf(" AND ");
			if (positionOfAnd > 0) {
				String firstCandle = valueToShow.substring(0, positionOfAnd);
				String secondCandle = valueToShow.substring(positionOfAnd + 5);
				if (firstCandle.contains("BULLISH")) {
					bullishCandles = new String(firstCandle);
					bearishCandles = new String(secondCandle);
					result[0] = new String(bullishCandles);
					result[1] = new String(bearishCandles);
				} else {
					bullishCandles = new String(secondCandle);
					bearishCandles = new String(firstCandle);
					result[0] = new String(bullishCandles);
					result[1] = new String(bearishCandles);
				}
			} else {
				if (valueToShow.contains("BULLISH")) {
					bullishCandles = new String(valueToShow);
					result[0] = new String(bullishCandles);
				} else {
					bearishCandles = new String(valueToShow);
					result[1] = new String(bearishCandles);
				}
			}
		}
		return result;
	}

	public TriggerDesc bullishReversalCandlePatternDesc(Instrument instrument,	Period pPeriod, Filter filter, OfferSide side, long time) throws JFException {
		lastBullishTrigger = TriggerType.NONE;
		IBar pivotLowBar = null;
		if ((pivotLowBar = this.oneBarBullishTriggerBar(instrument, pPeriod, filter, side, time)) != null) {
			lastBullishTrigger = TriggerType.BULLISH_1_BAR;
			IBar[] patternBars = new IBar[1];
			patternBars[0] = pivotLowBar;
			// calc % of the combined candle
			double range = pivotLowBar.getHigh() - pivotLowBar.getLow(), bodyTop = Math
					.max(pivotLowBar.getOpen(), pivotLowBar.getClose()), bodyBottom = Math
					.min(pivotLowBar.getOpen(), pivotLowBar.getClose());
			double[] bBandsDesc = priceChannelPos(instrument, pPeriod, filter, side, time, pivotLowBar.getLow(), 0);

			return new TriggerDesc(TriggerType.BULLISH_1_BAR, 1, patternBars,
					pivotLowBar.getLow(), getLowerHandleMiddle(pivotLowBar),
					bBandsDesc[0], bBandsDesc[1], bBandsDesc[2], bBandsDesc[3],
					priceKeltnerChannelPos(instrument, pPeriod, filter, side, time,	pivotLowBar.getLow(), 0),
					(pivotLowBar.getHigh() - bodyTop) / range * 100.0,
					(bodyTop - bodyBottom) / range * 100.0,
					(bodyBottom - pivotLowBar.getLow()) / range * 100.0,
					pivotLowBar.getClose() > pivotLowBar.getOpen());
		} else if ((pivotLowBar = this.twoBarsBullishTriggerBar(instrument,	pPeriod, filter, side, time)) != null) {
			List<IBar> bars = history.getBars(instrument, pPeriod, side, filter, 2, time, 0);
			IBar prevBar = bars.get(0);
			IBar currBar = bars.get(1);
			IBar[] patternBars = new IBar[2];
			patternBars[0] = prevBar;
			patternBars[1] = currBar;
			// calc % of the combined candle
			double h = Math.max(prevBar.getHigh(), currBar.getHigh()), l = Math
					.min(prevBar.getLow(), currBar.getLow()), o = prevBar
					.getOpen(), c = currBar.getClose(), range = h - l, bodyTop = Math
					.max(o, c), bodyBottom = Math.min(o, c);
			double[] bBandsDesc = priceChannelPos(instrument, pPeriod, filter, side, time, pivotLowBar.getLow(), prevBar.getLow() < currBar.getLow() ? 1 : 0);

			lastBullishTrigger = TriggerType.BULLISH_2_BARS;
			return new TriggerDesc(TriggerType.BULLISH_2_BARS, 2,
					patternBars,
					pivotLowBar.getLow(),
					// srLevel is simply last bar middle. Maybe something more
					// precise (with handles) can be done...
					currBar.getLow() + (currBar.getHigh() - currBar.getLow()) / 2, 
					bBandsDesc[0], bBandsDesc[1], bBandsDesc[2], bBandsDesc[3], 
					priceKeltnerChannelPos(instrument, pPeriod, filter,	side, time, pivotLowBar.getLow(),
					prevBar.getLow() < currBar.getLow() ? 1 : 0),
					(h - bodyTop) / range * 100.0, (bodyTop - bodyBottom) / range * 100.0, (bodyBottom - l) / range * 100.0,
					bodyTop > bodyBottom);
		} else if ((pivotLowBar = this.bullishPivotalLowsTriggerBar(instrument,	pPeriod, filter, side, time)) != null) {
			lastBullishTrigger = TriggerType.BULLISH_3_BARS;
			List<IBar> bars = history.getBars(instrument, pPeriod, side, filter, 3, time, 0);
			IBar firstBar = bars.get(0);
			IBar middleBar = bars.get(1);
			IBar currBar = bars.get(2);
			IBar[] patternBars = new IBar[3];
			patternBars[0] = firstBar;
			patternBars[1] = middleBar;
			patternBars[2] = currBar;
			// calc % of the combined candle
			double 
				h = Math.max(Math.max(firstBar.getHigh(), middleBar.getHigh()),	currBar.getHigh()), l = pivotLowBar.getLow(), 
				o = firstBar.getOpen(), c = currBar.getClose(), 
				range = h - l, 
				bodyTop = Math.max(o, c), 
				bodyBottom = Math.min(o, c);
			double[] bBandsDesc = priceChannelPos(instrument, pPeriod, filter, side, time, pivotLowBar.getLow(), 1);

			return new TriggerDesc(TriggerType.BULLISH_3_BARS, 3, patternBars,
					pivotLowBar.getLow(),
					getBullishPivotalThreeBarsSRLevel(middleBar),
					bBandsDesc[0], bBandsDesc[1], bBandsDesc[2], bBandsDesc[3],
					priceKeltnerChannelPos(instrument, pPeriod, filter, side, time,	pivotLowBar.getLow(), 1), 
					(h - bodyTop) / range * 100.0, 
					(bodyTop - bodyBottom) / range * 100.0,
					(bodyBottom - l) / range * 100.0, c > o);
		} else if ((pivotLowBar = this.bullishMaribouzuBar(instrument, pPeriod,	filter, side, time)) != null) {
			lastBullishTrigger = TriggerType.BULLISH_1_BAR;
			IBar[] patternBars = new IBar[1];
			patternBars[0] = pivotLowBar;
			// calc % of the combined candle
			double range = pivotLowBar.getHigh() - pivotLowBar.getLow(), bodyTop = Math
					.max(pivotLowBar.getOpen(), pivotLowBar.getClose()), bodyBottom = Math
					.min(pivotLowBar.getOpen(), pivotLowBar.getClose());
			double[] bBandsDesc = priceChannelPos(instrument, pPeriod, filter, side, time, pivotLowBar.getLow(), 0);

			return new TriggerDesc(TriggerType.BULLISH_1_BAR, 1, patternBars,
					pivotLowBar.getLow(), getLowerHandleMiddle(pivotLowBar),
					bBandsDesc[0], bBandsDesc[1], bBandsDesc[2], bBandsDesc[3],
					priceKeltnerChannelPos(instrument, pPeriod, filter, side, time,	pivotLowBar.getLow(), 0),
					(pivotLowBar.getHigh() - bodyTop) / range * 100.0,
					(bodyTop - bodyBottom) / range * 100.0,
					(bodyBottom - pivotLowBar.getLow()) / range * 100.0,
					pivotLowBar.getClose() > pivotLowBar.getOpen());
		} else
			return null;
	}

	public TriggerDesc bearishReversalCandlePatternDesc(Instrument instrument, Period pPeriod, Filter filter, OfferSide side, long time) throws JFException {
		double pivotHigh = Double.MAX_VALUE;
		lastBearishTrigger = TriggerType.NONE;

		if ((pivotHigh = this.oneBarBearishTrigger(instrument, pPeriod, filter, side, time)) != Double.MAX_VALUE) {
			lastBearishTrigger = TriggerType.BEARISH_1_BAR;
			List<IBar> bars = history.getBars(instrument, pPeriod, side, filter, 1, time, 0);
			IBar[] patternBars = new IBar[1];
			patternBars[0] = bars.get(0);
			// calc % of the combined candle
			double range = patternBars[0].getHigh() - patternBars[0].getLow(), bodyTop = Math
					.max(patternBars[0].getOpen(), patternBars[0].getClose()), bodyBottom = Math
					.min(patternBars[0].getOpen(), patternBars[0].getClose());
			double[] bBandsDesc = priceChannelPos(instrument, pPeriod, filter, side, time, pivotHigh, 0);

			return new TriggerDesc(TriggerType.BEARISH_1_BAR, 1, patternBars,
					pivotHigh,
					getUpperHandleMiddle(history.getBars(instrument, pPeriod, side, filter, 1, time, 0).get(0)),
					bBandsDesc[0], bBandsDesc[1], bBandsDesc[2], bBandsDesc[3],
					priceKeltnerChannelPos(instrument, pPeriod, filter, side, time,	pivotHigh, 0), 
					(patternBars[0].getHigh() - bodyTop) / range * 100.0, 
					(bodyTop - bodyBottom) / range * 100.0, (bodyBottom - patternBars[0].getLow()) / range * 100.0,
					patternBars[0].getClose() > patternBars[0].getOpen());
		} else if ((pivotHigh = this.twoBarsBearishTrigger(instrument, pPeriod,	filter, side, time)) != Double.MAX_VALUE) {
			List<IBar> bars = history.getBars(instrument, pPeriod, side, filter, 2, time, 0);
			IBar prevBar = bars.get(0);
			IBar currBar = bars.get(1);
			IBar[] patternBars = new IBar[2];
			patternBars[0] = prevBar;
			patternBars[1] = currBar;
			// calc % of the combined candle
			double h = Math.max(prevBar.getHigh(), currBar.getHigh()), l = Math
					.min(prevBar.getLow(), currBar.getLow()), o = prevBar
					.getOpen(), c = currBar.getClose(), range = h - l, bodyTop = Math
					.max(o, c), bodyBottom = Math.min(o, c);
			double[] bBandsDesc = priceChannelPos(instrument, pPeriod, filter, side, time, pivotHigh, prevBar.getHigh() > currBar.getHigh() ? 1	: 0);

			lastBearishTrigger = TriggerType.BEARISH_2_BARS;
			return new TriggerDesc(TriggerType.BEARISH_2_BARS, 2,
					patternBars,
					pivotHigh,
					// srLevel is simply last bar middle. Maybe something more
					// precise (with handles) can be done...
					currBar.getLow() + (currBar.getHigh() - currBar.getLow())
							/ 2, bBandsDesc[0], bBandsDesc[1], bBandsDesc[2],
					bBandsDesc[3], priceKeltnerChannelPos(instrument, pPeriod, filter, 	side, time, pivotHigh,	prevBar.getHigh() > currBar.getHigh() ? 1 : 0),
					(h - bodyTop) / range * 100.0, (bodyTop - bodyBottom) / range * 100.0, (bodyBottom - l) / range * 100.0,
					c > o);

		} else if ((pivotHigh = this.triggerFoundBearishPivotalHighs(instrument, pPeriod, filter, side, time)) != Double.MAX_VALUE) {
			List<IBar> bars = history.getBars(instrument, pPeriod, side, filter, 3, time, 0);
			IBar firstBar = bars.get(0);
			IBar middleBar = bars.get(1);
			IBar currBar = bars.get(2);
			IBar[] patternBars = new IBar[3];
			patternBars[0] = firstBar;
			patternBars[1] = middleBar;
			patternBars[2] = currBar;
			// calc % of the combined candle
			double h = pivotHigh, l = Math.min(
					Math.min(firstBar.getLow(), middleBar.getLow()),
					currBar.getLow()), o = firstBar.getOpen(), c = currBar
					.getClose(), range = h - l, bodyTop = Math.max(o, c), bodyBottom = Math
					.min(o, c);
			double[] bBandsDesc = priceChannelPos(instrument, pPeriod, filter, side, time, pivotHigh, 1);

			lastBearishTrigger = TriggerType.BEARISH_3_BARS;
			return new TriggerDesc(TriggerType.BEARISH_3_BARS, 3,
					patternBars,
					pivotHigh,
					// S/R level for bearish 3-bar is middle of middle bar's
					// UPPER handle
					getUpperHandleMiddle(middleBar), bBandsDesc[0],
					bBandsDesc[1], bBandsDesc[2], bBandsDesc[3],
					priceKeltnerChannelPos(instrument, pPeriod, filter, side, time,	pivotHigh, 1), (h - bodyTop) / range * 100.0,
					(bodyTop - bodyBottom) / range * 100.0, (bodyBottom - l) / range * 100.0, c > o);
		} else if ((pivotHigh = this.bearishMaribouzuBar(instrument, pPeriod, filter, side, time)) != Double.MAX_VALUE) {
			lastBearishTrigger = TriggerType.BEARISH_1_BAR;
			List<IBar> bars = history.getBars(instrument, pPeriod, side, filter, 1, time, 0);
			IBar[] patternBars = new IBar[1];
			patternBars[0] = bars.get(0);
			// calc % of the combined candle
			double range = patternBars[0].getHigh() - patternBars[0].getLow(), bodyTop = Math
					.max(patternBars[0].getOpen(), patternBars[0].getClose()), bodyBottom = Math
					.min(patternBars[0].getOpen(), patternBars[0].getClose());
			double[] bBandsDesc = priceChannelPos(instrument, pPeriod, filter, side, time, pivotHigh, 0);

			return new TriggerDesc(TriggerType.BEARISH_1_BAR, 1, patternBars,
					pivotHigh,
					getUpperHandleMiddle(history.getBars(instrument, pPeriod, side, filter, 1, time, 0).get(0)),
					bBandsDesc[0], bBandsDesc[1], bBandsDesc[2], bBandsDesc[3],
					priceKeltnerChannelPos(instrument, pPeriod, filter, side, time,	pivotHigh, 0), (patternBars[0].getHigh() - bodyTop)
							/ range * 100.0, (bodyTop - bodyBottom) / range
							* 100.0, (bodyBottom - patternBars[0].getLow())
							/ range * 100.0,
					patternBars[0].getClose() > patternBars[0].getOpen());
		} else
			return null;
	}
}