package trading.states.ichi;

import trading.elements.AbstractTradeState;
import trading.elements.ITradeState;

public class IchiStateNoPosition extends AbstractTradeState implements
		ITradeState {

	public IchiStateNoPosition(boolean pIsLong) {
		super(pIsLong);
		stateName = "IchiNoPosition";
	}

}
