package jforex.filters;

public class ConditionalFilterStochSlowMAsPosition extends
		ConditionalFilterOnMAsPosition implements IConditionalFilter {

	@Override
	public IFilter cloneFilter() {
		return new ConditionalFilterStochSlowMAsPosition();
	}

	@Override
	public IConditionalFilter cloneConditionalFilter() {
		return new ConditionalFilterStochSlowMAsPosition();
	}

	@Override
	protected void setMainFilter() {
		mainFilter = new StochSlowFilter();
	}

}
