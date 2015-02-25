package trading.elements;

public abstract class AbstractBrokerEvent implements IBrokerEvent {
	
	protected String ticker;
	protected String orderID;
	protected long time;


	public AbstractBrokerEvent(String ticker, String orderID, long time) {
		super();
		this.ticker = ticker;
		this.orderID = orderID;
		this.time = time;
	}

	@Override
	public long getTime() {
		return time;
	}

	@Override
	public String getTicker() {
		return ticker;
	}

	@Override
	public String getOrderID() {
		return orderID;
	}


}
