package jforex.filters;



public class ConditionalFilterMACD_HMACDsPosition extends ConditionalFilterOnMACDsPosition implements IConditionalFilter {
	
	@Override
	public IFilter cloneFilter() {
		return new ConditionalFilterMACD_HMACDsPosition();
	}

	@Override
	public IConditionalFilter cloneConditionalFilter() {
		return new ConditionalFilterMACD_HMACDsPosition();
	}

	@Override
	protected void setMainFilter() {
		mainFilter = new MACDHistogramFilter();	
	}

}
