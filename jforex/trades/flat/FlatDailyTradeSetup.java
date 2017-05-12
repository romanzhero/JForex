package jforex.trades.flat;

import java.util.List;
import java.util.Map;

import jforex.events.TAEventDesc;
import jforex.events.TAEventDesc.TAEventType;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.source.FlexTASource;
import jforex.techanalysis.source.FlexTAValue;
import jforex.trades.ITradeSetup;
import jforex.trades.TradeSetup;
import jforex.trades.momentum.LongCandleAndMomentumDetector;
import jforex.trades.momentum.ShortCandleAndMomentumDetector;
import jforex.utils.FXUtils;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public class FlatDailyTradeSetup extends TradeSetup implements ITradeSetup {
	// Trade simply generates all long and short canlde-momentum signals. The newest one wins
	// therefore it must be ensured that their check methods are called for each bar while strategy is running !
	// Should be OK with successive calls to checkEntry and inTradeProcessing
	protected LongCandleAndMomentumDetector longCmd = null;
	protected ShortCandleAndMomentumDetector shortCmd = null;

	protected TradeTrigger.TriggerDesc 
		lastLongSignal = null,
		lastShortSignal = null;
	protected boolean aggressive = false;

	public FlatDailyTradeSetup(IEngine engine, IContext context, boolean aggressive) {
		super(engine, context);
		// this way signals will be generated regardless of the channel position so they can be used both for entry and all exit checks
		// entry and exit checks must explicitly test channel position !
		longCmd = new LongCandleAndMomentumDetector(100, true);
		shortCmd = new ShortCandleAndMomentumDetector(0, true);
		this.aggressive = aggressive;
	}

	@Override
	public String getName() {
		return new String("FlatDaily");
	}

	@Override
	public TAEventDesc checkEntry(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, Map<String, FlexTAValue> taValues) throws JFException {
		TradeTrigger.TriggerDesc 
			currLongSignal = longCmd.checkEntry(instrument, period, OfferSide.BID, filter, bidBar, askBar, taValues), 
			currShortSignal = shortCmd.checkEntry(instrument, period, OfferSide.BID, filter, bidBar, askBar, taValues);

		if (currLongSignal == null && currShortSignal == null)
			return null;

		Trend.FLAT_REGIME_CAUSE currBarFlat = (Trend.FLAT_REGIME_CAUSE)taValues.get(FlexTASource.FLAT_REGIME).getValue();
		if (currBarFlat.equals(Trend.FLAT_REGIME_CAUSE.NONE))
			return null;

		Trend.TREND_STATE trendState = taValues.get(FlexTASource.TREND_ID).getTrendStateValue();
		boolean 
			isMA200Highest = taValues.get(FlexTASource.MA200_HIGHEST).getBooleanValue(), 
			isMA200Lowest = taValues.get(FlexTASource.MA200_LOWEST).getBooleanValue();
		double 
			trendStrengthPerc = taValues.get(FlexTASource.MAs_DISTANCE_PERC).getDoubleValue(), 
			bBandsSquezeePerc = taValues.get(FlexTASource.BBANDS_SQUEEZE_PERC).getDoubleValue();
		if (bBandsSquezeePerc < 30)
			return null; // no entries in narrow channel
		
		if (bBandsSquezeePerc > 75.0
			&& ((trendState.equals(Trend.TREND_STATE.UP_STRONG) && isMA200Lowest)
				|| (trendState.equals(Trend.TREND_STATE.UP_STRONG) && trendStrengthPerc > 30.0)
				|| (trendState.equals(Trend.TREND_STATE.DOWN_STRONG) && isMA200Highest) 
				|| (trendState.equals(Trend.TREND_STATE.DOWN_STRONG) && trendStrengthPerc > 30.0))) {
			return null; // per definition no flat exists in high volatility and at least decent trend !!
		}
		// there is a signal !
		boolean 
			bulishSignal = currLongSignal != null && currLongSignal.channelPosition < 0,
			bearishSignal = currShortSignal != null && currShortSignal.channelPosition > 100;
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

	@Override
	public void inTradeProcessing(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, IOrder order, Map<String, FlexTAValue> taValues, List<TAEventDesc> marketEvents) throws JFException {
		IBar barToCheck = null;
		/*
		 * if ((lastLongSignal != null && lastShortSignal != null &&
		 * lastLongSignal.getLastBar().getTime() >
		 * lastShortSignal.getLastBar().getTime()) || (lastLongSignal != null &&
		 * lastShortSignal == null)) barToCheck = bidBar; else if
		 * ((lastLongSignal != null && lastShortSignal != null &&
		 * lastShortSignal.getLastBar().getTime() >
		 * lastLongSignal.getLastBar().getTime()) || (lastShortSignal != null &&
		 * lastLongSignal == null)) barToCheck = askBar;
		 */
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
			double ma20 = taValues.get(FlexTASource.MAs).getDa2DimValue()[1][0]; 
			boolean
				longExitSignal = currShortSignal != null && currShortSignal.channelPosition > 100,
				shortExitSignal = currLongSignal != null && currLongSignal.channelPosition < 0, 
				// opposite signals anywhere above channel middle, at least last bar extreme also
				longProtectSignal = currShortSignal != null && currShortSignal.channelPosition > 50 && bidBar.getHigh() > ma20,
				shortProtectSignal = currLongSignal != null && currLongSignal.channelPosition < 50 && askBar.getLow() < ma20;				

			if (order.isLong() && longProtectSignal) {
				lastTradingEvent = "long move SL signal";				
				FXUtils.setStopLoss(order, bidBar.getLow(), bidBar.getTime(), this.getClass());
			} else if (!order.isLong() && shortProtectSignal) {
				lastTradingEvent = "short move SL signal";				
				FXUtils.setStopLoss(order, askBar.getHigh(), bidBar.getTime(), this.getClass());
			}
			// check for opposite signal. Depending on the configuration either set break even or close the trade
			else if (aggressive) {
				if ((order.isLong() && longExitSignal)
					|| (!order.isLong() && shortExitSignal)) {
					lastTradingEvent = "exit due to opposite flat signal";				
					order.close();
					order.waitForUpdate(null);
					//afterTradeReset(instrument);
				}
			} else {
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
			}
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
