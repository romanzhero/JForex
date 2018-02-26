package jforex.trades;

import java.util.List;
import java.util.Map;

import jforex.events.TAEventDesc;
import jforex.events.TAEventDesc.TAEventType;
import jforex.techanalysis.TradeTrigger.TriggerDesc;
import jforex.techanalysis.source.FlexTASource;
import jforex.utils.FXUtils;
import jforex.utils.StopLoss;
import jforex.utils.log.FlexLogEntry;
import jforex.utils.stats.RangesStats.InstrumentRangeStats;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

public abstract class TradeSetup implements ITradeSetup {
	protected IEngine engine = null;
	protected IContext context = null;
	protected IOrder order = null;
	protected Map<Instrument, InstrumentRangeStats> dayRanges = null;

	protected boolean 
		locked = false,
		takeOverOnly = false,
		useEntryFilters = false; // shall numerical filters be checked before logical entry criteria
	protected String lastTradingEvent = "none";

	public TradeSetup(boolean pUseEntryFilters, IEngine engine, IContext context) {
		super();
		this.engine = engine;
		this.context = context;
		this.useEntryFilters = pUseEntryFilters;
	}
	
	public TradeSetup(IEngine engine, IContext context, boolean pTakeOverOnly) {
		super();
		this.engine = engine;
		this.context = context;
		this.takeOverOnly = pTakeOverOnly;
	}

	@Override
	public boolean isTakeOverOnly() {
		return takeOverOnly;
	}


	@Override
	public EntryDirection checkCancel(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, Map<String, FlexLogEntry> taValues) throws JFException {
		return ITradeSetup.EntryDirection.NONE;
	}

	@Override
	public void inTradeProcessing(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, IOrder order, Map<String, FlexLogEntry> taValues, List<TAEventDesc> marketEvents) throws JFException {
	}

	@Override
	public void log(IMessage message) {
	}

	@Override
	public void takeTradingOver(IOrder order) {
		this.order = order;
	}

	@Override
	public void afterTradeReset(Instrument instrument) {
		lastTradingEvent = "none";
	}

	@Override
	public EntryDirection checkExit(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter, IOrder order, Map<String, FlexLogEntry> taValues) throws JFException {
		return ITradeSetup.EntryDirection.NONE;
	}

	@Override
	public EntryDirection checkTakeOver(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter, Map<String, FlexLogEntry> taValues) throws JFException {
		return ITradeSetup.EntryDirection.NONE;
	}

	@Override
	public boolean isTradeLocked(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter, IOrder order, Map<String, FlexLogEntry> taValues) throws JFException {
		return locked;
	}
	
