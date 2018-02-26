package jforex.utils;

import java.util.Map;

import com.dukascopy.api.IBar;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;

import jforex.events.TAEventDesc;
import jforex.events.TAEventDesc.TAEventType;
import jforex.techanalysis.source.FlexTASource;
import jforex.utils.log.FlexLogEntry;

public class StopLoss {

	// return false if the order was closed due to setting breakeven. Happens due to very fast moves that bring below entry price
	public static boolean setBreakEvenSituative(IOrder order, IBar lastBar) throws JFException {
		if (order.isLong()) {
			if (lastBar.getLow() > order.getOpenPrice()) {
				// still in profit
				if (order.getStopLossPrice() == 0.0
					|| order.getOpenPrice() > order.getStopLossPrice()) {
					order.setStopLossPrice(order.getOpenPrice(), OfferSide.BID);
					order.waitForUpdate(null);
				}
			} else {
				// must accept SL below entry price
				//TODO: check also if the last close is below entry price - simply close the order in that case !
				if (order.getStopLossPrice() == 0.0
					|| lastBar.getLow() > order.getStopLossPrice()) {
					order.setStopLossPrice(lastBar.getLow(), OfferSide.BID);
					order.waitForUpdate(null);
				}
			}			
		} else {
			if (lastBar.getHigh() < order.getOpenPrice()) {
				// still in profit
				if (order.getStopLossPrice() == 0.0
					|| order.getOpenPrice() < order.getStopLossPrice()) {
					order.setStopLossPrice(order.getOpenPrice(), OfferSide.ASK);
					order.waitForUpdate(null);
				}
			} else {
				// must accept SL below entry price
				if (order.getStopLossPrice() == 0.0
					|| lastBar.getHigh() < order.getStopLossPrice()) {
					order.setStopLossPrice(lastBar.getHigh(), OfferSide.ASK);
					order.waitForUpdate(null);
				}
			}			
		}
		if (order.getState().equals(IOrder.State.CLOSED))
			return false;
		else
			return true;
	}
	
	public static double calcATRBasedStopLossDistance(Instrument instrument, Map<String, FlexLogEntry> taValues, double multiple) {
		return FXUtils.roundToPip(multiple * taValues.get(FlexTASource.ATR).getDoubleValue() / Math.pow(10, instrument.getPipValue()), instrument);		
	}
	
	public static double calcATRBasedStopLoss(Instrument instrument, boolean isLong, Map<String, FlexLogEntry> taValues, double multiple, IBar bidBar, IBar askBar) {
		double distance = calcATRBasedStopLossDistance(instrument, taValues, multiple);	
		if (isLong) {
			return bidBar.getClose() - distance;
		} else {
			return askBar.getClose() + distance;			
		}
 	}

	public static void setStopLoss(IOrder order, double price, long time, Class c) {
		try {
			order.setStopLossPrice(FXUtils.roundToPip(price, order.getInstrument()));
			order.waitForUpdate(null);				
		} catch (JFException e) {
			System.out.println(FXUtils.getFormatedTimeGMT(time) + ": Exception when trying to set SL in class " + c.getCanonicalName() 
					+ ", exception message: " + e.getMessage());
		}
		
	}
	
	public static void setCloserOnlyStopLoss(IOrder order, double price, long time, Class c) {
		try {
			if (order.getStopLossPrice() != 0.0) {
				if (order.isLong()) {
					// new value can be only higher, i.e. closer to entry price
					if (FXUtils.roundToPip(price, order.getInstrument()) <= order.getStopLossPrice())
						return;
				} else {
					if (FXUtils.roundToPip(price, order.getInstrument()) >= order.getStopLossPrice())
						return;					
				}				
			}
			order.setStopLossPrice(FXUtils.roundToPip(price, order.getInstrument()));
			order.waitForUpdate(null);				
		} catch (JFException e) {
			System.out.println(FXUtils.getFormatedTimeGMT(time) + ": Exception when trying to set SL in class " + c.getCanonicalName() 
					+ ", exception message: " + e.getMessage());
		}
		
	}
}
