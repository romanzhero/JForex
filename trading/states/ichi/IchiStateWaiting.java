package trading.states.ichi;

import trading.elements.AbstractTradeState;
import trading.elements.ITradeState;

public class IchiStateWaiting extends AbstractTradeState implements ITradeState {

	public IchiStateWaiting(boolean pIsLongTrade, double open, double low, double high, double close) {
		super(pIsLongTrade);
		stateName = "Waiting for entry";
		OHLCValues[0] = open;
		OHLCValues[1] = low;
		OHLCValues[2] = high;
		OHLCValues[3] = close;
	}

}
