package jforex.trades;

import java.util.Map;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.source.FlexTASource;
import jforex.techanalysis.source.FlexTAValue;

public class ShortCandleAndMomentumDetector extends AbstractCandleAndMomentumDetector {
	public ShortCandleAndMomentumDetector(double thresholdLevel, boolean pStyleAggressive) {
		super(thresholdLevel, pStyleAggressive);
	}

	public TradeTrigger.TriggerDesc checkEntry(Instrument instrument, Period pPeriod, OfferSide side, Filter filter, IBar bidBar, IBar askBar, Map<String, FlexTAValue> taValues) throws JFException {
		// entry is two-step process. First a candle signal at channel extreme
		// is checked. Once this appears we wait for Stoch momentum to be
		// confirming
		// Only rarely does this happen on the same bar, but need to check this
		// situation too !
		if (!candleSignalAppeared) {
			//TradeTrigger.TriggerDesc bearishSignalDesc = candles.bearishReversalCandlePatternDesc(instrument, pPeriod, side, bidBar.getTime());
			TradeTrigger.TriggerDesc bearishSignalDesc = taValues.get(FlexTASource.BEARISH_CANDLES).getCandleValue();
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
				double [][] 
						stochs = taValues.get(FlexTASource.STOCH).getDa2DimValue(),
						smis = taValues.get(FlexTASource.SMI).getDa2DimValue();
				double 
					fastStoch = stochs[0][1], 
					slowStoch = stochs[1][1],
					prevSlowSMI = smis[1][1], 
					currSlowSMI = smis[1][2], 
					prevFastSMI = smis[0][1], 
					currFastSMI = smis[0][2];					
				momentumConfired = styleAggressive ? fastStoch < slowStoch && currFastSMI < prevFastSMI : fastStoch < slowStoch && fastStoch < 80.0;
			}
		}
		if (candleSignalAppeared && momentumConfired) {
			if (askBar.getClose() > candleSignalDesc.pivotLevel)
				reset();
			else {
				// signal is valid until momentum confirms it. Opposite signals
				// are ignored for the time being, strategies / setups should
				// take care about them
				double [][] 
						stochs = taValues.get(FlexTASource.STOCH).getDa2DimValue(),
						smis = taValues.get(FlexTASource.SMI).getDa2DimValue();
				double 
					fastStoch = stochs[0][1], 
					slowStoch = stochs[1][1],
					prevSlowSMI = smis[1][1], 
					currSlowSMI = smis[1][2], 
					prevFastSMI = smis[0][1], 
					currFastSMI = smis[0][2];					
				if ((!styleAggressive && !(fastStoch < slowStoch && fastStoch < 80.0)) || (fastStoch < 20 && slowStoch < 20))
					reset();
				if ((styleAggressive && !(fastStoch < slowStoch && currFastSMI < prevFastSMI)) || (fastStoch < 20 && slowStoch < 20))
					reset();
			}
		}
		return candleSignalAppeared && momentumConfired ? candleSignalDesc : null;
	}
}
