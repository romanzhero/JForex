package jforex.events;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

import jforex.events.ITAEvent;
import jforex.events.TAEventDesc.TAEventType;
import jforex.techanalysis.TradeTrigger;

public class LongCandlesEvent implements ITAEvent {
	protected TradeTrigger candles = null;

	public LongCandlesEvent(IIndicators pIndicators, IHistory pHistory) {
		super();
		this.candles = new TradeTrigger(pIndicators, pHistory, null);
	}

	@Override
	public String getName() {
		return new String("LongCandlesEvent");
	}

	@Override
	public TAEventDesc checkEvent(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter) throws JFException {
		TradeTrigger.TriggerDesc signal = candles.bullishReversalCandlePatternDesc(instrument, period, OfferSide.ASK, bidBar.getTime());
		if (signal == null)
			return null;
		
		TAEventDesc result = new TAEventDesc(TAEventType.TA_EVENT, getName(), instrument, true, askBar, bidBar, period);
		result.candles = signal;
		return result;
	}


}
