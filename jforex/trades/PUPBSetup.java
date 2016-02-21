package jforex.trades;

import java.util.List;

import jforex.events.TAEventDesc;
import jforex.events.TAEventDesc.TAEventType;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.utils.FXUtils;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public class PUPBSetup extends FlatTradeSetup implements ITradeSetup {

	public PUPBSetup(IIndicators indicators, IHistory history, IEngine engine) {
		super(indicators, history, engine, true);
		// TODO Auto-generated constructor stub
	}

	@Override
	public String getName() {
		return "PUPB";
	}

	@Override
	public TAEventDesc checkEntry(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter) throws JFException {
			TradeTrigger.TriggerDesc 
			currLongSignal = longCmd.checkEntry(instrument, period, OfferSide.BID, filter, bidBar, askBar), 
			currShortSignal = shortCmd.checkEntry(instrument, period, OfferSide.BID, filter, bidBar, askBar);
	
		if (currLongSignal == null && currShortSignal == null)
			return null;
	
		Trend.FLAT_REGIME_CAUSE currBarFlat = trend.isFlatRegime(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, filter,	bidBar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS, 30.0);
		// no entries in flat regime
		if (!currBarFlat.equals(Trend.FLAT_REGIME_CAUSE.NONE))
			return null;
	
		Trend.TREND_STATE trendState = trend.getTrendState(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime());
		boolean 
			isMA200Highest = trend.isMA200Highest(instrument, period, OfferSide.BID,IIndicators.AppliedPrice.CLOSE, bidBar.getTime()), 
			isMA200Lowest = trend.isMA200Lowest(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime()),
			uptrend = (trendState.equals(Trend.TREND_STATE.UP_STRONG) || trendState.equals(Trend.TREND_STATE.UP_MILD)) && isMA200Lowest,
			downtrend = (trendState.equals(Trend.TREND_STATE.DOWN_STRONG) || trendState.equals(Trend.TREND_STATE.DOWN_STRONG)) && isMA200Highest;
		if (!uptrend && !downtrend) {
			return null; 
		}
		
		boolean 
			bulishSignal = uptrend && currLongSignal != null && currLongSignal.channelPosition < 0,
			bearishSignal = downtrend && currShortSignal != null && currShortSignal.channelPosition > 100;
		locked = bulishSignal || bearishSignal;
		// there is a signal !
		if (bulishSignal) {
			lastLongSignal = currLongSignal;
			TAEventDesc result = new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, true, askBar, bidBar, period);
			result.candles = currLongSignal;
			return result;
		} else if (bearishSignal) {
			lastShortSignal = currShortSignal;
			TAEventDesc result = new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, false, askBar, bidBar, period);
			result.candles = currShortSignal;
			return result;
		} else
			return null;
	}

	@Override
	public void inTradeProcessing(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, IOrder order,	List<TAEventDesc> marketEvents) throws JFException {
		IBar barToCheck = null;
		if (order.isLong())
			barToCheck = bidBar;
		else
			barToCheck = askBar;
		if (order.getState().equals(IOrder.State.OPENED)) {
			// still waiting. Cancel if price already exceeded SL level without triggering entry stop
			if ((order.isLong() && barToCheck.getClose() < order.getStopLossPrice())
				|| (!order.isLong() && barToCheck.getClose() > order.getStopLossPrice())) {
				order.close();
				order.waitForUpdate(null);
				afterTradeReset(instrument);
			}
		} else if (order.getState().equals(IOrder.State.FILLED)) {
			// continue executing just to keep pace
			TradeTrigger.TriggerDesc 
				currLongSignal = longCmd.checkEntry(instrument, period, OfferSide.BID, filter, bidBar, askBar), 
				currShortSignal = shortCmd.checkEntry(instrument, period, OfferSide.BID, filter, bidBar, askBar);
			
			// check whether to unlock the trade - price exceeded opposite
			// channel border at the time of the signal
			if ((order.isLong() && askBar.getClose() > lastLongSignal.bBandsTop)
				|| (!order.isLong() && bidBar.getClose() < lastShortSignal.bBandsBottom)) {
				locked = false;
				// do not reset trade completely ! Keep control over order until other setups take over !
			}
		}
	}

}
