package jforex.techanalysis;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.IIndicators.MaType;

public class Volatility {
    private IIndicators indicators;

	public Volatility(IIndicators indicators) {
		super();
		this.indicators = indicators;
	}
	
	public double getATR(Instrument instrument, Period pPeriod, OfferSide side, long time, int lookBack) throws JFException
	{		
		return indicators.atr(instrument, pPeriod, side, lookBack, Filter.WEEKENDS, 1, time, 0)[0];
	}

	public double[] getATRTimeSeries(Instrument instrument, Period pPeriod, OfferSide side, long time, int lookBack, int timeSeriesLength) throws JFException
	{		
		return indicators.atr(instrument, pPeriod, side, lookBack, Filter.WEEKENDS, timeSeriesLength, time, 0);
	}
	
	/**
	 * @param lookBack
	 * @return Ratio (%) between widths of lookBack Bolinger and Keltner Bands. Values significantly below 100% (70% and less) indicate low volatility state and vice versa 
	 */
	public double getBBandsSqueeze(Instrument instrument, Period pPeriod, OfferSide side, long time, int lookBack) throws JFException
	{		
    	double[][] bBands = indicators.bbands(instrument, pPeriod, side, AppliedPrice.CLOSE, lookBack, 2.0, 2.0, MaType.SMA, 
    			Filter.WEEKENDS, 1, time, 0);
    	double 
    		twoStDev = bBands[0][0] - bBands[1][0],
    		twoATRs = 2 * getATR(instrument, pPeriod, side, time, lookBack); 
		return twoStDev / twoATRs * 100.0;
	}
	
}
