package jforex.filters;

import jforex.techanalysis.Trend;

import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.IIndicators.AppliedPrice;

public abstract class ConditionalFilterOnMAsPosition extends AbstractConditionalFilter {

	@Override
	protected double calcCondFilterValue(Instrument instrument, OfferSide side,	AppliedPrice appliedPrice, long time) throws JFException {
		Trend trendDetector = new Trend(indicators);
		if (trendDetector.getUptrendMAsMaxDifStDevPos(instrument, period, side, appliedPrice, time, 720) != -1000) {
			return 2.0;
		}
		else if (trendDetector.getDowntrendMAsMaxDifStDevPos(instrument, period, side, appliedPrice, time, 720) != -1000) {
			return 0.0;
		}
		else {
			return 1.0;			
		}
	}
}
