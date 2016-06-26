package jforex.explorers;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;

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

public class CFDRangeExplorer extends BasicStrategy implements IStrategy{
	public final Period selectedPeriod = Period.TEN_MINS;
	public class CFDRangeData {
		public String instrument;
		public double 
			currMax = -1.0,
			currMin = Double.MAX_VALUE,
			currRange = 0.0;
		public long
			lowTime = 0,
			highTime = 0;
		public CFDRangeData(String pInstrument) {
			instrument = pInstrument;
			currMax = -1.0;
			currMin = Double.MAX_VALUE;
		}		
		public void recalcRange() {
			if (currMax == -1.0 && currMin == Double.MAX_VALUE)
				return;
			currRange = (currMax - currMin) / currMin * 100;
		}
	}
	protected Map<Instrument, CFDRangeData> instData = new HashMap<Instrument, CFDRangeData>();

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
		for (Instrument i : pairsTimeFrames.keySet()) {
			instData.put(i, new CFDRangeData(i.name()));
		}
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException {
		
	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		if (!period.equals(selectedPeriod)
			|| !pairsTimeFrames.containsKey(instrument)
			|| skipPairs.contains(instrument)
			|| (bidBar.getOpen() == bidBar.getClose() && bidBar.getClose() == bidBar.getLow() && bidBar.getLow() == bidBar.getHigh()))
			return;
		
		CFDRangeData currInstData = instData.get(instrument);
		if (bidBar.getHigh() > currInstData.currMax) {
			currInstData.currMax = bidBar.getHigh();
			currInstData.highTime = bidBar.getTime();
		}
		if (bidBar.getLow() < currInstData.currMin) {
			currInstData.currMin = bidBar.getLow();
			currInstData.lowTime = bidBar.getTime();
		}
		currInstData.recalcRange();
	}

	@Override
	public void onMessage(IMessage message) throws JFException {
		
	}

	@Override
	public void onAccount(IAccount account) throws JFException {
		
	}

	@Override
	public void onStop() throws JFException {
		SortedMap<String, Instrument> rangesHitParade = new TreeMap<String, Instrument>();
		for (Instrument i : instData.keySet()) {
			rangesHitParade.put(FXUtils.df2.format(instData.get(i).currRange) + "_" + i.name(), i);
		}
		String keys[] = new String[rangesHitParade.keySet().size()]; 
		keys = rangesHitParade.keySet().toArray(keys);
		log.print("Instrument   ;Range;Low;Time of low;High;Time of high");
		for (int i = keys.length - 1; i >= 0; i--) {
			CFDRangeData currData = instData.get(rangesHitParade.get(keys[i]));
			log.print(rangesHitParade.get(keys[i]) 
						+ ";" + keys[i].substring(0, keys[i].indexOf("_")) + "%"
						+ ";" + currData.currMin
						+ ";" + FXUtils.getFormatedTimeGMT(currData.lowTime)
						+ ";" + currData.currMax
						+ ";" + FXUtils.getFormatedTimeGMT(currData.highTime));
		}
		super.onStopExec();
	}

}
