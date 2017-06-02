package jforex.explorers;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import jforex.BasicTAStrategy;
import jforex.techanalysis.TradeTrigger;
import jforex.utils.FXUtils;
import jforex.utils.log.FlexLogEntry;
import jforex.emailflex.EMailConfiguration;
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

public class FlexStatsCollector extends BasicTAStrategy implements IStrategy {

	protected EMailConfiguration flexMailGenerator = new EMailConfiguration();

	public FlexStatsCollector(Properties p) {
		super(p);
	}

	@Override
	public void onStart(IContext context) throws JFException {
		super.onStartExec(context);
		flexMailGenerator.parseConfFile(
				conf.getProperty("flexEmailConfigFile"), conf);
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException {
	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		// log.print("Entered onBar before filtering for timeframe " +
		// period.toString() + " and bidBar time of " +
		// FXUtils.getFormatedBarTimeWithSecs(bidBar));
		if (!tradingAllowed(bidBar.getTime())) {
			// log.print("onBar - trading not allowed; timeframe " +
			// period.toString() + " and bidBar time of " +
			// FXUtils.getFormatedBarTimeWithSecs(bidBar));
			return;
		}

		// for all subscribed instruments i.e. no filtering there
		/*
		 * if (period.equals(Period.FIVE_MINS)) {
		 * //log.print("onBar - for 5mins; timeframe " + period.toString() +
		 * " and bidBar time of " + FXUtils.getFormatedBarTimeWithSecs(bidBar));
		 * sendReportMail(instrument, period, bidBar, null); return; }
		 */
		if (!(period.equals(Period.THIRTY_MINS) || period.equals(Period.FOUR_HOURS))) {
			// log.print("onBar - irrelevant timeframe; timeframe " +
			// period.toString() + " and bidBar time of " +
			// FXUtils.getFormatedBarTimeWithSecs(bidBar));
			return;
		}
		log.print("Entered onBar AFTER filtering for timeframe "
				+ period.toString() + " and bidBar time of "
				+ FXUtils.getFormatedBarTimeWithSecs(bidBar));

		String mailBody = null, mailSubject = null;
		if (period.equals(Period.THIRTY_MINS)) {
			List<FlexLogEntry> logLine = new ArrayList<FlexLogEntry>();

			mailBody = flexMailGenerator.getMailText(instrument, period,
					bidBar, history, indicators, trendDetector,
					channelPosition, momentum, vola, tradeTrigger, conf,
					logLine, logDB);
			mailSubject = new String("FX pair report for " + period.toString()
					+ " timeframe: " + instrument.toString() + " at "
					+ FXUtils.getFormatedTimeCET(bidBar.getTime()));
			if (conf.getProperty("sendMail", "no").equals("yes"))
				sendReportMail(mailSubject, mailBody);
			if (conf.getProperty("logMail", "no").equals("yes"))
				log.print(mailBody);
		}
	}

	private void sendReportMail(String subject, String body) throws JFException {
		StringTokenizer t = new StringTokenizer(
				conf.getProperty("mail_recipients"), ";");
		int noOfRecepients = t.countTokens();
		if (noOfRecepients == 0)
			return;

		String[] recepients = new String[noOfRecepients];
		int i = 0;
		while (t.hasMoreTokens()) {
			recepients[i++] = t.nextToken();
		}

		sendMail("romanzhero@gmail.com", recepients, subject, body);
	}

	protected void addCandles(Instrument instrument, IBar bidBar,
			Period period, List<FlexLogEntry> logLine) throws JFException {
		double bar30minStat = tradeTrigger.barLengthStatPos(instrument, period,
				OfferSide.BID, bidBar, FXUtils.MONTH_WORTH_OF_30min_BARS);
		logLine.add(new FlexLogEntry("barStat30min", new Double(bar30minStat),
				FXUtils.df1));
		bar30minStat = tradeTrigger.avgBarLength(instrument, period,
				OfferSide.BID, bidBar, FXUtils.MONTH_WORTH_OF_30min_BARS);
		logLine.add(new FlexLogEntry("bar30minAvgSize",
				new Double(bar30minStat), FXUtils.df1));

		double bar30minOverlap = tradeTrigger.previousBarOverlap(instrument,
				period, OfferSide.BID, bidBar.getTime());
		logLine.add(new FlexLogEntry("prevBarOverlap30min", new Double(
				bar30minOverlap), FXUtils.df1));

		String candleTrigger30minStr = new String();
		TradeTrigger.TriggerDesc bullishTriggerDesc = null, bearishTriggerDesc = null;
		if ((bullishTriggerDesc = tradeTrigger
				.bullishReversalCandlePatternDesc(instrument, period,
						OfferSide.BID, bidBar.getTime())) != null) {
			candleTrigger30minStr += bullishTriggerDesc.type.toString();
			logLine.add(new FlexLogEntry("bullishCandleTriggerChannelPos30min",
					new Double(bullishTriggerDesc.channelPosition), FXUtils.df1));
			logLine.add(new FlexLogEntry("bullishPivotLevel30min", new Double(
					bullishTriggerDesc.pivotLevel), FXUtils.df5));
			logLine.add(new FlexLogEntry(
					"bullishCandleTriggerKChannelPos30min", new Double(
							bullishTriggerDesc.keltnerChannelPosition),
					FXUtils.df1));
		} else {
			logLine.add(new FlexLogEntry("bullishCandleTriggerChannelPos30min",
					new Double(0), FXUtils.df1));
			logLine.add(new FlexLogEntry("bullishPivotLevel30min",
					new Double(0), FXUtils.df5));
			logLine.add(new FlexLogEntry(
					"bullishCandleTriggerKChannelPos30min", new Double(0),
					FXUtils.df1));
		}
		if ((bearishTriggerDesc = tradeTrigger
				.bearishReversalCandlePatternDesc(instrument, period,
						OfferSide.BID, bidBar.getTime())) != null) {
			if (candleTrigger30minStr.length() > 0)
				candleTrigger30minStr += " AND "
						+ bearishTriggerDesc.type.toString();
			else
				candleTrigger30minStr += bearishTriggerDesc.type.toString();
			logLine.add(new FlexLogEntry("bearishCandleTriggerChannelPos30min",
					new Double(bearishTriggerDesc.channelPosition), FXUtils.df1));
			logLine.add(new FlexLogEntry("bearishPivotLevel30min", new Double(
					bearishTriggerDesc.pivotLevel), FXUtils.df5));
			logLine.add(new FlexLogEntry(
					"bearishCandleTriggerKChannelPos30min", new Double(
							bearishTriggerDesc.keltnerChannelPosition),
					FXUtils.df1));
		} else {
			logLine.add(new FlexLogEntry("bearishCandleTriggerChannelPos30min",
					new Double(0), FXUtils.df1));
			logLine.add(new FlexLogEntry("bearishPivotLevel30min",
					new Double(0), FXUtils.df5));
			logLine.add(new FlexLogEntry(
					"bearishCandleTriggerKChannelPos30min", new Double(0),
					FXUtils.df1));
		}

		logLine.add(new FlexLogEntry("CandleTrigger30min",
				candleTrigger30minStr.length() > 0 ? candleTrigger30minStr
						: "none"));

		double bar4hStat = 0.0;
		String candleTrigger4hStr = new String();
		TradeTrigger.TriggerDesc bearishCandleTriggerDesc4h = null, bullishCandleTriggerDesc4h = null;
		if (period.equals(Period.FOUR_HOURS)) {
			bullishCandleTriggerDesc4h = tradeTrigger
					.bullishReversalCandlePatternDesc(instrument,
							Period.FOUR_HOURS, OfferSide.BID, bidBar.getTime());
			if (bullishCandleTriggerDesc4h != null) {
				candleTrigger4hStr += bullishCandleTriggerDesc4h.type
						.toString();
				logLine.add(new FlexLogEntry(
						"bullishCandleTriggerChannelPos4h", new Double(
								bullishCandleTriggerDesc4h.channelPosition),
						FXUtils.df1));
				logLine.add(new FlexLogEntry("bullishPivotLevel4h", new Double(
						bullishCandleTriggerDesc4h.pivotLevel), FXUtils.df5));
			} else {
				logLine.add(new FlexLogEntry(
						"bullishCandleTriggerChannelPos4h", new Double(0),
						FXUtils.df1));
				logLine.add(new FlexLogEntry("bullishPivotLevel4h", new Double(
						0), FXUtils.df5));
			}
			if ((bearishCandleTriggerDesc4h = tradeTrigger
					.bearishReversalCandlePatternDesc(instrument,
							Period.FOUR_HOURS, OfferSide.BID, bidBar.getTime())) != null) {
				if (candleTrigger4hStr.length() > 0)
					candleTrigger4hStr += " AND "
							+ bearishCandleTriggerDesc4h.type.toString();
				else
					candleTrigger4hStr += bearishCandleTriggerDesc4h.type
							.toString();
				logLine.add(new FlexLogEntry(
						"bearishCandleTriggerChannelPos4h", new Double(
								bearishCandleTriggerDesc4h.channelPosition),
						FXUtils.df1));
				logLine.add(new FlexLogEntry("bearishPivotLevel4h", new Double(
						bearishCandleTriggerDesc4h.pivotLevel), FXUtils.df5));
			} else {
				logLine.add(new FlexLogEntry(
						"bearishCandleTriggerChannelPos4h", new Double(0),
						FXUtils.df1));
				logLine.add(new FlexLogEntry("bearishPivotLevel4h", new Double(
						0), FXUtils.df5));
			}

			bar4hStat = tradeTrigger.barLengthStatPos(instrument,
					Period.FOUR_HOURS, OfferSide.BID, bidBar,
					FXUtils.QUARTER_WORTH_OF_4h_BARS);
			logLine.add(new FlexLogEntry("CandleTrigger4h", candleTrigger4hStr
					.length() > 0 ? candleTrigger4hStr : "none"));
			logLine.add(new FlexLogEntry("barStat4h", new Double(bar4hStat),
					FXUtils.df1));
		} else {
			logLine.add(new FlexLogEntry("CandleTrigger4h", "n/a"));
			logLine.add(new FlexLogEntry("barStat4h", ""));
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
		// ib.disconnect();
		log.close();
	}

	@Override
	protected String getStrategyName() {
		return "TwoTFStatsCollector";
	}

	@Override
	protected String getReportFileName() {
		return "TwoTFStatsCollector_";
	}

}
