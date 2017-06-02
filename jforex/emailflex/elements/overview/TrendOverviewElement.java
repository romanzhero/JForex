package jforex.emailflex.elements.overview;

import java.sql.Connection;
import java.util.List;
import java.util.Properties;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

import jforex.emailflex.IFlexEmailElement;
import jforex.techanalysis.Channel;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Volatility;
import jforex.utils.FXUtils;
import jforex.utils.log.FlexLogEntry;

public class TrendOverviewElement extends AbstractOverview {

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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IFlexEmailElement cloneIt(Properties conf) {
		return cloneIt();
	}

	@Override
	public IFlexEmailElement cloneIt() {
		return new TrendOverviewElement();
	}

	@Override
	public String printHTML(Instrument instrument, Period pPeriod, IBar bidBar,
			Connection logDB) {
		if (!pPeriod.equals(Period.THIRTY_MINS))
			return new String("Trend overview available only for 30min period");

		String trendID = dbGetString(instrument, pPeriod, bidBar, logDB,
				"TrendId", true);
		float trendStrength = dbGetFloat(instrument, pPeriod, bidBar, logDB,
				"MAsDistance", true);
		boolean twoRowsNeeded = false;

		String trendID4h = dbGetString(instrument, Period.FOUR_HOURS, bidBar,
				logDB, "TrendId", false);
		float trendStrength4h = dbGetFloat(instrument, Period.FOUR_HOURS,
				bidBar, logDB, "MAsDistance", false);

		String trendID1d = dbGetString(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar, logDB, "TrendId", false);
		float trendStrength1d = dbGetFloat(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar, logDB, "MAsDistance",
				false);

		if ((trendStrength < -0.7 || trendStrength4h < -0.7 || trendStrength1d < -0.7)
				&& !(trendStrength < -0.7 && trendStrength4h < -0.7 && trendStrength1d < -0.7))
			twoRowsNeeded = true;

		return "<tr style=\"background-color:#f4f4f4\"><td>Trend</td>"
				+ getHTMLtd(trendID1d, trendStrength1d, twoRowsNeeded
						&& trendStrength1d >= -0.7)
				+ getHTMLtd(trendID4h, trendStrength4h, twoRowsNeeded
						&& trendStrength4h >= -0.7)
				+ getHTMLtd(trendID, trendStrength, twoRowsNeeded
						&& trendStrength >= -0.7) + "</tr>";
	}

	private String getHTMLtd(String trendID, float trendStrength,
			boolean twoRows) {
		String text = null, color = null, height = twoRows ? new String(
				" height:40px; line-height:40px;") : new String();
		if (trendStrength < -0.7) {
			text = new String(FXUtils.beautify("FLAT") + " ("
					+ FXUtils.df1.format(trendStrength) + ")<br />("
					+ FXUtils.beautify(trendID) + ")");
			if (trendStrength < -1.0)
				color = getDarkBlue();
			else
				color = getLightBlue();
		} else {
			text = new String(FXUtils.beautify(trendID) + " ("
					+ FXUtils.df1.format(trendStrength) + ")");
			if (trendID.equals("UP_STRONG"))
				color = getDarkGreen();
			else if (trendID.equals("UP_MILD"))
				color = getGreen();
			else if (trendID.equals("FRESH_UP"))
				color = getLightGreen();
			else if (trendID.equals("FRESH_DOWN"))
				color = getLightRed();
			else if (trendID.equals("DOWN_MILD"))
				color = getRed();
			else
				color = getDarkRed();
		}

		return new String("<td><span style=\"background-color:" + color
				+ "; display:block; margin:0 3px;" + height + "\">" + text
				+ "</span></td>");
	}
}