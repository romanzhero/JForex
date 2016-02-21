package singlejartest;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import com.dukascopy.api.Configurable;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

public class CalledStrategy implements IStrategy {

	@Configurable("Period")
	public Period basicTimeFrame = Period.THIRTY_MINS;
	private BufferedWriter logFile;

	@Override
	public void onStart(IContext context) throws JFException {
		try {
			this.logFile = new BufferedWriter(new FileWriter("logFile.txt"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException {
	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar,
			IBar bidBar) throws JFException {
		final SimpleDateFormat sdf;
		sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

		try {
			logFile.write("Entered onBar before filtering for timeframe "
					+ period.toString() + " and bidBar time of "
					+ sdf.format(bidBar.getTime()));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (period.equals(Period.FIVE_MINS)) {
			try {
				logFile.write("onBar - for 5mins; timeframe "
						+ period.toString() + " and bidBar time of "
						+ sdf.format(bidBar.getTime()));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return;
		}
		if (!(period.equals(basicTimeFrame) || period.equals(Period.FOUR_HOURS))) {
			try {
				logFile.write("onBar - irrelevant timeframe; timeframe "
						+ period.toString() + " and bidBar time of "
						+ sdf.format(bidBar.getTime()));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return;
		}
		try {
			logFile.write("Entered onBar AFTER filtering for timeframe "
					+ period.toString() + " and bidBar time of "
					+ sdf.format(bidBar.getTime()));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Override
	public void onMessage(IMessage message) throws JFException {
		// TODO Auto-generated method stub

	}

	@Override
	public void onAccount(IAccount account) throws JFException {
		// TODO Auto-generated method stub

	}

	@Override
	public void onStop() throws JFException {
		try {
			logFile.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
