package jforex.filters;

import jforex.techanalysis.Trend;

import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.IIndicators.AppliedPrice;

public class UptrendMAsDistanceFilter extends AbstractSimpleFilter implements IFilter {

	@Override
	public boolean check(Instrument instrument, OfferSide side, AppliedPrice appliedPrice, long time) throws JFException {
		Trend trendDetector = new Trend(indicators);
		double trendStDevPos = -1000.0;
		//TODO: configuration file must contain default lookback period per time frame !
		// 1000 for 30', 720 for 4h... And this should be read here instead hard coded !
		if ((trendStDevPos = trendDetector.getUptrendMAsMaxDifStDevPos(instrument, period, side, appliedPrice, time, 720)) != -1000) {
			return trendStDevPos >= minAllowedValue && trendStDevPos <= maxAllowedValue;
		}
		else
			return true; // filter not applicable, not an uptrend
	}

	@Override
	public IFilter cloneFilter() {
		return new UptrendMAsDistanceFilter();
	}

	@Override
	protected double calcIndicator(Instrument instrument, OfferSide side, AppliedPrice appliedPrice, long time) throws JFException {
		Trend trendDetector = new Trend(indicators);
		return trendDetector.getUptrendMAsMaxDifStDevPos(instrument, period, side, appliedPrice, time, 720);
	}

}
