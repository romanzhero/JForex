package jforex.utils.stats;

public class TradeStats {
	protected double maxWin = 0, maxLoss = 0; // in pips of course

	public void updateMaxPnL(double currPnLInPips) {
		if (currPnLInPips > 0 && currPnLInPips > maxWin) {
			maxWin = currPnLInPips;
		} else if (currPnLInPips <= 0 && currPnLInPips < maxLoss) {
			maxLoss = currPnLInPips;
		}
	}

	public double getMaxWin() {
		return maxWin;
	}

	public double getMaxLoss() {
		return maxLoss;
	}

	public void reset() {
		maxLoss = maxWin = 0;
	}
}
