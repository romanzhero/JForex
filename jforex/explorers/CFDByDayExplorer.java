package jforex.explorers;

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

import jforex.BasicStrategy;
import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;

public class CFDByDayExplorer extends BasicStrategy implements IStrategy{
	public static Period selectedPeriod = Period.DAILY;

	public CFDByDayExplorer() {
		super();
	}

	public CFDByDayExplorer(Properties props) {
		super(props);
	}

	@Override
	protected String getStrategyName() {
		return "CFDRangeExplorer";
	}

	@Override
	protected String getReportFileName() {
		return getStrategyName() + "_";
	}

	@Override
	public void onStart(IContext context) throws JFException {
		super.onStartExec(context);
		log.print("ticker;time;low;high;rangePerc", true);
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException {
		
	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		if (!period.equals(selectedPeriod) || skipPairs.contains(instrument))
			return;
		
		if (!FXUtils.isFlat(bidBar)) {
			// this should be the last day bar. Print out the data
			FlexLogEntry 
				ticker = new FlexLogEntry("ticker", instrument.name()),
				time = new FlexLogEntry("time", FXUtils.getFormatedTimeGMT(bidBar.getTime())),
				low = new FlexLogEntry("low", bidBar.getLow(), FXUtils.df2),
				high = new FlexLogEntry("high", bidBar.getHigh(), FXUtils.df2),
				rangePerc = new FlexLogEntry("rangePerc", (bidBar.getHigh() - bidBar.getLow()) / bidBar.getLow() * 100.0, FXUtils.df2);
			List<FlexLogEntry> line = new ArrayList<FlexLogEntry>();
			line.add(ticker);
			line.add(time);
			line.add(low);
			line.add(high);
			line.add(rangePerc);
			log.printValuesFlex(line);
		} 		
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
