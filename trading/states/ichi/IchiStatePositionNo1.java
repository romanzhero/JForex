package trading.states.ichi;

import trading.elements.AbstractTradeState;
import trading.elements.ITradeState;

public class IchiStatePositionNo1 extends AbstractTradeState implements ITradeState {

	public IchiStatePositionNo1(boolean isLongTrade) {
		super(isLongTrade);
		stateName = "in the first position";
	}

}
