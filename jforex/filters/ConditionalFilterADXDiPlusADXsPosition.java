package jforex.filters;

public class ConditionalFilterADXDiPlusADXsPosition extends
		ConditionalFilterOnADXsPosition implements IConditionalFilter {

	@Override
	public IFilter cloneFilter() {
		return new ConditionalFilterADXDiPlusADXsPosition();
	}

	@Override
	public IConditionalFilter cloneConditionalFilter() {
		return new ConditionalFilterADXDiPlusADXsPosition();
	}

	@Override
	protected void setMainFilter() {
		mainFilter = new ADXDiPlusFilter();
	}

}
