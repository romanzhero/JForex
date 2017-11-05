package jforex.utils;

import com.dukascopy.api.IBar;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;

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
}
