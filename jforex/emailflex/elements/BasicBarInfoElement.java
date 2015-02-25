package jforex.emailflex.elements;

import java.sql.Connection;
import java.util.List;
import java.util.Properties;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.IIndicators.AppliedPrice;

import jforex.emailflex.BaseFlexElement;
import jforex.emailflex.IFlexEmailElement;
import jforex.techanalysis.Channel;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Volatility;
import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;

public class BasicBarInfoElement extends BaseFlexElement implements	IFlexEmailElement {

	@Override
	public String print(Instrument instrument, Period pPeriod, IBar bidBar,
			IHistory history, IIndicators indicators, Trend trendDetector,
			Channel channelPosition, Momentum momentum, Volatility vola,
			TradeTrigger tradeTrigger, Properties conf, List<FlexLogEntry> logLine, Connection logDB) throws JFException {
		// TODO Auto-generated method stub
		double roc1 = indicators.roc(instrument, pPeriod, OfferSide.BID, AppliedPrice.CLOSE, 1, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0];
		String mailBody = new String();
		mailBody += "30min price change: " + FXUtils.df1.format(roc1 * 100) + " pips";
		mailBody += " (current price: " + FXUtils.df5.format(bidBar.getClose()) + ", ";  
		mailBody += "last bar low: " + FXUtils.df5.format(bidBar.getLow()) + " / ";  
		mailBody += "high: " + FXUtils.df5.format(bidBar.getHigh()) + " / ";  		
		mailBody += "middle: " + FXUtils.df5.format(bidBar.getLow() + (bidBar.getHigh() - bidBar.getLow()) / 2) + ")\n";  	
		return mailBody;
	}

	@Override
	public IFlexEmailElement cloneIt(Properties conf) {
		return cloneIt();
	}

	@Override
	public IFlexEmailElement cloneIt() {
		return new BasicBarInfoElement();
	}

	@Override
	public String print(Instrument instrument, Period pPeriod, IBar bidBar,	List<FlexLogEntry> logLine, Connection logDB) {
		// TODO Auto-generated method stub
		return null;
	}

}
