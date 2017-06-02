package jforex;

import java.security.Security;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import jforex.techanalysis.Channel;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.PriceZone;
import jforex.techanalysis.SRLevel;
import jforex.techanalysis.TAEventsSource;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Trendline;
import jforex.techanalysis.Volatility;
import jforex.utils.log.FlexLogEntry;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.dukascopy.api.IContext;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

public abstract class BasicTAStrategy extends BasicStrategy {
	protected Trend trendDetector;
	protected Channel channelPosition;
	protected Momentum momentum;
	protected Volatility vola;
	protected TradeTrigger tradeTrigger;

	protected TAEventsSource eventsSource;

	protected List<SRLevel> srLevels = new ArrayList<SRLevel>();
	protected List<SRLevel> takeProfitLevels = new ArrayList<SRLevel>();
	protected List<Trendline> trendlinesToCheck = new ArrayList<Trendline>();
	protected List<PriceZone> priceZones = null;
	protected boolean dbLogging;

	public BasicTAStrategy(Properties props) {
		super(props);
	}

	public void onStartExec(IContext context) throws JFException {
		super.onStartExec(context);

		eventsSource = new TAEventsSource(history, indicators);

		trendDetector = new Trend(indicators);
		channelPosition = new Channel(history, indicators);
		tradeTrigger = new TradeTrigger(indicators, history, log);
		momentum = new Momentum(history, indicators);
		vola = new Volatility(indicators);

		setHorizontalLevels(conf, srLevels, "SRLevels");
		setHorizontalLevels(conf, takeProfitLevels, "takeProfit");
		setTrendlines(conf, trendlinesToCheck);
		setPriceZones(conf);

		Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
	}

	void setPriceZones(Properties conf) {
		String prop = conf.getProperty("priceZones");
		if (prop == null)
			return;

		priceZones = PriceZone.parseAllPriceZones(prop);
	}

