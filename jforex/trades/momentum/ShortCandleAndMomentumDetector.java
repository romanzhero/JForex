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

public class ShortCandleAndMomentumDetector extends AbstractCandleAndMomentumDetector {
	public ShortCandleAndMomentumDetector(double thresholdLevel, boolean pStyleAggressive) {
		super(thresholdLevel, pStyleAggressive);
	}

	public TradeTrigger.TriggerDesc checkEntry(Instrument instrument, Period pPeriod, OfferSide side, Filter filter, IBar bidBar, IBar askBar, Map<String, FlexLogEntry> taValues) throws JFException {
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
			if (askBar.getClose() > candleSignalDesc.pivotLevel || taValues.get(FlexTASource.CHANNEL_POS).getDoubleValue() < 0)
				reset();

			if (candleSignalAppeared) {
				TechnicalSituation taSituation = taValues.get(FlexTASource.TA_SITUATION).getTehnicalSituationValue();
				Momentum.STOCH_STATE stoch = taSituation.stochState;
				Momentum.SMI_STATE smi = taSituation.smiState;
				Momentum.SINGLE_LINE_STATE fastSMI = taSituation.fastSMIState;
				momentumConfired = (stoch.equals(Momentum.STOCH_STATE.BEARISH_CROSS_FROM_OVERBOUGTH)
									|| stoch.equals(Momentum.STOCH_STATE.BEARISH_CROSS)
									|| stoch.equals(Momentum.STOCH_STATE.BEARISH_FALLING_IN_MIDDLE)
									|| stoch.equals(Momentum.STOCH_STATE.BEARISH_OVERSOLD_FAST)
									|| stoch.equals(Momentum.STOCH_STATE.BEARISH_OVERSOLD_BOTH))
									&& (fastSMI.toString().startsWith("FALLING") 
										|| fastSMI.toString().startsWith("TICKED_DOWN")
										|| smi.equals(Momentum.SMI_STATE.BEARISH_WEAK_OVERBOUGHT_SLOW_ABOVE_FAST)
										|| smi.equals(Momentum.SMI_STATE.BEARISH_WEAK_FALLING_IN_MIDDLE));
			}
		}
		if (candleSignalAppeared && momentumConfired) {
			if (askBar.getClose() > candleSignalDesc.pivotLevel)
				reset();
			else {
				// signal is valid until momentum confirms it. Opposite signals
				// are ignored for the time being, strategies / setups should
				// take care about them
				TechnicalSituation taSituation = taValues.get(FlexTASource.TA_SITUATION).getTehnicalSituationValue();
				Momentum.STOCH_STATE stoch = taSituation.stochState;
				Momentum.SMI_STATE smi = taSituation.smiState;
				Momentum.SINGLE_LINE_STATE fastSMI = taSituation.fastSMIState;
				boolean momentumStillConfirmed = (stoch.equals(Momentum.STOCH_STATE.BEARISH_CROSS_FROM_OVERBOUGTH)
									|| stoch.equals(Momentum.STOCH_STATE.BEARISH_CROSS)
									|| stoch.equals(Momentum.STOCH_STATE.BEARISH_FALLING_IN_MIDDLE)
									|| stoch.equals(Momentum.STOCH_STATE.BEARISH_OVERSOLD_FAST)
									|| stoch.equals(Momentum.STOCH_STATE.BEARISH_OVERSOLD_BOTH))
									&& (fastSMI.toString().startsWith("FALLING") 
										|| fastSMI.toString().startsWith("TICKED_DOWN")
										|| smi.equals(Momentum.SMI_STATE.BEARISH_WEAK_OVERBOUGHT_SLOW_ABOVE_FAST)
										|| smi.equals(Momentum.SMI_STATE.BEARISH_WEAK_FALLING_IN_MIDDLE));
				if (!momentumStillConfirmed)
					reset();
			}
		}
		return candleSignalAppeared && momentumConfired ? candleSignalDesc : null;
	}
}
