package jforex.utils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeZone;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.util.DateUtils;

public class FXUtils {
	
	public static final int TWO_WEEKS_WORTH_OF_10min_BARS = 1200;
	public static final int TWO_WEEKS_WORTH_OF_15min_BARS = 960;
	public static final int TWO_WEEKS_WORTH_OF_20min_BARS = 720;
	public static final int MONTH_WORTH_OF_30min_BARS = 960;
	public static final int MONTH_WORTH_OF_1h_BARS = 2 * 960;
	public static final int QUARTER_WORTH_OF_4h_BARS = 380;  //TODO: too little compared to others ???
    public static final int YEAR_WORTH_OF_4H_BARS = 1560;
    public static final int YEAR_WORTH_OF_1d_BARS = 250;
    public static final int TWO_YEAR_WORTH_OF_1W_BARS = 100;
	
	public static final DecimalFormat df5 = new DecimalFormat("0.00000");
	public static final DecimalFormat df4 = new DecimalFormat("0.0000");
	public static final DecimalFormat df1 = new DecimalFormat("0.0");
	public static final DecimalFormat df2 = new DecimalFormat("0.00");
	public static final DecimalFormat if1 = new DecimalFormat("0");
	public static final DecimalFormat if2 = new DecimalFormat("00");
	public static final DecimalFormat if3 = new DecimalFormat("000");
	
	protected static String dbToUse;
    
	public enum EntryFilters {
		FILTER_MOMENTUM,
		FILTER_ENTRY_BAR_HIGH,
		FILTER_ENTRY_BAR_LOW,
		FILTER_4H
	}
	
	public enum PreviousTrade { 
		NONE, // for the first trade in backtest run
		LOSS,
		WIN
	};

    private static Currency baseCurrency;
    private static IHistory history;
    private static BigDecimal DukaLotSize = BigDecimal.valueOf(1000000);
    protected static Map<Currency, Instrument> pairs = new HashMap<Currency, Instrument>();

    static {
        pairs.put(Currency.getInstance("AUD"), Instrument.AUDUSD);
        pairs.put(Currency.getInstance("CAD"), Instrument.USDCAD);
        pairs.put(Currency.getInstance("CHF"), Instrument.USDCHF);
        pairs.put(Currency.getInstance("DKK"), Instrument.USDDKK);
        pairs.put(Currency.getInstance("EUR"), Instrument.EURUSD);
        pairs.put(Currency.getInstance("GBP"), Instrument.GBPUSD);
        pairs.put(Currency.getInstance("HKD"), Instrument.USDHKD);
        pairs.put(Currency.getInstance("JPY"), Instrument.USDJPY);
        pairs.put(Currency.getInstance("MXN"), Instrument.USDMXN);
        pairs.put(Currency.getInstance("NOK"), Instrument.USDNOK);
        pairs.put(Currency.getInstance("NZD"), Instrument.NZDUSD);
        pairs.put(Currency.getInstance("SEK"), Instrument.USDSEK);
        pairs.put(Currency.getInstance("SGD"), Instrument.USDSGD);
        pairs.put(Currency.getInstance("TRY"), Instrument.USDTRY);
    }    
    
    public static Period lowestTimeframeSupported = Period.TEN_MINS;
    public static Period secondLowestTimeframeSupported = Period.FIFTEEN_MINS; // needed for :15 and :45 strategy calls

	public static List<Period> sortedTimeFrames = new ArrayList<Period>();
	static {
		sortedTimeFrames.add(Period.TEN_MINS);
		sortedTimeFrames.add(Period.FIFTEEN_MINS);
		sortedTimeFrames.add(Period.TWENTY_MINS);
		sortedTimeFrames.add(Period.THIRTY_MINS);
		sortedTimeFrames.add(Period.ONE_HOUR);
		sortedTimeFrames.add(Period.FOUR_HOURS);
		sortedTimeFrames.add(Period.DAILY_SUNDAY_IN_MONDAY);
		sortedTimeFrames.add(Period.WEEKLY);
	}
	
    public static Map<String, String> timeFrameNamesMap = new HashMap<String, String>();
	static {
		timeFrameNamesMap.put(Period.TEN_MINS.toString(), "10min");
		timeFrameNamesMap.put(Period.FIFTEEN_MINS.toString(), "15min");
		timeFrameNamesMap.put(Period.TWENTY_MINS.toString(), "20min");
		timeFrameNamesMap.put(Period.THIRTY_MINS.toString(), "30min");
		timeFrameNamesMap.put(Period.ONE_HOUR.toString(), "1h");
		timeFrameNamesMap.put(Period.FOUR_HOURS.toString(), "4h");
		timeFrameNamesMap.put(Period.DAILY_SUNDAY_IN_MONDAY.toString(), "1d");
		timeFrameNamesMap.put(Period.WEEKLY.toString(), "1w");
	}
	    
	public static Map<String, Period> reverseTimeFrameNamesMap = new HashMap<String, Period>();
	static {
		reverseTimeFrameNamesMap.put("10min", Period.TEN_MINS);
		reverseTimeFrameNamesMap.put("15min", Period.FIFTEEN_MINS);
		reverseTimeFrameNamesMap.put("20min", Period.TWENTY_MINS);
		reverseTimeFrameNamesMap.put("30min", Period.THIRTY_MINS);
		reverseTimeFrameNamesMap.put("1h", Period.ONE_HOUR);
		reverseTimeFrameNamesMap.put("4h", Period.FOUR_HOURS);
		reverseTimeFrameNamesMap.put("1d", Period.DAILY_SUNDAY_IN_MONDAY);
		reverseTimeFrameNamesMap.put("1w", Period.WEEKLY);
	}
	
