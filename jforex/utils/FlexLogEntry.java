package jforex.utils;

import java.text.DecimalFormat;

public class FlexLogEntry {
	private String label;
	private Object value;
	private DecimalFormat df = null;

	public FlexLogEntry(String label, Object value, DecimalFormat df) {
		super();
		this.label = label;
		this.value = value;
		this.df = df;
	}

	public FlexLogEntry(String label, String value) {
		super();
		this.label = label;
		this.value = value;
		this.df = null;
	}

	public String getLabel() {
		return label;
	}

	public Object getValue() {
		return value;
	}

	public double getDoubleValue() {
		if (isDouble()) {
			return ((Double) value).doubleValue();
		} else {
			return -1.0;
		}
	}

	public double getIntegerValue() {
		if (isInteger()) {
			return ((Integer) value).intValue();
		} else {
			return -1;
		}
	}

	public String getFormattedValue() {
		String res = new String();
		if (isDouble() && df != null) {
			res = df.format(((Double) value).doubleValue());
		} else
			res = value.toString();
		return res;
	}

	public boolean isDouble() {
		return value.getClass().equals(Double.class);
	}

	public boolean isInteger() {
		return value.getClass().equals(Integer.class);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof String)
			return getLabel().equals(obj);
		else
			return getLabel().equals(((FlexLogEntry) obj).getLabel());
	}

	public String getNoTFLabel() {
		if (label.contains("30min"))
			return label.replace("30min", "");
		if (label.contains("4h"))
			return label.replace("4h", "");
		if (label.contains("1d"))
			return label.replace("1d", "");
		return label;
	}

	public String getSQLValue() {
		if (isDouble() && df != null)
			return getFormattedValue();
		else
			return "'" + value.toString() + "'";
	}
}
