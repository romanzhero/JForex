package jforex.stopmovers;

import jforex.techanalysis.Channel;
import jforex.techanalysis.TradeTrigger;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public class LongMRStopMover implements IStopMover {

	final protected Period basicTimeFrame = Period.THIRTY_MINS;
	final protected Period higherTimeFrame = Period.FOUR_HOURS;

	protected IContext context;

	protected TradeTrigger tradeTrigger;
	protected Channel channelPosition;	
	
	@Override
	public boolean isLong() {
		return true;
	}

	@Override
	public boolean signalFoundBool(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		return signalFound(instrument, period, askBar, bidBar) != Double.MIN_VALUE;
	}

	@Override
	public double signalFound(Instrument instrument, Period period,	IBar askBar, IBar bidBar) throws JFException {
		if (!period.equals(basicTimeFrame))
			return Double.MIN_VALUE;
		
		if (channelPosition.priceChannelPos(instrument, basicTimeFrame, OfferSide.BID, bidBar.getTime(), bidBar.getLow()) > 20.0)
			return Double.MIN_VALUE;
		
		IBar triggerLowBar = tradeTrigger.bullishReversalCandlePatternBar(instrument, basicTimeFrame, OfferSide.BID, bidBar.getTime());
    	if (triggerLowBar == null)
    		return Double.MIN_VALUE;
		return triggerLowBar.getLow();
	}

	@Override
	public void onStartExec(IContext context) throws JFException {
		this.context = context;
		
        tradeTrigger = new TradeTrigger(context.getIndicators(), context.getHistory(), null);
        channelPosition = new Channel(context.getHistory(), context.getIndicators());
	}
}