	void setTrendlines(Properties input, List<Trendline> output) {
		String prop = input.getProperty("trendlines");
		if (prop == null)
			return;
		StringTokenizer st = new StringTokenizer(prop, ";");
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			// format is <name>-<startTime>,<startValue>,<endTime>,<endValue>
			// for example rectangle bottom-06.12.2011 09:00,1.33321,08.12.2011
			// 17:00,1.32878
			int sepPos = token.indexOf("-");
			if (sepPos > 0) {
				String name = token.substring(0, sepPos);
				DateTime startTime = null, endTime = null;
				double startValue = 0, endValue = 0;
				boolean correctFormats = true;
				StringTokenizer stPoints = new StringTokenizer(
						token.substring(sepPos + 1), ",");
				for (int count = 0; stPoints.hasMoreTokens() && correctFormats; count++) {
					String valueStr = stPoints.nextToken();
					// odd positions are dates, even values
					if (count == 0 || count == 2) {
						DateTimeFormatter fmt = DateTimeFormat
								.forPattern("dd.MM.yyyy HH:mm");
						try {
							if (count == 0)
								startTime = fmt.parseDateTime(valueStr);
							else
								endTime = fmt.parseDateTime(valueStr);
						} catch (IllegalArgumentException e2) {
							correctFormats = false;
							log.print("Format of trendline point date/time wrong: "
									+ valueStr
									+ ", exception "
									+ e2.getMessage());
						} catch (UnsupportedOperationException e1) {
							correctFormats = false;
							log.print("Format of trendline point date/time wrong: "
									+ valueStr
									+ ", exception "
									+ e1.getMessage());
						}
					} else {
						try {
							if (count == 1)
								startValue = Double.parseDouble(valueStr);
							else
								endValue = Double.parseDouble(valueStr);
						} catch (NumberFormatException e) {
							log.print("Trendline point value " + valueStr
									+ " can't be converted...");
						}
					}
				}
				if (correctFormats)
					output.add(new Trendline(name, startTime, endTime,
							startValue, endValue));
			}
		}

	}

	protected void setHorizontalLevels(Properties input, List<SRLevel> output,
			String level) {
		String prop = input.getProperty(level);
		if (prop == null)
			return;

		StringTokenizer st = new StringTokenizer(prop, ";");
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			// format is <name>:<value>
			int sepPos = token.indexOf(":");
			if (sepPos > 0) {
				String name = token.substring(0, sepPos);
				String valueStr = token.substring(sepPos + 1);
				try {
					double value = Double.parseDouble(valueStr);
					output.add(new SRLevel(name, value));
				} catch (NumberFormatException e) {
					log.print("SRLevel " + valueStr + " can't be converted...");
				}
			}
		}
	}

	public void sendMail(String from, String[] to, String subj, String text) {
		try {
			Properties props = new Properties();
			// Attaching to default Session, or we could start a new one
			String smtpServer = conf.getProperty("mail.smtp.host");
			if (smtpServer == null || to.length == 0)
				return;

			props.setProperty("mail.smtp.host", smtpServer);
			props.setProperty("mail.smtp.port", "465");
			props.put("mail.smtp.auth", "true");
			props.put("mail.debug", "false");
			props.put("mail.smtp.socketFactory.port", "465");
			props.put("mail.smtp.socketFactory.class",
					"javax.net.ssl.SSLSocketFactory");
			props.put("mail.smtp.socketFactory.fallback", "false");

			Session session = Session.getDefaultInstance(props,
					new javax.mail.Authenticator() {
						protected PasswordAuthentication getPasswordAuthentication() {
							return new PasswordAuthentication(
									"romanzhero@gmail.com", "taMACD84");
						}
					});
			session.setDebug(false);

			// Create a new message
			MimeMessage msg = new MimeMessage(session);
			// Set the FROM field
			msg.setFrom(new InternetAddress(from));
			// Set the TO fields
			InternetAddress[] recepients = new InternetAddress[to.length];
			for (int i = 0; i < to.length; i++) {
				recepients[i] = new InternetAddress(to[i]);
			}
			msg.setRecipients(RecipientType.TO, recepients);
			// Set the subject and body text
			msg.setSubject(subj);
			// msg.setText(text);
			// String htmlText = new String(text.replace("\n", "<br>"));
			// htmlText = htmlText.replace(" ", "&nbsp;");
			/*
			 * stari nacin
			 * 
			 * msg.setContent(htmlText, "text/html");
			 */
			Multipart mp = new MimeMultipart();
			MimeBodyPart htmlPart = new MimeBodyPart();
			htmlPart.setContent(text.replace("\n", "<br />"),
					"text/html; charset=utf-8");
			mp.addBodyPart(htmlPart);
			msg.setContent(mp);

			// Set some other header information
			msg.setHeader("PickoFX-Mailer", "PickoFX");
			msg.setSentDate(new Date());
			// Send the message
			Transport.send(msg);
		} catch (MessagingException mex) {
			System.out.println("send failed, exception: " + mex);
		}
	}

	protected void dbLogBarStats(Instrument pair, Period period,
			String timeStamp, List<FlexLogEntry> fields) {
		if (dbBarStatsExist(pair, period, timeStamp))
			dbDeleteBarStats(pair, period, timeStamp);

		DateTimeFormatter fmtIn = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm");
		String dbFieldNamesStr = new String(" (Instrument, TimeFrame, BarTime"), dbValuesStr = new String(
				" VALUES ('" + pair.toString() + "', '" + period.toString()
						+ "', " + getDateString(fmtIn.parseDateTime(timeStamp))), timeFrameID = null;
		if (period.equals(Period.THIRTY_MINS)) {
			timeFrameID = new String("30min");
		} else if (period.equals(Period.FOUR_HOURS)) {
			timeFrameID = new String("4h");
		} else if (period.equals(Period.DAILY_SUNDAY_IN_MONDAY)) {
			// no higher TF for 1d TF yet
			timeFrameID = new String("1d");
		} else
			return;

		for (int i = 0; i < fields.size(); i++) {
			FlexLogEntry field = fields.get(i);
			if (!field.getLabel().endsWith(timeFrameID))
				continue;

			dbFieldNamesStr += ", " + field.getNoTFLabel();
			String valueStr = field.getSQLValue();
			if (valueStr != null && !valueStr.equals("?"))
				dbValuesStr += ", " + field.getSQLValue();
			else
				// in case value string can not be constructed simply ignore
				// such bar !
				return;
		}
		// close the brackets
		dbFieldNamesStr += ") ";
		dbValuesStr += ")";
		String statementStr = "INSERT INTO TStatsEntry " + dbFieldNamesStr
				+ dbValuesStr;
		try {
			Statement insert = logDB.createStatement();
			insert.executeUpdate(statementStr);
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.print(statementStr);
			dbLogging = false;
			System.exit(1);
		}
	}

	protected boolean dbBarStatsExist(Instrument pair, Period p,
			String timeStamp) {
		DateTimeFormatter fmtIn = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm");
		DateTime time = fmtIn.parseDateTime(timeStamp);
		String dbWhere = new String("WHERE Instrument = '" + pair.toString()
				+ "' AND TimeFrame = '" + p.toString() + "' AND BarTime = "
				+ getDateString(time)), statementStr = "SELECT * FROM TStatsEntry "
				+ dbWhere;
		try {
			Statement qry = logDB.createStatement();
			ResultSet result = qry.executeQuery(statementStr);
			return result.next();
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.print(statementStr);
			log.close();
			System.exit(1);
		}
		return false;
	}

	private void dbDeleteBarStats(Instrument pair, Period p, String timeStamp) {
		DateTimeFormatter fmtIn = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm");
		DateTime time = fmtIn.parseDateTime(timeStamp);
		String dbWhere = new String("WHERE Instrument = '" + pair.toString()
				+ "' AND TimeFrame = '" + p.toString() + "' AND BarTime = "
				+ getDateString(time)), statementStr = "DELETE FROM TStatsEntry "
				+ dbWhere;
		try {
			Statement qry = logDB.createStatement();
			qry.executeUpdate(statementStr);
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.print(statementStr);
			log.close();
			System.exit(1);
		}
	}

	protected String getDateString(DateTime time) {
		DateTimeFormatter fmtOut = DateTimeFormat
				.forPattern("yyyy-MM-dd HH:mm");
		if (!conf.getProperty("sqlDriver", "sun.jdbc.odbc.JdbcOdbcDriver")
				.equals("com.mysql.jdbc.Driver"))
			return new String("#" + fmtOut.print(time) + "#");
		else
			return new String("'" + fmtOut.print(time) + "'");
	}

	protected boolean dbMailExists(Instrument pair, Period p, String timeStamp) {
		DateTimeFormatter fmtIn = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm");
		DateTime time = fmtIn.parseDateTime(timeStamp);
		String dbWhere = new String("WHERE Instrument = '" + pair.toString()
				+ "' AND TimeFrame = '" + p.toString() + "' AND BarTime = "
				+ getDateString(time)), statementStr = "SELECT * FROM TMailLog "
				+ dbWhere;
		try {
			Statement qry = logDB.createStatement();
			ResultSet result = qry.executeQuery(statementStr);
			return result.next();
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.print(statementStr);
			log.close();
			System.exit(1);
		}
		return false;
	}

	protected void deleteMail(Instrument pair, Period p, String timeStamp) {
		DateTimeFormatter fmtIn = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm");
		DateTime time = fmtIn.parseDateTime(timeStamp);
		String dbWhere = new String("WHERE Instrument = '" + pair.toString()
				+ "' AND TimeFrame = '" + p.toString() + "' AND BarTime = "
				+ getDateString(time)), statementStr = "DELETE FROM TMailLog "
				+ dbWhere;
		try {
			Statement qry = logDB.createStatement();
			qry.executeUpdate(statementStr);
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.print(statementStr);
			log.close();
			System.exit(1);
		}
	}

	protected void dbLogSignalStats(Instrument pair, Period p,
			String timeStamp, String mailContent) {
		if (conf.getProperty("mailToDBOnly", "no").equals("yes")) {
			if (dbMailExists(pair, p, timeStamp))
				deleteMail(pair, p, timeStamp);
			String dbFieldNamesStr = new String(
					" (Instrument, TimeFrame, BarTime, MailContent)"), dbValuesStr = new String(
					" VALUES ('" + pair.toString() + "', '" + p.toString()
							+ "', '" + timeStamp + "', '"
							+ mailContent.replace("\n", "<br>") + "')");
			String statementStr = "INSERT INTO TMailLog " + dbFieldNamesStr
					+ dbValuesStr;
			try {
				Statement insert = logDB.createStatement();
				insert.executeUpdate(statementStr);
			} catch (SQLException ex) {
				log.print("Log database problem: " + ex.getMessage());
				log.print(statementStr);
				log.close();
				System.exit(1);
			}
		}
	}

}