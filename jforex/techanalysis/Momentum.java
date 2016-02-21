package jforex.techanalysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import jforex.filters.FilterFactory;
import jforex.filters.IFilter;
import jforex.utils.FXUtils;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.IIndicators.MaType;

public class Momentum {

	public static int MACD_LINE = 0;
	public static int MACD_Signal = 1;
	public static int MACD_H = 2;

	public enum MACD_STATE {
		RAISING_BOTH_BELOW_0, // both below zero and MACD > Signal
		RAISING_FAST_ABOVE_0, // MACD >=0, Signal < 0 and MACD > Signal
		RAISING_BOTH_ABOVE_0, // MACD >=0, Signal >= 0 and MACD > Signal
		FALLING_BOTH_ABOVE_0, // both above zero and MACD < Signal
		FALLING_FAST_BELOW_0, // MACD <= 0, Signal > 0 and MACD < Signal
		FALLING_BOTH_BELOW_0 // MACD <= 0, Signal <= 0 and MACD < Signal
	}

	// three bars will be considered
	public enum MACD_H_STATE {
		RAISING_BELOW_0, RAISING_ABOVE_0, FALLING_ABOVE_0, FALLING_BELOW_0, TICKED_UP_BELOW_ZERO, TICKED_UP_ABOVE_ZERO, TICKED_DOWN_BELOW_ZERO, TICKED_DOWN_ABOVE_ZERO, NONE // backup,
																																												// if
																																												// appears
																																												// test
																																												// such
																																												// case
	}

	public enum MACD_CROSS {
		BULL_CROSS_BELOW_ZERO, BULL_CROSS_ABOVE_ZERO, BEAR_CROSS_ABOVE_ZERO, BEAR_CROSS_BELOW_ZERO, NONE
	}

	public enum STOCH_STATE {
		OVERSOLD_BOTH, // Fast and Slow both below 20
		OVERSOLD_FAST, // Fast below 20
		OVERSOLD_SLOW, // Slow below 20 but Fast not, rather bullish (raising
						// from OS)
		OVERBOUGHT_BOTH, // Fast and Slow both above 80
		OVERBOUGHT_FAST, // Fast above 80
		OVERBOUGHT_SLOW, // Slow above 80 but Fast not, rather bearish (falling
							// from OB)
		RAISING_IN_MIDDLE, // Fast above slow and none OS nor OB
		FALLING_IN_MIDDLE, // Fast below slow and none OS nor OB
		BULLISH_CROSS_FROM_OVERSOLD, // at least one line below 20
		BULLISH_CROSS, BEARISH_CROSS_FROM_OVERBOUGTH, // at least one line above
														// 80
		BEARISH_CROSS, NONE
	}

	// for RSI and CCI states
	public enum SINGLE_LINE_STATE {
		RAISING_IN_MIDDLE, FALLING_IN_MIDDLE, TICKED_DOWN_IN_MIDDLE, TICKED_UP_IN_MIDDLE, FALLING_OVERSOLD, RAISING_OVERSOLD, TICKED_DOWN_OVERSOLD, TICKED_UP_FROM_OVERSOLD, // the
																																												// most
																																												// interesting
		FALLING_OVERBOUGHT, RAISING_OVERBOUGHT, TICKED_DOWN_FROM_OVERBOUGHT, // the
																				// most
																				// interesting
		TICKED_UP_OVERBOUGHT, NONE
	}

	private IHistory history;
	private IIndicators indicators;
	private IFilter macdSignalFilter, macdHFilter, stochsDiffFilter,
			macdSignalMACDsPositionFilter, macdHMACDsPositionFilter,
			stochsDiffMACDsPositionFilter, stochSlowMACDsPositionFilter;
	private List<IFilter> allFilters = new ArrayList<IFilter>();

	private double[] macds = new double[3];
	private double[] stochs = new double[2];

	// TODO: separate calc time needed per instrument and timeframe !!!!

	public Momentum(IHistory pHistory, IIndicators indicators) {
		super();
		this.indicators = indicators;
		this.history = pHistory;
	}

