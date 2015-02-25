package jforex.explorers;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import jforex.AdvancedMailCreator;
import jforex.emailflex.FlexEmailElementFactory;
import jforex.emailflex.IFlexEmailElement;
import jforex.emailflex.IFlexEmailWrapper;
import jforex.emailflex.SignalUtils;
import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;

import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

public class OTTStatsCollector extends AdvancedMailCreator implements IStrategy {
	
	// used in backtesting to correctly set time for calling OTT interface
	protected long lastBarDone = -1;
	protected Period lastBarDonePeriod = null;
	
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

		private AdvancedMailCreator getOuterType() {
			return OTTStatsCollector.this;
		}
		
		
	}
	
	protected Set<TickerTimeFramePair> 
		tickerTimeFrames = new HashSet<TickerTimeFramePair>(),
		unprocessedTickerTimeFrames = new HashSet<TickerTimeFramePair>();
	protected Map<Instrument, Boolean> dailyFillDone = new HashMap<Instrument, Boolean>();

    public OTTStatsCollector(Properties p) {
		super(p);
	}

	@Override
	public void onStart(IContext context) throws JFException {
		super.onStartExec(context);
		if (conf.getProperty("mailToDBOnly", "no").equals("yes") || conf.getProperty("barStatsToDB", "no").equals("yes")) {
			dbLogOnStart();
			dbPopulateTickerTimeFrames();
		}
	}

	private void dbPopulateTickerTimeFrames() {
		String 
			statementStr = new String("SELECT distinct ticker, time_frame_basic as time_frame FROM " + FXUtils.getDbToUse() + ".vsubscriptions "
						+ "union all SELECT distinct ticker, time_frame_higher FROM " + FXUtils.getDbToUse() + ".vsubscriptions");
		try {
			Statement qry = logDB.createStatement();
			ResultSet result = qry.executeQuery(statementStr);
			while (result.next()) {
				tickerTimeFrames.add(new TickerTimeFramePair(result.getString("ticker"), result.getInt("time_frame")));
				if (!conf.getProperty("backtest", "no").equals("yes"))
					unprocessedTickerTimeFrames.add(new TickerTimeFramePair(result.getString("ticker"), result.getInt("time_frame")));

				dailyFillDone.put(Instrument.fromString(result.getString("ticker")), new Boolean(false));
			}
		} catch (SQLException ex) {
			   log.print("Log database problem: " + ex.getMessage());
			   log.print(statementStr);
			   log.close();
	           System.exit(1);
		}
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException { }

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException 
	{	
		if (skipPairs.contains(instrument))
			return;
		
		//log.print("Entered onBar before filtering for timeframe " + period.toString() + " and bidBar time of " + FXUtils.getFormatedBarTimeWithSecs(bidBar));
		if (!tradingAllowed(bidBar.getTime())) {
			//log.print("onBar - trading not allowed; timeframe " + period.toString() + " and bidBar time of " + FXUtils.getFormatedBarTimeWithSecs(bidBar));
			return;
		}
		
		// effectively here we steer which timeframes are possible at all in our system ! By listing them in FXUtils.timeFrameNamesMap !!!
		if (!FXUtils.timeFrameNamesMap.containsKey(period.toString())) {
			//log.print("onBar - irrelevant timeframe; timeframe " + period.toString() + ", ticker " + instrument.toString() + " and bidBar time of " + FXUtils.getFormatedBarTimeWithSecs(bidBar));
            return;			
		}
		
		TickerTimeFramePair ttfp = new TickerTimeFramePair(instrument.toString(), FXUtils.toTimeFrameCode(period, logDB, log));
		if (!tickerTimeFrames.contains(ttfp)) {
			log.print("onBar - irrelevant ticker/timeframe combination; timeframe " + period.toString() + ", ticker " + instrument.toString() + " and bidBar time of " + FXUtils.getFormatedBarTimeWithSecs(bidBar));
            return;
        }
		
		log.print("Entered onBar AFTER filtering for instrument " + instrument.toString() + ", timeframe " + period.toString() + " and bidBar time of " + FXUtils.getFormatedBarTimeWithSecs(bidBar));
        
		List<FlexLogEntry> logLine = new ArrayList<FlexLogEntry>();        
		eventsSource.collectOneTimeFrameStats(instrument, period, bidBar, logLine);
		
		// special case processing for OTT signals - no DB storage. Flagged with all 3 time frames being the same
		if (hasNoDBSubscriptions(instrument, period)) {
			sendNoDBReportMails(instrument, period, bidBar, logLine);
			if (!conf.getProperty("backtest", "no").equals("yes")) {
				unprocessedTickerTimeFrames.remove(ttfp);
				//TODO: this is dangerous !!! When cron calls every half an hour set doesn't get emptied of 4h timeframe from DB !
				if (unprocessedTickerTimeFrames.size() == 0) {
					File requestedCall = new File("requestedCall.bin");
					if (requestedCall.exists())
						requestedCall.delete();
				}
			} 
			lastBarDone = bidBar.getTime();
			lastBarDonePeriod = period;
			log.print("Last bar done set after processing instrument " + instrument.toString() + ", timeframe " + period.toString() + " and bidBar time of " + FXUtils.getFormatedBarTimeWithSecs(bidBar));
		}
		
	}

	private boolean hasNoDBSubscriptions(Instrument instrument, Period period) {
		String 
			dbWhere = new String("WHERE ticker = '" + instrument.toString() 
					+ "' AND time_frame_basic = " + FXUtils.toTimeFrameCode(period, logDB, log)
					+ " AND time_frame_basic = time_frame_higher"),
			statementStr = "SELECT * FROM " + FXUtils.getDbToUse() + ".vsubscriptions " + dbWhere;
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

	/**
	 * Fetch all the subscriptions for this time frame and this pair, and construct their emails
	 * Then send them
	 */
	private void sendNoDBReportMails(Instrument instrument, Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine) throws JFException {
		ResultSet matchingSubscriptions = dbGetNoDBSubscriptions(instrument, pPeriod);
		try {
			while (matchingSubscriptions.next()) {		
				String 
					mailBody = null,
					mailSubject = null;  
				if (FXUtils.toTimeFrameCode(pPeriod, logDB, log) == matchingSubscriptions.getInt("time_frame_basic")) {
					mailBody = createMailBodySimple(matchingSubscriptions.getInt("subscription_id"), instrument, pPeriod, bidBar, logLine);
					mailSubject = new String("FX pair report for " + pPeriod.toString() + " timeframe: " + instrument.toString() + " at " + FXUtils.getFormatedTimeCET(bidBar.getTime()));
				}
				else
					continue;
				
				String[] recepients = new String[1];
				recepients[0] = matchingSubscriptions.getString("email");
				
				if (conf.getProperty("sendMail", "no").equals("yes") && mailBody != null)
					sendMail("romanzhero@gmail.com", recepients, mailSubject, mailBody);
				
				if (conf.getProperty("logMail", "no").equals("yes")  && mailBody != null)
					log.print(mailBody);
			}
		} catch (SQLException e) {
		   log.print("Log database problem: " + e.getMessage() + " while trying to get subscriptions");
		   log.close();
           System.exit(1);
		}

	}
	
	private ResultSet dbGetNoDBSubscriptions(Instrument instrument, Period pPeriod) {
		String 
			dbWhere = new String("WHERE ticker = '" + instrument.toString() 
					+ "' AND time_frame_basic = " + FXUtils.toTimeFrameCode(pPeriod, logDB, log)
					+ " AND time_frame_higher = time_frame_basic"),
			statementStr = "SELECT * FROM " + FXUtils.getDbToUse() + ".vsubscriptions " + dbWhere;
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
	
	
	private ResultSet dbGetSubscriptionOptions(Instrument instrument, int subscription_id) {
		String 
			statementStr = new String("SELECT `group`, option_name, `order` FROM " + FXUtils.getDbToUse() + ".v_subscription_options " 
				+ "WHERE subscription_id = " + subscription_id
				+ " and ticker = '" + instrument.toString() + "' order by `order`");
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

	protected String createMailBodySimple(int subscription_id, Instrument instrument, Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine) throws JFException {
		
		String mailBody = new String();
		boolean 
			notEmpty = false,
			signal = false;
		
		mailBody = 
			"<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"
			+ "<html xmlns=\"http://www.w3.org/1999/xhtml\">"
			+ "<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" /><title>Table</title></head><body>" + 
			"Report for " + instrument.toString() + " (special), " + FXUtils.getFormatedTimeCET(bidBar.getTime()) 
			+ " CET (time frame: " + pPeriod.toString() 
			+ ")";
		
		ResultSet options = dbGetSubscriptionOptions(instrument, subscription_id);
		// need to re-arrange the list of options:
		// 1. check if an option requires a wrapper (like Overviews)
		// 2. if yes, create the wrapper and remove this option from the list, add wrapper on its position
		// 3. go through the rest of the list and check for other options requiring the same wrapper. Remove them from list and add to wrapper
		List<IFlexEmailElement> 
			checkList = new ArrayList<IFlexEmailElement>(),
			resultList = new ArrayList<IFlexEmailElement>();
		try {
			while (options.next()) {
				IFlexEmailElement e = FlexEmailElementFactory.create(options.getString("option_name"));
				if (e == null) {
					log.print("No mapped Java class for DB element " + options.getString("option_name"));
				}
				else
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
						if (checkWrap.getWrapper().getClass().equals(wrapper.getClass())) {
							wrapper.add(checkWrap);
							checkList.remove(j);
							j--;
						}
					}
					checkList.remove(i);
					i--;
				}
				else 
					resultList.add(currEl);
			}
			
			mailBody += printSimpleTableHeader();
			for (IFlexEmailElement e : resultList) {
				String html = null;
				if (e.isGeneric())
					html = e.print(instrument, pPeriod, bidBar, logLine, logDB);
				else
					html = e.print(instrument, pPeriod, bidBar, history, indicators, trendDetector, channelPosition, momentum, vola, tradeTrigger, conf, logLine, logDB);
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
			   log.print("Log database problem: " + ex.getMessage() + " while trying to get subscription options");
			   log.close();
	           System.exit(1);
		}
		if (signal && notEmpty)
			return mailBody + "</table></body></html>";
		else
			return null;
	}
	

	@Override
	public void onMessage(IMessage message) throws JFException { }

	@Override
	public void onAccount(IAccount account) throws JFException { }

	@Override
	public void onStop() throws JFException {
		if (conf.getProperty("triggerInterfaces", "no").equals("yes") && lastBarDone != -1) {
			String urlToCall;
			try {
				urlToCall = conf.getProperty("OTTinterface") + URLEncoder.encode(FXUtils.getMySQLTimeStamp(SignalUtils.getBarEndTime(lastBarDone, lastBarDonePeriod)), "ASCII");
				log.print("Calling OTT interface at " + urlToCall);
				FXUtils.httpGet(urlToCall);
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
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

	protected String createMailBodyHigherTF(int subscription_id, Instrument instrument, Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine) throws JFException {
		// stab until proper HTML implemented
		return createMailBody4h(instrument, pPeriod, bidBar, logLine);
	}
	
	protected String getChannelColorBullish30min(Map<String, FlexLogEntry> mailValuesMap) {
		double
		triggerLowChannelPos30min = mailValuesMap.get("bullishCandleTriggerChannelPos30min").getDoubleValue(),
		volatility30min = mailValuesMap.get("volatility30min").getDoubleValue(),
		triggerLowKChannelPos30min = mailValuesMap.get("bullishCandleTriggerKChannelPos30min").getDoubleValue();
	
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
	
	protected String getChannelColorBearish30min(Map<String, FlexLogEntry> mailValuesMap) {
		double
		triggerHighChannelPos30min = mailValuesMap.get("bearishCandleTriggerChannelPos30min").getDoubleValue(),
		volatility30min = mailValuesMap.get("volatility30min").getDoubleValue(),
		triggerHighKChannelPos30min = mailValuesMap.get("bearishCandleTriggerKChannelPos30min").getDoubleValue();
		
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
	
	protected String get4hChannelColor(Map<String, FlexLogEntry> mailValuesMap, String fieldId) {
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
