package jforex.techanalysis;

import org.apache.commons.math3.stat.ranking.NaturalRanking;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.IIndicators.MaType;

public class Volatility {
	private IIndicators indicators;

	public Volatility(IIndicators indicators) {
		super();
		this.indicators = indicators;
	}

	public double getATR(Instrument instrument, Period pPeriod, Filter filter, OfferSide side, long time, int lookBack) throws JFException {
		return indicators.atr(instrument, pPeriod, side, lookBack, filter, 1, time, 0)[0];
	}
	
	public double getATR(Instrument instrument, Period pPeriod, OfferSide side,	long time, int lookBack) throws JFException {
		return getATR(instrument, pPeriod, Filter.WEEKENDS, side, time, lookBack);
	}

	public double[] getATRTimeSeries(Instrument instrument, Period pPeriod,
			OfferSide side, long time, int lookBack, int timeSeriesLength)
			throws JFException {
		return indicators.atr(instrument, pPeriod, side, lookBack,
				Filter.WEEKENDS, timeSeriesLength, time, 0);
	}

	public double[] getATRTimeSeries(Instrument instrument, Period pPeriod,
			OfferSide side, Filter filter, long time, int lookBack,
			int timeSeriesLength) throws JFException {
		return indicators.atr(instrument, pPeriod, side, lookBack, filter,
				timeSeriesLength, time, 0);
	}

	/**
	 * @param lookBack
	 * @return Ratio (%) between widths of lookBack Bolinger and Keltner Bands.
	 *         Values significantly below 100% (70% and less) indicate low
	 *         volatility state and vice versa
	 */
	public double getBBandsSqueeze(Instrument instrument, Period pPeriod,
			OfferSide side, long time, int lookBack) throws JFException {
		double[][] bBands = indicators.bbands(instrument, pPeriod, side,
				AppliedPrice.CLOSE, lookBack, 2.0, 2.0, MaType.SMA,
				Filter.WEEKENDS, 1, time, 0);
		double twoStDev = bBands[0][0] - bBands[1][0], twoATRs = 2 * getATR(
				instrument, pPeriod, side, time, lookBack);
		return twoStDev / twoATRs * 100.0;
	}

	public double getBBandsSqueeze(Instrument instrument, Period pPeriod,
			OfferSide side, Filter filter, long time, int lookBack)
			throws JFException {
		double[][] bBands = indicators.bbands(instrument, pPeriod, side,
				AppliedPrice.CLOSE, lookBack, 2.0, 2.0, MaType.SMA, filter, 1,
				time, 0);
		double twoStDev = bBands[0][0] - bBands[1][0], twoATRs = 2 * getATR(
				instrument, pPeriod, side, time, lookBack);
		return twoStDev / twoATRs * 100.0;
	}

	public double getBBandsSqueezePercentile(Instrument instrument,
			Period pPeriod, OfferSide side,
			IIndicators.AppliedPrice appliedPrice, Filter filter, long time,
			int lookback, int historyBars) throws JFException {
		double[] rawData = getBBandsSqueezes(instrument, pPeriod, side,
				appliedPrice, filter, time, lookback, historyBars);
		double[] rank = new NaturalRanking().rank(rawData);

		// the last in rawData should be the latest bar. Rank 1 means it is the
		// biggest etc. Percentile is simply rank / array size * 100
		return rank[rank.length - 1] / rank.length * 100.0;
	}

	private double[] getBBandsSqueezes(Instrument instrument, Period pPeriod,
			OfferSide side, AppliedPrice appliedPrice, Filter filter,
			long time, int lookback, int historyBars) throws JFException {
		double[][] bBands = indicators.bbands(instrument, pPeriod, side,
				AppliedPrice.CLOSE, lookback, 2.0, 2.0, MaType.SMA, filter,
				historyBars, time, 0);
		double[] ATRs = getATRTimeSeries(instrument, pPeriod, side, filter,
				time, lookback, historyBars);
		double[] results = new double[ATRs.length];

		for (int i = 0; i < ATRs.length; i++) {
			double twoStDev = bBands[0][i] - bBands[1][i], twoATRs = 2 * ATRs[i];
			results[i] = twoStDev / twoATRs * 100.0;
		}
		return results;
	}

}
