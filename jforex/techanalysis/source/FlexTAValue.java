package jforex.techanalysis.source;

import java.text.DecimalFormat;

import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.utils.FXUtils;
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

	@Override
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
				return res;			
			} else if (getLabel().equals(FlexTASource.STOCH)) {
				String res = new String();
				res += df.format(da2dim_values[0][0]);
				res += ";" + df.format(da2dim_values[1][0]);
				res += ";" + df.format(da2dim_values[0][1]);
				res += ";" + df.format(da2dim_values[1][1]);
				res += ";" + (da2dim_values[0][1] > da2dim_values[1][1] ? "fast" : "slow");
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
			} else
				return super.getFormattedValue();
	}

	@Override
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
		}
		else
			return super.getHeaderLabel();
	}
}
	
	

