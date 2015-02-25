package jforex.filters;

import jforex.techanalysis.Trend;

import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.IIndicators.AppliedPrice;

public class ADXDiPlusFilter extends AbstractSimpleFilter implements IFilter {

	@Override
	public IFilter cloneFilter() {
		return new ADXDiPlusFilter();
	}

	@Override
	protected double calcIndicator(Instrument instrument, OfferSide side, AppliedPrice appliedPrice, long time) throws JFException {
		Trend trendDetector = new Trend(indicators);
		double[] adxs = trendDetector.getADXs(instrument, period, side, time);
		return adxs[1];
	}

}
