package jforex.emailflex.elements;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

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
import jforex.utils.log.FlexLogEntry;

public class NextSetupElement extends BaseFlexElement implements
		IFlexEmailElement {

	protected Map<String, String> nextSetups = new HashMap<String, String>();

	@Override
	public String print(Instrument instrument, Period pPeriod, IBar bidBar,
			IHistory history, IIndicators indicators, Trend trendDetector,
			Channel channelPosition, Momentum momentum, Volatility vola,
			TradeTrigger tradeTrigger, Properties conf,
			List<FlexLogEntry> logLine, Connection logDB) {
		if (nextSetups.containsKey(instrument.toString())) {
			return new String("Next recommended setup: "
					+ nextSetups.get(instrument.toString()) + "\n\n");
		}
		return "";
	}

	@Override
	public IFlexEmailElement cloneIt(Properties conf) {
		String nextSetupsStr = conf.getProperty("nextSetups");
		if (nextSetupsStr != null) {
			// format should be: nextSetups=<pair>:<setup
			// description>;<pair>:<setup description>;...;<pair>:<setup
			// description>
			StringTokenizer st = new StringTokenizer(nextSetupsStr, ";");
			while (st.hasMoreTokens()) {
				String nextSetup = st.nextToken();
				StringTokenizer internal = new StringTokenizer(nextSetup, ":");
				nextSetups.put(internal.nextToken(), internal.nextToken()
						.replace("<br>", "\n"));
			}
		}
		return new NextSetupElement();
	}

	@Override
	public IFlexEmailElement cloneIt() {
		return new NextSetupElement();
	}

	@Override
	public String print(Instrument instrument, Period pPeriod, IBar bidBar,
			List<FlexLogEntry> logLine, Connection logDB) {
		// TODO Auto-generated method stub
		return null;
	}

}
