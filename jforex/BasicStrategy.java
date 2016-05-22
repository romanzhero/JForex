package jforex;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;

import jforex.utils.FXUtils;
import jforex.utils.Logger;

import com.dukascopy.api.IConsole;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;

public abstract class BasicStrategy {

	protected IConsole console;
	protected IHistory history;
	protected IIndicators indicators;
	protected Properties conf = null;
	protected Logger log;
	protected String reportDir;
	protected IContext context;
	protected IEngine engine;

	protected Map<Instrument, String> pairsTimeFrames;
	protected Set<Instrument> skipPairs;
	protected Connection logDB = null;

	public BasicStrategy() {
		super();
	}

	protected abstract String getStrategyName();

	protected abstract String getReportFileName();

	public BasicStrategy(Properties props) {
		super();
		conf = props;
		reportDir = props.getProperty("reportDirectory", ".");
	}

	public void onStartExec(IContext context) throws JFException {
		this.context = context;
		this.engine = context.getEngine();
		this.console = context.getConsole();
		this.history = context.getHistory();
		this.indicators = context.getIndicators();

		pairsTimeFrames = new HashMap<Instrument, String>();
		// parse timeframes per pair, they are in format
		// <pair>,<timeframe>;<pair>,<timeframe>...
		StringTokenizer st = new StringTokenizer(conf.getProperty("pairsToCheck"), ";");
		while (st.hasMoreTokens()) {
			String nextPair = st.nextToken();
			StringTokenizer st2 = new StringTokenizer(nextPair, ",");
			Instrument nextInstrument = Instrument.fromString(st2.nextToken());
			String tf = null;
			if (st2.hasMoreTokens())
				tf = new String(st2.nextToken());
			pairsTimeFrames.put(nextInstrument, tf);
		}

		skipPairs = new HashSet<Instrument>();
		StringTokenizer stSkipPairs = new StringTokenizer(conf.getProperty("skipPairs", ""), ";");
		while (stSkipPairs.hasMoreTokens()) {
			Instrument nextInstrument = Instrument.fromString(stSkipPairs.nextToken());
			skipPairs.add(nextInstrument);

		}

		DateTime ts = new DateTime();
		String tsString = ts.toString("yyyy_MM_dd_HH_mm_ss");
		this.log = conf.getProperty("logToFileOnly", "no").equals("yes") ? new Logger(
				reportDir + "//" + getReportFileName() + tsString + ".txt")
				: new Logger(console, reportDir + "//" + getReportFileName() + tsString + ".txt");
		// Print header of the logfile
		log.print("Starting strategy " + getStrategyName() + "; Logging to " + reportDir + "//" + getReportFileName() + tsString + ".txt");
		log.print("Configuration:");
		Enumeration<Object> e = conf.keys();
		while (e.hasMoreElements()) {
			String key = (String) e.nextElement();
			log.print(key + " = " + conf.getProperty(key));
		}
	}

	protected boolean tradingAllowed(long time) {
		// No trading after Fri 17h, on Christmas and New Years Eve
		DateTime timeStamp = new DateTime(time);
		return !((timeStamp.getDayOfWeek() == DateTimeConstants.FRIDAY && timeStamp
				.getHourOfDay() > 22)
				|| timeStamp.getDayOfWeek() == DateTimeConstants.SATURDAY || (timeStamp
				.getDayOfWeek() == DateTimeConstants.SUNDAY && timeStamp
				.getHourOfDay() < 23));
	}

	public void onStopExec() throws JFException {
		log.print("Stopping strategy " + getStrategyName());
		for (IOrder entryOrder : engine.getOrders()) {
			if (entryOrder.getState().equals(IOrder.State.FILLED)) {
				log.print("Closing order " + entryOrder.getLabel()
						+ "; Profit/loss: "
						+ FXUtils.df1.format(entryOrder.getProfitLossInPips())
						+ " pips");
				engine.getOrder(entryOrder.getLabel()).close();
			}
		}
		log.close();
	}

	protected void dbLogOnStart() {
		try {
			Class.forName(conf.getProperty("sqlDriver",
					"sun.jdbc.odbc.JdbcOdbcDriver"));
		} catch (java.lang.ClassNotFoundException e1) {
			log.print("ODBC driver load failure (ClassNotFoundException: "
					+ e1.getMessage() + ")");
			log.close();
			System.exit(1);
		}
		String url = conf.getProperty("odbcURL");
		try {
			logDB = DriverManager.getConnection(url,
					conf.getProperty("dbUserName", ""),
					conf.getProperty("dbPassword", ""));
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.close();
			System.exit(1);
		}
	}

}