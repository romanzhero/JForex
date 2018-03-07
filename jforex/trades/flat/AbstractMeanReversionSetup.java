package jforex.trades.flat;

import java.util.List;
import java.util.Map;

import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

import jforex.events.TAEventDesc;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.source.FlexTASource;
import jforex.techanalysis.source.TechnicalSituation;
import jforex.techanalysis.source.TechnicalSituation.OverallTASituation;
import jforex.techanalysis.source.TechnicalSituation.TASituationReason;
import jforex.trades.TradeSetup;
import jforex.trades.momentum.LongCandleAndMomentumDetector;
import jforex.trades.momentum.ShortCandleAndMomentumDetector;
import jforex.utils.log.FlexLogEntry;

public abstract class AbstractMeanReversionSetup extends TradeSetup {
	protected LongCandleAndMomentumDetector longCmd = null;
	protected ShortCandleAndMomentumDetector shortCmd = null;
	protected TradeTrigger.TriggerDesc lastLongSignal = null;
	protected TradeTrigger.TriggerDesc lastShortSignal = null;
	protected boolean aggressive = false;
	protected static final double CHANNEL_OFFSET = 3;

	public AbstractMeanReversionSetup(boolean pUseEntryFilters, IEngine engine, IContext context) {
		super(pUseEntryFilters, engine, context);
		for (Instrument i : context.getSubscribedInstruments()) {
			tradeEvents.get(i.name()).put(OPPOSITE_CHANNEL_PIERCED, new FlexLogEntry(OPPOSITE_CHANNEL_PIERCED, new Boolean(false)));
		}
	}

	public AbstractMeanReversionSetup(IEngine engine, IContext context, boolean pTakeOverOnly) {
		super(engine, context, pTakeOverOnly);
		for (Instrument i : context.getSubscribedInstruments()) {
			tradeEvents.get(i.name()).put(OPPOSITE_CHANNEL_PIERCED, new FlexLogEntry(OPPOSITE_CHANNEL_PIERCED, new Boolean(false)));
		}
	}

	protected void checkSetupUnlock(Instrument instrument, Period period, IBar askBar, IBar bidBar, IOrder order, List<TAEventDesc> marketEvents) {
		// check whether to unlock the trade - price exceeded opposite channel border at the time of the signal
		if ((order.isLong() && askBar.getClose() > lastLongSignal.bBandsTop)
			|| (!order.isLong() && bidBar.getClose() < lastShortSignal.bBandsBottom)) {
			lastTradingEvent = "Unlock setup (other setups allowed)";
			locked = false;
			addTradeHistoryEvent(instrument, period, marketEvents, bidBar.getTime(), 0, lastTradingEvent);
			// do not reset trade completely ! Keep control over order until other setups take over !
		}
	}

	protected boolean openOrderProcessing(Instrument instrument, Period period, IBar bidBar, IOrder order, Map<String, FlexLogEntry> taValues, List<TAEventDesc> marketEvents,
			IBar barToCheck, TechnicalSituation taSituation, TAEventDesc trendSprintEntry, TAEventDesc momentumReversalEntry) throws JFException {
			if (!order.getState().equals(IOrder.State.OPENED)) 
				return false;
		
			// still waiting. Cancel if price already exceeded SL level without triggering entry stop
			// or if an opposite trend developed
			boolean 
				exitLongSL = false,
				exitLongTrend = false,
				exitShortSL = false,
				exitShortTrend = false;
			if (order.isLong()) {
				exitLongSL = barToCheck.getClose() < order.getStopLossPrice();
				exitLongTrend = (taSituation.taSituation.equals(OverallTASituation.BEARISH)
									&& taSituation.taReason.equals(TASituationReason.TREND))
								|| (trendSprintEntry != null && !trendSprintEntry.isLong)
								|| (momentumReversalEntry != null && !momentumReversalEntry.isLong)
								|| (FlexTASource.solidBearishMomentum(taValues) && taValues.get(FlexTASource.CHANNEL_POS).getDoubleValue() < 50);
				lastTradingEvent = exitLongSL ? "Cancel entry, SL exceeded" : "Cancel entry, bearish trend or momentum";
		
			} else {
				exitShortSL = barToCheck.getClose() > order.getStopLossPrice();
				exitShortTrend = (taSituation.taSituation.equals(OverallTASituation.BULLISH)
									&& taSituation.taReason.equals(TASituationReason.TREND))
								|| (trendSprintEntry != null && trendSprintEntry.isLong)
								|| (momentumReversalEntry != null && momentumReversalEntry.isLong)
								|| (FlexTASource.solidBullishMomentum(taValues) && taValues.get(FlexTASource.CHANNEL_POS).getDoubleValue() > 50);
				lastTradingEvent = exitLongSL ? "Cancel entry, SL exceeded" : "Cancel entry, bullish trend or momentum";
			}
			if ((order.isLong() && (exitLongSL || exitLongTrend))
				|| (!order.isLong() && (exitShortSL || exitShortTrend))) {
				addTradeHistoryEvent(instrument, period, marketEvents, bidBar.getTime(), 0, lastTradingEvent);
				order.close();
				order.waitForUpdate(null);
				return true;
				//afterTradeReset(instrument);
			} else
				return false;
		}

	@Override
	public IOrder submitOrder(String label, Instrument instrument, boolean isLong, double amount, IBar bidBar, IBar askBar)
			throws JFException {
				return submitStpOrder(label, instrument, isLong, amount, bidBar, askBar, isLong ? lastLongSignal.pivotLevel : lastShortSignal.pivotLevel);
			}

	public TradeTrigger.TriggerDesc getLastLongSignal() {
		return lastLongSignal;
	}

	public TradeTrigger.TriggerDesc getLastShortSignal() {
		return lastShortSignal;
	}

}