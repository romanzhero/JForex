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
import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;

public class IchiCloudCrossElement extends BaseFlexElement implements
		IFlexEmailElement {

	protected boolean signalFound = false;

	@Override
	public IFlexEmailElement cloneIt() {
		return new IchiCloudCrossElement();
	}

	@Override
	public IFlexEmailElement cloneIt(Properties conf) {
		return cloneIt();
	}

	@Override
	public String print(Instrument instrument, Period pPeriod, IBar bidBar,
			List<FlexLogEntry> logLine, Connection logDB) {
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailValuesMap.put(e.getLabel(), e);
		}

		FlexLogEntry logEntry = mailValuesMap.get("IchiCloudDist"
				+ FXUtils.timeFrameNamesMap.get(pPeriod.toString()));
		double valueDbl = logEntry.getDoubleValue();
		String color = getWhite(), value = "";
		if (valueDbl > 2.0) {
			color = getRed();
			value = "Extreme distance from Ichimoku cloud ("
					+ logEntry.getFormattedValue() + ") !";
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