	public Momentum(IHistory pHistory, IIndicators indicators,
			Properties filters) {
		super();
		this.indicators = indicators;
		this.history = pHistory;
		if (filters.containsKey(FilterFactory.FILTER_PREFIX
				+ "30min_MACDSignal")) {
			macdSignalFilter = FilterFactory.createFilter(
					FilterFactory.FILTER_PREFIX + "30min_MACDSignal",
					filters.getProperty(FilterFactory.FILTER_PREFIX
							+ "30min_MACDSignal"), indicators, history);
			allFilters.add(macdSignalFilter);
		}
		if (filters.containsKey(FilterFactory.FILTER_PREFIX
				+ "30min_MACDHistogram")) {
			macdHFilter = FilterFactory.createFilter(
					FilterFactory.FILTER_PREFIX + "30min_MACDHistogram",
					filters.getProperty(FilterFactory.FILTER_PREFIX
							+ "30min_MACDHistogram"), indicators, history);
			allFilters.add(macdHFilter);
		}
		if (filters.containsKey(FilterFactory.FILTER_PREFIX
				+ "30min_StochsDiff")) {
			stochsDiffFilter = FilterFactory.createFilter(
					FilterFactory.FILTER_PREFIX + "30min_StochsDiff",
					filters.getProperty(FilterFactory.FILTER_PREFIX
							+ "30min_StochsDiff"), indicators, history);
			allFilters.add(stochsDiffFilter);
		}
		if (filters.containsKey(FilterFactory.FILTER_CONDITIONAL_PREFIX
				+ "30min_MACDSignal_MACDsPosition_30min")) {
			macdSignalMACDsPositionFilter = FilterFactory.createFilter(
					FilterFactory.FILTER_CONDITIONAL_PREFIX
							+ "30min_MACDSignal_MACDsPosition_30min",
					filters.getProperty(FilterFactory.FILTER_CONDITIONAL_PREFIX
							+ "30min_MACDSignal_MACDsPosition_30min"),
					indicators, history);
			allFilters.add(macdSignalMACDsPositionFilter);
		}
		if (filters.containsKey(FilterFactory.FILTER_CONDITIONAL_PREFIX
				+ "30min_MACDHistogram_MACDsPosition_30min")) {
			macdHMACDsPositionFilter = FilterFactory.createFilter(
					FilterFactory.FILTER_CONDITIONAL_PREFIX
							+ "30min_MACDHistogram_MACDsPosition_30min",
					filters.getProperty(FilterFactory.FILTER_CONDITIONAL_PREFIX
							+ "30min_MACDHistogram_MACDsPosition_30min"),
					indicators, history);
			allFilters.add(macdHMACDsPositionFilter);
		}
		if (filters.containsKey(FilterFactory.FILTER_CONDITIONAL_PREFIX
				+ "30min_StochsDiff_MACDsPosition_30min")) {
			stochsDiffMACDsPositionFilter = FilterFactory.createFilter(
					FilterFactory.FILTER_CONDITIONAL_PREFIX
							+ "30min_StochsDiff_MACDsPosition_30min",
					filters.getProperty(FilterFactory.FILTER_CONDITIONAL_PREFIX
							+ "30min_StochsDiff_MACDsPosition_30min"),
					indicators, history);
			allFilters.add(stochsDiffMACDsPositionFilter);
		}
		if (filters.containsKey(FilterFactory.FILTER_CONDITIONAL_PREFIX
				+ "30min_StochSlow_MACDsPosition_30min")) {
			stochSlowMACDsPositionFilter = FilterFactory.createFilter(
					FilterFactory.FILTER_CONDITIONAL_PREFIX
							+ "30min_StochSlow_MACDsPosition_30min",
					filters.getProperty(FilterFactory.FILTER_CONDITIONAL_PREFIX
							+ "30min_StochSlow_MACDsPosition_30min"),
					indicators, history);
			allFilters.add(stochSlowMACDsPositionFilter);
		}
	}

	public double[] getMACDs(Instrument instrument, Period pPeriod,
			OfferSide side, IIndicators.AppliedPrice appliedPrice, long time)
			throws JFException {
		double resultTmp[][] = indicators.macd(instrument, pPeriod, side,
				appliedPrice, 26, 12, 9, Filter.WEEKENDS, 1, time, 0);
		macds[MACD_LINE] = resultTmp[MACD_LINE][0];
		macds[MACD_Signal] = resultTmp[MACD_Signal][0];
		macds[MACD_H] = resultTmp[MACD_H][0];
		return macds;
	}

	public double MACD(Instrument instrument, Period pPeriod, OfferSide side,
			IIndicators.AppliedPrice appliedPrice, long time)
			throws JFException {
		return getMACDs(instrument, pPeriod, side, appliedPrice, time)[MACD_LINE];
	}

