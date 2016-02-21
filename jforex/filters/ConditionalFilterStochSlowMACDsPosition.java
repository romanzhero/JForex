package jforex.filters;

public class ConditionalFilterStochSlowMACDsPosition extends
		ConditionalFilterOnMACDsPosition implements IConditionalFilter {

	@Override
	public IFilter cloneFilter() {
		return new ConditionalFilterStochSlowMACDsPosition();
	}

	@Override
	protected void setMainFilter() {
		mainFilter = new StochSlowFilter();

	}

	@Override
	public IConditionalFilter cloneConditionalFilter() {
		return new ConditionalFilterStochSlowMACDsPosition();
	}

}
