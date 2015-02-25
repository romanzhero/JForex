package jforex.emailflex.elements.overview;

import java.sql.Connection;
import java.util.List;
import java.util.Properties;import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

import jforex.emailflex.IFlexEmailElement;
import jforex.techanalysis.Channel;
import jforex.techanalysis.Momentum;import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Volatility;
import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;

public class OSOBOverviewElement extends AbstractOverview {

@Override
 public String print(Instrument instrument, Period pPeriod, IBar bidBar,
   IHistory history, IIndicators indicators, Trend trendDetector,Channel channelPosition, Momentum momentum, Volatility vola,
   TradeTrigger tradeTrigger, Properties conf, List<FlexLogEntry> logLine, Connection logDB) throws JFException {
return new String();
 }


 @Override
 public String print(Instrument instrument, Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine, Connection logDB) {
  // TODO Auto-generated method stub
  return null;
 }

 @Override
 public IFlexEmailElement cloneIt(Properties conf) {
  return cloneIt();
 }

 @Override
 public IFlexEmailElement cloneIt() {
  return new OSOBOverviewElement();
 }


@Override
public String printHTML(Instrument instrument, Period pPeriod, IBar bidBar, Connection logDB) {
	if (!pPeriod.equals(Period.THIRTY_MINS))
		return new String("OS/OB overview available only for 30min period");

	String res = new String("<tr><td><span style=\"display:block;\">OS / OB</span>" 
        + "<span style=\"display:block;\">RSI</span>"
        + "<span style=\"display:block;\">&nbsp;</span>"
        + "<span style=\"display:block;\">Stoch</span>"
        + "<span style=\"display:block;\">CCI</span>"
        + "<span style=\"display:block;\">&nbsp;</span></td>"),
	currRSIState= dbGetString(instrument, Period.DAILY_SUNDAY_IN_MONDAY, bidBar, logDB, "RSIState", false),
	currStochState= dbGetString(instrument, Period.DAILY_SUNDAY_IN_MONDAY, bidBar, logDB, "StochState", false),
	currCCIState = dbGetString(instrument, Period.DAILY_SUNDAY_IN_MONDAY, bidBar, logDB, "CCIState", false);
	
	float 
		currRSI = dbGetFloat(instrument, Period.DAILY_SUNDAY_IN_MONDAY, bidBar, logDB, "RSI", false),
		currCCI = dbGetFloat(instrument, Period.DAILY_SUNDAY_IN_MONDAY, bidBar, logDB, "CCI", false),
		currStochFast = dbGetFloat(instrument, Period.DAILY_SUNDAY_IN_MONDAY, bidBar, logDB, "StochFast", false),
		currStochSlow = dbGetFloat(instrument, Period.DAILY_SUNDAY_IN_MONDAY, bidBar, logDB, "StochSlow", false);
	res += printCell(currRSI, currRSIState, currStochState, currStochFast, currStochSlow, currCCI, currCCIState);

	currRSIState= dbGetString(instrument, Period.FOUR_HOURS, bidBar, logDB, "RSIState", false);
	currStochState= dbGetString(instrument, Period.FOUR_HOURS, bidBar, logDB, "StochState", false);
	currCCIState = dbGetString(instrument, Period.FOUR_HOURS, bidBar, logDB, "CCIState", false);
	
	currRSI = dbGetFloat(instrument, Period.FOUR_HOURS, bidBar, logDB, "RSI", false);
	currCCI = dbGetFloat(instrument, Period.FOUR_HOURS, bidBar, logDB, "CCI", false);
	currStochFast = dbGetFloat(instrument, Period.FOUR_HOURS, bidBar, logDB, "StochFast", false);
	currStochSlow = dbGetFloat(instrument, Period.FOUR_HOURS, bidBar, logDB, "StochSlow", false);
	res += printCell(currRSI, currRSIState, currStochState, currStochFast, currStochSlow, currCCI, currCCIState);

	currRSIState= dbGetString(instrument, Period.THIRTY_MINS, bidBar, logDB, "RSIState", true);
	currStochState= dbGetString(instrument, Period.THIRTY_MINS, bidBar, logDB, "StochState", true);
	currCCIState = dbGetString(instrument, Period.THIRTY_MINS, bidBar, logDB, "CCIState", true);
	
	currRSI = dbGetFloat(instrument, Period.THIRTY_MINS, bidBar, logDB, "RSI", true);
	currCCI = dbGetFloat(instrument, Period.THIRTY_MINS, bidBar, logDB, "CCI", true);
	currStochFast = dbGetFloat(instrument, Period.THIRTY_MINS, bidBar, logDB, "StochFast", true);
	currStochSlow = dbGetFloat(instrument, Period.THIRTY_MINS, bidBar, logDB, "StochSlow", true);
	res += printCell(currRSI, currRSIState, currStochState, currStochFast, currStochSlow, currCCI, currCCIState);

	return res + "</tr>";
}


private String printCell(float currRSI, String currRSIState, String currStochState, float currStochFast, float currStochSlow, float currCCI, String currCCIState) {
	String 
		color = null,
		text = null,
		res = new String("<td><span style=\"background-color:#FFF; display:block; margin:0 1px;\">&nbsp;</span>"); // needs empty cell first

	if (currRSI > 68.0)
		res += "<span style=\"background-color:#090; display:block; margin:0 1px;\">Overbought ! " + FXUtils.df1.format(currRSI) + "</span>";
	else if (currRSI < 32.0)
		res += "<span style=\"background-color:#C00; display:block; margin:0 1px;\">Oversold ! " + FXUtils.df1.format(currRSI) + "</span>";
	else 
		res += "<span style=\"background-color:#FFF; display:block; margin:0 1px;\">" + FXUtils.df1.format(currRSI) + "</span>";
	
	if (currRSIState == null || currRSIState.toUpperCase().equals("NONE"))
		color = new String("#FFF");
	else if (currRSIState.toUpperCase().equals("RAISING_OVERBOUGHT")
			 || currRSIState.toUpperCase().equals("TICKED_UP_OVERBOUGHT")) // dark green, most bullish
		color = new String("#090");
	else if (currRSIState.toUpperCase().equals("TICKED_DOWN_FROM_OVERBOUGHT")
			 || currRSIState.toUpperCase().equals("FALLING_OVERBOUGHT")) // light red, first signs of weakness
		color = new String("#F66");
//	else if (currRSIState.toUpperCase().equals("RAISING_IN_MIDDLE")
//			 || currRSIState.toUpperCase().equals("TICKED_UP_IN_MIDDLE")) // normal green
//		color = new String("#0C3");
//	else if (currRSIState.toUpperCase().equals("TICKED_DOWN_IN_MIDDLE")
//			 || currRSIState.toUpperCase().equals("FALLING_IN_MIDDLE")) // normal red
//		color = new String("#F00");
	else if (currRSIState.toUpperCase().equals("RAISING_OVERSOLD")
			 || currRSIState.toUpperCase().equals("TICKED_UP_FROM_OVERSOLD")) // light green, first signs of recovery
		color = new String("#0F6");
	else if (currRSIState.toUpperCase().equals("TICKED_DOWN_OVERSOLD")
			 || currRSIState.toUpperCase().equals("FALLING_OVERSOLD")) // dark red, most bearish
		color = new String("#C00");
	else 
		color = new String("#FFF");
	res += "<span style=\"background-color:" + color + "; display:block; margin:0 1px;\">" + FXUtils.beautify(currRSIState) + "</span>";
	
	if (currStochState.equals("OVERBOUGHT_BOTH")) {
		color = new String("#090");
		text = new String("Overbought ! (" + FXUtils.df1.format(currStochFast) + "/" + FXUtils.df1.format(currStochSlow) + ")");
	}
	else if (currStochState.equals("OVERSOLD_BOTH")) {
		color = new String("#C00");
		text = new String("Oversold ! (" + FXUtils.df1.format(currStochFast) + "/" + FXUtils.df1.format(currStochSlow) + ")");
	}
	else { 
		if (currStochState.toUpperCase().equals("OVERSOLD_FAST"))
			color = new String("#F66");
		else if (currStochState.toUpperCase().equals("OVERBOUGHT_FAST"))
			color = new String("#0F6");
		else 
			color = new String("#FFF");
		text = new String(FXUtils.df1.format(currStochFast) + "/" + FXUtils.df1.format(currStochSlow));
	}
	res += "<span style=\"background-color:" + color + "; display:block; margin:0 1px;\">" + FXUtils.beautify(text) + "</span>";
	
	if (currCCI > 190) {
		color = new String("#090");
		text = new String("Overbought ! " + FXUtils.df1.format(currCCI));
	}
	else if (currCCI < -190) {
		color = new String("#C00");
		text = new String("Oversold ! " + FXUtils.df1.format(currCCI));
	}
	else {
		color = new String("#FFF");
		text = new String(FXUtils.df1.format(currCCI));
	}
	res += "<span style=\"background-color:" + color + "; display:block; margin:0 1px;\">" + FXUtils.beautify(text) + "</span>";

	if (currCCIState == null || currCCIState.toUpperCase().equals("NONE"))
		color = new String("#FFF");
	else if (currCCIState.toUpperCase().equals("RAISING_OVERBOUGHT")
			 || currCCIState.toUpperCase().equals("TICKED_UP_OVERBOUGHT")) // dark green, most bullish
		color = new String("#090");
	else if (currCCIState.toUpperCase().equals("TICKED_DOWN_FROM_OVERBOUGHT")
			 || currCCIState.toUpperCase().equals("FALLING_OVERBOUGHT")) // light red, first signs of weakness
		color = new String("#F66");
//	else if (currCCIState.toUpperCase().equals("RAISING_IN_MIDDLE")
//			 || currCCIState.toUpperCase().equals("TICKED_UP_IN_MIDDLE")) // normal green
//		color = new String("#0C3");
//	else if (currCCIState.toUpperCase().equals("TICKED_DOWN_IN_MIDDLE")
//			 || currCCIState.toUpperCase().equals("FALLING_IN_MIDDLE")) // normal red
//		color = new String("#F00");
	else if (currCCIState.toUpperCase().equals("RAISING_OVERSOLD")
			 || currCCIState.toUpperCase().equals("TICKED_UP_FROM_OVERSOLD")) // light green, first signs of recovery
		color = new String("#0F6");
	else if (currCCIState.toUpperCase().equals("TICKED_DOWN_OVERSOLD")
			 || currCCIState.toUpperCase().equals("FALLING_OVERSOLD")) // dark red, most bearish
		color = new String("#C00");
	else 
		color = new String("#FFF");
	res += "<span style=\"background-color:" + color + "; display:block; margin:0 1px;\">" + FXUtils.beautify(currCCIState) + "</span>";	

	return res + "</td>";
}

}