	public double MACDSignal(Instrument instrument, Period pPeriod,
			OfferSide side, IIndicators.AppliedPrice appliedPrice, long time)
			throws JFException {
		return getMACDs(instrument, pPeriod, side, appliedPrice, time)[MACD_Signal];
	}

	public double MACDHistogram(Instrument instrument, Period pPeriod,
			OfferSide side, IIndicators.AppliedPrice appliedPrice, long time)
			throws JFException {
		return getMACDs(instrument, pPeriod, side, appliedPrice, time)[MACD_H];
	}

	/**
	 * @return last lookBackBars values of MACD-H in chronological order (zero
	 *         index oldest)
	 */
	public double[] getLastNBarsMACDHistograms(Instrument instrument,
			Period pPeriod, OfferSide side,
			IIndicators.AppliedPrice appliedPrice, long time, int lookBackBars)
			throws JFException {
		double resultTmp[][] = indicators
				.macd(instrument, pPeriod, side, appliedPrice, 26, 12, 9,
						Filter.WEEKENDS, lookBackBars, time, 0);
		double[] macdHistograms = resultTmp[MACD_H];
		return macdHistograms;
	}

	/**
	 * @return last lookBackBars values of MACD-H in chronological order (zero
	 *         index oldest)
	 */
	public double[] getLastNBarsCCI(Instrument instrument, Period pPeriod,
			OfferSide side, long time, int lookBackBars) throws JFException {
		return indicators.cci(instrument, pPeriod, side, 14, Filter.WEEKENDS,
				lookBackBars, time, 0);
	}

	/**
	 * @return last lookBackBars values of MACD-H in chronological order (zero
	 *         index oldest)
	 */
	public double[] getLastNBarsMACD(Instrument instrument, Period pPeriod,
			OfferSide side, IIndicators.AppliedPrice appliedPrice, long time,
			int lookBackBars) throws JFException {
		double resultTmp[][] = indicators
				.macd(instrument, pPeriod, side, appliedPrice, 26, 12, 9,
						Filter.WEEKENDS, lookBackBars, time, 0);
		double[] macdHistograms = resultTmp[MACD_LINE];
		return macdHistograms;
	}

	public double getMACDStDevPos(Instrument instrument, Period pPeriod,
			OfferSide side, IIndicators.AppliedPrice appliedPrice, long time,
			int lookBackBars) throws JFException {
		double[] macds = getLastNBarsMACD(instrument, pPeriod, side,
				appliedPrice, time, lookBackBars);
		double[] stDevStats = FXUtils.sdFast(macds);
		return (macds[lookBackBars - 1] - stDevStats[0]) / stDevStats[1];
	}

	public MACD_CROSS getMACDCross(Instrument instrument, Period pPeriod,
			OfferSide side, IIndicators.AppliedPrice appliedPrice, long time)
			throws JFException {
		macds = getMACDs(instrument, pPeriod, side, appliedPrice, time);
		double[] last2Histograms = getLastNBarsMACDHistograms(instrument,
				pPeriod, side, appliedPrice, time, 2);
		boolean bullishCross = last2Histograms[0] < 0.0
				&& last2Histograms[1] > 0.0;
		boolean bearishCross = last2Histograms[0] > 0.0
				&& last2Histograms[1] < 0.0;

		// only MACD Signal determines whether cross happened below or above
		// zero
		if (bullishCross && macds[MACD_Signal] < 0)
			return MACD_CROSS.BULL_CROSS_BELOW_ZERO;
		else if (bullishCross && macds[MACD_Signal] >= 0)
			return MACD_CROSS.BULL_CROSS_ABOVE_ZERO;
		else if (bearishCross && macds[MACD_Signal] < 0)
			return MACD_CROSS.BEAR_CROSS_BELOW_ZERO;
		else if (bearishCross && macds[MACD_Signal] >= 0)
			return MACD_CROSS.BEAR_CROSS_ABOVE_ZERO;
		else
			return MACD_CROSS.NONE;
	}