	public static Map<String, Integer> timeFrameStatsMap = new HashMap<String, Integer>();
	static {
		timeFrameStatsMap.put(Period.TEN_MINS.toString(), new Integer(TWO_WEEKS_WORTH_OF_10min_BARS));
		timeFrameStatsMap.put(Period.FIFTEEN_MINS.toString(), new Integer(TWO_WEEKS_WORTH_OF_15min_BARS));
		timeFrameStatsMap.put(Period.TWENTY_MINS.toString(), new Integer(TWO_WEEKS_WORTH_OF_20min_BARS));
		timeFrameStatsMap.put(Period.THIRTY_MINS.toString(), new Integer(MONTH_WORTH_OF_30min_BARS));
		timeFrameStatsMap.put(Period.ONE_HOUR.toString(), new Integer(MONTH_WORTH_OF_1h_BARS));
		timeFrameStatsMap.put(Period.FOUR_HOURS.toString(), new Integer(QUARTER_WORTH_OF_4h_BARS));
		timeFrameStatsMap.put(Period.DAILY_SUNDAY_IN_MONDAY.toString(), new Integer(YEAR_WORTH_OF_1d_BARS));
		timeFrameStatsMap.put(Period.WEEKLY.toString(), new Integer(TWO_YEAR_WORTH_OF_1W_BARS));
	}

	public static Map<String, String> timeFramesHigherTFMap = new HashMap<String, String>();
	static {
		timeFramesHigherTFMap.put(Period.THIRTY_MINS.toString(), Period.FOUR_HOURS.toString());
		timeFramesHigherTFMap.put(Period.FOUR_HOURS.toString(), Period.DAILY_SUNDAY_IN_MONDAY.toString());
		timeFramesHigherTFMap.put(Period.DAILY_SUNDAY_IN_MONDAY.toString(), Period.WEEKLY.toString());
	}
	
	public static Map<Period, Period> timeFramesHigherTFPeriodMap = new HashMap<Period, Period>();
	static {
		timeFramesHigherTFPeriodMap.put(Period.THIRTY_MINS, Period.FOUR_HOURS);
		timeFramesHigherTFPeriodMap.put(Period.FOUR_HOURS, Period.DAILY_SUNDAY_IN_MONDAY);
		timeFramesHigherTFPeriodMap.put(Period.DAILY_SUNDAY_IN_MONDAY, Period.WEEKLY);
	}
	
	
	public static String getFormatedBarTime(IBar bar)
    {
		return getFormatedTimeGMT(bar.getTime());        
    }

	public static String getFormatedBarTimeWithSecs(IBar bar)
    {
		return getFormatedTimeWithSecsGMT(bar.getTime());        
    }

    public static String getFormatedTimeGMT(long time)
    {
		final SimpleDateFormat sdf;
        sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		return sdf.format(time);        
    }
    
    public static String getFormatedTimeGMTforID(long time)
    {
		final SimpleDateFormat sdf;
        sdf = new SimpleDateFormat("yyyyMMddHHmm");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		return sdf.format(time);        
    }
   

    public static String getFormatedTimeWithSecsGMT(long time)
    {
		final SimpleDateFormat sdf;
        sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		return sdf.format(time);        
    }
    
    public static String getFormatedTimeCET(long time)
    {
		final SimpleDateFormat sdf;
        sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
		sdf.setTimeZone(TimeZone.getTimeZone("CET"));
		return sdf.format(time);        
    }
    
    public static String getFileTimeStamp(long time)
    {
		final SimpleDateFormat sdf;
        sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm");
		return sdf.format(time);        
    }
    
    public static String getMySQLTimeStamp(long time)
    {
		final SimpleDateFormat sdf;
        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		return sdf.format(time);        
    }
    
    public static String getMySQLTimeStampGMT(long time)
    {
		final SimpleDateFormat sdf;
        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		return sdf.format(time);        
    }
    
    public static Date getDateTimeFromStringGMT(String dateStr)
    {
		final SimpleDateFormat sdf;
        sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		try {
			return sdf.parse(dateStr);
		} catch (ParseException e) {
			System.out.println("Invalid date format: " + dateStr + "; expecting dd.MM.yyyy HH:mm");
		}
		return null;
    }
    
    public static Date getDateTimeFromString(String dateStr)
    {
		final SimpleDateFormat sdf;
        sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
		try {
			return sdf.parse(dateStr);
		} catch (ParseException e) {
			System.out.println("Invalid date format: " + dateStr + "; expecting dd.MM.yyyy HH:mm");
		}
		return null;
    }
    
    /**
     * @return 1st element mean, 2nd StDev of data
     */
    public static double[] sdFast ( double[] data )
    {
	    // sd is sqrt of sum of (values-mean) squared divided by n - 1
	    // Calculate the mean
	    double mean = 0;
	    final int n = data.length;
	    double[] res = new double[2];
	    if ( n < 2 )
       {
	    	res[0] = res[1] = Double.NaN;
	        return res;
       }
	    for ( int i=0; i<n; i++ )
	       {
	       mean += data[i];
	       }
	    mean /= n;
	    // calculate the sum of squares
	    double sum = 0;
	    for ( int i=0; i<n; i++ )
	       {
	       final double v = data[i] - mean;
	       sum += v * v;
	       }
	    // Change to ( n - 1 ) to n if you have complete data instead of a sample.
	    res[0] = mean;
	    res[1] = Math.sqrt( sum / n ); 
	    return res;
    }    

    /**
     * @return calc average
     */
    public static double average( double[] data )
    {
	    double mean = 0;
	    final int n = data.length;
	    for ( int i=0; i<n; i++ ) {
	       mean += data[i];
	    }
	    mean /= n;
	    return mean;
    }    
    
    public static double ema( double[] data )
    {
	    double curr_ema = 0;
	    final int n = data.length;
	    for ( int i=0; i<n; i++ ) {
		    double smooth = 2.0 / (1.0 + i + 1.0);
		    curr_ema = (data[i] - curr_ema) * smooth + curr_ema;
	    }
	    return curr_ema;
    }  
    
    public static void setProfitLossHelper(Currency pBaseCurrency, IHistory pHistory) {
        baseCurrency = pBaseCurrency;
        history = pHistory;    
    }

