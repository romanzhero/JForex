package jforex.emailflex.elements.overview;

import jforex.emailflex.BaseFlexElement;
import jforex.emailflex.IFlexEmailWrapper;
import jforex.emailflex.wrappers.OverviewWrapper;

public abstract class AbstractOverview extends BaseFlexElement {

	@Override
	public boolean needsWrapper() {
		return true;
	}

	@Override
	public IFlexEmailWrapper getWrapper() {
		return new OverviewWrapper();
	}

}
