/**
 * Special class to generate signals only for one pair and timeframe
 * It's being called by another class taking care of onBar calls and everything else
 */
package jforex.explorers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

import jforex.AdvancedMailCreator;
import jforex.emailflex.FlexEmailElementFactory;
import jforex.emailflex.IFlexEmailElement;
import jforex.emailflex.IFlexEmailElement.SignalResult;
import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;
import jforex.utils.SignalGeneratorResponse;
import jforex.utils.StrategyResponseListener;

/**
 * Special class to generate signals only for one pair and timeframe
 * It's being called by another class taking care of onBar calls and everything else
 */
public class OTTLeanSignalGenerator extends AdvancedMailCreator implements IStrategy {
	
	protected IBar bidBar, askBar;
	protected Period timeframe;
	protected Instrument pair;
	private StrategyResponseListener strategyResponseListener;

	public OTTLeanSignalGenerator(Properties props, IBar pBidBar, IBar pAskBar, Period pTimeframe, Instrument pPair, StrategyResponseListener response) {
		super(props);
		strategyResponseListener = response;
		bidBar = pBidBar;
		askBar = pAskBar;
		timeframe = pTimeframe;
		pair = pPair;
	}

	/* (non-Javadoc)
	 * @see jforex.BasicStrategy#getStrategyName()
	 */
	@Override
	protected String getStrategyName() {
		return "OTTLeanSignalGenerator";
	}

	/* (non-Javadoc)
	 * @see jforex.BasicStrategy#getReportFileName()
	 */
	@Override
	protected String getReportFileName() {
		return null;
	}

	@Override
	public void onStart(IContext context) throws JFException {
		super.onStartExec(context);
		if (conf.getProperty("mailToDBOnly", "no").equals("yes") || conf.getProperty("barStatsToDB", "no").equals("yes")) {
			dbLogOnStart();
		}
		// all the processing is here
		if (skipPairs.contains(pair))
			return;
		
		if (!tradingAllowed(bidBar.getTime())) {
			return;
		}
		
		// effectively here we steer which timeframes are possible at all in our system ! By listing them in FXUtils.timeFrameNamesMap !!!
		if (!FXUtils.timeFrameNamesMap.containsKey(timeframe.toString())) {
            return;			
		}	
		log.print("Called for instrument " + pair.toString() + ", timeframe " + timeframe.toString() + " and bidBar time of " + FXUtils.getFormatedBarTimeWithSecs(bidBar));
        
		List<FlexLogEntry> logLine = new ArrayList<FlexLogEntry>();        
		eventsSource.collectOneTimeFrameStats(pair, timeframe, bidBar, logLine);
		ResultSet strategiesToCalculate = dbGetAllSubscriptionsOptions(pair, timeframe);
		String 
			mailBody = null,
			mailSubject = null;  

		boolean 
			notEmpty = false,
			signal = false;
		
		mailBody = 
			new String("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"
			+ "<html xmlns=\"http://www.w3.org/1999/xhtml\">"
			+ "<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" /><title>Table</title></head><body>" + 
			"Report for " + pair.toString() + " (special), " + FXUtils.getFormatedTimeCET(bidBar.getTime()) 
			+ " CET (time frame: " + timeframe.toString() 
			+ ")");
		
		List<IFlexEmailElement> resultList = new ArrayList<IFlexEmailElement>();
		ArrayList<String> signals = new ArrayList<String>();
		try {
			while (strategiesToCalculate.next()) {
				IFlexEmailElement e = FlexEmailElementFactory.create(strategiesToCalculate.getString("option_name"));
				if (e == null) {
					log.print("No mapped Java class for DB element " + strategiesToCalculate.getString("option_name"));
				}
				else
					resultList.add(e);				
			}
			
			mailBody += printSimpleTableHeader();
			for (IFlexEmailElement e : resultList) {
				String html = null;
				SignalResult result = null;
				if (e.isGeneric())
					result = e.detectSignal(pair, timeframe, bidBar, logLine, logDB);
				else
					result = e.detectSignal(pair, timeframe, bidBar, history, indicators, trendDetector, channelPosition, momentum, vola, tradeTrigger, conf, logLine, logDB);
				html = result.mailBody;
				if (e.isSignal()) {
					signal = true;
					signals.add(result.insertSQL);
				}
				if (html != null && html.length() > 0
					&& !html.toLowerCase().contains("not implemented")
					&& !html.toUpperCase().contains("NONE")) {
					mailBody += html;
					notEmpty = true;
				}				
			}
		} catch (SQLException ex) {
			   log.print("Log database problem: " + ex.getMessage() + " while trying to get subscription options");
			   log.close();
		       System.exit(1);
		}
		if (signal && notEmpty)
			mailBody += "</table></body></html>";
		else
			mailBody = null;
			
		mailSubject = new String("FX pair report for " + timeframe.toString() + " timeframe: " + pair.toString() + " at " + FXUtils.getFormatedTimeCET(bidBar.getTime()));
						
		if (conf.getProperty("logMail", "no").equals("yes")  && mailBody != null)
			log.print(mailBody);
		
		SignalGeneratorResponse response = new SignalGeneratorResponse(pair, timeframe, bidBar.getTime());
		response.emailSubject = mailSubject;
		response.emailBody = mailBody;
		response.signals = new String[signals.size()];
		int i = 0;
		for (String s : signals) {
			response.signals[i++] = new String(s);
		}
		strategyResponseListener.processResponse(response);
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException {	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {	}

	@Override
	public void onMessage(IMessage message) throws JFException { }

	@Override
	public void onAccount(IAccount account) throws JFException { }

	@Override
	public void onStop() throws JFException { super.onStartExec(context);	}

	protected ResultSet dbGetAllSubscriptionsOptions(Instrument instrument, Period pTimeFrame) {
		String 
			statementStr = new String("SELECT DISTINCT option_name FROM " + FXUtils.getDbToUse() + ".v_subscription_options " 
				+ "WHERE time_frame_basic = time_frame_higher and time_frame_higher = time_frame_highest "
				+ "and ticker = '" + instrument.toString() + "' AND time_frame_basic = " + FXUtils.toTimeFrameCode(pTimeFrame, logDB, log));
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

}
