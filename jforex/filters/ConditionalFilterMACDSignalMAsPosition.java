package jforex.filters;

public class ConditionalFilterMACDSignalMAsPosition extends	ConditionalFilterOnMAsPosition implements IConditionalFilter {

	@Override
	public IFilter cloneFilter() {
		return new ConditionalFilterMACDSignalMAsPosition();
	}

	@Override
	public IConditionalFilter cloneConditionalFilter() {
		return new ConditionalFilterMACDSignalMAsPosition();
	}

	@Override
	protected void setMainFilter() {
		mainFilter = new MACDSignalFilter();
	}

}
