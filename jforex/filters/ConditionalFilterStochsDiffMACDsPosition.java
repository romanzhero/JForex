package jforex.filters;

public class ConditionalFilterStochsDiffMACDsPosition extends ConditionalFilterOnMACDsPosition implements IConditionalFilter {

	@Override
	protected void setMainFilter() {
		mainFilter = new StochsDiffFilter();		
	}

	@Override
	public IFilter cloneFilter() {
		return new ConditionalFilterStochsDiffMACDsPosition();
	}

	@Override
	public IConditionalFilter cloneConditionalFilter() {
		return new ConditionalFilterStochsDiffMACDsPosition();
	}

}
