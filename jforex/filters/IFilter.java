package jforex.filters;

import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public interface IFilter {
	public boolean check(Instrument instrument, OfferSide side, IIndicators.AppliedPrice appliedPrice, long time) throws JFException;
	public String explain(long barTime);
	public String getName();
	
	public void set(String pName, IIndicators pIndicators, Period p, double pMin, double pMax);
	public void setAuxParams(double[] auxParams);
	
	public IFilter cloneFilter();
}
