package jforex.filters;

public class ConditionalFilterADXMAsPosition extends ConditionalFilterOnMAsPosition implements IConditionalFilter {

	@Override
	public IFilter cloneFilter() {
		return new ConditionalFilterADXMAsPosition();
	}

	@Override
	public IConditionalFilter cloneConditionalFilter() {
		return new ConditionalFilterADXMAsPosition();
	}

	@Override
	protected void setMainFilter() {
		mainFilter = new ADXFilter();
	}

}
