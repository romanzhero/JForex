package jforex.filters;

import jforex.utils.FXUtils;

import com.dukascopy.api.IHistory;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.IIndicators.AppliedPrice;

public abstract class AbstractConditionalFilter extends AbstractSimpleFilter {

	protected IFilter mainFilter;
	protected double[] mainMin;
	protected double[] mainMax;

	protected Period condPeriod;
	protected double[] conditionMin;
	protected double[] conditionMax;
	protected IHistory history;
	
	protected String lastCondFilter = new String();

	public AbstractConditionalFilter() {
		super();
	}

	public void setConditionalParams(IHistory pHistory, Period condPeriod, double[] pMainMin, double[] pMainMax, double[] pCondMin, double[] pCondMax) {
		this.history = pHistory;
		this.condPeriod = condPeriod;
		mainMin = pMainMin;
		mainMax = pMainMax;
		conditionMin = pCondMin;
		conditionMax = pCondMax;
		setMainFilter();
	}

	protected abstract void setMainFilter();

	protected abstract double calcCondFilterValue(Instrument instrument, OfferSide side, AppliedPrice appliedPrice, long time) throws JFException;

	public boolean check(Instrument instrument, OfferSide side, AppliedPrice appliedPrice, long time) throws JFException {
		// must ensure that conditional filter gets correct time. Possible situations:
		// 1. both timeframes same - no action needed
		// 2. conditional timeframe higher then main - calculate start of the previous completed bar for CONDITIONAL period ! 
		// 3. conditional timeframe lower then main - calculate start of the previous completed bar for MAIN period !
		long condTime = time, mainTime = time;
		//TODO: make a comparable wrapper for Dukascopy Period so all timeframes can be used
		if (condPeriod == Period.FOUR_HOURS)
			condTime = history.getPreviousBarStart(condPeriod, time);
		if (period == Period.FOUR_HOURS)
			mainTime = history.getPreviousBarStart(period, time);
		lastCondFilter = "";
		double condFilterValue = calcCondFilterValue(instrument, side, appliedPrice, condTime);
		for (int i = 0; i < mainMin.length; i++) {
	    	// find condition range which applies
			if (condFilterValue >= conditionMin[i] && condFilterValue <= conditionMax[i]) {
				lastCondFilter = "[" + FXUtils.df2.format(conditionMin[i]) + ", " + FXUtils.df2.format(conditionMax[i]) + "]"; 
		    	// for the rest simply delegate to simple filter
		    	mainFilter.set("Main filter of " + name, indicators, period, mainMin[i], mainMax[i]);
		    	mainFilter.setAuxParams(auxParams);
				return mainFilter.check(instrument, side, appliedPrice, mainTime);        		
	    	}
		}
		// If none of conditional ranges applies, filter is irrelevant and check passes on with filtering by returning true
		return true;
	}

	public String explain(long barTime) { return mainFilter.explain(barTime) + "; cond. filter interval = " + lastCondFilter; }

	// not used but must have this dummy implementation for correct syntax
	protected double calcIndicator(Instrument instrument, OfferSide side,	AppliedPrice appliedPrice, long time) throws JFException
	{
		return Double.NaN;
	}
	
}