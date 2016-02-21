package jforex.filters;

import jforex.techanalysis.Trend;

import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.IIndicators.AppliedPrice;

public abstract class ConditionalFilterOnADXsPosition extends
		AbstractConditionalFilter {

	@Override
	protected double calcCondFilterValue(Instrument instrument, OfferSide side,
			AppliedPrice appliedPrice, long time) throws JFException {
		Trend trendDetector = new Trend(indicators);
		double[] adx = trendDetector.getADXs(instrument, period, side, time);
		if (adx[1] < adx[2] && adx[1] < adx[0]) // most bearish - DI_PLUS lowest
												// of all
			return 0;
		else
			return 1;
	}

}
