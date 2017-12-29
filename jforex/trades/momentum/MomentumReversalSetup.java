package jforex.trades.momentum;

import java.util.List;
import java.util.Map;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

import jforex.events.TAEventDesc;
import jforex.events.TAEventDesc.TAEventType;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.Trend.FLAT_REGIME_CAUSE;
import jforex.techanalysis.Trend.TREND_STATE;
import jforex.techanalysis.source.FlexTASource;
import jforex.techanalysis.source.TechnicalSituation;
import jforex.trades.ITradeSetup;
import jforex.trades.TradeSetup;
import jforex.trades.flat.FlatTradeSetup;
import jforex.trades.trend.TrendSprint;
import jforex.utils.StopLoss;
import jforex.utils.log.FlexLogEntry;

public class MomentumReversalSetup extends TradeSetup implements ITradeSetup {
	public static String SETUP_NAME = "MomentumReversal";

	public MomentumReversalSetup(IEngine engine, IContext context) {
		super(engine, context);
	}

	public MomentumReversalSetup(IEngine engine, IContext context, boolean pTakeOverOnly) {
		super(engine, context, pTakeOverOnly);
	}

	@Override
	public String getName() {
		return new String(SETUP_NAME);
	}
	/* 
 Kriterijumi za ulazak u LONG trejd:
 Grupa 1: trend ili flat
1. TREND_ID							
2. MAs_DISTANCE_PERC						
3. MA200_HIGHEST						
4. MA200_LOWEST							
5. MA200_IN_CHANNEL						
6. MA200MA100_TREND_DISTANCE_PERC			
7. FLAT_REGIME polozaj MAs u kanalu			
8. ICHI							
9. MAs SLOPE

Grupa 2: momentum i OS/OB
10. Stanje STOCH linija		- samo strogo bulish stanja						
11. Stanje SMI linija		- samo strogo bulish stanja				
12. Stanje RSI linije ?
13. Stanje CCI linije ?
14. CHANNEL_POS (close)

Grupa 3: volatilitet (uzak / sirok kanal)
15. BBANDS_SQUEEZE_PERC		- kanal se mora siriti i biti iznad 30 perc (ne suvise uzak) !						

Grupa 4: price action / candlestick paterns
16. BULLISH_CANDLES / BEARISH_CANDLES		
17. poslednja svecica		- poslednja svecica mora biti:
								- close iznad ivice kanala
								- close IZNAD SVIH MAs !!!
								- bullish telo
							- prethodna ili minimalno ona pre nje moraju biti sa close koji NIJE iznad svih MA 
							--> cross preko najvisljeg MA ! 
	 */
	@Override
	public TAEventDesc checkEntry(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter, Map<String, FlexLogEntry> taValues) throws JFException {
		TREND_STATE entryTrendID = taValues.get(FlexTASource.TREND_ID).getTrendStateValue();
		double 
			maDistance = taValues.get(FlexTASource.MAs_DISTANCE_PERC).getDoubleValue(),
			bBandsSqueeze = taValues.get(FlexTASource.BBANDS_SQUEEZE_PERC).getDoubleValue(),
			ma200ma100distance = taValues.get(FlexTASource.MA200MA100_TREND_DISTANCE_PERC).getDoubleValue(),
			chPos = taValues.get(FlexTASource.CHANNEL_POS).getDoubleValue();
		FLAT_REGIME_CAUSE isFlat = (FLAT_REGIME_CAUSE)taValues.get(FlexTASource.FLAT_REGIME).getValue();
		boolean 
			ma200Highest = taValues.get(FlexTASource.MA200_HIGHEST).getBooleanValue(), 
			ma200Lowest = taValues.get(FlexTASource.MA200_LOWEST).getBooleanValue(),
			ma200InChannel = taValues.get(FlexTASource.MA200_IN_CHANNEL).getBooleanValue(),
			closeAboveAllMAs = taValues.get(FlexTASource.CLOSE_ABOVE_ALL_MAs).getBooleanValue(),
			closeBelowAllMAs = taValues.get(FlexTASource.CLOSE_BELOW_ALL_MAs).getBooleanValue();
		double[][] smis = taValues.get(FlexTASource.SMI).getDa2DimValue();
		double
			fastSMI = smis[0][2],
			slowSMI = smis[1][2];
		Momentum.SINGLE_LINE_STATE
			ma20Slope = (Momentum.SINGLE_LINE_STATE)taValues.get(FlexTASource.MA20_SLOPE).getValue(),
			ma50Slope = (Momentum.SINGLE_LINE_STATE)taValues.get(FlexTASource.MA50_SLOPE).getValue(),
			ma100Slope = (Momentum.SINGLE_LINE_STATE)taValues.get(FlexTASource.MA100_SLOPE).getValue(),
			ma200Slope = (Momentum.SINGLE_LINE_STATE)taValues.get(FlexTASource.MA200_SLOPE).getValue(),
			channelWidthDirection = (Momentum.SINGLE_LINE_STATE)taValues.get(FlexTASource.CHANNEL_WIDTH_DIRECTION).getValue();
		String maSlopesScore = taValues.get(FlexTASource.MA_SLOPES_SCORE).getFormattedValue();
		
		if (bBandsSqueeze < 30)
			return null;
		
		TechnicalSituation taSituation = taValues.get(FlexTASource.TA_SITUATION).getTehnicalSituationValue();
					
		double[][] mas = taValues.get(FlexTASource.MAs).getDa2DimValue();
		boolean 
			bullishMomentum = !(ma200Lowest && !ma200InChannel)
							&& chPos > 100 
							&& !channelWidthDirection.equals(Momentum.SINGLE_LINE_STATE.FALLING_IN_MIDDLE)
							&& (taSituation.smiState.equals(Momentum.SMI_STATE.BULLISH_BOTH_RAISING_IN_MIDDLE)
							|| taSituation.smiState.equals(Momentum.SMI_STATE.BULLISH_OVERBOUGHT_BOTH)
							|| taSituation.smiState.equals(Momentum.SMI_STATE.BULLISH_OVERBOUGHT_FAST_ABOVE_RAISING_SLOW)
							|| (taSituation.smiState.equals(Momentum.SMI_STATE.BULLISH_WEAK_RAISING_IN_MIDDLE) && slowSMI > 0))
							&& taSituation.stochState.toString().startsWith("BULLISH"),
			bearishMomentum = !(ma200Highest && !ma200InChannel) 
							&& chPos < 0 
							&& !channelWidthDirection.equals(Momentum.SINGLE_LINE_STATE.FALLING_IN_MIDDLE)
							&& (taSituation.smiState.equals(Momentum.SMI_STATE.BEARISH_BOTH_FALLING_IN_MIDDLE)
							|| taSituation.smiState.equals(Momentum.SMI_STATE.BEARISH_OVERSOLD_BOTH)
							|| taSituation.smiState.equals(Momentum.SMI_STATE.BEARISH_OVERSOLD_FAST_BELOW_FALLING_SLOW)
							|| (taSituation.smiState.equals(Momentum.SMI_STATE.BEARISH_WEAK_FALLING_IN_MIDDLE) && slowSMI < 0))
							&& taSituation.stochState.toString().startsWith("BEARISH");
		
			if (!bullishMomentum && ! bearishMomentum)
				return null;

			
			if (bullishMomentum) {
				double[] 
						mas20 = context.getIndicators().sma(instrument, period, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 20, filter, 
								3, bidBar.getTime(), 0),
						mas50 = context.getIndicators().sma(instrument, period, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 50, filter, 
								3, bidBar.getTime(), 0),
						mas100 = context.getIndicators().sma(instrument, period, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 100, filter, 
								3, bidBar.getTime(), 0),
						mas200 = context.getIndicators().sma(instrument, period, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 200, filter, 
								3, bidBar.getTime(), 0);
					List<IBar> last3AskBars = context.getHistory().getBars(instrument, period, OfferSide.ASK, filter, 3, bidBar.getTime(), 0);
				boolean 
					previousBarCloseNotAboveAllMAs = !(last3AskBars.get(1).getClose() > mas20[1]
														&& last3AskBars.get(1).getClose() > mas50[1]
														&& last3AskBars.get(1).getClose() > mas100[1]
														&& last3AskBars.get(1).getClose() > mas200[1]), 
					prePreviousBarCloseNotAboveAllMAs = !(last3AskBars.get(0).getClose() > mas20[0]
													&& last3AskBars.get(0).getClose() > mas50[0]
													&& last3AskBars.get(0).getClose() > mas100[0]
													&& last3AskBars.get(0).getClose() > mas200[0]); 
				if (closeAboveAllMAs 
					&& (previousBarCloseNotAboveAllMAs || prePreviousBarCloseNotAboveAllMAs)) {
					return new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, true, askBar, bidBar, period);
				} else 
					return null;				
			} else if (bearishMomentum) {
				double[] 
						mas20 = context.getIndicators().sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 20, filter, 
								3, bidBar.getTime(), 0),
						mas50 = context.getIndicators().sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 50, filter, 
								3, bidBar.getTime(), 0),
						mas100 = context.getIndicators().sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 100, filter, 
								3, bidBar.getTime(), 0),
						mas200 = context.getIndicators().sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 200, filter, 
								3, bidBar.getTime(), 0);
					List<IBar> last3BidBars = context.getHistory().getBars(instrument, period, OfferSide.BID, filter, 3, bidBar.getTime(), 0);
					boolean 
					previousBarCloseNotBelowAllMAs = !(last3BidBars.get(1).getClose() < mas20[1]
														&& last3BidBars.get(1).getClose() < mas50[1]
														&& last3BidBars.get(1).getClose() < mas100[1]
														&& last3BidBars.get(1).getClose() < mas200[1]), 
					prePreviousBarCloseNotBelowAllMAs = !(last3BidBars.get(0).getClose() < mas20[0]
													&& last3BidBars.get(0).getClose() < mas50[0]
													&& last3BidBars.get(0).getClose() < mas100[0]
													&& last3BidBars.get(0).getClose() < mas200[0]); 
				if (closeBelowAllMAs 
					&& (previousBarCloseNotBelowAllMAs || prePreviousBarCloseNotBelowAllMAs)) {
					return new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, false, askBar, bidBar, period);
				} else 
					return null;				
				
			}
			return null;
	}

	// SL ce se postavljati tek pri secenju najudaljenijeg MA
	@Override
	public IOrder submitOrder(String label, Instrument instrument, boolean isLong, double amount, IBar bidBar, IBar askBar) throws JFException {
		return super.submitMktOrder(label, instrument, isLong, amount, bidBar, askBar);
	}

	/* 
	Dogadjaji i reakcije:
	- suprotni signal istog setup-a: zatvara se trejd
	- suprotni signal FlatTrade: SL na breakeven ako je trejd profitabilan, inace zatvarati !
	- suprotni signal TrendSprint: zatvara se trejd

	Reakcije na ostale dogadjaje:
	Grupa 1: trend ili flat
	1. TREND_ID							
	2. MAs_DISTANCE_PERC						
	3. MA200_HIGHEST						
	4. MA200_LOWEST							
	5. MA200_IN_CHANNEL						
	6. MA200MA100_TREND_DISTANCE_PERC			
	7. FLAT_REGIME polozaj MAs u kanalu			
	8. ICHI							
	9. MAs SLOPE

	Grupa 2: momentum i OS/OB
	10. Stanje STOCH linija						
	11. Stanje SMI linija						
	12. Stanje RSI linije ?
	13. Stanje CCI linije ?
	14. CHANNEL_POS (close)

	Grupa 3: volatilitet (uzak / sirok kanal)
	15. BBANDS_SQUEEZE_PERC						

	Grupa 4: price action / candlestick paterns
	16. BULLISH_CANDLES / BEARISH_CANDLES		
	17. poslednja svecica: presecanje najnizeg MA postavlja SL na dno te svecice 						
	 */
	@Override
	public void inTradeProcessing(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter,
			IOrder order, Map<String, FlexLogEntry> taValues, List<TAEventDesc> marketEvents) throws JFException {
		TREND_STATE entryTrendID = taValues.get(FlexTASource.TREND_ID).getTrendStateValue();
		TAEventDesc
			flatEntry = findTAEvent(marketEvents, TAEventType.ENTRY_SIGNAL, FlatTradeSetup.SETUP_NAME, instrument, period),
			trendSprintEntry = findTAEvent(marketEvents, TAEventType.ENTRY_SIGNAL, TrendSprint.SETUP_NAME, instrument, period),
			momentumReversalEntry = findTAEvent(marketEvents, TAEventType.ENTRY_SIGNAL, MomentumReversalSetup.SETUP_NAME, instrument, period);
	
		if (order.isLong()) {
			double[] 
					mas20 = context.getIndicators().sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 20, filter, 
							1, bidBar.getTime(), 0),
					mas50 = context.getIndicators().sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 50, filter, 
							1, bidBar.getTime(), 0),
					mas100 = context.getIndicators().sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 100, filter, 
							1, bidBar.getTime(), 0);
			
			if (FlexTASource.solidBearishMomentum(taValues) && bidBar.getClose() < mas20[0]) {
				if (order.getStopLossPrice() <= 0) {
					order.setStopLossPrice(bidBar.getLow());
					order.waitForUpdate(null);
					return;
				} else if (bidBar.getLow() > order.getStopLossPrice()) {
					order.setStopLossPrice(bidBar.getLow());
					order.waitForUpdate(null);
					return;
				}
			}

			if ((flatEntry != null && !flatEntry.isLong && order.getProfitLossInPips() > 0)
				|| FlexTASource.solidBearishMomentum(taValues)) {
				lastTradingEvent = "breakeven set due to opposite flat signal or momentum";
				StopLoss.setBreakEvenSituative(order, bidBar);
				return;
			}
			if ((flatEntry != null && !flatEntry.isLong && order.getProfitLossInPips() <= 0)
				|| (trendSprintEntry != null && !trendSprintEntry.isLong)
				|| (momentumReversalEntry != null && !momentumReversalEntry.isLong)) {
				lastTradingEvent = "closed due to opposite signals";
				order.close();
				order.waitForUpdate(null);
				return;
			}
			
			double lowestMAValue = -1;
			if (entryTrendID.equals(TREND_STATE.UP_STRONG) || entryTrendID.equals(TREND_STATE.UP_MILD))
				lowestMAValue = mas100[0];
			else if (entryTrendID.equals(TREND_STATE.FRESH_DOWN) || entryTrendID.equals(TREND_STATE.DOWN_STRONG))
				lowestMAValue = mas20[0];
			else
				lowestMAValue = mas50[0];
			if (bidBar.getClose() < lowestMAValue) {
				if (order.getStopLossPrice() == 0) {
					lastTradingEvent = "MomentumReversal SL set (1st)"; 					
					order.setStopLossPrice(bidBar.getLow());
					order.waitForUpdate(null);
				} else if (bidBar.getLow() > order.getStopLossPrice()) {
					lastTradingEvent = "MomentumReversal SL set"; 					
					order.setStopLossPrice(bidBar.getLow());
					order.waitForUpdate(null);					
				}
			}

		} else {
			// short
			double[] 
					mas20 = context.getIndicators().sma(instrument, period, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 20, filter, 
							1, bidBar.getTime(), 0),
					mas50 = context.getIndicators().sma(instrument, period, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 50, filter, 
							1, bidBar.getTime(), 0),
					mas100 = context.getIndicators().sma(instrument, period, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 100, filter, 
							1, bidBar.getTime(), 0);
			
			if (FlexTASource.solidBullishMomentum(taValues) && askBar.getClose() > mas20[0]) {
				if (order.getStopLossPrice() <= 0) {
					order.setStopLossPrice(askBar.getHigh());
					order.waitForUpdate(null);
					return;
				} else if (askBar.getHigh() < order.getStopLossPrice()) {
					order.setStopLossPrice(askBar.getHigh());
					order.waitForUpdate(null);
					return;
				}
			}
			
			if ((flatEntry != null && flatEntry.isLong && order.getProfitLossInPips() > 0)
				|| FlexTASource.solidBullishMomentum(taValues)) {
				lastTradingEvent = "breakeven set due to opposite flat signal or momentum";
				StopLoss.setBreakEvenSituative(order, bidBar);
				return;
			}
			if ((flatEntry != null && flatEntry.isLong && order.getProfitLossInPips() <= 0)
				|| (trendSprintEntry != null && trendSprintEntry.isLong)
				|| (momentumReversalEntry != null && momentumReversalEntry.isLong)) {
				lastTradingEvent = "closed due to opposite signals";
				order.close();
				order.waitForUpdate(null);
				return;
			}

			double highestMAValue = -1;
			if (entryTrendID.equals(TREND_STATE.DOWN_STRONG) || entryTrendID.equals(TREND_STATE.DOWN_MILD))
				highestMAValue = mas100[0];
			else if (entryTrendID.equals(TREND_STATE.FRESH_UP) || entryTrendID.equals(TREND_STATE.UP_STRONG))
				highestMAValue = mas20[0];
			else
				highestMAValue = mas50[0];
			if (askBar.getClose() > highestMAValue) {
				if (order.getStopLossPrice() == 0) {
					lastTradingEvent = "MomentumReversal SL set (1st)"; 					
					order.setStopLossPrice(askBar.getHigh());
					order.waitForUpdate(null);
				} else if (bidBar.getHigh() < order.getStopLossPrice()) {
					lastTradingEvent = "MomentumReversal SL set"; 					
					order.setStopLossPrice(askBar.getHigh());
					order.waitForUpdate(null);					
				}
			}
		}
	}

}
