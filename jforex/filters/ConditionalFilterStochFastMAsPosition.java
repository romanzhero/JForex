package jforex.filters;

public class ConditionalFilterStochFastMAsPosition extends ConditionalFilterOnMAsPosition implements IConditionalFilter {

	@Override
	public IFilter cloneFilter() {
		return new ConditionalFilterStochFastMAsPosition();
	}

	@Override
	public IConditionalFilter cloneConditionalFilter() {
		return new ConditionalFilterStochFastMAsPosition();
	}

	@Override
	protected void setMainFilter() {
		mainFilter = new StochFastFilter();
	}

}