    public static double calculateProfitLoss(IOrder order) throws JFException {
        double closePrice;
        if (order.getState() == IOrder.State.CLOSED) {
            closePrice = order.getClosePrice();
        } else {
            ITick tick = history.getLastTick(order.getInstrument());
            if (order.getOrderCommand().isLong()) {
                closePrice = tick.getBid();
            } else {
                closePrice = tick.getAsk();
            }
        }
        BigDecimal profLossInSecondaryCCY;
        if (order.getOrderCommand().isLong()) {
            profLossInSecondaryCCY = BigDecimal.valueOf(closePrice).subtract(BigDecimal.valueOf(order.getOpenPrice()))
            .multiply(BigDecimal.valueOf(order.getAmount()).multiply(DukaLotSize)).setScale(2, BigDecimal.ROUND_HALF_EVEN);
        } else {
            profLossInSecondaryCCY = BigDecimal.valueOf(order.getOpenPrice()).subtract(BigDecimal.valueOf(closePrice))
            .multiply(BigDecimal.valueOf(order.getAmount()).multiply(DukaLotSize)).setScale(2, BigDecimal.ROUND_HALF_EVEN);
        }
        OfferSide side = order.getOrderCommand().isLong() ? OfferSide.ASK : OfferSide.BID;
        BigDecimal convertedProfLoss = convertByTick(profLossInSecondaryCCY, order.getInstrument().getSecondaryCurrency(),
                baseCurrency, side).setScale(2, BigDecimal.ROUND_HALF_EVEN);
        return convertedProfLoss.doubleValue();
    }

    public static BigDecimal[] calculateProfitLossData(IOrder order) throws JFException {
        BigDecimal[] res = new BigDecimal[3];
        res[0] = BigDecimal.ZERO;
        res[1] = BigDecimal.ZERO;
        res[2] = BigDecimal.ZERO;
        double closePrice;
        if (order.getState() == IOrder.State.CLOSED) {
            closePrice = order.getClosePrice();
        } else {
            ITick tick = history.getLastTick(order.getInstrument());
            if (order.getOrderCommand().isLong()) {
                closePrice = tick.getBid();
            } else {
                closePrice = tick.getAsk();
            }
        }
        BigDecimal profLossInSecondaryCCY;
        if (order.getOrderCommand().isLong()) {
            profLossInSecondaryCCY = BigDecimal.valueOf(closePrice).subtract(BigDecimal.valueOf(order.getOpenPrice()))
            .multiply(BigDecimal.valueOf(order.getAmount()).multiply(DukaLotSize)).setScale(2, BigDecimal.ROUND_HALF_EVEN);
            res[2] = BigDecimal.valueOf(closePrice).subtract(BigDecimal.valueOf(order.getOpenPrice()))
            .multiply(BigDecimal.valueOf(10).pow(order.getInstrument().getPipScale())).setScale(1, BigDecimal.ROUND_HALF_EVEN);
        } else {
            profLossInSecondaryCCY = BigDecimal.valueOf(order.getOpenPrice()).subtract(BigDecimal.valueOf(closePrice))
            .multiply(BigDecimal.valueOf(order.getAmount()).multiply(DukaLotSize)).setScale(2, BigDecimal.ROUND_HALF_EVEN);
            res[2] = BigDecimal.valueOf(order.getOpenPrice()).subtract(BigDecimal.valueOf(closePrice))
            .multiply(BigDecimal.valueOf(10).pow(order.getInstrument().getPipScale())).setScale(1, BigDecimal.ROUND_HALF_EVEN);
        }
        res[0] = profLossInSecondaryCCY;
        OfferSide side = order.getOrderCommand().isLong() ? OfferSide.ASK : OfferSide.BID;
        res[1] = convertByTick(profLossInSecondaryCCY, order.getInstrument().getSecondaryCurrency(),
                baseCurrency, side).setScale(2, BigDecimal.ROUND_HALF_EVEN);        
        return res;
    }
    
    
    
    public static BigDecimal convertByTick(BigDecimal amount, Currency sourceCurrency, Currency targetCurrency, OfferSide side) throws JFException {
        if (targetCurrency.equals(sourceCurrency)) {
            return amount;
        }

        BigDecimal dollarValue;
        if (sourceCurrency.equals(Instrument.EURUSD.getSecondaryCurrency())) {
            dollarValue = amount;
        } else {
            Instrument helperSourceCurrencyPair = pairs.get(sourceCurrency);
            if (helperSourceCurrencyPair == null) {
                throw new IllegalArgumentException("No currency pair found for " + sourceCurrency);
            }

            BigDecimal helperSourceCurrencyPrice = getLastTickPrice(helperSourceCurrencyPair, side);
            if (null == helperSourceCurrencyPrice) return null;

            dollarValue = helperSourceCurrencyPair.toString().indexOf("USD") == 0 ?
                    amount.divide(helperSourceCurrencyPrice, 2, RoundingMode.HALF_EVEN)
                    : amount.multiply(helperSourceCurrencyPrice).setScale(2, RoundingMode.HALF_EVEN);
        }

        if (targetCurrency.equals(Instrument.EURUSD.getSecondaryCurrency())) {
            return dollarValue;
        }

        Instrument pair = pairs.get(targetCurrency);
        BigDecimal price = getLastTickPrice(pair, side);
        if (null == price) return null;

        BigDecimal result = pair.toString().indexOf("USD") == 0 ?
                dollarValue.multiply(price).setScale(2, RoundingMode.HALF_EVEN)
                : dollarValue.divide(price, 2, RoundingMode.HALF_EVEN);

                return result;
    }

    protected static BigDecimal getLastTickPrice(Instrument pair, OfferSide side) throws JFException {
        ITick tick = history.getLastTick(pair);
        if (tick == null) {
            return null;
        }
        if (side == OfferSide.BID) {
            return BigDecimal.valueOf(tick.getBid());
        } else {
            return BigDecimal.valueOf(tick.getAsk());
        }
    }
    
