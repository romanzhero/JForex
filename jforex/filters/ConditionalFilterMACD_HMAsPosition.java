package jforex.filters;

public class ConditionalFilterMACD_HMAsPosition extends	ConditionalFilterOnMAsPosition implements IConditionalFilter {

	@Override
	public IFilter cloneFilter() {
		return new ConditionalFilterMACD_HMAsPosition();
	}

	@Override
	public IConditionalFilter cloneConditionalFilter() {
		return new ConditionalFilterMACD_HMAsPosition();
	}

	@Override
	protected void setMainFilter() {
		mainFilter = new MACDHistogramFilter();
	}

}
