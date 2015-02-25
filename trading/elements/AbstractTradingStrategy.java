package trading.elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jforex.utils.Logger;

/**
 * Generic class supporting multi-position multi-instrument trading
 *
 */
public abstract class AbstractTradingStrategy implements ITradingStrategy {
	
	protected Logger log = null;
	
	public class TickerTradeData {
		protected List<Position> positions = new ArrayList<Position>();
		protected ITradeState
			currentState = null,
			previousState = null;
		protected String waitingOrderID = null;
		protected long 
			lastBarTime = 0,
			tradeBarNo = 0;
		protected double stopLoss;
		
		public TickerTradeData(ITradeState initialState) {
			super();
			currentState = initialState;
		}

		public ITradeState getCurrentState() {
			return currentState;
		}

		public ITradeState getPreviousState() {
			return previousState;
		}

		public void changeState(ITradeState toState) {
			previousState = currentState; 
			currentState = toState;
		}

		public void setWaitingOrderID(String waitingOrderID) {
			this.waitingOrderID = waitingOrderID;
		}

		public long getLastBarTime() {
			return lastBarTime;
		}

		public void setLastBarTime(long lastBarTime) {
			this.lastBarTime = lastBarTime;
		}

		public long getTradeBarNo() {
			return tradeBarNo;
		}

		public void incTradeBarNo() {
			tradeBarNo++;
		}

		public String getWaitingOrderID() {
			return waitingOrderID;
		}

		public List<Position> getPositions() {
			return positions;
		}

		public double getStopLoss() {
			return stopLoss;
		}

		public void setStopLoss(double stopLoss) {
			this.stopLoss = stopLoss;
		}

		public void reset() {
			tradeBarNo = 0;
			for (int i = 0; i < positions.size(); i++)
				positions.remove(i);
			waitingOrderID = null;
			lastBarTime = 0;			
		}
	}
	
	protected Map<String, TickerTradeData> tickersTradeData = new HashMap<String, TickerTradeData>();
	
	protected int maxPositions = 0;
	
	protected TimeFrames basicTimeFrame;
	protected String 
		name = "not assigned",
		shortName = "not assigned";
	
	
	public AbstractTradingStrategy(int maxPositions, IBrokerEngine brokerEngine, TimeFrames pBasicTimeFrame) {
		super();
		this.maxPositions = maxPositions;
		this.brokerEngine = brokerEngine;
		this.basicTimeFrame = pBasicTimeFrame;
	}
	
	protected IBrokerEngine brokerEngine = null;

	@Override
	public String getName() {
		return name;
	}

	@Override
	public int getMaxPositions() {
		return maxPositions;
	}

	public String getShortName() {
		return shortName;
	}

	@Override
	public void setLogger(Logger l) {
		log = l;		
	}


}
