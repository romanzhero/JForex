package jforex.trades;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jforex.events.TAEventDesc;
import jforex.events.TAEventDesc.TAEventType;
import jforex.techanalysis.Momentum;
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

public class SmaTradeSetup extends TradeSetup {

	// IMPORTANT ! If true it means entry signal will be given ONLY on one bar,
	// when MAs cross to 1 or 6 position !
	// Otherwise entry signal is given for all bars when MAs are in 1 or 6
	// position !
	protected boolean
			onlyCross = true,
			mktEntry = true;
	private Map<String, Boolean> ma50TrailFlags = new HashMap<String, Boolean>();
	protected Trend trend = null;
	protected Volatility vola = null;
	protected Momentum momentum = null;
	private double 
		flatPercThreshold,
		bBandsSqueezeThreshold;

	public SmaTradeSetup(IIndicators indicators, IHistory history, IEngine engine, Set<Instrument> subscribedInstruments, boolean mktEntry, boolean onlyCross, double pFlatPercThreshold, double pBBandsSqueezeThreshold) {
		super(indicators, history, engine);
		this.mktEntry = mktEntry;
		this.onlyCross = onlyCross;
		this.flatPercThreshold = pFlatPercThreshold;
		this.bBandsSqueezeThreshold = pBBandsSqueezeThreshold;
		this.trend = new Trend(indicators);
		this.vola = new Volatility(indicators);
		this.momentum = new Momentum(history, indicators);

		for (Instrument i : subscribedInstruments) {
			ma50TrailFlags.put(i.name(), new Boolean(false));
		}
	}

	@Override
	public String getName() {
		return new String("SMATrendIDFollow");
	}

