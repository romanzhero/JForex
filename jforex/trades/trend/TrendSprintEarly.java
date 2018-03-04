package jforex.trades.trend;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
import jforex.techanalysis.Momentum;
import jforex.techanalysis.Volatility;
import jforex.techanalysis.Momentum.MACD_H_STATE;
import jforex.techanalysis.Momentum.STOCH_STATE;
import jforex.techanalysis.Trend.TREND_STATE;
import jforex.techanalysis.source.FlexTASource;
import jforex.techanalysis.source.TechnicalSituation;
import jforex.utils.StopLoss;
import jforex.utils.log.FlexLogEntry;

public class TrendSprintEarly extends AbstractSmaTradeSetup {

	public TrendSprintEarly(IEngine engine, IContext context, Set<Instrument> subscribedInstruments, boolean mktEntry,
			boolean onlyCross, boolean useEntryFilters, double pFlatPercThreshold, double pBBandsSqueezeThreshold,
			boolean trailsOnMA50) {
		super(engine, context, subscribedInstruments, mktEntry, onlyCross, useEntryFilters, pFlatPercThreshold,
				pBBandsSqueezeThreshold, trailsOnMA50);
	}

	public TrendSprintEarly(IEngine engine, IContext context, Set<Instrument> subscribedInstruments, boolean mktEntry,
			boolean onlyCross, double pFlatPercThreshold, double pBBandsSqueezeThreshold, boolean trailsOnMA50,
			boolean takeOverOnly) {
		super(engine, context, subscribedInstruments, mktEntry, onlyCross, pFlatPercThreshold, pBBandsSqueezeThreshold,
				trailsOnMA50, takeOverOnly);
	}

	@Override
	protected boolean sellSignal(Instrument instrument, Period period, Filter filter, double[] ma20, double[] ma50,
			double[] ma100, double[] ma200, IBar bidBar, boolean strict, Map<String, FlexLogEntry> taValues)
			throws JFException {
		TREND_STATE trendID = taValues.get(FlexTASource.TREND_ID).getTrendStateValue();
		TechnicalSituation taSituation = taValues.get(FlexTASource.TA_SITUATION).getTehnicalSituationValue();
		MACD_H_STATE macdHState = taSituation.macdHistoState;
		STOCH_STATE stochState = taSituation.stochState;
		boolean 
			closeBelowAllMAs = taValues.get(FlexTASource.CLOSE_BELOW_ALL_MAs).getBooleanValue(),
			ma200Lowest = taValues.get(FlexTASource.MA200_LOWEST).getBooleanValue();
		return closeBelowAllMAs
				&& !ma200Lowest
				&& (trendID.equals(TREND_STATE.DOWN_STRONG) || trendID.equals(TREND_STATE.FRESH_DOWN))
				&& !taValues.get(FlexTASource.MA20_SLOPE).toString().toUpperCase().startsWith("RAISING")
				&& macdHState.toString().toUpperCase().startsWith("FALLING")
				&& (stochState.equals(STOCH_STATE.BEARISH_FALLING_IN_MIDDLE)
					|| stochState.equals(STOCH_STATE.BEARISH_OVERSOLD_BOTH)
					|| stochState.equals(STOCH_STATE.BEARISH_OVERSOLD_FAST));
	}

