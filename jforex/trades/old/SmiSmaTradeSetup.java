package jforex.trades.old;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jforex.events.TAEventDesc;
import jforex.events.TAEventDesc.TAEventType;
import jforex.utils.FXUtils;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IConsole;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public class SmiSmaTradeSetup extends TradeSetup {
	protected IConsole console = null;

	protected boolean mktEntry = true;
	protected Map<String, Boolean> ma50TrailFlags = new HashMap<String, Boolean>();

	public SmiSmaTradeSetup(IIndicators indicators, IHistory history,
			IEngine engine, IConsole console,
			Set<Instrument> subscribedInstruments, boolean mktEntry) {
		super(indicators, history, engine);
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
	public TAEventDesc checkEntry(Instrument instrument, Period period,
			IBar askBar, IBar bidBar, Filter filter) throws JFException {
		double[][] slowSMI = indicators.smi(instrument, period, OfferSide.BID,
				50, 15, 5, 3, filter, 2, bidBar.getTime(), 0), fastSMI = indicators
				.smi(instrument, period, OfferSide.BID, 10, 3, 5, 3, filter, 2,
						bidBar.getTime(), 0);
		double ma20 = indicators.sma(instrument, period, OfferSide.BID,
				IIndicators.AppliedPrice.CLOSE, 20, filter, 1,
				bidBar.getTime(), 0)[0], ma50 = indicators.sma(instrument,
				period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 50,
				filter, 1, bidBar.getTime(), 0)[0], ma100 = indicators.sma(
				instrument, period, OfferSide.BID,
				IIndicators.AppliedPrice.CLOSE, 100, filter, 1,
				bidBar.getTime(), 0)[0], ma200 = indicators.sma(instrument,
				period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 200,
				filter, 1, bidBar.getTime(), 0)[0];

		if (buySignal(slowSMI, fastSMI, ma20, ma50, ma100, ma200, bidBar)) {
			TAEventDesc result = new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, true, askBar, bidBar, period);
			return result;
		} else if (sellSignal(slowSMI, fastSMI, ma20, ma50, ma100, ma200,
				askBar)) {
			// console.getOut().println("Short signal at " +
			// FXUtils.getFormatedBarTime(bidBar)
			// + "; currFast: " + FXUtils.df1.format(fastSMI[0][1])
			// + "; prevFast: " + FXUtils.df1.format(fastSMI[0][0])
			// + "; currSlow: " + FXUtils.df1.format(slowSMI[0][1])
			// + "; prevSlow: " + FXUtils.df1.format(slowSMI[0][0]));
			TAEventDesc result = new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, false, askBar, bidBar, period);
			return result;
		}
		return null;
	}

	@Override
	public EntryDirection checkExit(Instrument instrument, Period period,
			IBar askBar, IBar bidBar, Filter filter, IOrder order)
			throws JFException {
		double[][] slowSMI = indicators.smi(instrument, period, OfferSide.BID,
				50, 15, 5, 3, filter, 2, bidBar.getTime(), 0), fastSMI = indicators
				.smi(instrument, period, OfferSide.BID, 10, 3, 5, 3, filter, 2,
						bidBar.getTime(), 0);

		if (exitLong(slowSMI, fastSMI))
			return ITradeSetup.EntryDirection.LONG;
		else if (exitShort(slowSMI, fastSMI))
			return ITradeSetup.EntryDirection.SHORT;
		return ITradeSetup.EntryDirection.NONE;
	}

	boolean buySignal(double[][] slowSMI, double[][] fastSMI, double ma20,
			double ma50, double ma100, double ma200, IBar bidBar) {
		double prevSlow = slowSMI[0][0], currSlow = slowSMI[0][1], prevFast = fastSMI[0][0], currFast = fastSMI[0][1];

		// too strong a downtrend, price below middle MA, don't try it !
		if (ma20 < ma50 && ma50 < ma100 && bidBar.getClose() < ma50)
			return false;
		return !(currFast < -60.0 && currSlow < -60.0) // no long entries in
														// heavily oversold !
				&& currFast < 60.0
				&& prevSlow < currSlow
				&& prevFast < currFast;
	}

	boolean sellSignal(double[][] slowSMI, double[][] fastSMI, double ma20,
			double ma50, double ma100, double ma200, IBar bidBar) {
		double prevSlow = slowSMI[0][0], currSlow = slowSMI[0][1], prevFast = fastSMI[0][0], currFast = fastSMI[0][1];

		// too strong an uptrend, price above middle MA, don't try it !
		if (ma20 > ma50 && ma50 > ma100 && bidBar.getClose() > ma50)
			return false;

		return !(currFast > 60.0 && currSlow > 60.0) // no short entries in
														// heavily overbought !
				&& currFast > -60.0
				&& prevSlow > currSlow
				&& prevFast > currFast;
	}

	boolean exitLong(double[][] slowSMI, double[][] fastSMI) {
		double currSlow = slowSMI[0][1], prevFast = fastSMI[0][0], currFast = fastSMI[0][1];

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

	boolean exitShort(double[][] slowSMI, double[][] fastSMI) {
		double currSlow = slowSMI[0][1], prevFast = fastSMI[0][0], currFast = fastSMI[0][1];

		return (!(currFast < -60.0 && currSlow < -60.0) // no need to exixt
														// short while heavily
														// oversold !
				&& currFast > currSlow && prevFast < currFast)
				|| (currFast > 60.0 && currSlow > 60.0)
				|| (currSlow > 60.0 && currFast > prevFast);
	}

	@Override
	public void inTradeProcessing(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, IOrder order, List<TAEventDesc> marketEvents) throws JFException {
		if (order == null)
			return;

		double ma20 = indicators.sma(instrument, period, OfferSide.BID,
				IIndicators.AppliedPrice.CLOSE, 20, filter, 1,
				bidBar.getTime(), 0)[0], ma50 = indicators.sma(instrument,
				period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 50,
				filter, 1, bidBar.getTime(), 0)[0], ma100 = indicators.sma(
				instrument, period, OfferSide.BID,
				IIndicators.AppliedPrice.CLOSE, 100, filter, 1,
				bidBar.getTime(), 0)[0];

		if (order.isLong())
			startTrailingLong(ma20, ma50, ma100, bidBar, order);
		else
			startTrailingShort(ma20, ma50, ma100, askBar, order);

	}

	// true means there's a strong uptrend and momentum exits are now switched
	// OFF
	boolean startTrailingLong(double ma20, double ma50, double ma100,
			IBar bidBar, IOrder order) throws JFException {
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

	boolean buySignalWeak(double[][] slowSMI, double[][] fastSMI, double ma20,
			double ma50, double ma100, double ma200, IBar bidBar) {
		double prevSlow = slowSMI[0][0], currSlow = slowSMI[0][1], prevFast = fastSMI[0][0], currFast = fastSMI[0][1];

		// too strong a downtrend, price below middle MA, don't try it !
		if (ma20 < ma50 && ma50 < ma100 && bidBar.getClose() < ma50)
			return false;

		return !(currFast < -60.0 && currSlow < -60.0) // no long entries in
														// heavily oversold !
				&& prevSlow < currSlow && prevFast < currFast;
	}

	boolean sellSignalWeak(double[][] slowSMI, double[][] fastSMI, double ma20,
			double ma50, double ma100, double ma200, IBar bidBar) {
		double prevSlow = slowSMI[0][0], currSlow = slowSMI[0][1], prevFast = fastSMI[0][0], currFast = fastSMI[0][1];

		// too strong an uptrend, price above middle MA, don't try it !
		if (ma20 > ma50 && ma50 > ma100 && bidBar.getClose() > ma50)
			return false;

		return !(currFast > 60.0 && currSlow > 60.0) // no short entries in
														// heavily overbought !
				&& prevSlow > currSlow && prevFast > currFast;
	}

	@Override
	public EntryDirection checkCancel(Instrument instrument, Period period,
			IBar askBar, IBar bidBar, Filter filter) throws JFException {
		ITradeSetup.EntryDirection result = ITradeSetup.EntryDirection.NONE;
		double[][] slowSMI = indicators.smi(instrument, period, OfferSide.BID,
				50, 15, 5, 3, filter, 2, bidBar.getTime(), 0), fastSMI = indicators
				.smi(instrument, period, OfferSide.BID, 10, 3, 5, 3, filter, 2,
						bidBar.getTime(), 0);
		double ma20 = indicators.sma(instrument, period, OfferSide.BID,
				IIndicators.AppliedPrice.CLOSE, 20, filter, 1,
				bidBar.getTime(), 0)[0], ma50 = indicators.sma(instrument,
				period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 50,
				filter, 1, bidBar.getTime(), 0)[0], ma100 = indicators.sma(
				instrument, period, OfferSide.BID,
				IIndicators.AppliedPrice.CLOSE, 100, filter, 1,
				bidBar.getTime(), 0)[0], ma200 = indicators.sma(instrument,
				period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 200,
				filter, 1, bidBar.getTime(), 0)[0];

		if (!buySignalWeak(slowSMI, fastSMI, ma20, ma50, ma100, ma200, bidBar))
			return ITradeSetup.EntryDirection.LONG;
		else if (!sellSignalWeak(slowSMI, fastSMI, ma20, ma50, ma100, ma200,
				askBar))
			return ITradeSetup.EntryDirection.SHORT;

		return result;
	}

	@Override
	public void afterTradeReset(Instrument instrument) {
		ma50TrailFlags.put(instrument.name(), new Boolean(false));
	}
}
