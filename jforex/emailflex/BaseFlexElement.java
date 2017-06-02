package jforex.emailflex;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

import jforex.emailflex.IFlexEmailElement.SignalResult;
import jforex.techanalysis.Channel;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Volatility;
import jforex.utils.FXUtils;
import jforex.utils.MySQLDBUtils;
import jforex.utils.log.FlexLogEntry;

import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

public abstract class BaseFlexElement implements IFlexEmailElement {

	protected List<String> parameters;

	@Override
	public boolean isGeneric() {
		return true;
	}

	public void setParameters(List<String> newParameters) {
		parameters = newParameters;
	}

	protected ResultSet dbGetElementData(Instrument instrument, Period pPeriod,
			IBar bidBar, Connection logDB, String fieldList, boolean exact) {
		String statementStr = new String("SELECT " + fieldList + " FROM "
				+ FXUtils.getDbToUse() + ".tstatsentry "
				+ "WHERE Instrument = '" + instrument.toString() + "'"
				+ " AND TimeFrame = '" + pPeriod.toString() + "'"
				+ " AND BarTime " + (exact ? "=" : "<=") + " '"
				+ MySQLDBUtils.getFormatedTimeCET(bidBar) + "'"
				+ (exact ? "" : " order by BarTime desc limit 1"));
		try {
			Statement qry = logDB.createStatement();
			ResultSet result = qry.executeQuery(statementStr);
			if (result.next())
				return result;
			else
				return null;
		} catch (SQLException ex) {
			System.out.print("Log database problem: " + ex.getMessage());
			System.out.print(statementStr);
			System.exit(1);
		}
		return null;
	}

	protected float dbGetFloat(Instrument instrument, Period pPeriod,
			IBar bidBar, Connection logDB, String field, boolean exact) {
		ResultSet r = dbGetElementData(instrument, pPeriod, bidBar, logDB,
				field, exact);
		if (r == null)
			return Float.NEGATIVE_INFINITY;

		try {
			return r.getFloat(field);
		} catch (SQLException e) {
			System.out.print("Log database problem: " + e.getMessage());
			System.out.print(field);
			System.exit(1);
		}
		return Float.NEGATIVE_INFINITY;
	}

	protected String dbGetString(Instrument instrument, Period pPeriod,
			IBar bidBar, Connection logDB, String field, boolean exact) {
		ResultSet r = dbGetElementData(instrument, pPeriod, bidBar, logDB,
				field, exact);
		if (r == null)
			return null;

		try {
			return r.getString(field);
		} catch (SQLException e) {
			System.out.print("Log database problem: " + e.getMessage());
			System.out.print(field);
			System.exit(1);
		}
		return null;
	}

	@Override
	public String print(Instrument instrument, Period pPeriod, IBar bidBar,
			IHistory history, IIndicators indicators, Trend trendDetector,
			Channel channelPosition, Momentum momentum, Volatility vola,
			TradeTrigger tradeTrigger, Properties conf,
			List<FlexLogEntry> logLine, Connection logDB) throws JFException {
		return new String("method (Duka Java) not implemented");
	}

	@Override
	public String print(Instrument instrument, Period pPeriod, IBar bidBar,
			List<FlexLogEntry> logLine, Connection logDB) {
		return new String("method (Duka DB) not implemented");
	}

	@Override
	public String printHTML(Instrument instrument, Period pPeriod, IBar bidBar,
			Connection logDB) {
		return new String("method (HTML) not implemented");
	}

	public SignalResult detectSignal(Instrument instrument, Period pPeriod,
			IBar bidBar, IHistory history, IIndicators indicators,
			Trend trendDetector, Channel channelPosition, Momentum momentum,
			Volatility vola, TradeTrigger tradeTrigger, Properties conf,
			List<FlexLogEntry> logLine, Connection logDB) throws JFException {
		return null;
	}

	public SignalResult detectSignal(Instrument instrument, Period pPeriod,
			IBar bidBar, List<FlexLogEntry> logLine, Connection logDB) {
		return null;
	}

	@Override
	public abstract IFlexEmailElement cloneIt();

	@Override
	public abstract IFlexEmailElement cloneIt(Properties conf);

	@Override
	public boolean needsWrapper() {
		return false;
	}

	@Override
	public IFlexEmailWrapper getWrapper() {
		if (!needsWrapper())
			return null;
		else
			return null;
	}

	protected String getLightBlue() {
		return new String("#3CF");
	}

	protected String getDarkBlue() {
		return new String("#06C");
	}

	protected String getDarkGreen() {
		return new String("#090");
	}

	protected String getGreen() {
		return new String("#0C3");
	}

	protected String getLightGreen() {
		return new String("#0F6");
	}

	protected String getLightRed() {
		return new String("#F66");
	}

	protected String getRed() {
		return new String("#F00");
	}

	protected String getDarkRed() {
		return new String("#C00");
	}

	protected String getWhite() {
		return new String("#FFF");
	}

	@Override
	public boolean isSignal() {
		return false;
	}
}
