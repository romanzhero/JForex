package jforex.filters;

public class ConditionalFilterStochsDiffMAsPosition extends	ConditionalFilterOnMAsPosition implements IConditionalFilter {

	@Override
	public IFilter cloneFilter() {
		return new ConditionalFilterStochsDiffMAsPosition();
	}

	@Override
	public IConditionalFilter cloneConditionalFilter() {
		return new ConditionalFilterStochsDiffMAsPosition();
	}

	@Override
	protected void setMainFilter() {
		mainFilter = new StochsDiffFilter();
	}

}
