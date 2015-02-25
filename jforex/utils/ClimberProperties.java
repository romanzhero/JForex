package jforex.utils;

import java.io.File;


import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;

public class ClimberProperties extends SortedProperties {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1895673503665642586L;

	protected DateTime testIntervalStart = null;
	protected DateTime testIntervalEnd = null;
	
	public void validate(Logger pLog) {
        if (!containsKey("username")) {
            pLog.error("Dukascopy user name must be set...");
            System.exit(1);
	    }
        if (!containsKey("password")) {
            pLog.error("Dukascopy password must be set...");
            System.exit(1);
	    }

        if (!containsKey("initialdeposit")) {
        	setProperty("initialdeposit", "50000.0");        
        	pLog.info("No initial deposit set, using 50000 USD");
        }
        if (!containsKey("cachedir")) {
                pLog.error("Cache directory (cachedir key properties file) must be set...");
                System.exit(1);
        }
        else {
        	// check that it exists...
        	File cachedir = new File(getProperty("cachedir"));
        	if (!cachedir.isDirectory() || !cachedir.canRead() || !cachedir.canWrite()) {
                pLog.error("Cache directory " + getProperty("cachedir") + " either doesn't exist or can't be read or write, please check...");
                System.exit(1);        		
        	}
        }
        if (!containsKey("pairsToCheck")) {
            pLog.error("Pairs to check must be set...");
            System.exit(1);
	    }

        
        DateTime now = new DateTime();
        if (!containsKey("testIntervalStart")) {
            pLog.info("No start of test interval set, setting interval to 6 months before up to now...");
            testIntervalStart = now.minusMonths(6);
            testIntervalEnd = now;
        }
        else {
        	//TODO: enable entering hours and minutes !
        	DateTimeFormatter fmt = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm");
        	try {
				testIntervalStart = fmt.parseDateTime(getProperty("testIntervalStart"));
			} catch (IllegalArgumentException e2) {
                pLog.error("Format of test interval start date wrong: " + getProperty("testIntervalStart") + ", exception " + e2.getMessage());
                System.exit(1);        		
			} catch (UnsupportedOperationException e1) {
                pLog.error("Format of test interval start date wrong: " + getProperty("testIntervalStart") + ", exception " + e1.getMessage());
                System.exit(1);        		
			}
	        if (!containsKey("testIntervalEnd")) {
	            pLog.info("No end of test interval set, setting to now...");
	            testIntervalEnd = now;
	        }
	        else {
	        	try {
					testIntervalEnd = fmt.parseDateTime(getProperty("testIntervalEnd"));
				} catch (IllegalArgumentException e2) {
	                pLog.error("Format of test interval end date wrong: " + getProperty("testIntervalEnd") + ", exception " + e2.getMessage());
	                System.exit(1);        		
				} catch (UnsupportedOperationException e1) {
	                pLog.error("Format of test interval end date wrong: " + getProperty("testIntervalEnd") + ", exception " + e1.getMessage());
	                System.exit(1);        		
				}
	        }
        }
        
        if (!containsKey("MAX_RISK")) {
        	setProperty("MAX_RISK", "35");        
        	pLog.info("No maximum allowed risk set, using 35 pips");
        }
        else {
        	try {
        		Integer.parseInt(getProperty("MAX_RISK"));
			} catch (NumberFormatException e) {
                pLog.error("Format of maximum risk wrong, must be integer: " + getProperty("MAX_RISK") + ", exception " + e.getMessage());
                System.exit(1);        		
			}
        }
        	
        if (!containsKey("BREAK_EVEN_PROFIT_THRESHOLD")) {
        	setProperty("BREAK_EVEN_PROFIT_THRESHOLD", "60");        
        	pLog.info("No break even profit threshold set, using 60 pips");
        }
        else {
        	try {
            	Integer.parseInt(getProperty("BREAK_EVEN_PROFIT_THRESHOLD"));
			} catch (NumberFormatException e) {
                pLog.error("Format of breakeven profit level wrong, must be integer: " + getProperty("BREAK_EVEN_PROFIT_THRESHOLD") + ", exception " + e.getMessage());
                System.exit(1);        		
			}
        }

        if (!containsKey("PROTECT_PROFIT_THRESHOLD")) {
        	setProperty("PROTECT_PROFIT_THRESHOLD", "100");        
        	pLog.info("No profit protection level set, using 100 pips");
        }
        else {
        	try {
            	Integer.parseInt(getProperty("PROTECT_PROFIT_THRESHOLD"));
			} catch (NumberFormatException e) {
                pLog.error("Format of profit protection level wrong, must be integer: " + getProperty("PROTECT_PROFIT_THRESHOLD") + ", exception " + e.getMessage());
                System.exit(1);        		
			}
        }
        if (!containsKey("PROTECT_PROFIT_THRESHOLD_OFFSET")) {
        	setProperty("PROTECT_PROFIT_THRESHOLD_OFFSET", "20");        
        	pLog.info("No profit protection offset set, using 20 pips");
        }
        else {
        	try {
            	Integer.parseInt(getProperty("PROTECT_PROFIT_THRESHOLD_OFFSET"));
			} catch (NumberFormatException e) {
                pLog.error("Format of profit protection offset wrong, must be integer: " + getProperty("PROTECT_PROFIT_THRESHOLD_OFFSET") + ", exception " + e.getMessage());
                System.exit(1);        		
			}
        }
        
        if (!containsKey("leverage")) {
        	setProperty("leverage", "5");        
        	pLog.info("No leverage set, using 5x");        	
        }
        else {
        	try {
            	Integer.parseInt(getProperty("leverage"));
			} catch (NumberFormatException e) {
                pLog.error("Format of leverage wrong, must be integer: " + getProperty("leverage") + ", exception " + e.getMessage());
                System.exit(1);        		
			}        	
        }
        if (!containsKey("flexibleLeverage")) {
        	setProperty("flexibleLeverage", "false");        
        	pLog.info("No flexible leverage rule, using none");        	
        }
        else {
            	if (!getProperty("flexibleLeverage").equals("yes") 
            		&& !getProperty("flexibleLeverage").equals("no")) {
                    pLog.error("Format of leverage rule wrong (" + getProperty("flexibleLeverage") + "), must be either yes or no");
                    System.exit(1);        		
    			}        		
        }
        if (!containsKey("reportDirectory")) {
        	setProperty("reportDirectory", ".");        
        	pLog.info("No report directory set, using current");        	
        }
        else {
	    	// check that it exists...
	    	File reportDir = new File(getProperty("reportDirectory"));
	    	if (!reportDir.isDirectory() || !reportDir.canRead() || !reportDir.canWrite()) {
	            pLog.error("Report directory " + getProperty("reportDirectory") + " either doesn't exist or can't be read or write, please check...");
	            System.exit(1);        		
	    	}
        }
        if (!containsKey("trendStDevDefinitionMin")) {
        	setProperty("trendStDevDefinitionMin", "-0.7");        
        	pLog.info("No trend StDev definition min. value set, using -0.7");
        }
        else {
        	try {
        		Double.parseDouble(getProperty("trendStDevDefinitionMin"));
			} catch (NumberFormatException e) {
                pLog.error("Format of trend StDev definition minimum wrong, must be decimal number: " + getProperty("trendStDevDefinitionMin") + ", exception " + e.getMessage());
                System.exit(1);        		
			}
        }
        if (!containsKey("trendStDevDefinitionMax")) {
        	setProperty("trendStDevDefinitionMax", "3.18");        
        	pLog.info("No trend StDev definition max. value set, using 3.18");
        }
        else {
        	try {
        		Double.parseDouble(getProperty("trendStDevDefinitionMax"));
			} catch (NumberFormatException e) {
                pLog.error("Format of trend StDev definition maximum wrong, must be decimal number: " + getProperty("trendStDevDefinitionMax") + ", exception " + e.getMessage());
                System.exit(1);        		
			}
        }
        if (!containsKey("safeZone")) {
        	setProperty("safeZone", "3.0");        
        	pLog.info("No SafeZone set, using 3 pips");
        }
        else {
        	try {
        		Double.parseDouble(getProperty("safeZone"));
			} catch (NumberFormatException e) {
                pLog.error("Format of SafeZone wrong, must be decimal number: " + getProperty("safeZone") + ", exception " + e.getMessage());
                System.exit(1);        		
			}
        }

        if (!containsKey("FILTER_MOMENTUM")) {
        	pLog.info("No momentum filtering set, using none");        	
        }
        else {
            	if (!getProperty("FILTER_MOMENTUM").equals("yes") 
            		&& !getProperty("FILTER_MOMENTUM").equals("no")) {
                    pLog.error("Format of momentum filter rule wrong (" + getProperty("FILTER_MOMENTUM") + "), must be either yes or no");
                    System.exit(1);        		
    			}        		
        }
        if (!containsKey("FILTER_ENTRY_BAR_HIGH")) {
        	pLog.info("No entry bar high filtering set, using none");        	
        }
        else {
            	if (!getProperty("FILTER_ENTRY_BAR_HIGH").equals("yes") 
            		&& !getProperty("FILTER_ENTRY_BAR_HIGH").equals("no")) {
                    pLog.error("Format of entry bar high filter rule wrong (" + getProperty("FILTER_ENTRY_BAR_HIGH") + "), must be either yes or no");
                    System.exit(1);        		
    			}        		
        }
        if (!containsKey("FILTER_ENTRY_BAR_LOW")) {
        	pLog.info("No entry bar low filtering set, using none");        	
        }
        else {
            	if (!getProperty("FILTER_ENTRY_BAR_LOW").equals("yes") 
            		&& !getProperty("FILTER_ENTRY_BAR_LOW").equals("no")) {
                    pLog.error("Format of entry bar low filter rule wrong (" + getProperty("FILTER_ENTRY_BAR_LOW") + "), must be either yes or no");
                    System.exit(1);        		
    			}        		
        }
        if (!containsKey("trailProfit")) {
        	pLog.info("No 4 hours timeframe filtering set, using none");        	
        }
        else {
            	if (!getProperty("trailProfit").equals("yes") 
            		&& !getProperty("trailProfit").equals("no")) {
                    pLog.error("Format of 4 hours timeframe filter wrong (" + getProperty("trailProfit") + "), must be either yes or no");
                    System.exit(1);        		
    			}        		
        }
        if (!containsKey("trailProfit")) {
        	pLog.info("No traling profit protection rule set, using none");        	
        }
        else {
            	if (!getProperty("trailProfit").equals("yes") 
            		&& !getProperty("trailProfit").equals("no")) {
                    pLog.error("Format of traling profit protection rule wrong (" + getProperty("trailProfit") + "), must be either yes or no");
                    System.exit(1);        		
    			}        		
        }
        if (!containsKey("USE_FILTERS")) {
        	pLog.info("No filter usage rule set, using none");        	
        }
        else {
            	if (!getProperty("USE_FILTERS").equals("yes") 
            		&& !getProperty("USE_FILTERS").equals("no")) {
                    pLog.error("Format of filter usage rule wrong (" + getProperty("USE_FILTERS") + "), must be either yes or no");
                    System.exit(1);        		
    			}        		
        }
        if (!containsKey("REPORT_FILTERS")) {
        	pLog.info("No filter reports rule set, using none");        	
        }
        else {
            	if (!getProperty("REPORT_FILTERS").equals("yes") 
            		&& !getProperty("REPORT_FILTERS").equals("no")) {
                    pLog.error("Format of filter reports rule wrong (" + getProperty("REPORT_FILTERS") + "), must be either yes or no");
                    System.exit(1);        		
    			}        		
        }
        if (!containsKey("DB_LOG")) {
        	pLog.info("No database logging set, using none");        	
        }
        else {
            	if (!getProperty("DB_LOG").equals("yes") 
            		&& !getProperty("DB_LOG").equals("no")) {
                    pLog.error("Format of database logging flag wrong (" + getProperty("DB_LOG") + "), must be either yes or no");
                    System.exit(1);        		
    			}        		
        }
		
	}

	public DateTime getTestIntervalStart() {
		return testIntervalStart;
	}

	public DateTime getTestIntervalEnd() {
		return testIntervalEnd;
	}

}
