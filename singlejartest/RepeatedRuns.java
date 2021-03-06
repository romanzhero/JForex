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

import com.dukascopy.api.IStrategy;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.LoadingProgressListener;
import com.dukascopy.api.system.ISystemListener;
import com.dukascopy.api.system.ITesterClient;
import com.dukascopy.api.system.TesterFactory;
import com.dukascopy.api.system.ITesterClient.DataLoadingMethod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jforex.explorers.IchimokuTradeTestRun;
import jforex.utils.FXUtils;
import jforex.utils.props.ClimberProperties;

/**
 * This small program demonstrates how to initialize Dukascopy tester and start
 * a strategy
 */
public class RepeatedRuns {
	private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

	// url of the DEMO jnlp
	private static String jnlpUrl = "https://www.dukascopy.com/client/demo/jclient/jforex.jnlp";
	private static File tradeTestRunningSignal = null;

	public static void main(String[] args) throws Exception {
		// get the instance of the IClient interface
		final ITesterClient client = TesterFactory.getDefaultInstance();
		final ClimberProperties properties = new ClimberProperties();
		// set the listener that will receive system events
		client.setSystemListener(new ISystemListener() {
			@Override
			public void onStart(long processId) {
				LOGGER.info("Strategy started: " + processId);
			}

			@Override
			public void onStop(long processId) {
				LOGGER.info("Strategy stopped: " + processId);
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

		if (args.length < 1) {
			LOGGER.error("Argument needed: name of config file");
			System.exit(1);
		}

		try {
			properties.load(new FileInputStream(args[0]));
		} catch (IOException e) {
			LOGGER.error("Can't open or can't read properties file " + args[0]
					+ "...");
			System.exit(1);
		}

		properties.validate(LOGGER);

		LOGGER.info("Connecting...");
		// connect to the server using jnlp, user name and password
		// connection is needed for data downloading
		client.connect(jnlpUrl, properties.getProperty("username"),
				properties.getProperty("password"));

		// wait for it to connect
		int i = 10; // wait max ten seconds
		while (i > 0 && !client.isConnected()) {
			Thread.sleep(1000);
			i--;
		}
		if (!client.isConnected()) {
			LOGGER.error("Failed to connect to Dukascopy servers");
			System.exit(1);
		}

		// setting initial deposit
		client.setCacheDirectory(new File(properties.getProperty("cachedir")));

		int runNo = 0;
		ResultSet dbTestTradesCnt = FXUtils.dbCountTestTrades(properties);
		while (dbTestTradesCnt.next() && dbTestTradesCnt.getInt(1) > 0) {
			System.out.println("Entered loop " + (runNo + 1)
					+ "; records to process: " + dbTestTradesCnt.getInt(1));
			// get all trades to run through strategy

			ResultSet dbTestTrades = FXUtils.dbGetTestTrades(properties);
			while (dbTestTrades.next()) {
				runNo++;
				String ticker = dbTestTrades.getString("Label").substring(0, 6), tradeDirection = dbTestTrades
						.getString("Event"), startDateStr = dbTestTrades
						.getString("EventStart"), endDateStr = dbTestTrades
						.getString("EventEnd");

				Date startDate = FXUtils.getDateTimeFromString(startDateStr);
				if (startDate == null) {
					LOGGER.error("Wrong start date format: " + startDateStr
							+ "; expecting dd.MM.yyy HH:mm");
					continue;
				}
				Date endDate = FXUtils.getDateTimeFromStringGMT(endDateStr);
				if (endDate == null) {
					LOGGER.error("Wrong start date format: " + endDateStr
							+ "; expecting dd.MM.yyy HH:mm");
					continue;
				}

				client.setInitialDeposit(Instrument.EURUSD
						.getSecondaryCurrency(), Double.parseDouble(properties
						.getProperty("initialdeposit", "1000000.0")));

				Set<Instrument> instruments = new HashSet<Instrument>();
				String instrumentID = new String(ticker.substring(0, 3) + "/"
						+ ticker.substring(3));
				Instrument currentInstrument = Instrument
						.fromString(instrumentID);
				instruments.add(currentInstrument);

				LOGGER.info("Subscribing instruments...");
				client.setSubscribedInstruments(instruments);

				// need to widen the interval end in order to catch exit at big
				// ATR + cloud
				client.setDataInterval(DataLoadingMethod.ALL_TICKS, startDate
						.getTime(), FXUtils.plusTradingTime(endDate.getTime(),
						3 * 24 * 3600 * 1000));

				tradeTestRunningSignal = new File("tradeTestRunning.bin");
				if (tradeTestRunningSignal.exists())
					tradeTestRunningSignal.delete();
				tradeTestRunningSignal.createNewFile();
				// once test run is finished this file should be deleted !

				// load data
				LOGGER.info("Downloading data");
				Future<?> future = client.downloadData(null);
				// wait for downloading to complete
				try {
					Thread.sleep(20000); // this timeout helped
					future.get(120, TimeUnit.SECONDS);
				} catch (TimeoutException e) {
					System.out.println("Can't get data for: "
							+ dbTestTrades.getString("Label"));
					e.printStackTrace();
				}
				// start the strategy
				LOGGER.info("Starting strategy");
				IStrategy strategyToRun = new IchimokuTradeTestRun(runNo,
						currentInstrument, dbTestTrades.getString("Label"),
						tradeDirection.equals("BUY EXIT"), endDate.getTime(),
						properties);
				client.startStrategy(strategyToRun,
						new LoadingProgressListener() {
							@Override
							public void dataLoaded(long startTime,
									long endTime, long currentTime,
									String information) {
								LOGGER.info(information);
							}

							@Override
							public void loadingFinished(boolean allDataLoaded,
									long startTime, long endTime,
									long currentTime) {
							}

							@Override
							public boolean stopJob() {
								return false;
							}
						});
				// now it's running - wait until it finishes before starting
				// next test trade run
				while (tradeTestRunningSignal != null
						&& tradeTestRunningSignal.exists()) {
					// Thread.sleep(10000);
				}
			}
			dbTestTradesCnt = FXUtils.dbCountTestTrades(properties);
		}
		if (client.getStartedStrategies().size() == 0) {
			System.exit(0);
		}
	}

}
