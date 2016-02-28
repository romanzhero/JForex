package jforex.trades.old;

import java.util.List;

import jforex.events.TAEventDesc;
import jforex.events.TAEventDesc.TAEventType;
import jforex.techanalysis.Channel;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Trend.FLAT_REGIME_CAUSE;
import jforex.techanalysis.Volatility;
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

public class SmiTradeSetup extends TradeSetup {

	protected boolean mktEntry = true;
	protected Channel channel = null;
	protected Trend trend = null;
	protected Volatility vola = null;
	private double 
		flatPercThreshold,
		bBandsSqueezeThreshold;

	public SmiTradeSetup(IIndicators indicators, IHistory history, IEngine engine, boolean mktEntry, double pFlatPercThreshold, double pBBandsSqueezeThreshold) {
		super(indicators, history, engine);
		this.mktEntry = mktEntry;
		channel = new Channel(history, indicators);
		trend = new Trend(indicators);
		vola = new Volatility(indicators);
		flatPercThreshold = pFlatPercThreshold;
		bBandsSqueezeThreshold = pBBandsSqueezeThreshold;
	}

	@Override
	public String getName() {
		return new String("SMI");
	}

	@Override
	public void inTradeProcessing(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, IOrder order, List<TAEventDesc> marketEvents)	throws JFException {
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
			EntryDirection cancelSignal = checkCancel(instrument, period, askBar, bidBar, filter);
			if ((order.isLong() && cancelSignal.equals(EntryDirection.LONG))
				|| (!order.isLong() && cancelSignal.equals(EntryDirection.SHORT))
				|| oppositeFlatEntrySignal
				|| oppositePUPBSignal) {
				lastTradingEvent = "cancel due to opposite flat signal";
				order.close();
				order.waitForUpdate(null);
			}				
			return;
		}
		TAEventDesc signal = checkEntry(instrument, period,	askBar, bidBar, filter);
		if (order.isLong() && signal != null && !signal.isLong) {
			lastTradingEvent = "long breakeven signal";
			if (bidBar.getClose() > order.getOpenPrice()) {
				FXUtils.setStopLoss(order, order.getOpenPrice(), bidBar.getTime(), getClass());
			}
			else if (bidBar.getLow() > order.getStopLossPrice() || order.getStopLossPrice() == 0.0) {
				FXUtils.setStopLoss(order, bidBar.getLow(), bidBar.getTime(), getClass());
			}
		} else if (!order.isLong()	&& signal != null && signal.isLong) {
			lastTradingEvent = "short breakeven signal";
			if (askBar.getClose() < order.getOpenPrice()) {
				FXUtils.setStopLoss(order, order.getOpenPrice(), bidBar.getTime(), getClass());
			}
			else if (bidBar.getHigh() < order.getStopLossPrice() || order.getStopLossPrice() == 0.0) {
				FXUtils.setStopLoss(order, askBar.getHigh(), bidBar.getTime(), getClass());
			}
		}
		// check market events. Get out if opposite flat signal occurs - it will take over immediately !
		for (TAEventDesc taEvent : marketEvents) {
			if (taEvent.eventType.equals(TAEventType.ENTRY_SIGNAL) && taEvent.eventName.equals("Flat")
				&& ((order.isLong() && !taEvent.isLong) || (!order.isLong() && taEvent.isLong))) {
				double[][] 
					slowSMI = indicators.smi(instrument, period, OfferSide.BID,	50, 15, 5, 3, filter, 2, bidBar.getTime(), 0), 
					fastSMI = indicators.smi(instrument, period, OfferSide.BID, 10, 3, 5, 3, filter, 2,	bidBar.getTime(), 0);
				double 
					ma20 = indicators.sma(instrument, period, OfferSide.BID,	IIndicators.AppliedPrice.CLOSE, 20, filter, 1, bidBar.getTime(), 0)[0], 
					ma50 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 50, filter, 1, bidBar.getTime(), 0)[0], 
					ma100 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 100, filter, 1, bidBar.getTime(), 0)[0],
					atr = vola.getATR(instrument, period, OfferSide.BID, bidBar.getTime(), 14),
					prevSlow = slowSMI[0][0], 
					currSlow = slowSMI[0][1], 
					prevFast = fastSMI[0][0], 
					currFast = fastSMI[0][1];
 
