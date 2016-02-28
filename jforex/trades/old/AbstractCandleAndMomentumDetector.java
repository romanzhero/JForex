package jforex.trades.old;

import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger;

public class AbstractCandleAndMomentumDetector {
	protected TradeTrigger candles = null;
	protected Momentum momentum = null;
	protected double thresholdLevel, lastSupportResistance = -1.0, // needed to
																	// set SL
																	// and check
																	// for
																	// cancel
																	// criteria
			oppositeChannelBorder = -1.0;
	protected boolean candleSignalAppeared = false, momentumConfired = false;
	protected long lastSignalTime;
	protected TradeTrigger.TriggerDesc candleSignalDesc = null;

	public AbstractCandleAndMomentumDetector(TradeTrigger candles,
			Momentum momentum, double thresholdLevel) {
		this.candles = candles;
		this.momentum = momentum;
		this.thresholdLevel = thresholdLevel;
	}

	public void reset() {
		candleSignalAppeared = false;
		momentumConfired = false;
		candleSignalDesc = null;
		lastSupportResistance = -1.0;
		oppositeChannelBorder = -1.0;
	}
}
