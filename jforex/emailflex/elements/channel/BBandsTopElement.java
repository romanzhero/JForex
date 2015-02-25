package jforex.emailflex.elements.channel;

import java.sql.Connection;
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
import jforex.utils.FlexLogEntry;

public class BBandsTopElement extends BaseFlexElement implements IFlexEmailElement {

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
  return new BBandsTopElement();
 }

}