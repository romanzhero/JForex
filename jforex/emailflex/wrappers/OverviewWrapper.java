package jforex.emailflex.wrappers;

import java.sql.Connection;
import java.util.List;
import java.util.Properties;

import jforex.emailflex.IFlexEmailElement;
import jforex.emailflex.IFlexEmailWrapper;
import jforex.techanalysis.Channel;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Volatility;
import jforex.utils.FlexLogEntry;

import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

public class OverviewWrapper extends AbstractWrapper {

	@Override
	public String printHTML(Instrument instrument, Period pPeriod, IBar bidBar, Connection logDB) {
		String res = new String();
		res += "<table width=\"780\" border=\"0\" cellspacing=\"2\" cellpadding=\"0\" style=\"text-align:center; vertical-align:middle; font-size:12px; line-height:20px; font-family:Arial, Helvetica, sans-serif; border:1px solid #f4f4f4;\">"
		  + "<tr><td width=\"132\" rowspan=\"2\"><strong>Indicators</strong></td>"
		  + "<td colspan=\"3\" style=\"background-color:#f4f4f4\"><strong>Timeframe</strong></td>"
		  + "</tr><tr><td width=\"220\"><strong>Daily</strong></td>"
		  + "<td width=\"220\"><strong>4 Hours</strong></td>"
		  + "<td width=\"220\"><strong>30 Mins</strong></td></tr>";
		
		for (IFlexEmailElement e : wrappedElements) {
			res += e.printHTML(instrument, pPeriod, bidBar, logDB);
		}
		
		res += "</table>";
		return res;
	}

	@Override
	public String print(Instrument instrument, Period pPeriod, IBar bidBar,
			IHistory history, IIndicators indicators, Trend trendDetector,
			Channel channelPosition, Momentum momentum, Volatility vola,
			TradeTrigger tradeTrigger, Properties conf,
			List<FlexLogEntry> logLine, Connection logDB) throws JFException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String print(Instrument instrument, Period pPeriod, IBar bidBar,
			List<FlexLogEntry> logLine, Connection logDB) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean needsWrapper() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public IFlexEmailWrapper getWrapper() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IFlexEmailElement cloneIt() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IFlexEmailElement cloneIt(Properties conf) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setParameters(List<String> parameters) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isSignal() {
		return false;
	}

	@Override
	public SignalResult detectSignal(Instrument instrument, Period pPeriod,
			IBar bidBar, IHistory history, IIndicators indicators,
			Trend trendDetector, Channel channelPosition, Momentum momentum,
			Volatility vola, TradeTrigger tradeTrigger, Properties conf,
			List<FlexLogEntry> logLine, Connection logDB) throws JFException {
		return null;
	}

	@Override
	public SignalResult detectSignal(Instrument instrument, Period pPeriod,
			IBar bidBar, List<FlexLogEntry> logLine, Connection logDB) {
		return null;
	}

}
