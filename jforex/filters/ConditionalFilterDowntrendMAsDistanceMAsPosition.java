package jforex.filters;

public class ConditionalFilterDowntrendMAsDistanceMAsPosition extends ConditionalFilterOnMAsPosition implements IConditionalFilter {

	@Override
	public IFilter cloneFilter() {
		return new ConditionalFilterDowntrendMAsDistanceMAsPosition();
	}

	@Override
	public IConditionalFilter cloneConditionalFilter() {
		return new ConditionalFilterDowntrendMAsDistanceMAsPosition();
	}

	@Override
	protected void setMainFilter() {
		mainFilter = new DowntrendMAsDistanceFilter();

	}

}
