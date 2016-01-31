package jforex.trades;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

public class SmaTradeSetup extends TradeSetup {
	
	protected boolean
		// IMPORTANT ! If true it means entry signal will be given ONLY on one bar, when MAs cross to 1 or 6 position ! 
		// Otherwise entry signal is given for all bars when MAs are in 1 or 6 position ! 
		onlyCross = true, 
		mktEntry = true;
    private Map<String, Boolean>  ma50TrailFlags = new HashMap<String, Boolean>();


	public SmaTradeSetup(IIndicators indicators, IHistory history, IEngine engine, Set<Instrument> subscribedInstruments, boolean mktEntry, boolean onlyCross) {
		super(indicators, history, engine);
		this.mktEntry = mktEntry;
		
        for (Instrument i : subscribedInstruments) {
            ma50TrailFlags.put(i.name(), new Boolean(false));
        }
	}

	@Override
	public String getName() {
		return new String("SMATrendIDFollow");
	}

	@Override
	public EntryDirection checkEntry(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter) throws JFException {
		double[] 
			ma20 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 20, filter, 2,	bidBar.getTime(), 0), 
			ma50 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 50, filter, 2, bidBar.getTime(), 0), 
			ma100 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 100, filter, 2, bidBar.getTime(), 0), 
			ma200 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 200, filter, 2, bidBar.getTime(), 0);

		if (buySignal(ma20, ma50, ma100, ma200, bidBar, onlyCross)) {
			return ITradeSetup.EntryDirection.LONG;
		} else if (sellSignal(ma20, ma50, ma100, ma200, askBar, onlyCross)) {
			return ITradeSetup.EntryDirection.SHORT;
		}
		return ITradeSetup.EntryDirection.NONE;
	}
	
	@Override
	public EntryDirection checkTakeOver(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter) throws JFException {
		double[] 
				ma20 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 20, filter, 2,	bidBar.getTime(), 0), 
				ma50 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 50, filter, 2, bidBar.getTime(), 0), 
				ma100 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 100, filter, 2, bidBar.getTime(), 0), 
				ma200 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 200, filter, 2, bidBar.getTime(), 0);

		if (buySignal(ma20, ma50, ma100, ma200, bidBar, false)) {
			return ITradeSetup.EntryDirection.LONG;
		} else if (sellSignal(ma20, ma50, ma100, ma200, askBar, false)) {
			return ITradeSetup.EntryDirection.SHORT;
		}
		return ITradeSetup.EntryDirection.NONE;
	}

   boolean buySignal(double[] ma20, double[] ma50, double[] ma100, double[] ma200, IBar bidBar, boolean strict) {
	   	double
	   		currMA20 = ma20[1],
	   		currMA50 = ma50[1],
 	   		currMA100 = ma100[1],
   	   		prevMA20 = ma20[0],
	   		prevMA50 = ma50[0],
 	   		prevMA100 = ma100[0];
	   
       	if (strict) 
    		return (currMA20 > currMA50 && currMA50 > currMA100) && !(prevMA20 > prevMA50 && prevMA50 > prevMA100);
    	else
       		return currMA20 > currMA50 && currMA50 > currMA100;
    }
    
    boolean sellSignal(double[] ma20, double[] ma50, double[] ma100, double[] ma200, IBar bidBar, boolean strict) {
	   	double
   		currMA20 = ma20[1],
   		currMA50 = ma50[1],
	   	currMA100 = ma100[1],
	   	prevMA20 = ma20[0],
   		prevMA50 = ma50[0],
	   	prevMA100 = ma100[0];
   
	   	if (strict) 
			return (currMA20 < currMA50 && currMA50 < currMA100) && !(prevMA20 < prevMA50 && prevMA50 < prevMA100);
		else
	   		return currMA20 < currMA50 && currMA50 < currMA100;
    }
    

	@Override
	public void inTradeProcessing(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, IOrder order) throws JFException {
		// TODO Auto-generated method stub
		super.inTradeProcessing(instrument, period, askBar, bidBar, filter, order);
		if (order == null)
			return;

		double
	        ma20 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 20, filter, 1, bidBar.getTime(), 0)[0],
	        ma50 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 50, filter, 1, bidBar.getTime(), 0)[0],
	        ma100 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 100, filter, 1, bidBar.getTime(), 0)[0];
		
		if (order.isLong())
			startTrailingLong(ma20, ma50, ma100, bidBar, order);
		else 
			startTrailingShort(ma20, ma50, ma100, askBar, order);
				
	}
	
    boolean startTrailingLong(double ma20, double ma50, double ma100, IBar bidBar, IOrder order) throws JFException {
    	// POGRESNO !!! Uslov if (ma20 > ma50 && ma50 > ma100) je vec proveren pri ulazu, ALI !!! moze da se promeni u toku trejda ! Vidi EURUSD 4H 10.-12.02.15 !!! Iz 
    	// STRONG_DOWNTREND je presao u FRESH_DOWNTREND !!! U svakom slucaju ova provera treba da se izbaci za sada i ma50 koristi kao support/resistance
    	// tj. kriterijum za trail bez obzira na polozaj MAs !
    	// mozda jos jedino proveriti da li je cena ISPOD / IZNAD ma50, jer inace trailing odmah zatvara poziciju !
    	
    	// u konkretnom primeru je market situation bio u stvari cisti flat !!!
        
        boolean result = false;
    	Boolean ma50Trailing = ma50TrailFlags.get(order.getInstrument().name());
        if (ma50Trailing.booleanValue() && bidBar.getHigh() > ma20 && bidBar.getLow() > ma50) {
           ma50Trailing = new Boolean(false);
           ma50TrailFlags.put(order.getInstrument().name(), new Boolean(false));
        }
        // check whether MA50 was WITHIN candle 
        if (!ma50Trailing.booleanValue() && bidBar.getLow() < ma50 && (order.getStopLossPrice() == 0.0 || bidBar.getLow() > order.getStopLossPrice())) {
           // put SL on low of the bar which crossed MA50. No real trailing, do it only once
           ma50TrailFlags.put(order.getInstrument().name(), new Boolean(true));
           order.setStopLossPrice(FXUtils.roundToPip(bidBar.getLow(), order.getInstrument()));     
           order.waitForUpdate(null);          
           result = true;
        }
        else if (!ma50Trailing.booleanValue() && bidBar.getHigh() < ma20) {
            // start trailing on MA50
            // additional criteria !!! ONLY if ma50 and ma100 relatively close !! If far apart in a strong trend don't do it, same for piercing MA50 !!!
            // also for ENTRIES !!! Crossing MA50 in WEAK and STRONG trend is not the same !!! See 12.11 - 4.12.15 ! Missed some 160 pips profit compared to exit at ma100, plus 350 pips of unnecessary losses !!! Total 500+ pips missed !!!!
            if (order.getStopLossPrice() == 0.0 || ma50 > order.getStopLossPrice()) {
                order.setStopLossPrice(FXUtils.roundToPip(ma50, order.getInstrument())); 
                order.waitForUpdate(null);
                result = true;
            }
        }
        return result;
    }
    
    // true means there's a strong downtrend and momentum exits are now switched OFF
    boolean startTrailingShort(double ma20, double ma50, double ma100, IBar askBar, IOrder order)  throws JFException {
        boolean result = false;
        Boolean ma50Trailing = (Boolean)ma50TrailFlags.get(order.getInstrument().name());
        if (ma50Trailing.booleanValue() && askBar.getLow() < ma20 && askBar.getHigh() < ma50) {
            ma50Trailing = new Boolean(false);
            ma50TrailFlags.put(order.getInstrument().name(), new Boolean(false));
        }
        // check whether MA50 was WITHIN candle 
        if (!ma50Trailing.booleanValue() && askBar.getHigh() > ma50 && (order.getStopLossPrice() == 0.0 || askBar.getHigh() < order.getStopLossPrice())) {
           // put SL on low of the bar which crossed MA50. No real trailing, do it only once
           ma50TrailFlags.put(order.getInstrument().name(), new Boolean(true));
           order.setStopLossPrice(FXUtils.roundToPip(askBar.getHigh(), order.getInstrument())); 
           order.waitForUpdate(null);                
           result = true;
        }
        else if (!ma50Trailing.booleanValue() && askBar.getLow() > ma20) {
            // start trailing on MA50
            if (order.getStopLossPrice() == 0.0 || ma50 < order.getStopLossPrice()) {
                order.setStopLossPrice(FXUtils.roundToPip(ma50, order.getInstrument())); 
                order.waitForUpdate(null);
                result = true;
            }
        }
        return result;
    }

	@Override
	public void afterTradeReset(Instrument instrument) {
        ma50TrailFlags.put(instrument.name(), new Boolean(false));
	}

	@Override
	public boolean isTradeLocked(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter, IOrder order) throws JFException {
		double[] 
				ma20 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 20, filter, 2,	bidBar.getTime(), 0), 
				ma50 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 50, filter, 2, bidBar.getTime(), 0), 
				ma100 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 100, filter, 2, bidBar.getTime(), 0), 
				ma200 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 200, filter, 2, bidBar.getTime(), 0);

		if (order.isLong() && !buySignal(ma20, ma50, ma100, ma200, bidBar, false)) {
			return false;
		} else if (!order.isLong() && !sellSignal(ma20, ma50, ma100, ma200, askBar, false)) {
			return false;
		}
		return true;
	}

}
