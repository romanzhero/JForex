package jforex.utils.log;

import java.text.DecimalFormat;

import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.source.FlexTASource;
import jforex.techanalysis.source.TechnicalSituation;
import jforex.utils.FXUtils;

public class FlexLogEntry {
	protected String label;
	protected Object value;
	protected DecimalFormat df = null;
	protected double[] da1dim_values = null;
	protected double[][] da2dim_values = null;

	public FlexLogEntry(String label, Object value, DecimalFormat df) {
		super();
		this.label = label;
		this.value = value;
		this.df = df;
	}

	public FlexLogEntry(String label, String value) {
		super();
		this.label = label;
		this.value = value;
		this.df = null;
	}

	public FlexLogEntry(String label, Object value) {
		super();
		this.label = label;
		this.value = value;
		this.df = null;
	}

	public FlexLogEntry(String label, double[] value, DecimalFormat df) {
		this.label = label;
		this.value = null;
		this.df = df;
		da1dim_values = value;
	}

	public FlexLogEntry(String label, double[][] value, DecimalFormat df) {
		this.label = label;
		this.value = null;
		this.df = df;
		da2dim_values = value;
	}

	public String getLabel() {
		return label;
	}

	public Object getValue() {
		return value;
	}

	public double getDoubleValue() {
		if (isDouble()) {
			return ((Double) value).doubleValue();
		} else {
			return -1.0;
		}
	}

	public double getIntegerValue() {
		if (isInteger()) {
			return ((Integer) value).intValue();
		} else {
			return -1;
		}
	}
	
	public double getLongValue() {
		if (isLong()) {
			return ((Long)value).intValue();
		} else {
			return -1;
		}
	}
	
	
	public boolean getBooleanValue() {
		return ((Boolean) value).booleanValue();
	}

	public boolean isDouble() {
		return value != null ? value.getClass().equals(Double.class) : false;
	}

	public boolean isInteger() {
		return value != null ? value.getClass().equals(Integer.class) : false;
	}
	
	public boolean isLong() {
		return value != null ? value.getClass().equals(Long.class) : false;
	}
	
