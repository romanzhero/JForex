package jforex.filters;

public class ConditionalFilterMACDSignalMACDsPosition extends
		ConditionalFilterOnMACDsPosition implements IConditionalFilter {

	@Override
	public IFilter cloneFilter() {
		return new ConditionalFilterMACDSignalMACDsPosition();
	}

	@Override
	public IConditionalFilter cloneConditionalFilter() {
		return new ConditionalFilterMACDSignalMACDsPosition();
	}

	@Override
	protected void setMainFilter() {
		mainFilter = new MACDSignalFilter();
	}

}
