package jforex.trades.old;

import java.util.List;

import jforex.events.TAEventDesc;
import jforex.events.TAEventDesc.TAEventType;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Volatility;
import jforex.utils.FXUtils;
import jforex.utils.StopLoss;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
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
	protected Volatility vola = null;
	protected Trend trend = null;

	public FlatTradeSetup(IIndicators indicators, IHistory history, IEngine engine, boolean aggressive) {
		super(indicators, history, engine);
		// this way signals will be generated regardless of the channel position so they can be used both for entry and all exit checks
		// entry and exit checks must explicitly test channel position !
		longCmd = new LongCandleAndMomentumDetector(new TradeTrigger(indicators, history, null), new Momentum(history, indicators), 100);
		shortCmd = new ShortCandleAndMomentumDetector(new TradeTrigger(indicators, history, null), new Momentum(history, indicators), 0);
		this.aggressive = aggressive;
		vola = new Volatility(indicators);
		trend = new Trend(indicators);
	}

	@Override
	public String getName() {
		return new String("Flat");
	}

	@Override
	public TAEventDesc checkEntry(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter) throws JFException {
		TradeTrigger.TriggerDesc 
			currLongSignal = longCmd.checkEntry(instrument, period, OfferSide.BID, filter, bidBar, askBar), 
			currShortSignal = shortCmd.checkEntry(instrument, period, OfferSide.BID, filter, bidBar, askBar);

		if (currLongSignal == null && currShortSignal == null)
			return null;

		Trend.FLAT_REGIME_CAUSE currBarFlat = trend.isFlatRegime(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, filter,	bidBar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS, 30.0);
		if (currBarFlat.equals(Trend.FLAT_REGIME_CAUSE.NONE))
			return null;

		Trend.TREND_STATE trendState = trend.getTrendState(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime());
		boolean 
			isMA200Highest = trend.isMA200Highest(instrument, period, OfferSide.BID,IIndicators.AppliedPrice.CLOSE, bidBar.getTime()), 
			isMA200Lowest = trend.isMA200Lowest(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime());
		double 
			trendStrengthPerc = trend.getMAsMaxDiffPercentile(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS), 
			bBandsSquezeePerc = vola.getBBandsSqueezePercentile(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, filter, bidBar.getTime(), 20, FXUtils.YEAR_WORTH_OF_4H_BARS);
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
			lastTradingEvent = "buy signal";
			lastLongSignal = currLongSignal;
			TAEventDesc result = new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, true, askBar, bidBar, period);
			result.candles = currLongSignal;
			return result;
		} else if (bearishSignal) {
			lastTradingEvent = "sell signal";
			lastShortSignal = currShortSignal;
			TAEventDesc result = new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, false, askBar, bidBar, period);
			result.candles = currShortSignal;
			return result;
		} else
			return null;
	}

	@Override
	public void inTradeProcessing(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, IOrder order, List<TAEventDesc> marketEvents) throws JFException {
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
			// still waiting. Cancel if price already exceeded SL level without
			// triggering entry stop
			if ((order.isLong() && barToCheck.getClose() < order.getStopLossPrice())
					|| (!order.isLong() && barToCheck.getClose() > order.getStopLossPrice())) {
				order.close();
				order.waitForUpdate(null);
				afterTradeReset(instrument);
			}
		} else if (order.getState().equals(IOrder.State.FILLED)) {
			// check whether to unlock the trade - price exceeded opposite
			// channel border at the time of the signal
			if ((order.isLong() && askBar.getClose() > lastLongSignal.bBandsTop)
				|| (!order.isLong() && bidBar.getClose() < lastShortSignal.bBandsBottom)) {
				lastTradingEvent = "unlock setup (other setups allowed)";
				locked = false;
				// do not reset trade completely ! Keep control over order until
				// other setups take over !
			}
			// Trade simply generates all long and short canlde-momentum signals.
			// The newest one wins therefore it must be ensured that their check methods are called for each bar while strategy is running !
			// Should be OK with successive calls to checkEntry and inTradeProcessing
			TradeTrigger.TriggerDesc 
				currLongSignal = longCmd.checkEntry(instrument, period, OfferSide.BID, filter, bidBar, askBar), 
				currShortSignal = shortCmd.checkEntry(instrument, period, OfferSide.BID, filter, bidBar, askBar);
			double ma20 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 20, filter, 1, bidBar.getTime(), 0)[0];
			boolean
				longExitSignal = currShortSignal != null && currShortSignal.channelPosition > 100,
				shortExitSignal = currLongSignal != null && currLongSignal.channelPosition < 0, 
				// opposite signals anywhere above channel middle, at least last bar extreme also
				longProtectSignal = currShortSignal != null && currShortSignal.channelPosition > 50 && bidBar.getHigh() > ma20,
				shortProtectSignal = currLongSignal != null && currLongSignal.channelPosition < 50 && askBar.getLow() < ma20;				

			if (order.isLong() && longProtectSignal) {
				lastTradingEvent = "long move SL signal";				
				StopLoss.setStopLoss(order, bidBar.getLow(), bidBar.getTime(), this.getClass());
			} else if (!order.isLong() && shortProtectSignal) {
				lastTradingEvent = "short move SL signal";				
				StopLoss.setStopLoss(order, askBar.getHigh(), bidBar.getTime(), this.getClass());
			}
			// check for opposite signal. Depending on the configuration either
			// set break even or close the trade
			else if (aggressive) {
				if ((order.isLong() && longExitSignal)
					|| (!order.isLong() && shortExitSignal)) {
					lastTradingEvent = "exit due to opposite flat signal";				
					order.close();
					order.waitForUpdate(null);
					afterTradeReset(instrument);
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
							StopLoss.setStopLoss(order, order.getOpenPrice(), bidBar.getTime(), getClass());
						} else if (bidBar.getLow() > order.getStopLossPrice()) {
							StopLoss.setStopLoss(order, bidBar.getLow(), bidBar.getTime(), getClass());
						}
					} else {
						if (askBar.getClose() < order.getOpenPrice()) {
							StopLoss.setStopLoss(order, order.getOpenPrice(), bidBar.getTime(), getClass());
						} else if (askBar.getHigh() < order.getStopLossPrice()) {
							StopLoss.setStopLoss(order, askBar.getHigh(), bidBar.getTime(), getClass());
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

}
