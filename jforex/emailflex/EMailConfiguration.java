package jforex.emailflex;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.util.ArrayList;
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

public class EMailConfiguration {
	protected List<IFlexEmailElement> mailElements = new ArrayList<IFlexEmailElement>();

	public void parseConfFile(String filePath, Properties conf) {
		// format of text config file is one element per line, # as comment
		try {
			BufferedReader in = new BufferedReader(new FileReader(filePath));
			String str;
			while ((str = in.readLine()) != null) {
				if (str.startsWith("#"))
					continue;

				IFlexEmailElement e = FlexEmailElementFactory.create(str, conf);
				if (e != null)
					mailElements.add(e);
			}
			in.close();
		} catch (IOException e) {
		}
	}

	public String getMailText(Instrument instrument, Period pPeriod,
			IBar bidBar, IHistory history, IIndicators indicators,
			Trend trendDetector, Channel channelPosition, Momentum momentum,
			Volatility vola, TradeTrigger tradeTrigger, Properties conf,
			List<FlexLogEntry> logLine, Connection logDB) throws JFException {
		String mailText = new String();
		for (IFlexEmailElement e : mailElements) {
			mailText += e.print(instrument, pPeriod, bidBar, history,
					indicators, trendDetector, channelPosition, momentum, vola,
					tradeTrigger, conf, logLine, logDB)
					+ "\n";
		}
		return mailText;
	}
}
