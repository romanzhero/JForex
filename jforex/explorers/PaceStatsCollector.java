package jforex.explorers;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import jforex.AdvancedMailCreator;
import jforex.emailflex.FlexEmailElementFactory;
import jforex.emailflex.IFlexEmailElement;
import jforex.emailflex.IFlexEmailWrapper;
import jforex.emailflex.SignalUtils;
import jforex.techanalysis.PriceZone;
import jforex.techanalysis.SRLevel;
import jforex.utils.FXUtils;
import jforex.utils.log.FlexLogEntry;

import com.dukascopy.api.Configurable;
import com.dukascopy.api.Filter;
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

public class PaceStatsCollector extends AdvancedMailCreator implements
		IStrategy {

	protected boolean headerPrinted = false, sendFilteredMail = false;

	protected static final String TREND_ID_30MIN = "TREND_ID_30MIN",
			TREND_ID_4H = "TREND_ID_4H",
			TREND_STRENGTH_30MIN = "TREND_STRENGTH_30MIN",
			TREND_STRENGTH_4H = "TREND_STRENGTH_4H";

	// used in backtesting to correctly set time for calling OTT interface
	protected long lastBarDone = -1;
	protected Period lastPeriodDone = null;

	protected boolean timeFrameFilterDone = false;
	protected List<Integer> relevantTimeFrames = new ArrayList<Integer>();
	protected Period lowestRelevantTimeFrame = null,
			secondLowestRelevantTimeFrame = null;

	public class TickerTimeFramePair {
		public String ticker;
		public int time_frame;

		public TickerTimeFramePair(String ticker, int time_frame) {
			super();
			this.ticker = ticker;
			this.time_frame = time_frame;
		}

		@Override
		public String toString() {
			return new String("[" + ticker + ", " + time_frame + "]");
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result
					+ ((ticker == null) ? 0 : ticker.hashCode());
			result = prime * result + time_frame;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			TickerTimeFramePair other = (TickerTimeFramePair) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (ticker == null) {
				if (other.ticker != null)
					return false;
			} else if (!ticker.equals(other.ticker))
				return false;
			if (time_frame != other.time_frame)
				return false;
			return true;
		}

		private PaceStatsCollector getOuterType() {
			return PaceStatsCollector.this;
		}

	}

	protected Set<TickerTimeFramePair> tickerTimeFrames = new HashSet<TickerTimeFramePair>(),
			unprocessedTickerTimeFrames = new HashSet<TickerTimeFramePair>();
	protected Map<Instrument, Boolean> dailyFillDone = new HashMap<Instrument, Boolean>();

	@Configurable("Period")
	public Period basicTimeFrame = Period.THIRTY_MINS;

	private boolean ottInterfaceCalled = false;

	public PaceStatsCollector(Properties p) {
		super(p);
	}

	@Override
	public void onStart(IContext context) throws JFException {
		super.onStartExec(context);
		if (conf.getProperty("mailToDBOnly", "no").equals("yes")
				|| conf.getProperty("barStatsToDB", "no").equals("yes")) {
			dbLogOnStart();
			dbPopulateTickerTimeFrames();
			dbGetRelevantTimeframes();
			for (int i = 0; i < FXUtils.sortedTimeFrames.size(); i++) {
				if (relevantTimeFrames.contains(FXUtils.toTimeFrameCode(
						FXUtils.sortedTimeFrames.get(i), logDB, log))) {
					lowestRelevantTimeFrame = FXUtils.sortedTimeFrames.get(i);
					if (lowestRelevantTimeFrame.equals(FXUtils.lowestTimeframeSupported))
						secondLowestRelevantTimeFrame = FXUtils.secondLowestTimeframeSupported;
					break;
				}
			}
		}
	}

	protected void dbFillDailyBars(Instrument instrument, IBar bidBar)
			throws JFException {
		DateTime barTime = new DateTime(bidBar.getTime(),
				DateTimeZone.forID("Europe/Zurich"));
		String statementStr = "SELECT BarTime FROM " + FXUtils.getDbToUse()
				+ ".tstatsentry where Instrument = '" + instrument.toString()
				+ "' and TimeFrame = 'Daily' " + " and BarTime < "
				+ getDateString(barTime) + " order by BarTime desc limit 1";
		try {
			Statement qry = logDB.createStatement();
			ResultSet result = qry.executeQuery(statementStr);
			while (result.next()) {
				Timestamp lastDailyBarFetched = result.getTimestamp("BarTime");
				GregorianCalendar c = new GregorianCalendar();
				c.setTime(lastDailyBarFetched);
				// this is as all Duka times GMT !
				long startTimeOfPrevious4hBar = history.getPreviousBarStart(
						Period.FOUR_HOURS, bidBar.getTime());
				DateTime prev4hBarTime = new DateTime(startTimeOfPrevious4hBar,
						DateTimeZone.forID("Europe/Zurich"));
				DateTime lastInDB = new DateTime(c.getTimeInMillis());
				if (lastInDB.isBefore(prev4hBarTime)) {
					DateTime lastInDBGMT = new DateTime(c.getTimeInMillis(),
							DateTimeZone.forID("GMT"));
					dbStoreIntervalData(instrument, lastInDBGMT.getMillis(),
							bidBar.getTime());
				}
			}
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.print(statementStr);
			log.close();
			System.exit(1);
		}
	}

	private void dbStoreIntervalData(Instrument instrument, long start, long end)
			throws JFException {
		List<IBar> bars = history.getBars(instrument, Period.THIRTY_MINS,
				OfferSide.BID, Filter.WEEKENDS, start, end);
		for (IBar currBar : bars) {
			if (!tradingAllowed(currBar.getTime()))
				continue;

			List<FlexLogEntry> logLine = new ArrayList<FlexLogEntry>();
			eventsSource.collectAllStats(instrument, Period.THIRTY_MINS,
					currBar, logLine);

			// each record for a separate TF. Note NOT to use this for 30min TF
			// otherwise too many records in the database !
			dbLogBarStats(instrument, Period.THIRTY_MINS, logLine.get(2)
					.getFormattedValue(), logLine);
			dbLogBarStats(instrument, Period.FOUR_HOURS, logLine.get(2)
					.getFormattedValue(), logLine);
			if (currBar.getTime() == history.getBarStart(Period.FOUR_HOURS,
					currBar.getTime())) {
				List<IBar> prev4hBars = history.getBars(instrument,
						Period.FOUR_HOURS, OfferSide.BID, Filter.WEEKENDS, 1,
						currBar.getTime(), 0);
				List<FlexLogEntry> logLine4h = new ArrayList<FlexLogEntry>();
				eventsSource.collectAllStats(instrument, Period.FOUR_HOURS,
						prev4hBars.get(0), logLine4h);
				dbLogBarStats(instrument, Period.DAILY_SUNDAY_IN_MONDAY,
						logLine4h.get(2).getFormattedValue(), logLine4h);
			}
		}
	}

	private void dbPopulateTickerTimeFrames() {
		String statementStr = new String(
				"SELECT distinct ticker, time_frame_basic as time_frame FROM "
						+ FXUtils.getDbToUse()
						+ ".vsubscriptions "
						+ "union all SELECT distinct ticker, time_frame_higher FROM "
						+ FXUtils.getDbToUse() + ".vsubscriptions");
		try {
			Statement qry = logDB.createStatement();
			ResultSet result = qry.executeQuery(statementStr);
			while (result.next()) {
				tickerTimeFrames.add(new TickerTimeFramePair(result
						.getString("ticker"), result.getInt("time_frame")));
				unprocessedTickerTimeFrames.add(new TickerTimeFramePair(result
						.getString("ticker"), result.getInt("time_frame")));

				dailyFillDone.put(
						Instrument.fromString(result.getString("ticker")),
						new Boolean(false));
			}
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.print(statementStr);
			log.close();
			System.exit(1);
		}
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException {
	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar,
			IBar bidBar) throws JFException {
		if (skipPairs.contains(instrument))
			return;

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

		if (!FXUtils.timeFrameNamesMap.containsKey(period.toString())) {
			// log.print("onBar - irrelevant timeframe; timeframe " +
			// period.toString() + ", ticker " + instrument.toString() +
			// " and bidBar time of " +
			// FXUtils.getFormatedBarTimeWithSecs(bidBar));
			return;
		}

		TickerTimeFramePair ttfp = new TickerTimeFramePair(
				instrument.toString(), FXUtils.toTimeFrameCode(period, logDB,
						log));
		// for (TickerTimeFramePair t : tickerTimeFrames) {
		// if (t.equals(ttfp)) {
		// log.print("Found");
		// }
		// }
		if (!tickerTimeFrames.contains(ttfp)) {
			log.print("onBar - irrelevant ticker/timeframe combination; timeframe "
					+ period.toString()
					+ ", ticker "
					+ instrument.toString()
					+ " and bidBar time of "
					+ FXUtils.getFormatedBarTimeWithSecs(bidBar));
			return;
		}

		// first entry after all the filtering. Analyze time of the bar vs.
		// timeframes and eliminate those that will not be called in
		// this strategy run at all
		if (!timeFrameFilterDone
				&& (period.equals(lowestRelevantTimeFrame) || period
						.equals(secondLowestRelevantTimeFrame))) {
			// Regardless of timeframe order in which onBar is called, it's OK
			// to call filtering only on the first
			// call for the lowest timeframe. Maybe there will be calls before
			// for higher timeframes, but they will
			// anyways mark those pair/timeframe combinations done
			timeFrameFilterDone = true;
			long relevantEndTime = bidBar.getTime() + period.getInterval(), calcTime = relevantEndTime - 60 * 1000;
			boolean skipCurrTF = false;
			for (Integer i : relevantTimeFrames) {
				Period currTF = FXUtils.fromTimeFrameCode(i.intValue(), logDB,
						log);
				long currEndTime = history.getBarStart(currTF, calcTime)
						+ currTF.getInterval();
				if (currEndTime > relevantEndTime) {
					if (currTF.equals(period))
						skipCurrTF = true;

					Iterator<TickerTimeFramePair> i1 = tickerTimeFrames
							.iterator();
					while (i1.hasNext()) {
						TickerTimeFramePair currTTFP = i1.next();
						if (currTTFP.time_frame == i.intValue())
							i1.remove();
					}
					Iterator<TickerTimeFramePair> i2 = unprocessedTickerTimeFrames
							.iterator();
					while (i2.hasNext()) {
						TickerTimeFramePair currTTFP = i2.next();
						if (currTTFP.time_frame == i.intValue())
							i2.remove();
					}
					/*
					 * for (TickerTimeFramePair currTTFP : tickerTimeFrames) {
					 * if (currTTFP.time_frame == i.intValue())
					 * tickerTimeFrames.remove(currTTFP); } for
					 * (TickerTimeFramePair currTTFP :
					 * unprocessedTickerTimeFrames) { if (currTTFP.time_frame ==
					 * i.intValue())
					 * unprocessedTickerTimeFrames.remove(currTTFP); }
					 */
				}
			}
			// finally also check whether very instrument/timeframe combination
			// for this onBar call is now
			// relevant or not
			if (skipCurrTF)
				return;
		}

		log.print("Entered onBar AFTER filtering for instrument "
				+ instrument.toString() + ", timeframe " + period.toString()
				+ " and bidBar time of "
				+ FXUtils.getFormatedBarTimeWithSecs(bidBar));
		// Attention: 4h indicators are all calculated for 7 out 8 30min candles
		// as INCOMPLETE bars ! On purpose - immediate reaction wanted, not up
		// to 3,5 hours later !

		List<FlexLogEntry> logLine = new ArrayList<FlexLogEntry>();
		eventsSource.collectOneTimeFrameStats(instrument, period, bidBar,
				logLine);

		// special case processing for Ichi cloud cross signals - no DB storage.
		// Flagged with all 3 time frames being the same
		if (hasNoDBSubscriptions(instrument, period)) {
			sendNoDBReportMails(instrument, period, bidBar, logLine);
			if (bidBar.getTime() > lastBarDone) {
				lastBarDone = bidBar.getTime();
				lastPeriodDone = period;
				log.print(
						"Unprocessed ticker-timeframes: "
								+ FXUtils.if1
										.format(unprocessedTickerTimeFrames
												.size())
								+ ", last bar time / period set:"
								+ FXUtils.getFormatedTimeGMT(bidBar.getTime())
								+ " / " + period.toString(), true);
			}
			unprocessedTickerTimeFrames.remove(ttfp);
			if (unprocessedTickerTimeFrames.size() == 0) {
				if (conf.getProperty("triggerInterfaces", "no").equals("yes"))
					callOTTInterface();
				if (!conf.getProperty("backtest", "no").equals("yes")) {
					File requestedCall = new File("requestedCall.bin");
					if (requestedCall.exists())
						requestedCall.delete();
				}
			}

		}

		if (hasNormalSubscriptions(instrument, period)) {
			if (period.equals(Period.THIRTY_MINS)
					&& !dailyFillDone.get(instrument).booleanValue()) {
				dbFillDailyBars(instrument, bidBar);
				dailyFillDone.remove(instrument);
				dailyFillDone.put(instrument, new Boolean(true));
			}
			// each record for a separate TF. Note NOT to use this for 30min TF
			// otherwise too many records in the database !
			if (conf.getProperty("barStatsToDB", "no").equals("yes")) {
				dbLogBarStats(instrument, period, logLine.get(2)
						.getFormattedValue(), logLine);
				if (period.equals(basicTimeFrame))
					dbLogBarStats(instrument, Period.FOUR_HOURS, logLine.get(2)
							.getFormattedValue(), logLine);
				else if (period.equals(Period.FOUR_HOURS))
					dbLogBarStats(instrument, Period.DAILY_SUNDAY_IN_MONDAY,
							logLine.get(2).getFormattedValue(), logLine);
			}

			if (!headerPrinted) {
				headerPrinted = true;
				log.printLabelsFlex(logLine);
			}
			log.printValuesFlex(logLine);

			sendReportMails(instrument, period, bidBar, logLine);

			if (!conf.getProperty("backtest", "no").equals("yes")) {
				unprocessedTickerTimeFrames.remove(ttfp);
				if (unprocessedTickerTimeFrames.size() == 0) {
					File requestedCall = new File("requestedCall.bin");
					if (requestedCall.exists())
						requestedCall.delete();
				}
			}
		}
	}

	protected void dbGetRelevantTimeframes() {
		String statementStr = new String(
				"SELECT distinct time_frame_basic as time_frame FROM "
						+ FXUtils.getDbToUse() + ".vsubscriptions");
		try {
			Statement qry = logDB.createStatement();
			ResultSet result = qry.executeQuery(statementStr);
			while (result.next()) {
				relevantTimeFrames
						.add(new Integer(result.getInt("time_frame")));
			}
		} catch (SQLException ex) {
			log.print("Log database problem when fetching relevant timeframes: "
					+ ex.getMessage());
			log.print(statementStr);
			log.close();
			System.exit(1);
		}
	}

	protected void callOTTInterface() {
		if (!ottInterfaceCalled)
			ottInterfaceCalled = true;
		else
			return;

		String urlToCall;
		try {
			urlToCall = conf.getProperty("OTTinterface")
					+ URLEncoder.encode(FXUtils.getMySQLTimeStamp(SignalUtils
							.getBarEndTime(lastBarDone, lastPeriodDone)),
							"ASCII");
			log.print("Calling OTT interface at " + urlToCall);
			log.print("Result: " + FXUtils.httpGet(urlToCall));
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private boolean hasNoDBSubscriptions(Instrument instrument, Period period) {
		String dbWhere = new String("WHERE ticker = '" + instrument.toString()
				+ "' AND time_frame_basic = "
				+ FXUtils.toTimeFrameCode(period, logDB, log)
				+ " AND time_frame_basic = time_frame_higher"), statementStr = "SELECT * FROM "
				+ FXUtils.getDbToUse() + ".vsubscriptions " + dbWhere;
		try {
			Statement qry = logDB.createStatement();
			ResultSet result = qry.executeQuery(statementStr);
			return result.next();
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.print(statementStr);
			log.close();
			System.exit(1);
		}
		return false;
	}

	private boolean hasNormalSubscriptions(Instrument instrument, Period period) {
		try {
			ResultSet result = dbGetMatchingSubscriptions(instrument, period);
			return result.next();

		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.close();
			System.exit(1);
		}
		return false;
	}

	/**
	 * Fetch all the subscriptions for this time frame and this pair, and
	 * construct their emails Then send them
	 */
	private void sendReportMails(Instrument instrument, Period pPeriod,
			IBar bidBar, List<FlexLogEntry> logLine) throws JFException {
		ResultSet matchingSubscriptions = dbGetMatchingSubscriptions(
				instrument, pPeriod);
		try {
			while (matchingSubscriptions.next()) {
				String mailBody = null, mailSubject = null;
				if (FXUtils.toTimeFrameCode(pPeriod, logDB, log) == matchingSubscriptions
						.getInt("time_frame_basic")) {
					mailBody = createMailBodyBasicTF(
							matchingSubscriptions.getInt("subscription_id"),
							instrument, pPeriod, bidBar, logLine);
					mailSubject = new String("FX pair report for "
							+ pPeriod.toString() + " timeframe: "
							+ instrument.toString() + " at "
							+ FXUtils.getFormatedTimeCET(bidBar.getTime()));
				} else if (FXUtils.toTimeFrameCode(pPeriod, logDB, log) == matchingSubscriptions
						.getInt("time_frame_higher")) {
					mailBody = createMailBodyHigherTF(
							matchingSubscriptions.getInt("subscription_id"),
							instrument, pPeriod, bidBar, logLine);
					mailSubject = new String("FX pair report for "
							+ pPeriod.toString() + " timeframe: "
							+ instrument.toString() + " at "
							+ FXUtils.getFormatedTimeCET(bidBar.getTime()));
				} else
					continue;

				String[] recepients = new String[1];
				recepients[0] = matchingSubscriptions.getString("email");

				if (conf.getProperty("sendMail", "no").equals("yes")
						&& (!filterMail
								|| mailBody.contains("time frame: 4 Hours") || sendFilteredMail))
					sendMail("romanzhero@gmail.com", recepients, mailSubject,
							mailBody);

				if (conf.getProperty("logMail", "no").equals("yes"))
					log.print(mailBody);
			}
		} catch (SQLException e) {
			log.print("Log database problem: " + e.getMessage()
					+ " while trying to get subscriptions");
			log.close();
			System.exit(1);
		}

	}

	/**
	 * Fetch all the subscriptions for this time frame and this pair, and
	 * construct their emails Then send them
	 */
	private void sendNoDBReportMails(Instrument instrument, Period pPeriod,
			IBar bidBar, List<FlexLogEntry> logLine) throws JFException {
		ResultSet matchingSubscriptions = dbGetNoDBSubscriptions(instrument,
				pPeriod);
		try {
			while (matchingSubscriptions.next()) {
				String mailBody = null, mailSubject = null;
				if (FXUtils.toTimeFrameCode(pPeriod, logDB, log) == matchingSubscriptions
						.getInt("time_frame_basic")) {
					mailBody = createMailBodySimple(
							matchingSubscriptions.getInt("subscription_id"),
							instrument, pPeriod, bidBar, logLine);
					mailSubject = new String("FX pair report for "
							+ pPeriod.toString() + " timeframe: "
							+ instrument.toString() + " at "
							+ FXUtils.getFormatedTimeCET(bidBar.getTime()));
				} else
					continue;

				String[] recepients = new String[1];
				recepients[0] = matchingSubscriptions.getString("email");

				if (conf.getProperty("sendMail", "no").equals("yes")
						&& mailBody != null)
					sendMail("romanzhero@gmail.com", recepients, mailSubject,
							mailBody);

				if (conf.getProperty("logMail", "no").equals("yes")
						&& mailBody != null)
					log.print(mailBody);
			}
		} catch (SQLException e) {
			log.print("Log database problem: " + e.getMessage()
					+ " while trying to get subscriptions");
			log.close();
			System.exit(1);
		}

	}

	private ResultSet dbGetMatchingSubscriptions(Instrument instrument,
			Period pPeriod) {
		String dbWhere = new String("WHERE ticker = '" + instrument.toString()
				+ "' AND (time_frame_basic = "
				+ FXUtils.toTimeFrameCode(pPeriod, logDB, log)
				+ " OR time_frame_higher = "
				+ FXUtils.toTimeFrameCode(pPeriod, logDB, log) + ")"
				+ " AND time_frame_basic <> time_frame_higher"), statementStr = "SELECT * FROM "
				+ FXUtils.getDbToUse() + ".vsubscriptions " + dbWhere;
		try {
			Statement qry = logDB.createStatement();
			ResultSet result = qry.executeQuery(statementStr);
			return result;
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.print(statementStr);
			log.close();
			System.exit(1);
		}
		return null;
	}

	private ResultSet dbGetNoDBSubscriptions(Instrument instrument,
			Period pPeriod) {
		String dbWhere = new String("WHERE ticker = '" + instrument.toString()
				+ "' AND time_frame_basic = "
				+ FXUtils.toTimeFrameCode(pPeriod, logDB, log)
				+ " AND time_frame_higher = time_frame_basic"), statementStr = "SELECT * FROM "
				+ FXUtils.getDbToUse() + ".vsubscriptions " + dbWhere;
		try {
			Statement qry = logDB.createStatement();
			ResultSet result = qry.executeQuery(statementStr);
			return result;
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.print(statementStr);
			log.close();
			System.exit(1);
		}
		return null;
	}

	private ResultSet dbGetSubscriptionOptions(Instrument instrument,
			int subscription_id) {
		String statementStr = new String(
				"SELECT `group`, option_name, `order` FROM "
						+ FXUtils.getDbToUse() + ".v_subscription_options "
						+ "WHERE subscription_id = " + subscription_id
						+ " and ticker = '" + instrument.toString()
						+ "' order by `order`");
		try {
			Statement qry = logDB.createStatement();
			ResultSet result = qry.executeQuery(statementStr);
			return result;
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.print(statementStr);
			log.close();
			System.exit(1);
		}
		return null;
	}

	protected String createMailBodyBasicTF(int subscription_id,
			Instrument instrument, Period pPeriod, IBar bidBar,
			List<FlexLogEntry> logLine) throws JFException {

		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}

		String mailBody = new String();

		mailBody = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"
				+ "<html xmlns=\"http://www.w3.org/1999/xhtml\">"
				+ "<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" /><title>Table</title></head><body>"
				+ "Report for "
				+ instrument.toString()
				+ ", "
				+ FXUtils.getFormatedTimeCET(bidBar.getTime())
				+ " CET (time frame: " + pPeriod.toString() + ")";

		mailBody += createMailBodyTop(instrument, pPeriod, bidBar, logLine);

		ResultSet options = dbGetSubscriptionOptions(instrument,
				subscription_id);
		// need to re-arrange the list of options:
		// 1. check if an option requires a wrapper (like Overviews)
		// 2. if yes, create the wrapper and remove this option from the list,
		// add wrapper on its position
		// 3. go through the rest of the list and check for other options
		// requiring the same wrapper. Remove them from list and add to wrapper
		List<IFlexEmailElement> checkList = new ArrayList<IFlexEmailElement>(), resultList = new ArrayList<IFlexEmailElement>();
		try {
			while (options.next()) {
				IFlexEmailElement e = FlexEmailElementFactory.create(options
						.getString("option_name"));
				if (e == null) {
					log.print("No mapped Java class for DB element "
							+ options.getString("option_name"));
				} else
					checkList.add(e);
			}
			for (int i = 0; i < checkList.size(); i++) {
				IFlexEmailElement currEl = checkList.get(i);
				if (currEl.needsWrapper()) {
					IFlexEmailWrapper wrapper = currEl.getWrapper();
					wrapper.add(currEl);
					resultList.add(wrapper);
					for (int j = i + 1; j < checkList.size(); j++) {
						IFlexEmailElement checkWrap = checkList.get(j);
						// check ! Critical !
						if (checkWrap.getWrapper().getClass()
								.equals(wrapper.getClass())) {
							wrapper.add(checkWrap);
							checkList.remove(j);
							j--;
						}
					}
					checkList.remove(i);
					i--;
				} else
					resultList.add(currEl);
			}
			for (IFlexEmailElement e : resultList) {
				mailBody += e.printHTML(instrument, pPeriod, bidBar, logDB);
			}
			mailBody += createMailBodyBottom(instrument, pPeriod, bidBar,
					logLine);
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage()
					+ " while trying to get subscription options");
			log.close();
			System.exit(1);
		}
		return mailBody + "</body></html>";
	}

	protected String createMailBodySimple(int subscription_id,
			Instrument instrument, Period pPeriod, IBar bidBar,
			List<FlexLogEntry> logLine) throws JFException {

		String mailBody = new String();
		boolean notEmpty = false, signal = false;

		mailBody = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"
				+ "<html xmlns=\"http://www.w3.org/1999/xhtml\">"
				+ "<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" /><title>Table</title></head><body>"
				+ "Report for "
				+ instrument.toString()
				+ " (special), "
				+ FXUtils.getFormatedTimeCET(bidBar.getTime())
				+ " CET (time frame: " + pPeriod.toString() + ")";

		ResultSet options = dbGetSubscriptionOptions(instrument,
				subscription_id);
		// need to re-arrange the list of options:
		// 1. check if an option requires a wrapper (like Overviews)
		// 2. if yes, create the wrapper and remove this option from the list,
		// add wrapper on its position
		// 3. go through the rest of the list and check for other options
		// requiring the same wrapper. Remove them from list and add to wrapper
		List<IFlexEmailElement> checkList = new ArrayList<IFlexEmailElement>(), resultList = new ArrayList<IFlexEmailElement>();
		try {
			while (options.next()) {
				IFlexEmailElement e = FlexEmailElementFactory.create(options
						.getString("option_name"));
				if (e == null) {
					log.print("No mapped Java class for DB element "
							+ options.getString("option_name"));
				} else
					checkList.add(e);
			}
			for (int i = 0; i < checkList.size(); i++) {
				IFlexEmailElement currEl = checkList.get(i);
				if (currEl.needsWrapper()) {
					IFlexEmailWrapper wrapper = currEl.getWrapper();
					wrapper.add(currEl);
					resultList.add(wrapper);
					for (int j = i + 1; j < checkList.size(); j++) {
						IFlexEmailElement checkWrap = checkList.get(j);
						// check ! Critical !
						if (checkWrap.getWrapper().getClass()
								.equals(wrapper.getClass())) {
							wrapper.add(checkWrap);
							checkList.remove(j);
							j--;
						}
					}
					checkList.remove(i);
					i--;
				} else
					resultList.add(currEl);
			}

			mailBody += printSimpleTableHeader();
			for (IFlexEmailElement e : resultList) {
				String html = null;
				if (e.isGeneric())
					html = e.print(instrument, pPeriod, bidBar, logLine, logDB);
				else
					html = e.print(instrument, pPeriod, bidBar, history,
							indicators, trendDetector, channelPosition,
							momentum, vola, tradeTrigger, conf, logLine, logDB);
				if (html != null && html.length() > 0
						&& !html.toLowerCase().contains("not implemented")
						&& !html.toUpperCase().contains("NONE")) {
					mailBody += html;
					notEmpty = true;
					if (e.isSignal())
						signal = true;
				}
			}
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage()
					+ " while trying to get subscription options");
			log.close();
			System.exit(1);
		}
		if (signal && notEmpty)
			return mailBody + "</table></body></html>";
		else
			return null;
	}

	@Override
	public void onMessage(IMessage message) throws JFException {
	}

	@Override
	public void onAccount(IAccount account) throws JFException {
	}

	@Override
	public void onStop() throws JFException {
		if (conf.getProperty("triggerInterfaces", "no").equals("yes")
				&& lastBarDone != -1) {
			callOTTInterface();
		}
		log.close();
	}

	@Override
	protected String getStrategyName() {
		return "PaceStatsCollector";
	}

	@Override
	protected String getReportFileName() {
		return getStrategyName() + "_";
	}

	protected String createMailBodyHigherTF(int subscription_id,
			Instrument instrument, Period pPeriod, IBar bidBar,
			List<FlexLogEntry> logLine) throws JFException {
		// stab until proper HTML implemented
		return createMailBody4h(instrument, pPeriod, bidBar, logLine);
	}

	protected String printSimpleTableHeader() {
		String res = new String();
		res += "<table width=\"780\" border=\"0\" cellspacing=\"2\" cellpadding=\"0\" style=\"text-align:left; vertical-align:middle; font-size:12px; line-height:20px; font-family:Arial, Helvetica, sans-serif; border:1px solid #f4f4f4;\">";
		return res;
	}

	protected String createMailBodyTop(Instrument instrument, Period pPeriod,
			IBar bidBar, List<FlexLogEntry> logLine) throws JFException {

		sendFilteredMail = false;

		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}

		String mailBody = new String(),

		bullishCandlesText = new String(), bullishChannelPosText = new String(), bullish4hChannelPosText = new String(), bullishBreakOutText = new String(), bullishOrderText = new String(), bullishVicinityText = new String(), lowRiskHighQualitySignalsBullish = new String(),

		bearishCandlesText = new String(), bearishChannelPosText = new String(), bearish4hChannelPosText = new String(), bearishBreakOutText = new String(), bearishOrderText = new String(), bearishVicinityText = new String(), lowRiskHighQualitySignalsBearish = new String(),

		noGoZonesText = new String();

		Double barHigh = (Double) mailValuesMap.get("barHigh30min").getValue();
		Double barLow = (Double) mailValuesMap.get("barLow30min").getValue();
		mailBody = printSimpleTableHeader()
				+ "<tr><td>"
				+ getPriceChange(instrument, bidBar, mailStringsMap,
						mailValuesMap, barHigh, barLow) + "</td></tr></table>";

		String valueToShow = mailStringsMap.get("CandleTrigger30min"), bullishCandles = null, bearishCandles = null;
		boolean twoCandleSignals = false;

		if (valueToShow != null && !valueToShow.toUpperCase().equals("NONE")) {
			int positionOfAnd = valueToShow.indexOf(" AND ");
			if (positionOfAnd > 0) {
				twoCandleSignals = true;
				String firstCandle = valueToShow.substring(0, positionOfAnd);
				String secondCandle = valueToShow.substring(positionOfAnd + 5);
				if (firstCandle.contains("BULLISH")) {
					bullishCandles = new String(firstCandle);
					bearishCandles = new String(secondCandle);
				} else {
					bullishCandles = new String(secondCandle);
					bearishCandles = new String(firstCandle);
				}
			} else {
				if (valueToShow.contains("BULLISH"))
					bullishCandles = new String(valueToShow);
				else
					bearishCandles = new String(valueToShow);
			}

			if (valueToShow.contains("BULLISH")) {
				// TODO: check for any of explicit patterns found by JForex API
				String candleDesc = tradeTrigger.bullishCandleDescription(
						instrument, pPeriod, OfferSide.BID, bidBar.getTime());
				if (twoCandleSignals)
					bullishCandlesText += bullishCandles
							+ (candleDesc != null && candleDesc.length() > 0 ? " ("
									+ candleDesc + "), "
									: ", ");
				else
					bullishCandlesText += valueToShow
							+ (candleDesc != null && candleDesc.length() > 0 ? " ("
									+ candleDesc + "), "
									: ", ");
				if (!valueToShow.contains("BULLISH_1_BAR"))
					bullishCandlesText += "combined ";
				bullishCandlesText += "signal StDev size: "
						+ mailStringsMap
								.get("bullishTriggerCombinedSizeStat30min")
						+ ",<br />lower handle : "
						+ mailStringsMap
								.get("bullishTriggerCombinedLHPerc30min")
						+ "%"
						+ ", real body : "
						+ mailStringsMap
								.get("bullishTriggerCombinedBodyPerc30min")
						+ "% ("
						+ mailStringsMap
								.get("bullishTriggerCombinedBodyDirection30min")
						+ "), upper handle : "
						+ mailStringsMap
								.get("bullishTriggerCombinedUHPerc30min")
						+ "%<br />";
				bullishChannelPosText += "pivot bar low 30min channel positions: BBands "
						+ mailStringsMap
								.get("bullishCandleTriggerChannelPos30min")
						+ "% / Keltner channel: "
						+ mailStringsMap
								.get("bullishCandleTriggerKChannelPos30min")
						+ "%"
						+ "<br />(volatility30min (BBands squeeze): "
						+ mailStringsMap.get("volatility30min") + "%)";
				bullish4hChannelPosText = "4h channel position: "
						+ mailStringsMap
								.get("bullishPivotLevelHigherTFChannelPos30min")
						+ "%<br />";
				double barHighPerc = (Double) mailValuesMap.get(
						"barHighChannelPos30min").getValue();
				if (barHighPerc > 100)
					bullishBreakOutText = "WARNING ! Possible breakout, careful with going long ! Bar high channel position "
							+ mailStringsMap.get("barHighChannelPos30min")
							+ "%<br />";

				double avgHandleSize = tradeTrigger.avgHandleLength(instrument,
						basicTimeFrame, OfferSide.BID, bidBar,
						FXUtils.MONTH_WORTH_OF_30min_BARS);
				Double barHalf = new Double(barLow.doubleValue()
						+ (barHigh.doubleValue() - barLow.doubleValue()) / 2), bullishPivotLevel = (Double) mailValuesMap
						.get("bullishPivotLevel30min").getValue(), aggressiveSL = new Double(
						bullishPivotLevel.doubleValue() - avgHandleSize), riskStandardSTP = new Double(
						(barHigh - bullishPivotLevel)
								* Math.pow(10, instrument.getPipScale())), riskAggressiveSTP = new Double(
						(barHigh - aggressiveSL)
								* Math.pow(10, instrument.getPipScale())), riskStandardLMT = new Double(
						(barHalf - bullishPivotLevel)
								* Math.pow(10, instrument.getPipScale())), riskAggressiveLMT = new Double(
						(barHalf - aggressiveSL)
								* Math.pow(10, instrument.getPipScale()));
				String colorSTP = new String(getWhite()), colorLMT = new String(
						getWhite());
				if (riskAggressiveSTP.doubleValue() < 25)
					colorSTP = getGreen();
				if (riskAggressiveLMT < 20)
					colorLMT = getGreen();
				// "<span style=\"background-color:" + col + "; \">" +
				bullishOrderText += "<span style=\"background-color:"
						+ colorSTP + "; \">BUY STP: "
						+ mailStringsMap.get("barHigh30min") + " (risks "
						+ FXUtils.df1.format(riskStandardSTP.doubleValue())
						+ "/"
						+ FXUtils.df1.format(riskAggressiveSTP.doubleValue())
						+ ")</span><br /><span style=\"background-color:"
						+ colorLMT + "; \">BUY LMT: "
						+ FXUtils.df5.format(barHalf.doubleValue())
						+ " (risks "
						+ FXUtils.df1.format(riskStandardLMT.doubleValue())
						+ "/"
						+ FXUtils.df1.format(riskAggressiveLMT.doubleValue())
						+ ")</span><br />SL: "
						+ mailStringsMap.get("bullishPivotLevel30min")
						+ " (aggressive: "
						+ FXUtils.df5.format(aggressiveSL.doubleValue())
						+ ")<br />";

				// any technical levels in avg bar size distance from current
				// pivot low
				double avgBarSize = mailValuesMap.get("bar30minAvgSize")
						.getDoubleValue();
				String vicinity = checkNearTALevels(instrument, basicTimeFrame,
						bidBar, mailValuesMap, bullishPivotLevel.doubleValue(),
						avgBarSize, true);
				if (vicinity != null && vicinity.length() > 0)
					bullishVicinityText += vicinity + "<br />";

				// now detect low risk high quality BULLISH entries !
				Double triggerLowChannelPos30min = (Double) mailValuesMap.get(
						"bullishCandleTriggerChannelPos30min").getValue(), volatility30min = (Double) mailValuesMap
						.get("volatility30min").getValue(), barLowChannelPos4h = (Double) mailValuesMap
						.get("barLowChannelPos4h").getValue(), triggerLowKChannelPos30min = (Double) mailValuesMap
						.get("bullishCandleTriggerKChannelPos30min").getValue(), stochFast30min = (Double) mailValuesMap
						.get("StochFast30min").getValue(), stochSlow30min = (Double) mailValuesMap
						.get("StochSlow30min").getValue();

				if ((volatility30min > 70 && triggerLowChannelPos30min <= 50.0)
						|| (volatility30min <= 70.0
								&& triggerLowChannelPos30min < -5.0 && triggerLowKChannelPos30min < 15.0))
					sendFilteredMail = true;

				if ((riskAggressiveSTP <= 25.0 || riskAggressiveLMT <= 25.0)
						&& ((volatility30min > 70 && triggerLowChannelPos30min < 10.0) || (volatility30min <= 70.0
								&& triggerLowChannelPos30min < -5.0 && triggerLowKChannelPos30min < 15.0))
						&& barLowChannelPos4h < 55.0
						&& (mailStringsMap.get("MACDHState30min").contains(
								"TICKED_UP")
								|| mailStringsMap.get("MACDHState30min")
										.contains("RAISING")
								|| momentum
										.getRSIState(instrument, pPeriod,
												OfferSide.BID, bidBar.getTime())
										.toString()
										.contains("TICKED_UP_FROM_OVERSOLD") || (stochFast30min > stochSlow30min & (stochFast30min <= 20.0 || stochSlow30min <= 20.0)))) {
					lowRiskHighQualitySignalsBullish += "ATTENTION: low risk and good quality BULLISH entry signal ! Risk STP "
							+ FXUtils.df1.format(riskAggressiveSTP)
							+ ", risk LMT "
							+ FXUtils.df1.format(riskAggressiveLMT)
							+ " !<br />";
					sendFilteredMail = true;
				}

				// now detect trigger pivot in entry zone !
				PriceZone hit = null;
				if ((hit = checkEntryZones(instrument,
						bullishPivotLevel.doubleValue(), true)) != null) {
					lowRiskHighQualitySignalsBullish += "ATTENTION: bullish candle trigger in long entry zone ! "
							+ hit.getHitText() + " !<br />";
					sendFilteredMail = true;
				}
			}
			if (valueToShow.contains("BEARISH")) {
				if (twoCandleSignals)
					bearishCandlesText += bearishCandles + ", ";
				else
					bearishCandlesText += valueToShow + ", ";
				if (!valueToShow.contains("BEARISH_1_BAR"))
					bearishCandlesText += "combined ";
				bearishCandlesText += "signal StDev size: "
						+ mailStringsMap
								.get("bearishTriggerCombinedSizeStat30min")
						+ ",<br />upper handle : "
						+ mailStringsMap
								.get("bearishTriggerCombinedUHPerc30min")
						+ "%"
						+ ", real body : "
						+ mailStringsMap
								.get("bearishTriggerCombinedBodyPerc30min")
						+ "%, ("
						+ mailStringsMap
								.get("bearishTriggerCombinedBodyDirection30min")
						+ "), lower handle : "
						+ mailStringsMap
								.get("bearishTriggerCombinedLHPerc30min")
						+ "%<br />";
				bearishChannelPosText += "pivot bar high 30min channels position: BBands "
						+ mailStringsMap
								.get("bearishCandleTriggerChannelPos30min")
						+ "% / Keltner channel: "
						+ mailStringsMap
								.get("bearishCandleTriggerKChannelPos30min")
						+ "%<br />"
						+ "(volatility30min (BBands squeeze): "
						+ mailStringsMap.get("volatility30min") + "%)";
				bearish4hChannelPosText = "4h channel position: "
						+ mailStringsMap
								.get("bearishPivotLevelHigherTFChannelPos30min")
						+ "%<br />";
				double barLowPerc = (Double) mailValuesMap.get(
						"barLowChannelPos30min").getValue();
				if (barLowPerc < 0)
					bearishBreakOutText = "WARNING ! Possible breakout, careful with going short ! Bar low channel position "
							+ mailStringsMap.get("barLowChannelPos30min")
							+ "%<br />";

				double avgHandleSize = tradeTrigger.avgHandleLength(instrument,
						basicTimeFrame, OfferSide.BID, bidBar,
						FXUtils.MONTH_WORTH_OF_30min_BARS);
				Double barHalf = new Double(barLow.doubleValue()
						+ (barHigh.doubleValue() - barLow.doubleValue()) / 2), bearishPivotLevel = (Double) mailValuesMap
						.get("bearishPivotLevel30min").getValue(), aggressiveSL = new Double(
						bearishPivotLevel.doubleValue() + avgHandleSize), riskStandardSTP = new Double(
						(bearishPivotLevel - barLow)
								* Math.pow(10, instrument.getPipScale())), riskAggressiveSTP = new Double(
						(aggressiveSL - barLow)
								* Math.pow(10, instrument.getPipScale())), riskStandardLMT = new Double(
						(bearishPivotLevel - barHalf)
								* Math.pow(10, instrument.getPipScale())), riskAggressiveLMT = new Double(
						(aggressiveSL - barHalf)
								* Math.pow(10, instrument.getPipScale()));
				String colorSTP = new String(getWhite()), colorLMT = new String(
						getWhite());
				if (riskAggressiveSTP.doubleValue() < 25)
					colorSTP = getRed();
				if (riskAggressiveLMT < 20)
					colorLMT = getRed();
				bearishOrderText += "<span style=\"background-color:"
						+ colorSTP
						+ "; \">SELL STP: "
						+ mailStringsMap.get("barLow30min")
						+ " (risks "
						+ FXUtils.df1.format(riskStandardSTP.doubleValue())
						+ "/"
						+ FXUtils.df1.format(riskAggressiveSTP.doubleValue())
						+ ")</span><br /><span style=\"background-color:"
						+ colorLMT
						+ "; \">SELL LMT: "
						+ FXUtils.df5
								.format(barLow.doubleValue()
										+ (barHigh.doubleValue() - barLow
												.doubleValue()) / 2)
						+ " (risks "
						+ FXUtils.df1.format(riskStandardLMT.doubleValue())
						+ "/"
						+ FXUtils.df1.format(riskAggressiveLMT.doubleValue())
						+ ")</span><br />SL: "
						+ mailStringsMap.get("bearishPivotLevel30min")
						+ " (aggressive: "
						+ FXUtils.df5.format(aggressiveSL.doubleValue())
						+ ")<br />";
				// any technical levels in avg bar size distance from current
				// pivot high
				double avgBarSize = mailValuesMap.get("bar30minAvgSize")
						.getDoubleValue();
				String vicinity = checkNearTALevels(instrument, basicTimeFrame,
						bidBar, mailValuesMap, bearishPivotLevel.doubleValue(),
						avgBarSize, false);
				if (vicinity != null && vicinity.length() > 0)
					bearishVicinityText += vicinity + "<br />";

				// now detect low risk high quality BEARISH entries !
				Double triggerHighChannelPos30min = (Double) mailValuesMap.get(
						"bearishCandleTriggerChannelPos30min").getValue(), volatility30min = (Double) mailValuesMap
						.get("volatility30min").getValue(), barHighChannelPos4h = (Double) mailValuesMap
						.get("barHighChannelPos4h").getValue(), triggerHighKChannelPos30min = (Double) mailValuesMap
						.get("bearishCandleTriggerKChannelPos30min").getValue(), stochFast30min = (Double) mailValuesMap
						.get("StochFast30min").getValue(), stochSlow30min = (Double) mailValuesMap
						.get("StochSlow30min").getValue();

				if ((volatility30min > 70 && triggerHighChannelPos30min >= 50.0)
						|| (volatility30min <= 70.0
								&& triggerHighChannelPos30min > 105.0 && triggerHighKChannelPos30min > 85.0))
					sendFilteredMail = true;

				if ((riskAggressiveSTP <= 25.0 || riskAggressiveLMT <= 25.0)
						&& ((volatility30min > 70 && triggerHighChannelPos30min > 90.0) || (volatility30min <= 70.0
								&& triggerHighChannelPos30min > 105.0 && triggerHighKChannelPos30min > 85.0))
						&& barHighChannelPos4h > 45.0
						&& (mailStringsMap.get("MACDHState30min").contains(
								"TICKED_DOWN")
								|| mailStringsMap.get("MACDHState30min")
										.contains("FALLING")
								|| momentum
										.getRSIState(instrument, pPeriod,
												OfferSide.BID, bidBar.getTime())
										.toString()
										.contains("TICKED_DOWN_FROM_OVERBOUGHT") || (stochFast30min < stochSlow30min && (stochFast30min >= 80.0 || stochSlow30min >= 80.0)))) {
					lowRiskHighQualitySignalsBearish += "ATTENTION: low risk and good quality BEARISH entry signal ! Risk STP "
							+ FXUtils.df1.format(riskAggressiveSTP)
							+ ", risk LMT "
							+ FXUtils.df1.format(riskAggressiveLMT)
							+ " !<br />";
					sendFilteredMail = true;
				}

				// now detect trigger pivot in entry zone !
				PriceZone hit = null;
				if ((hit = checkEntryZones(instrument,
						bearishPivotLevel.doubleValue(), false)) != null) {
					lowRiskHighQualitySignalsBearish += "ATTENTION: bearish candle trigger in short entry zone ! "
							+ hit.getHitText() + " !<br />";
					sendFilteredMail = true;
				}
			}
		}

		// check no-go zones
		noGoZonesText += checkNoGoZones(mailStringsMap, mailValuesMap).replace(
				"\n", "<br />");

		if (noGoZonesText.length() > 0)
			mailBody += printSimpleTableHeader() + "<tr><td>" + noGoZonesText
					+ "</td></tr></table>";
		if (lowRiskHighQualitySignalsBullish.length() > 0)
			mailBody += printSimpleTableHeader()
					+ "<tr><td><span style=\"background-color:#090; display:block; margin:0 1px; color:#fff;\">"
					+ lowRiskHighQualitySignalsBullish
					+ "</span></td></tr></table>";
		if (bullishCandlesText.length() > 0) {
			mailBody += printSimpleTableHeader()
					+ "<tr><td width=\"575\"><span style=\"background-color:"
					+ getBullishSignalColor(mailValuesMap)
					+ "; display:block; margin:0 1px;\">"
					+ bullishCandlesText
					+ "</span><span style=\"background-color:"
					+ getChannelColorBullish30min(mailValuesMap)
					+ "; display:block; margin:0 1px;\">"
					+ bullishChannelPosText
					+ "</span><span style=\"background-color:"
					+ get4hChannelColor(mailValuesMap,
							"bullishPivotLevelHigherTFChannelPos30min")
					+ "; display:block; margin:0 1px;\">"
					+ bullish4hChannelPosText
					+ "</span>"
					+ (bullishBreakOutText.length() > 0 ? "<span style=\"background-color:"
							+ getRed()
							+ "; display:block; margin:0 1px;\">"
							+ bullishBreakOutText + "</span>"
							: "")
					+ (bullishVicinityText.length() > 0 ? "<span style=\"background-color:#FFF; display:block; margin:0 1px;\">"
							+ bullishVicinityText + "</span>"
							: "") + "</td><td valign=\"top\" width=\"205\">"
					+ bullishOrderText + "</td></tr></table>";
		}
		if (lowRiskHighQualitySignalsBearish.length() > 0)
			mailBody += printSimpleTableHeader()
					+ "<tr><td><span style=\"background-color:#C00; display:block; margin:0 1px; color:#fff;\">"
					+ lowRiskHighQualitySignalsBearish
					+ "</span></td></tr></table>";
		if (bearishCandlesText.length() > 0) {
			mailBody += printSimpleTableHeader()
					+ "<tr><td width=\"575\"><span style=\"background-color:"
					+ getBearishSignalColor(mailValuesMap)
					+ "; display:block; margin:0 1px;\">"
					+ bearishCandlesText
					+ "</span><span style=\"background-color:"
					+ getChannelColorBearish30min(mailValuesMap)
					+ "; display:block; margin:0 1px;\">"
					+ bearishChannelPosText
					+ "</span><span style=\"background-color:"
					+ get4hChannelColor(mailValuesMap,
							"bearishPivotLevelHigherTFChannelPos30min")
					+ "; display:block; margin:0 1px;\">"
					+ bearish4hChannelPosText
					+ "</span>"
					+ (bearishBreakOutText.length() > 0 ? "<span style=\"background-color:"
							+ getGreen()
							+ "; display:block; margin:0 1px;\">"
							+ bearishBreakOutText + "</span>"
							: "")
					+ (bearishVicinityText.length() > 0 ? "<span style=\"background-color:#FFF; display:block; margin:0 1px;\">"
							+ bearishVicinityText + "</span>"
							: "") + "</td><td valign=\"top\" width=\"205\">"
					+ bearishOrderText + "</td></tr></table>";
		}

		return mailBody;
	}

	protected String getPriceChange(Instrument instrument, IBar bidBar,
			Map<String, String> mailStringsMap,
			Map<String, FlexLogEntry> mailValuesMap, Double barHigh,
			Double barLow) throws JFException {
		double roc1 = indicators.roc(instrument, basicTimeFrame, OfferSide.BID,
				AppliedPrice.CLOSE, 1, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0] * 100, atr = ((Double) mailValuesMap
				.get("ATR30min").getValue()).doubleValue();

		String col = new String(), extreme = new String();
		if (roc1 > 0.0) {
			if (roc1 > 3 * atr) {
				col = "#090";
				extreme = " (" + FXUtils.df1.format(roc1 / atr) + " x ATR !) ";
			} else if (roc1 > 2 * atr) {
				col = "#0C3";
				extreme = " (" + FXUtils.df1.format(roc1 / atr) + " x ATR !) ";
			} else if (roc1 > 0.8 * atr)
				col = "#0F6";
			else
				col = "#FFF";
		} else {
			double absRoc = Math.abs(roc1);
			if (absRoc > 3 * atr) {
				col = "#C00";
				extreme = " (" + FXUtils.df1.format(absRoc / atr)
						+ " x ATR !) ";
			} else if (absRoc > 2 * atr) {
				col = "#F00";
				extreme = " (" + FXUtils.df1.format(absRoc / atr) + "x ATR !) ";
			} else if (absRoc > 0.8 * atr)
				col = "#F66";
			else
				col = "#FFF";
		}

		String elementText1 = new String("30min price change: "), elementText2 = new String(
				FXUtils.df1.format(roc1) + " pips" + extreme), elementText3 = new String(
				" (current price: " + FXUtils.df5.format(bidBar.getClose())
						+ ", ");
		elementText3 += "last bar low: " + mailStringsMap.get("barLow30min")
				+ " / ";
		elementText3 += "high: " + mailStringsMap.get("barHigh30min") + " / ";
		elementText3 += "middle: "
				+ FXUtils.df5.format(barLow.doubleValue()
						+ (barHigh.doubleValue() - barLow.doubleValue()) / 2)
				+ ")";
		return "<span style=\"background-color:FFF; display:block; margin:0 1px;\">"
				+ elementText1
				+ "<span style=\"background-color:"
				+ col
				+ "; \">" + elementText2 + "</span>" + elementText3 + "</span>";
	}

	protected String createMailBodyBottom(Instrument instrument,
			Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine)
			throws JFException {

		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}
		Double barHigh = (Double) mailValuesMap.get("barHigh30min").getValue();
		Double barLow = (Double) mailValuesMap.get("barLow30min").getValue();

		// other COMBINED signals
		String mailBody = new String(), res = new String();
		if (((Double) mailValuesMap.get("barLowChannelPos30min").getValue())
				.doubleValue() < 50.0) {
			boolean additionalSignal = false;
			if (mailStringsMap.get("MACDHState30min").equals(
					"TICKED_UP_BELOW_ZERO")) {
				mailBody += "\nAdditional signals: MACDHistogram 30min "
						+ mailStringsMap.get("MACDHState30min");
				additionalSignal = true;
			}
			if (momentum
					.getStochCross(instrument, pPeriod, OfferSide.BID,
							bidBar.getTime()).toString()
					.equals("BULLISH_CROSS_FROM_OVERSOLD")) {
				mailBody += "\nAdditional signals: Stoch 30min BULLISH CROSS from oversold";
				additionalSignal = true;
			}
			if (momentum
					.getRSIState(instrument, pPeriod, OfferSide.BID,
							bidBar.getTime()).toString()
					.contains("TICKED_UP_FROM_OVERSOLD")) {
				mailBody += "\nAdditional signals: RSI 30min TICKED UP from oversold";
				additionalSignal = true;
			}
			if (additionalSignal) {
				mailBody += "\nand bar low 30min channelPos "
						+ mailStringsMap.get("barLowChannelPos30min")
						+ "% / 4h channelPos "
						+ mailStringsMap.get("barLowChannelPos4h") + "%\n";

				double avgHandleSize = tradeTrigger.avgHandleLength(instrument,
						basicTimeFrame, OfferSide.BID, bidBar,
						FXUtils.MONTH_WORTH_OF_30min_BARS);
				IBar prevBar = history.getBar(instrument, pPeriod,
						OfferSide.BID, 2);
				Double barHalf = new Double(barLow.doubleValue()
						+ (barHigh.doubleValue() - barLow.doubleValue()) / 2), bullishPivotLevel = new Double(
						barLow < prevBar.getLow() ? barLow : prevBar.getLow()), aggressiveSL = new Double(
						bullishPivotLevel.doubleValue() - avgHandleSize), riskStandard = new Double(
						(barHigh - bullishPivotLevel)
								* Math.pow(10, instrument.getPipScale())), riskAggressive = new Double(
						(barHigh - aggressiveSL)
								* Math.pow(10, instrument.getPipScale())), riskStandard2 = new Double(
						(barHalf - bullishPivotLevel)
								* Math.pow(10, instrument.getPipScale())), riskAggressive2 = new Double(
						(barHalf - aggressiveSL)
								* Math.pow(10, instrument.getPipScale()));
				mailBody += "\nBUY STP: " + mailStringsMap.get("barHigh30min")
						+ " (risks "
						+ FXUtils.df1.format(riskStandard.doubleValue()) + "/"
						+ FXUtils.df1.format(riskAggressive.doubleValue())
						+ ")\nBUY LMT: "
						+ FXUtils.df5.format(barHalf.doubleValue())
						+ " (risks "
						+ FXUtils.df1.format(riskStandard2.doubleValue()) + "/"
						+ FXUtils.df1.format(riskAggressive2.doubleValue())
						+ ")\nSL: " + FXUtils.df5.format(bullishPivotLevel)
						+ " (aggressive: "
						+ FXUtils.df5.format(aggressiveSL.doubleValue())
						+ ")\n";
			}
		} else if (((Double) mailValuesMap.get("barHighChannelPos30min")
				.getValue()).doubleValue() > 50.0) {
			boolean additionalSignal = false;
			if (mailStringsMap.get("MACDHState30min").equals(
					"TICKED_DOWN_ABOVE_ZERO")) {
				mailBody += "\nAdditional signals: MACDHistogram 30min "
						+ mailStringsMap.get("MACDHState30min");
				additionalSignal = true;
			}
			if (momentum
					.getStochCross(instrument, pPeriod, OfferSide.BID,
							bidBar.getTime()).toString()
					.equals("BEARISH_CROSS_FROM_OVERBOUGTH")) {
				mailBody += "\nAdditional signals: Stoch 30min BEARISH CROSS from overbought";
				additionalSignal = true;
			}
			if (momentum
					.getRSIState(instrument, pPeriod, OfferSide.BID,
							bidBar.getTime()).toString()
					.equals("TICKED_DOWN_FROM_OVERBOUGHT")) {
				mailBody += "\nAdditional signals: RSI 30min TICKED DOWN from overbought";
				additionalSignal = true;
			}

			if (additionalSignal) {
				mailBody += "\nand bar high channelPos "
						+ mailStringsMap.get("barHighChannelPos30min")
						+ "% / 4h channelPos "
						+ mailStringsMap.get("barHighChannelPos4h") + "%\n";
				IBar prevBar = history.getBar(instrument, pPeriod,
						OfferSide.BID, 2);
				double avgHandleSize = tradeTrigger.avgHandleLength(instrument,
						basicTimeFrame, OfferSide.BID, bidBar,
						FXUtils.MONTH_WORTH_OF_30min_BARS);
				Double barHalf = new Double(barLow.doubleValue()
						+ (barHigh.doubleValue() - barLow.doubleValue()) / 2), bearishPivotLevel = new Double(
						barHigh > prevBar.getHigh() ? barHigh
								: prevBar.getHigh()), aggressiveSL = new Double(
						bearishPivotLevel.doubleValue() + avgHandleSize), riskStandard = new Double(
						(bearishPivotLevel - barLow)
								* Math.pow(10, instrument.getPipScale())), riskAggressive = new Double(
						(aggressiveSL - barLow)
								* Math.pow(10, instrument.getPipScale())), riskStandard2 = new Double(
						(bearishPivotLevel - barHalf)
								* Math.pow(10, instrument.getPipScale())), riskAggressive2 = new Double(
						(aggressiveSL - barHalf)
								* Math.pow(10, instrument.getPipScale()));
				mailBody += "\nSELL STP: "
						+ mailStringsMap.get("barLow30min")
						+ " (risks "
						+ FXUtils.df1.format(riskStandard.doubleValue())
						+ "/"
						+ FXUtils.df1.format(riskAggressive.doubleValue())
						+ ")\nSELL LMT: "
						+ FXUtils.df5
								.format(barLow.doubleValue()
										+ (barHigh.doubleValue() - barLow
												.doubleValue()) / 2)
						+ " (risks "
						+ FXUtils.df1.format(riskStandard2.doubleValue()) + "/"
						+ FXUtils.df1.format(riskAggressive2.doubleValue())
						+ ")\nSL: " + FXUtils.df5.format(bearishPivotLevel)
						+ " (aggressive: "
						+ FXUtils.df5.format(aggressiveSL.doubleValue())
						+ ")\n";
			}
		}

		String overlaps = new String();
		for (FlexLogEntry e : logLine) {
			if (e.getLabel().contains("Overlap")
					&& e.getFormattedValue().startsWith("YES")) {
				overlaps = e.getLabel() + " "
						+ e.getFormattedValue().substring(3) + "\n";
			}
		}
		if (overlaps.length() > 0)
			overlaps = "\nTimeframes overlaps:\n" + overlaps;
		mailBody += overlaps + "\n\n";

		Double rsi30min = (Double) mailValuesMap.get("RSI30min").getValue();
		Double rsi4h = (Double) mailValuesMap.get("RSI4h").getValue();
		Double cci4h = (Double) mailValuesMap.get("CCI4h").getValue();
		Double barHighPos30min = (Double) mailValuesMap.get(
				"barHighChannelPos30min").getValue();
		Double barLowPos30min = (Double) mailValuesMap.get(
				"barLowChannelPos30min").getValue();
		Double barHighPos4h = (Double) mailValuesMap.get("barHighChannelPos4h")
				.getValue();
		Double barLowPos4h = (Double) mailValuesMap.get("barLowChannelPos4h")
				.getValue();
		Double bBandsSqueeze30min = (Double) mailValuesMap.get(
				"volatility30min").getValue();
		Double flatRegime30min = (Double) mailValuesMap.get("MAsDistance30min")
				.getValue();
		Double flatRegime4h = (Double) mailValuesMap.get("MAsDistance4h")
				.getValue();
		Double adx4h = (Double) mailValuesMap.get("ADX4h").getValue();
		Double adx30min = (Double) mailValuesMap.get("ADX30min").getValue();
		Double lowVolatility4h = (Double) mailValuesMap.get("volatility4h")
				.getValue();
		Double bar30minStat = (Double) mailValuesMap.get("barStat30min")
				.getValue();
		Integer barsAboveChannel30min = (Integer) mailValuesMap.get(
				"barsAboveChannel30min").getValue();
		Integer barsBelowChannel30min = (Integer) mailValuesMap.get(
				"barsBelowChannel30min").getValue();
		Integer barsAboveChannel4h = (Integer) mailValuesMap.get(
				"barsAboveChannel4h").getValue();
		Integer barsBelowChannel4h = (Integer) mailValuesMap.get(
				"barsBelowChannel4h").getValue();

		if ((rsi30min.doubleValue() > 68.0 || rsi30min.doubleValue() < 32.0)
				|| (rsi4h.doubleValue() > 68.0 || rsi4h.doubleValue() < 32.0)
				|| (cci4h.doubleValue() > 190.0 || cci4h.doubleValue() < -190.0)
				|| barHighPos30min.doubleValue() > 95.0
				|| barHighPos30min.doubleValue() < 0.0
				|| barLowPos30min.doubleValue() < 5.0
				|| barLowPos30min.doubleValue() > 100.0
				|| barHighPos4h.doubleValue() > 95.0
				|| barLowPos4h.doubleValue() < 5.0
				|| mailStringsMap.get("StochState30min").contains("OVER")
				|| mailStringsMap.get("StochState4h").contains("OVER")
				|| (bBandsSqueeze30min < 70.0 || bBandsSqueeze30min > 180.0)
				|| flatRegime30min < -0.7 || flatRegime4h < -0.7
				|| adx4h > 40.0 || adx30min > 40.0 || lowVolatility4h < 70.0
				|| bar30minStat > 2.0 || barsAboveChannel30min > 1
				|| barsBelowChannel30min > 1 || barsAboveChannel4h > 1
				|| barsBelowChannel4h > 1) {
			mailBody += "Extreme values:";
			if (flatRegime30min < -0.7)
				mailBody += "\nflat regime 30min: "
						+ mailStringsMap.get("MAsDistance30min");
			if (flatRegime4h < -0.7)
				mailBody += "\nflat regime 4h: "
						+ mailStringsMap.get("MAsDistance4h");
			if (rsi30min.doubleValue() > 68 || rsi30min.doubleValue() < 32)
				mailBody += "\nRSI 30min: " + mailStringsMap.get("RSI30min");
			if (rsi4h.doubleValue() > 68 || rsi4h.doubleValue() < 32)
				mailBody += "\nRSI 4h: " + mailStringsMap.get("RSI4h");
			if (cci4h.doubleValue() > 190.0 || cci4h.doubleValue() < -190.0) {
				mailBody += "\nCCI 4h: " + mailStringsMap.get("CCI4h") + " ("
						+ mailStringsMap.get("CCIState4h") + ")";
				if (cci4h.doubleValue() < -190.0
						&& mailStringsMap.get("CCIState4h").contains(
								"TICKED_UP"))
					sendFilteredMail = true;
				if (cci4h.doubleValue() > 190.0
						&& mailStringsMap.get("CCIState4h").contains(
								"TICKED_DOWN"))
					sendFilteredMail = true;
			}
			if (barHighPos30min.doubleValue() > 95.0)
				mailBody += "\nBar high in 30min channel: "
						+ mailStringsMap.get("barHighChannelPos30min") + " %";
			if (barHighPos30min.doubleValue() < 0.0)
				mailBody += "\nBar high BELOW 30min channel: "
						+ mailStringsMap.get("barHighChannelPos30min") + " %";
			if (barLowPos30min.doubleValue() < 5)
				mailBody += "\nBar low in 30min channel: "
						+ mailStringsMap.get("barLowChannelPos30min") + " %";
			if (barLowPos30min.doubleValue() > 100)
				mailBody += "\nBar low ABOVE 30min channel: "
						+ mailStringsMap.get("barLowChannelPos30min") + " %";
			if (barHighPos4h.doubleValue() > 95.0)
				mailBody += "\nBar high in 4h channel: "
						+ mailStringsMap.get("barHighChannelPos4h") + " %";
			if (barLowPos4h.doubleValue() < 5)
				mailBody += "\nBar low in 4h channel: "
						+ mailStringsMap.get("barLowChannelPos4h") + " %";
			if (mailStringsMap.get("StochState30min").contains("OVER"))
				mailBody += "\nStoch 30min: "
						+ mailStringsMap.get("StochState30min");
			if (mailStringsMap.get("StochState4h").contains("OVER"))
				mailBody += "\nStoch 4h: " + mailStringsMap.get("StochState4h");
			if (bBandsSqueeze30min < 70.0)
				mailBody += "\nlow 30min volatility (BBands squeeze): "
						+ mailStringsMap.get("volatility30min") + "%";
			if (bBandsSqueeze30min > 180.0)
				mailBody += "\nhigh 30min volatility (BBands squeeze): "
						+ mailStringsMap.get("volatility30min") + "%";
			if (adx4h > 40.0)
				mailBody += "\nADX 4h: " + mailStringsMap.get("ADX4h")
						+ " (DI+: " + mailStringsMap.get("DI_PLUS4h")
						+ " / DI-: " + mailStringsMap.get("DI_MINUS4h") + ")";
			if (adx30min > 40.0)
				mailBody += "\nADX 30min: " + mailStringsMap.get("ADX30min")
						+ " (DI+: " + mailStringsMap.get("DI_PLUS30min")
						+ " / DI-: " + mailStringsMap.get("DI_MINUS30min")
						+ ")";
			if (lowVolatility4h < 70.0)
				mailBody += "\nlow 4h volatility (BBands squeeze): "
						+ mailStringsMap.get("volatility4h") + "%";
			if (bar30minStat > 2.0) {
				mailBody += "\nBig bar range: "
						+ FXUtils.df1.format(bar30minStat)
						+ " StDev(s) from average ("
						+ FXUtils.df1.format((bidBar.getHigh() - bidBar
								.getLow())
								* Math.pow(10, instrument.getPipScale()))
						+ " pips) !\nCandle stats: upper handle = "
						+ FXUtils.df1.format(tradeTrigger
								.barsUpperHandlePerc(bidBar))
						+ "% / "
						+ (bidBar.getOpen() < bidBar.getClose() ? "BULLISH"
								: "BEARISH")
						+ " body = "
						+ FXUtils.df1.format(tradeTrigger.barsBodyPerc(bidBar))
						+ "% / lower handle = "
						+ FXUtils.df1.format(tradeTrigger
								.barsLowerHandlePerc(bidBar)) + "%";
				sendFilteredMail = true;
			}
			if (barsAboveChannel30min > 1)
				mailBody += "\n" + barsAboveChannel30min
						+ " 30min bars ABOVE channel top !";
			if (barsBelowChannel30min > 1)
				mailBody += "\n" + barsBelowChannel30min
						+ " 30min bars BELOW channel bottom !";
			if (barsAboveChannel4h > 1)
				mailBody += "\n" + barsAboveChannel4h
						+ " 4h bars ABOVE channel top !";
			if (barsBelowChannel4h > 1)
				mailBody += "\n" + barsBelowChannel4h
						+ " 4h bars BELOW channel bottom !";
			mailBody += "\n\n";
		}

		if (nextSetups.containsKey(instrument.toString())) {
			mailBody += "Next recommended setup: "
					+ nextSetups.get(instrument.toString()) + "\n\n";
		}

		res = printSimpleTableHeader() + "<tr><td><br />"
				+ mailBody.replace("\n", "<br />") + "<br /></td></tr>";

		res += "<tr><td><br />Moving averages clusters - ";
		List<SRLevel> mas = getMAsValues(instrument, bidBar);
		res += showSortedMAsList(instrument, mas, bidBar).replace("\n",
				"<br />")
				+ "<br /></td></tr>";

		if (srLevels != null && srLevels.size() > 0) {
			res += "<tr><td><br />Support / resistance levels - ";
			res += showSortedSRLevels(instrument,
					new ArrayList<SRLevel>(srLevels), bidBar).replace("\n",
					"<br />")
					+ "<br /></td></tr>";
		}

		if (trendlinesToCheck != null && trendlinesToCheck.size() > 0) {
			res += "<tr><td><br />Current trendline values - ";
			res += showSortedTrendlineValues(instrument, bidBar).replace("\n",
					"<br />")
					+ "<br /></td></tr>";
		}

		return res;
	}

	protected String getChannelColorBullish30min(
			Map<String, FlexLogEntry> mailValuesMap) {
		double triggerLowChannelPos30min = mailValuesMap.get(
				"bullishCandleTriggerChannelPos30min").getDoubleValue(), volatility30min = mailValuesMap
				.get("volatility30min").getDoubleValue(), triggerLowKChannelPos30min = mailValuesMap
				.get("bullishCandleTriggerKChannelPos30min").getDoubleValue();

		if (volatility30min > 70) {
			if (triggerLowChannelPos30min < 5)
				return getDarkGreen();
			else if (triggerLowChannelPos30min < 15)
				return getGreen();
			else if (triggerLowChannelPos30min < 50)
				return getLightGreen();
			else if (triggerLowChannelPos30min < 85)
				return getLightRed();
			else if (triggerLowChannelPos30min < 95)
				return getRed();
			else
				return getDarkRed();
		} else {
			if (triggerLowKChannelPos30min < 5)
				return getDarkGreen();
			else if (triggerLowKChannelPos30min < 15)
				return getGreen();
			else if (triggerLowKChannelPos30min < 50)
				return getLightGreen();
			else if (triggerLowKChannelPos30min < 85)
				return getLightRed();
			else if (triggerLowKChannelPos30min < 95)
				return getRed();
			else
				return getDarkRed();
		}
	}

	protected String getChannelColorBearish30min(
			Map<String, FlexLogEntry> mailValuesMap) {
		double triggerHighChannelPos30min = mailValuesMap.get(
				"bearishCandleTriggerChannelPos30min").getDoubleValue(), volatility30min = mailValuesMap
				.get("volatility30min").getDoubleValue(), triggerHighKChannelPos30min = mailValuesMap
				.get("bearishCandleTriggerKChannelPos30min").getDoubleValue();

		if (volatility30min > 70) {
			if (triggerHighChannelPos30min < 5)
				return getDarkGreen();
			else if (triggerHighChannelPos30min < 15)
				return getGreen();
			else if (triggerHighChannelPos30min < 50)
				return getLightGreen();
			else if (triggerHighChannelPos30min < 85)
				return getLightRed();
			else if (triggerHighChannelPos30min < 95)
				return getRed();
			else
				return getDarkRed();
		} else {
			if (triggerHighKChannelPos30min < 5)
				return getDarkGreen();
			else if (triggerHighKChannelPos30min < 15)
				return getGreen();
			else if (triggerHighKChannelPos30min < 50)
				return getLightGreen();
			else if (triggerHighKChannelPos30min < 85)
				return getLightRed();
			else if (triggerHighKChannelPos30min < 95)
				return getRed();
			else
				return getDarkRed();
		}
	}

	protected String get4hChannelColor(Map<String, FlexLogEntry> mailValuesMap,
			String fieldId) {
		double pivot4hPos = mailValuesMap.get(fieldId).getDoubleValue();
		if (pivot4hPos < 5)
			return getDarkGreen();
		else if (pivot4hPos < 15)
			return getGreen();
		else if (pivot4hPos < 50)
			return getLightGreen();
		else if (pivot4hPos < 85)
			return getLightRed();
		else if (pivot4hPos < 95)
			return getRed();
		else
			return getDarkRed();
	}
}
