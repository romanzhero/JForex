package jforex.filters;

import jforex.techanalysis.Trend;

import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.IIndicators.AppliedPrice;

public abstract class ConditionalFilterOnADX extends AbstractConditionalFilter {

	@Override
	protected double calcCondFilterValue(Instrument instrument, OfferSide side,	AppliedPrice appliedPrice, long time) throws JFException {
		Trend trendDetector = new Trend(indicators);
		return trendDetector.getADXs(instrument, period, side, time)[0];
	}

}
