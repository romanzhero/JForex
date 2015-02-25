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
import com.dukascopy.api.Period;
import com.dukascopy.api.system.ISystemListener;
import com.dukascopy.api.system.ITesterClient;
import com.dukascopy.api.system.TesterFactory;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.Future;

/**
 * This small program demonstrates how to initialize Dukascopy tester and start a strategy
 */
public class Strategy30MinRepeat {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    //url of the DEMO jnlp
    private static String jnlpUrl = "https://www.dukascopy.com/client/demo/jclient/jforex.jnlp";

    public static void main(String[] args) throws Exception {
        //get the instance of the IClient interface
        final ITesterClient client = TesterFactory.getDefaultInstance();
        final Properties properties = new Properties();
        //set the listener that will receive system events
        client.setSystemListener(new ISystemListener() {
            @Override
            public void onStart(long processId) {
                LOGGER.info("Strategy started: " + processId);
            }

            @Override
            public void onStop(long processId) {
                LOGGER.info("Strategy stopped: " + processId);
                //TODO: find a way to run in Java repeadetly
                if (client.getStartedStrategies().size() == 0) {
                    System.exit(0);
                }
            }

            @Override
            public void onConnect() {
                LOGGER.info("Connected");
            }

            @Override
            public void onDisconnect() {
                //tester doesn't disconnect
            }
        });

        if (args.length < 2) {
            LOGGER.error("Two arguments needed: name of config file and explorer class ID");
            System.exit(1);        	
        }
        
        try {
            properties.load(new FileInputStream(args[0]));
        } catch (IOException e) {
            LOGGER.error("Can't open or can't read properties file " + args[0] + "...");
            System.exit(1);
        }
        
        
        LOGGER.info("Connecting...");
        //connect to the server using jnlp, user name and password
        //connection is needed for data downloading
        client.connect(jnlpUrl, properties.getProperty("username"), properties.getProperty("password"));

        //wait for it to connect
        int i = 10; //wait max ten seconds
        while (i > 0 && !client.isConnected()) {
            Thread.sleep(1000);
            i--;
        }
        if (!client.isConnected()) {
            LOGGER.error("Failed to connect Dukascopy servers");
            System.exit(1);
        }      	
        	
        //set instruments that will be used in testing
        StringTokenizer st = new StringTokenizer(properties.getProperty("pairsToCheck"), ";");
        Set<Instrument> instruments = new HashSet<Instrument>();
        while(st.hasMoreTokens()) {
        	String nextPair = st.nextToken();
            instruments.add(Instrument.fromString(nextPair));        	
        }
        
        LOGGER.info("Subscribing instruments...");
        client.setSubscribedInstruments(instruments);
        //setting initial deposit
        client.setInitialDeposit(Instrument.EURUSD.getSecondaryCurrency(), Double.parseDouble(properties.getProperty("initialdeposit", "50000.0")));
        client.setCacheDirectory(new File(properties.getProperty("cachedir")));
              
        // key idea is here: ignore config file and setup test interval artificially to last two 30min intervals
    	// round to last 30min ended
    	DateTime roundedEnd = calcEndBar();
    	DateTime startInterval = new DateTime(roundedEnd.minusMinutes(30));
    	
    	client.setDataInterval(Period.TICK, null, null, startInterval.getMillis(), roundedEnd.getMillis());
        //load data
        LOGGER.info("Downloading data");
        Future<?> future = client.downloadData(null);
        //wait for downloading to complete
        future.get();
        //start the strategy
        LOGGER.info("Starting strategy");
        IStrategy strategyToRun = new CalledStrategy();
        client.startStrategy(strategyToRun, 
        	new LoadingProgressListener() {
		        @Override
		        public void dataLoaded(long startTime, long endTime, long currentTime, String information) {
		            LOGGER.info(information);
		        }
		
		        @Override
		        public void loadingFinished(boolean allDataLoaded, long startTime, long endTime, long currentTime) {
		        }
		
		        @Override
		        public boolean stopJob() {
		            return false;
		        }
        	});
        //now it's running
    }

    protected static DateTime calcEndBar() {
		// During weekend set end bar to last trading bar of previous Friday
		DateTime 
			timeStamp = new DateTime(),
			roundedEnd = new DateTime(timeStamp.getYear(), timeStamp.getMonthOfYear(), timeStamp.getDayOfMonth(), 
    			timeStamp.getHourOfDay(), timeStamp.getMinuteOfHour() < 30 ? 0 : 30, 0, 0),
    		result = new DateTime(roundedEnd);
		if ((roundedEnd.getDayOfWeek() == DateTimeConstants.FRIDAY && roundedEnd.getHourOfDay() > 22)
				|| roundedEnd.getDayOfWeek() == DateTimeConstants.SATURDAY
				|| (roundedEnd.getDayOfWeek() == DateTimeConstants.SUNDAY && roundedEnd.getHourOfDay() < 23)) {
			if (roundedEnd.getDayOfWeek() == DateTimeConstants.FRIDAY)
				result = new DateTime(roundedEnd.getYear(), roundedEnd.getMonthOfYear(), roundedEnd.getDayOfMonth(), 22, 30, 0, 0);
			else if (roundedEnd.getDayOfWeek() == DateTimeConstants.SATURDAY) {
				DateTime friday = new DateTime(roundedEnd.minusDays(1));
				result = new DateTime(friday.getYear(), friday.getMonthOfYear(), friday.getDayOfMonth(), 22, 30, 0, 0);
			}
			else if (roundedEnd.getDayOfWeek() == DateTimeConstants.SUNDAY && roundedEnd.getHourOfDay() < 23) {
				DateTime friday = new DateTime(roundedEnd.minusDays(2));
				result = new DateTime(friday.getYear(), friday.getMonthOfYear(), friday.getDayOfMonth(), 22, 30, 0, 0);
			} 
		}
		return result;
	}
}