	public boolean isBoolean() {
		return value != null ? value.getClass().equals(Boolean.class) : false;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof String)
			return getLabel().equals(obj);
		else
			return getLabel().equals(((FlexLogEntry) obj).getLabel());
	}

	public String getNoTFLabel() {
		if (label.contains("30min"))
			return label.replace("30min", "");
		if (label.contains("4h"))
			return label.replace("4h", "");
		if (label.contains("1d"))
			return label.replace("1d", "");
		return label;
	}

	public String getSQLValue() {
		if (isDouble() && df != null)
			return getFormattedValue();
		else
			return "'" + value.toString() + "'";
	}

	public DecimalFormat getDecimalFormat() {
		return df;
	}

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
			|| getLabel().equals(FlexTASource.STOCH)
			|| getLabel().equals(FlexTASource.BBANDS)) {
			return da2dim_values;
		} else
			return null;
		
	}

	public double[] getDa1dim_values() {
		return da1dim_values;
	}

	public Trend.TREND_STATE getTrendStateValue() {
		if (getLabel().equals(FlexTASource.TREND_ID))
			return (Trend.TREND_STATE)value;
		else
			return null;
	}

	public TechnicalSituation getTehnicalSituationValue() {
		if (getLabel().equals(FlexTASource.TA_SITUATION))
			return (TechnicalSituation)value;
		else
			return null;
	}


	public String getFormattedValue() {
		if (getLabel().equals(FlexTASource.BULLISH_CANDLES)
				|| getLabel().equals(FlexTASource.BEARISH_CANDLES)) {
				TradeTrigger.TriggerDesc candles = (TradeTrigger.TriggerDesc)value;
				String res = new String();
				if (candles != null) {
					res += candles.type.toString();
					res += ";" + FXUtils.df1.format(candles.sizePercentile);
					res += ";" + FXUtils.df1.format(candles.reversalSize);
					res += ";" + FXUtils.df1.format(candles.channelPosition);
					res += ";" + FXUtils.df1.format(candles.keltnerChannelPosition);
					res += ";" + (candles.combinedRealBodyDirection ? "bullish" : "bearish");
					res += ";" + FXUtils.df1.format(candles.combinedUpperHandlePerc);
					res += ";" + FXUtils.df1.format(candles.combinedRealBodyPerc);
					res += ";" + FXUtils.df1.format(candles.combinedLowerHandlePerc);
				} else {
					//res += ";";
					res += ";";
					res += ";";
					res += ";";
					res += ";";
					res += ";";
					res += ";";
					res += ";";
					res += ";";					
				}
				return res;
			} else if (getLabel().equals(FlexTASource.MAs)) {
				String res = new String();
				for (int i = 0; i < 2; i++)
					for (int j = 0; j < 4; j++)
							if (i == 0 && j == 0)
								res += df.format(da2dim_values[i][j]);
							else
								res += ";" + df.format(da2dim_values[i][j]);
				return res;
				
			} else if (getLabel().equals(FlexTASource.SMI)) {
				String res = new String();
				for (int i = 0; i < 2; i++)
					for (int j = 0; j < 3; j++)
						if (i == 0 && j == 0)
							res += df.format(da2dim_values[i][j]);
						else
							res += ";" + df.format(da2dim_values[i][j]);
				res += ";" + (da2dim_values[0][2] > da2dim_values[1][2] ? "fast" : "slow");
				res += ";" + (da2dim_values[0][2] > da2dim_values[1][2] ? df.format(da2dim_values[0][2] - da2dim_values[1][2]) : df.format(da2dim_values[1][2] - da2dim_values[0][2]));
				return res;			
			} else if (getLabel().equals(FlexTASource.STOCH)) {
				String res = new String();
				res += df.format(da2dim_values[0][0]);
				res += ";" + df.format(da2dim_values[1][0]);
				res += ";" + df.format(da2dim_values[0][1]);
				res += ";" + df.format(da2dim_values[1][1]);
				res += ";" + (da2dim_values[0][1] > da2dim_values[1][1] ? "fast" : "slow");
				return res;						
			}  else if (getLabel().equals(FlexTASource.BBANDS)) {
				String res = new String();
				res += df.format(da2dim_values[0][0]);
				res += ";" + df.format(da2dim_values[1][0]);
				res += ";" + df.format(da2dim_values[2][0]);
				return res;						
			} else if (getLabel().equals(FlexTASource.ICHI)) {
				Trend.IchiDesc ichi = (Trend.IchiDesc)getValue();
				String res = new String();
				res += FXUtils.df5.format(ichi.fastLine); 
				res += ";" + FXUtils.df5.format(ichi.slowLine); 
				res += ";" + FXUtils.df5.format(ichi.cloudTop); 
				res += ";" + FXUtils.df5.format(ichi.cloudBottom);
				res += ";" + (ichi.isBullishCloudCross ? "bullishCloudCross" : "no"); 
				res += ";" + (ichi.isBearishCloudCross ? "bearishCloudCross" : "no"); 
				res += ";" + FXUtils.df1.format(ichi.widthToATR);
				return res;
			} else if (getLabel().equals(FlexTASource.TA_SITUATION)) {
				TechnicalSituation taSituation = getTehnicalSituationValue();
				String res = new String();
				res += taSituation.toString();
				res += ";" + taSituation.smiState.toString();
				res += ";" + taSituation.slowSMIState.toString();
				res += ";" + taSituation.fastSMIState.toString();
				res += ";" + taSituation.stochState.toString();
				return res;				
			} else {
				String res = new String();
				if (isDouble() && df != null) {
					res = df.format(((Double) value).doubleValue());
				} else
					res = value.toString();
				return res;
			}
	}

	public String getHeaderLabel() {
		if (getLabel().equals(FlexTASource.BULLISH_CANDLES)
			|| getLabel().equals(FlexTASource.BEARISH_CANDLES)) {
			String res = new String();
			if (getLabel().equals(FlexTASource.BULLISH_CANDLES))
				res += "bullishType";
			else
				res += "bearishType";
			res += ";sizePerc";
			res += ";reversalSize";
			res += ";channelPosition";
			res += ";keltnerChannelPosition";
			res += ";bodyDirection";
			res += ";upperHandlePerc";
			res += ";bodyPerc";
			res += ";lowerHandlePerc";
			return res;
		} else if (getLabel().equals(FlexTASource.MAs)) {
			String res = new String();
			res += "ma20Prev";
			res += ";ma50Prev";
			res += ";ma100Prev";
			res += ";ma200Prev";
			res += ";ma20";
			res += ";ma50";
			res += ";ma100";
			res += ";ma200";
			return res;
			
		} else if (getLabel().equals(FlexTASource.SMI)) {
			String res = new String();
			res += "fastSMIPrev2";
			res += ";fastSMIPrev";
			res += ";fastSMI";
			res += ";slowSMIPrev2";
			res += ";slowSMIPrev";
			res += ";slowSMI";
			res += ";higherSMI";
			res += ";SMIsDifference";
			return res;			
		} else if (getLabel().equals(FlexTASource.STOCH)) {
			String res = new String();
			/*
		double[][] lastTwoStochs = getStochs(instrument, pPeriod, side, time, 2);
		double 
			fastStochPrev = lastTwoStochs[0][0], 
			slowStochPrev = lastTwoStochs[1][0];			 
			fastStochLast = lastTwoStochs[0][1], 
			slowStochLast = lastTwoStochs[1][1], 
			 */
			res += "fastStochPrev";
			res += ";slowStochPrev";
			res += ";fastStoch";
			res += ";slowStoch";
			res += ";higherStoch";
			return res;						
		} else if (getLabel().equals(FlexTASource.ICHI)) {
			String res = new String();
			res += "fastLine"; 
			res += ";slowLine"; 
			res += ";cloudTop"; 
			res += ";cloudBottom"; 
			res += ";bullishBreakout"; 
			res += ";bearishBreakout"; 
			res += ";widthToATR";			
			return res;
		} else if (getLabel().equals(FlexTASource.BBANDS)) {
			String res = new String();
			res += "bBandsTop";
			res += ";bBandsMiddle";
			res += ";bBandsBottom";
			return res;						
		} else if (getLabel().equals(FlexTASource.TA_SITUATION)) {
			String res = new String();
			res += "TA situation";
			res += ";" + "reason";
			res += ";" + "Summary";
			res += ";" + "SMI state";
			res += ";" + "Slow SMI";
			res += ";" + "Fast SMI";
			res += ";" + "Stoch";
			return res;
		} else
			return getLabel();
	}
}
