package jforex.utils.log;

import com.dukascopy.api.IBar;

// Draw down log for multiple levels
public class MultiDDLog {
	public class LevelLog {
		public double maxDD,
		// ATR values at time of maxDD
				atr, atrEMA, atrSMA;
		public long DDtime;
		public boolean isTouched = false;
	}

	protected boolean isLong;

	protected int noOfLevels, levelMultiplier = 2, // how many ATRs are counted
													// for new level
			currLevel = 0;

	protected LevelLog[] levelRecords = null;

	public MultiDDLog(int pNoOfLevels, int pLevelMultiplier, boolean pIsLong) {
		super();
		this.noOfLevels = pNoOfLevels;
		levelMultiplier = pLevelMultiplier;
		currLevel = 0;
		isLong = pIsLong;
		levelRecords = new LevelLog[pNoOfLevels];
		for (int i = 0; i < pNoOfLevels; i++)
			levelRecords[i] = new LevelLog();
	}

	public void updateLevel(int level, double maxDD, double atr, double atrEMA,
			double atrSMA, long DDtime) {
		levelRecords[level].maxDD = maxDD;
		levelRecords[level].atr = atr;
		levelRecords[level].atrEMA = atrEMA;
		levelRecords[level].atrSMA = atrSMA;
		levelRecords[level].DDtime = DDtime;
	}

	// generic call to start internal calculations. maxProfit must be already
	// calculated !!
	public void update(IBar currBar, double fillPrice, double maxProfitPrice,
			long maxProfitTime, double atr, double atrEMA, double atrSMA) {
		// establish in which level to work
		int tempLevel = new Double(Math.floor((isLong ? currBar.getHigh()
				- fillPrice : fillPrice - currBar.getLow())
				/ (levelMultiplier * atr))).intValue();
		if (tempLevel >= currLevel && tempLevel < noOfLevels
				&& !levelRecords[tempLevel].isTouched) {
			currLevel = tempLevel;
			levelRecords[currLevel].isTouched = true;
		}

		// // avoid low of the maxProfit bar
		// if (currBar.getTime() <= maxProfitTime)
		// return;

		if (isLong) {
			if (maxProfitPrice - currBar.getLow() > levelRecords[currLevel].maxDD) {
				levelRecords[currLevel].maxDD = maxProfitPrice
						- currBar.getLow();
				levelRecords[currLevel].DDtime = currBar.getTime();
				levelRecords[currLevel].atr = atr;
				levelRecords[currLevel].atrEMA = atrEMA;
				levelRecords[currLevel].atrSMA = atrSMA;
			}
		} else {
			if (currBar.getHigh() - maxProfitPrice > levelRecords[currLevel].maxDD) {
				levelRecords[currLevel].maxDD = currBar.getHigh()
						- maxProfitPrice;
				levelRecords[currLevel].DDtime = currBar.getTime();
				levelRecords[currLevel].atr = atr;
				levelRecords[currLevel].atrEMA = atrEMA;
				levelRecords[currLevel].atrSMA = atrSMA;
			}
		}
	}

	public LevelLog getLevel(int level) {
		return levelRecords[level];
	}

	public LevelLog getCurrent() {
		return getLevel(currLevel);
	}

}
