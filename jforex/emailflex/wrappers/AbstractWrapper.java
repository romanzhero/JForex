package jforex.emailflex.wrappers;

import java.util.ArrayList;
import java.util.List;

import jforex.emailflex.IFlexEmailElement;
import jforex.emailflex.IFlexEmailWrapper;

public abstract class AbstractWrapper implements IFlexEmailWrapper {
	protected List<IFlexEmailElement> wrappedElements = new ArrayList<IFlexEmailElement>();

	@Override
	public boolean isGeneric() {
		return true;
	}

	@Override
	public void add(IFlexEmailElement e) {
		wrappedElements.add(e);
	}

}
