package jforex.explorers;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import jforex.BasicTAStrategy;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger.TriggerType;
import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;

import com.dukascopy.api.Configurable;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.IIndicators.AppliedPrice;

public class MomentumPlayExplorer extends BasicTAStrategy implements IStrategy {

	protected boolean headerPrinted = false;

	@Configurable("Period")
	public Period selectedPeriod = Period.THIRTY_MINS;

	@Override
	public void onStart(IContext context) throws JFException {
		super.onStartExec(context);
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException {
	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar,
			IBar bidBar) throws JFException {

		// for all subscribed instruments i.e. no filtering there
		if (!period.equals(selectedPeriod)) {
			return;
		}

		double[] MACDs4h = momentum.getMACDs(instrument, Period.FOUR_HOURS,
				OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime());
		double[] stochs4h = momentum.getStochs(instrument, Period.FOUR_HOURS,
				OfferSide.BID, bidBar.getTime());
		double ATR30min = vola.getATR(instrument, selectedPeriod,
				OfferSide.BID, bidBar.getTime(), 14)
				* Math.pow(10, instrument.getPipScale());
		double ATR4h = vola.getATR(instrument, Period.FOUR_HOURS,
				OfferSide.BID, bidBar.getTime(), 14)
				* Math.pow(10, instrument.getPipScale());
		double ATR1d = vola.getATR(instrument, Period.DAILY, OfferSide.BID,
				bidBar.getTime(), 14) * Math.pow(10, instrument.getPipScale());
		int trendID4h = trendDetector.getTrendId(instrument, Period.FOUR_HOURS,
				OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime());
		int trendID1d = trendDetector.getTrendId(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				AppliedPrice.CLOSE, bidBar.getTime());

		if (MACDs4h[Momentum.MACD_LINE] > MACDs4h[Momentum.MACD_Signal]
				&& MACDs4h[Momentum.MACD_LINE] < 0
				&& MACDs4h[Momentum.MACD_Signal] < 0
				&& (stochs4h[0] > stochs4h[1] || (stochs4h[0] > 80 && stochs4h[1] > 80))
				&& tradeTrigger.bullishReversalCandlePattern(instrument,
						selectedPeriod, OfferSide.BID, bidBar.getTime()) != Double.MIN_VALUE) {
			double lowsChannelPos = 0;
			if (tradeTrigger.getLastBullishTrigger() == TriggerType.BULLISH_1_BAR)
				lowsChannelPos = channelPosition.priceChannelPos(instrument,
						selectedPeriod, OfferSide.BID, bidBar.getTime(),
						bidBar.getLow());
			else
				lowsChannelPos = channelPosition.bullishTriggerChannelStats(
						instrument, selectedPeriod, OfferSide.BID,
						bidBar.getTime())[1];
			if (lowsChannelPos >= 10)
				return;
			List<FlexLogEntry> logLine = new ArrayList<FlexLogEntry>();

			logLine.add(new FlexLogEntry("Pair", instrument.toString()));
			logLine.add(new FlexLogEntry("Time 30min", FXUtils
					.getFormatedTimeGMT(bidBar.getTime())));
			logLine.add(new FlexLogEntry("StochFast4h",
					new Double(stochs4h[0]), FXUtils.df1));
			logLine.add(new FlexLogEntry("StochSlow4h",
					new Double(stochs4h[1]), FXUtils.df1));
			logLine.add(new FlexLogEntry("StocsDiff4h", new Double(stochs4h[0]
					- stochs4h[1]), FXUtils.df1));
			logLine.add(new FlexLogEntry("TriggerType", tradeTrigger
					.getLastBullishTrigger().toString()));
			logLine.add(new FlexLogEntry("LowChannelPos30min", new Double(
					lowsChannelPos), FXUtils.df1));
			logLine.add(new FlexLogEntry("ATR30min", new Double(ATR30min),
					FXUtils.df1));
			logLine.add(new FlexLogEntry("ATR4h", new Double(ATR4h),
					FXUtils.df1));
			logLine.add(new FlexLogEntry("ATR1d", new Double(ATR1d),
					FXUtils.df1));
			logLine.add(new FlexLogEntry("TrendId4h", new Double(trendID4h),
					FXUtils.df1));
			logLine.add(new FlexLogEntry("TrendId1d", new Double(trendID1d),
					FXUtils.df1));

			if (!headerPrinted) {
				headerPrinted = true;
				log.printLabelsFlex(logLine);
			}
			log.printValuesFlex(logLine);

		}

	}

	@Override
	public void onMessage(IMessage message) throws JFException {
	}

	@Override
	public void onAccount(IAccount account) throws JFException {
	}

	@Override
	public void onStop() throws JFException {
		log.close();
	}

	public MomentumPlayExplorer(Properties p) {
		super(p);
	}

	@Override
	protected String getStrategyName() {
		return "MomentumPlayExplorer";
	}

	@Override
	protected String getReportFileName() {
		return "MomentumPlayExplorer_";
	}

}
