package jforex.emailflex.elements.candles;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Properties;import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

import jforex.emailflex.BaseFlexElement;
import jforex.emailflex.IFlexEmailElement;
import jforex.techanalysis.Channel;
import jforex.techanalysis.Momentum;import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Volatility;
import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;

public class BarOHLCElement extends BaseFlexElement implements IFlexEmailElement {

@Override
 public String print(Instrument instrument, Period pPeriod, IBar bidBar,
   IHistory history, IIndicators indicators, Trend trendDetector,Channel channelPosition, Momentum momentum, Volatility vola,
   TradeTrigger tradeTrigger, Properties conf, List<FlexLogEntry> logLine, Connection logDB) throws JFException {
return new String();
 }


 @Override
 public String print(Instrument instrument, Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine, Connection logDB) {
	 ResultSet elementData = dbGetElementData(instrument, pPeriod, bidBar, logDB, "barOpen, barClose, barHigh, barLow", true);
	 if (elementData != null) {
		String res = new String();
		DecimalFormat df = instrument.getSecondaryCurrency().equals("JPY") ? FXUtils.df1 : FXUtils.df5;
		try {
			res += "current price: " + df.format(elementData.getDouble("barClose")) + ", ";
			res += "last bar low: " + df.format(elementData.getDouble("barLow")) + " / ";  
			res += "high: " + df.format(elementData.getDouble("barHigh")) + " / ";  		
			res += "middle: " + df.format(elementData.getDouble("barLow") + (elementData.getDouble("barHigh") - elementData.getDouble("barLow")) / 2);
		} catch (SQLException e) {
			   System.out.print("Log database problem in BarOHLCElement: " + e.getMessage());
	           System.exit(1);
		}  
		return res;
	 }	 
	 return null;
 }

 @Override
 public IFlexEmailElement cloneIt(Properties conf) {
  return cloneIt();
 }

 @Override
 public IFlexEmailElement cloneIt() {
  return new BarOHLCElement();
 }

}