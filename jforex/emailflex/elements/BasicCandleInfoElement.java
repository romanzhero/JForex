package jforex.emailflex.elements;

import java.sql.Connection;
import java.util.List;
import java.util.Properties;

import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
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

public class BasicCandleInfoElement extends BaseFlexElement implements IFlexEmailElement {

	@Override
	public String print(Instrument instrument, Period pPeriod, IBar bidBar,
			IHistory history, IIndicators indicators, Trend trendDetector,
			Channel channelPosition, Momentum momentum, Volatility vola,
			TradeTrigger tradeTrigger, Properties conf, List<FlexLogEntry> logLine, Connection logDB) throws JFException {
		IBar prevBar30min = history.getBar(instrument, pPeriod, OfferSide.BID, 2);
		return new String("Last bar candle stats: upper handle = "  
				+ FXUtils.df1.format(tradeTrigger.barsUpperHandlePerc(bidBar)) + "% / " + 
				(bidBar.getClose() > bidBar.getOpen() ? "BULLISH" : "BEARISH") + " body = "  
				+ FXUtils.df1.format(tradeTrigger.barsBodyPerc(bidBar)) + "% / lower handle = "			
				+ FXUtils.df1.format(tradeTrigger.barsLowerHandlePerc(bidBar)) + "%\n"
				+ "Previous bar candle stats: upper handle = " 
				+ FXUtils.df1.format(tradeTrigger.barsUpperHandlePerc(prevBar30min)) + "% / " + 
				(prevBar30min.getClose() > prevBar30min.getOpen() ? "BULLISH" : "BEARISH") + " body = "  
				+ FXUtils.df1.format(tradeTrigger.barsBodyPerc(prevBar30min)) + "% / lower handle = "			
				+ FXUtils.df1.format(tradeTrigger.barsLowerHandlePerc(prevBar30min)) + "%\n");
	}

	@Override
	public IFlexEmailElement cloneIt(Properties conf) {
		return new BasicCandleInfoElement();
	}

	@Override
	public IFlexEmailElement cloneIt() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String print(Instrument instrument, Period pPeriod, IBar bidBar,	List<FlexLogEntry> logLine, Connection logDB) {
		// TODO Auto-generated method stub
		return null;
	}

}