	@Override
	public void updateOnBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) {	}

	public IOrder submitMktOrder(String label, Instrument instrument, boolean isLong, double amount, IBar bidBar, IBar askBar) throws JFException {
		IOrder order = engine.submitOrder(label, instrument, isLong ? IEngine.OrderCommand.BUY : IEngine.OrderCommand.SELL,	amount);
		//order.waitForUpdate(IOrder.State.FILLED);
		return order;
	}
	
	public IOrder submitMktOrder(String label, Instrument instrument, boolean isLong, double amount, IBar bidBar, IBar askBar, double stopLoss) throws JFException {
		IOrder order = null;
		if (stopLoss == -1.0) {
			order = submitMktOrder(label, instrument, isLong, amount, bidBar, askBar);			
		} else {
			double stopLossPrice = stopLoss;
			stopLossPrice = FXUtils.roundToPip(stopLoss, instrument);
			order = engine.submitOrder(label, instrument, isLong ? IEngine.OrderCommand.BUY : IEngine.OrderCommand.SELL, amount, 0, -1, stopLossPrice, 0);
		}
		//order.waitForUpdate(IOrder.State.FILLED);
		return order;
	}	
	public IOrder submitStpOrder(String label, Instrument instrument, boolean isLong, double amount, IBar bidBar, IBar askBar, double stopLoss) throws JFException {
		double 
			stpPrice = isLong ? askBar.getHigh() : bidBar.getLow(),
			stopLossPrice = stopLoss;
		stpPrice = FXUtils.roundToPip(stpPrice, instrument);
		stopLossPrice = FXUtils.roundToPip(stopLossPrice, instrument);
		IOrder order = engine.submitOrder(label, instrument, isLong ? IEngine.OrderCommand.BUYSTOP: IEngine.OrderCommand.SELLSTOP, amount,	stpPrice, Double.NaN, stopLossPrice, 0);
		//order.waitForUpdate(IOrder.State.OPENED, IOrder.State.FILLED);
		return order;
	}

	@Override
	public String getLastTradingEvent() {
		return lastTradingEvent ;
	}

	protected void shortSetBreakEvenOnProfit(Instrument instrument, IBar bidBar, IOrder order, Map<String, FlexLogEntry> taValues, double howManyATRs) {
				// move SL to break even after position is profitable for more then 2.5 ATRs 
				if (order.getProfitLossInPips() > howManyATRs * taValues.get(FlexTASource.ATR).getDoubleValue() * Math.pow(10, instrument.getPipScale()) 
					&& order.getOpenPrice() < order.getStopLossPrice()) {
					lastTradingEvent = "(Short) Moved SL to breakeven (profit = " + FXUtils.df1.format(howManyATRs) + " ATR)";
					StopLoss.setStopLoss(order, order.getOpenPrice(), bidBar.getTime(), getClass());
				}
			}

	protected void shortProtectProfitOnATRCount(Instrument instrument, IBar bidBar, IOrder order, Map<String, FlexLogEntry> taValues, double howManyATRs) {
		// move SL to break even after position is profitable for more then 2.5 ATRs 
		if (order.getProfitLossInPips() > howManyATRs * taValues.get(FlexTASource.ATR).getDoubleValue() * Math.pow(10, instrument.getPipScale()) 
			&& bidBar.getHigh() < order.getStopLossPrice()) {
			lastTradingEvent = "(Short) Moved SL to protect profit = " + FXUtils.df1.format(howManyATRs) + " ATR";
			StopLoss.setStopLoss(order, bidBar.getHigh(), bidBar.getTime(), getClass());
		}
	}
	
	protected void longSetBreakEvenOnProfit(Instrument instrument, IBar bidBar,	IOrder order, Map<String, FlexLogEntry> taValues, double howManyATRs) {
				// move SL to break even after position is profitable for more then 2.5 ATRs 
				if (order.getProfitLossInPips() > howManyATRs * taValues.get(FlexTASource.ATR).getDoubleValue() * Math.pow(10, instrument.getPipScale()) 
					&& order.getOpenPrice() > order.getStopLossPrice()) {
					lastTradingEvent = "(Long) Moved SL to breakeven (profit = " + FXUtils.df1.format(howManyATRs) + " ATR)";
					StopLoss.setStopLoss(order, order.getOpenPrice(), bidBar.getTime(), getClass());
				}
			}

	protected void longProtectProfitOnATRCount(Instrument instrument, IBar bidBar,	IOrder order, Map<String, FlexLogEntry> taValues, double howManyATRs) {
		if (order.getProfitLossInPips() > howManyATRs * taValues.get(FlexTASource.ATR).getDoubleValue() * Math.pow(10, instrument.getPipScale()) 
			&& bidBar.getLow() > order.getStopLossPrice()) {
			lastTradingEvent = "(Long) Moved SL to protect profit = " + FXUtils.df1.format(howManyATRs) + " ATR";
			StopLoss.setStopLoss(order, bidBar.getLow(), bidBar.getTime(), getClass());
		}
	}
	protected void longMoveSLDueToShortFlatSignal(IBar bidBar, IOrder order, List<TAEventDesc> marketEvents) {
		for (TAEventDesc taEvent : marketEvents) {
			if (taEvent.eventType.equals(TAEventType.ENTRY_SIGNAL) && taEvent.eventName.equals("Flat") && !taEvent.isLong) {
				double newSL = bidBar.getClose() < order.getOpenPrice() ? bidBar.getLow() : order.getOpenPrice();
				if (newSL > order.getStopLossPrice()) {
					lastTradingEvent = "(Long) Moved SL due to bearish flat signal";
					StopLoss.setStopLoss(order, newSL, bidBar.getTime(), getClass());
				}
			}
		}
	}

	protected void shortMoveSLDueToLongFlatSignal(IBar bidBar, IOrder order, List<TAEventDesc> marketEvents) {
		for (TAEventDesc taEvent : marketEvents) {
			if (taEvent.eventType.equals(TAEventType.ENTRY_SIGNAL) && taEvent.eventName.equals("Flat") && taEvent.isLong) {
				double newSL = bidBar.getClose() > order.getOpenPrice() ? bidBar.getHigh() : order.getOpenPrice();
				if (newSL < order.getStopLossPrice()) {
					lastTradingEvent = "(Short) Moved SL due to bullish flat signal";
					StopLoss.setStopLoss(order, newSL, bidBar.getTime(), getClass());
				}
			}
		}
	}
	
	protected void longMoveSLDueToShortFlatStrongSignal(IBar bidBar, IOrder order, List<TAEventDesc> marketEvents) {
		for (TAEventDesc taEvent : marketEvents) {
			if (taEvent.eventType.equals(TAEventType.ENTRY_SIGNAL) && taEvent.eventName.equals("FlatStrong") && !taEvent.isLong) {
				if (bidBar.getLow() > order.getStopLossPrice()) {
					lastTradingEvent = "(Long) Moved SL due to bearish flat signal";
					StopLoss.setStopLoss(order, bidBar.getLow(), bidBar.getTime(), getClass());
				}
			}
		}
	}

	protected void shortMoveSLDueToLongFlatStrongSignal(IBar bidBar, IOrder order, List<TAEventDesc> marketEvents) {
		for (TAEventDesc taEvent : marketEvents) {
			if (taEvent.eventType.equals(TAEventType.ENTRY_SIGNAL) && taEvent.eventName.equals("FlatStrong") && taEvent.isLong) {
				if (bidBar.getHigh() < order.getStopLossPrice()) {
					lastTradingEvent = "(Short) Moved SL due to bullish flat signal";
					StopLoss.setStopLoss(order, bidBar.getHigh(), bidBar.getTime(), getClass());
				}
			}
		}
	}
	
	protected boolean shortFlatStrongSignal(IBar bidBar, IOrder order, List<TAEventDesc> marketEvents) {
		for (TAEventDesc taEvent : marketEvents) {
			if (taEvent.eventType.equals(TAEventType.ENTRY_SIGNAL) && taEvent.eventName.equals("FlatStrong") && !taEvent.isLong) 
				return true;			
		}
		return false;
	}

	protected boolean longFlatStrongSignal(IBar bidBar, IOrder order, List<TAEventDesc> marketEvents) {
		for (TAEventDesc taEvent : marketEvents) {
			if (taEvent.eventType.equals(TAEventType.ENTRY_SIGNAL) && taEvent.eventName.equals("FlatStrong") && taEvent.isLong) 
				return true;
		}
		return false;
	}	

	protected void shortMoveSLDueToBigBullishCandle(IBar bidBar,
			IOrder order, Map<String, FlexLogEntry> taValues) {
				TriggerDesc candles = taValues.get(FlexTASource.BULLISH_CANDLES).getCandleValue();
				if (candles != null && candles.sizePercentile > 70 && candles.reversalSize > 70) {
					double newSL = bidBar.getClose() > order.getOpenPrice() ? bidBar.getHigh() : order.getOpenPrice();
					if (newSL < order.getStopLossPrice()) {
						lastTradingEvent = "(Short) Moved SL due to big bullish candle (size perc: " 
									+ FXUtils.df1.format(candles.sizePercentile) + ", reversal size: " + FXUtils.df1.format(candles.reversalSize) + "%)";
						StopLoss.setStopLoss(order, newSL, bidBar.getTime(), getClass());
					}
				}
			}

	protected void longMoveSLDueToBigBearishCandle(IBar bidBar, IOrder order, Map<String, FlexLogEntry> taValues) {
		TriggerDesc candles = taValues.get(FlexTASource.BEARISH_CANDLES).getCandleValue();
		if (candles != null && candles.sizePercentile > 70 && candles.reversalSize > 70) {
				double newSL = bidBar.getClose() < order.getOpenPrice() ? bidBar.getLow() : order.getOpenPrice();
				if (newSL > order.getStopLossPrice()) {
					lastTradingEvent = "(Long) Moved SL due to big bearish candle (size perc: " 
							+ FXUtils.df1.format(candles.sizePercentile) + ", reversal size: " + FXUtils.df1.format(candles.reversalSize) + "%)";
					StopLoss.setStopLoss(order, newSL, bidBar.getTime(), getClass());
				}
		}
	}
	
	protected void longProtectProfitDueToBigBearishCandle(IBar bidBar, IOrder order, double howManyATRs, Map<String, FlexLogEntry> taValues) {
		TriggerDesc candles = taValues.get(FlexTASource.BEARISH_CANDLES).getCandleValue();
		if (candles != null && candles.sizePercentile > 70 && candles.reversalSize > 70
			&& order.getProfitLossInPips() > howManyATRs * taValues.get(FlexTASource.ATR).getDoubleValue() * Math.pow(10, order.getInstrument().getPipScale())) {
			double newSL = bidBar.getLow();
			if (newSL > order.getStopLossPrice()) {
				lastTradingEvent = "(Long) Protect profit (" + FXUtils.df1.format(howManyATRs) + ") due to big bearish candle (size perc: " 
						+ FXUtils.df1.format(candles.sizePercentile + ", reversal size: " + FXUtils.df1.format(candles.reversalSize) + "%)") ;
				StopLoss.setStopLoss(order, newSL, bidBar.getTime(), getClass());
			}
		}
	}
	
	protected void shortProtectProfitDueToBigBullishCandle(IBar bidBar, IOrder order, double howManyATRs, Map<String, FlexLogEntry> taValues) {
		TriggerDesc candles = taValues.get(FlexTASource.BULLISH_CANDLES).getCandleValue();
		if (candles != null && candles.sizePercentile > 70 && candles.reversalSize > 70
			&& order.getProfitLossInPips() > howManyATRs * taValues.get(FlexTASource.ATR).getDoubleValue() * Math.pow(10, order.getInstrument().getPipScale())) {
			double newSL = bidBar.getHigh();
			if (newSL < order.getStopLossPrice()) {
				lastTradingEvent = "(Short) Protect profit (" + FXUtils.df1.format(howManyATRs) + ") due to big bullish candle (size perc: " 
						+ FXUtils.df1.format(candles.sizePercentile) + ", reversal size: " + FXUtils.df1.format(candles.reversalSize) + "%)" ;
				StopLoss.setStopLoss(order, newSL, bidBar.getTime(), getClass());
			}
		}
	}


	protected void longMoveSLDueToExtremeRSI3(IBar bidBar, IOrder order, Map<String, FlexLogEntry> taValues) {
				double RSI3 = taValues.get(FlexTASource.RSI3).getDoubleValue();
				if (RSI3 < 30) {
						double newSL = bidBar.getClose() < order.getOpenPrice() ? bidBar.getLow() : order.getOpenPrice();
						if (newSL > order.getStopLossPrice()) {
							lastTradingEvent = "(Long) Moved SL due to extreme RSI3 (" + FXUtils.df1.format(RSI3) + ")";
							StopLoss.setStopLoss(order, newSL, bidBar.getTime(), getClass());
						}
				}
			}
	
	protected void shortMoveSLDueToExtremeRSI3(IBar bidBar, IOrder order, Map<String, FlexLogEntry> taValues) {
		double RSI3 = taValues.get(FlexTASource.RSI3).getDoubleValue();
		if (RSI3 > 70) {
				double newSL = bidBar.getClose() > order.getOpenPrice() ? bidBar.getHigh() : order.getOpenPrice();
				if (newSL < order.getStopLossPrice()) {
					lastTradingEvent = "(Short) Moved SL due to extreme RSI3 (" + FXUtils.df1.format(RSI3) + ")";
					StopLoss.setStopLoss(order, newSL, bidBar.getTime(), getClass());
				}
		}
	}

	@Override
	public void setLastTradingEvent(String lastTradingEvent) {
		this.lastTradingEvent = lastTradingEvent;		
	}

	@Override
	public void addDayRanges(Map<Instrument, InstrumentRangeStats> dayRanges) {
		this.dayRanges = dayRanges;		
	}

	protected boolean maxProfitExceededAvgDayRange(List<TAEventDesc> marketEvents) {
		for (TAEventDesc currEvent : marketEvents) {
			if (currEvent.eventType.equals(TAEventDesc.TAEventType.MAX_TRADE_PROFIT_IN_PERC)) {
				return currEvent.tradeProfitInPerc > currEvent.avgPnLRange;
			}
		}
		return false;
	}
	
	protected double ratioMaxProfitToAvgDayRange(List<TAEventDesc> marketEvents) {
		for (TAEventDesc currEvent : marketEvents) {
			if (currEvent.eventType.equals(TAEventDesc.TAEventType.MAX_TRADE_PROFIT_IN_PERC)) {
				return currEvent.tradeProfitInPerc / currEvent.avgPnLRange;
			}
		}
		return 0.0;
	}
	
	protected TAEventDesc findTAEvent(List<TAEventDesc> taEvents, TAEventDesc.TAEventType eventType, String eventName, Instrument instrument, Period timeFrame) {
		for (TAEventDesc currEvent : taEvents) {
			if (currEvent.eventType.equals(eventType)
				&& currEvent.eventName.equals(eventName)
				&& currEvent.instrument.equals(instrument)
				&& currEvent.timeFrame.equals(timeFrame))
				return currEvent;
		}
		return null;
	}

	protected boolean extremeUpTrend(Map<String, FlexLogEntry> taValues) {
		double ma200ma100Distance = taValues.get(FlexTASource.MA200MA100_TREND_DISTANCE_PERC).getDoubleValue();
		boolean ma200Lowest = taValues.get(FlexTASource.MA200_LOWEST).getBooleanValue();
		return ma200Lowest && ma200ma100Distance > 80.0;
	}

	protected boolean extremeDownTrend(Map<String, FlexLogEntry> taValues) {
		double ma200ma100Distance = taValues.get(FlexTASource.MA200MA100_TREND_DISTANCE_PERC).getDoubleValue();
		boolean ma200Highest = taValues.get(FlexTASource.MA200_HIGHEST).getBooleanValue();
		return ma200Highest && ma200ma100Distance > 80.0;
	}

	protected double getLowestMAExceptMA200(double[][] mas) {
		double
			ma20 = mas[1][0],
			ma50 = mas[1][1],
			ma100 = mas[1][2];
		if (ma20 < ma50 && ma50 < ma100)
			return ma20;
		else if (ma20 >= ma50 && ma50 < ma100)
			return ma50;
		return ma100;
	}

	protected double getHighestMAExceptMA200(double[][] mas) {
		double
			ma20 = mas[1][0],
			ma50 = mas[1][1],
			ma100 = mas[1][2];
		if (ma20 > ma50 && ma50 > ma100)
			return ma20;
		else if (ma20 <= ma50 && ma50 > ma100)
			return ma50;
		return ma100;
	}
}