    public static BigDecimal convertByBar(BigDecimal amount, Currency sourceCurrency, Currency targetCurrency, Period period, OfferSide side, long time) throws JFException {
        if (targetCurrency.equals(sourceCurrency)) {
            return amount;
        }

        BigDecimal dollarValue;
        if (sourceCurrency.equals(Instrument.EURUSD.getSecondaryCurrency())) {
            dollarValue = amount;
        } else {
            Instrument helperSourceCurrencyPair = pairs.get(sourceCurrency);
            if (helperSourceCurrencyPair == null) {
                throw new IllegalArgumentException("No currency pair found for " + sourceCurrency);
            }

            BigDecimal helperSourceCurrencyPrice = getLastBarPrice(helperSourceCurrencyPair, side, period, time);
            if (null == helperSourceCurrencyPrice) {
            	System.out.println("Can't get last bar price for " + helperSourceCurrencyPair.toString() + ", timeframe " + period.toString() + ", bar " + FXUtils.getFormatedTimeGMT(time));
            	System.err.println("Can't get last bar price for " + helperSourceCurrencyPair.toString() + ", timeframe " + period.toString() + ", bar " + FXUtils.getFormatedTimeGMT(time));
            	System.exit(1);
            	return null;
            }

            dollarValue = helperSourceCurrencyPair.toString().indexOf("USD") == 0 ?
                    amount.divide(helperSourceCurrencyPrice, 2, RoundingMode.HALF_EVEN)
                    : amount.multiply(helperSourceCurrencyPrice).setScale(2, RoundingMode.HALF_EVEN);
        }

        if (targetCurrency.equals(Instrument.EURUSD.getSecondaryCurrency())) {
            return dollarValue;
        }

        Instrument pair = pairs.get(targetCurrency);
        BigDecimal price = getLastBarPrice(pair, side, period, time);
        if (null == price) {
        	System.out.println("Can't get last bar price for " + pair.toString() + ", timeframe " + period.toString() + ", bar " + FXUtils.getFormatedTimeGMT(time));
        	System.err.println("Can't get last bar price for " + pair.toString() + ", timeframe " + period.toString() + ", bar " + FXUtils.getFormatedTimeGMT(time));
        	System.exit(1);
        	return null;
        }

        BigDecimal result = pair.toString().indexOf("USD") == 0 ?
                dollarValue.multiply(price).setScale(2, RoundingMode.HALF_EVEN)
                : dollarValue.divide(price, 2, RoundingMode.HALF_EVEN);

        return result;
    }

    protected static BigDecimal getLastBarPrice(Instrument pair, OfferSide side, Period period, long time) throws JFException {
    	long lastBarTime = history.getPreviousBarStart(period, time);
        List<IBar> bars = history.getBars(pair, period, side, Filter.WEEKENDS, 1, lastBarTime, 0);
        if (bars == null || bars.size() == 0) {
            return null;
        }
        return BigDecimal.valueOf(bars.get(0).getClose());
    }
    
    public static DateTime calcEndBar() {
		// During weekend set end bar to last trading bar of previous Friday
		DateTime 
			timeStamp = new DateTime(),
			roundedEnd = new DateTime(timeStamp.getYear(), timeStamp.getMonthOfYear(), timeStamp.getDayOfMonth(), 
				timeStamp.getHourOfDay(), timeStamp.getMinuteOfHour() < 30 ? 0 : 30, 0, 0),
			result = new DateTime(roundedEnd);
		if ((roundedEnd.getDayOfWeek() == DateTimeConstants.FRIDAY && roundedEnd.getHourOfDay() > 22)
				|| roundedEnd.getDayOfWeek() == DateTimeConstants.SATURDAY
				|| (roundedEnd.getDayOfWeek() == DateTimeConstants.SUNDAY && roundedEnd.getHourOfDay() < 23)) {
			if (roundedEnd.getDayOfWeek() == DateTimeConstants.FRIDAY)
				result = new DateTime(roundedEnd.getYear(), roundedEnd.getMonthOfYear(), roundedEnd.getDayOfMonth(), 22, 30, 0, 0);
			else if (roundedEnd.getDayOfWeek() == DateTimeConstants.SATURDAY) {
				DateTime friday = new DateTime(roundedEnd.minusDays(1));
				result = new DateTime(friday.getYear(), friday.getMonthOfYear(), friday.getDayOfMonth(), 22, 30, 0, 0);
			}
			else if (roundedEnd.getDayOfWeek() == DateTimeConstants.SUNDAY && roundedEnd.getHourOfDay() < 23) {
				DateTime friday = new DateTime(roundedEnd.minusDays(2));
				result = new DateTime(friday.getYear(), friday.getMonthOfYear(), friday.getDayOfMonth(), 22, 30, 0, 0);
			} 
		}
		return result;
	}    

