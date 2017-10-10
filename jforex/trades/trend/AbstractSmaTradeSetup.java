package jforex.trades.trend;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jforex.events.TAEventDesc;
import jforex.events.TAEventDesc.TAEventType;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Trend.FLAT_REGIME_CAUSE;
import jforex.techanalysis.source.FlexTASource;
import jforex.techanalysis.source.FlexTAValue;
import jforex.trades.TradeSetup;
import jforex.utils.FXUtils;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public abstract class AbstractSmaTradeSetup extends TradeSetup {

	protected boolean 
		onlyCross = true,
		mktEntry = true,
		trailsOnMA50 = false;
	protected Map<String, Boolean> ma50TrailFlags = new HashMap<String, Boolean>();
	protected double flatPercThreshold;
	protected double bBandsSqueezeThreshold;
	protected double lastSL;
	
	public AbstractSmaTradeSetup(IEngine engine, IContext context, Set<Instrument> subscribedInstruments, 
			boolean mktEntry, 
			boolean onlyCross, 
			double pFlatPercThreshold, 
			double pBBandsSqueezeThreshold, 
			boolean trailsOnMA50) {
		super(engine, context);
		this.mktEntry = mktEntry;
		this.onlyCross = onlyCross;
		this.flatPercThreshold = pFlatPercThreshold;
		this.bBandsSqueezeThreshold = pBBandsSqueezeThreshold;
		this.trailsOnMA50 = trailsOnMA50;

		for (Instrument i : subscribedInstruments) {
			ma50TrailFlags.put(i.name(), new Boolean(false));
		}
	}
	
	public AbstractSmaTradeSetup(IEngine engine, IContext context, Set<Instrument> subscribedInstruments, 
			boolean mktEntry, 
			boolean onlyCross, 
			double pFlatPercThreshold, 
			double pBBandsSqueezeThreshold, 
			boolean trailsOnMA50,
			boolean takeOverOnly) {
		super(engine, context, takeOverOnly);
		this.mktEntry = mktEntry;
		this.onlyCross = onlyCross;
		this.flatPercThreshold = pFlatPercThreshold;
		this.bBandsSqueezeThreshold = pBBandsSqueezeThreshold;
		this.trailsOnMA50 = trailsOnMA50;

		for (Instrument i : subscribedInstruments) {
			ma50TrailFlags.put(i.name(), new Boolean(false));
		}
	}

	protected abstract boolean sellSignal(Instrument instrument, Period period, Filter filter, double[] ma20, double[] ma50, double[] ma100, double[] ma200,
			IBar bidBar, boolean strict, Map<String, FlexTAValue> taValues) throws JFException;

	protected abstract boolean buySignal(Instrument instrument, Period period, Filter filter,	double[] ma20, double[] ma50, double[] ma100, double[] ma200, 
			IBar bidBar, boolean strict, Map<String, FlexTAValue> taValues) throws JFException;

	public abstract String getName();

	@Override
	public TAEventDesc checkEntry(Instrument instrument, Period period, IBar askBar,
			IBar bidBar, Filter filter, Map<String, FlexTAValue> taValues) throws JFException {
				double[][] mas = taValues.get(FlexTASource.MAs).getDa2DimValue();
				double[] 
						ma20 = new double[]{ mas[0][0], mas[1][0]}, 
						ma50 = new double[]{ mas[0][1], mas[1][1]},
						ma100 = new double[]{ mas[0][2], mas[1][2]},
						ma200 = new double[]{ mas[0][3], mas[1][3]};
			
				if (buySignal(instrument, period, filter, ma20, ma50, ma100, ma200, bidBar, onlyCross, taValues)) {
					//lastTradingEvent = "buy signal";
					double twoAtrAbs = FXUtils.roundToPip(2	* taValues.get(FlexTASource.ATR).getDoubleValue() / Math.pow(10, instrument.getPipValue()), instrument);
					lastSL = bidBar.getClose() - twoAtrAbs;
					TAEventDesc result = new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, true, askBar, bidBar, period);
					return result;
				} else if (sellSignal(instrument, period, filter, ma20, ma50, ma100, ma200, askBar, onlyCross, taValues)) {
					//lastTradingEvent = "sell signal";
					double twoAtrAbs = FXUtils.roundToPip(2	* taValues.get(FlexTASource.ATR).getDoubleValue() / Math.pow(10, instrument.getPipValue()), instrument);
					lastSL = askBar.getClose() + twoAtrAbs;
					TAEventDesc result = new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, false, askBar, bidBar, period);
					return result;
				}
				return null;
			}

	protected boolean isDownMomentum(Instrument instrument, Period period, Filter filter,
			IBar bidBar, Map<String, FlexTAValue> taValues) throws JFException {
				double[][] 
						smis = taValues.get(FlexTASource.SMI).getDa2DimValue(),
						stochs = taValues.get(FlexTASource.STOCH).getDa2DimValue();	
				double 
					prevSlow = smis[1][1], 
					currSlow = smis[1][2], 
					prevFast = smis[0][1], 
					currFast = smis[0][2],
					fastStoch = stochs[0][1], 
					slowStoch = stochs[1][1]; 
				return ((currSlow < prevSlow && currFast < currSlow)
						|| (currSlow < prevSlow && currFast < prevFast)
						|| fastStoch < slowStoch);
			}

	protected boolean isUpMomentum(Instrument instrument, Period period, Filter filter,
			IBar bidBar, Map<String, FlexTAValue> taValues) throws JFException {
				double[][] 
						smis = taValues.get(FlexTASource.SMI).getDa2DimValue(),
						stochs = taValues.get(FlexTASource.STOCH).getDa2DimValue();	
				double 
					prevSlow = smis[1][1], 
					currSlow = smis[1][2], 
					prevFast = smis[0][1], 
					currFast = smis[0][2],
					fastStoch = stochs[0][1], 
					slowStoch = stochs[1][1]; 
				return ((currSlow > prevSlow && currFast > currSlow)
						|| (currSlow > prevSlow && currFast > prevFast)
						|| fastStoch > slowStoch);
			}

	@Override
	public void inTradeProcessing(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, IOrder order, Map<String, FlexTAValue> taValues, List<TAEventDesc> marketEvents) throws JFException {
		super.inTradeProcessing(instrument, period, askBar, bidBar, filter,	order, taValues, marketEvents);
		if (order == null || marketEvents == null)
			return;

		for (TAEventDesc curr : marketEvents) {
			if (curr.eventType.equals(TAEventType.PNL_INFO)) {
				double pnlRangeRatio = curr.pnlDayRangeRatio + FXUtils.currPercPnL(order, bidBar, askBar) / curr.avgPnLRange;
				if (pnlRangeRatio > 1.5) {
					startTrailingNBarsBack(context.getHistory(), instrument, period, askBar, bidBar, filter, order, 1);
					return;
				} else if (pnlRangeRatio > 1) { 
					startTrailingNBarsBack(context.getHistory(), instrument, period, askBar, bidBar, filter, order, 3);
					return;
				}
			}
		}
		
		
		double[][] mas = taValues.get(FlexTASource.MAs).getDa2DimValue();
		double
			ma20 = mas[1][0], 
			ma50 = mas[1][1],
			ma100 = mas[1][2],
			ma200ma100dist = taValues.get(FlexTASource.MA200MA100_TREND_DISTANCE_PERC).getDoubleValue(),
			bBandsSqueeze = taValues.get(FlexTASource.BBANDS_SQUEEZE_PERC).getDoubleValue();
		Trend.TREND_STATE trendId = taValues.get(FlexTASource.TREND_ID).getTrendStateValue();
		if (order.isLong()) {
			//longMoveSLDueToBigBearishCandle(bidBar, order, taValues);
			//longMoveSLDueToShortFlatSignal(bidBar, order, marketEvents);
//			longMoveSLDueToShortFlatStrongSignal(bidBar, order, marketEvents);
			//longSetBreakEvenOnProfit(instrument, bidBar, order, taValues, 2.5);
			//longMoveSLDueToExtremeRSI3(bidBar, order, taValues);
			
			if (ma100 > order.getStopLossPrice()) {
				// check all the posibilities of MA100 position at position opening ??!!
				FXUtils.setStopLoss(order, ma100, bidBar.getTime(), getClass());
			}
			
			boolean isMA200Lowest = taValues.get(FlexTASource.MA200_LOWEST).getBooleanValue();
			// no trailing if strong uptrend or narrow channel
			if (bBandsSqueeze < 30
				|| (isMA200Lowest && trendId.equals(Trend.TREND_STATE.UP_STRONG) && ma200ma100dist >= 70))
				return;
			
			if (shortFlatStrongSignal(askBar, order, marketEvents)) {
				double newSL = ma50 > order.getOpenPrice() ? ma50 : order.getOpenPrice();
				if (newSL > order.getStopLossPrice()) {
					lastTradingEvent = "(Long) Moved SL due to strong flat signal";
					FXUtils.setStopLoss(order, newSL, bidBar.getTime(), getClass());
				}
			}
			if (!taValues.get(FlexTASource.TREND_ID).getTrendStateValue().equals(Trend.TREND_STATE.UP_STRONG)) {
				double newSL = ma50 > order.getOpenPrice() ? ma50 : order.getOpenPrice();
				if (newSL > order.getStopLossPrice()) {
					lastTradingEvent = "(Long) Moved SL due to trend ID change";
					FXUtils.setStopLoss(order, newSL, bidBar.getTime(), getClass());
				}
			}
			startTrailingLong(ma20, ma50, ma100, bidBar, order);
		}
		else { 
//			shortMoveSLDueToBigBullishCandle(bidBar, order, taValues);
//			shortMoveSLDueToLongFlatSignal(bidBar, order, marketEvents);	
//			shortMoveSLDueToLongFlatStrongSignal(bidBar, order, marketEvents);	
//			shortSetBreakEvenOnProfit(instrument, bidBar, order, taValues, 2.5);
//			shortMoveSLDueToExtremeRSI3(bidBar, order, taValues);
			
			if (ma100 < order.getStopLossPrice()) {
				FXUtils.setStopLoss(order, ma100, bidBar.getTime(), getClass());
			}
			boolean isMA200Highest = taValues.get(FlexTASource.MA200_HIGHEST).getBooleanValue();
			// no trailing if strong uptrend or narrow channel
			if (bBandsSqueeze < 30
				|| (isMA200Highest && trendId.equals(Trend.TREND_STATE.DOWN_STRONG) && ma200ma100dist >= 70))
				return;

			
			if (longFlatStrongSignal(bidBar, order, marketEvents)) {
				double newSL = ma50 < order.getOpenPrice() ? ma50 : order.getOpenPrice();
				if (newSL < order.getStopLossPrice()) {
					lastTradingEvent = "(Short) Moved SL due to strong flat signal";
					FXUtils.setStopLoss(order, newSL, bidBar.getTime(), getClass());
				}
			}
			if (!taValues.get(FlexTASource.TREND_ID).getTrendStateValue().equals(Trend.TREND_STATE.DOWN_STRONG)) {
				double newSL = ma50 < order.getOpenPrice() ? ma50 : order.getOpenPrice();
				if (newSL < order.getStopLossPrice()) {
					lastTradingEvent = "(Short) Moved SL due to trend ID change";
					FXUtils.setStopLoss(order, newSL, bidBar.getTime(), getClass());
				}
			}
			startTrailingShort(ma20, ma50, ma100, askBar, order);
		}

	}

	protected boolean startTrailingNBarsBack(IHistory history, Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter, IOrder order, int howManyBars) throws JFException {
		List<IBar> prevBars = history.getBars(instrument, period, OfferSide.BID, filter, howManyBars, bidBar.getTime(), 0);
		if (order.isLong()) {
			if (prevBars.get(0).getLow() > order.getStopLossPrice()) {
				order.setStopLossPrice(prevBars.get(0).getLow());
				order.waitForUpdate(null);
				this.lastTradingEvent = "Moved SL due to big profit compared to daily range";
				return true;
			}
		} else {
			if (prevBars.get(0).getHigh() < order.getStopLossPrice()) {
				order.setStopLossPrice(prevBars.get(0).getHigh());
				order.waitForUpdate(null);
				this.lastTradingEvent = "Moved SL due to big profit compared to daily range";
				return true;
			}			
		}		
		return false;
	}

	boolean startTrailingLong(double ma20, double ma50, double ma100, IBar bidBar, IOrder order) throws JFException {
		if (!trailsOnMA50)
			return false;
		
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
				&& (order.getStopLossPrice() == 0.0 || bidBar.getLow() > order.getStopLossPrice())) {
			// put SL on low of the bar which crossed MA50. No real trailing, do it only once
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

	boolean startTrailingShort(double ma20, double ma50, double ma100, IBar askBar, IOrder order) throws JFException {
		if (!trailsOnMA50)
			return false;
		
		boolean result = false;
		Boolean ma50Trailing = (Boolean) ma50TrailFlags.get(order.getInstrument().name());
		if (ma50Trailing.booleanValue() && askBar.getLow() < ma20 && askBar.getHigh() < ma50) {
			ma50Trailing = new Boolean(false);
			ma50TrailFlags.put(order.getInstrument().name(), new Boolean(false));
		}
		// check whether MA50 was WITHIN candle
		if (!ma50Trailing.booleanValue() && askBar.getHigh() > ma50
			&& (order.getStopLossPrice() == 0.0 || askBar.getHigh() < order.getStopLossPrice())) {
			// put SL on low of the bar which crossed MA50. No real trailing, do
			// it only once
			ma50TrailFlags.put(order.getInstrument().name(), new Boolean(true));
			FXUtils.setStopLoss(order, FXUtils.roundToPip(askBar.getHigh(),	order.getInstrument()), askBar.getTime(), this.getClass());
			result = true;
			lastTradingEvent = "start trailing short, ma50 broken";			
	
		} else if (!ma50Trailing.booleanValue() && askBar.getLow() > ma20) {
			// start trailing on MA50
			if (order.getStopLossPrice() == 0.0	|| ma50 < order.getStopLossPrice()) {
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
	public boolean isTradeLocked(Instrument instrument, Period period, IBar askBar,
			IBar bidBar, Filter filter, IOrder order, Map<String, FlexTAValue> taValues)
			throws JFException {
				double[][] mas = taValues.get(FlexTASource.MAs).getDa2DimValue();
				double[] 
						ma20 = new double[]{ mas[0][0], mas[1][0]}, 
						ma50 = new double[]{ mas[0][1], mas[1][1]},
						ma100 = new double[]{ mas[0][2], mas[1][2]},
						ma200 = new double[]{ mas[0][3], mas[1][3]};
			
				if (order.isLong() && !buySignal(instrument, period, filter, ma20, ma50, ma100, ma200, bidBar, false, taValues)) {
					return false;
				} else if (!order.isLong() && !sellSignal(instrument, period, filter, ma20, ma50, ma100, ma200, askBar, false, taValues)) {
					return false;
				}
				return true;
			}

	@Override
	public IOrder submitOrder(String label, Instrument instrument, boolean isLong, double amount, IBar bidBar, IBar askBar) throws JFException {
				if (this.mktEntry)
					return submitMktOrder(label, instrument, isLong, amount, bidBar, askBar, lastSL);
				else
					return submitStpOrder(label, instrument, isLong, amount, bidBar, askBar, lastSL);
			}

	@Override
	public EntryDirection checkTakeOver(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter,	Map<String, FlexTAValue> taValues) throws JFException {
		double[][] mas = taValues.get(FlexTASource.MAs).getDa2DimValue();
		double[] 
				ma20 = new double[]{ mas[0][0], mas[1][0]}, 
				ma50 = new double[]{ mas[0][1], mas[1][1]},
				ma100 = new double[]{ mas[0][2], mas[1][2]},
				ma200 = new double[]{ mas[0][3], mas[1][3]};
		double	bBandsSqueezePerc = taValues.get(FlexTASource.BBANDS_SQUEEZE_PERC).getDoubleValue(); 
		FLAT_REGIME_CAUSE isFlat = (FLAT_REGIME_CAUSE)taValues.get(FlexTASource.FLAT_REGIME).getValue();
		boolean
			isMA200Highest = taValues.get(FlexTASource.MA200_HIGHEST).getBooleanValue(),
			isMA200Lowest = taValues.get(FlexTASource.MA200_LOWEST).getBooleanValue();

		if (takeOverBuySignal(instrument, period, filter, ma20, ma50, ma100, ma200, bidBar, false, isFlat, bBandsSqueezePerc, isMA200Highest, taValues)) {
			lastTradingEvent = "long takeover";
			return EntryDirection.LONG;
		} else if (takeOverSellSignal(filter, period, instrument, ma20, ma50, ma100, ma200, askBar, false, isFlat, bBandsSqueezePerc, isMA200Lowest, taValues)) {
			lastTradingEvent = "short takeover";
			return EntryDirection.SHORT;
		}
		return EntryDirection.NONE;	
	}
	
	boolean takeOverBuySignal(Instrument instrument, Period period, Filter filter, double[] ma20, double[] ma50, double[] ma100, double[] ma200, IBar bidBar, boolean strict, 
			FLAT_REGIME_CAUSE isFlat, double bBandsSqueezePerc, boolean isMA200Highest, Map<String, FlexTAValue> taValues) throws JFException {
		if (!isFlat.equals(FLAT_REGIME_CAUSE.NONE) || bBandsSqueezePerc < bBandsSqueezeThreshold || isMA200Highest)
			return false;
		
		double currMA20 = ma20[1], currMA50 = ma50[1], currMA100 = ma100[1], prevMA20 = ma20[0], prevMA50 = ma50[0], prevMA100 = ma100[0];
		if (strict)
			return (currMA20 > currMA50 && currMA50 > currMA100) && !(prevMA20 > prevMA50 && prevMA50 > prevMA100);
		else {
			if (isDownMomentum(instrument, period, filter, bidBar, taValues))
				return false;
			return currMA20 > currMA50 && currMA50 > currMA100;
		}
	}

	boolean takeOverSellSignal(Filter filter, Period period, Instrument instrument, double[] ma20, double[] ma50, double[] ma100, double[] ma200, IBar bidBar, boolean strict, 
			FLAT_REGIME_CAUSE isFlat, double bBandsSqueezePerc, boolean isMA200Lowest, Map<String, FlexTAValue> taValues) throws JFException {
		if (!isFlat.equals(FLAT_REGIME_CAUSE.NONE) || bBandsSqueezePerc < bBandsSqueezeThreshold || isMA200Lowest)
			return false;

		double currMA20 = ma20[1], currMA50 = ma50[1], currMA100 = ma100[1], prevMA20 = ma20[0], prevMA50 = ma50[0], prevMA100 = ma100[0];
		if (strict)
			return (currMA20 < currMA50 && currMA50 < currMA100) && !(prevMA20 < prevMA50 && prevMA50 < prevMA100);
		else {
			if (isUpMomentum(instrument, period, filter, bidBar, taValues))
				return false;
			return currMA20 < currMA50 && currMA50 < currMA100;
		}
	}

}