package jforex.techanalysis;

public class TrendLineSnapshot implements Comparable<TrendLineSnapshot> {
	protected String name;
	protected double 
		currentValue,
		slope30min;

	public TrendLineSnapshot(String name, double currentValue, double slope30min) {
		super();
		this.name = name;
		this.currentValue = currentValue;
		this.slope30min = slope30min;
	}

	@Override
	public int compareTo(TrendLineSnapshot other) {
		if (this.currentValue > other.currentValue)
			return -1;
		if (this.currentValue < other.currentValue)
			return 1;
		return 0;
	}

	public String getName() {
		return name;
	}

	public double getCurrentValue() {
		return currentValue;
	}

	public double getSlope30min() {
		return slope30min;
	}

}