	public MACD_STATE getMACDState(Instrument instrument, Period pPeriod,
			OfferSide side, IIndicators.AppliedPrice appliedPrice, long time)
			throws JFException {
		macds = getMACDs(instrument, pPeriod, side, appliedPrice, time);
		if (macds[MACD_LINE] > macds[MACD_Signal] && macds[MACD_LINE] < 0
				&& macds[MACD_Signal] < 0)
			return MACD_STATE.RAISING_BOTH_BELOW_0;
		else if (macds[MACD_LINE] > macds[MACD_Signal] && macds[MACD_LINE] >= 0
				&& macds[MACD_Signal] < 0)
			return MACD_STATE.RAISING_FAST_ABOVE_0;
		else if (macds[MACD_LINE] > macds[MACD_Signal] && macds[MACD_LINE] >= 0
				&& macds[MACD_Signal] >= 0)
			return MACD_STATE.RAISING_BOTH_ABOVE_0;
		else if (macds[MACD_LINE] <= macds[MACD_Signal] && macds[MACD_LINE] > 0
				&& macds[MACD_Signal] > 0)
			return MACD_STATE.FALLING_BOTH_ABOVE_0;
		else if (macds[MACD_LINE] <= macds[MACD_Signal]
				&& macds[MACD_LINE] <= 0 && macds[MACD_Signal] > 0)
			return MACD_STATE.FALLING_FAST_BELOW_0;
		else
			return MACD_STATE.FALLING_BOTH_BELOW_0;
	}

	public MACD_H_STATE getMACDHistogramState(Instrument instrument,
			Period pPeriod, OfferSide side,
			IIndicators.AppliedPrice appliedPrice, long time)
			throws JFException {
		// zero-index OLDEST !
		double[] last3MACDHs = getLastNBarsMACDHistograms(instrument, pPeriod,
				side, appliedPrice, time, 3);

		// raising ?
		if (last3MACDHs[2] >= last3MACDHs[1]
				&& last3MACDHs[1] >= last3MACDHs[0]) {
			if (last3MACDHs[2] > 0)
				return MACD_H_STATE.RAISING_ABOVE_0;
			else
				return MACD_H_STATE.RAISING_BELOW_0;
		}
		// falling ?
		else if (last3MACDHs[2] <= last3MACDHs[1]
				&& last3MACDHs[1] <= last3MACDHs[0]) {
			if (last3MACDHs[2] > 0)
				return MACD_H_STATE.FALLING_ABOVE_0;
			else
				return MACD_H_STATE.FALLING_BELOW_0;
		}
		// ticked up ?
		else if (last3MACDHs[2] >= last3MACDHs[1]
				&& last3MACDHs[1] <= last3MACDHs[0]) {
			if (last3MACDHs[1] > 0)
				return MACD_H_STATE.TICKED_UP_ABOVE_ZERO;
			else
				return MACD_H_STATE.TICKED_UP_BELOW_ZERO;
		}
		// ticked down ?
		else if (last3MACDHs[2] <= last3MACDHs[1]
				&& last3MACDHs[1] >= last3MACDHs[0]) {
			if (last3MACDHs[1] > 0)
				return MACD_H_STATE.TICKED_DOWN_ABOVE_ZERO;
			else
				return MACD_H_STATE.TICKED_DOWN_BELOW_ZERO;
		} else
			return MACD_H_STATE.NONE;
	}

