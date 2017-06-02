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

public class VolatilityOverviewElement extends AbstractOverview {

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
		return new VolatilityOverviewElement();
	}

	@Override
	public String printHTML(Instrument instrument, Period pPeriod, IBar bidBar,
			Connection logDB) {
		if (!pPeriod.equals(Period.THIRTY_MINS))
			return new String("Trend overview available only for 30min period");
		String res = new String(
				"<tr><td><span style=\"display:block;\">Volatility</span><span style=\"display:block;\">ATR</span></td>");
		float currVola = dbGetFloat(instrument, Period.DAILY_SUNDAY_IN_MONDAY,
				bidBar, logDB, "volatility", false), currATR = dbGetFloat(
				instrument, Period.DAILY_SUNDAY_IN_MONDAY, bidBar, logDB,
				"ATR", false);
		res += printCell(currVola, currATR);

		currVola = dbGetFloat(instrument, Period.FOUR_HOURS, bidBar, logDB,
				"volatility", false);
		currATR = dbGetFloat(instrument, Period.FOUR_HOURS, bidBar, logDB,
				"ATR", false);
		res += printCell(currVola, currATR);

		currVola = dbGetFloat(instrument, Period.THIRTY_MINS, bidBar, logDB,
				"volatility", true);
		currATR = dbGetFloat(instrument, Period.THIRTY_MINS, bidBar, logDB,
				"ATR", true);
		res += printCell(currVola, currATR);

		return res + "</tr>";
	}

	private String printCell(float currVola, float currATR) {
		String res = new String("<td>");
		if (currVola > 190.0)
			res += "<span style=\"background-color:" + getDarkRed()
					+ "; display:block; margin:0 1px;\">Very high ! ("
					+ FXUtils.df1.format(currVola) + "%)</span>";
		if (currVola > 150.0 && currVola <= 190.0)
			res += "<span style=\"background-color:" + getRed()
					+ "; display:block; margin:0 1px;\">High ! ("
					+ FXUtils.df1.format(currVola) + "%)</span>";
		else if (currVola < 70.0 && currVola >= 50)
			res += "<span style=\"background-color:" + getLightBlue()
					+ "; display:block; margin:0 1px;\">Low ! ("
					+ FXUtils.df1.format(currVola) + "%)</span>";
		else if (currVola < 50.0)
			res += "<span style=\"background-color:" + getDarkBlue()
					+ "; display:block; margin:0 1px;\">Low ! ("
					+ FXUtils.df1.format(currVola) + "%)</span>";
		else
			res += "<span style=\"background-color:" + getWhite()
					+ "; display:block; margin:0 1px;\">"
					+ FXUtils.df1.format(currVola) + "%</span>";

		res += "<span style=\"background-color:" + getWhite()
				+ "; display:block; margin:0 1px;\">"
				+ FXUtils.df1.format(currATR) + " pips</span>";

		return res + "</td>";
	}

}