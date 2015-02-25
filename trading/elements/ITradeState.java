/**
 * 
 */
package trading.elements;

/**
 * The state a trade can have
 *
 */
public interface ITradeState {
	
	public final static int 
		OPEN = 1, HIGH = 2, LOW = 3, CLOSE = 4;

	public boolean isLong();
	public double[] getOHLC();
	public String getStateName();
	
}
