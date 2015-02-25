package jforex.techanalysis;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import jforex.utils.FXUtils;

public class PriceZone implements Comparable<PriceZone> {
	
	String direction;
	double 
		bottom, 
		top;

	public PriceZone(String direction, double bottom, double top) {
		super();
		this.direction = direction;
		this.bottom = bottom;
		this.top = top;
	}
	
	public static PriceZone parsePriceZone(String input) {
		if (input == null || input.length() < 10)
			return null;
		
		// format: <L or S>:<bottom>-<top>
		StringTokenizer st1 = new StringTokenizer(input, ":");
		if (!st1.hasMoreTokens()) 
			return null;
		
		String direction = st1.nextToken();
		if (!st1.hasMoreTokens()) 
			return null;

		StringTokenizer st2 = new StringTokenizer(st1.nextToken(), "-");
		
		String
			bottomStr, topStr;
		double
			bottomValue = -1.0, topValue = -1.0; 
		if (st2.hasMoreTokens())
			bottomStr = st2.nextToken();
		else 
			return null;
		if (st2.hasMoreTokens())
			topStr = st2.nextToken();
		else 
			return null;
		try {
			bottomValue = Double.parseDouble(bottomStr);
		} catch (NumberFormatException e) { System.err.print("PriceZone bottom " + bottomStr + " can't be converted..."); }
		try {
			topValue = Double.parseDouble(topStr);
		} catch (NumberFormatException e) { System.err.print("PriceZone bottom " + bottomStr + " can't be converted..."); }
		
		if (bottomValue > topValue) {
			System.err.print("PriceZone bottom " + bottomStr + " bigger then top " + topStr);
			return null;
		}
		return new PriceZone(direction, bottomValue, topValue);
	}		
	
	public static List<PriceZone> parseAllPriceZones(String input) {
		if (input == null || input.length() < 10)
			return null;
		
		List<PriceZone> result = new ArrayList<PriceZone>();
		StringTokenizer st = new StringTokenizer(input, ";");
		while (st.hasMoreTokens()) {
			String nextPriceZone = st.nextToken();
			PriceZone created = PriceZone.parsePriceZone(nextPriceZone);
			if (created != null)
				result.add(created);
		}
		return result;
	}
	
	public boolean hit(double price, double tolerance) {
		return bottom - tolerance <= price && price <= top + tolerance;
	}

	public String getHitText() {
		return new String((direction.toUpperCase().equals("L") ? "Long entry zone " : "Short entry zone ") 
			+ FXUtils.df4.format(bottom) + " - " + FXUtils.df4.format(top) + " hit !");
	}
	

	/* 
	 * PriceZones are sorted in descending order of bottom value, so they appear in lists as on the chart
	 */
	@Override
	public int compareTo(PriceZone other) {
		if (this.bottom > other.bottom)
			return -1;
		if (this.bottom < other.bottom)
			return 1;
		return 0;	}

	public String getDirection() {
		return direction;
	}

}