	public SINGLE_LINE_STATE getCCIState(Instrument instrument, Period pPeriod,
			OfferSide side, long time) throws JFException {
		// zero-index OLDEST !
		double[] last3CCI = getLastNBarsCCI(instrument, pPeriod, side, time, 3);
		double lastCCI = last3CCI[2], middleCCI = last3CCI[1], firstCCI = last3CCI[0];

		// pivot one in the middle, and last either oversold or overbought
		if (middleCCI < 190 && middleCCI > -190 && lastCCI < 190
				&& lastCCI > -190) {
			if (lastCCI > middleCCI && middleCCI > firstCCI)
				return SINGLE_LINE_STATE.RAISING_IN_MIDDLE;
			else if (lastCCI < middleCCI && middleCCI < firstCCI)
				return SINGLE_LINE_STATE.FALLING_IN_MIDDLE;
			else if (lastCCI > middleCCI && middleCCI < firstCCI)
				return SINGLE_LINE_STATE.TICKED_UP_IN_MIDDLE;
			else
				return SINGLE_LINE_STATE.TICKED_DOWN_IN_MIDDLE;
		} else if (middleCCI > 190 && firstCCI < middleCCI
				&& middleCCI > lastCCI) { // middle overbought
			return SINGLE_LINE_STATE.TICKED_DOWN_FROM_OVERBOUGHT;
		} else if (lastCCI > 190) { // all overbought states here
			if (lastCCI > middleCCI && middleCCI > firstCCI)
				return SINGLE_LINE_STATE.RAISING_OVERBOUGHT;
			else if (lastCCI < middleCCI && middleCCI < firstCCI)
				return SINGLE_LINE_STATE.FALLING_OVERBOUGHT;
			else if (lastCCI > middleCCI && middleCCI < firstCCI)
				return SINGLE_LINE_STATE.TICKED_UP_OVERBOUGHT;
			else
				return SINGLE_LINE_STATE.TICKED_DOWN_FROM_OVERBOUGHT;
		} else if (middleCCI < 190 && firstCCI < middleCCI
				&& middleCCI > lastCCI) { // middle overbought
			return SINGLE_LINE_STATE.TICKED_UP_FROM_OVERSOLD;
		} else if (lastCCI < -190) { // all oversold states here
			if (lastCCI > middleCCI && middleCCI > firstCCI)
				return SINGLE_LINE_STATE.RAISING_OVERSOLD;
			else if (lastCCI < middleCCI && middleCCI < firstCCI)
				return SINGLE_LINE_STATE.FALLING_OVERSOLD;
			else if (lastCCI > middleCCI && middleCCI < firstCCI)
				return SINGLE_LINE_STATE.TICKED_UP_FROM_OVERSOLD;
			else
				return SINGLE_LINE_STATE.TICKED_DOWN_OVERSOLD;
		}
		return SINGLE_LINE_STATE.NONE;
	}

	public SINGLE_LINE_STATE getRSIState(Instrument instrument, Period pPeriod,
			OfferSide side, long time) throws JFException {
		// zero-index OLDEST !
		double[] last3RSI = getLastNBarsRSI(instrument, pPeriod, side,
				AppliedPrice.CLOSE, time, 3);
		double lastRSI = last3RSI[2], middleRSI = last3RSI[1], firstRSI = last3RSI[0];

		// pivot one in the middle, and last either oversold or overbought
		if (middleRSI < 68 && middleRSI > 32 && lastRSI < 68 && lastRSI > 32) {
			if (lastRSI > middleRSI && middleRSI > firstRSI)
				return SINGLE_LINE_STATE.RAISING_IN_MIDDLE;
			else if (lastRSI < middleRSI && middleRSI < firstRSI)
				return SINGLE_LINE_STATE.FALLING_IN_MIDDLE;
			else if (lastRSI > middleRSI && middleRSI < firstRSI)
				return SINGLE_LINE_STATE.TICKED_UP_IN_MIDDLE;
			else
				return SINGLE_LINE_STATE.TICKED_DOWN_IN_MIDDLE;
		} else if (middleRSI > 68 && firstRSI < middleRSI
				&& middleRSI > lastRSI) { // middle overbought
			return SINGLE_LINE_STATE.TICKED_DOWN_FROM_OVERBOUGHT;
		} else if (lastRSI > 68) { // all overbought states here
			if (lastRSI > middleRSI && middleRSI > firstRSI)
				return SINGLE_LINE_STATE.RAISING_OVERBOUGHT;
			else if (lastRSI < middleRSI && middleRSI < firstRSI)
				return SINGLE_LINE_STATE.FALLING_OVERBOUGHT;
			else if (lastRSI > middleRSI && middleRSI < firstRSI)
				return SINGLE_LINE_STATE.TICKED_UP_OVERBOUGHT;
			else
				return SINGLE_LINE_STATE.TICKED_DOWN_FROM_OVERBOUGHT;
		} else if (middleRSI < 68 && firstRSI < middleRSI
				&& middleRSI > lastRSI) { // middle overbought
			return SINGLE_LINE_STATE.TICKED_UP_FROM_OVERSOLD;
		} else if (lastRSI < 32) { // all oversold states here
			if (lastRSI > middleRSI && middleRSI > firstRSI)
				return SINGLE_LINE_STATE.RAISING_OVERSOLD;
			else if (lastRSI < middleRSI && middleRSI < firstRSI)
				return SINGLE_LINE_STATE.FALLING_OVERSOLD;
			else if (lastRSI > middleRSI && middleRSI < firstRSI)
				return SINGLE_LINE_STATE.TICKED_UP_FROM_OVERSOLD;
			else
				return SINGLE_LINE_STATE.TICKED_DOWN_OVERSOLD;
		}
		return SINGLE_LINE_STATE.NONE;
	}

