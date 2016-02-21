package jforex.strategies;

import trading.elements.AbstractBrokerEvent;
import trading.elements.IBrokerEvent;

public class OrderSLCloseEvent extends AbstractBrokerEvent implements
		IBrokerEvent {

	public OrderSLCloseEvent(String ticker, String orderID, long time) {
		super(ticker, orderID, time);
	}

	@Override
	public BrokerEventType getType() {
		return BrokerEventType.STOP_LOSS;
	}

}
