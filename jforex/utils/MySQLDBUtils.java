package jforex.utils;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

import com.dukascopy.api.IBar;

public class MySQLDBUtils {

    public static String getFormatedTimeGMT(long time)
    {
		final SimpleDateFormat sdf;
        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		return sdf.format(time);        
    }

    public static String getFormatedTimeGMT(IBar bar)
    {
		return getFormatedTimeGMT(bar.getTime());        
    }

    public static String getFormatedTimeCET(long time)
    {
		final SimpleDateFormat sdf;
        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		sdf.setTimeZone(TimeZone.getTimeZone("CET"));
		return sdf.format(time);        
    }

    public static String getFormatedTimeCET(IBar bar)
    {
		return getFormatedTimeCET(bar.getTime());        
    }
    
}
