package jforex.explorers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

import jforex.BasicStrategy;
import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;

public class CFDRangeExplorer extends BasicStrategy implements IStrategy{
	public final Period selectedPeriod = Period.FIVE_MINS;
	public class CFDRangeData {
		public IBar 
			prevBar = null,
			earliestBar = null,
			latestBar = null;
		public double 
			currMax = -1.0,
			currMin = -1.0;
		public CFDRangeData() {
			prevBar = null;
			earliestBar = null;
			latestBar = null;
			currMax = -1.0;
			currMin = -1.0;			
		}		
	}
	protected Map<Instrument, CFDRangeData> instData = new HashMap<Instrument, CFDRangeData>();

	public CFDRangeExplorer() {
		super();
	}

	public CFDRangeExplorer(Properties props) {
		super(props);
	}

	@Override
	protected String getStrategyName() {
		return "CFDRangeExplorer";
	}

	@Override
	protected String getReportFileName() {
		return getStrategyName();
	}

	@Override
	public void onStart(IContext context) throws JFException {
		super.onStartExec(context);
		Set<Instrument> insts = context.getSubscribedInstruments();
		for (Instrument i : insts) {
			instData.put(i, new CFDRangeData());
		}
		log.print("ticker;time;firstBar;lastBar;low;high;rangePerc", true);
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException {
		
	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		if (!period.equals(selectedPeriod) || skipPairs.contains(instrument))
			return;
		
		CFDRangeData currInstData = instData.get(instrument);
		if (currInstData.prevBar == null && FXUtils.isFlat(bidBar)) {
			return;
		}

		if (currInstData.prevBar == null && !FXUtils.isFlat(bidBar)) {
			currInstData.prevBar = bidBar;
			currInstData.earliestBar = bidBar;
			currInstData.latestBar = bidBar;
			currInstData.currMax = bidBar.getHigh();
			currInstData.currMin = bidBar.getLow();
			return;
		}
		if (bidBar.getHigh() > currInstData.currMax)
			currInstData.currMax = bidBar.getHigh();
		if (bidBar.getLow() < currInstData.currMin)
			currInstData.currMin = bidBar.getLow();
		if (bidBar.getTime() > currInstData.latestBar.getTime())
			currInstData.latestBar = bidBar;
		if (FXUtils.isFlat(bidBar) && !FXUtils.isFlat(currInstData.prevBar)) {
			// this should be the last day bar. Print out the data
			FlexLogEntry 
				ticker = new FlexLogEntry("ticker", instrument.name()),
				time = new FlexLogEntry("time", FXUtils.getFormatedTimeGMT(bidBar.getTime())),
				earliest = new FlexLogEntry("firstBar", FXUtils.getFormatedTimeGMT(currInstData.earliestBar.getTime())),
				latest = new FlexLogEntry("lastBar", FXUtils.getFormatedTimeGMT(currInstData.latestBar.getTime())),
				low = new FlexLogEntry("low", currInstData.currMin, FXUtils.df2),
				high = new FlexLogEntry("high", currInstData.currMax, FXUtils.df2),
				rangePerc = new FlexLogEntry("rangePerc", (currInstData.currMax - currInstData.currMin) / currInstData.currMin * 100.0, FXUtils.df2);
			List<FlexLogEntry> line = new ArrayList<FlexLogEntry>();
			line.add(ticker);
			line.add(time);
			line.add(earliest);
			line.add(latest);
			line.add(low);
			line.add(high);
			line.add(rangePerc);
			log.printValuesFlex(line);
			instData.put(instrument, new CFDRangeData());
		} else
			currInstData.prevBar = bidBar;		
	}

	@Override
	public void onMessage(IMessage message) throws JFException {
		
	}

	@Override
	public void onAccount(IAccount account) throws JFException {
		
	}

	@Override
	public void onStop() throws JFException {
		super.onStopExec();
	}

}
