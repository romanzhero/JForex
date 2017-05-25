package jforex.entries;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.IIndicators.AppliedPrice;

import jforex.techanalysis.Channel;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.utils.FXUtils;

public class LongMREntry implements IEntry {

	final protected Period basicTimeFrame = Period.THIRTY_MINS;
	final protected Period higherTimeFrame = Period.FOUR_HOURS;

	protected IContext context;

	protected TradeTrigger tradeTrigger;
	protected Channel channelPosition;
	protected Trend trendDetector;
	protected Momentum momentum;

	@Override
	public boolean isLong() {
		return true;
	}

	@Override
	public boolean signalFoundBool(Instrument instrument, Period period,
			IBar askBar, IBar bidBar) throws JFException {
		return signalFound(instrument, period, askBar, bidBar) != Double.MAX_VALUE;
	}

	/*
	 * Entry rules are: 1. 30' bullish trigger deep in the channel 2. 4h
	 * momentum raising: MACD-H not falling below zero, Stochs not falling in
	 * the middle 3. 4h trend flat or falling
	 */
	@Override
	public double signalFound(Instrument instrument, Period period,
			IBar askBar, IBar bidBar) throws JFException {
		if (!period.equals(basicTimeFrame))
			return Double.MIN_VALUE;

		if (!highTimeFrameConditionsOK(instrument, askBar, bidBar))
			return Double.MIN_VALUE;

		if (channelPosition.priceChannelPos(instrument, basicTimeFrame,
				OfferSide.BID, bidBar.getTime(), bidBar.getLow()) > 20.0)
			return Double.MIN_VALUE;

		IBar triggerLowBar = tradeTrigger.bullishReversalCandlePatternBar(
				instrument, basicTimeFrame, OfferSide.BID, bidBar.getTime());
		if (triggerLowBar == null)
			return Double.MIN_VALUE;
		return triggerLowBar.getLow();
	}

	private boolean highTimeFrameConditionsOK(Instrument instrument,
			IBar askBar, IBar bidBar) throws JFException {
		Trend.TREND_STATE trend4h = trendDetector.getTrendState(instrument,
				higherTimeFrame, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime());
		double trend4hStat = trendDetector.getMAsMaxDiffStDevPos(instrument,
				higherTimeFrame, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime(), FXUtils.QUARTER_WORTH_OF_4h_BARS);
		if (!((trend4h.equals(Trend.TREND_STATE.DOWN_MILD)
				|| trend4h.equals(Trend.TREND_STATE.DOWN_STRONG) || trend4h
					.equals(Trend.TREND_STATE.FRESH_DOWN)) || trend4hStat <= -1.0))
			return false;

		Momentum.MACD_STATE macd4h = momentum.getMACDState(instrument,
				higherTimeFrame, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime());
		if (macd4h.equals(Momentum.MACD_STATE.FALLING_BOTH_ABOVE_0))
			return false;
		Momentum.MACD_H_STATE macdH4h = momentum.getMACDHistogramState(
				instrument, higherTimeFrame, OfferSide.BID, AppliedPrice.CLOSE,
				bidBar.getTime());
		if (!(macdH4h.toString().startsWith("RAISING") || macdH4h.toString()
				.startsWith("TICKED_UP")))
			return false;

		if (context.getIndicators().rsi(instrument, higherTimeFrame,
				OfferSide.BID, AppliedPrice.CLOSE, 14, Filter.WEEKENDS, 1,
				bidBar.getTime(), 0)[0] > 70.0)
			return false;

		Momentum.STOCH_STATE stoch4h = momentum.getStochState(instrument,
				higherTimeFrame, OfferSide.BID, bidBar.getTime());
		double[] stochValues4h = momentum.getStochs(instrument,
				higherTimeFrame, OfferSide.BID, bidBar.getTime());
		if (stoch4h.equals(Momentum.STOCH_STATE.BEARISH_FALLING_IN_MIDDLE)
				|| (stoch4h.equals(Momentum.STOCH_STATE.BEARISH_OVERSOLD_BOTH) && stochValues4h[0] < stochValues4h[1]))
			return false;

		return true;
	}

	@Override
	public boolean signalCanceled(Instrument instrument, Period period,
			IBar askBar, IBar bidBar) throws JFException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onStartExec(IContext context) throws JFException {
		this.context = context;

		tradeTrigger = new TradeTrigger(context.getIndicators(),
				context.getHistory(), null);
		channelPosition = new Channel(context.getHistory(),
				context.getIndicators());
		trendDetector = new Trend(context.getIndicators());
		momentum = new Momentum(context.getHistory(), context.getIndicators());
	}

}
