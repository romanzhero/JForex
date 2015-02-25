package trading.states.ichi;

import trading.elements.AbstractTradeState;
import trading.elements.ITradeState;

public class IchiStatePositionNo2 extends AbstractTradeState implements ITradeState {

	public IchiStatePositionNo2(boolean isLongTrade) {
		super(isLongTrade);
		stateName = "in the second position";
	}

}
