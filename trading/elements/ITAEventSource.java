package trading.elements;

public interface ITAEventSource {
	ITAEvent get(String ticker, String eventName, TimeFrames timeFrame, long time);
	ITAEvent get(String ticker, String eventName, TimeFrames timeFrame, long time, ITradeState relevantState);

	double getValue(String ticker, String eventName, TimeFrames timeFrame, long time);
}
