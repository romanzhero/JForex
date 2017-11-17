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
import jforex.techanalysis.Momentum;
import jforex.techanalysis.Momentum.SMI_STATE;
import jforex.techanalysis.Momentum.STOCH_STATE;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Trend.TREND_STATE;
import jforex.techanalysis.Volatility;
import jforex.techanalysis.source.FlexTASource;
import jforex.techanalysis.source.TechnicalSituation;
import jforex.utils.FXUtils;
import jforex.utils.StopLoss;
import jforex.utils.log.FlexLogEntry;

public class TrendSprint extends AbstractSmaTradeSetup {

	public TrendSprint(IEngine engine, IContext context, Set<Instrument> subscribedInstruments, boolean mktEntry,
			boolean onlyCross, double pFlatPercThreshold, double pBBandsSqueezeThreshold, boolean trailsOnMA50) {
		super(engine, context, subscribedInstruments, mktEntry, onlyCross, pFlatPercThreshold, pBBandsSqueezeThreshold,	trailsOnMA50);
		// TODO Auto-generated constructor stub
	}

	public TrendSprint(IEngine engine, IContext context, Set<Instrument> subscribedInstruments, boolean mktEntry,
			boolean onlyCross, double pFlatPercThreshold, double pBBandsSqueezeThreshold, boolean trailsOnMA50,
			boolean takeOverOnly) {
		super(engine, context, subscribedInstruments, mktEntry, onlyCross, pFlatPercThreshold, pBBandsSqueezeThreshold,	trailsOnMA50, takeOverOnly);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected boolean sellSignal(Instrument instrument, Period period, Filter filter, double[] ma20, double[] ma50, double[] ma100, double[] ma200, 
			IBar bidBar, boolean strict, Map<String, FlexLogEntry> taValues)
			throws JFException {
		if(taValues.get(FlexTASource.MA200_LOWEST).getBooleanValue()
			|| taValues.get(FlexTASource.BBANDS_SQUEEZE_PERC).getDoubleValue() < 30.0)
			return false;
		
		boolean 
			ma200_highest = taValues.get(FlexTASource.MA200_HIGHEST).getBooleanValue(),
			ma200_in_channel = taValues.get(FlexTASource.MA200_IN_CHANNEL).getBooleanValue();
		if (ma200_in_channel && !ma200_highest)
			return false;
		
		if (bidBar.getClose() > bidBar.getOpen())
			return false;
		
		TechnicalSituation taSituation = taValues.get(FlexTASource.TA_SITUATION).getTehnicalSituationValue();
		if (taSituation.smiState.equals(SMI_STATE.BULLISH_BOTH_RAISING_IN_MIDDLE) && 
			(taSituation.stochState.equals(STOCH_STATE.BULLISH_RAISING_IN_MIDDLE) || taSituation.stochState.equals(STOCH_STATE.BULLISH_CROSS)))
			return false;
		
		TREND_STATE trend = taValues.get(FlexTASource.TREND_ID).getTrendStateValue();
		if ((trend.equals(Trend.TREND_STATE.DOWN_STRONG) || trend.equals(Trend.TREND_STATE.FRESH_DOWN))
			&& bidBar.getClose() < ma20[1] && bidBar.getClose() < ma50[1] && bidBar.getClose() < ma100[1] && bidBar.getClose() < ma200[1])
			return true;
		
		return false;	}
	
	/* 
 Kriterijumi za POCETAK long perioda (isto kao kasnije za ulazak u trejd):
 Grupa 1: trend ili flat
1. TREND_ID									4 ili 6 (MA20 najvislji)
2. MAs_DISTANCE_PERC						-
3. MA200_HIGHEST							ne sme
4. MA200_LOWEST								MORA samo ako je MA200_IN_CHANNEL 
5. MA200_IN_CHANNEL							TRUE i NIJE MA200_LOWEST blokira ulazak u trejd. Trebalo bi time da se isfiltrira flat rezim  
6. MA200MA100_TREND_DISTANCE_PERC			-
7. FLAT_REGIME polozaj MAs u kanalu			-
8. ICHI										mozda da li je svecica van oblaka ?

Grupa 2: momentum i OS/OB
9. Stanje STOCH linija						ne sme nijedan cisto BEARISH
10. Stanje SMI linija						ne sme nijedan cisto BEARISH
11. Stanje RSI linije ?
12. Stanje CCI linije ?
13. CHANNEL_POS (close)

Grupa 3: volatilitet (uzak / sirok kanal)
14. BBANDS_SQUEEZE_PERC						-

Grupa 4: price action / candlestick paterns
15. BULLISH_CANDLES / BEARISH_CANDLES		NIJE bearish sa jasno bearish ukupnim telom
16. poslednja svecica						16.1. IZNAD svih MA  
											(MOZDA CROSS IZNAD svih MA kao (pred)uslov. Prati se flag-om i resetuje kad uslov vise nije ostvaren - PROVERITI)
											16.2. mora biti ili cisto bulish ili 1-bar bullish reversal 
	 */
	@Override
	protected boolean buySignal(Instrument instrument, Period period, Filter filter, 
			double[] ma20, double[] ma50, double[] ma100, double[] ma200, 
			IBar bidBar, 
			boolean strict, 
			Map<String, FlexLogEntry> taValues)
			throws JFException {
		if(taValues.get(FlexTASource.MA200_HIGHEST).getBooleanValue()
			|| taValues.get(FlexTASource.BBANDS_SQUEEZE_PERC).getDoubleValue() < 30.0)
			return false;
		
		boolean 
			ma200_lowest = taValues.get(FlexTASource.MA200_LOWEST).getBooleanValue(),
			ma200_in_channel = taValues.get(FlexTASource.MA200_IN_CHANNEL).getBooleanValue();
		if (ma200_in_channel && !ma200_lowest)
			return false;
		
		if (bidBar.getClose() < bidBar.getOpen())
			return false;

		
/*		public enum STOCH_STATE {
			BEARISH_OVERSOLD_BOTH, // Fast and Slow both below 20
			BEARISH_OVERSOLD_FAST, // Fast below 20
			BULLISH_WEAK_OVERSOLD_SLOW, // Slow below 20 but Fast not, rather bullish (raising from OS)
			BULLISH_OVERBOUGHT_BOTH, // Fast and Slow both above 80
			BULLISH_OVERBOUGHT_FAST, // Fast above 80
			BEARISH_WEAK_OVERBOUGHT_SLOW, // Slow above 80 but Fast not, rather bearish (falling from OB)
			BULLISH_RAISING_IN_MIDDLE, // Fast above slow and none OS nor OB
			BEARISH_FALLING_IN_MIDDLE, // Fast below slow and none OS nor OB
			BULLISH_CROSS_FROM_OVERSOLD, // at least one line below 20
			BULLISH_CROSS, 
			BEARISH_CROSS_FROM_OVERBOUGTH, // at least one line above 80
			BEARISH_CROSS, 
			NONE, OTHER
		}
		
		public enum SMI_STATE {
			BEARISH_OVERSOLD_BOTH, // Fast and Slow both below -60
			BEARISH_OVERSOLD_FAST_BELOW_FALLING_SLOW, // Fast below -60
			BULLISH_WEAK_OVERSOLD_SLOW_BELOW_RAISING_FAST, // Slow below -60 but Fast not, rather bullish (raising from OS)
			BULLISH_WEAK_OVERSOLD_SLOW_BELOW_FAST, // Slow below -60 but Fast not, however not raising
			BULLISH_OVERBOUGHT_BOTH, // Fast and Slow both above +60
			BULLISH_OVERBOUGHT_FAST_ABOVE_RAISING_SLOW, // Fast above +60
			BEARISH_WEAK_OVERBOUGHT_SLOW_ABOVE_FALLING_FAST, // Slow above 80 but Fast not, rather bearish (falling OB)
			BEARISH_WEAK_OVERBOUGHT_SLOW_ABOVE_FAST, // Slow above 80 but Fast not, however not falling
			BULLISH_BOTH_RAISING_IN_MIDDLE, // Fast above slow and none OS nor OB
			BEARISH_BOTH_FALLING_IN_MIDDLE, // Fast below slow and none OS nor OB
			BULLISH_WEAK_RAISING_IN_MIDDLE,
			BEARISH_WEAK_FALLING_IN_MIDDLE,
			OTHER // neither of these clear cases, when lines ticked up/down etc
		}
*/		
		TechnicalSituation taSituation = taValues.get(FlexTASource.TA_SITUATION).getTehnicalSituationValue();
		if (taSituation.smiState.equals(SMI_STATE.BEARISH_BOTH_FALLING_IN_MIDDLE) && 
			(taSituation.stochState.equals(STOCH_STATE.BEARISH_FALLING_IN_MIDDLE) || taSituation.stochState.equals(STOCH_STATE.BEARISH_CROSS)))
			return false;
		
		TREND_STATE trend = taValues.get(FlexTASource.TREND_ID).getTrendStateValue();
		if ((trend.equals(Trend.TREND_STATE.UP_STRONG) || trend.equals(Trend.TREND_STATE.FRESH_UP))
			&& bidBar.getClose() > ma20[1] && bidBar.getClose() > ma50[1] && bidBar.getClose() > ma100[1] && bidBar.getClose() > ma200[1])
			return true;
		
		return false;
	}

	/* 
 Kriterijumi za IZLAZ iz LONG trejda:
 Grupa 1: trend ili flat
1. TREND_ID									-											
2. MAs_DISTANCE_PERC						-
3. MA200_HIGHEST							-
4. MA200_LOWEST								- 
5. MA200_IN_CHANNEL							-  
6. MA200MA100_TREND_DISTANCE_PERC			-
7. FLAT_REGIME polozaj MAs u kanalu			-
8. ICHI										-

Grupa 2: momentum i OS/OB
9. Stanje STOCH linija						-
10. Stanje SMI linija						-
11. Stanje RSI linije ?
12. Stanje CCI linije ?
13. CHANNEL_POS (close)

Grupa 3: volatilitet (uzak / sirok kanal)
14. BBANDS_SQUEEZE_PERC						-

Grupa 4: price action / candlestick paterns
15. BULLISH_CANDLES / BEARISH_CANDLES		-
16. poslednja svecica						1. nakon ulaza se ne postavlja SL !!!
  											2. SL se postavlja SAMO JEDNOM i vise se NE dira (nema trejling !) kada:
  												2.1 poslednja svecica ZATVORI ispod MA50 a MA100 je UNUTAR kanala
  												2.2 ako ne 2.1, onda kada poslednja svecica ZATVORI ispod MA100 
  											3. fleg se RESETUJE (dozvoljeno je menjati SL) kada za vreme setovanog flega poslednja svecica ponovo zatvori 
  											IZNDAD svih MA (alternativa: kada low bude iznad i MA50 i MA100)
											 
	 */

	@Override
	public void inTradeProcessing(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter, IOrder order, Map<String, FlexLogEntry> taValues, List<TAEventDesc> marketEvents) throws JFException {
		// set SL to breakeven in case of clear opposite momentum
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
		if (order.isLong()) {
			if (bidBar.getClose() < ma20[1] && bidBar.getLow() < ma50[1]
				&& (smiState.toString().startsWith("BEARISH") && !smiState.toString().startsWith("BEARISH_WEAK"))
				&& (stochState.toString().startsWith("BEARISH") && !stochState.toString().startsWith("BEARISH_WEAK"))) {
				lastTradingEvent = "Set to breakeven due to bearish momentum";
				// this can close the order in the worst case ! If so exit the method !
				if (!StopLoss.setBreakEvenSituative(order, bidBar))
					return;
			}
		}
		else {
			if (askBar.getClose() > ma20[1] && askBar.getHigh() > ma50[1]
				&& (smiState.toString().startsWith("BULLISH") && !smiState.toString().startsWith("BULLISH_WEAK"))
				&& (stochState.toString().startsWith("BULLISH") && !stochState.toString().startsWith("BULLISH_WEAK"))) {
				lastTradingEvent = "Set to breakeven due to bullish momentum";
				// this can close the order in the worst case ! If so exit the method !
				if (!StopLoss.setBreakEvenSituative(order, askBar));
					return;
			}
			
		}
		double 
			ma200ma100Distance = taValues.get(FlexTASource.MA200MA100_TREND_DISTANCE_PERC).getDoubleValue(),
			ma200 = mas[1][3];
		boolean 
			ma200InChannel = taValues.get(FlexTASource.MA200_IN_CHANNEL).getBooleanValue();
		String masSlopes = taValues.get(FlexTASource.MA_SLOPES_SCORE).getFormattedValue();
		
		boolean stopLossSet = ma50TrailFlags.get(instrument.name()).booleanValue();
		IBar prevBar = this.context.getHistory().getBars(instrument, period, OfferSide.BID, Filter.WEEKENDS, 2, bidBar.getTime(), 0).get(0);
		
		if (!stopLossSet) {
			if (order.isLong()) {
				boolean ma200Lowest = taValues.get(FlexTASource.MA200_LOWEST).getBooleanValue();
				// NO cross of MA100 is observed in a very strong trend (MA200 outside of channel and decent distance MA200 - MA100)
				// AND a consolidation (MA100 in channel). In that case MA200 is SL
				if (!ma200InChannel 
					&& ma200Lowest
					&& ma200ma100Distance > 30 
					&& ma100[1] >= bBands[Volatility.BBANDS_BOTTOM][0]) {
					lastTradingEvent = "SL set to MA200 long";
					FXUtils.setStopLoss(order, ma200, bidBar.getTime(), this.getClass());
					order.setStopLossPrice(ma200);
					order.waitForUpdate(null);				
					return;
				}
				// BUT what happens if in the meantime price ALREADY CROSSED MA100 / MA50 ??
				// Either let SL at MA200 be hit or close the trade !
				
				// Cross of MA100 is observed in stronger trend, when MA100 is below channel or at least above MA50 
				// (i.e. TrendID in (5, 6, but also fresh downtrend !)
				if (ma100[1] < bBands[Volatility.BBANDS_BOTTOM][0]
					|| ma100[1] < ma50[1]) {
					if (prevBar.getClose() > ma100[0] && bidBar.getClose() < ma100[1]) {
						lastTradingEvent = "SL set long";
						ma50TrailFlags.put(instrument.name(), new Boolean(true));
						order.setStopLossPrice(bidBar.getLow());
						order.waitForUpdate(null);
					}					
				} else {
					// Cross of MA50 is observed in a consolidation, when MA100 is in channel
					// and MA50 is ABOVE MA100 (TrendID in 1, 2 or fresh uptrend)
					if (prevBar.getClose() > ma50[0] && bidBar.getClose() < ma50[1]) {
						lastTradingEvent = "SL set long";
						ma50TrailFlags.put(instrument.name(), new Boolean(true));
						order.setStopLossPrice(bidBar.getLow());
						order.waitForUpdate(null);						
					}
				}					
			} else {
				boolean ma200Highest = taValues.get(FlexTASource.MA200_HIGHEST).getBooleanValue();
				if (!ma200InChannel 
					&& ma200Highest
					&& ma200ma100Distance > 30 
					&& ma100[1] <= bBands[Volatility.BBANDS_TOP][0]) {
					lastTradingEvent = "SL set to MA200 long";
					FXUtils.setStopLoss(order, ma200, bidBar.getTime(), this.getClass());
					order.setStopLossPrice(ma200);
					order.waitForUpdate(null);					
					return;
				}
				if (ma100[1] > bBands[Volatility.BBANDS_TOP][0]
					|| ma100[1] > ma50[1]) {
					if (prevBar.getClose() < ma100[0] && bidBar.getClose() > ma100[1]) {
						lastTradingEvent = "SL set short";
						ma50TrailFlags.put(instrument.name(), new Boolean(true));
						order.setStopLossPrice(bidBar.getHigh());
						order.waitForUpdate(null);
					}					
				} else {
					if (prevBar.getClose() < ma50[0] && bidBar.getClose() > ma50[1]) {
						lastTradingEvent = "SL set short";
						ma50TrailFlags.put(instrument.name(), new Boolean(true));
						order.setStopLossPrice(bidBar.getHigh());
						order.waitForUpdate(null);						
					}
				}					
			}
		} else {
			// SL was set once. Reset the flag so it can be set again on the next cross if price closed again above / below all MAs
			if (order.isLong()) {
				if (bidBar.getClose() > ma20[1] && bidBar.getClose() > ma50[1] && bidBar.getClose() > ma100[1]) {
					lastTradingEvent = "SL reset long";
					ma50TrailFlags.put(instrument.name(), new Boolean(false));
				}
			} else {
				if (bidBar.getClose() < ma20[1] && bidBar.getClose() < ma50[1] && bidBar.getClose() < ma100[1]) {
					lastTradingEvent = "SL reset short";
					ma50TrailFlags.put(instrument.name(), new Boolean(false));				
				}
			}
		}
	}	
	
	@Override
	public String getName() {
		return new String("TrendSprint");
	}

	@Override
	public TAEventDesc checkEntry(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter, Map<String, FlexLogEntry> taValues) throws JFException {
		TAEventDesc eventDesc = super.checkEntry(instrument, period, askBar, bidBar, filter, taValues);
		lastSL = -1.0; // no SL set at open
		return eventDesc ;
	}

	@Override
	public void afterTradeReset(Instrument instrument) {
		// TODO Auto-generated method stub
		super.afterTradeReset(instrument);
	}

}