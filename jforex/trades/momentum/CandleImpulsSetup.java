/**
 * 
 */
package jforex.trades.momentum;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

import jforex.events.TAEventDesc;
import jforex.events.TAEventDesc.TAEventType;
import jforex.techanalysis.source.FlexTASource;
import jforex.techanalysis.source.FlexTAValue;
import jforex.trades.ITradeSetup;
import jforex.trades.TradeSetup;
import jforex.utils.RollingAverage;

/**
 * Simple setup to enter on 3 candles all in the same direction. At least one must be strong, with range above average and 80% body
 * Exit on the first opposite candle, but not while Stoch is OS/OB in the direction of the trade
 * Stop-loss is from the entry until the extreme of the first bar in the block of three
 * The strategy should avoid trading after profit is above some significant percentage of daily range
 * Idea is to fetch few small but relatively safe trades per day
 *
 */
public class CandleImpulsSetup extends TradeSetup implements ITradeSetup {
	
	protected Map<Instrument, RollingAverage> barRangeAverages = null;
	protected TAEventDesc lastEntryDesc = null;
	
	protected enum BodyDirection {UP, DOWN, NONE};

	public CandleImpulsSetup(IEngine engine, IContext context, Map<Instrument, RollingAverage> averages) {
		super(engine, context);
		barRangeAverages = averages;
	}


	public CandleImpulsSetup(IEngine engine, IContext context, Map<Instrument, RollingAverage> averages, boolean pTakeOverOnly) {
		super(engine, context, pTakeOverOnly);
		barRangeAverages = averages;
	}


	@Override
	public String getName() {
		return "CandleImpulsSetup";
	}

	public TAEventDesc checkEntry(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter, Map<String, FlexTAValue> taValues) throws JFException {
		List<IBar> last3BidBars = context.getHistory().getBars(instrument, period, OfferSide.BID, Filter.WEEKENDS, 3, bidBar.getTime(), 0);
		DateTime
			firstBarDate = new DateTime(last3BidBars.get(0).getTime()),
			lastBarDate = new DateTime(last3BidBars.get(2).getTime());
		if (firstBarDate.getDayOfWeek() == DateTimeConstants.FRIDAY && lastBarDate.getDayOfWeek() == DateTimeConstants.SUNDAY)
			return null;
		
		List<IBar> last3AskBars = context.getHistory().getBars(instrument, period, OfferSide.ASK, Filter.WEEKENDS, 3, bidBar.getTime(), 0);
		if (barsSignalGiven(last3BidBars, instrument).equals(BodyDirection.DOWN)) {
			lastEntryDesc = new TAEventDesc(TAEventType.ENTRY_SIGNAL, "CandleImpulsShort", instrument, false, askBar, bidBar, period);
			lastEntryDesc.stopLossLevel = last3AskBars.get(0).getHigh();
			return lastEntryDesc;
		} else if (barsSignalGiven(last3AskBars, instrument).equals(BodyDirection.UP)) {
			lastEntryDesc = new TAEventDesc(TAEventType.ENTRY_SIGNAL, "CandleImpulsLong", instrument, true, askBar, bidBar, period);
			lastEntryDesc.stopLossLevel = last3BidBars.get(0).getLow();
			return lastEntryDesc;
		}
		return null;
	}
	
	protected BodyDirection barsSignalGiven(List<IBar> bars, Instrument instrument) {
		boolean barSizeOK = false;
		BodyDirection prevBodyDirection = BodyDirection.NONE;
		RollingAverage average = barRangeAverages.get(instrument);
		for (IBar currBar : bars) {
			BodyDirection currBodyDirection = currBar.getClose() > currBar.getOpen() ? BodyDirection.UP : BodyDirection.DOWN;
			if (!prevBodyDirection.equals(BodyDirection.NONE)) {
				if (!currBodyDirection.equals(prevBodyDirection))
					return BodyDirection.NONE;
			}
			if (!barSizeOK) // one big candle in trade direction enough
				barSizeOK = (currBar.getHigh() - currBar.getLow()) > average.getAverage() 
							&& Math.abs(currBar.getClose() - currBar.getOpen()) / (currBar.getHigh() - currBar.getLow()) > 0.6;
			prevBodyDirection = currBodyDirection;
		}
		return barSizeOK ? prevBodyDirection : BodyDirection.NONE;	
	}

	/*
	 * Moves SL to the oposite extreme of the last candle if there is
	 * - profitable trade (if price moved above entry leave the original SL - let the trade "breathe")
	 * - strong candle opposite the trade (range above average and body > 80% of range)
	 * (possible refinement: in addition any strong reversal 1- or 2-bar candle pattern, with same range criteria)
	 * - but only if there is no more favourable OS/OB in Stoch 
	 */
	@Override
	public void inTradeProcessing(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter,
			IOrder order, Map<String, FlexTAValue> taValues, List<TAEventDesc> marketEvents) throws JFException {
		super.inTradeProcessing(instrument, period, askBar, bidBar, filter, order, taValues, marketEvents);
		//TODO: also reach to the opposite signal ! Close the previous trade !
		RollingAverage average = barRangeAverages.get(instrument);
		double[][] stochs = taValues.get(FlexTASource.STOCH).getDa2DimValue();
		double 
			fastStoch = stochs[0][1], 
			slowStoch = stochs[1][1]; 
		if (order.isLong()) {
			if ((fastStoch >= 80 && slowStoch >= 80)
				|| bidBar.getClose() < order.getOpenPrice())
				return;
			
			double barRange = bidBar.getHigh() - bidBar.getLow();
			if (bidBar.getClose() < bidBar.getOpen()
				&&  barRange > average.getAverage()
				&& Math.abs(bidBar.getOpen() - bidBar.getClose()) / barRange > 0.8
				&& bidBar.getHigh() > order.getOpenPrice()
				&& bidBar.getHigh() > order.getStopLossPrice()) {
				lastTradingEvent = "CandleImpuls move SL signal (long)";
				order.setStopLossPrice(bidBar.getHigh(), OfferSide.BID);
			}
		} else {
			if ((fastStoch <= 20 && slowStoch <= 20)
				|| askBar.getClose() > order.getOpenPrice())
					return;
			
				double barRange = askBar.getHigh() - askBar.getLow();
				if (askBar.getClose() > askBar.getOpen()
					&&  barRange > average.getAverage()
					&& Math.abs(askBar.getOpen() - askBar.getClose()) / barRange > 0.8
					&& askBar.getLow() < order.getOpenPrice()
					&& askBar.getLow() < order.getStopLossPrice()) {
					lastTradingEvent = "CandleImpuls move SL signal (short)";
					order.setStopLossPrice(askBar.getLow(), OfferSide.ASK);
				}
			
		}
		
	}


	@Override
	public IOrder submitOrder(String label, Instrument instrument, boolean isLong, double amount, IBar bidBar, IBar askBar) throws JFException {
		return submitMktOrder(label, instrument, isLong, amount, bidBar, askBar, lastEntryDesc.stopLossLevel);
	}


	@Override
	public void afterTradeReset(Instrument instrument) {
		super.afterTradeReset(instrument);
		lastEntryDesc = null;
	}


	@Override
	public void updateOnBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) {
		super.updateOnBar(instrument, period, askBar, bidBar);
		RollingAverage average = barRangeAverages.get(instrument);
		average.calcUpdatedAverage(bidBar.getHigh() - bidBar.getLow());
	}
	
	

}
