package jforex.filters;

import jforex.techanalysis.Momentum;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;

public abstract class ConditionalFilterOnMACDsPosition extends AbstractConditionalFilter {

	public ConditionalFilterOnMACDsPosition() {
		super();
	}

	public double calcCondFilterValue(Instrument instrument, OfferSide side, AppliedPrice appliedPrice, long time) throws JFException {
		double resultTmp[][] = indicators.macd(instrument, condPeriod, side, appliedPrice, 26, 12, 9, Filter.WEEKENDS, 1, time, 0);
		double macd = resultTmp[Momentum.MACD_LINE][0];
		double macdSignal = resultTmp[Momentum.MACD_Signal][0];
		double condMacdsPosition = 0.0; // both equal or below zero
		if (macd > 0 && macdSignal > 0)
			condMacdsPosition = 2.0; 
		else if (macd <= 0 && macdSignal > 0)
			condMacdsPosition = 1.0;
		return condMacdsPosition;
	}

}