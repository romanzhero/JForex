/*
 * Copyright (c) 2009 Dukascopy (Suisse) SA. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * -Redistribution of source code must retain the above copyright notice, this
 *  list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduce the above copyright notice,
 *  this list of conditions and the following disclaimer in the documentation
 *  and/or other materials provided with the distribution.
 * 
 * Neither the name of Dukascopy (Suisse) SA or the names of contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. DUKASCOPY (SUISSE) SA ("DUKASCOPY")
 * AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE
 * AS A RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL DUKASCOPY OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE,
 * EVEN IF DUKASCOPY HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 */
package singlejartest;

import com.dukascopy.api.Instrument;
import com.dukascopy.api.LoadingProgressListener;
import com.dukascopy.api.Period;
import com.dukascopy.api.system.ISystemListener;
import com.dukascopy.api.system.ITesterClient;
import com.dukascopy.api.system.TesterFactory;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.Future;

import jforex.strategies.FlatCascTest;
import jforex.utils.FXUtils;
import jforex.utils.props.ClimberProperties;

/**
 * This small program demonstrates how to initialize Dukascopy tester and start
 * a strategy
 */
public class StrategyTesterLoop {
	private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
	private static File tradeTestRunningSignal = null;
	// url of the DEMO jnlp
	private static String jnlpUrl = "https://www.dukascopy.com/client/demo/jclient/jforex.jnlp";

