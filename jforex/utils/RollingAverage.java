/**
 * 
 */
package jforex.utils;

/**
 * Continuously updated average. After initialisation with original array,
 * each update deletes the oldest value and adds the newest, so that new average is calculated
 * Implementation bases on holding and updating the sum of values
 *
 */
public class RollingAverage {
	protected double[] initialValues = null;
	protected double sum = 0.0;
	protected long length;
	int currentIndex = 0;
	
	public RollingAverage(double[] initialValues) {
		super();
		this.initialValues = initialValues;
		length = initialValues.length;
		sum = 0.0;
		for (int i = 0; i < length; i++) {
			sum += initialValues[i]; 
		}
	}
	
	public double getAverage() {
		return sum / length;
	}
	
	public double calcUpdatedAverage(double newValue) {
		if (currentIndex < length)
			sum = sum - initialValues[currentIndex++] + newValue;
		else
			sum += newValue;
		return getAverage();
	}
}
