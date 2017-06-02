package jforex.emailflex.elements.candles;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
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
import jforex.utils.log.FlexLogEntry;

public class BodyDirectionElement extends BaseFlexElement implements
		IFlexEmailElement {

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
		ResultSet elementData = dbGetElementData(
				instrument,
				pPeriod,
				bidBar,
				logDB,
				"upperHandlePerc, barBodyPerc, lowerHandlePerc, barOpen, barClose",
				true);
		if (elementData != null) {
			try {
				return new String(
						"Last bar candle stats: upper handle = "
								+ FXUtils.df1.format(elementData
										.getDouble("upperHandlePerc"))
								+ "% / "
								+ (elementData.getDouble("barClose") > elementData
										.getDouble("barOpen") ? "BULLISH"
										: "BEARISH")
								+ " body = "
								+ FXUtils.df1.format(elementData
										.getDouble("barBodyPerc"))
								+ "% / lower handle = "
								+ FXUtils.df1.format(elementData
										.getDouble("lowerHandlePerc")) + "%");
			} catch (SQLException e) {
				System.out
						.print("Log database problem in BodyDirectionElement: "
								+ e.getMessage());
				System.exit(1);
			}
		}
		return null;
	}

	@Override
	public IFlexEmailElement cloneIt(Properties conf) {
		return cloneIt();
	}

	@Override
	public IFlexEmailElement cloneIt() {
		return new BodyDirectionElement();
	}

}