	public static void main(String[] args) throws Exception {
		final ClimberProperties properties = new ClimberProperties();
		if (args.length < 1) {
			LOGGER.error("One argument needed (name of config file)");
			System.exit(1);
		}

		try {
			properties.load(new FileInputStream(args[0]));
		} catch (IOException e) {
			LOGGER.error("Can't open or can't read properties file " + args[0]
					+ "...");
			System.exit(1);
		}
		if (properties.getProperty("noConsoleOutput", "no").equals("yes"))
			System.setOut(new PrintStream(".\\Strategy_log_"
							+ FXUtils.getFileTimeStamp(System.currentTimeMillis())
							+ ".log"));

		properties.validate(LOGGER);
		
		// get the instance of the IClient interface
		final ITesterClient client = TesterFactory.getDefaultInstance();
		// set the listener that will receive system events
		client.setSystemListener(new ISystemListener() {
			@Override
			public void onStart(long processId) {
				LOGGER.info("Strategy started: " + processId);
			}

			@Override
			public void onStop(long processId) {
				LOGGER.info("Strategy stopped: " + processId);
				File reportFile = new File(properties.getProperty("reportDirectory", ".")
						+ "\\Strategy_run_report_"
						+ FXUtils.getFileTimeStamp(System.currentTimeMillis())
						+ ".html");
				try {
					client.createReport(processId, reportFile);
				} catch (Exception e) {
					LOGGER.error(e.getMessage(), e);
				}

			}

			@Override
			public void onConnect() {
				LOGGER.info("Connected");
			}

			@Override
			public void onDisconnect() {
				// tester doesn't disconnect
			}
		});
		
		
		FXUtils.setDbToUse(properties.getProperty("dbToUse"));
		
		String timePeriod = properties.getProperty("period");
		if (timePeriod == null 
			|| timePeriod.length() == 0
			|| !(timePeriod.equalsIgnoreCase("y") || timePeriod.equalsIgnoreCase("m"))) {
			LOGGER.error("property period needed, must by either m - for months or y - for years");
			System.exit(1);			
		}
		String repeat = properties.getProperty("repeat");
		int repeatNo = -1;
		if (repeat == null || repeat.length() == 0) {
			LOGGER.error("repeat property must be set !");
			System.exit(1);			
		} else {
			try {
				repeatNo = Integer.parseInt(repeat);
			} catch (NumberFormatException e) {
				LOGGER.error("Format of repeat property wrong, must be integer: "
						+ repeat + ", exception " + e.getMessage());
				System.exit(1);
			}
		}
		String noOfPeriodsStr = properties.getProperty("howManyPeriods");
		int noOfPeriods = -1;
		if (noOfPeriodsStr == null || noOfPeriodsStr.length() == 0) {
			LOGGER.error("howManyPeriods property must be set !");
			System.exit(1);			
		} else {
			try {
				noOfPeriods = Integer.parseInt(noOfPeriodsStr);
			} catch (NumberFormatException e) {
				LOGGER.error("Format of repeat property wrong, must be integer: "
						+ repeat + ", exception " + e.getMessage());
				System.exit(1);
			}
		}		
		
		// set instruments that will be used in testing
		StringTokenizer st = new StringTokenizer(properties.getProperty("pairsToCheck"), ";");
		Set<Instrument> instruments = new HashSet<Instrument>();
		String pair = null;
		while (st.hasMoreTokens()) {
			String nextPair = st.nextToken();
			instruments.add(Instrument.fromString(nextPair));
			if (pair == null)
				pair = new String(nextPair);
		}
		Instrument selectedInstrument = Instrument.fromString(pair);

		DateTime startDate = new DateTime(properties.getTestIntervalStart().getMillis());
		for (int j = 0; j < repeatNo; j++) {
			DateTime endDate = null;
			if (timePeriod.equalsIgnoreCase("m"))
				endDate = startDate.plusMonths(noOfPeriods);
			else 
				endDate = startDate.plusYears(noOfPeriods);
			LOGGER.info("Starting backtest for the period: " + startDate.toString("dd.MM.yyyy") + " to " + endDate.toString("dd.MM.yyyy"));
			

			LOGGER.info("Connecting...");
			// connect to the server using jnlp, user name and password
			// connection is needed for data downloading
			client.connect(jnlpUrl, properties.getProperty("username"),	properties.getProperty("password"));

			// wait for it to connect
			int i = 10; // wait max ten seconds
			while (i > 0 && !client.isConnected()) {
				Thread.sleep(1000);
				i--;
			}
			if (!client.isConnected()) {
				LOGGER.error("Failed to connect Dukascopy servers");
				System.exit(1);
			}

			LOGGER.info("Subscribing instruments...");
			client.setCacheDirectory(new File(properties.getProperty("cachedir")));
			client.setSubscribedInstruments(instruments);
			// setting initial deposit
			client.setInitialDeposit(Instrument.EURUSD.getSecondaryJFCurrency(), Double.parseDouble(properties.getProperty("initialdeposit", "100000.0")));
			client.setDataInterval(Period.TICK, null, null, startDate.getMillis(), endDate.getMillis());
			// load data
			LOGGER.info("Downloading data");
			Future<?> future = client.downloadData(null);
			// wait for downloading to complete
			Thread.sleep(10000); // this timeout helped
			future.get();
			// start the strategy
			LOGGER.info("Starting strategy");
			// client.startStrategy(new IchiAutoEntry(properties,
			// properties.getTestIntervalStart().getMillis(),
			// properties.getTestIntervalEnd().getMillis(), startTime),
			tradeTestRunningSignal = new File("strategyTestRunning.bin");
			if (tradeTestRunningSignal.exists())
				tradeTestRunningSignal.delete();
			tradeTestRunningSignal.createNewFile();
			// once test run is finished this file should be deleted !
			client.startStrategy(//new SimpleMAsIDCrossTrendFollow(properties),
					new FlatCascTest(selectedInstrument, properties),
					new LoadingProgressListener() {
						@Override
						public void dataLoaded(long startTime, long endTime, long currentTime, String information) {
							LOGGER.info(information);
						}

						@Override
						public void loadingFinished(boolean allDataLoaded,	long startTime, long endTime, long currentTime) {
						}

						@Override
						public boolean stopJob() {
							return false;
						}
					});
			// now it's running			
			while (tradeTestRunningSignal.exists()) {	}
			
			startDate = endDate;
		}
		
		if (client.getStartedStrategies().size() == 0) {
			System.exit(0);
		}
		
	}
}
