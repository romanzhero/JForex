package jforex.trades.momentum;

import java.util.Map;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.source.FlexTASource;
import jforex.techanalysis.source.TechnicalSituation;
import jforex.utils.log.FlexLogEntry;

public class LongCandleAndMomentumDetector extends AbstractCandleAndMomentumDetector {
	
	public LongCandleAndMomentumDetector(double thresholdLevel, boolean pStyleAggressive) {
		super(thresholdLevel, pStyleAggressive);
	}

	public TradeTrigger.TriggerDesc checkEntry(Instrument instrument, Period pPeriod, OfferSide side, Filter filter, IBar bidBar, IBar askBar, Map<String, FlexLogEntry> taValues) throws JFException {
		// entry is two-step process. First a candle signal at channel extreme is checked. Once this appears we wait for Stoch momentum to be confirming
		// Only rarely does this happen on the same bar, but need to check this situation too !
		if (!candleSignalAppeared) {
			//TradeTrigger.TriggerDesc bullishSignalDesc = candles.bullishReversalCandlePatternDesc(instrument, pPeriod, side, bidBar.getTime());
			TradeTrigger.TriggerDesc bullishSignalDesc = taValues.get(FlexTASource.BULLISH_CANDLES).getCandleValue();
			if (bullishSignalDesc != null && bullishSignalDesc.channelPosition <= thresholdLevel) {
				candleSignalAppeared = true;
				candleSignalDesc = bullishSignalDesc;
			}
		}
		// now check the momentum condition too
		if (candleSignalAppeared && !momentumConfired) {
			// however it might happen that S/R of candle signal was exceeded in
			// the opposite direction
			// MUST cancel the whole signal !
			if (bidBar.getClose() < candleSignalDesc.pivotLevel)
				reset();

			if (candleSignalAppeared) {
				TechnicalSituation taSituation = taValues.get(FlexTASource.TA_SITUATION).getTehnicalSituationValue();
				Momentum.STOCH_STATE stoch = taSituation.stochState;
				Momentum.SMI_STATE smi = taSituation.smiState;
				Momentum.SINGLE_LINE_STATE fastSMI = taSituation.fastSMIState;
				momentumConfired = (stoch.equals(Momentum.STOCH_STATE.BULLISH_CROSS_FROM_OVERSOLD)
									|| stoch.equals(Momentum.STOCH_STATE.BULLISH_CROSS)
									|| stoch.equals(Momentum.STOCH_STATE.BULLISH_RAISING_IN_MIDDLE)
									|| stoch.equals(Momentum.STOCH_STATE.BULLISH_OVERBOUGHT_FAST)
									|| stoch.equals(Momentum.STOCH_STATE.BULLISH_OVERBOUGHT_BOTH))
									&& (fastSMI.toString().startsWith("RAISING") 
										|| fastSMI.toString().startsWith("TICKED_UP")
										|| smi.equals(Momentum.SMI_STATE.BULLISH_WEAK_OVERSOLD_SLOW_BELOW_FAST)
										|| smi.equals(Momentum.SMI_STATE.BULLISH_WEAK_RAISING_IN_MIDDLE));
			}
		}
		if (candleSignalAppeared && momentumConfired) {
			// however it might happen that S/R of candle signal was exceeded in the opposite direction
			// MUST cancel the whole signal !
			if (bidBar.getClose() < candleSignalDesc.pivotLevel)
				reset();
			else {
				// signal is valid until momentum confirms it. Opposite signals are ignored for the time being, strategies / setups should take care about them
				TechnicalSituation taSituation = taValues.get(FlexTASource.TA_SITUATION).getTehnicalSituationValue();
				Momentum.STOCH_STATE stoch = taSituation.stochState;
				Momentum.SINGLE_LINE_STATE fastSMI = taSituation.fastSMIState;
				boolean momentumStillConfired = (stoch.equals(Momentum.STOCH_STATE.BULLISH_CROSS_FROM_OVERSOLD)
									|| stoch.equals(Momentum.STOCH_STATE.BULLISH_CROSS)
									|| stoch.equals(Momentum.STOCH_STATE.BULLISH_OVERBOUGHT_FAST)
									|| stoch.equals(Momentum.STOCH_STATE.BULLISH_OVERBOUGHT_BOTH))
								&& (fastSMI.toString().startsWith("RAISING") || fastSMI.toString().startsWith("TICKED_UP"));

				if (!momentumStillConfired)
					reset();
			}
		}
		return candleSignalAppeared && momentumConfired ? candleSignalDesc : null;
	}
}
