package jforex.utils.log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Duration {
	protected long startTime, previousTime;
	protected SimpleDateFormat 
		withMinutes = new SimpleDateFormat("mm:ss"),
		withHours = new SimpleDateFormat("HH:mm:ss"),
		withDays = new SimpleDateFormat("dd days, HH:mm:ss");
	protected List<String> logLines = new ArrayList<String>();


	public Duration(long startTime, String header) {
		this.startTime = startTime;
		previousTime = startTime;
		logLines.add(header);
	}

	public long stepDuration(long stepEndTime) {
		long oldPreviousTime = previousTime;
		previousTime = stepEndTime;
		return stepEndTime - oldPreviousTime;
	}

	public long fullDuration(long stepEndTime) {
		previousTime = stepEndTime;
		return stepEndTime - startTime;
	}

	public String getStepDurationString(long stepEndTime) {
		long stepDuration = stepDuration(stepEndTime);
		SimpleDateFormat format = chooseFormat(stepDuration);
		String res = new String(format.format(new Date(stepDuration)));
		return res;
	}
	
	protected SimpleDateFormat chooseFormat(long stepDuration) {
		if (stepDuration > 24 * 3600 * 1000)
			return withDays;
		else if (stepDuration > 3600 * 1000)
			return withHours;
		return withMinutes;
	}

	public String getFullDurationString(long stepEndTime) {
		long stepDuration = fullDuration(stepEndTime);
		SimpleDateFormat format = chooseFormat(stepDuration);
		String res = new String(format.format(new Date(stepDuration)));
		return res;
	}
	
	public void addStep(String desc, long stepEndTime) {
		logLines.add(desc + ", duration: " + getStepDurationString(stepEndTime));
	}
	
	public void addLastStep(String desc, long stepEndTime) {
		addStep(desc, stepEndTime);
		logLines.add("Total duration: " + getFullDurationString(stepEndTime));
	}
	
	public List<String> getFullReport() {
		return logLines;
	}
}
