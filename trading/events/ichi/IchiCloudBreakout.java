package trading.events.ichi;

import trading.elements.AbstractTAEvent;
import trading.elements.ITAEvent;

public class IchiCloudBreakout extends AbstractTAEvent implements ITAEvent {

	protected double atr, dolarAdjustedAtr, open, low, high, close;

	public IchiCloudBreakout(boolean isLongEvent, double stopPrice,
			double limitPrice, long time, double pAtr, double pAdjustedAtr,
			double pOpen, double pLow, double pHigh, double pClose) {
		super(isLongEvent, stopPrice, limitPrice, time);
		atr = pAtr;
		dolarAdjustedAtr = pAdjustedAtr;

		if (isLongEvent)
			stopLoss = stopPrice - 1.42 * atr;
		else
			stopLoss = stopPrice + 1.42 * atr;

		open = pOpen;
		low = pLow;
		high = pHigh;
		close = pClose;
	}

	public double getATR() {
		return atr;
	}

	public double getDolarAdjustedAtr() {
		return dolarAdjustedAtr;
	}

	public double getOpen() {
		return open;
	}

	public double getLow() {
		return low;
	}

	public double getHigh() {
		return high;
	}

	public double getClose() {
		return close;
	}

}
