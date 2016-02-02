package jforex.trades;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger;

public class CandleAndMomentumDetector {
	protected TradeTrigger candles = null;
	protected Momentum momentum = null;
	protected double
		bottomLevel = 0.0, 
		topLevel = 100.0,
		lastSupportResistance, // needed to set SL and check for cancel criteria
		channelTop, // needed to check trade unlock 
		channelBottom;
	protected boolean
		candleSignalAppeared = false,
		momentumConfired = false;
	protected long lastSignalTime;
	protected TradeTrigger.TriggerDesc candleSignalDesc = null;

	public CandleAndMomentumDetector(TradeTrigger candles, Momentum momentum, double bottomLevel, double topLevel) {
		this.candles = candles;
		this.momentum = momentum;
		this.bottomLevel = bottomLevel;
		this.topLevel = topLevel;
	}

	public CandleAndMomentumDetector(TradeTrigger candles, Momentum momentum) {
		this.candles = candles;
		this.momentum = momentum;
		this.bottomLevel = 0.0;
		this.topLevel = 100.0;
	}
	
	public TradeTrigger.TriggerDesc checkEntry(Instrument instrument, Period pPeriod, OfferSide side, Filter filter, IBar bidBar, IBar askBar) throws JFException {
		// entry is two-step process. First a candle signal at channel extreme is checked. Once this appears we wait for Stoch momentum to be confirming
		// Only rarely does this happen on the same bar, but need to check this situation too !
		if (!candleSignalAppeared) {
			TradeTrigger.TriggerDesc 
				bullishSignalDesc = candles.bullishReversalCandlePatternDesc(instrument, pPeriod, side, bidBar.getTime()),
				bearishSignalDesc = candles.bearishReversalCandlePatternDesc(instrument, pPeriod, side, bidBar.getTime());
			boolean 
				bullishSignal = bullishSignalDesc != null && bullishSignalDesc.channelPosition <= bottomLevel,
				bearishSignal = bearishSignalDesc != null && bearishSignalDesc.channelPosition >= topLevel;
			if (bullishSignal && bearishSignal) {
				// both signals can happen at the same bar ! To resolve conflict analyze and compare both signal qualities (body orientation & size), handles
				if (bullishSignalDesc.combinedRealBodyDirection && !bearishSignalDesc.combinedRealBodyDirection) {
					// both bodies in signal direction. Bigger body wins
					if (bullishSignalDesc.combinedRealBodyPerc > bearishSignalDesc.combinedRealBodyPerc)
						candleSignalDesc = bullishSignalDesc;
					else
						candleSignalDesc = bearishSignalDesc;
				} else {
					if (bullishSignalDesc.combinedLowerHandlePerc > bearishSignalDesc.combinedUpperHandlePerc)
						candleSignalDesc = bullishSignalDesc;
					else
						candleSignalDesc = bearishSignalDesc;
				}					
				candleSignalAppeared = true;
			} else if (bullishSignal) {
				candleSignalAppeared = true;
				candleSignalDesc = bullishSignalDesc;
			} else if (bearishSignal) {
				candleSignalAppeared = true;
				candleSignalDesc = bearishSignalDesc;
			}
		}
		// now check the momentum condition too
		if (candleSignalAppeared && !momentumConfired) {
			// however it might happen that S/R of candle signal was exceeded in the opposite direction
			// MUST cancel the whole signal !
			if (candleSignalDesc.type.toString().contains("BULLISH") && bidBar.getClose() < candleSignalDesc.pivotLevel)
				reset();
			else if (candleSignalDesc.type.toString().contains("BEARISH") && askBar.getClose() > candleSignalDesc.pivotLevel)
				reset();
			
			if (candleSignalAppeared) {
				double stochs[] = momentum.getStochs(instrument, pPeriod, side, bidBar.getTime());
				double
					fastStoch = stochs[0],
					slowStoch = stochs[1];
	
				if (candleSignalDesc.type.toString().contains("BULLISH"))
					momentumConfired = fastStoch > slowStoch && fastStoch > 20.0;
				else
					momentumConfired = fastStoch < slowStoch && fastStoch < 80.0;
			}
		}
		return candleSignalAppeared && momentumConfired ? candleSignalDesc : null;
	}
	
	public void reset() {
		candleSignalAppeared = false;
		momentumConfired = false;
		candleSignalDesc = null;		
	}
}
