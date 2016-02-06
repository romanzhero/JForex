package jforex.trades;

import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Volatility;
import jforex.utils.FXUtils;

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
	protected CandleAndMomentumDetector 
		cmd = null,
		oppositeCmd = null;
	protected TradeTrigger.TriggerDesc lastSignal = null;
	protected boolean
		aggressive = false,
		locked = false;
	Volatility vola = null;
	protected Trend trend = null;
	

	public FlatTradeSetup(IIndicators indicators, IHistory history,	IEngine engine, boolean aggressive) {
		super(indicators, history, engine);
		cmd = new CandleAndMomentumDetector(new TradeTrigger(indicators, history, null), new Momentum(history, indicators));
		oppositeCmd = new CandleAndMomentumDetector(new TradeTrigger(indicators, history, null), new Momentum(history, indicators));
		this.aggressive = aggressive;
		vola = new Volatility(indicators);
		trend = new Trend(indicators);
	}

	@Override
	public String getName() {
		return new String("Flat");
	}

	@Override
	public EntryDirection checkEntry(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter) throws JFException {		
		lastSignal = cmd.checkEntry(instrument, period, OfferSide.BID, filter, bidBar, askBar);
		locked = lastSignal != null;
		if (lastSignal == null)
			return EntryDirection.NONE;
		
		Trend.FLAT_REGIME_CAUSE currBarFlat = trend.isFlatRegime(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, filter, bidBar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS, 30.0);
		if (currBarFlat.equals(Trend.FLAT_REGIME_CAUSE.NONE)) 
			return EntryDirection.NONE;
		
		Trend.TREND_STATE trendState = trend.getTrendState(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime());
		boolean
			isMA200Highest = trend.isMA200Highest(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime()),
			isMA200Lowest = trend.isMA200Lowest(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime());
		double
			trendStrengthPerc = trend.getMAsMaxDiffPercentile(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS),
			bBandsSquezeePerc = vola.getBBandsSqueezePercentile(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, filter, bidBar.getTime(), 20, FXUtils.YEAR_WORTH_OF_4H_BARS);
		if (bBandsSquezeePerc > 75.0 &&
			((trendState.equals(Trend.TREND_STATE.UP_STRONG) && isMA200Lowest)
			|| (trendState.equals(Trend.TREND_STATE.UP_STRONG) && trendStrengthPerc > 30.0)
			|| (trendState.equals(Trend.TREND_STATE.DOWN_STRONG) && isMA200Highest)
			|| (trendState.equals(Trend.TREND_STATE.DOWN_STRONG) && trendStrengthPerc > 30.0))) {
			// mind to reset both signal detectors, so they can start searching again !!!
			cmd.reset();
			oppositeCmd.reset();
			return EntryDirection.NONE; // per definition no flat exists in high volatility and at least decent trend !!
		}
		
		if (lastSignal.type.toString().contains("BULLISH")) {
			oppositeCmd.reset();
			return EntryDirection.LONG;
		}
		else {
			oppositeCmd.reset();
			return EntryDirection.SHORT;
		}
	}

	@Override
	public void inTradeProcessing(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, IOrder order) throws JFException {
		IBar barToCheck = null;
		if (lastSignal.type.toString().contains("BULLISH"))
			barToCheck = bidBar;
		else
			barToCheck = askBar;
		if (order.getState().equals(IOrder.State.OPENED)) {
			// still waiting. Cancel if price already exceeded SL level without triggering entry stop
			if ((order.isLong() && barToCheck.getClose() < order.getStopLossPrice())
				|| (!order.isLong() && barToCheck.getClose() > order.getStopLossPrice())) {
				order.close();
				order.waitForUpdate(null);			
				afterTradeReset(instrument);
			}
		} else if (order.getState().equals(IOrder.State.FILLED)) {
			// check whether to unlock the trade - price exceeded opposite channel border at the time of the signal
			if ((order.isLong() && askBar.getClose() > lastSignal.bBandsTop)
				|| (!order.isLong() && bidBar.getClose() < lastSignal.bBandsBottom)) {
				locked = false;
				// do not reset trade completely ! Keep control over order until other setups take over !
				return;
			}
				
			// check for opposite signal. Depending on the configuration either set break even or close the trade
			TradeTrigger.TriggerDesc oppositeSignal = oppositeCmd.checkEntry(instrument, period, OfferSide.BID, filter, bidBar, askBar);
			if (aggressive) {
				if (oppositeSignal != null &&
					((order.isLong() && oppositeSignal.type.toString().contains("BEARISH")) 
					|| (!order.isLong() && oppositeSignal.type.toString().contains("BULLISH")))) {
					order.close();
					order.waitForUpdate(null);				
					afterTradeReset(instrument);
				}
			} else {
				// set to break even. However check if current price allows it ! If not set SL on extreme of the last bar (if it not already exceeded the SL !)
				if (oppositeSignal != null &&
						((order.isLong() && oppositeSignal.type.toString().contains("BEARISH")) 
						|| (!order.isLong() && oppositeSignal.type.toString().contains("BULLISH")))) {
					if (order.isLong()) {
						if (bidBar.getClose() > order.getOpenPrice()) {
							order.setStopLossPrice(order.getOpenPrice());
							order.waitForUpdate(null);
						} else if (bidBar.getLow() > order.getStopLossPrice()) {
							order.setStopLossPrice(bidBar.getLow());
							order.waitForUpdate(null);							
						}
					} else {
						if (askBar.getClose() < order.getOpenPrice()) {
							order.setStopLossPrice(order.getOpenPrice());
							order.waitForUpdate(null);
						} else if (askBar.getHigh() < order.getStopLossPrice()) {
							order.setStopLossPrice(askBar.getHigh());
							order.waitForUpdate(null);							
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
		cmd.reset();
		oppositeCmd.reset();
		lastSignal = null;
	}

	@Override
	public IOrder submitOrder(String label, Instrument instrument,	boolean isLong, double amount, IBar bidBar, IBar askBar) throws JFException {
        return engine.submitOrder(label, instrument, isLong ? IEngine.OrderCommand.BUYSTOP : IEngine.OrderCommand.SELLSTOP, amount,
        		isLong ? askBar.getHigh() : bidBar.getLow(), -1.0, lastSignal.pivotLevel, 0.0);
	}

}
