package jforex.utils;

import java.io.File;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;

public class SimpleProperties extends SortedProperties {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1895673503665642586L;

	protected DateTime testIntervalStart = null;
	protected DateTime testIntervalEnd = null;

	public void validate(Logger pLog) {
		if (!containsKey("username")) {
			pLog.error("Dukascopy user name must be set...");
			System.exit(1);
		}
		if (!containsKey("password")) {
			pLog.error("Dukascopy password must be set...");
			System.exit(1);
		}

		if (!containsKey("initialdeposit")) {
			setProperty("initialdeposit", "50000.0");
			pLog.info("No initial deposit set, using 50000 USD");
		}
		if (!containsKey("pairsToCheck")) {
			pLog.error("Pairs to check must be set...");
			System.exit(1);
		}

		DateTime now = new DateTime();
		if (!containsKey("testIntervalStart")) {
			pLog.info("No start of test interval set, setting interval to 6 months before up to now...");
			testIntervalStart = now.minusMonths(6);
			testIntervalEnd = now;
		} else {
			DateTimeFormatter fmt = DateTimeFormat
					.forPattern("dd.MM.yyyy HH:mm");
			try {
				testIntervalStart = fmt
						.parseDateTime(getProperty("testIntervalStart"));
			} catch (IllegalArgumentException e2) {
				pLog.error("Format of test interval start date wrong: "
						+ getProperty("testIntervalStart") + ", exception "
						+ e2.getMessage());
				System.exit(1);
			} catch (UnsupportedOperationException e1) {
				pLog.error("Format of test interval start date wrong: "
						+ getProperty("testIntervalStart") + ", exception "
						+ e1.getMessage());
				System.exit(1);
			}
			if (!containsKey("testIntervalEnd")) {
				pLog.info("No end of test interval set, setting to now...");
				testIntervalEnd = now;
			} else {
				try {
					testIntervalEnd = fmt
							.parseDateTime(getProperty("testIntervalEnd"));
				} catch (IllegalArgumentException e2) {
					pLog.error("Format of test interval end date wrong: "
							+ getProperty("testIntervalEnd") + ", exception "
							+ e2.getMessage());
					System.exit(1);
				} catch (UnsupportedOperationException e1) {
					pLog.error("Format of test interval end date wrong: "
							+ getProperty("testIntervalEnd") + ", exception "
							+ e1.getMessage());
					System.exit(1);
				}
			}
		}

		if (!containsKey("leverage")) {
			setProperty("leverage", "5");
			pLog.info("No leverage set, using 5x");
		} else {
			try {
				Integer.parseInt(getProperty("leverage"));
			} catch (NumberFormatException e) {
				pLog.error("Format of leverage wrong, must be integer: "
						+ getProperty("leverage") + ", exception "
						+ e.getMessage());
				System.exit(1);
			}
		}
		if (!containsKey("flexibleLeverage")) {
			setProperty("flexibleLeverage", "false");
			pLog.info("No flexible leverage rule, using none");
		} else {
			if (!getProperty("flexibleLeverage").equals("yes")
					&& !getProperty("flexibleLeverage").equals("no")) {
				pLog.error("Format of leverage rule wrong ("
						+ getProperty("flexibleLeverage")
						+ "), must be either yes or no");
				System.exit(1);
			}
		}
		if (!containsKey("reportDirectory")) {
			setProperty("reportDirectory", ".");
			pLog.info("No report directory set, using current");
		} else {
			// check that it exists...
			File reportDir = new File(getProperty("reportDirectory"));
			if (!reportDir.isDirectory() || !reportDir.canRead()
					|| !reportDir.canWrite()) {
				pLog.error("Report directory "
						+ getProperty("reportDirectory")
						+ " either doesn't exist or can't be read or write, please check...");
				System.exit(1);
			}
		}
		if (!containsKey("DB_LOG")) {
			pLog.info("No database logging set, using none");
		} else {
			if (!getProperty("DB_LOG").equals("yes")
					&& !getProperty("DB_LOG").equals("no")) {
				pLog.error("Format of database logging flag wrong ("
						+ getProperty("DB_LOG") + "), must be either yes or no");
				System.exit(1);
			}
		}

	}

	public DateTime getTestIntervalStart() {
		return testIntervalStart;
	}

	public DateTime getTestIntervalEnd() {
		return testIntervalEnd;
	}

}