	public double[] getStochs(Instrument instrument, Period pPeriod,
			OfferSide side, long time) throws JFException {
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

		stochs[0] = fastStoch;
		stochs[1] = slowStoch;
		return stochs;
	}

	public double[][] getStochs(Instrument instrument, Period pPeriod,
			OfferSide side, long time, int lookBack) throws JFException {
		int fastKPeriod = 14;
		int slowKPeriod = 3;
		MaType slowKMaType = MaType.SMA;
		int slowDPeriod = 3;
		MaType slowDMaType = MaType.SMA;
		return indicators.stoch(instrument, pPeriod, side, fastKPeriod,
				slowKPeriod, slowKMaType, slowDPeriod, slowDMaType,
				Filter.WEEKENDS, lookBack, time, 0);
	}

	public STOCH_STATE getStochState(Instrument instrument, Period pPeriod,
			OfferSide side, long time) throws JFException {
		getStochs(instrument, pPeriod, side, time);
		double fastStoch = stochs[0];
		double slowStoch = stochs[1];

		if (fastStoch >= 80 && slowStoch >= 80)
			return STOCH_STATE.OVERBOUGHT_BOTH;
		else if (fastStoch >= 80)
			return STOCH_STATE.OVERBOUGHT_FAST;
		else if (slowStoch >= 80)
			return STOCH_STATE.OVERBOUGHT_SLOW;
		else if (fastStoch <= 20 && slowStoch <= 20)
			return STOCH_STATE.OVERSOLD_BOTH;
		else if (fastStoch <= 20)
			return STOCH_STATE.OVERSOLD_FAST;
		else if (slowStoch <= 20)
			return STOCH_STATE.OVERSOLD_SLOW;
		else if (fastStoch > slowStoch)
			return STOCH_STATE.RAISING_IN_MIDDLE;
		else
			return STOCH_STATE.FALLING_IN_MIDDLE;
	}

	public STOCH_STATE getStochCross(Instrument instrument, Period pPeriod,
			OfferSide side, long time) throws JFException {
		double[][] lastTwoStochs = getStochs(instrument, pPeriod, side, time, 2);
		double fastStochLast = lastTwoStochs[0][1], slowStochLast = lastTwoStochs[1][1], fastStochPrev = lastTwoStochs[0][0], slowStochPrev = lastTwoStochs[1][0];

		// bullish cross
		if (fastStochLast > slowStochLast && fastStochPrev < slowStochPrev) {
			if (fastStochPrev < 20 || slowStochPrev < 20)
				return STOCH_STATE.BULLISH_CROSS_FROM_OVERSOLD;
			else
				return STOCH_STATE.BULLISH_CROSS;
		}
		// bearish cross
		if (fastStochLast < slowStochLast && fastStochPrev > slowStochPrev) {
			if (fastStochPrev > 80 || slowStochPrev > 80)
				return STOCH_STATE.BEARISH_CROSS_FROM_OVERBOUGTH;
			else
				return STOCH_STATE.BEARISH_CROSS;
		}
		return STOCH_STATE.NONE;
	}

	public double getRSI(Instrument instrument, Period pPeriod, OfferSide side,
			IIndicators.AppliedPrice appliedPrice, long time)
			throws JFException {
		return indicators.rsi(instrument, pPeriod, side, appliedPrice, 14,
				Filter.WEEKENDS, 1, time, 0)[0];
	}

	public double[] getLastNBarsRSI(Instrument instrument, Period pPeriod,
			OfferSide side, IIndicators.AppliedPrice appliedPrice, long time,
			int lookBack) throws JFException {
		return indicators.rsi(instrument, pPeriod, side, appliedPrice, 14,
				Filter.WEEKENDS, lookBack, time, 0);
	}

	public double[] getADXs(Instrument instrument, Period pPeriod,
			OfferSide side, IIndicators.AppliedPrice appliedPrice, long time)
			throws JFException {
		return indicators.adx(instrument, pPeriod, side, 14, Filter.WEEKENDS,
				1, time, 0);
	}

