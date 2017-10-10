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
import jforex.techanalysis.Trend;
import jforex.techanalysis.Trend.TREND_STATE;
import jforex.techanalysis.Volatility;
import jforex.techanalysis.source.FlexTASource;
import jforex.techanalysis.source.FlexTAValue;
import jforex.techanalysis.source.TechnicalSituation;

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
			IBar bidBar, boolean strict, Map<String, FlexTAValue> taValues)
			throws JFException {
		if(taValues.get(FlexTASource.MA200_LOWEST).getBooleanValue())
			return false;
		
		boolean 
			ma200_highest = taValues.get(FlexTASource.MA200_HIGHEST).getBooleanValue(),
			ma200_in_channel = taValues.get(FlexTASource.MA200_IN_CHANNEL).getBooleanValue();
		if (ma200_in_channel && !ma200_highest)
			return false;
		
		if (bidBar.getClose() > bidBar.getOpen())
			return false;
		
/*		TechnicalSituation taSituation = taValues.get(FlexTASource.TA_SITUATION).getTehnicalSituationValue();
		if (taSituation.stochState.toString().startsWith("BULLISH") && !taSituation.stochState.toString().startsWith("BULLISH_WEAK"))
			return false;
*/		
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
			Map<String, FlexTAValue> taValues)
			throws JFException {
		if(taValues.get(FlexTASource.MA200_HIGHEST).getBooleanValue())
			return false;
		
		boolean 
			ma200_lowest = taValues.get(FlexTASource.MA200_LOWEST).getBooleanValue(),
			ma200_in_channel = taValues.get(FlexTASource.MA200_IN_CHANNEL).getBooleanValue();
		if (ma200_in_channel && !ma200_lowest)
			return false;
		
		if (bidBar.getClose() < bidBar.getOpen())
			return false;
		
/*		TechnicalSituation taSituation = taValues.get(FlexTASource.TA_SITUATION).getTehnicalSituationValue();
		if (taSituation.stochState.toString().startsWith("BEARISH") && !taSituation.stochState.toString().startsWith("BEARISH_WEAK"))
			return false;*/
		
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
	public void inTradeProcessing(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter, IOrder order, Map<String, FlexTAValue> taValues, List<TAEventDesc> marketEvents) throws JFException {
		boolean stopLossSet = ma50TrailFlags.get(instrument.name()).booleanValue();
		double[][] 
				mas = taValues.get(FlexTASource.MAs).getDa2DimValue(),
				bBands = taValues.get(FlexTASource.BBANDS).getDa2DimValue();
		double[]
			ma20 = new double[]{mas[0][0], mas[1][0]},
			ma50 = new double[]{mas[0][1], mas[1][1]},
			ma100 = new double[]{mas[0][2], mas[1][2]};
		IBar prevBar = this.context.getHistory().getBars(instrument, period, OfferSide.BID, Filter.WEEKENDS, 2, bidBar.getTime(), 0).get(0);
		
		if (!stopLossSet) {
			if (order.isLong()) {
				if (ma100[1] < bBands[Volatility.BBANDS_BOTTOM][0]
					|| ma100[1] < ma50[1]) {
					if (prevBar.getClose() > ma100[0] && bidBar.getClose() < ma100[1]) {
						lastTradingEvent = "SL set long";
						ma50TrailFlags.put(instrument.name(), new Boolean(true));
						order.setStopLossPrice(bidBar.getLow());
						order.waitForUpdate(null);
					}					
				} else {
					if (prevBar.getClose() > ma50[0] && bidBar.getClose() < ma50[1]) {
						lastTradingEvent = "SL set long";
						ma50TrailFlags.put(instrument.name(), new Boolean(true));
						order.setStopLossPrice(bidBar.getLow());
						order.waitForUpdate(null);						
					}
				}					
			} else {
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
	public TAEventDesc checkEntry(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter, Map<String, FlexTAValue> taValues) throws JFException {
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
