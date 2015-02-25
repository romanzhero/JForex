package jforex.filters;

import java.text.DecimalFormat;

import jforex.utils.FXUtils;

import com.dukascopy.api.Filter;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.IIndicators.AppliedPrice;

public class MACDSignalFilter extends AbstractSimpleFilter implements IFilter {

	@Override
	public IFilter cloneFilter() {
		return new MACDSignalFilter();
	}

	@Override
	protected double calcIndicator(Instrument instrument, OfferSide side, AppliedPrice appliedPrice, long time) throws JFException {
    	return indicators.macd(instrument, period, side, appliedPrice, 26, 12, 9, Filter.WEEKENDS, 1, time, 0)[1][0];
	}

	protected DecimalFormat decFormat() {
		return FXUtils.df5;
	}
}
