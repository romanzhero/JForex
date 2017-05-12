package jforex.trades.momentum;

import java.util.List;
import java.util.Map;

import jforex.events.TAEventDesc;
import jforex.events.TAEventDesc.TAEventType;
import jforex.techanalysis.Trend.FLAT_REGIME_CAUSE;
import jforex.techanalysis.source.FlexTASource;
import jforex.techanalysis.source.FlexTAValue;
import jforex.trades.ITradeSetup;
import jforex.trades.TradeSetup;
import jforex.trades.ITradeSetup.EntryDirection;
import jforex.utils.FXUtils;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

public class SmiTradeSetup extends TradeSetup {

	protected boolean mktEntry = true;
	private double 
		flatPercThreshold,
		bBandsSqueezeThreshold;
	private double lastSL = 0.0;

	public SmiTradeSetup(IEngine engine, IContext context, boolean mktEntry, double pFlatPercThreshold, double pBBandsSqueezeThreshold) {
		super(engine, context);
		this.mktEntry = mktEntry;
		flatPercThreshold = pFlatPercThreshold;
		bBandsSqueezeThreshold = pBBandsSqueezeThreshold;
	}

	@Override
	public String getName() {
		return new String("SMI");
	}

	@Override
	public void inTradeProcessing(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, IOrder order, Map<String, FlexTAValue> taValues, List<TAEventDesc> marketEvents)	throws JFException {
		// put on break even if opposite signal occurs
		if (!mktEntry && order.getState().equals(IOrder.State.OPENED)) {
			boolean 
				oppositeFlatEntrySignal = false,
				oppositePUPBSignal = false;
			for (TAEventDesc taEvent : marketEvents) {
				oppositeFlatEntrySignal = taEvent.eventType.equals(TAEventType.ENTRY_SIGNAL) && taEvent.eventName.equals("Flat")
										  && ((order.isLong() && !taEvent.isLong) || (!order.isLong() && taEvent.isLong));
				oppositePUPBSignal = taEvent.eventType.equals(TAEventType.ENTRY_SIGNAL) && taEvent.eventName.equals("PUPB")
						  && ((order.isLong() && !taEvent.isLong) || (!order.isLong() && taEvent.isLong));
			}
			EntryDirection cancelSignal = checkCancel(instrument, period, askBar, bidBar, filter, taValues);
			if ((order.isLong() && cancelSignal.equals(EntryDirection.LONG))
				|| (!order.isLong() && cancelSignal.equals(EntryDirection.SHORT))
				|| oppositeFlatEntrySignal
				|| oppositePUPBSignal) {
				lastTradingEvent = "SMI cancel due to opposite flat signal";
				order.close();
				order.waitForUpdate(null);
			}				
			return;
		}
		
		TAEventDesc signal = checkEntry(instrument, period,	askBar, bidBar, filter, taValues);
		if (order.isLong() && signal != null && !signal.isLong) {
			if (order.getProfitLossInPips() < 2.5 * taValues.get(FlexTASource.ATR).getDoubleValue() * Math.pow(10, instrument.getPipScale())) { 
				lastTradingEvent = "SMI long breakeven signal";
				if (bidBar.getClose() > order.getOpenPrice()) {
					FXUtils.setStopLoss(order, order.getOpenPrice(), bidBar.getTime(), getClass());
				}
				else if (bidBar.getLow() > order.getStopLossPrice() || order.getStopLossPrice() == 0.0) {
					FXUtils.setStopLoss(order, bidBar.getLow(), bidBar.getTime(), getClass());
				}
			} else {
				lastTradingEvent = "SMI long profit protect signal (profit = 2.5 ATR)";
				if (bidBar.getLow() > order.getStopLossPrice() || order.getStopLossPrice() == 0.0) {
					FXUtils.setStopLoss(order, bidBar.getLow(), bidBar.getTime(), getClass());
				}
			}
		} else if (!order.isLong()	&& signal != null && signal.isLong) {
			lastTradingEvent = "SMI short breakeven signal";
			if (askBar.getClose() < order.getOpenPrice()) {
				FXUtils.setStopLoss(order, order.getOpenPrice(), bidBar.getTime(), getClass());
			}
			else if (bidBar.getHigh() < order.getStopLossPrice() || order.getStopLossPrice() == 0.0) {
				FXUtils.setStopLoss(order, askBar.getHigh(), bidBar.getTime(), getClass());
			}
		}
		if (order.isLong()) {
			longProtectProfitDueToBigBearishCandle(bidBar, order, 4, taValues);
			longProtectProfitOnATRCount(instrument, bidBar, order, taValues, 4);
			longMoveSLDueToBigBearishCandle(bidBar, order, taValues);
			longSetBreakEvenOnProfit(instrument, bidBar, order, taValues, 2);
			longMoveSLDueToExtremeRSI3(bidBar, order, taValues);			
		} else {
			shortProtectProfitDueToBigBullishCandle(askBar, order, 4, taValues);
			shortProtectProfitOnATRCount(instrument, askBar, order, taValues, 4);
			shortMoveSLDueToBigBullishCandle(bidBar, order, taValues);
			shortSetBreakEvenOnProfit(instrument, bidBar, order, taValues, 2);
			shortMoveSLDueToExtremeRSI3(bidBar, order, taValues);			
		}
		double bBandsSqueeze = taValues.get(FlexTASource.BBANDS_SQUEEZE_PERC).getDoubleValue();	
		// check market events. Get out if opposite flat signal occurs - it will take over immediately !
		for (TAEventDesc taEvent : marketEvents) {
			if (taEvent.eventType.equals(TAEventType.ENTRY_SIGNAL) && taEvent.eventName.equals("Flat")
				&& ((order.isLong() && !taEvent.isLong) || (!order.isLong() && taEvent.isLong))) {
				double[][] 
					mas = taValues.get(FlexTASource.MAs).getDa2DimValue(),
					smis = taValues.get(FlexTASource.SMI).getDa2DimValue();	
				double 
					prevSlow = smis[1][1], 
					currSlow = smis[1][2], 
					currFast = smis[0][2]; 		
				double
					atr = taValues.get(FlexTASource.ATR).getDoubleValue(),
					ma20 = mas[1][0], 
					ma50 = mas[1][1],
					ma100 = mas[1][2];				
 
				boolean 
					continueLong = order.getProfitLossInPips() < 2.5 * atr * Math.pow(10, instrument.getPipScale())
									&& ((currSlow > prevSlow && currFast > currSlow && bidBar.getClose() > ma20)
										|| (bidBar.getClose() > ma20 && bidBar.getClose() > ma50 && bidBar.getClose() > ma100)
										|| bBandsSqueeze < 30),
					continueShort = order.getProfitLossInPips() < 2.5 * atr * Math.pow(10, instrument.getPipScale())
									&& ((currSlow < prevSlow && currFast < currSlow && bidBar.getClose() < ma20)
										|| (bidBar.getClose() < ma20 && bidBar.getClose() < ma50 && bidBar.getClose() < ma100)
										|| bBandsSqueeze < 30);
				if ((order.isLong() && !continueLong) || (!order.isLong() && !continueShort)) {
					lastTradingEvent = "SMI move SL due to opposite flat signal";
					FXUtils.setStopLoss(order, order.isLong() ? bidBar.getLow() : askBar.getHigh(), bidBar.getTime(), getClass());
				}
			}
			else if (taEvent.eventType.equals(TAEventType.ENTRY_SIGNAL) && taEvent.eventName.equals("PUPB")
					&& ((order.isLong() && !taEvent.isLong) || (!order.isLong() && taEvent.isLong))) {
					lastTradingEvent = "SMI close due to opposite PUPB signal";
					order.close();
					order.waitForUpdate(null);
			}
		}
	}

