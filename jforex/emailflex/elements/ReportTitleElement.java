package jforex.emailflex.elements;

import java.sql.Connection;
import java.util.List;
import java.util.Properties;

import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.Period;

import jforex.emailflex.BaseFlexElement;
import jforex.emailflex.IFlexEmailElement;
import jforex.techanalysis.Channel;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Volatility;
import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;

public class ReportTitleElement extends BaseFlexElement implements IFlexEmailElement {
	
	protected String id = "reportTitle";
	
	public ReportTitleElement() {	}

	@Override
	public IFlexEmailElement cloneIt(Properties conf) {
		return cloneIt();
	}

	@Override
	public void setParameters(List<String> parameters) {
		super.setParameters(parameters);		
	}

	@Override
	public String print(Instrument instrument, Period pPeriod, IBar bidBar,
			IHistory history, IIndicators indicators, 
			Trend trendDetector, Channel channelPosition, Momentum momentum, Volatility vola, TradeTrigger tradeTrigger,
			Properties conf, List<FlexLogEntry> logLine, Connection logDB) {
		return new String("Report for " 
			+ instrument.toString() + ", " 
			+ FXUtils.getFormatedTimeCET(bidBar.getTime()) + " CET (time frame: " + pPeriod.toString() 
			+ ")\n\n");
	}

	@Override
	public IFlexEmailElement cloneIt() {
		return new ReportTitleElement();
	}

	@Override
	public String print(Instrument instrument, Period pPeriod, IBar bidBar,	List<FlexLogEntry> logLine, Connection logDB) {
		// TODO Auto-generated method stub
		return null;
	}
}
