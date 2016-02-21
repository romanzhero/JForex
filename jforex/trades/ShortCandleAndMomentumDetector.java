package jforex.trades;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger;

public class ShortCandleAndMomentumDetector extends
		AbstractCandleAndMomentumDetector {
	public ShortCandleAndMomentumDetector(TradeTrigger candles,	Momentum momentum, double thresholdLevel) {
		super(candles, momentum, thresholdLevel);
	}

	public ShortCandleAndMomentumDetector(TradeTrigger candles,	Momentum momentum) {
		super(candles, momentum, 100);
	}

	public TradeTrigger.TriggerDesc checkEntry(Instrument instrument,
			Period pPeriod, OfferSide side, Filter filter, IBar bidBar,
			IBar askBar) throws JFException {
		// entry is two-step process. First a candle signal at channel extreme
		// is checked. Once this appears we wait for Stoch momentum to be
		// confirming
		// Only rarely does this happen on the same bar, but need to check this
		// situation too !
		if (!candleSignalAppeared) {
			TradeTrigger.TriggerDesc bearishSignalDesc = candles
					.bearishReversalCandlePatternDesc(instrument, pPeriod,
							side, bidBar.getTime());
			if (bearishSignalDesc != null
					&& bearishSignalDesc.channelPosition >= thresholdLevel) {
				candleSignalAppeared = true;
				candleSignalDesc = bearishSignalDesc;
			}
		}
		// now check the momentum condition too
		if (candleSignalAppeared && !momentumConfired) {
			if (askBar.getClose() > candleSignalDesc.pivotLevel)
				reset();

			if (candleSignalAppeared) {
				double stochs[] = momentum.getStochs(instrument, pPeriod, side,
						bidBar.getTime());
				double fastStoch = stochs[0], slowStoch = stochs[1];
				momentumConfired = fastStoch < slowStoch && fastStoch < 80.0;
			}
		}
		if (candleSignalAppeared && momentumConfired) {
			if (askBar.getClose() > candleSignalDesc.pivotLevel)
				reset();
			else {
				// signal is valid until momentum confirms it. Opposite signals
				// are ignored for the time being, strategies / setups should
				// take care about them
				double stochs[] = momentum.getStochs(instrument, pPeriod, side,
						bidBar.getTime());
				double fastStoch = stochs[0], slowStoch = stochs[1];
				if (!(fastStoch < slowStoch && fastStoch < 80.0)
						|| (fastStoch < 20 && slowStoch < 20))
					reset();
			}
		}
		return candleSignalAppeared && momentumConfired ? candleSignalDesc
				: null;
	}
}
