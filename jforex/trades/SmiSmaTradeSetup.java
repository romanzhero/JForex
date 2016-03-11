package jforex.trades;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jforex.events.TAEventDesc;
import jforex.events.TAEventDesc.TAEventType;
import jforex.techanalysis.source.FlexTASource;
import jforex.techanalysis.source.FlexTAValue;
import jforex.utils.FXUtils;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IConsole;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

public class SmiSmaTradeSetup extends TradeSetup {
	protected IConsole console = null;

	protected boolean mktEntry = true;
	protected Map<String, Boolean> ma50TrailFlags = new HashMap<String, Boolean>();

	public SmiSmaTradeSetup(IEngine engine, IConsole console, Set<Instrument> subscribedInstruments, boolean mktEntry) {
		super(engine);
		this.console = console;
		this.mktEntry = mktEntry;

		for (Instrument i : subscribedInstruments) {
			ma50TrailFlags.put(i.name(), new Boolean(false));
		}

	}

	@Override
	public String getName() {
		return new String("SmiSmaCombined");
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
			ma200 = mas[1][3];
		if (buySignal(prevSlow, currSlow, prevFast, currFast, ma20, ma50, ma100, ma200, bidBar)) {
			TAEventDesc result = new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, true, askBar, bidBar, period);
			return result;
		} else if (sellSignal(prevSlow, currSlow, prevFast, currFast, ma20, ma50, ma100, ma200, askBar)) {
			TAEventDesc result = new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, false, askBar, bidBar, period);
			return result;
		}
		return null;
	}

	@Override
	public EntryDirection checkExit(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter, IOrder order, Map<String, FlexTAValue> taValues)	throws JFException {
		double[][] 
				smis = taValues.get(FlexTASource.SMI).getDa2DimValue();	
			double 
				currSlow = smis[1][2], 
				prevFast = smis[0][1], 
				currFast = smis[0][2]; 	

		if (exitLong(currSlow, prevFast, currFast))
			return ITradeSetup.EntryDirection.LONG;
		else if (exitShort(currSlow, prevFast, currFast))
			return ITradeSetup.EntryDirection.SHORT;
		return ITradeSetup.EntryDirection.NONE;
	}

	boolean buySignal(double prevSlow, double currSlow, double prevFast, double currFast, 
			double ma20, double ma50, double ma100, double ma200, IBar bidBar) {
		
		// too strong a downtrend, price below middle MA, don't try it !
		if (ma20 < ma50 && ma50 < ma100 && bidBar.getClose() < ma50)
			return false;
		return !(currFast < -60.0 && currSlow < -60.0) // no long entries in
														// heavily oversold !
				&& currFast < 60.0
				&& prevSlow < currSlow
				&& prevFast < currFast;
	}

	boolean sellSignal(double prevSlow, double currSlow, double prevFast, double currFast, 
			double ma20, double ma50, double ma100, double ma200, IBar bidBar) {
		// too strong an uptrend, price above middle MA, don't try it !
		if (ma20 > ma50 && ma50 > ma100 && bidBar.getClose() > ma50)
			return false;

		return !(currFast > 60.0 && currSlow > 60.0) // no short entries in
														// heavily overbought !
				&& currFast > -60.0
				&& prevSlow > currSlow
				&& prevFast > currFast;
	}

	boolean exitLong(double currSlow, double prevFast, double currFast) {		
		return (!(currFast > 60.0 && currSlow > 60.0) // no need to exit long
														// while heavily
														// overbought !
				&& currFast < currSlow && prevFast > currFast) // probably no
																// need to exit
																// if slow
																// overbought
																// and still
																// raising !!
				|| (currFast < -60.0 && currSlow < -60.0) // also exit if both
															// lines heavily
															// overSOLD, clearly
															// strong downtrend
															// !
				|| (currSlow < -60.0 && currFast < prevFast); // also if slow
																// oversold
																// (even if
																// raising !)
																// and fast
																// starts
																// falling
	}

	boolean exitShort(double currSlow, double prevFast, double currFast) {
		return (!(currFast < -60.0 && currSlow < -60.0) // no need to exixt
														// short while heavily
														// oversold !
				&& currFast > currSlow && prevFast < currFast)
				|| (currFast > 60.0 && currSlow > 60.0)
				|| (currSlow > 60.0 && currFast > prevFast);
	}

	@Override
	public void inTradeProcessing(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, IOrder order, Map<String, FlexTAValue> taValues, List<TAEventDesc> marketEvents) throws JFException {
		if (order == null)
			return;

		double[][] mas = taValues.get(FlexTASource.MAs).getDa2DimValue();
		double
			ma20 = mas[1][0], 
			ma50 = mas[1][1],
			ma100 = mas[1][2];
		if (order.isLong())
			startTrailingLong(ma20, ma50, ma100, bidBar, order);
		else
			startTrailingShort(ma20, ma50, ma100, askBar, order);

	}

	// true means there's a strong uptrend and momentum exits are now switched
	// OFF
	boolean startTrailingLong(double ma20, double ma50, double ma100, IBar bidBar, IOrder order) throws JFException {
		if (ma20 > ma50 && ma50 > ma100) {
			Boolean ma50Trailing = ma50TrailFlags.get(order.getInstrument()
					.name());
			if (ma50Trailing.booleanValue() && bidBar.getHigh() > ma20
					&& bidBar.getLow() > ma50) {
				ma50Trailing = new Boolean(false);
				ma50TrailFlags.put(order.getInstrument().name(), new Boolean(
						false));
			}
			// check whether MA50 was WITHIN candle
			if (!ma50Trailing.booleanValue()
					&& bidBar.getLow() < ma50
					&& (order.getStopLossPrice() == 0.0 || bidBar.getLow() > order
							.getStopLossPrice())) {
				// put SL on low of the bar which crossed MA50. No real
				// trailing, do it only once
				ma50TrailFlags.put(order.getInstrument().name(), new Boolean(true));
				FXUtils.setStopLoss(order, FXUtils.roundToPip(bidBar.getLow(), order.getInstrument()), bidBar.getTime(), this.getClass());
			} else if (!ma50Trailing.booleanValue() && bidBar.getHigh() < ma20) {
				// start trailing on MA50
				// additional criteria !!! ONLY if ma50 and ma100 relatively
				// close !! If far apart in a strong trend don't do it, same for
				// piercing MA50 !!!
				// also for ENTRIES !!! Crossing MA50 in WEAK and STRONG trend
				// is not the same !!! See 12.11 - 4.12.15 ! Missed some 160
				// pips profit compared to exit at ma100, plus 350 pips of
				// unnecessary losses !!! Total 500+ pips missed !!!!
				if (order.getStopLossPrice() == 0.0
						|| ma50 > order.getStopLossPrice()) {
					FXUtils.setStopLoss(order, FXUtils.roundToPip(ma50, order.getInstrument()), bidBar.getTime(), this.getClass());
				}
			}
			return true;
		} else
			return false;
	}

	// true means there's a strong downtrend and momentum exits are now switched
	// OFF
	boolean startTrailingShort(double ma20, double ma50, double ma100,
			IBar askBar, IOrder order) throws JFException {
		if (ma20 < ma50 && ma50 < ma100) {
			Boolean ma50Trailing = (Boolean) ma50TrailFlags.get(order
					.getInstrument().name());
			if (ma50Trailing.booleanValue() && askBar.getLow() < ma20
					&& askBar.getHigh() < ma50) {
				ma50Trailing = new Boolean(false);
				ma50TrailFlags.put(order.getInstrument().name(), new Boolean(
						false));
			}
			// check whether MA50 was WITHIN candle
			if (!ma50Trailing.booleanValue()
					&& askBar.getHigh() > ma50
					&& (order.getStopLossPrice() == 0.0 || askBar.getHigh() < order
							.getStopLossPrice())) {
				// put SL on low of the bar which crossed MA50. No real
				// trailing, do it only once
				ma50TrailFlags.put(order.getInstrument().name(), new Boolean(
						true));
				order.setStopLossPrice(FXUtils.roundToPip(askBar.getHigh(),
						order.getInstrument()));
				order.waitForUpdate(null);
			} else if (!ma50Trailing.booleanValue() && askBar.getLow() > ma20) {
				// start trailing on MA50
				if (order.getStopLossPrice() == 0.0
						|| ma50 < order.getStopLossPrice()) {
					order.setStopLossPrice(FXUtils.roundToPip(ma50,
							order.getInstrument()));
					order.waitForUpdate(null);
				}
			}
			return true;
		} else
			return false;
	}

	boolean buySignalWeak(double prevSlow, double currSlow, double prevFast, double currFast, 
			double ma20, double ma50, double ma100, double ma200, IBar bidBar) {
			// too strong a downtrend, price below middle MA, don't try it !
		if (ma20 < ma50 && ma50 < ma100 && bidBar.getClose() < ma50)
			return false;

		return !(currFast < -60.0 && currSlow < -60.0) // no long entries in
														// heavily oversold !
				&& prevSlow < currSlow && prevFast < currFast;
	}

	boolean sellSignalWeak(double prevSlow, double currSlow, double prevFast, double currFast, 
			double ma20, double ma50, double ma100, double ma200, IBar bidBar) {
		// too strong an uptrend, price above middle MA, don't try it !
		if (ma20 > ma50 && ma50 > ma100 && bidBar.getClose() > ma50)
			return false;

		return !(currFast > 60.0 && currSlow > 60.0) // no short entries in
														// heavily overbought !
				&& prevSlow > currSlow && prevFast > currFast;
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
	public void afterTradeReset(Instrument instrument) {
		super.afterTradeReset(instrument);
		ma50TrailFlags.put(instrument.name(), new Boolean(false));
	}
}
