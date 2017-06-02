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
import com.dukascopy.api.system.ClientFactory;
import com.dukascopy.api.system.IClient;
import com.dukascopy.api.system.ISystemListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import jforex.autoentry.IchiAutoEntry;
import jforex.utils.FXUtils;
import jforex.utils.props.ClimberProperties;

/**
 * This small program demonstrates how to initialize Dukascopy tester and start
 * a strategy
 */
public class IchiAutoEntryRunWeakReconnect {
	private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

	// url of the DEMO jnlp
	private static String jnlpUrl = "https://www.dukascopy.com/client/demo/jclient/jforex.jnlp";

	public static void main(String[] args) throws Exception {
		// get the instance of the IClient interface
		final IClient client = ClientFactory.getDefaultInstance();
		final ClimberProperties properties = new ClimberProperties();

		// set the listener that will receive system events
		client.setSystemListener(new ISystemListener() {
			private int lightReconnects = 5;

			@Override
			public void onStart(long processId) {
				LOGGER.info("Strategy started: " + processId);
			}

			@Override
			public void onStop(long processId) {
				LOGGER.info("Strategy stopped: " + processId);
				if (client.getStartedStrategies().size() == 0) {
					System.exit(0);
				}
			}

			@Override
			public void onConnect() {
				LOGGER.info("Connected");
				lightReconnects = 5;
			}

			@Override
			public void onDisconnect() {
				LOGGER.warn("Disconnected");
				if (lightReconnects > 0) {
					client.reconnect();
					--lightReconnects;
				}
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

		LOGGER.info("Connecting...");
		// connect to the server using jnlp, user name and password
		// connection is needed for data downloading

		int attempt = 0;
		while (attempt++ < 5 && !client.isConnected()) {
			client.connect(jnlpUrl, properties.getProperty("username"),
					properties.getProperty("password"));

			// wait for it to connect
			int i = 10; // wait max ten seconds
			while (i > 0 && !client.isConnected()) {
				Thread.sleep(1000);
				i--;
			}

			// not successful, try again in 30 sec
			if (!client.isConnected())
				Thread.sleep(30 * 1000);
		}
		if (!client.isConnected()) {
			LOGGER.error("Failed to connect Dukascopy servers");
			System.exit(1);
		}

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

		long theTime = System.currentTimeMillis();
		client.startStrategy(new IchiAutoEntry(properties, theTime, theTime,
				theTime));
	}

}
