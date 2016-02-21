package trading.elements;

public abstract class AbstractTradeState implements ITradeState {

	protected boolean isLongTrade;
	protected double[] OHLCValues = new double[4];
	protected String stateName;

	public AbstractTradeState(boolean isLongTrade) {
		super();
		this.isLongTrade = isLongTrade;
		stateName = "not assigned";
	}

	@Override
	public boolean isLong() {
		return isLongTrade;
	}

	@Override
	public double[] getOHLC() {
		return OHLCValues;
	}

	@Override
	public String getStateName() {
		return stateName;
	}

}
