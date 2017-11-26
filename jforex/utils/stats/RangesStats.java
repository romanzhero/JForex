package jforex.utils.stats;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

import jforex.utils.FXUtils;

public class RangesStats {

	public class InstrumentRangeStats {
		public double avgRange = 0.0, medianRange = 0.0, rangeStDev = 0.0, maxRange = 0.0, minRange = 0.0;
	}

	protected Set<Instrument> instruments = null;
	protected IHistory history = null;

	public RangesStats(Set<Instrument> subscribedInstruments, IHistory history) {
		this.instruments = subscribedInstruments;
		this.history = history;
	}

	public Map<Instrument, InstrumentRangeStats> init(IBar askBar, IBar bidBar) throws JFException {
		Map<Instrument, InstrumentRangeStats> result = new TreeMap<Instrument, RangesStats.InstrumentRangeStats>();
		for (Instrument currI : instruments) {
			InstrumentRangeStats currStats = calcInstrumentStats(currI, askBar, bidBar);
			result.put(currI, currStats);
		}
		return result;
	}

	private InstrumentRangeStats calcInstrumentStats(Instrument instrument, IBar askBar, IBar bidBar) throws JFException {
		long currDayTime = history.getBarStart(Period.DAILY, bidBar.getTime());
		// three months
		List<IBar> historyBars = history.getBars(instrument, Period.DAILY, OfferSide.BID, Filter.ALL_FLATS,	3 * 30, currDayTime, 0);
		double[] ranges = new double[historyBars.size()];
		int i = 0;
		double maxRange = 0.0, minRange = Double.MAX_VALUE;
		for (IBar currBar : historyBars) {
			double range = (currBar.getHigh() - currBar.getLow()) / currBar.getLow() * 100.0;
			ranges[i++] = range;
			if (range > maxRange) {
				maxRange = range;
			}
			if (range < minRange) {
				minRange = range;
			}
		}
		double[] stats = FXUtils.sdFast(ranges);
		double median = FXUtils.median(ranges);
		InstrumentRangeStats result = new InstrumentRangeStats();
		result.avgRange = stats[0];
		result.maxRange = maxRange;
		result.minRange = minRange;
		result.rangeStDev = stats[1];
		result.medianRange = median;
		return result;
	}

}
