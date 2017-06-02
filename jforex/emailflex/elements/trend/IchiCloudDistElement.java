package jforex.emailflex.elements.trend;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.dukascopy.api.IBar;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.Period;

import jforex.emailflex.BaseFlexElement;
import jforex.emailflex.IFlexEmailElement;
import jforex.techanalysis.Trend;
import jforex.utils.FXUtils;
import jforex.utils.log.FlexLogEntry;

public class IchiCloudDistElement extends BaseFlexElement implements
		IFlexEmailElement {

	protected boolean signalFound = false;

	@Override
	public IFlexEmailElement cloneIt() {
		return new IchiCloudDistElement();
	}

	@Override
	public IFlexEmailElement cloneIt(Properties conf) {
		return cloneIt();
	}

	@Override
	public String print(Instrument instrument, Period pPeriod, IBar bidBar,
			List<FlexLogEntry> logLine, Connection logDB) {
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
		}

		String value = mailStringsMap.get("IchiCloudCross"
				+ FXUtils.timeFrameNamesMap.get(pPeriod.toString())), color = getWhite();
		if (value.equals(Trend.ICHI_CLOUD_CROSS.BULLISH.toString())) {
			color = getGreen();
			value = "Bullish breakout from Ichimoku cloud !";
			signalFound = true;
		} else if (value.equals(Trend.ICHI_CLOUD_CROSS.BEARISH.toString())) {
			color = getRed();
			value = "Bearish breakout from Ichimoku cloud !";
			signalFound = true;
		}

		return new String("<tr><td><span style=\"background-color:" + color
				+ "; display:block; margin:0 1px; color:#fff;\">" + value
				+ "</span></td></tr>");
	}

	@Override
	public boolean isSignal() {
		return signalFound;
	}

}
