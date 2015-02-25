package jforex.filters;

import com.dukascopy.api.Filter;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.IIndicators.MaType;

public class StochsDiffFilter extends AbstractSimpleFilter implements IFilter {

	@Override
	public IFilter cloneFilter() {
		return new StochsDiffFilter();
	}

	@Override
	protected double calcIndicator(Instrument instrument, OfferSide side, AppliedPrice appliedPrice, long time) throws JFException {
    	int fastKPeriod = 14;
        int slowKPeriod = 3;
        MaType slowKMaType = MaType.SMA;
        int slowDPeriod = 3;
        MaType slowDMaType = MaType.SMA;
		double[][] stoch2 = indicators.stoch(instrument, period, side, 
				fastKPeriod, slowKPeriod, slowKMaType, slowDPeriod, slowDMaType, 
				Filter.WEEKENDS, 1, time, 0);
		double fastStoch = stoch2[0][0];
		double slowStoch = stoch2[1][0];
		return fastStoch - slowStoch;
	}

}