	@Override
	public TAEventDesc checkEntry(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter) throws JFException {
		double[] ma20 = indicators.sma(instrument, period, OfferSide.BID,
				IIndicators.AppliedPrice.CLOSE, 20, filter, 2,
				bidBar.getTime(), 0), ma50 = indicators.sma(instrument, period,
				OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 50, filter, 2,
				bidBar.getTime(), 0), ma100 = indicators.sma(instrument,
				period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 100,
				filter, 2, bidBar.getTime(), 0), ma200 = indicators.sma(
				instrument, period, OfferSide.BID,
				IIndicators.AppliedPrice.CLOSE, 200, filter, 2,
				bidBar.getTime(), 0);

		if (buySignal(instrument, period, filter, ma20, ma50, ma100, ma200, bidBar, onlyCross)) {
			lastTradingEvent = "buy signal";
			TAEventDesc result = new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, true, askBar, bidBar, period);
			return result;
		} else if (sellSignal(instrument, period, filter, ma20, ma50, ma100, ma200, askBar, onlyCross)) {
			lastTradingEvent = "sell signal";
			TAEventDesc result = new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, false, askBar, bidBar, period);
			return result;
		}
		return null;
	}

	@Override
	public EntryDirection checkTakeOver(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter) throws JFException {
		double[] 
			ma20 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 20, filter, 2, bidBar.getTime(), 0), 
			ma50 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 50, filter, 2,	bidBar.getTime(), 0), 
			ma100 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 100, filter, 2, bidBar.getTime(), 0), 
			ma200 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 200, filter, 2, bidBar.getTime(), 0);
		double	bBandsSqueezePerc = vola.getBBandsSqueezePercentile(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, filter, bidBar.getTime(), 20, FXUtils.YEAR_WORTH_OF_4H_BARS);
		FLAT_REGIME_CAUSE isFlat = trend.isFlatRegime(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, filter, bidBar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS, flatPercThreshold);
		boolean
			isMA200Highest = trend.isMA200Highest(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime()),
			isMA200Lowest = trend.isMA200Lowest(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime());

		if (takeOverBuySignal(instrument, period, filter, ma20, ma50, ma100, ma200, bidBar, false, isFlat, bBandsSqueezePerc, isMA200Highest)) {
			lastTradingEvent = "long takeover";
			return ITradeSetup.EntryDirection.LONG;
		} else if (takeOverSellSignal(filter, period, instrument, ma20, ma50, ma100, ma200, askBar, false, isFlat, bBandsSqueezePerc, isMA200Lowest)) {
			lastTradingEvent = "short takeover";
			return ITradeSetup.EntryDirection.SHORT;
		}
		return ITradeSetup.EntryDirection.NONE;
	}

	boolean buySignal(Instrument instrument, Period period, Filter filter, double[] ma20, double[] ma50, double[] ma100, double[] ma200, IBar bidBar, boolean strict) throws JFException {
		double currMA20 = ma20[1], currMA50 = ma50[1], currMA100 = ma100[1], prevMA20 = ma20[0], prevMA50 = ma50[0], prevMA100 = ma100[0];

		if (strict)
			return (currMA20 > currMA50 && currMA50 > currMA100) && !(prevMA20 > prevMA50 && prevMA50 > prevMA100);
		else {
			FLAT_REGIME_CAUSE isFlat = trend.isFlatRegime(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, filter, bidBar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS, flatPercThreshold);		
			if (isDownMomentum(instrument, period, filter, bidBar)
				|| !isFlat.equals(FLAT_REGIME_CAUSE.NONE))
				return false;
			
			return currMA20 > currMA50 && currMA50 > currMA100;
		}
	}

	protected boolean isDownMomentum(Instrument instrument, Period period, Filter filter, IBar bidBar) throws JFException {
		double[][] 
				slowSMI = indicators.smi(instrument, period, OfferSide.BID,	50, 15, 5, 3, filter, 2, bidBar.getTime(), 0), 
				fastSMI = indicators.smi(instrument, period, OfferSide.BID, 10, 3, 5, 3, filter, 2,	bidBar.getTime(), 0);
		double[] stoch = momentum.getStochs(instrument, period, OfferSide.BID, bidBar.getTime());
		double 
			prevSlow = slowSMI[0][0], 
			currSlow = slowSMI[0][1], 
			prevFast = fastSMI[0][0], 
			currFast = fastSMI[0][1],
			fastStoch = stoch[0],
			slowStoch = stoch[1];
		return ((currSlow < prevSlow && currFast < currSlow)
				|| (currSlow < prevSlow && currFast < prevFast)
				|| fastStoch < slowStoch);
	}

	boolean sellSignal(Instrument instrument, Period period, Filter filter, double[] ma20, double[] ma50, double[] ma100, double[] ma200, IBar bidBar, boolean strict) throws JFException {
		double currMA20 = ma20[1], currMA50 = ma50[1], currMA100 = ma100[1], prevMA20 = ma20[0], prevMA50 = ma50[0], prevMA100 = ma100[0];

		if (strict)
			return (currMA20 < currMA50 && currMA50 < currMA100) && !(prevMA20 < prevMA50 && prevMA50 < prevMA100);
		else {
			FLAT_REGIME_CAUSE isFlat = trend.isFlatRegime(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, filter, bidBar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS, flatPercThreshold);			
			if(isUpMomentum(instrument, period, filter, bidBar)	|| !isFlat.equals(FLAT_REGIME_CAUSE.NONE))				
				return false;
			
			return currMA20 < currMA50 && currMA50 < currMA100;
		}
	}

	protected boolean isUpMomentum(Instrument instrument, Period period, Filter filter, IBar bidBar) throws JFException {
		double[][] 
				slowSMI = indicators.smi(instrument, period, OfferSide.BID,	50, 15, 5, 3, filter, 2, bidBar.getTime(), 0), 
				fastSMI = indicators.smi(instrument, period, OfferSide.BID, 10, 3, 5, 3, filter, 2,	bidBar.getTime(), 0);
		double[] stoch = momentum.getStochs(instrument, period, OfferSide.BID, bidBar.getTime());
		double 
			prevSlow = slowSMI[0][0], 
			currSlow = slowSMI[0][1], 
			prevFast = fastSMI[0][0], 
			currFast = fastSMI[0][1],
			fastStoch = stoch[0],
			slowStoch = stoch[1];
		return ((currSlow > prevSlow && currFast > currSlow)
				|| (currSlow > prevSlow && currFast > prevFast)
				|| fastStoch > slowStoch);
	}
	
	boolean takeOverBuySignal(Instrument instrument, Period period, Filter filter, double[] ma20, double[] ma50, double[] ma100,	double[] ma200, IBar bidBar, boolean strict, 
			FLAT_REGIME_CAUSE isFlat, double bBandsSqueezePerc, boolean isMA200Highest) throws JFException {
		if (!isFlat.equals(FLAT_REGIME_CAUSE.NONE) || bBandsSqueezePerc < bBandsSqueezeThreshold || isMA200Highest)
			return false;
		
		double currMA20 = ma20[1], currMA50 = ma50[1], currMA100 = ma100[1], prevMA20 = ma20[0], prevMA50 = ma50[0], prevMA100 = ma100[0];
		if (strict)
			return (currMA20 > currMA50 && currMA50 > currMA100) && !(prevMA20 > prevMA50 && prevMA50 > prevMA100);
		else {
			if (isDownMomentum(instrument, period, filter, bidBar))
				return false;
			return currMA20 > currMA50 && currMA50 > currMA100;
		}
	}

	boolean takeOverSellSignal(Filter filter, Period period, Instrument instrument, double[] ma20, double[] ma50, double[] ma100, double[] ma200, IBar bidBar, boolean strict, 
			FLAT_REGIME_CAUSE isFlat, double bBandsSqueezePerc, boolean isMA200Lowest) throws JFException {
		if (!isFlat.equals(FLAT_REGIME_CAUSE.NONE) || bBandsSqueezePerc < bBandsSqueezeThreshold || isMA200Lowest)
			return false;

		double currMA20 = ma20[1], currMA50 = ma50[1], currMA100 = ma100[1], prevMA20 = ma20[0], prevMA50 = ma50[0], prevMA100 = ma100[0];
		if (strict)
			return (currMA20 < currMA50 && currMA50 < currMA100) && !(prevMA20 < prevMA50 && prevMA50 < prevMA100);
		else {
			if (isUpMomentum(instrument, period, filter, bidBar))
				return false;
			return currMA20 < currMA50 && currMA50 < currMA100;
		}
	}

	@Override
	public void inTradeProcessing(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, IOrder order, List<TAEventDesc> marketEvents) throws JFException {
		// TODO Auto-generated method stub
		super.inTradeProcessing(instrument, period, askBar, bidBar, filter,	order, marketEvents);
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

	boolean startTrailingLong(double ma20, double ma50, double ma100,
			IBar bidBar, IOrder order) throws JFException {
		// POGRESNO !!! Uslov if (ma20 > ma50 && ma50 > ma100) je vec proveren
		// pri ulazu, ALI !!! moze da se promeni u toku trejda ! Vidi EURUSD 4H
		// 10.-12.02.15 !!! Iz
		// STRONG_DOWNTREND je presao u FRESH_DOWNTREND !!! U svakom slucaju ova
		// provera treba da se izbaci za sada i ma50 koristi kao
		// support/resistance
		// tj. kriterijum za trail bez obzira na polozaj MAs !
		// mozda jos jedino proveriti da li je cena ISPOD / IZNAD ma50, jer
		// inace trailing odmah zatvara poziciju !

		// u konkretnom primeru je market situation bio u stvari cisti flat !!!

		boolean result = false;
		Boolean ma50Trailing = ma50TrailFlags.get(order.getInstrument().name());
		if (ma50Trailing.booleanValue() && bidBar.getHigh() > ma20 && bidBar.getLow() > ma50) {
			ma50Trailing = new Boolean(false);
			ma50TrailFlags.put(order.getInstrument().name(), new Boolean(false));
		}
		// check whether MA50 was WITHIN candle
		if (!ma50Trailing.booleanValue()
				&& bidBar.getLow() < ma50
				&& (order.getStopLossPrice() == 0.0 || bidBar.getLow() > order
						.getStopLossPrice())) {
			// put SL on low of the bar which crossed MA50. No real trailing, do
			// it only once
			ma50TrailFlags.put(order.getInstrument().name(), new Boolean(true));
			FXUtils.setStopLoss(order, FXUtils.roundToPip(bidBar.getLow(), order.getInstrument()), bidBar.getTime(), this.getClass());
			lastTradingEvent = "start trailing long, ma50 broken";			
			result = true;
		} else if (!ma50Trailing.booleanValue() && bidBar.getHigh() < ma20) {
			// start trailing on MA50
			// additional criteria !!! ONLY if ma50 and ma100 relatively close
			// !! If far apart in a strong trend don't do it, same for piercing
			// MA50 !!!
			// also for ENTRIES !!! Crossing MA50 in WEAK and STRONG trend is
			// not the same !!! See 12.11 - 4.12.15 ! Missed some 160 pips
			// profit compared to exit at ma100, plus 350 pips of unnecessary
			// losses !!! Total 500+ pips missed !!!!
			if (order.getStopLossPrice() == 0.0	|| ma50 > order.getStopLossPrice()) {
				lastTradingEvent = "start trailing long, below ma50";			
				FXUtils.setStopLoss(order, FXUtils.roundToPip(ma50,	order.getInstrument()), bidBar.getTime(), this.getClass());
				result = true;
			}
		}
		return result;
	}

	// true means there's a strong downtrend and momentum exits are now switched
	// OFF
	boolean startTrailingShort(double ma20, double ma50, double ma100, IBar askBar, IOrder order) throws JFException {
		boolean result = false;
		Boolean ma50Trailing = (Boolean) ma50TrailFlags.get(order.getInstrument().name());
		if (ma50Trailing.booleanValue() && askBar.getLow() < ma20 && askBar.getHigh() < ma50) {
			ma50Trailing = new Boolean(false);
			ma50TrailFlags.put(order.getInstrument().name(), new Boolean(false));
		}
		// check whether MA50 was WITHIN candle
		if (!ma50Trailing.booleanValue()
				&& askBar.getHigh() > ma50
				&& (order.getStopLossPrice() == 0.0 || askBar.getHigh() < order.getStopLossPrice())) {
			// put SL on low of the bar which crossed MA50. No real trailing, do
			// it only once
			ma50TrailFlags.put(order.getInstrument().name(), new Boolean(true));
			FXUtils.setStopLoss(order, FXUtils.roundToPip(askBar.getHigh(),	order.getInstrument()), askBar.getTime(), this.getClass());
			result = true;
			lastTradingEvent = "start trailing short, ma50 broken";			

		} else if (!ma50Trailing.booleanValue() && askBar.getLow() > ma20) {
			// start trailing on MA50
			if (order.getStopLossPrice() == 0.0
					|| ma50 < order.getStopLossPrice()) {
				FXUtils.setStopLoss(order, FXUtils.roundToPip(ma50, order.getInstrument()), askBar.getTime(), this.getClass());
				result = true;
				lastTradingEvent = "start trailing short, below ma50";
			}
		}
		return result;
	}

	@Override
	public void afterTradeReset(Instrument instrument) {
		ma50TrailFlags.put(instrument.name(), new Boolean(false));
	}

	@Override
	public boolean isTradeLocked(Instrument instrument, Period period,
			IBar askBar, IBar bidBar, Filter filter, IOrder order)
			throws JFException {
		double[] ma20 = indicators.sma(instrument, period, OfferSide.BID,
				IIndicators.AppliedPrice.CLOSE, 20, filter, 2,
				bidBar.getTime(), 0), ma50 = indicators.sma(instrument, period,
				OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 50, filter, 2,
				bidBar.getTime(), 0), ma100 = indicators.sma(instrument,
				period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 100,
				filter, 2, bidBar.getTime(), 0), ma200 = indicators.sma(
				instrument, period, OfferSide.BID,
				IIndicators.AppliedPrice.CLOSE, 200, filter, 2,
				bidBar.getTime(), 0);

		if (order.isLong() && !buySignal(instrument, period, filter, ma20, ma50, ma100, ma200, bidBar, false)) {
			return false;
		} else if (!order.isLong() && !sellSignal(instrument, period, filter, ma20, ma50, ma100, ma200, askBar, false)) {
			return false;
		}
		return true;
	}

}