	/* 
 Kriterijumi za ulazak u LONG trejd:
 Grupa 1: trend ili flat
1. TREND_ID							MA20 mora biti najvislji --> FRESH_UP, UP_STRONG
2. MAs_DISTANCE_PERC						
3. MA200_HIGHEST						
4. MA200_LOWEST							
5. MA200_IN_CHANNEL						
6. MA200MA100_TREND_DISTANCE_PERC			
7. FLAT_REGIME 
8. ICHI							
9. MAs SLOPE

Grupa 2: momentum i OS/OB
10. Stanje STOCH linija				SAMO cisto bullish, CROSS NEDOVOLJAN ! BULLISH_RAISING_IN_MIDDLE, OVERBOUGHT_BOTH, OVERBOUGHT_FAST		
11. Stanje SMI linija						
12. Stanje MACD						SAMO cisto bullish MACD-H, RAISING !
13. Stanje RSI linije ?
14. Stanje CCI linije ?
15. CHANNEL_POS (close)

Grupa 3: volatilitet (uzak / sirok kanal)
15. BBANDS_SQUEEZE_PERC						

Grupa 4: price action / candlestick paterns
16. BULLISH_CANDLES / BEARISH_CANDLES		
17. poslednja svecica				Mora bullish svecica 
18. polozaj MAs u kanalu			
	 */	
	@Override
	protected boolean buySignal(Instrument instrument, Period period, Filter filter, double[] ma20, double[] ma50,
			double[] ma100, double[] ma200, IBar bidBar, boolean strict, Map<String, FlexLogEntry> taValues)
			throws JFException {
		TREND_STATE trendID = taValues.get(FlexTASource.TREND_ID).getTrendStateValue();
		TechnicalSituation taSituation = taValues.get(FlexTASource.TA_SITUATION).getTehnicalSituationValue();
		MACD_H_STATE macdHState = taSituation.macdHistoState;
		STOCH_STATE stochState = taSituation.stochState;
		boolean 
			closeAboveAllMAs = taValues.get(FlexTASource.CLOSE_ABOVE_ALL_MAs).getBooleanValue(),
			ma200Highest = taValues.get(FlexTASource.MA200_HIGHEST).getBooleanValue();
		return !ma200Highest
				&& closeAboveAllMAs
				&& (trendID.equals(TREND_STATE.UP_STRONG) || trendID.equals(TREND_STATE.FRESH_UP))
				&& !taValues.get(FlexTASource.MA20_SLOPE).toString().toUpperCase().startsWith("FALLING")
				&& macdHState.toString().toUpperCase().startsWith("RAISING")
				&& (stochState.equals(STOCH_STATE.BULLISH_OVERBOUGHT_BOTH)
					|| stochState.equals(STOCH_STATE.BULLISH_OVERBOUGHT_FAST)
					|| stochState.equals(STOCH_STATE.BULLISH_RAISING_IN_MIDDLE));
	}

	@Override
	public String getName() {
		return new String("TrendSprintEarly");
	}

