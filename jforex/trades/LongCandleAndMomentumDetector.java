package jforex.trades;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger;

public class LongCandleAndMomentumDetector extends AbstractCandleAndMomentumDetector {
	public LongCandleAndMomentumDetector(TradeTrigger candles, Momentum momentum, double thresholdLevel) {
		super(candles, momentum, thresholdLevel);
	}

	public LongCandleAndMomentumDetector(TradeTrigger candles, Momentum momentum) {
		super(candles, momentum, 0);
	}
	
	public TradeTrigger.TriggerDesc checkEntry(Instrument instrument, Period pPeriod, OfferSide side, Filter filter, IBar bidBar, IBar askBar) throws JFException {
		// entry is two-step process. First a candle signal at channel extreme is checked. Once this appears we wait for Stoch momentum to be confirming
		// Only rarely does this happen on the same bar, but need to check this situation too !
		if (!candleSignalAppeared) {
			TradeTrigger.TriggerDesc bullishSignalDesc = candles.bullishReversalCandlePatternDesc(instrument, pPeriod, side, bidBar.getTime());
			if (bullishSignalDesc != null && bullishSignalDesc.channelPosition <= thresholdLevel) {
				candleSignalAppeared = true;
				candleSignalDesc = bullishSignalDesc;
			} 
		}
		// now check the momentum condition too
		if (candleSignalAppeared && !momentumConfired) {
			// however it might happen that S/R of candle signal was exceeded in the opposite direction
			// MUST cancel the whole signal !
			if (bidBar.getClose() < candleSignalDesc.pivotLevel)
				reset();
			
			if (candleSignalAppeared) {
				double stochs[] = momentum.getStochs(instrument, pPeriod, side, bidBar.getTime());
				double
					fastStoch = stochs[0],
					slowStoch = stochs[1];	
				momentumConfired = fastStoch > slowStoch && fastStoch > 20.0;
			}
		}
		if (candleSignalAppeared && momentumConfired) {
			// however it might happen that S/R of candle signal was exceeded in the opposite direction
			// MUST cancel the whole signal !
			if (bidBar.getClose() < candleSignalDesc.pivotLevel)
				reset();
			else {
				// signal is valid until momentum confirms it. Opposite signals are ignored for the time being, strategies / setups should take care about them
				double stochs[] = momentum.getStochs(instrument, pPeriod, side, bidBar.getTime());
				double
					fastStoch = stochs[0],
					slowStoch = stochs[1];
				if (!(fastStoch > slowStoch && fastStoch > 20.0) || (fastStoch > 80 && slowStoch > 80))
					reset();
			}
		}
		return candleSignalAppeared && momentumConfired ? candleSignalDesc : null;
	}	
}
