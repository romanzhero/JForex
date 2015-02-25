package jforex.filters;

public class ConditionalFilterUptrendMAsDistanceMAsPosition extends	ConditionalFilterOnMAsPosition implements IConditionalFilter {

	@Override
	public IFilter cloneFilter() {
		return new ConditionalFilterUptrendMAsDistanceMAsPosition();
	}

	@Override
	public IConditionalFilter cloneConditionalFilter() {
		return new ConditionalFilterUptrendMAsDistanceMAsPosition();
	}

	@Override
	protected void setMainFilter() {
		mainFilter = new UptrendMAsDistanceFilter();

	}

}
