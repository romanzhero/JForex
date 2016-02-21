package jforex.techanalysis;

import java.util.Properties;

import jforex.techanalysis.Channel;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Volatility;
import jforex.utils.FXUtils;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IConsole;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.IIndicators.AppliedPrice;

public class TASignals {

	protected IContext context;
	protected IEngine engine;
	protected IConsole console;
	protected IHistory history;
	protected IIndicators indicators;

	protected Trend trendDetector;
	protected Channel channelPosition;
	protected Momentum momentum;
	protected Volatility vola;
	protected TradeTrigger tradeTrigger;

	public TASignals(Properties props) {
		super();
	}

	public void onStartExec(IContext context) throws JFException {
		this.context = context;
		this.engine = context.getEngine();
		this.console = context.getConsole();
		this.history = context.getHistory();
		this.indicators = context.getIndicators();

		trendDetector = new Trend(indicators);
		channelPosition = new Channel(history, indicators);
		tradeTrigger = new TradeTrigger(indicators, history, null);
		momentum = new Momentum(history, indicators);
		vola = new Volatility(indicators);

	}

	public boolean LongMR1dTimeFrameCondition(Instrument instrument, IBar bidBar)
			throws JFException {
		long prevDayTime = history.getPreviousBarStart(
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		Trend.TREND_STATE trend1d = trendDetector.getTrendState(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				AppliedPrice.CLOSE, prevDayTime);
		double trend1dStat = trendDetector.getMAsMaxDiffStDevPos(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				AppliedPrice.CLOSE, prevDayTime, FXUtils.YEAR_WORTH_OF_1d_BARS);
		Momentum.MACD_STATE macd1d = momentum.getMACDState(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				AppliedPrice.CLOSE, prevDayTime);
		Momentum.MACD_H_STATE macdH1d = momentum.getMACDHistogramState(
				instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				AppliedPrice.CLOSE, prevDayTime);
		Momentum.STOCH_STATE stoch1d = momentum.getStochState(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prevDayTime);
		double[] stochValues1d = momentum.getStochs(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prevDayTime);

		// 1d uptrend reversal going on, no long trades
		if (trend1dStat > 0.5
				&& trend1d.equals(Trend.TREND_STATE.UP_STRONG)
				&& macd1d.equals(Momentum.MACD_STATE.FALLING_BOTH_ABOVE_0)
				&& macdH1d.equals(Momentum.MACD_H_STATE.FALLING_BELOW_0)
				&& (stoch1d.equals(Momentum.STOCH_STATE.FALLING_IN_MIDDLE)
						|| stoch1d.equals(Momentum.STOCH_STATE.OVERSOLD_FAST) || (stoch1d
						.equals(Momentum.STOCH_STATE.OVERSOLD_BOTH) && stochValues1d[0] < stochValues1d[1])))
			return false;

		return true;
	}

	/**
	 * @param firstEntry
	 * @return true if all the conditions according to 4h timeframe are OK,
	 *         false if no valid signal
	 */
	public boolean LongMR4hTimeFrameCondition(Instrument instrument,
			IBar bidBar, boolean firstEntry) throws JFException {
		if (firstEntry) {
			if (channelPosition.priceChannelPos(instrument, Period.FOUR_HOURS,
					OfferSide.BID, bidBar.getTime(), bidBar.getLow()) > 60.0)
				return false;

			Trend.TREND_STATE trend4h = trendDetector.getTrendState(instrument,
					Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE,
					bidBar.getTime());
			double trend4hStat = trendDetector.getMAsMaxDiffStDevPos(
					instrument, Period.FOUR_HOURS, OfferSide.BID,
					AppliedPrice.CLOSE, bidBar.getTime(),
					FXUtils.QUARTER_WORTH_OF_4h_BARS);
			if (!((trend4h.equals(Trend.TREND_STATE.DOWN_MILD)
					|| trend4h.equals(Trend.TREND_STATE.DOWN_STRONG) || trend4h
						.equals(Trend.TREND_STATE.FRESH_DOWN)) || trend4hStat <= -1.0))
				return false;

			Momentum.MACD_STATE macd4h = momentum.getMACDState(instrument,
					Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE,
					bidBar.getTime());
			if (macd4h.equals(Momentum.MACD_STATE.FALLING_BOTH_ABOVE_0))
				return false;
			Momentum.MACD_H_STATE macdH4h = momentum.getMACDHistogramState(
					instrument, Period.FOUR_HOURS, OfferSide.BID,
					AppliedPrice.CLOSE, bidBar.getTime());
			if (!(macdH4h.toString().startsWith("RAISING")
					|| macdH4h.toString().startsWith("TICKED_UP") || macdH4h
						.equals(Momentum.MACD_H_STATE.TICKED_DOWN_ABOVE_ZERO)))
				return false;

			if (context.getIndicators().rsi(instrument, Period.FOUR_HOURS,
					OfferSide.BID, AppliedPrice.CLOSE, 14, Filter.WEEKENDS, 1,
					bidBar.getTime(), 0)[0] > 70.0)
				return false;

			Momentum.STOCH_STATE stoch4h = momentum.getStochState(instrument,
					Period.FOUR_HOURS, OfferSide.BID, bidBar.getTime());
			double[] stochValues4h = momentum.getStochs(instrument,
					Period.FOUR_HOURS, OfferSide.BID, bidBar.getTime());
			if (stoch4h.equals(Momentum.STOCH_STATE.FALLING_IN_MIDDLE)
					|| (stoch4h.equals(Momentum.STOCH_STATE.OVERSOLD_BOTH) && stochValues4h[0] < stochValues4h[1]))
				return false;

			return true;
		} else {
			Momentum.MACD_STATE macd4h = momentum.getMACDState(instrument,
					Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE,
					bidBar.getTime());
			if (macd4h.toString().startsWith("RAISING"))
				return true;
			else
				return false;
		}
	}

}