	@Override
	public TAEventDesc checkEntry(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, Map<String, FlexTAValue> taValues) throws JFException {
		double[][] 
			mas = taValues.get(FlexTASource.MAs).getDa2DimValue(),
			smis = taValues.get(FlexTASource.SMI).getDa2DimValue();	
		double 
			prevSlow = smis[1][1], 
			currSlow = smis[1][2], 
			prevFast = smis[0][1], 
			currFast = smis[0][2]; 		
		double
			ma20 = mas[1][0], 
			ma50 = mas[1][1],
			ma100 = mas[1][2],
			ma200 = mas[1][3],
			channelPos = taValues.get(FlexTASource.CHANNEL_POS).getDoubleValue(),
			bBandsSqueezePerc = taValues.get(FlexTASource.BBANDS_SQUEEZE_PERC).getDoubleValue();
		FLAT_REGIME_CAUSE isFlat = (FLAT_REGIME_CAUSE)taValues.get(FlexTASource.FLAT_REGIME).getValue();
	
		if (buySignal(instrument, period, prevSlow, currSlow, prevFast, currFast, ma20, ma50, ma100, ma200, bidBar, channelPos, isFlat, bBandsSqueezePerc)) {
			double stopLossOffset = FXUtils.roundToPip(2 * taValues.get(FlexTASource.ATR).getDoubleValue() / Math.pow(10, instrument.getPipValue()), instrument);
			lastSL = bidBar.getClose() - stopLossOffset;			
			TAEventDesc result = new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, true, askBar, bidBar, period);
			//lastTradingEvent = "buy signal";			
			return result;
		} else if (sellSignal(instrument, period, prevSlow, currSlow, prevFast, currFast, ma20, ma50, ma100, ma200, askBar, channelPos, isFlat, bBandsSqueezePerc)) {
			double stopLossOffset = FXUtils.roundToPip(2 * taValues.get(FlexTASource.ATR).getDoubleValue() / Math.pow(10, instrument.getPipValue()), instrument);
			lastSL = askBar.getClose() + stopLossOffset;
			TAEventDesc result = new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, false, askBar, bidBar, period);
			//lastTradingEvent = "sell signal";			
			return result;
		}
		return null;
	}

	@Override
	public EntryDirection checkExit(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter, IOrder order, Map<String, FlexTAValue> taValues) throws JFException {
		double[][] 
			mas = taValues.get(FlexTASource.MAs).getDa2DimValue(),
			smis = taValues.get(FlexTASource.SMI).getDa2DimValue();	
		double 
			currSlow = smis[1][2], 
			prevFast = smis[0][1], 
			currFast = smis[0][2], 		
			ma20 = mas[1][0];
		if (exitLong(currSlow, prevFast, currFast, ma20, instrument, period, filter, bidBar)) {
			//lastTradingEvent = "long exit signal";
			return ITradeSetup.EntryDirection.LONG;
		}
		else if (exitShort(currSlow, prevFast, currFast, ma20, instrument, period, filter, askBar)) {
			//lastTradingEvent = "short exit signal";
			return ITradeSetup.EntryDirection.SHORT;
		}
		return ITradeSetup.EntryDirection.NONE;
	}

	boolean buySignal(Instrument instrument, Period pPeriod, double	prevSlow, double currSlow, double prevFast, double currFast, 
			double ma20, double ma50, double ma100, double ma200, IBar bidBar, double channelPos, FLAT_REGIME_CAUSE isFlat, double bBandsSqueezePerc) throws JFException {

		boolean 
			strongUpTrend = ma200 < ma100 && ma200 < ma50 && ma200 < ma20 // ma200 lowest
							&& ma50 > ma100 && ma20 > ma100, // TrendID 5 or 6
			strongDownTrend = ma200 > ma100 && ma200 > ma50 && ma200 > ma20 // ma200 highest
							  && ma50 < ma100 && ma20 < ma100; // TrendID 1 or 2
		// filter out first
		if (!(strongUpTrend || strongDownTrend) 
			&& (!isFlat.equals(FLAT_REGIME_CAUSE.NONE) || bBandsSqueezePerc < bBandsSqueezeThreshold) && channelPos > 55) // no entries above ma20 in flat regime !
			return false;

		if ((strongDownTrend && bidBar.getClose() < ma50) // too strong a downtrend, price below middle MA, don't try it !
			|| (currFast < -60.0 && currSlow < -60.0) // no long entries in heavily oversold !
			|| (channelPos > 55.0 && !strongUpTrend && !strongDownTrend)) // not a favorable entry at channel extreme, allowed only for bullish reversal or bullish breakout / continuation !
			return false;
		
		return ((currFast < 60.0 || channelPos < 95.0) && currFast > currSlow)
				&& prevSlow < currSlow
				&& prevFast < currFast;
	}

	boolean sellSignal(Instrument instrument, Period pPeriod, double	prevSlow, double currSlow, double prevFast, double currFast, 
			double ma20, double ma50, double ma100, double ma200, IBar bidBar, double channelPos, FLAT_REGIME_CAUSE isFlat, double bBandsSqueezePerc) throws JFException {

		boolean 
		strongUpTrend = ma200 < ma100 && ma200 < ma50 && ma200 < ma20 // ma200 lowest
						&& ma50 > ma100 && ma20 > ma100, // TrendID 5 or 6
		strongDownTrend = ma200 > ma100 && ma200 > ma50 && ma200 > ma20 // ma200 highest
						  && ma50 < ma100 && ma20 < ma100; // TrendID 1 or 2

		// filter out first
		if (!(strongUpTrend || strongDownTrend)
			&& (!isFlat.equals(FLAT_REGIME_CAUSE.NONE) || bBandsSqueezePerc < bBandsSqueezeThreshold) && channelPos < 45) // no entries below ma20 in flat regime !
			return false;

		if ((strongUpTrend && bidBar.getClose() > ma50) // too strong an uptrend, price above middle MA, don't try it !
			|| (currFast > 60 && currSlow > 60) // no short entries in heavily overbought !
			|| (channelPos < 45 && !strongUpTrend && !strongDownTrend)) // not a favorable entry at channel extreme, allowed only for bullish reversal or bullish breakout / continuation ! 
			return false;

		return ((currFast > -60.0 || channelPos > 5.0) && currFast < currSlow)
				&& prevSlow > currSlow
				&& prevFast > currFast;
	}

	boolean exitLong(double	currSlow, double prevFast, double currFast, double ma20, Instrument instrument, Period period, Filter filter, IBar currBar)	throws JFException {
		return !(currFast > 60.0 && currSlow > 60.0) && // no need to exit long while heavily overbought !
				((currFast < currSlow && currFast < prevFast  && currBar.getClose() < ma20) // probably no need to exit if slow overbought and still raising !!
				|| (currFast < -60.0 && currSlow < -60.0) // also exit if both lines heavily overSOLD, clearly strong downtrend !
				|| (currSlow < -60.0 && currFast < prevFast)); // also if slow oversold (even if raising !) and fast starts falling
	}

	boolean exitShort(double currSlow, double prevFast, double currFast, double ma20, Instrument instrument, Period period, Filter filter, IBar currBar) throws JFException {
		return !(currFast < -60.0 && currSlow < -60.0) && // no need to exit short while heavily oversold !
				((currFast > currSlow && currFast > prevFast && currBar.getClose() > ma20)
				|| (currFast > 60.0 && currSlow > 60.0)
				|| (currSlow > 60.0 && currFast > prevFast));
	}

	boolean buySignalWeak(double prevSlow, double currSlow, double prevFast, double currFast, double ma20, double ma50, double ma100, double ma200, IBar bidBar) {
		// too strong a downtrend, price below middle MA, don't try it !
		if (ma20 < ma50 && ma50 < ma100 && bidBar.getClose() < ma50)
			return false;

		return !(currFast < -60.0 && currSlow < -60.0) // no long entries in heavily oversold !
				&& currSlow > prevSlow && (currFast > prevFast || currFast > currSlow);
	}

	boolean sellSignalWeak(double prevSlow, double currSlow, double prevFast, double currFast, double ma20,	double ma50, double ma100, double ma200, IBar bidBar) {
		// too strong an uptrend, price above middle MA, don't try it !
		if (ma20 > ma50 && ma50 > ma100 && bidBar.getClose() > ma50)
			return false;

		return !(currFast > 60.0 && currSlow > 60.0) // no short entries in heavily overbought !
				&& currSlow < prevSlow && (currFast < prevFast || currFast < currSlow);
	}

	@Override
	public EntryDirection checkCancel(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, Map<String, FlexTAValue> taValues) throws JFException {
		ITradeSetup.EntryDirection result = ITradeSetup.EntryDirection.NONE;
		double[][] 
				mas = taValues.get(FlexTASource.MAs).getDa2DimValue(),
				smis = taValues.get(FlexTASource.SMI).getDa2DimValue();	
			double 
				prevSlow = smis[1][1], 
				currSlow = smis[1][2], 
				prevFast = smis[0][1], 
				currFast = smis[0][2]; 		
			double
				ma20 = mas[1][0], 
				ma50 = mas[1][1],
				ma100 = mas[1][2],
				ma200 = mas[1][3];

		if (!buySignalWeak(prevSlow, currSlow, prevFast, currFast, ma20, ma50, ma100, ma200, bidBar))
			return ITradeSetup.EntryDirection.LONG;
		else if (!sellSignalWeak(prevSlow, currSlow, prevFast, currFast, ma20, ma50, ma100, ma200, askBar))
			return ITradeSetup.EntryDirection.SHORT;

		return result;
	}

	@Override
	public IOrder submitOrder(String label, Instrument instrument, boolean isLong, double amount, IBar bidBar, IBar askBar)	throws JFException {
		if (this.mktEntry)
			return submitMktOrder(label, instrument, isLong, amount, bidBar, askBar, lastSL);
		else
			return submitStpOrder(label, instrument, isLong, amount, bidBar, askBar, lastSL);
	}

}
