/**
 * 
 */
package trading.elements;

/**
 * Interface to order management (submit/cancel) functionality provided by
 * brokers
 * 
 */
public interface IBrokerEngine {
	public String submitBuyStpOrder(String ticker, String ID, double amount,
			double stp, double sl, double tp);

	public String submitSellStpOrder(String ticker, String ID, double amount,
			double stp, double sl, double tp);

	public void cancelOrder(String ticker, String orderID);

	public double getEquity();

	public void closeOrderMkt(String ticker, String orderID);

}
