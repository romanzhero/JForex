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
package jforex.liverun;

import com.dukascopy.api.Instrument;
import com.dukascopy.api.system.ClientFactory;
import com.dukascopy.api.system.IClient;
import com.dukascopy.api.system.ISystemListener;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import singlejartest.Main;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import jforex.explorers.PaceStatsCollector;
import jforex.utils.ClimberProperties;
import jforex.utils.FXUtils;

/**
 * This small program demonstrates how to initialize Dukascopy tester and start
 * a strategy
 */
public class Explorer30MinLive {
	private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

	private static File requestedCallSignal = null;
	private static File strategyRunningSignal = new File("strategyRunning.bin");

	public static void main(String[] args) throws Exception {
		// get the instance of the IClient interface
		final IClient client = ClientFactory.getDefaultInstance();
		final ClimberProperties properties = new ClimberProperties();

		// set the listener that will receive system events
		client.setSystemListener(new ISystemListener() {
			@Override
			public void onStart(long processId) {
				LOGGER.info("Strategy started: " + processId);
				try {
					strategyRunningSignal.createNewFile();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			@Override
			public void onStop(long processId) {
				LOGGER.info("Strategy stopped: " + processId);
				if (client.getStartedStrategies().size() == 0) {
					// System.exit(0);
					strategyRunningSignal.delete();
				}
			}

			@Override
			public void onConnect() {
				LOGGER.info("Connected");
			}

			@Override
			public void onDisconnect() {
				LOGGER.info("Disconnected at "
						+ FXUtils.getFormatedTimeCET(System.currentTimeMillis())
						+ " CET");
			}
		});

		if (args.length < 1) {
			LOGGER.error("One argument needed: name of config file");
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
		FXUtils.setDbToUse(properties.getProperty("dbToUse"));

		LOGGER.info("Connecting...");
		// connect to the server using jnlp, user name and password
		// connection is needed for data downloading

		int attempt = 0;
		while (attempt++ < 15 && !client.isConnected()) {
			client.connect(
					properties
							.getProperty("environment_url",
									"https://www.dukascopy.com/client/demo/jclient/jforex.jnlp"),
					properties.getProperty("username"), properties
							.getProperty("password"));

			// wait for it to connect
			int i = 30; // wait max thirty seconds
			while (i > 0 && !client.isConnected()) {
				Thread.sleep(1000);
				i--;
			}

			// not successful, try again in 30 sec
			if (!client.isConnected()) {
				LOGGER.info("Connection attempt " + attempt
						+ " not successful, wil re-try in 30 secs...");
				Thread.sleep(30 * 1000);
			}
		}
		if (!client.isConnected()) {
			LOGGER.error("Failed to connect Dukascopy servers after " + attempt
					+ " attempts, exiting");
			System.exit(1);
		}

		// set instruments that will be used in testing. To set different
		// timeframe per pair format is <pair>,<timeframe>;<pair>,<timeframe>...
		// StringTokenizer st = new
		// StringTokenizer(properties.getProperty("pairsToCheck"), ";");
		// Set<Instrument> instruments = new HashSet<Instrument>();
		// while(st.hasMoreTokens()) {
		// String nextPair = st.nextToken();
		// StringTokenizer st2 = new StringTokenizer(nextPair, ",");
		// instruments.add(Instrument.fromString(st2.nextToken()));
		// }
		// get all instruments having a subscription
		Set<Instrument> instruments = new HashSet<Instrument>();
		ResultSet dbInstruments = FXUtils
				.dbGetAllSubscribedInstruments(properties);
		while (dbInstruments.next()) {
			instruments.add(Instrument.fromString(dbInstruments
					.getString("ticker")));
		}

		LOGGER.info("Subscribing instruments...");
		client.setSubscribedInstruments(instruments);
		// setting initial deposit
		client.setCacheDirectory(new File(properties.getProperty("cachedir")));

		attempt = 0;
		requestedCallSignal = new File("requestedCall.bin");
		requestedCallSignal.createNewFile();
		DateTime current = new DateTime();
		long strategyID = client.startStrategy(new PaceStatsCollector(
				properties));
		while (!requestedBarDone()
				&& ((current.getMinuteOfHour() >= 56 || current
						.getMinuteOfHour() <= 4)
						|| (current.getMinuteOfHour() >= 6 && current
								.getMinuteOfHour() <= 14)
						|| (current.getMinuteOfHour() >= 16 && current
								.getMinuteOfHour() <= 24)
						|| (current.getMinuteOfHour() >= 26 && current
								.getMinuteOfHour() <= 34)
						|| (current.getMinuteOfHour() >= 36 && current
								.getMinuteOfHour() <= 44) || (current
						.getMinuteOfHour() >= 46 && current.getMinuteOfHour() <= 54))) {
			Thread.sleep(30000);
			current = new DateTime();
			attempt++;
		}
		client.stopStrategy(strategyID);

		if (requestedCallSignal.exists()) {
			requestedCallSignal.delete();
			LOGGER.info("Unsuccessful run after " + attempt + " attempts");
		} else
			LOGGER.info("Successful run after " + attempt + " attempts");
		if (strategyRunningSignal.exists())
			strategyRunningSignal.delete();
		System.exit(0);
	}

	private static boolean requestedBarDone() {
		if (requestedCallSignal == null)
			return true;
		return !requestedCallSignal.exists();
	}
}
