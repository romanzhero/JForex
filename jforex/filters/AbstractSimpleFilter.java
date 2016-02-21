package jforex.filters;

import java.text.DecimalFormat;

import jforex.utils.FXUtils;

import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.IIndicators.AppliedPrice;

public abstract class AbstractSimpleFilter {

	protected String name, explanation;

	protected Period period;

	protected double minAllowedValue;
	protected double maxAllowedValue;
	protected double[] auxParams;

	protected IIndicators indicators;

	public AbstractSimpleFilter() {
		super();
	}

	public void set(String pName, IIndicators pIndicators, Period p,
			double pMin, double pMax) {
		name = pName;
		indicators = pIndicators;
		period = p;
		minAllowedValue = pMin;
		maxAllowedValue = pMax;
	}

	public void setAuxParams(double[] auxParams) {
		this.auxParams = auxParams;
	}

	protected abstract double calcIndicator(Instrument instrument,
			OfferSide side, AppliedPrice appliedPrice, long time)
			throws JFException;

	protected DecimalFormat decFormat() {
		return FXUtils.df2;
	}

	public boolean check(Instrument instrument, OfferSide side,
			AppliedPrice appliedPrice, long time) throws JFException {
		double indicatorValue = calcIndicator(instrument, side, appliedPrice,
				time);
		if (indicatorValue >= minAllowedValue
				&& indicatorValue <= maxAllowedValue) {
			explanation = "filter passed";
			return true;
		} else {
			explanation = new String(name + ";" + instrument.toString() + ";"
					+ FXUtils.getFormatedTimeGMT(time) + ";filtered out: "
					+ decFormat().format(indicatorValue) + " not within ["
					+ decFormat().format(minAllowedValue) + ", "
					+ decFormat().format(maxAllowedValue) + "]");
			return false;
		}
	}

	public String explain(long barTime) {
		return "For bar: " + FXUtils.getFormatedTimeGMT(barTime) + ";"
				+ explanation;
	}

	public String getName() {
		return name;
	}
}