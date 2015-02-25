package trading.elements;

public abstract class AbstractTAEvent implements ITAEvent {
	
	protected boolean
		isLongEvent,
		isOnMarket = false;
	
	protected double
		stopPrice, limitPrice, stopLoss;
	
	protected long time;
	
	protected String name;

	public AbstractTAEvent(boolean isLongEvent) {
		super();
		this.isLongEvent = isLongEvent;
	}

	public AbstractTAEvent(boolean isLongEvent, double stopPrice, double limitPrice, long time) {
		super();
		this.isLongEvent = isLongEvent;
		this.stopPrice = stopPrice;
		this.limitPrice = limitPrice;
		this.time = time;
	}

	@Override
	public boolean isLong() {
		return isLongEvent;
	}

	@Override
	public boolean isOnMarket() {
		return isOnMarket;
	}

	@Override
	public double getSTPPrice() {
		return stopPrice;
	}

	@Override
	public double getLMTPrice() {
		return limitPrice;
	}

	@Override
	public long getTime() {
		return time;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public double getStopLossPrice() {
		return stopLoss;
	}

	@Override
	public void roundSL(int pipScale) {
		stopLoss = Math.round(stopLoss * Math.pow(10, pipScale)) / Math.pow(10, pipScale); 		
	}

}
