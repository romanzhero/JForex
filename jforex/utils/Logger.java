package jforex.utils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;


import com.dukascopy.api.IConsole;

public class Logger {
	IConsole console;
	BufferedWriter logFile;
	boolean isOpen = false;
	static String SEPARATOR = ";";
	
	public enum logTags 
	{
		ORDER, // log entries for orders
		STRATEGY, // log entries for strategies
		ENTRY_FOUND, 
		ENTRY_CANCELED,
		ENTRY_FILLED,
		EXIT_STOP_LOSS,
		STOP_UPDATED,
		TRAILING_CONDITION_FOUND,
		TRAILING_STOP_UPDATED,
		EXIT_TRAILING_STOP,
		ENTRY_STATS,
		EXIT_STATS, 
		PROFIT_REPORT
	}
	
	public Logger(IConsole pConsole, String logFileName) {
		super();
		this.console = pConsole;
		try {
		    this.logFile = new BufferedWriter(new FileWriter(logFileName));
		    isOpen = true;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public Logger(String logFileName) {
		super();
		try {
		    this.logFile = new BufferedWriter(new FileWriter(logFileName));
		    isOpen = true;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Logger(IConsole pConsole) {
		super();
		this.console = pConsole;
	}
	
	public void printAction(String type, 
					String orderID, 
					String timestamp, 
					String action, 
					double stop, 
					double stopDistanceToEntryPips,
					double PnLPips,
					double commisions)
	{
		String outStr = type + SEPARATOR + orderID + SEPARATOR + timestamp + SEPARATOR + action;
		DecimalFormat df5 = new DecimalFormat("#.#####"); // half pip
	    outStr = outStr + SEPARATOR + df5.format(stop);
		DecimalFormat df1 = new DecimalFormat("#.#"); 
	    outStr = outStr + SEPARATOR + df1.format(stopDistanceToEntryPips) + SEPARATOR + df1.format(PnLPips) + SEPARATOR + df1.format(commisions);
	    
	    print(outStr);
	}

	public void printStatsHeader(String type, // might be both ENTRY_STATS and EXIT_STATS
			String orderID, 
			String timestamp)
	{
		String outStr = type + SEPARATOR + orderID + SEPARATOR + timestamp + SEPARATOR 
			+ "MACD" + SEPARATOR + "MACD_Signal" + SEPARATOR + "MACD_H" + SEPARATOR
			+ "StochFast" + SEPARATOR + "StochSlow" + SEPARATOR + "StocsDiff" + SEPARATOR
			+ "ADX" + SEPARATOR + "DI_PLUS" + SEPARATOR + "DI_MINUS" + SEPARATOR
			+ "entryBarHighChannelPerc" + SEPARATOR + "stopBarLowChannelPerc" + SEPARATOR
			+ "MAsStDevPos";
		
		print(outStr);
	}

	public void printStats(String type, // might be both ENTRY_STATS and EXIT_STATS
			String orderID, 
			String timestamp,
			double macd,
			double macd_signal,
			double macd_h,
			double stochFast,
			double stochSlow,
			double adx,
			double di_plus,
			double di_minus,
			double entryBarHighChannelPerc,
			double stopBarLowChannelPerc,
			double MAStDevPos)
	{
		DecimalFormat dfMACD = new DecimalFormat("#.##########"); 
		DecimalFormat df1 = new DecimalFormat("#.#"); 

		String outStr = type + SEPARATOR + orderID + SEPARATOR + timestamp + SEPARATOR 
			+ dfMACD.format(macd) + SEPARATOR + dfMACD.format(macd_signal) + SEPARATOR + dfMACD.format(macd_h) + SEPARATOR
			+ df1.format(stochFast) + SEPARATOR + df1.format(stochSlow) + SEPARATOR + df1.format(stochFast - stochSlow) + SEPARATOR 
			+ df1.format(adx) + SEPARATOR + df1.format(di_plus) + SEPARATOR + df1.format(di_minus) + SEPARATOR
			+ df1.format(entryBarHighChannelPerc) + SEPARATOR + df1.format(stopBarLowChannelPerc) + SEPARATOR
			+ df1.format(MAStDevPos);
		
		print(outStr);
	}
	
	public void printLabelsFlex(List<FlexLogEntry> line)
	{
		String output = new String();
		int cnt = 0;
		for (FlexLogEntry e : line)
		{
			if (cnt++ > 0)
				output += SEPARATOR + e.getLabel();
			else
				output += e.getLabel();
		}
		print(output);
	}

	public void printValuesFlex(List<FlexLogEntry> line)
	{
		String output = new String();
		int cnt = 0;
		for (FlexLogEntry e : line)
		{
			if (cnt++ > 0)
				output += SEPARATOR + e.getFormattedValue();
			else
				output += e.getFormattedValue();
		}
		print(output);
	}
	
	
	public void print(String message) {
		if (console != null)
			console.getOut().println(message);
		if (logFile != null && isOpen)
			try {
				logFile.write(message + "\n");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}	

	public void print(String message, boolean flush) {
		if (console != null)
			console.getOut().println(message);
		if (logFile != null && isOpen)
			try {
				logFile.write(message + "\n");
				if (flush)
					logFile.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}	
	
	public void close() 
	{
		if (logFile != null)
			try {
				isOpen = false;
				logFile.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}		
	}
}
