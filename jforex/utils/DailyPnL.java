package jforex.utils;

import java.util.Map;
import java.util.TreeMap;

import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;

import jforex.utils.RangesStats.InstrumentRangeStats;

public class DailyPnL {
	
	public class InstrumentDailyPnL {
		public double PnLPerc = 0.0;
		public InstrumentRangeStats rangeStats = null;
		
		public InstrumentDailyPnL(InstrumentRangeStats rangeStats) {
			super();
			this.rangeStats = rangeStats;
		}
	}
	
	private Map<Instrument, InstrumentDailyPnL> dailyPnLs = new TreeMap<Instrument, InstrumentDailyPnL>();

	public DailyPnL(Map<Instrument, InstrumentRangeStats> rangeStats) {
		for (Instrument currI : rangeStats.keySet()) {
			dailyPnLs.put(currI, new InstrumentDailyPnL(rangeStats.get(currI)));
		}
	}
	
	public void updateInstrumentPnL(Instrument i, IOrder order) {
		if (order == null)
			return;
		InstrumentDailyPnL dailyPnL = dailyPnLs.get(i);
		if (dailyPnL == null)
			return;
		
		if (order.isLong()) {
			dailyPnL.PnLPerc += (order.getClosePrice() - order.getOpenPrice()) / order.getOpenPrice() * 100.0;
		} else {
			dailyPnL.PnLPerc += (order.getOpenPrice() - order.getClosePrice()) / order.getClosePrice() * 100.0;			
		}
		
	}
	
	public double ratioPnLAvgRange(Instrument i) {
		InstrumentDailyPnL dailyPnL = dailyPnLs.get(i);
		return dailyPnL.PnLPerc / dailyPnL.rangeStats.avgRange;
	}

}