	@Override
	public void inTradeProcessing(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter,
			IOrder order, Map<String, FlexLogEntry> taValues, List<TAEventDesc> marketEvents) throws JFException {
		TechnicalSituation taSituation = taValues.get(FlexTASource.TA_SITUATION).getTehnicalSituationValue();
		Momentum.SMI_STATE smiState = taSituation.smiState;
		Momentum.STOCH_STATE stochState = taSituation.stochState;
		double[][] 
				mas = taValues.get(FlexTASource.MAs).getDa2DimValue(),
				bBands = taValues.get(FlexTASource.BBANDS).getDa2DimValue();
		double[]
			ma20 = new double[]{mas[0][0], mas[1][0]},
			ma50 = new double[]{mas[0][1], mas[1][1]},
			ma100 = new double[]{mas[0][2], mas[1][2]};
		double 
			ma200ma100Distance = taValues.get(FlexTASource.MA200MA100_TREND_DISTANCE_PERC).getDoubleValue();
		boolean
			narrowChannel = taValues.get(FlexTASource.BBANDS_SQUEEZE_PERC).getDoubleValue() < 30.0,
			ma200InChannel = taValues.get(FlexTASource.MA200_IN_CHANNEL).getBooleanValue();
		String masSlopes = taValues.get(FlexTASource.MA_SLOPES_SCORE).getFormattedValue();
		
		if (maxProfitExceededAvgDayRange(marketEvents)) {
			profitToProtectReached.put(instrument.name(), new Boolean(true));
			addTradeHistoryEvent(instrument, period, marketEvents, bidBar.getTime(), 0, "Trade profit exceeded avg. daily range !");
		}

		if (narrowChannel)
			return;

		IBar prevBar = this.context.getHistory().getBars(instrument, period, OfferSide.BID, Filter.WEEKENDS, 2, bidBar.getTime(), 0).get(0);
		
		if (order.isLong()) {
			boolean ma200Lowest = taValues.get(FlexTASource.MA200_LOWEST).getBooleanValue();
			// Cross of MA50 is always observed if trade profit exceeded 1 x avg. daily range ! 
			// Profit protection ! Only exception: extremely strong trend (MA200MA100 distance > 80)
			if (!extremeUpTrend(taValues)
				&& profitToProtectReached.get(instrument.name()).booleanValue()
				&& prevBar.getClose() > ma50[0] && bidBar.getClose() < ma50[1]) {
				lastTradingEvent = "SL set long to MA50 to protect profit";
				ma50TrailFlags.put(instrument.name(), new Boolean(true));
				addTradeHistoryEvent(instrument, period, marketEvents, bidBar.getTime(), bidBar.getLow(), lastTradingEvent);
				order.setStopLossPrice(bidBar.getLow());
				order.waitForUpdate(null);
				return;
			} 							
			
			// no action needed as long as there are clear strong trend:
			// MA100 below channel, MA200 lowest, MA100 not crossed
			if (ma100[1] < bBands[Volatility.BBANDS_BOTTOM][0] 
				&& ma200Lowest
				&& ma50[1] > ma100[1]
				&& bidBar.getClose() > ma100[1])
				return;
			
			// Only cross of THE CURRENTLY LOWEST MA is observed in a consolidation
			if (bidBar.getClose() < getLowestMAExceptMA200(mas)) {
				lastTradingEvent = "SL set long due to cross of the lowest MA";
				ma50TrailFlags.put(instrument.name(), new Boolean(true));
				addTradeHistoryEvent(instrument, period, marketEvents, bidBar.getTime(), bidBar.getLow(), lastTradingEvent);
				StopLoss.setCloserOnlyStopLoss(order, bidBar.getLow(), bidBar.getTime(), this.getClass());
				return;
			} 
		} else {
			// short
			if (!extremeDownTrend(taValues)
				&& profitToProtectReached.get(instrument.name()).booleanValue()
				&& prevBar.getClose() < ma50[0] && bidBar.getClose() > ma50[1]) {
				lastTradingEvent = "SL set short to MA50  to protect profit";
				ma50TrailFlags.put(instrument.name(), new Boolean(true));
				addTradeHistoryEvent(instrument, period, marketEvents, bidBar.getTime(), bidBar.getHigh(), lastTradingEvent);
				order.setStopLossPrice(bidBar.getHigh());
				order.waitForUpdate(null);
				return;
			}
			
			boolean ma200Highest = taValues.get(FlexTASource.MA200_HIGHEST).getBooleanValue();
			if (ma100[1] > bBands[Volatility.BBANDS_TOP][0] 
				&& ma200Highest
				&& ma50[1] < ma100[1]
				&& bidBar.getClose() < ma100[1])
				return;
			
			if (bidBar.getClose() > getHighestMAExceptMA200(mas)) {
				lastTradingEvent = "SL set short due to cross of the highest MA";
				ma50TrailFlags.put(instrument.name(), new Boolean(true));
				addTradeHistoryEvent(instrument, period, marketEvents, bidBar.getTime(), bidBar.getHigh(), lastTradingEvent);
				StopLoss.setCloserOnlyStopLoss(order, bidBar.getHigh(), bidBar.getTime(), this.getClass());
				return;
			}
		} 
	}

	@Override
	public EntryDirection checkTakeOver(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter,
			Map<String, FlexLogEntry> taValues) throws JFException {
		return EntryDirection.NONE;
	}

	@Override
	public boolean isTradeLocked(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter,
			IOrder order, Map<String, FlexLogEntry> taValues) throws JFException {
		return false;
	}

}
