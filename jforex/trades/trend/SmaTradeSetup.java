package jforex.trades.trend;

import java.util.Map;
import java.util.Set;

import jforex.techanalysis.Trend.FLAT_REGIME_CAUSE;
import jforex.techanalysis.source.FlexTASource;
import jforex.utils.log.FlexLogEntry;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

public class SmaTradeSetup extends AbstractSmaTradeSetup {

	public SmaTradeSetup(IIndicators indicators, IContext context, IHistory history, IEngine engine, Set<Instrument> subscribedInstruments, 
			boolean mktEntry, boolean onlyCross, boolean useEntryFilters,
			double pFlatPercThreshold, double pBBandsSqueezeThreshold, boolean trailsOnMA50) {
		super(engine, context, subscribedInstruments, mktEntry, onlyCross, useEntryFilters, pFlatPercThreshold, pBBandsSqueezeThreshold, trailsOnMA50);
	}

	@Override
	public String getName() {
		return new String("SMATrendIDFollow");
	}

	protected boolean buySignal(Instrument instrument, Period period, Filter filter, double[] ma20, double[] ma50, double[] ma100, double[] ma200, IBar bidBar, boolean strict, Map<String, FlexLogEntry> taValues) throws JFException {
		double currMA20 = ma20[1], currMA50 = ma50[1], currMA100 = ma100[1], prevMA20 = ma20[0], prevMA50 = ma50[0], prevMA100 = ma100[0];

		if (strict)
			return (currMA20 > currMA50 && currMA50 > currMA100) && !(prevMA20 > prevMA50 && prevMA50 > prevMA100);
		else {
			FLAT_REGIME_CAUSE isFlat = (FLAT_REGIME_CAUSE)taValues.get(FlexTASource.FLAT_REGIME).getValue();		
			if (isDownMomentum(instrument, period, filter, bidBar, taValues)
				|| !isFlat.equals(FLAT_REGIME_CAUSE.NONE))
				return false;
			
			return currMA20 > currMA50 && currMA50 > currMA100;
		}
	}

	protected boolean sellSignal(Instrument instrument, Period period, Filter filter, double[] ma20, double[] ma50, double[] ma100, double[] ma200, IBar bidBar, boolean strict, Map<String, FlexLogEntry> taValues) throws JFException {
		double currMA20 = ma20[1], currMA50 = ma50[1], currMA100 = ma100[1], prevMA20 = ma20[0], prevMA50 = ma50[0], prevMA100 = ma100[0];

		if (strict)
			return (currMA20 < currMA50 && currMA50 < currMA100) && !(prevMA20 < prevMA50 && prevMA50 < prevMA100);
		else {
			FLAT_REGIME_CAUSE isFlat = (FLAT_REGIME_CAUSE)taValues.get(FlexTASource.FLAT_REGIME).getValue();			
			if(isUpMomentum(instrument, period, filter, bidBar, taValues) || !isFlat.equals(FLAT_REGIME_CAUSE.NONE))				
				return false;
			
			return currMA20 < currMA50 && currMA50 < currMA100;
		}
	}

}
