package jforex.strategies;

import trading.elements.AbstractBrokerEvent;
import trading.elements.IBrokerEvent;

public class OrderFillEvent extends AbstractBrokerEvent implements IBrokerEvent {

	protected double amount, entryPrice;

	public OrderFillEvent(String ticker, String orderID, long time,
			double pAmount, double pEntryPrice) {
		super(ticker, orderID, time);
		amount = pAmount;
		entryPrice = pEntryPrice;
	}

	@Override
	public BrokerEventType getType() {
		return BrokerEventType.ENTRY_FILL;
	}

	public double getAmount() {
		return amount;
	}

	public double getEntryPrice() {
		return entryPrice;
	}

}
