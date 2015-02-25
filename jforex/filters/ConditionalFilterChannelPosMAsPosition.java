package jforex.filters;

public class ConditionalFilterChannelPosMAsPosition extends
		ConditionalFilterOnMAsPosition implements IConditionalFilter {

	@Override
	public IFilter cloneFilter() {
		return new ConditionalFilterChannelPosMAsPosition();
	}

	@Override
	public IConditionalFilter cloneConditionalFilter() {
		return new ConditionalFilterChannelPosMAsPosition();
	}

	@Override
	protected void setMainFilter() {
		mainFilter = new ChannelPosFilter();
	}

}
