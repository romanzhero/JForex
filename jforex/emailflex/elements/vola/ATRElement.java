package jforex.emailflex.elements.vola;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
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

public class ATRElement extends BaseFlexElement implements IFlexEmailElement {

	@Override
	public String print(Instrument instrument, Period pPeriod, IBar bidBar,
			IHistory history, IIndicators indicators, Trend trendDetector,
			Channel channelPosition, Momentum momentum, Volatility vola,
			TradeTrigger tradeTrigger, Properties conf,
			List<FlexLogEntry> logLine, Connection logDB) throws JFException {
		return new String();
	}

	@Override
	public String print(Instrument instrument, Period pPeriod, IBar bidBar,
			List<FlexLogEntry> logLine, Connection logDB) {
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
		}

		String value = new String("ATR: "
				+ mailStringsMap.get("ATR"
						+ FXUtils.timeFrameNamesMap.get(pPeriod.toString()))), valueHigh = new String(
				"bar high: "
						+ mailStringsMap.get("barHigh"
								+ FXUtils.timeFrameNamesMap.get(pPeriod
										.toString()))), valueLow = new String(
				"bar low: "
						+ mailStringsMap.get("barLow"
								+ FXUtils.timeFrameNamesMap.get(pPeriod
										.toString()))), color = getWhite();

		return new String("<tr><td><span style=\"background-color:" + color
				+ "; display:block; margin:0 1px; \">" + value + "; "
				+ valueHigh + " / " + valueLow + "</span></td></tr>");
	}

	@Override
	public IFlexEmailElement cloneIt(Properties conf) {
		return cloneIt();
	}

	@Override
	public IFlexEmailElement cloneIt() {
		return new ATRElement();
	}

}