package jforex.events;

import jforex.events.TAEventDesc.TAEventType;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.TradeTrigger.TriggerDesc;
import jforex.trades.CandleAndMomentumDetector;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public class CandleMomentumEvent implements ITAEvent {
	protected CandleAndMomentumDetector cmd = null;

	public CandleMomentumEvent(IIndicators pIndicators, IHistory pHistory) {
		cmd = new CandleAndMomentumDetector(new TradeTrigger(pIndicators, pHistory, null), new Momentum(pHistory, pIndicators));
	}

	@Override
	public String getName() {
		return new String("CandleMomentumEvent");
	}

	@Override
	public TAEventDesc checkEvent(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter) throws JFException {
		TriggerDesc longSignal = cmd.checkEntry(instrument, period, OfferSide.ASK, filter, bidBar, askBar);
		if (longSignal != null && longSignal.type.toString().contains("BULLISH")) {
			TAEventDesc result = new TAEventDesc(TAEventType.TA_EVENT, getName(), instrument, true, askBar, bidBar, period);
			result.candles = longSignal;
			return result;
		}
		
		TriggerDesc shortSignal = cmd.checkEntry(instrument, period, OfferSide.BID, filter, bidBar, askBar);
		if (shortSignal != null && shortSignal.type.toString().contains("BEARISH")) {
			TAEventDesc result = new TAEventDesc(TAEventType.TA_EVENT, getName(), instrument, false, askBar, bidBar, period);
			result.candles = longSignal;
			return result;
		}
		
		return null;
	}

}
