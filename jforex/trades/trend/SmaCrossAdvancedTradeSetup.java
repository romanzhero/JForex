package jforex.trades.trend;

import java.util.Map;
import java.util.Set;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

import jforex.techanalysis.Trend.FLAT_REGIME_CAUSE;
import jforex.techanalysis.source.FlexTASource;
import jforex.techanalysis.source.FlexTAValue;

public class SmaCrossAdvancedTradeSetup extends SmaCrossTradeSetup {

	public SmaCrossAdvancedTradeSetup(IEngine engine, IContext context, Set<Instrument> subscribedInstruments,
			boolean mktEntry, boolean onlyCross, double pFlatPercThreshold, double pBBandsSqueezeThreshold,
			boolean trailsOnMA50) {
		super(engine, context, subscribedInstruments, mktEntry, onlyCross, pFlatPercThreshold, pBBandsSqueezeThreshold,
				trailsOnMA50);
	}

	/* (non-Javadoc)
	 * @see jforex.trades.trend.SmaCrossTradeSetup#sellSignal(com.dukascopy.api.Instrument, com.dukascopy.api.Period, com.dukascopy.api.Filter, double[], double[], double[], double[], com.dukascopy.api.IBar, boolean, java.util.Map)
	 */
	@Override
	protected boolean sellSignal(Instrument instrument, Period period, Filter filter, double[] ma20, double[] ma50,
			double[] ma100, double[] ma200, IBar bidBar, boolean strict, Map<String, FlexTAValue> taValues)
			throws JFException {
		// TODO Auto-generated method stub
		return super.sellSignal(instrument, period, filter, ma20, ma50, ma100, ma200, bidBar, strict, taValues);
	}

	/* 
 Kriterijumi za POCETAK long perioda (isto kao kasnije za ulazak u trejd):
 Grupa 1: trend ili flat
1. TREND_ID									6 (plus na prethodnoj svecici nije bio, tj. CROSS !!!)
2. MAs_DISTANCE_PERC						veca od nekog minimuma, verovatno iznad 20/25 percentile
3. MA200_HIGHEST							ne sme
4. MA200_LOWEST							
5. MA200_IN_CHANNEL							sme samo ako je MAs_DISTANCE_PERC dovoljno veliki
6. MA200MA100_TREND_DISTANCE_PERC			(za ulaz nevazno, interesatno za izlaz)
7. FLAT_REGIME polozaj MAs u kanalu			ne sme ako su sva CETIRI u kanalu (?), mozda sme ako je MA200 najnizi i MAs_DISTANCE_PERC OK ??? Prouciti !
8. ICHI										mozda zgodno proveriti da li je svecica van oblaka ?

Grupa 2: momentum i OS/OB
9. Stanje STOCH linija						ne sme obe OB zajedno sa oba SMI OB (prekasno), ne sme oba FALLING_IN_MIDDLE
10. Stanje SMI linija						ne sme oba OB (zavisno ili nezavisno od Stoch ??), makar brzi mora da raste
11. Stanje RSI linije ?
12. Stanje CCI linije ?
13. CHANNEL_POS (close)

Grupa 3: volatilitet (uzak / sirok kanal)
14. BBANDS_SQUEEZE_PERC						veca od nekog minimuma, verovatno iznad 20/25 percentile. Mozda nevazno ako je MAs_DISTANCE_PERC dovoljno velik i jos eventualno MA200 najnizi ??

Grupa 4: price action / candlestick paterns
15. BULLISH_CANDLES / BEARISH_CANDLES		eventualno bullish sa jasno bullish ukupnim telom
16. poslednja svecica						mora biti ili cisto bulish ili 1-bar bullish reversal 
	 */
	@Override
	protected boolean buySignal(Instrument instrument, Period period, Filter filter, double[] ma20, double[] ma50,
			double[] ma100, double[] ma200, IBar bidBar, boolean strict, Map<String, FlexTAValue> taValues)
			throws JFException {
		
		return super.buySignal(instrument, period, filter, ma20, ma50, ma100, ma200, bidBar, strict, taValues);
	}

	/* (non-Javadoc)
	 * @see jforex.trades.trend.SmaCrossTradeSetup#getName()
	 */
	@Override
	public String getName() {
		return new String("SmaCrossAdvancedTradeSetup");
	}
	
	

}
