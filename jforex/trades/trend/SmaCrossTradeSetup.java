package jforex.trades.trend;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

import jforex.events.TAEventDesc;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Trend.FLAT_REGIME_CAUSE;
import jforex.techanalysis.Trend.TREND_STATE;
import jforex.techanalysis.source.FlexTASource;
import jforex.utils.log.FlexLogEntry;

public class SmaCrossTradeSetup extends AbstractSmaTradeSetup {
	protected boolean 
		longCrossHappened = false,
		shortCrossHappened = false;

	public SmaCrossTradeSetup(IEngine engine, IContext context, Set<Instrument> subscribedInstruments, boolean mktEntry,
			boolean onlyCross, boolean useEntryFilters, 
			double pFlatPercThreshold, double pBBandsSqueezeThreshold, boolean trailsOnMA50) {
		super(engine, context, subscribedInstruments, mktEntry, onlyCross, useEntryFilters, pFlatPercThreshold, pBBandsSqueezeThreshold,	trailsOnMA50);
	}

	@Override
	protected boolean sellSignal(Instrument instrument, Period period, Filter filter, double[] ma20, double[] ma50,	double[] ma100, double[] ma200, IBar bidBar, boolean strict, Map<String, FlexLogEntry> taValues)	throws JFException {
		double currMA20 = ma20[1], currMA50 = ma50[1], currMA100 = ma100[1], prevMA20 = ma20[0], prevMA50 = ma50[0], prevMA100 = ma100[0];

		if (!shortCrossHappened)
			shortCrossHappened = (currMA20 < currMA50 && currMA50 < currMA100) && !(prevMA20 < prevMA50 && prevMA50 < prevMA100);
		if (!shortCrossHappened)
			return false;
		
		FLAT_REGIME_CAUSE isFlat = (FLAT_REGIME_CAUSE)taValues.get(FlexTASource.FLAT_REGIME).getValue();			
		double bBandsSqueeze = taValues.get(FlexTASource.BBANDS_SQUEEZE_PERC).getDoubleValue();

		if(isUpMomentum(instrument, period, filter, bidBar, taValues) 
			|| (isFlat.equals(FLAT_REGIME_CAUSE.MAs_CLOSE) && bBandsSqueeze <= bBandsSqueezeThreshold))				
			return false;
			
		return currMA20 < currMA50 && currMA50 < currMA100;
	}

	@Override
	protected boolean buySignal(Instrument instrument, Period period, Filter filter, double[] ma20, double[] ma50,	double[] ma100, double[] ma200, IBar bidBar, boolean strict, Map<String, FlexLogEntry> taValues)	throws JFException {
		double currMA20 = ma20[1], currMA50 = ma50[1], currMA100 = ma100[1], prevMA20 = ma20[0], prevMA50 = ma50[0], prevMA100 = ma100[0];

		if (!longCrossHappened)
			longCrossHappened = (currMA20 > currMA50 && currMA50 > currMA100) && !(prevMA20 > prevMA50 && prevMA50 > prevMA100);
		if (!longCrossHappened)
			return false; 
		
		FLAT_REGIME_CAUSE isFlat = (FLAT_REGIME_CAUSE)taValues.get(FlexTASource.FLAT_REGIME).getValue();		
		double bBandsSqueeze = taValues.get(FlexTASource.BBANDS_SQUEEZE_PERC).getDoubleValue();
		
		if (isDownMomentum(instrument, period, filter, bidBar, taValues)
			|| (isFlat.equals(FLAT_REGIME_CAUSE.MAs_CLOSE) && bBandsSqueeze <= bBandsSqueezeThreshold))
			return false;
		
		return currMA20 > currMA50 && currMA50 > currMA100;
	}

	@Override
	public String getName() {
		return new String("SmaCrossTradeSetup");
	}

	@Override
	public void inTradeProcessing(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter, IOrder order, Map<String, FlexLogEntry> taValues, List<TAEventDesc> marketEvents) throws JFException {
		Trend.TREND_STATE trendID = taValues.get(FlexTASource.TREND_ID).getTrendStateValue();
		if (longCrossHappened && !trendID.equals(TREND_STATE.UP_STRONG))
			longCrossHappened = false;
		if (shortCrossHappened && !trendID.equals(TREND_STATE.DOWN_STRONG))
			shortCrossHappened = false;
		super.inTradeProcessing(instrument, period, askBar, bidBar, filter, order, taValues, marketEvents);
	}

	@Override
	public void afterTradeReset(Instrument instrument) {
		super.afterTradeReset(instrument);
		if (longCrossHappened)
			longCrossHappened = false;
		if (shortCrossHappened)
			shortCrossHappened = false;
	}

}