	public boolean momentumDown(Instrument instrument, Period pPeriod,
			OfferSide side, IIndicators.AppliedPrice appliedPrice, long time)
			throws JFException {
		// getMACDs(instrument, pPeriod, side, appliedPrice, time);
		// TODO: EXTREMELY IMPORTANT - these numbers valid only for EURUSD 30'
		// !!!! Redesign to stdev !!!
		for (IFilter f : allFilters) {
			if (!f.check(instrument, side, appliedPrice, time))
				return true;
		}
		// if (!macdHFilter.check(instrument, side, appliedPrice, time)
		// || !macdSignalFilter.check(instrument, side, appliedPrice, time))
		// return true;
		//
		// if (!stochsDiffFilter.check(instrument, side, appliedPrice, time))
		// return true;
		//
		// if (!macdSignalMACDsPositionFilter.check(instrument, side,
		// appliedPrice, time))
		// return true;
		// if (!macdHMACDsPositionFilter.check(instrument, side, appliedPrice,
		// time))
		// return true;
		// if (!stochsDiffMACDsPositionFilter.check(instrument, side,
		// appliedPrice, time))
		// return true;
		// if (!stochSlowMACDsPositionFilter.check(instrument, side,
		// appliedPrice, time))
		// return true;

		// // cascade top to bottom through 3 possible MACD/Signal setups
		// if (macds[MACD_LINE] > 0 && macds[MACD_Signal] > 0) {
		// //if (macds[MACD_Signal] > 0.00316)
		// if (!macdSignalMACDsPositionFilter.check(instrument, side,
		// appliedPrice, time))
		// return true;
		// //if (macds[MACD_H] > -0.00002)
		// if (!macdHMACDsPositionFilter.check(instrument, side, appliedPrice,
		// time))
		// return true;
		// //if (stochsDiff > 6 || stochsDiff < -14.2)
		// if (!stochsDiffMACDsPositionFilter.check(instrument, side,
		// appliedPrice, time))
		// return true;
		// //if (stochs[1] < 11.3)
		// if (!stochSlowMACDsPositionFilter.check(instrument, side,
		// appliedPrice, time))
		// return true;
		// // double[] adx = indicators.adx(instrument, pPeriod, side, 14,
		// Filter.WEEKENDS, 1, time, 0);
		// // // Bad ADX
		// // if (adx[1] < adx[2] && adx[1] < adx[0]
		// // && triggerBarsChannelPos[1] > 8.55)
		// // return true;
		//
		// }
		// else if (macds[MACD_LINE] <= 0 && macds[MACD_Signal] > 0) {
		// //if (macds[MACD_H] < -0.00096)
		// if (!macdHMACDsPositionFilter.check(instrument, side, appliedPrice,
		// time))
		// return true;
		// //if (stochsDiff > 6.9)
		// if (!stochsDiffMACDsPositionFilter.check(instrument, side,
		// appliedPrice, time))
		// return true;
		// //if (stochs[1] < 8.6)
		// if (!stochSlowMACDsPositionFilter.check(instrument, side,
		// appliedPrice, time))
		// return true;
		// }
		// else if (macds[MACD_LINE] <= 0 && macds[MACD_Signal] <= 0) {
		// //if (macds[MACD_H] < -0.0011)
		// if (!macdHMACDsPositionFilter.check(instrument, side, appliedPrice,
		// time))
		// return true;
		// if (macds[MACD_LINE] < -0.00346)
		// return true;
		// //if (stochs[1] < 7.7)
		// if (!stochSlowMACDsPositionFilter.check(instrument, side,
		// appliedPrice, time))
		// return true;
		// //if (stochsDiff < -16.8 || stochsDiff > 8.8)
		// if (!stochsDiffMACDsPositionFilter.check(instrument, side,
		// appliedPrice, time))
		// return true;
		// }
		return false;
	}

	public boolean momentumUp(Instrument instrument, Period period,
			OfferSide bid, AppliedPrice close, long time,
			double[] last2BarsChannelPos) {
		return false;
	}

	public double getMACD_HStDevPos(Instrument instrument, Period pPeriod,
			OfferSide side, AppliedPrice appliedPrice, long time,
			int lookBackBars) throws JFException {
		double[] macdHs = getLastNBarsMACDHistograms(instrument, pPeriod, side,
				appliedPrice, time, lookBackBars);
		double[] stDevStats = FXUtils.sdFast(macdHs);
		return (macdHs[lookBackBars - 1] - stDevStats[0]) / stDevStats[1];
	}

}
