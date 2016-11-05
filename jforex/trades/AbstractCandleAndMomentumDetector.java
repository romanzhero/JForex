package jforex.trades;

import jforex.techanalysis.TradeTrigger;

public class AbstractCandleAndMomentumDetector {
	protected double 
		thresholdLevel, 
		lastSupportResistance = -1.0, // needed to set SL and check for cancel criteria
		oppositeChannelBorder = -1.0;
	protected boolean 
		styleAggressive = false, // if true Stoch can be OS/OB, enough for fast SMI to confirm. Used for day trading
		candleSignalAppeared = false, 
		momentumConfired = false;
	protected long lastSignalTime;
	protected TradeTrigger.TriggerDesc candleSignalDesc = null;

	public AbstractCandleAndMomentumDetector(double thresholdLevel, boolean pStyleAggressive) {
		this.thresholdLevel = thresholdLevel;
		this.styleAggressive = pStyleAggressive;
	}

	public void reset() {
		candleSignalAppeared = false;
		momentumConfired = false;
		candleSignalDesc = null;
		lastSupportResistance = -1.0;
		oppositeChannelBorder = -1.0;
	}
}
