package trading.elements;

public class Position {
	protected String orderID;
	protected boolean isLong;
	protected double amount, entryPrice;

	public Position(String pOrderID, boolean pIsLong, double pAmount,
			double pEntryPrice) {
		orderID = pOrderID;
		isLong = pIsLong;
		amount = pAmount;
		entryPrice = pEntryPrice;
	}

	public String getOrderID() {
		return orderID;
	}

}