	public static DateTime calcTimeOfLastNYClose30minBar(long time) {
		// During weekend set end bar to last trading bar of previous Friday
		DateTime 
			timeStamp = new DateTime(time),
			roundedEnd = new DateTime(timeStamp.getYear(), timeStamp.getMonthOfYear(), timeStamp.getDayOfMonth(), 
				timeStamp.getHourOfDay(), timeStamp.getMinuteOfHour() < 30 ? 0 : 30, 0, 0),
			result = new DateTime(roundedEnd);
		if ((roundedEnd.getDayOfWeek() == DateTimeConstants.FRIDAY && roundedEnd.getHourOfDay() > 22)
				|| roundedEnd.getDayOfWeek() == DateTimeConstants.SATURDAY
				|| roundedEnd.getDayOfWeek() == DateTimeConstants.SUNDAY
				|| (roundedEnd.getDayOfWeek() == DateTimeConstants.MONDAY && (roundedEnd.getHourOfDay() < 22 || (roundedEnd.getHourOfDay() == 22  && roundedEnd.getMinuteOfHour() < 30)))) {
			// anything after last close in the week and on Sunday --> put to last Friday's close !
			if (roundedEnd.getDayOfWeek() == DateTimeConstants.FRIDAY)
				result = new DateTime(roundedEnd.getYear(), roundedEnd.getMonthOfYear(), roundedEnd.getDayOfMonth(), 22, 30, 0, 0);
			else if (roundedEnd.getDayOfWeek() == DateTimeConstants.SATURDAY) {
				DateTime friday = new DateTime(roundedEnd.minusDays(1));
				result = new DateTime(friday.getYear(), friday.getMonthOfYear(), friday.getDayOfMonth(), 22, 30, 0, 0);
			}
			else if (roundedEnd.getDayOfWeek() == DateTimeConstants.SUNDAY) {
				DateTime friday = new DateTime(roundedEnd.minusDays(2));
				result = new DateTime(friday.getYear(), friday.getMonthOfYear(), friday.getDayOfMonth(), 22, 30, 0, 0);
			} 
			else if (roundedEnd.getDayOfWeek() == DateTimeConstants.MONDAY) {
				DateTime friday = new DateTime(roundedEnd.minusDays(3));
				result = new DateTime(friday.getYear(), friday.getMonthOfYear(), friday.getDayOfMonth(), 22, 30, 0, 0);
			} 
		} else {
			// bar is within weekly working hours
			if (roundedEnd.getHourOfDay() >= 0) {
				DateTime prevDay = new DateTime(roundedEnd.minusDays(1));
				result = new DateTime(prevDay.getYear(), prevDay.getMonthOfYear(), prevDay.getDayOfMonth(), 22, 30, 0, 0);
			}
			else {
				result = new DateTime(roundedEnd.getYear(), roundedEnd.getMonthOfYear(), roundedEnd.getDayOfMonth(), 22, 30, 0, 0);				
			}							
		}
		return result;
	}    
	
	public static DateTime calcTimeOfLastNYClose30minBar() {
		return calcTimeOfLastNYClose30minBar(System.currentTimeMillis());
	}

	public static DateTime getLastTradingDay(long time) {
		DateTime 
			current = new DateTime(time, DateTimeZone.forID("UTC")),
			result = null;
		// ignores the whole weekend
		switch (current.getDayOfWeek()) {
			case 1:
				result = current.minusDays(3);
				break;
			case 6:
				result = current.minusDays(1);
				break;
			case 7:
				result = current.minusDays(2);
				break;
			default: 
				result = current;
				break;
		}
		return new DateTime(result.getYear(), result.getMonthOfYear(), result.getDayOfMonth(), 0, 0, 0, 0, DateTimeZone.forID("UTC"));
	}

	/**
	 * Accounts for weekends. For trades made during Fri next allowed trade is calculated from Sun 23h CET 
	 */
	public static boolean tradingDistanceOK(long currentTime, long nextTradeAllowedTime) {
		DateTime next = new DateTime(nextTradeAllowedTime, DateTimeZone.forID("UTC"));
		if (next.getDayOfWeek() < 5 
			|| (next.getDayOfWeek() == 5 && next.getHourOfDay() < 21)
			|| (next.getDayOfWeek() == 7 && next.getHourOfDay() >= 21))
			return currentTime > nextTradeAllowedTime;
		
		// nextTradeAllowedTime falls in market close hours over weekend. Need adjustment for difference between Fri market close and Sun market open
		// that's exactly two days 
		DateTime 
			adjustedNext = next.plus(2 * 24 * 3600 * 1000);
		return currentTime > adjustedNext.getMillis();
	}    
	
	/**
	 * Adds time but accounts for weekends, so more is added if original currentTime + timeToAdd falls into weekend 
	 */
	public static long plusTradingTime(long currentTime, long timeToAdd) {
		DateTime next = new DateTime(currentTime + timeToAdd, DateTimeZone.forID("GMT"));
		if (next.getDayOfWeek() < 5 
			|| (next.getDayOfWeek() == 5 && next.getHourOfDay() < 20)
			|| (next.getDayOfWeek() == 7 && next.getHourOfDay() >= 20))
			return next.getMillis();
		
		// nextTradeAllowedTime falls in market close hours over weekend. Need adjustment for difference between Fri market close and Sun market open
		// that's exactly two days 
		DateTime adjustedNext = next.plus(2 * 24 * 3600 * 1000);
		return adjustedNext.getMillis();
	}    
	
	public static String replaceCentered(String template, String replacement) {
		int 
			replPos = (template.length() - replacement.length()) / 2,
			restPos = replPos + replacement.length();
		if (replPos > 0 && restPos > 0)
			return new String(template.substring(0, replPos) + replacement + template.substring(restPos));
		else
			return new String("Can not convert ! template = " + template + ", replacement = " + replacement 
					+ " (replPos = " + replPos + ", restPos = " + restPos);
	}

	public static String beautify(String s) {
		String lower = s.toLowerCase().replace('_', ' ').replace("zero", "0");
		return new String(lower.substring(0, 1).toUpperCase() + lower.substring(1));
	}

	public static ResultSet dbGetAllSubscribedInstruments(Properties properties) {
	    try {
	        Class.forName(properties.getProperty("sqlDriver", "sun.jdbc.odbc.JdbcOdbcDriver"));
	    } catch (java.lang.ClassNotFoundException e1) {
	    	System.out.print("ODBC driver load failure (ClassNotFoundException: " + e1.getMessage() + ")");
	        System.exit(1);
	    }
		String url = properties.getProperty("odbcURL");
		Connection logDB = null;
		try {
		    logDB = DriverManager.getConnection(url, properties.getProperty("dbUserName", ""), properties.getProperty("dbPassword", ""));
		} catch (SQLException ex) {
			System.out.print("Log database problem: " + ex.getMessage() + " when trying to establish connection");
	        System.exit(1);
		}        
	
		String statementStr = new String("SELECT distinct ticker as ticker FROM " + FXUtils.getDbToUse() + ".vsubscriptions");
		try {
			Statement qry = logDB.createStatement();
			ResultSet result = qry.executeQuery(statementStr);
			return result;
		} catch (SQLException ex) {
			   System.out.print("Log database problem: " + ex.getMessage());
			   System.out.print(statementStr);
	           System.exit(1);
		}
		return null;
	}

