package jforex.techanalysis;

import java.util.List;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.IIndicators.MaType;

public class Channel {

	public static int TOP = 0, MIDDLE = 1, BOTTOM = 2;

	private IIndicators indicators;
	private IHistory history;

	private double MA20;

	public Channel(IHistory pHistory, IIndicators pIndicators) {
		this.history = pHistory;
		this.indicators = pIndicators;
	}

	public boolean previousBarLowInLowerChannel(Instrument instrument,
			Period pPeriod, OfferSide side,
			IIndicators.AppliedPrice appliedPrice, long time)
			throws JFException {
		MA20 = indicators.sma(instrument, pPeriod, side, appliedPrice, 20,
				Filter.WEEKENDS, 2, time, 0)[1];
		List<IBar> bars = history.getBars(instrument, pPeriod, side,
				Filter.WEEKENDS, 2, time, 0);

		return bars.get(0).getLow() < MA20;
	}

	public double[] bullishTriggerChannelStats(Instrument instrument,
			Period pPeriod, OfferSide side, long time) throws JFException {
		double[] results = new double[2];

		List<IBar> bars = history.getBars(instrument, pPeriod, side,
				Filter.WEEKENDS, 2, time, 0);
		double[][] bBands = indicators.bbands(instrument, pPeriod, side,
				AppliedPrice.CLOSE, 20, 2.0, 2.0, MaType.SMA, Filter.WEEKENDS,
				2, time, 0);
		double bBandsWidthPrev = bBands[0][0] - bBands[2][0];
		double bBandsBottomPrev = bBands[2][0];
		results[0] = (bars.get(1).getHigh() - bBandsBottomPrev)
				/ bBandsWidthPrev * 100;

		double bBandsWidthPrev1 = bBands[0][1] - bBands[2][1];
		double bBandsBottomPrev1 = bBands[2][1];
		results[1] = (bars.get(0).getLow() - bBandsBottomPrev1)
				/ bBandsWidthPrev1 * 100;
		return results;
	}

	public double priceChannelPos(Instrument instrument, Period pPeriod, Filter filter, OfferSide side, long time, double price) throws JFException {
		double[][] bBands = indicators.bbands(instrument, pPeriod, side, AppliedPrice.CLOSE, 20, 2.0, 2.0, MaType.SMA, filter, 1, time, 0);
		double bBandsWidth = bBands[0][0] - bBands[2][0];
		double bBandsBottom = bBands[2][0];
		return (price - bBandsBottom) / bBandsWidth * 100;
	}
	
	public double priceChannelPos(Instrument instrument, Period pPeriod, OfferSide side, long time, double price) throws JFException {
		return priceChannelPos(instrument, pPeriod, Filter.WEEKENDS, side, time, price);
	}


	public double[] getRawBBandsData(Instrument instrument, Period pPeriod,
			OfferSide side, long time) throws JFException {
		double[][] bBands = indicators.bbands(instrument, pPeriod, side,
				AppliedPrice.CLOSE, 20, 2.0, 2.0, MaType.SMA, Filter.WEEKENDS,
				1, time, 0);
		double[] res = { bBands[0][0], bBands[1][0], bBands[2][0] };
		return res;
	}

	public int consequitiveBarsBelow(Instrument instrument, Period pPeriod,
			OfferSide side, long time, int lookBack) throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side,
				Filter.WEEKENDS, lookBack, time, 0);
		IBar[] barsArray = new IBar[bars.size()];
		bars.toArray(barsArray);
		double[][] bBands = indicators.bbands(instrument, pPeriod, side,
				AppliedPrice.CLOSE, 20, 2.0, 2.0, MaType.SMA, Filter.WEEKENDS,
				lookBack, time, 0);
		// both bars and bBands ARE in ascending chronological order - the
		// latest is the last !
		int res = 0;
		for (int cnt = lookBack; cnt > 0
				&& barsArray[cnt - 1].getLow() < bBands[BOTTOM][cnt - 1]; cnt--) {
			if (barsArray[cnt - 1].getLow() < bBands[BOTTOM][cnt - 1])
				res++;
		}
		return res;
	}

	public int consequitiveBarsAbove(Instrument instrument, Period pPeriod,
			OfferSide side, long time, int lookBack) throws JFException {
		List<IBar> bars = history.getBars(instrument, pPeriod, side,
				Filter.WEEKENDS, lookBack, time, 0);
		IBar[] barsArray = new IBar[bars.size()];
		bars.toArray(barsArray);
		double[][] bBands = indicators.bbands(instrument, pPeriod, side,
				AppliedPrice.CLOSE, 20, 2.0, 2.0, MaType.SMA, Filter.WEEKENDS,
				lookBack, time, 0);

		// both bars and bBands ARE in ascending chronological order - the
		// latest is the last !
		int res = 0;
		for (int cnt = lookBack; cnt > 0
				&& barsArray[cnt - 1].getHigh() > bBands[TOP][cnt - 1]; cnt--) {
			if (barsArray[cnt - 1].getHigh() > bBands[TOP][cnt - 1])
				res++;
		}
		return res;
	}

	/**
	 * @return 1st element: channel position of low of the last bar 2nd element:
	 *         channel position of high of the bar before
	 * 
	 */
	public double[] bearishTriggerChannelStats(Instrument instrument,
			Period pPeriod, OfferSide side, long time) throws JFException {
		double[] results = new double[2];

		List<IBar> bars = history.getBars(instrument, pPeriod, side,
				Filter.WEEKENDS, 2, time, 0);
		double[][] bBands = indicators.bbands(instrument, pPeriod, side,
				AppliedPrice.CLOSE, 20, 2.0, 2.0, MaType.SMA, Filter.WEEKENDS,
				2, time, 0);
		double bBandsWidthPrev = bBands[TOP][0] - bBands[BOTTOM][0];
		double bBandsBottomPrev = bBands[BOTTOM][0];
		results[0] = (bars.get(1).getLow() - bBandsBottomPrev)
				/ bBandsWidthPrev * 100;

		double bBandsWidthPrev1 = bBands[TOP][1] - bBands[BOTTOM][1];
		double bBandsBottomPrev1 = bBands[BOTTOM][1];
		results[1] = (bars.get(0).getHigh() - bBandsBottomPrev1)
				/ bBandsWidthPrev1 * 100;
		return results;
	}

	public double[] bBandsWidthTS(Instrument instrument, Period pPeriod, OfferSide side, long time, int timeSeriesLength) throws JFException {
		double[] results = new double[timeSeriesLength];

		double[][] bBands = indicators.bbands(instrument, pPeriod, side, AppliedPrice.CLOSE, 20, 2.0, 2.0, MaType.SMA, Filter.WEEKENDS,
				timeSeriesLength, time, 0);

		for (int i = 0; i < bBands[0].length; i++) {
			results[i] = bBands[TOP][i] - bBands[BOTTOM][i];
		}
		return results;
	}
	
	public double[] bBandsWidthPerc(Instrument instrument, Period pPeriod, OfferSide side, long time, int timeSeriesLength) throws JFException {
		double[] results = new double[timeSeriesLength];

		double[][] bBands = indicators.bbands(instrument, pPeriod, side, AppliedPrice.CLOSE, 20, 2.0, 2.0, MaType.SMA, Filter.WEEKENDS,
				timeSeriesLength, time, 0);

		for (int i = 0; i < bBands[0].length; i++) {
			results[i] = (bBands[TOP][i] - bBands[BOTTOM][i]) / bBands[BOTTOM][i] * 100.0;
		}
		return results;
	}

}