package jforex.trades.flat;

import java.util.List;
import java.util.Map;

import jforex.utils.StopLoss;
import jforex.utils.log.FlexLogEntry;

import jforex.events.TAEventDesc;
import jforex.events.TAEventDesc.TAEventType;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Trend.FLAT_REGIME_CAUSE;
import jforex.techanalysis.Trend.TREND_STATE;
import jforex.techanalysis.source.FlexTASource;
import jforex.trades.ITradeSetup;
import jforex.trades.TradeSetup;
import jforex.trades.momentum.LongStrongCandleAndMomentumDetector;
import jforex.trades.momentum.ShortStrongCandleAndMomentumDetector;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public class FlatStrongTradeSetup extends TradeSetup implements ITradeSetup {
	protected IHistory history = null;
	// Trade simply generates all long and short canlde-momentum signals. The newest one wins
	// therefore it must be ensured that their check methods are called for each bar while strategy is running !
	// Should be OK with successive calls to checkEntry and inTradeProcessing
	protected LongStrongCandleAndMomentumDetector longCmd = null;
	protected ShortStrongCandleAndMomentumDetector shortCmd = null;

	protected TradeTrigger.TriggerDesc 
		lastLongSignal = null,
		lastShortSignal = null;
	protected boolean aggressive = false;

	public FlatStrongTradeSetup(IEngine engine, IContext context, IHistory history, boolean aggressive, boolean useEntryFilters) {
		super(useEntryFilters, engine, context);
		this.history = history;
		// this way signals will be generated regardless of the channel position so they can be used both for entry and all exit checks
		// entry and exit checks must explicitly test channel position !
		longCmd = new LongStrongCandleAndMomentumDetector(100, false);
		shortCmd = new ShortStrongCandleAndMomentumDetector(0, false);
		this.aggressive = aggressive;
	}

	@Override
	public String getName() {
		return new String("FlatStrong");
	}

	@Override
	public TAEventDesc checkEntry(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, Map<String, FlexLogEntry> taValues) throws JFException {
		TradeTrigger.TriggerDesc 
			currLongSignal = longCmd.checkEntry(instrument, period, OfferSide.BID, filter, bidBar, askBar, taValues), 
			currShortSignal = shortCmd.checkEntry(instrument, period, OfferSide.BID, filter, bidBar, askBar, taValues);

		if (currLongSignal == null && currShortSignal == null)
			return null;

		Trend.IchiDesc ichi = (Trend.IchiDesc)taValues.get(FlexTASource.ICHI).getValue();
		if ((currLongSignal != null && (askBar.getLow() > ichi.cloudBottom || askBar.getClose() > ichi.cloudTop))
			|| (currShortSignal != null && (bidBar.getHigh() < ichi.cloudTop || bidBar.getClose() < ichi.cloudBottom)))
			return null;

		Trend.FLAT_REGIME_CAUSE currBarFlat = (Trend.FLAT_REGIME_CAUSE)taValues.get(FlexTASource.FLAT_REGIME).getValue();
		Trend.TREND_STATE trendState = taValues.get(FlexTASource.TREND_ID).getTrendStateValue();
		boolean 
			isMA200Highest = taValues.get(FlexTASource.MA200_HIGHEST).getBooleanValue(), 
			isMA200Lowest = taValues.get(FlexTASource.MA200_LOWEST).getBooleanValue();
		double 
			trendStrengthPerc = taValues.get(FlexTASource.MAs_DISTANCE_PERC).getDoubleValue(), 
			bBandsSquezeePerc = taValues.get(FlexTASource.BBANDS_SQUEEZE_PERC).getDoubleValue();
//		if (bBandsSquezeePerc > 75.0
//			&& ((trendState.equals(Trend.TREND_STATE.UP_STRONG) && isMA200Lowest)
//				|| (trendState.equals(Trend.TREND_STATE.UP_STRONG) && trendStrengthPerc > 30.0)
//				|| (trendState.equals(Trend.TREND_STATE.DOWN_STRONG) && isMA200Highest) 
//				|| (trendState.equals(Trend.TREND_STATE.DOWN_STRONG) && trendStrengthPerc > 30.0))) {
//			return null; // per definition no flat exists in high volatility and at least decent trend !!
//		}
		if ((trendState.equals(Trend.TREND_STATE.UP_STRONG) && isMA200Lowest && askBar.getLow() > ichi.slowLine)
			|| (trendState.equals(Trend.TREND_STATE.DOWN_STRONG) && isMA200Highest && bidBar.getHigh() < ichi.slowLine))
			return null;
		
		// there is a signal ! Due to dual Stoch / SMI check and Ichi cloud confirmation allow all entries in favorable half of the channel
		boolean 
			bulishSignal = currLongSignal != null && currLongSignal.channelPosition < 50,
			bearishSignal = currShortSignal != null && currShortSignal.channelPosition > 50;
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
	public void inTradeProcessing(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, IOrder order, Map<String, FlexLogEntry> taValues, List<TAEventDesc> marketEvents) throws JFException {
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
			if ((order.isLong() && (barToCheck.getClose() < order.getStopLossPrice() || !longCmd.momentumConfirms(taValues)))
				|| (!order.isLong() && (barToCheck.getClose() > order.getStopLossPrice() || !shortCmd.momentumConfirms(taValues)))) {
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
			
			// 1. for the time being no breakeven setting, but only move SL to nearer (to entry) Ichi cloud border
			// when at least two bars closed below it. Ideally this should be STATE so SL can be moved up AND down
			// along cloud border. Will be done in 2nd step
			List<IBar> lastTwoBars = history.getBars(instrument, period, OfferSide.BID, filter, 2, bidBar.getTime(), 0);
			Trend.IchiDesc ichi = (Trend.IchiDesc)taValues.get(FlexTASource.ICHI).getValue();
			boolean
				longProtectSignal = lastTwoBars.get(0).getClose() > ichi.prevCloudTop && lastTwoBars.get(1).getClose() > ichi.cloudTop,
				shortProtectSignal = lastTwoBars.get(0).getClose() < ichi.prevCloudBottom && lastTwoBars.get(1).getClose() < ichi.cloudBottom;				
/*			if (order.isLong() && longProtectSignal) {
				lastTradingEvent = "long move SL signal";	
				if (ichi.cloudBottom > order.getStopLossPrice())
					FXUtils.setStopLoss(order, ichi.cloudBottom, bidBar.getTime(), this.getClass());
			} else if (!order.isLong() && shortProtectSignal) {
				lastTradingEvent = "short move SL signal";				
				if (ichi.cloudTop < order.getStopLossPrice())
					FXUtils.setStopLoss(order, ichi.cloudTop, bidBar.getTime(), this.getClass());
			}*/
			
			// 2. move SL to protect profit. Goal is to let  starting trends develop ("let your profits run"), not to take a lot of profit in this setup
			// Events to move SL are: 
			// 1) opposite signal
			// 2) maybe opposite "normal" ("weak") Flat signal but only after decent profit, 2-3 ATRs
			// 3) maybe strong opposite candle signals but only after decent profit, 2-3 ATRs
			// Filter NOT to move the SL (let trade run) are (only long version, short all the opposite):
			// 1) TrendID = 6 and (enough MA distance (FLAT_REGIME_CAUSE <> MAs_CLOSE) or MA200 lowest)
			// 2) and last bar low above SLOW Ichi and all MAs
			// Note: while all MAs within channel SL will be moved !
			TREND_STATE trendId = taValues.get(FlexTASource.TREND_ID).getTrendStateValue();
			FLAT_REGIME_CAUSE isFlat = (FLAT_REGIME_CAUSE)taValues.get(FlexTASource.FLAT_REGIME).getValue();
			double[][] mas = taValues.get(FlexTASource.MAs).getDa2DimValue();
			double
				ma20 = mas[1][0],
				bBandsSqueeze = taValues.get(FlexTASource.BBANDS_SQUEEZE_PERC).getDoubleValue();
			boolean 
				isMA200Highest = taValues.get(FlexTASource.MA200_HIGHEST).getBooleanValue(), 
				isMA200Lowest = taValues.get(FlexTASource.MA200_LOWEST).getBooleanValue();
			if ((order.isLong() 
				&& ((trendId.equals(TREND_STATE.UP_STRONG) && (!isFlat.equals(FLAT_REGIME_CAUSE.MAs_CLOSE) || isMA200Lowest) && bidBar.getLow() > ichi.slowLine)
					|| bidBar.getHigh() > ichi.fastLine
					|| (bidBar.getHigh() > ma20 && bBandsSqueeze < 30)))
				||
				(!order.isLong() 
				&& ((trendId.equals(TREND_STATE.DOWN_STRONG) && (!isFlat.equals(FLAT_REGIME_CAUSE.MAs_CLOSE) || isMA200Highest) && askBar.getHigh() < ichi.slowLine)
					|| askBar.getLow() < ichi.fastLine
					|| (askBar.getLow() < ma20 && bBandsSqueeze < 30)))
				)
				return;
				
			boolean
				longExitSignal = currShortSignal != null,
				shortExitSignal = currLongSignal != null; 
			if ((order.isLong() && longExitSignal)
				|| (!order.isLong() && shortExitSignal)) {
				lastTradingEvent = "move SL due to opposite flat signal";				
				if (order.isLong() && bidBar.getLow() > order.getStopLossPrice()) {
					StopLoss.setStopLoss(order, bidBar.getLow(), bidBar.getTime(), getClass());
				} else if (!order.isLong() && askBar.getHigh() < order.getStopLossPrice()) {
					StopLoss.setStopLoss(order, askBar.getHigh(), bidBar.getTime(), getClass());
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
