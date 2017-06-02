package jforex.utils.stats;

import java.util.Map;
import java.util.TreeMap;

import org.joda.time.DateTime;

import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;

import jforex.utils.stats.RangesStats.InstrumentRangeStats;

public class DailyPnL {
	
	public class InstrumentDailyPnL {
		public double PnLPerc = 0.0;
		public InstrumentRangeStats rangeStats = null;
		// enable backtesting and reset of PnL for the new day
		public long dayStart;
		
		public InstrumentDailyPnL(InstrumentRangeStats rangeStats, long pDayStart) {
			super();
			this.rangeStats = rangeStats;
			this.dayStart = pDayStart;
		}
	}
	
	protected Map<Instrument, InstrumentDailyPnL> dailyPnLs = new TreeMap<Instrument, InstrumentDailyPnL>();

	public DailyPnL(Map<Instrument, InstrumentRangeStats> rangeStats, long pDayStart) {
		for (Instrument currI : rangeStats.keySet()) {
			dailyPnLs.put(currI, new InstrumentDailyPnL(rangeStats.get(currI), pDayStart));
		}
	}
	
	public InstrumentDailyPnL getInstrumentData(Instrument i) {
		return dailyPnLs.get(i);
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
	
	public void resetInstrumentDailyPnL(Instrument i, long currTime) {
		InstrumentDailyPnL dailyPnL = dailyPnLs.get(i);
		dailyPnL.PnLPerc = 0.0;
		dailyPnL.dayStart = currTime;
	}

}
