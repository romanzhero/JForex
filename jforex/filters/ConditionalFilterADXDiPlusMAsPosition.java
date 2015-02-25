package jforex.filters;

public class ConditionalFilterADXDiPlusMAsPosition extends ConditionalFilterOnMAsPosition implements IConditionalFilter {

	@Override
	public IFilter cloneFilter() {
		return new ConditionalFilterADXDiPlusMAsPosition();
	}

	@Override
	public IConditionalFilter cloneConditionalFilter() {
		return new ConditionalFilterADXDiPlusMAsPosition();
	}

	@Override
	protected void setMainFilter() {
		mainFilter = new ADXDiPlusFilter();
	}

}