	public static ResultSet dbGetTestTrades(Properties properties) {
	    try {
	        Class.forName(properties.getProperty("sqlDriver", "sun.jdbc.odbc.JdbcOdbcDriver"));
	    } catch (java.lang.ClassNotFoundException e1) {
	    	System.out.print("ODBC driver load failure (ClassNotFoundException: " + e1.getMessage() + ")");
	        System.exit(1);
	    }
		String url = properties.getProperty("odbcURL");
		Connection logDB = null;
		try {
		    logDB = DriverManager.getConnection(url, properties.getProperty("dbUserName", ""), properties.getProperty("dbPassword", ""));
		} catch (SQLException ex) {
			System.out.print("Log database problem: " + ex.getMessage() + " when trying to establish connection");
	        System.exit(1);
		}        
	
		String statementStr = new String("SELECT Label, Event, EventStart, EventEnd FROM " + FXUtils.getDbToUse() + ".ttesttrades WHERE testRunDone = 'N'");
		try {
			Statement qry = logDB.createStatement();
			ResultSet result = qry.executeQuery(statementStr);
			return result;
		} catch (SQLException ex) {
			   System.out.print("Log database problem: " + ex.getMessage());
			   System.out.print(statementStr);
	           System.exit(1);
		}
		return null;
	}
	
	public static ResultSet dbCountTestTrades(Properties properties) {
	    try {
	        Class.forName(properties.getProperty("sqlDriver", "sun.jdbc.odbc.JdbcOdbcDriver"));
	    } catch (java.lang.ClassNotFoundException e1) {
	    	System.out.print("ODBC driver load failure (ClassNotFoundException: " + e1.getMessage() + ")");
	        System.exit(1);
	    }
		String url = properties.getProperty("odbcURL");
		Connection logDB = null;
		try {
		    logDB = DriverManager.getConnection(url, properties.getProperty("dbUserName", ""), properties.getProperty("dbPassword", ""));
		} catch (SQLException ex) {
			System.out.print("Log database problem: " + ex.getMessage() + " when trying to establish connection");
	        System.exit(1);
		}        
	
		String statementStr = new String("SELECT count(*) as cnt FROM " + FXUtils.getDbToUse() + ".ttesttrades WHERE testRunDone = 'N'");
		try {
			Statement qry = logDB.createStatement();
			ResultSet result = qry.executeQuery(statementStr);
			return result;
		} catch (SQLException ex) {
			   System.out.print("Log database problem: " + ex.getMessage());
			   System.out.print(statementStr);
	           System.exit(1);
		}
		return null;
	}
	
	public static double toPips(double absValue, Instrument i) {
		return absValue * Math.pow(10, i.getPipScale());
	}
	
	public static void dbUpdateInsert(Connection logDB, String sql) {
		try {
			Statement insert = logDB.createStatement();
			insert.executeUpdate(sql);
		} catch (SQLException ex) {
			   System.out.println("Problem when inserting into the DB: " + ex.getMessage());
			   System.out.println(sql);
			   try {
				System.setErr(new PrintStream(new FileOutputStream("d:\\temp\\system.err.txt")));
			   } catch (FileNotFoundException e) {
				e.printStackTrace();
			   }
			   System.err.println("Problem when inserting into the DB: " + ex.getMessage());
			   System.err.println(sql);
			   System.err.flush();
			   System.err.close();
	           System.exit(1);
		}		
	}
	
	public static String dbGetTradeLogInsert(List<FlexLogEntry> fields, String bt_run, String order_label, String direction, String event)
	{
		String 
			dbFieldNamesStr = new String(" (bt_run, order_label, direction, event"),
			dbValuesStr = new String(" VALUES ('" 
					+ bt_run + "', '" 
					+ order_label + "', '" 
					+ direction + "', '" 
					+ event + "'");
		
		for (int i = 0; i < fields.size() && i < 50; i++) {
			FlexLogEntry field = fields.get(i);
			dbFieldNamesStr += ", ValueName" + (i + 1);
			if (field.isDouble()) {
				dbFieldNamesStr += ", ValueD" + (i + 1);
				dbValuesStr += ", '" + field.getLabel() + "', " + field.getFormattedValue();
			}
			else {
				dbFieldNamesStr += ", ValueS" + (i + 1);
				dbValuesStr += ", '" + field.getLabel() + "', '" + field.getFormattedValue() + "'";
			}
		}
		// close the brackets
		dbFieldNamesStr += ") ";
		dbValuesStr += ")";
		String statementStr = "INSERT INTO " + dbToUse + ".tabstradelog " + dbFieldNamesStr + dbValuesStr;
		return new String(statementStr);
	}	
	
	public static void dbExecSQL(Connection logDB, String sql) {
		try {
			Statement insert = logDB.createStatement();
			insert.execute(sql);
		} catch (SQLException ex) {
			   System.out.println("Problem when executing SQL: " + ex.getMessage());
			   System.out.println(sql);
			   System.err.println("Problem when executing SQL: " + ex.getMessage());
			   System.err.println(sql);
	           System.exit(1);
		}		
	}	
	
	public static ResultSet dbReadQuery(Connection logDB, String sql) {
		try {
			Statement qry = logDB.createStatement();
			ResultSet result = qry.executeQuery(sql);
			return result;
		} catch (SQLException ex) {
			   System.out.println("Problem when reading from the DB: " + ex.getMessage());
			   System.out.println(sql);
	           System.exit(1);
		}
		return null;
	}
	public static int dbGetInstrumentID(Connection logDB, String ticker) {
		
		String statementStr = new String("SELECT instrument_id FROM " + FXUtils.getDbToUse() + ".tinstrument WHERE ticker = '" + ticker + "'");
		try {
			Statement qry = logDB.createStatement();
			ResultSet result = qry.executeQuery(statementStr);
			if (result.next())
				return result.getInt("instrument_id");
			else {
				   System.out.print("No such instrument: " + ticker);
		           System.exit(1);				
			}
		} catch (SQLException ex) {
			   System.out.print("Log database problem: " + ex.getMessage());
			   System.out.print(statementStr);
	           System.exit(1);
		}
		return -1;
	}
	
