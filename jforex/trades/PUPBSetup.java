package jforex.trades;

import java.util.List;
import java.util.Map;

import jforex.events.TAEventDesc;
import jforex.events.TAEventDesc.TAEventType;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.source.FlexTASource;
import jforex.techanalysis.source.TechnicalSituation;
import jforex.techanalysis.source.TechnicalSituation.OverallTASituation;
import jforex.techanalysis.source.TechnicalSituation.TASituationReason;
import jforex.trades.flat.AbstractMeanReversionSetup;
import jforex.trades.momentum.MomentumReversalSetup;
import jforex.trades.trend.TrendSprint;
import jforex.utils.StopLoss;
import jforex.utils.log.FlexLogEntry;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public class PUPBSetup extends AbstractMeanReversionSetup implements ITradeSetup {
	public String SETUP_NAME = "PUPB";
	

	public PUPBSetup(IIndicators indicators, IContext context, IHistory history, IEngine engine, boolean useEntryFilters) {
		super(useEntryFilters, engine, context);
	}

	@Override
	public String getName() {
		return SETUP_NAME;
	}

	@Override
	public TAEventDesc checkEntry(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, Map<String, FlexLogEntry> taValues) throws JFException {
		TradeTrigger.TriggerDesc 
			currLongSignal = longCmd.checkEntry(instrument, period, OfferSide.BID, filter, bidBar, askBar, taValues), 
			currShortSignal = shortCmd.checkEntry(instrument, period, OfferSide.BID, filter, bidBar, askBar, taValues);
	
		if (currLongSignal == null && currShortSignal == null)
			return null;
		
		double ma100ma200Distance = taValues.get(FlexTASource.MA200MA100_TREND_DISTANCE_PERC).getDoubleValue();
		boolean 
			isMA200Highest = taValues.get(FlexTASource.MA200_HIGHEST).getBooleanValue(), 
			isMA200Lowest = taValues.get(FlexTASource.MA200_LOWEST).getBooleanValue(),
			MA200NotInChannel = !taValues.get(FlexTASource.MA200_IN_CHANNEL).getBooleanValue(),
			uptrend = (MA200NotInChannel || ma100ma200Distance > 50) && isMA200Lowest,
			downtrend = (MA200NotInChannel || ma100ma200Distance > 50) && isMA200Highest;
		if (!uptrend && !downtrend) {
			return null; 
		}
		
		boolean 
			bulishSignal = uptrend && currLongSignal != null && currLongSignal.channelPosition < 0,
			bearishSignal = downtrend && currShortSignal != null && currShortSignal.channelPosition > 100;
		locked = bulishSignal || bearishSignal;
		// there is a signal !
		if (bulishSignal) {
			lastLongSignal = currLongSignal;
			TAEventDesc result = new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, true, askBar, bidBar, period);
			result.candles = currLongSignal;
			return result;
		} else if (bearishSignal) {
			lastShortSignal = currShortSignal;
			TAEventDesc result = new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, false, askBar, bidBar, period);
			result.candles = currShortSignal;
			return result;
		} else
			return null;
	}
	/* 
	Dogadjaji i reakcije:
	- suprotni signal FlatTrade: SL na dno poslednje svecice, ali se trejd NE zatvara da bi Flat mogao da udje !
		(pretpostavka je da trend i dalje traje i ne zeli se rizikovati protiv njega)
	- profit > 50% max. dnevnog opsega: SL na prvu svecicu suprotnog karaktera
	- kontrolise se probijanje suprotne ivice kanala, SAMO ako kanal nije UZAK. Ako se desilo, postavlja se SL na SVAKU svecicu koja je nepovoljno probila MA20

	Kriterijumi za OSTANAK u trejdu i prestanak provere ostalih dogadjaja za LONG trejd:
	- uzak kanal (ali unlock treba PRVO proveriti !)
	- Slow SMI raising oversold (?)
	
	Kriterijumi za izlazak iz LONG trejd-a:

	Grupa 2: momentum i OS/OB
	10. Stanje STOCH linija						
	11. Stanje SMI linija						
	14. CHANNEL_POS (close)
		- kombinovani bearish momentum Stoch/SMI + close < MA20 (sredine kanala): SL na dno poslednje svecice

	 */
	@Override
	public void inTradeProcessing(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, IOrder order, Map<String, FlexLogEntry> taValues,	List<TAEventDesc> marketEvents) throws JFException {
		IBar barToCheck = null;
		if (order.isLong())
			barToCheck = bidBar;
		else
			barToCheck = askBar;
		TechnicalSituation taSituation = taValues.get(FlexTASource.TA_SITUATION).getTehnicalSituationValue();
		TAEventDesc
			flatEntry = findTAEvent(marketEvents, TAEventType.ENTRY_SIGNAL, this.SETUP_NAME, instrument, period),
			trendSprintEntry = findTAEvent(marketEvents, TAEventType.ENTRY_SIGNAL, TrendSprint.SETUP_NAME, instrument, period),
			momentumReversalEntry = findTAEvent(marketEvents, TAEventType.ENTRY_SIGNAL, MomentumReversalSetup.SETUP_NAME, instrument, period);
		if (openOrderProcessing(instrument, period, bidBar, order, taValues, marketEvents, barToCheck, 
				taSituation, trendSprintEntry, momentumReversalEntry))
			return;
		
		if (order.getState().equals(IOrder.State.FILLED)) {
			checkSetupUnlock(instrument, period, askBar, bidBar, order, marketEvents);
			checkOppositeChannelPierced(instrument, period, bidBar, askBar, order, taValues, marketEvents);
			
			if (taValues.get(FlexTASource.BBANDS_SQUEEZE_PERC).getDoubleValue() < 30.0)
				return;
			
			if (checkExtremeProfit(instrument, period, askBar, bidBar, order, marketEvents))
				return;
			
			// Trade simply generates all long and short canlde-momentum signals.
			// The newest one wins therefore it must be ensured that their check methods are called for each bar while strategy is running !
			// Should be OK with successive calls to checkEntry and inTradeProcessing
			
			boolean
				longProtectSignal = (flatEntry != null && !flatEntry.isLong)
									|| (taSituation.taSituation.equals(OverallTASituation.BEARISH)
										&& taSituation.taReason.equals(TASituationReason.TREND))
									|| (trendSprintEntry != null && !trendSprintEntry.isLong)
									|| (momentumReversalEntry != null && !momentumReversalEntry.isLong)	 
									|| (FlexTASource.solidBearishMomentum(taValues) && taValues.get(FlexTASource.CHANNEL_POS).getDoubleValue() < 50)
									|| (tradeEvents.get(instrument.name()).get(OPPOSITE_CHANNEL_PIERCED).getBooleanValue() && taValues.get(FlexTASource.CHANNEL_POS).getDoubleValue() < 50)
									|| taValues.get(FlexTASource.CHANNEL_POS).getDoubleValue() < 0,
				shortProtectSignal = (flatEntry != null && flatEntry.isLong)
									|| (taSituation.taSituation.equals(OverallTASituation.BULLISH)
										&& taSituation.taReason.equals(TASituationReason.TREND))
									|| (trendSprintEntry != null && trendSprintEntry.isLong)
									|| (momentumReversalEntry != null && momentumReversalEntry.isLong)
									|| (FlexTASource.solidBullishMomentum(taValues) && taValues.get(FlexTASource.CHANNEL_POS).getDoubleValue() > 50)
									|| (tradeEvents.get(instrument.name()).get(OPPOSITE_CHANNEL_PIERCED).getBooleanValue() && taValues.get(FlexTASource.CHANNEL_POS).getDoubleValue() > 50)
									|| taValues.get(FlexTASource.CHANNEL_POS).getDoubleValue() > 100;
				
				// check for opposite signal with good profit
				if (order.isLong() && longProtectSignal) {
					lastTradingEvent = "long breakeven signal due to ";			
					if (flatEntry != null && !flatEntry.isLong)
						lastTradingEvent += "short flat signal, ";
					if (taSituation.taSituation.equals(OverallTASituation.BEARISH)
						&& taSituation.taReason.equals(TASituationReason.TREND))
						lastTradingEvent += "bearish (trend) TA situation, ";
					if (trendSprintEntry != null && !trendSprintEntry.isLong)
						lastTradingEvent += "short TradeSprintEarly entry signal, ";
					if (momentumReversalEntry != null && !momentumReversalEntry.isLong)	 
						lastTradingEvent += "short MomentumReversal signal, ";
					if (FlexTASource.solidBearishMomentum(taValues) && taValues.get(FlexTASource.CHANNEL_POS).getDoubleValue() < 50)
						lastTradingEvent += "bearish momentum and close below MA20, ";
					if (tradeEvents.get(instrument.name()).get(OPPOSITE_CHANNEL_PIERCED).getBooleanValue() && taValues.get(FlexTASource.CHANNEL_POS).getDoubleValue() < 50)
						lastTradingEvent += "opposite channel border pierced and close below MA20";

					addTradeHistoryEvent(instrument, period, marketEvents, bidBar.getTime(), 0, lastTradingEvent);
					if (!StopLoss.setBreakEvenSituative(order, bidBar))
						// order was closed !
						return;
				} else if (!order.isLong() && shortProtectSignal) {
					lastTradingEvent = "short breakeven signal due to";	
					if (flatEntry != null && flatEntry.isLong)
						lastTradingEvent += "long flat signal, ";
					if (taSituation.taSituation.equals(OverallTASituation.BULLISH)
						&& taSituation.taReason.equals(TASituationReason.TREND))
						lastTradingEvent += "bullish (trend) TA situation, ";
					if (trendSprintEntry != null && trendSprintEntry.isLong)
						lastTradingEvent += "long TradeSprintEarly entry signal, ";
					if (momentumReversalEntry != null && momentumReversalEntry.isLong)	 
						lastTradingEvent += "long MomentumReversal signal, ";
					if (FlexTASource.solidBullishMomentum(taValues) && taValues.get(FlexTASource.CHANNEL_POS).getDoubleValue() > 50)
						lastTradingEvent += "bullish momentum and close above MA20, ";
					if (tradeEvents.get(instrument.name()).get(OPPOSITE_CHANNEL_PIERCED).getBooleanValue() && taValues.get(FlexTASource.CHANNEL_POS).getDoubleValue() > 50)
						lastTradingEvent += "opposite channel border pierced and close below MA20";
					addTradeHistoryEvent(instrument, period, marketEvents, bidBar.getTime(), 0, lastTradingEvent);
					if (!StopLoss.setBreakEvenSituative(order, askBar))
						// order was closed !
						return;
			} 
		}
	} 
	
}