				boolean 
					continueLong = order.getProfitLossInPips() < 2.5 * atr * Math.pow(10, instrument.getPipScale())
									&& ((currSlow > prevSlow && currFast > currSlow && bidBar.getClose() > ma20)
										|| (bidBar.getClose() > ma20 && bidBar.getClose() > ma50 && bidBar.getClose() > ma100)),
					continueShort = order.getProfitLossInPips() < 2.5 * atr * Math.pow(10, instrument.getPipScale())
									&& (currSlow < prevSlow && currFast < currSlow && bidBar.getClose() < ma20)
										|| (bidBar.getClose() < ma20 && bidBar.getClose() < ma50 && bidBar.getClose() < ma100);
				if ((order.isLong() && !continueLong) || (!order.isLong() && !continueShort)) {
					lastTradingEvent = "move SL due to opposite flat signal";
					FXUtils.setStopLoss(order, order.isLong() ? bidBar.getLow() : askBar.getHigh(), bidBar.getTime(), getClass());
				}
			}
			else if (taEvent.eventType.equals(TAEventType.ENTRY_SIGNAL) && taEvent.eventName.equals("PUPB")
					&& ((order.isLong() && !taEvent.isLong) || (!order.isLong() && taEvent.isLong))) {
					lastTradingEvent = "close due to opposite PUPB signal";
					order.close();
					order.waitForUpdate(null);
			}
		}
	}

	@Override
	public TAEventDesc checkEntry(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter) throws JFException {
		double[][] 
			slowSMI = indicators.smi(instrument, period, OfferSide.BID,	50, 15, 5, 3, filter, 2, bidBar.getTime(), 0), 
			fastSMI = indicators.smi(instrument, period, OfferSide.BID, 10, 3, 5, 3, filter, 2,	bidBar.getTime(), 0);
		double 
			ma20 = indicators.sma(instrument, period, OfferSide.BID,	IIndicators.AppliedPrice.CLOSE, 20, filter, 1, bidBar.getTime(), 0)[0], 
			ma50 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 50, filter, 1, bidBar.getTime(), 0)[0], 
			ma100 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 100, filter, 1, bidBar.getTime(), 0)[0], 
			ma200 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 200, filter, 1, bidBar.getTime(), 0)[0],
			channelPos = channel.priceChannelPos(instrument, period, OfferSide.BID, bidBar.getTime(), bidBar.getClose()),
			bBandsSqueezePerc = vola.getBBandsSqueezePercentile(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, filter, bidBar.getTime(), 20, FXUtils.YEAR_WORTH_OF_4H_BARS);
		FLAT_REGIME_CAUSE isFlat = trend.isFlatRegime(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, filter, bidBar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS, flatPercThreshold);
	
		if (buySignal(instrument, period, slowSMI, fastSMI, ma20, ma50, ma100, ma200, bidBar, channelPos, isFlat, bBandsSqueezePerc)) {
			TAEventDesc result = new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, true, askBar, bidBar, period);
			lastTradingEvent = "buy signal";			
			return result;
		} else if (sellSignal(instrument, period, slowSMI, fastSMI, ma20, ma50, ma100, ma200, askBar, channelPos, isFlat, bBandsSqueezePerc)) {
			TAEventDesc result = new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, false, askBar, bidBar, period);
			lastTradingEvent = "sell signal";			
			return result;
		}
		return null;
	}

	@Override
	public EntryDirection checkExit(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter, IOrder order) throws JFException {
		double[][] 
			slowSMI = indicators.smi(instrument, period, OfferSide.BID,	50, 15, 5, 3, filter, 2, bidBar.getTime(), 0), 
			fastSMI = indicators.smi(instrument, period, OfferSide.BID, 10, 3, 5, 3, filter, 2,	bidBar.getTime(), 0);

		if (exitLong(slowSMI, fastSMI, instrument, period, filter, bidBar)) {
			lastTradingEvent = "long exit signal";
			return ITradeSetup.EntryDirection.LONG;
		}
		else if (exitShort(slowSMI, fastSMI, instrument, period, filter, askBar)) {
			lastTradingEvent = "short exit signal";
			return ITradeSetup.EntryDirection.SHORT;
		}
		return ITradeSetup.EntryDirection.NONE;
	}

	boolean buySignal(Instrument instrument, Period pPeriod, double[][] slowSMI, double[][] fastSMI, double ma20, double ma50, double ma100, double ma200, IBar bidBar, double channelPos, FLAT_REGIME_CAUSE isFlat, double bBandsSqueezePerc) throws JFException {
		double 
			prevSlow = slowSMI[0][0], 
			currSlow = slowSMI[0][1], 
			prevFast = fastSMI[0][0], 
			currFast = fastSMI[0][1];

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

	boolean sellSignal(Instrument instrument, Period pPeriod, double[][] slowSMI, double[][] fastSMI, double ma20,	double ma50, double ma100, double ma200, IBar bidBar, double channelPos, FLAT_REGIME_CAUSE isFlat, double bBandsSqueezePerc) throws JFException {
		double 
			prevSlow = slowSMI[0][0], 
			currSlow = slowSMI[0][1], 
			prevFast = fastSMI[0][0], 
			currFast = fastSMI[0][1];

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

	boolean exitLong(double[][] slowSMI, double[][] fastSMI, Instrument instrument, Period period, Filter filter, IBar currBar)	throws JFException {
		double 
			currSlow = slowSMI[0][1], 
			prevFast = fastSMI[0][0], 
			currFast = fastSMI[0][1], 
			ma20 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 20, filter, 1,	currBar.getTime(), 0)[0];

		return !(currFast > 60.0 && currSlow > 60.0) && // no need to exit long while heavily overbought !
				((currFast < currSlow && currFast < prevFast  && currBar.getClose() < ma20) // probably no need to exit if slow overbought and still raising !!
				|| (currFast < -60.0 && currSlow < -60.0) // also exit if both lines heavily overSOLD, clearly strong downtrend !
				|| (currSlow < -60.0 && currFast < prevFast)); // also if slow oversold (even if raising !) and fast starts falling
	}

	boolean exitShort(double[][] slowSMI, double[][] fastSMI, Instrument instrument, Period period, Filter filter, IBar currBar) throws JFException {
		double 
			currSlow = slowSMI[0][1], 
			prevFast = fastSMI[0][0], 
			currFast = fastSMI[0][1], 
			ma20 = indicators.sma(instrument, period, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 20, filter, 1,	currBar.getTime(), 0)[0];

		return !(currFast < -60.0 && currSlow < -60.0) && // no need to exit short while heavily oversold !
				((currFast > currSlow && currFast > prevFast && currBar.getClose() > ma20)
				|| (currFast > 60.0 && currSlow > 60.0)
				|| (currSlow > 60.0 && currFast > prevFast));
	}

	boolean buySignalWeak(double[][] slowSMI, double[][] fastSMI, double ma20, double ma50, double ma100, double ma200, IBar bidBar) {
		double prevSlow = slowSMI[0][0], currSlow = slowSMI[0][1], prevFast = fastSMI[0][0], currFast = fastSMI[0][1];

		// too strong a downtrend, price below middle MA, don't try it !
		if (ma20 < ma50 && ma50 < ma100 && bidBar.getClose() < ma50)
			return false;

		return !(currFast < -60.0 && currSlow < -60.0) // no long entries in heavily oversold !
				&& currSlow > prevSlow  && currFast > prevFast;
	}

	boolean sellSignalWeak(double[][] slowSMI, double[][] fastSMI, double ma20,	double ma50, double ma100, double ma200, IBar bidBar) {
		double prevSlow = slowSMI[0][0], currSlow = slowSMI[0][1], prevFast = fastSMI[0][0], currFast = fastSMI[0][1];

		// too strong an uptrend, price above middle MA, don't try it !
		if (ma20 > ma50 && ma50 > ma100 && bidBar.getClose() > ma50)
			return false;

		return !(currFast > 60.0 && currSlow > 60.0) // no short entries in heavily overbought !
				&& currSlow < prevSlow && currFast < prevFast;
	}

	@Override
	public EntryDirection checkCancel(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter) throws JFException {
		ITradeSetup.EntryDirection result = ITradeSetup.EntryDirection.NONE;
		double[][] slowSMI = indicators.smi(instrument, period, OfferSide.BID,	50, 15, 5, 3, filter, 2, bidBar.getTime(), 0), 
				fastSMI = indicators.smi(instrument, period, OfferSide.BID, 10, 3, 5, 3, filter, 2,	bidBar.getTime(), 0);
		double ma20 = indicators.sma(instrument, period, OfferSide.BID,	IIndicators.AppliedPrice.CLOSE, 20, filter, 1, bidBar.getTime(), 0)[0], 
				ma50 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 50, filter, 1, bidBar.getTime(), 0)[0], 
				ma100 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 100, filter, 1, bidBar.getTime(), 0)[0], 
				ma200 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 200, filter, 1, bidBar.getTime(), 0)[0];

		if (!buySignalWeak(slowSMI, fastSMI, ma20, ma50, ma100, ma200, bidBar))
			return ITradeSetup.EntryDirection.LONG;
		else if (!sellSignalWeak(slowSMI, fastSMI, ma20, ma50, ma100, ma200, askBar))
			return ITradeSetup.EntryDirection.SHORT;

		return result;
	}

	@Override
	public IOrder submitOrder(String label, Instrument instrument, boolean isLong, double amount, IBar bidBar, IBar askBar)	throws JFException {
		if (mktEntry)
			return submitMktOrder(label, instrument, isLong, amount, bidBar, askBar);
		else
			return submitStpOrder(label, instrument, isLong, amount, bidBar, askBar, 0);
	}

}
