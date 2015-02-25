package trading.events.ichi;

import trading.elements.AbstractTAEvent;
import trading.elements.ITAEvent;

public class IchiCancel extends AbstractTAEvent implements ITAEvent {

	public IchiCancel(boolean isLongEvent) {
		super(isLongEvent);
	}

}
