package trading.elements;

import jforex.utils.Logger;

public interface ITradingStrategy {
	
	public String getName();
	public int getMaxPositions();
	
	public void processTAEvent(String ticker, TimeFrames timeframe, long time, ITAEventSource eventSource);
	public void processBrokerEvent(String ticker, IBrokerEvent event, ITAEventSource eventSource);
	
	//TODO: unwanted reference to JForex layer !!! Must be replaced with generic solution only in this layer !
	public void setLogger(Logger l);

}
