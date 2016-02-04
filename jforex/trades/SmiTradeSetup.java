package jforex.trades;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.ITimedData;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public class SmiTradeSetup extends TradeSetup {
	
	protected boolean mktEntry = true;

	public SmiTradeSetup(IIndicators indicators, IHistory history, IEngine engine, boolean mktEntry) {
		super(indicators, history, engine);
		this.mktEntry = mktEntry;
	}

	@Override
	public String getName() {
		return new String("SMI");
	}

	@Override
	public void inTradeProcessing(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, IOrder order) throws JFException {
		// put on break even if opposite signal occurs
		ITradeSetup.EntryDirection signal = checkEntry(instrument, period, askBar, bidBar, filter);
		if (order.isLong() && signal.equals(ITradeSetup.EntryDirection.SHORT)) {
			if (bidBar.getClose() > order.getOpenPrice())
				order.setStopLossPrice(order.getOpenPrice());
			else if (bidBar.getLow() > order.getStopLossPrice())
				order.setStopLossPrice(bidBar.getLow());
			order.waitForUpdate(null);			
		} else if (!order.isLong() && signal.equals(ITradeSetup.EntryDirection.LONG))  {
			if (askBar.getClose() < order.getOpenPrice())
				order.setStopLossPrice(order.getOpenPrice());
			else if (bidBar.getHigh() < order.getStopLossPrice())
				order.setStopLossPrice(askBar.getHigh());
			order.waitForUpdate(null);						
		}
	}

	@Override
	public EntryDirection checkEntry(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter) throws JFException {
		double[][] slowSMI = indicators.smi(instrument, period, OfferSide.BID, 50, 15, 5, 3, filter, 2, bidBar.getTime(), 0), 
				fastSMI = indicators.smi(instrument, period, OfferSide.BID, 10, 3, 5, 3, filter, 2,	bidBar.getTime(), 0);
		double 
			ma20 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 20, filter, 1,	bidBar.getTime(), 0)[0], 
			ma50 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 50, filter, 1, bidBar.getTime(), 0)[0], 
			ma100 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 100, filter, 1, bidBar.getTime(), 0)[0], 
			ma200 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 200, filter, 1, bidBar.getTime(), 0)[0];

		if (buySignal(slowSMI, fastSMI, ma20, ma50, ma100, ma200, bidBar)) {
			return ITradeSetup.EntryDirection.LONG;
		} else if (sellSignal(slowSMI, fastSMI, ma20, ma50, ma100, ma200, askBar)) {
			return ITradeSetup.EntryDirection.SHORT;
		}
		return ITradeSetup.EntryDirection.NONE;
	}

	@Override
	public EntryDirection checkExit(Instrument instrument, Period period, IBar askBar,	IBar bidBar, Filter filter, IOrder order) throws JFException {
		double[][] 
			slowSMI = indicators.smi(instrument, period, OfferSide.BID, 50, 15, 5, 3, filter, 2, bidBar.getTime(), 0), 
			fastSMI = indicators.smi(instrument, period, OfferSide.BID, 10, 3, 5, 3, filter, 2,	bidBar.getTime(), 0);

		if (exitLong(slowSMI, fastSMI, instrument, period, filter, bidBar))
			return ITradeSetup.EntryDirection.LONG;
		else if (exitShort(slowSMI, fastSMI, instrument, period, filter, askBar))
			return ITradeSetup.EntryDirection.SHORT;
		return ITradeSetup.EntryDirection.NONE;
	}

   boolean buySignal(double[][] slowSMI, double[][] fastSMI, double ma20, double ma50, double ma100, double ma200, IBar bidBar) {
        double
            prevSlow = slowSMI[0][0],
            currSlow = slowSMI[0][1],
            prevFast = fastSMI[0][0],
            currFast = fastSMI[0][1];
        
        // too strong a downtrend, price below middle MA, don't try it !
        if (ma20 < ma50 && ma50 < ma100 && bidBar.getClose() < ma50)
            return false; 
        return !(currFast < -60.0 && currSlow < -60.0) // no long entries in heavily oversold !
               && currFast < 60.0
               && prevSlow < currSlow
               && prevFast < currFast;
    }
    
    boolean sellSignal(double[][] slowSMI, double[][] fastSMI, double ma20, double ma50, double ma100, double ma200, IBar bidBar) {
        double
            prevSlow = slowSMI[0][0],
            currSlow = slowSMI[0][1],
            prevFast = fastSMI[0][0],
            currFast = fastSMI[0][1];

        // too strong an uptrend, price above middle MA, don't try it !
        if (ma20 > ma50 && ma50 > ma100 && bidBar.getClose() > ma50) 
            return false; 

        return !(currFast > 60.0 && currSlow > 60.0) // no short entries in heavily overbought !
               && currFast > -60.0 
               && prevSlow > currSlow
               && prevFast > currFast;
    }
    
    boolean exitLong(double[][] slowSMI, double[][] fastSMI, Instrument instrument, Period period, Filter filter, IBar currBar) throws JFException {
        double
            currSlow = slowSMI[0][1],
            prevFast = fastSMI[0][0],
            currFast = fastSMI[0][1],
            ma20 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 20, filter, 1, currBar.getTime(), 0)[0];
        
        return (!(currFast > 60.0 && currSlow > 60.0) // no need to exit long while heavily overbought !
               && currFast < currSlow && prevFast > currFast && currBar.getClose() < ma20) // probably no need to exit if slow overbought and still raising !!
               || (currFast < -60.0 && currSlow < -60.0) // also exit if both lines heavily overSOLD, clearly strong downtrend !
               || (currSlow < -60.0 && currFast < prevFast); // also if slow oversold (even if raising !) and fast starts falling               
    }
    
    boolean exitShort(double[][] slowSMI, double[][] fastSMI, Instrument instrument, Period period, Filter filter, IBar currBar) throws JFException {
        double
            currSlow = slowSMI[0][1],
            prevFast = fastSMI[0][0],
            currFast = fastSMI[0][1],
            ma20 = indicators.sma(instrument, period, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 20, filter, 1, currBar.getTime(), 0)[0];
        
        return (!(currFast < -60.0 && currSlow < -60.0) // no need to exixt short while heavily oversold !
               && currFast > currSlow && prevFast < currFast && currBar.getClose() > ma20)
               || (currFast > 60.0 && currSlow > 60.0)
               || (currSlow > 60.0 && currFast > prevFast);
    }
    
    boolean buySignalWeak(double[][] slowSMI, double[][] fastSMI, double ma20, double ma50, double ma100, double ma200, IBar bidBar) {
        double
            prevSlow = slowSMI[0][0],
            currSlow = slowSMI[0][1],
            prevFast = fastSMI[0][0],
            currFast = fastSMI[0][1];
        
        // too strong a downtrend, price below middle MA, don't try it !
        if (ma20 < ma50 && ma50 < ma100 && bidBar.getClose() < ma50)
            return false; 

        return !(currFast < -60.0 && currSlow < -60.0) // no long entries in heavily oversold !
               && prevSlow < currSlow
               && prevFast < currFast;
   }
    
    boolean sellSignalWeak(double[][] slowSMI, double[][] fastSMI, double ma20, double ma50, double ma100, double ma200, IBar bidBar) {
        double
            prevSlow = slowSMI[0][0],
            currSlow = slowSMI[0][1],
            prevFast = fastSMI[0][0],
            currFast = fastSMI[0][1];

        // too strong an uptrend, price above middle MA, don't try it !
        if (ma20 > ma50 && ma50 > ma100 && bidBar.getClose() > ma50) 
            return false; 

        return !(currFast > 60.0 && currSlow > 60.0) // no short entries in heavily overbought !
               && prevSlow > currSlow
               && prevFast > currFast;
    }

	@Override
	public EntryDirection checkCancel(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter) throws JFException {
		ITradeSetup.EntryDirection result = ITradeSetup.EntryDirection.NONE;
        double[][] 
                slowSMI = indicators.smi(instrument, period, OfferSide.BID, 50, 15, 5, 3, filter, 2, bidBar.getTime(), 0),
                fastSMI = indicators.smi(instrument, period, OfferSide.BID, 10, 3, 5, 3, filter, 2, bidBar.getTime(), 0);
            double
                ma20 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 20, filter, 1, bidBar.getTime(), 0)[0],
                ma50 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 50, filter, 1, bidBar.getTime(), 0)[0],
                ma100 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 100, filter, 1, bidBar.getTime(), 0)[0],
                ma200 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 200, filter, 1, bidBar.getTime(), 0)[0];
		
		if (!buySignalWeak(slowSMI, fastSMI, ma20, ma50, ma100, ma200, bidBar))
			return ITradeSetup.EntryDirection.LONG;
		else if (!sellSignalWeak(slowSMI, fastSMI, ma20, ma50, ma100, ma200, askBar))
			return ITradeSetup.EntryDirection.SHORT;
		
		return result;
	}

}
