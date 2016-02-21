package jforex.techanalysis;

import jforex.utils.FXUtils;

import com.dukascopy.api.IBar;

public class SRLevel implements Comparable<SRLevel> {
	protected String name;
	protected double value;

	public SRLevel(String name, double value) {
		super();
		this.name = name;
		this.value = value;
	}

	public boolean hit(IBar bar, double tolerance) {
		return bar.getLow() - tolerance <= value
				&& bar.getHigh() + tolerance >= value;
	}

	public boolean isNear(double price, double tolerance) {
		return price >= value - tolerance && price <= value + tolerance;
	}

	/**
	 * @param highOrLow
	 *            - for true hit of bar's high will be checked (bearish
	 *            signals), otherwise hit of low (bullish signals)
	 * @return
	 */
	public boolean hit(IBar bar, boolean highOrLow, double tolerance) {
		if (highOrLow)
			return hitHigh(bar, tolerance);
		else
			return hitLow(bar, tolerance);
	}

	/**
	 * To be used for BULLISH candle signals
	 */
	public boolean hitLow(IBar bar, double tolerance) {
		return bar.getLow() - tolerance <= value
				&& bar.getLow() + tolerance >= value;
	}

	/**
	 * To be used for BEARISH candle signals
	 */
	public boolean hitHigh(IBar bar, double tolerance) {
		return bar.getHigh() - tolerance <= value
				&& bar.getHigh() + tolerance >= value;
	}

	@Override
	public String toString() {
		return new String(name + " (" + FXUtils.df5.format(value) + ")");
	}

	public String getName() {
		return name;
	}

	/*
	 * SRLevels are sorted in descending order of value, so they appear in lists
	 * as on the chart
	 */
	@Override
	public int compareTo(SRLevel other) {
		if (this.value > other.value)
			return -1;
		if (this.value < other.value)
			return 1;
		return 0;
	}

	public double getValue() {
		return value;
	}
}
