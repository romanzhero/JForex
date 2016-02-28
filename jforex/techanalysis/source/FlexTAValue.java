package jforex.techanalysis.source;

import java.text.DecimalFormat;

import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.utils.FlexLogEntry;

public class FlexTAValue extends FlexLogEntry {
	protected double[] da1dim_values = null;
	protected double[][] da2dim_values = null;

	public FlexTAValue(String label, Object value, DecimalFormat df) {
		super(label, value, df);
	}

	public FlexTAValue(String label, String value) {
		super(label, value);
	}
	
	public FlexTAValue(String label, Object value) {
		super(label, value);
	}

	public FlexTAValue(String label, double[] value, DecimalFormat df) {
		super(label, null, df);
		da1dim_values = value;
	}

	public FlexTAValue(String label, double[][] value, DecimalFormat df) {
		super(label, null, df);
		da2dim_values = value;
	}
	
	/*
	 * 		CANDLES = "Candles",
		MAs = "Moving averages",
		TREND_ID = "TrendID",
		MA200_HIGHEST = "MA200Highest",
		MA200_LOWEST = "MA200Lowest",
		SMI = "SMI",
		STOCH = "Stoch",
		RSI3 = "RSI3",
		CHANNEL_POS = "Channel position",
		BBANDS_SQUEEZE_PERC = "BBands squeeze percentile",
		MAs_DISTANCE_PERC = "MAs distance percentile";
	 */
	public TradeTrigger.TriggerDesc getCandleValue() {
		if (getLabel().equals(FlexTASource.BULLISH_CANDLES)
			|| getLabel().equals(FlexTASource.BEARISH_CANDLES)) {
			return (TradeTrigger.TriggerDesc)value;
		} else
			return null;
	}
	
	public double[][] getDa2DimValue() {
		if (getLabel().equals(FlexTASource.MAs)
			|| getLabel().equals(FlexTASource.SMI)
			|| getLabel().equals(FlexTASource.STOCH)) {
			return da2dim_values;
		} else
			return null;
		
	}
	
	public Trend.TREND_STATE getTrendStateValue() {
		if (getLabel().equals(FlexTASource.TREND_ID))
			return (Trend.TREND_STATE)value;
		else
			return null;
	}
}