	public static int dbGetUserID(Connection logDB, String userID) {
		
		String statementStr = new String("SELECT user_id FROM " + FXUtils.getDbToUse() + ".tuser where email = '" + userID + "'");
		try {
			Statement qry = logDB.createStatement();
			ResultSet result = qry.executeQuery(statementStr);
			if (result.next())
				return result.getInt("user_id");
			else {
				   System.out.print("No such user, ID: " + userID);
		           System.exit(1);				
			}
		} catch (SQLException ex) {
			   System.out.print("Log database problem: " + ex.getMessage());
			   System.out.print(statementStr);
	           System.exit(1);
		}
		return -1;
	}
	
	public static double roundToPip(double calcPrice, Instrument i) {
		return (Math.round(calcPrice * Math.pow(10, i.getPipScale()))) / Math.pow(10, i.getPipScale());
	}

	public static String getDbToUse() {
		return dbToUse;
	}

	public static void setDbToUse(String dbToUse) {
		FXUtils.dbToUse = dbToUse;
	}

	public static String httpGet(String urlToCall) {
		String result = "";
		try {			
            URL url = new URL(urlToCall);
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"));
            result = in.readLine();						
			/* old
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			while ((line = rd.readLine()) != null) {
				result += line;
			}
			rd.close();
			*/
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}
	
	public static int toTimeFrameCode(Period pPeriod, Connection logDB, Logger log) {
		String statementStr = new String("SELECT code_value FROM " + FXUtils.getDbToUse() + ".tcode WHERE code_group = 'TimeFrame' AND code_desc = '" 
				+ FXUtils.timeFrameNamesMap.get(pPeriod.toString()) + "'");
		try {
			Statement qry = logDB.createStatement();
			ResultSet result = qry.executeQuery(statementStr);
			if (!result.next())
				return -1;
			return result.getInt("code_value");
		} catch (SQLException ex) {
			   log.print("Log database problem: " + ex.getMessage());
			   log.print(statementStr);
			   log.close();
	           System.exit(1);
		}
		return -1;
	}	
	
	public static Period fromTimeFrameCode(int timeFrameCode, Connection logDB, Logger log) {
		String statementStr = new String("SELECT code_desc FROM " + FXUtils.getDbToUse() 
				+ ".tcode WHERE code_group = 'TimeFrame' AND code_value = " 
				+ timeFrameCode);
		try {
			Statement qry = logDB.createStatement();
			ResultSet result = qry.executeQuery(statementStr);
			if (!result.next())
				return null;
			return reverseTimeFrameNamesMap.get(result.getString("code_desc"));
		} catch (SQLException ex) {
			   log.print("Log database problem fetching time frame name: " + ex.getMessage());
			   log.print(statementStr);
			   log.close();
	           System.exit(1);
		}
		return null;
	}	
	
    public static class TradeLog {
        public String orderLabel;
        public boolean 
            isLong;
        public long 
            signalTime,
            fillTime,
            maxProfitTime,
            maxLossTime,
            maxDDTime,
            exitTime;
        public double 
            entryPrice,
            fillPrice,
            SL,
            initialRisk,
            ratioCloudATR,
            maxRisk,
            maxLoss,
            maxLossATR,
            maxDD,
            maxDDATR,
            maxProfit,
            maxProfitPrice,
            PnL;
        
         public String
             exitReason = null;
         
         public TradeLog(String pOrderLabel, boolean pIsLong, long pSignalTime, double pEntryPrice, double pSL, double pInitialRisk) {
                orderLabel = pOrderLabel;
                isLong = pIsLong;
                signalTime = pSignalTime;
                entryPrice = pEntryPrice;
                fillPrice = entryPrice; // needed for risk calc of unfilled orders, which can get new SL while waiting due to changes in the clould borders !
                SL = pSL;
                initialRisk = pInitialRisk;
                maxRisk = initialRisk;
                
                exitReason = null;
                PnL = 0.0;
                maxLossATR = maxLoss = 0.0;
                maxDD = maxDDATR = 0.0;
                maxProfit = 0.0;
                maxProfitPrice = 0.0;
                maxLossTime = maxProfitTime = maxDDTime = 0;
         }
         
         public double missedProfit(Instrument instrument) {
             // PnL taken from IOrder and already in pips
             return maxProfit * Math.pow(10, instrument.getPipScale()) - PnL;
         }
         
         public double missedProfitPerc(Instrument instrument) {
             // PnL taken from IOrder and already in pips
             return PnL > 0.0 && maxProfit != 0.0 ? 100 * missedProfit(instrument) / (maxProfit * Math.pow(10, instrument.getPipScale())) : 0.0;
         }
         
         public void updateMaxRisk(double newSL) {
             if (isLong) {
                 if (fillPrice - newSL > maxRisk)
                     maxRisk = fillPrice - newSL;
             }
             else {
                 if (newSL - fillPrice > maxRisk)
                     maxRisk = newSL - fillPrice;
             }
         }
         
         public void updateMaxLoss(IBar bidBar, double ATR) {
             if (isLong) {
                 if (bidBar.getLow() < fillPrice && bidBar.getLow() - fillPrice  < maxLoss) {
                     maxLoss = bidBar.getLow() - fillPrice;
                     maxLossATR = maxLoss / ATR;
                     maxLossTime = bidBar.getTime();
                 }
             }
             else {
                 if (bidBar.getHigh() > fillPrice && fillPrice - bidBar.getHigh() < maxLoss) {
                     maxLoss = fillPrice - bidBar.getHigh();
                     maxLossATR = maxLoss / ATR;
                     maxLossTime = bidBar.getTime();
                 }
             }
         }
         
         public void updateMaxDD(IBar bidBar, double ATR) {
             //updateMaxProfit(bidBar);
             // avoid low of the maxProfit bar
        	 if (bidBar.getTime() <= maxProfitTime) 
                 return;
                 
             if (isLong) {
                 if (bidBar.getLow() >= fillPrice && maxProfitPrice - bidBar.getLow() > maxDD) {
                     maxDD = maxProfitPrice - bidBar.getLow();
                     maxDDTime = bidBar.getTime();
                     maxDDATR = maxDD / ATR;
                 }
             }
             else {
                 if (bidBar.getHigh() <= fillPrice && bidBar.getHigh() - maxProfitPrice > maxDD) {
                     maxDD = bidBar.getHigh() - maxProfitPrice;
                     maxDDTime = bidBar.getTime();
                     maxDDATR = maxDD / ATR;
                 }
             }
         }
         
         public void updateMaxProfit(IBar bidBar) {
             if (isLong) {
                 if (bidBar.getHigh() > fillPrice && bidBar.getHigh() - fillPrice > maxProfit) {
                     maxProfit = bidBar.getHigh() - fillPrice;                 
                     maxProfitPrice = bidBar.getHigh();
                     maxProfitTime = bidBar.getTime();
                 }
             }
             else {
                 if (bidBar.getLow() < fillPrice && fillPrice - bidBar.getLow() > maxProfit) {
                     maxProfit = fillPrice - bidBar.getLow();                                  
                     maxProfitPrice = bidBar.getLow();
                     maxProfitTime = bidBar.getTime();
                 }
             }             
         }
         
         public String exitReport(Instrument instrument) {
            return new String("ER;" 
                + orderLabel + ";" 
                + (isLong ? "LONG" : "SHORT") + ";" 
                + getFormatedTimeGMT(signalTime) + ";" 
                + getFormatedTimeGMT(fillTime) + ";"
                + getFormatedTimeGMT(exitTime) + ";"
                + exitReason + ";" 
                + (instrument.getPipScale() == 2 ? df2.format(entryPrice) : df5.format(entryPrice)) + ";" 
                + (instrument.getPipScale() == 2 ? df2.format(fillPrice) : df5.format(fillPrice)) + ";" 
                + (instrument.getPipScale() == 2 ? df2.format(SL) : df5.format(SL)) + ";" 
                + df1.format(initialRisk * Math.pow(10, instrument.getPipScale())) + ";" 
                + df1.format(maxRisk * Math.pow(10, instrument.getPipScale())) + ";" 
                + df1.format(maxLoss * Math.pow(10, instrument.getPipScale())) + ";" 
                + df2.format(maxLossATR) + ";" 
                + getFormatedTimeGMT(maxLossTime) + ";" 
                + df1.format(maxDD * Math.pow(10, instrument.getPipScale())) + ";" 
                + df2.format(maxDDATR) + ";" 
                + getFormatedTimeGMT(maxDDTime) + ";" 
                + df1.format(maxProfit * Math.pow(10, instrument.getPipScale())) + ";" 
                + (instrument.getPipScale() != 2 ? df5.format(maxProfitPrice) : df2.format(maxProfitPrice)) + ";"
                + getFormatedTimeGMT(maxProfitTime) + ";" 
                + df1.format(PnL) + ";" 
                + df1.format(missedProfit(instrument)) + ";" 
                + df1.format(missedProfitPerc(instrument)));
               
         }

		public List<FlexLogEntry> exportToFlexLogs(Instrument instrument, int noOfBarsInTrade) {
			List<FlexLogEntry> l = new ArrayList<FlexLogEntry>();
			l.add(new FlexLogEntry("signalTime", FXUtils.getFormatedTimeGMT(signalTime)));
			l.add(new FlexLogEntry("fillTime", FXUtils.getFormatedTimeGMT(fillTime)));
			l.add(new FlexLogEntry("exitTime", FXUtils.getFormatedTimeGMT(exitTime)));
			l.add(new FlexLogEntry("exitReason", exitReason));
			l.add(new FlexLogEntry("entryPrice", new Double(entryPrice), instrument.getPipScale() == 2 ? FXUtils.df2 : FXUtils.df5));
			l.add(new FlexLogEntry("fillPrice", new Double(fillPrice), instrument.getPipScale() == 2 ? FXUtils.df2 : FXUtils.df5));
			l.add(new FlexLogEntry("SL", new Double(SL), instrument.getPipScale() == 2 ? FXUtils.df2 : FXUtils.df5));
			l.add(new FlexLogEntry("initialRisk", new Double(initialRisk * Math.pow(10, instrument.getPipScale())), FXUtils.df1));
			l.add(new FlexLogEntry("maxRisk", new Double(maxRisk * Math.pow(10, instrument.getPipScale())), FXUtils.df1));
			l.add(new FlexLogEntry("maxLoss", new Double(maxLoss * Math.pow(10, instrument.getPipScale())), FXUtils.df1));
			l.add(new FlexLogEntry("maxLossATR", new Double(maxLossATR), FXUtils.df1));
			l.add(new FlexLogEntry("maxLossTime", FXUtils.getFormatedTimeGMT(maxLossTime)));
			l.add(new FlexLogEntry("maxDD", new Double(maxDD * Math.pow(10, instrument.getPipScale())), FXUtils.df1));
			l.add(new FlexLogEntry("maxDDATR", new Double(maxDDATR), FXUtils.df1));
			l.add(new FlexLogEntry("maxDDTime", FXUtils.getFormatedTimeGMT(maxDDTime)));
			l.add(new FlexLogEntry("maxProfit", new Double(maxProfit * Math.pow(10, instrument.getPipScale())), FXUtils.df1));
			l.add(new FlexLogEntry("maxProfitPrice", new Double(maxProfitPrice), instrument.getPipScale() == 2 ? FXUtils.df2 : FXUtils.df5));
			l.add(new FlexLogEntry("maxProfitTime", FXUtils.getFormatedTimeGMT(maxProfitTime)));
			l.add(new FlexLogEntry("PnL", new Double(PnL), FXUtils.df1));
			l.add(new FlexLogEntry("missedProfit", new Double(missedProfit(instrument)), FXUtils.df1));
			l.add(new FlexLogEntry("missedProfitPerc", new Double(missedProfitPerc(instrument)), FXUtils.df1));
			return l;
		}
    }
 }
