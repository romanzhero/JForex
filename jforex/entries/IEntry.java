package jforex.entries;

import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

/**
 * @author Sascha_2
 * Interface to detect entry signals. Also detect their cancelations
 *
 */
public interface IEntry {

	public boolean isLong();
	
	public boolean signalFoundBool(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException;
	/**

	 * @return Double.MIN_VALUE if no signal found, initial protective stop value if found
	 * @throws JFException
	 */
	public double signalFound(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException;
	public boolean signalCanceled(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException;
	
	public void onStartExec(IContext context) throws JFException;

}
