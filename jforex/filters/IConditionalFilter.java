package jforex.filters;

import com.dukascopy.api.IHistory;
import com.dukascopy.api.Period;

public interface IConditionalFilter extends IFilter {
	public void setConditionalParams(IHistory pHistory, Period condPeriod, double[] pMainMin, double[] pMainMax, double[] pCondMin, double[] pCondMax);

	public IConditionalFilter cloneConditionalFilter();
}
