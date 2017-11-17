package jforex.trades.flat;

import java.util.List;
import java.util.Map;

import jforex.events.TAEventDesc;
import jforex.events.TAEventDesc.TAEventType;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.source.FlexTASource;
import jforex.trades.ITradeSetup;
import jforex.trades.TradeSetup;
import jforex.trades.momentum.LongCandleAndMomentumDetector;
import jforex.trades.momentum.ShortCandleAndMomentumDetector;
import jforex.utils.FXUtils;
import jforex.utils.log.FlexLogEntry;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public class FlatTradeSetup extends TradeSetup implements ITradeSetup {
	// Trade simply generates all long and short canlde-momentum signals. The newest one wins
	// therefore it must be ensured that their check methods are called for each bar while strategy is running !
	// Should be OK with successive calls to checkEntry and inTradeProcessing
	protected LongCandleAndMomentumDetector longCmd = null;
	protected ShortCandleAndMomentumDetector shortCmd = null;

	protected TradeTrigger.TriggerDesc 
		lastLongSignal = null,
		lastShortSignal = null;
	protected boolean aggressive = false;

	public FlatTradeSetup(IEngine engine, IContext context, boolean aggressive) {
		super(engine, context);
		// this way signals will be generated regardless of the channel position so they can be used both for entry and all exit checks
		// entry and exit checks must explicitly test channel position !
		longCmd = new LongCandleAndMomentumDetector(100, false);
		shortCmd = new ShortCandleAndMomentumDetector(0, false);
		this.aggressive = aggressive;
	}

	@Override
	public String getName() {
		return new String("Flat");
	}

/* 
Kriterijumi za ulazak u LONG trejd:
Grupa 1: trend ili flat
1. TREND_ID							
2. MAs_DISTANCE_PERC						
3. MA200_HIGHEST						
4. MA200_LOWEST							
5. MA200_IN_CHANNEL						DA ! OBAVEZNO !
6. MA200MA100_TREND_DISTANCE_PERC			
7. FLAT_REGIME polozaj MAs u kanalu		DA ! Bilo koje od dve mogucnosti (svi MA u kanalu ili MAs blizu, cak i ako je jedan izvan)	
8. ICHI							
9. MAs SLOPE !!

Grupa 2: momentum i OS/OB
9. Stanje STOCH linija					Vidi LongCandleAndMomentumDetector. Mora jedan od cistih BULLISH stanja	
10. Stanje SMI linija					Brza mora rasti ili ticked_up, ili brza iznad spore bez obzira na pravac obe	
11. Stanje RSI linije ?
12. Stanje CCI linije ?
13. CHANNEL_POS (close)					NE SME iznad cca. 70% ! Inace je prekasno i SL postaje prevelik !

Grupa 3: volatilitet (uzak / sirok kanal)
14. BBANDS_SQUEEZE_PERC					NE SME ispod 30. Nema ulazaka u uskom kanalu !!!

Grupa 4: price action / candlestick paterns
15. BULLISH_CANDLES / BEARISH_CANDLES	DA ! Vidi LongCandleAndMomentumDetector. Mora BULLISH_CANDLES ciji je pivot probio dno kanala.
16. poslednja svecica						
*/	
	@Override
	public TAEventDesc checkEntry(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, Map<String, FlexLogEntry> taValues) throws JFException {
		TradeTrigger.TriggerDesc 
			currLongSignal = longCmd.checkEntry(instrument, period, OfferSide.BID, filter, bidBar, askBar, taValues), 
			currShortSignal = shortCmd.checkEntry(instrument, period, OfferSide.BID, filter, bidBar, askBar, taValues);

		if (currLongSignal == null && currShortSignal == null)
			return null;

		Trend.FLAT_REGIME_CAUSE currBarFlat = (Trend.FLAT_REGIME_CAUSE)taValues.get(FlexTASource.FLAT_REGIME).getValue();
		if (currBarFlat.equals(Trend.FLAT_REGIME_CAUSE.NONE))
			return null;

		double 
			trendStrengthPerc = taValues.get(FlexTASource.MAs_DISTANCE_PERC).getDoubleValue(), 
			bBandsSquezeePerc = taValues.get(FlexTASource.BBANDS_SQUEEZE_PERC).getDoubleValue(),
			channelPos = taValues.get(FlexTASource.CHANNEL_POS).getDoubleValue();
		if (bBandsSquezeePerc < 30.0)
			return null;
		
		boolean 
			isMA200InChannel = taValues.get(FlexTASource.MA200_IN_CHANNEL).getBooleanValue(),
			isMA200Highest = taValues.get(FlexTASource.MA200_HIGHEST).getBooleanValue(), 
			isMA200Lowest = taValues.get(FlexTASource.MA200_LOWEST).getBooleanValue();
		if (!isMA200InChannel)
			return null;
		
		// there is a signal, assuming favourable channel pos of the last candle !
		boolean 
			bulishSignal = currLongSignal != null && currLongSignal.channelPosition < 0 && channelPos < 70,
			bearishSignal = currShortSignal != null && currShortSignal.channelPosition > 100 && channelPos > 30;
		locked = bulishSignal || bearishSignal;
		if (bulishSignal) {
			//lastTradingEvent = "buy signal";
			lastLongSignal = currLongSignal;
			TAEventDesc result = new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, true, askBar, bidBar, period);
			result.candles = currLongSignal;
			return result;
		} else if (bearishSignal) {
			//lastTradingEvent = "sell signal";
			lastShortSignal = currShortSignal;
			TAEventDesc result = new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, false, askBar, bidBar, period);
			result.candles = currShortSignal;
			return result;
		} else
			return null;
	}
	
	/* 
	Kriterijumi za zatvaranje LONG trejda ili stavljanje SL na brake even:
	Grupa 1: trend ili flat
	1. TREND_ID							
	2. MAs_DISTANCE_PERC						
	3. MA200_HIGHEST						
	4. MA200_LOWEST							
	5. MA200_IN_CHANNEL						
	6. MA200MA100_TREND_DISTANCE_PERC			
	7. FLAT_REGIME polozaj MAs u kanalu			
	8. ICHI		
	9. MAs SLOPE !!					

	Grupa 2: momentum i OS/OB
	9. Stanje STOCH linija					
	10. Stanje SMI linija					
	11. Stanje RSI linije ?
	12. Stanje CCI linije ?
	13. CHANNEL_POS (close)					

	Grupa 3: volatilitet (uzak / sirok kanal)
	14. BBANDS_SQUEEZE_PERC					NISTA se ne radie ako je ispod 30. Nema promena u uskom kanalu !!!

	Grupa 4: price action / candlestick paterns
	15. BULLISH_CANDLES / BEARISH_CANDLES	.
	16. poslednja svecica						
	*/	

	@Override
	public void inTradeProcessing(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, IOrder order, Map<String, FlexLogEntry> taValues, List<TAEventDesc> marketEvents) throws JFException {
		IBar barToCheck = null;

		if (order.isLong())
			barToCheck = bidBar;
		else
			barToCheck = askBar;
		if (order.getState().equals(IOrder.State.OPENED)) {
			// still waiting. Cancel if price already exceeded SL level without triggering entry stop
			if ((order.isLong() && barToCheck.getClose() < order.getStopLossPrice())
				|| (!order.isLong() && barToCheck.getClose() > order.getStopLossPrice())) {
				lastTradingEvent = "Cancel entry, SL exceeded";
				order.close();
				order.waitForUpdate(null);
				return;
				//afterTradeReset(instrument);
			}
			
		} else if (order.getState().equals(IOrder.State.FILLED)) {
			// check whether to unlock the trade - price exceeded opposite
			// channel border at the time of the signal
			if ((order.isLong() && askBar.getClose() > lastLongSignal.bBandsTop)
				|| (!order.isLong() && bidBar.getClose() < lastShortSignal.bBandsBottom)) {
				lastTradingEvent = "Unlock setup (other setups allowed)";
				locked = false;
				// do not reset trade completely ! Keep control over order until
				// other setups take over !
			}
			// Trade simply generates all long and short canlde-momentum signals.
			// The newest one wins therefore it must be ensured that their check methods are called for each bar while strategy is running !
			// Should be OK with successive calls to checkEntry and inTradeProcessing
			TradeTrigger.TriggerDesc 
				currLongSignal = longCmd.checkEntry(instrument, period, OfferSide.BID, filter, bidBar, askBar, taValues), 
				currShortSignal = shortCmd.checkEntry(instrument, period, OfferSide.BID, filter, bidBar, askBar, taValues);

			double 
				bBandsSquezeePerc = taValues.get(FlexTASource.BBANDS_SQUEEZE_PERC).getDoubleValue();
			if (bBandsSquezeePerc < 30.0)
				return;
			
			Trend.FLAT_REGIME_CAUSE currBarFlat = (Trend.FLAT_REGIME_CAUSE)taValues.get(FlexTASource.FLAT_REGIME).getValue();
			boolean 
				isMA200InChannel = taValues.get(FlexTASource.MA200_IN_CHANNEL).getBooleanValue(),
				isMA200Highest = taValues.get(FlexTASource.MA200_HIGHEST).getBooleanValue(), 
				isMA200Lowest = taValues.get(FlexTASource.MA200_LOWEST).getBooleanValue();
			// should react to opposite signals only when there is still flat regime !
			if (!isMA200InChannel || currBarFlat.equals(Trend.FLAT_REGIME_CAUSE.NONE))
				return;
			
			boolean
				longExitSignal = currShortSignal != null && currShortSignal.channelPosition > 100,
				shortExitSignal = currLongSignal != null && currLongSignal.channelPosition < 0, 
				//TODO: to be defined, probably very clear momentum against the trade
				longProtectSignal = false, //currShortSignal != null && currShortSignal.channelPosition > 50 && bidBar.getHigh() > ma20,
				shortProtectSignal = false; //currLongSignal != null && currLongSignal.channelPosition < 50 && askBar.getLow() < ma20;				

			if (order.isLong() && longProtectSignal) {
				lastTradingEvent = "long move SL signal";				
				FXUtils.setStopLoss(order, bidBar.getLow(), bidBar.getTime(), this.getClass());
			} else if (!order.isLong() && shortProtectSignal) {
				lastTradingEvent = "short move SL signal";				
				FXUtils.setStopLoss(order, askBar.getHigh(), bidBar.getTime(), this.getClass());
			}
			// check for opposite signal. Depending on the configuration either set break even or close the trade
			else {
				if ((order.isLong() && longExitSignal)
					|| (!order.isLong() && shortExitSignal)) {
					lastTradingEvent = "exit due to opposite flat signal";				
					order.close();
					order.waitForUpdate(null);
					//afterTradeReset(instrument);
				}
			} /*else {
				// set to break even. However check if current price allows it !
				// If not set SL on extreme of the last bar (if it not already
				// exceeded the SL !)
				if ((order.isLong() && longExitSignal)
					|| (!order.isLong() && shortExitSignal)) {
					lastTradingEvent = "move SL due to opposite flat signal";				
					if (order.isLong()) {
						if (bidBar.getClose() > order.getOpenPrice()) {
							FXUtils.setStopLoss(order, order.getOpenPrice(), bidBar.getTime(), getClass());
						} else if (bidBar.getLow() > order.getStopLossPrice()) {
							FXUtils.setStopLoss(order, bidBar.getLow(), bidBar.getTime(), getClass());
						}
					} else {
						if (askBar.getClose() < order.getOpenPrice()) {
							FXUtils.setStopLoss(order, order.getOpenPrice(), bidBar.getTime(), getClass());
						} else if (askBar.getHigh() < order.getStopLossPrice()) {
							FXUtils.setStopLoss(order, askBar.getHigh(), bidBar.getTime(), getClass());
						}
					}
				}
			}*/
		}
	}

	@Override
	public void afterTradeReset(Instrument instrument) {
		super.afterTradeReset(instrument);
		locked = false;
	}

	@Override
	public IOrder submitOrder(String label, Instrument instrument, boolean isLong, double amount, IBar bidBar, IBar askBar)	throws JFException {
		return submitStpOrder(label, instrument, isLong, amount, bidBar, askBar, isLong ? lastLongSignal.pivotLevel : lastShortSignal.pivotLevel);
	}

	public TradeTrigger.TriggerDesc getLastLongSignal() {
		return lastLongSignal;
	}

	public TradeTrigger.TriggerDesc getLastShortSignal() {
		return lastShortSignal;
	}

}
