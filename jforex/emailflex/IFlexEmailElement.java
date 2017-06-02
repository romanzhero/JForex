package jforex.emailflex;

import java.sql.Connection;
import java.util.List;
import java.util.Properties;

import jforex.techanalysis.Channel;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Volatility;
import jforex.utils.log.FlexLogEntry;

import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

public interface IFlexEmailElement {

	public class SignalResult {
		public String insertSQL = null, mailBody = null;
	}

	/**
	 * @return true if element needs only generic TA data as delivered in
	 *         FlexLogEntry list
	 */
	public boolean isGeneric();

	public String print(Instrument instrument, Period pPeriod, IBar bidBar,
			IHistory history, IIndicators indicators, Trend trendDetector,
			Channel channelPosition, Momentum momentum, Volatility vola,
			TradeTrigger tradeTrigger, Properties conf,
			List<FlexLogEntry> logLine, Connection logDB) throws JFException;

	public String print(Instrument instrument, Period pPeriod, IBar bidBar,
			List<FlexLogEntry> logLine, Connection logDB);

	public String printHTML(Instrument instrument, Period pPeriod, IBar bidBar,
			Connection logDB);

	public SignalResult detectSignal(Instrument instrument, Period pPeriod,
			IBar bidBar, IHistory history, IIndicators indicators,
			Trend trendDetector, Channel channelPosition, Momentum momentum,
			Volatility vola, TradeTrigger tradeTrigger, Properties conf,
			List<FlexLogEntry> logLine, Connection logDB) throws JFException;

	public SignalResult detectSignal(Instrument instrument, Period pPeriod,
			IBar bidBar, List<FlexLogEntry> logLine, Connection logDB);

	public boolean needsWrapper();

	public IFlexEmailWrapper getWrapper();

	public IFlexEmailElement cloneIt();

	public IFlexEmailElement cloneIt(Properties conf);

	void setParameters(List<String> parameters);

	public boolean isSignal();
}
