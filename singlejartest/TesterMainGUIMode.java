package singlejartest;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import jforex.strategies.FlatCascTest;
import jforex.utils.FXUtils;
import jforex.utils.log.Duration;
import jforex.utils.props.ClimberProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IChart;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFCurrency;
import com.dukascopy.api.LoadingProgressListener;
import com.dukascopy.api.Period;
import com.dukascopy.api.feed.IFeedDescriptor;
import com.dukascopy.api.feed.util.TimePeriodAggregationFeedDescriptor;
import com.dukascopy.api.system.ISystemListener;
import com.dukascopy.api.system.ITesterClient;
import com.dukascopy.api.system.ITesterClient.InterpolationMethod;
import com.dukascopy.api.system.TesterFactory;
import com.dukascopy.api.system.tester.ITesterExecution;
import com.dukascopy.api.system.tester.ITesterExecutionControl;
import com.dukascopy.api.system.tester.ITesterGui;
import com.dukascopy.api.system.tester.ITesterUserInterface;

/**
 * This small program demonstrates how to initialize Dukascopy tester and start
 * a strategy in GUI mode
 */
@SuppressWarnings("serial")
public class TesterMainGUIMode extends JFrame implements ITesterUserInterface, ITesterExecution {
	private static final Logger LOGGER = LoggerFactory.getLogger(TesterMainGUIMode.class);
	private Duration durationStats = null;
	final static ClimberProperties properties = new ClimberProperties();

	private final int frameWidth = 1000;
	private final int frameHeight = 600;
	private final int controlPanelHeight = 40;

	private JPanel currentChartPanel = null;
	private ITesterExecutionControl executionControl = null;
	private ITesterGui gui = null;

	private JPanel controlPanel = null;
	private JButton 
			pauseButton = null,	continueButton = null, cancelButton = null, 
			zoomInButton = null, zoomOutButton = null, indisButton = null;

	// url of the DEMO jnlp
	private static String jnlpUrl = "http://platform.dukascopy.com/demo/jforex.jnlp";

	private Instrument instrument = null;
	
	public TesterMainGUIMode() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
	}

	@Override
	public void setChartPanels(Map<IChart, ITesterGui> chartPanels) {
		for (Map.Entry<IChart, ITesterGui> entry : chartPanels.entrySet()) {
			IChart chart = entry.getKey();
			ITesterGui currGui = entry.getValue();
			currGui.getTesterChartController().addOHLCInformer();
			currGui.getTesterChartController().setFilter(Filter.ALL_FLATS);
			IFeedDescriptor fd = new TimePeriodAggregationFeedDescriptor(null, FXUtils.reverseTimeFrameNamesMap.get(properties.getProperty("timeFrame")), null);
			fd.setFilter(Filter.ALL_FLATS);
			currGui.getTesterChartController().setFeedDescriptor(fd);
			JPanel chartPanel = currGui.getChartPanel();
			if (chart.getFeedDescriptor().getInstrument().equals(instrument)) {
				gui = currGui;
				IFeedDescriptor fd2 = chart.getFeedDescriptor();
				setTitle(fd2.toString() + ", showing " + chart.getBarsCount() + " bars");
				addChartPanel(chartPanel);
				break;
			}
		}
	}

	@Override
	public void setExecutionControl(ITesterExecutionControl executionControl) {
		this.executionControl = executionControl;
	}

	public void startStrategy() throws Exception {
		// get the instance of the IClient interface
		final ITesterClient client = TesterFactory.getDefaultInstance();
		// set the listener that will receive system events
		client.setSystemListener(new ISystemListener() {
			@Override
			public void onStart(long processId) {
				LOGGER.info("Strategy started: " + processId);
				updateButtons();
				durationStats.addStep("Strategy start", System.currentTimeMillis());
			}

			@Override
			public void onStop(long processId) {
				LOGGER.info("Strategy stopped: " + processId);
				resetButtons();

				File reportFile = new File(properties.getProperty("reportDirectory", ".")
						+ "\\Strategy_run_report_"
						+ FXUtils.getFileTimeStamp(System.currentTimeMillis())
						+ ".html");

				try {
					client.createReport(processId, reportFile);
				} catch (Exception e) {
					LOGGER.error(e.getMessage(), e);
				}
				durationStats.addLastStep("Strategy start", System.currentTimeMillis());
				for (String currLine : durationStats.getFullReport()) 
					LOGGER.info(currLine);

				if (client.getStartedStrategies().size() == 0) {
					// Do nothing
				}
			}

			@Override
			public void onConnect() {
				LOGGER.info("Connected");
			}

			@Override
			public void onDisconnect() {
				// tester doesn't disconnect
			}
		});
		durationStats = new Duration(System.currentTimeMillis(), "Strategy execution length for instruments " 
				+ properties.getProperty("pairsToCheck", "Pairs to check not found")
				+ ", time frame " + properties.getProperty("timeFrame", "Time frame to check not found")
				+ ", for period from " + properties.getProperty("testIntervalStart", "Start time not found")
				+ " to " + properties.getProperty("testIntervalEnd", "End time not found"));
		LOGGER.info("Connecting...");
		// connect to the server using jnlp, user name and password
		// connection is needed for data downloading
		client.connect(jnlpUrl, properties.getProperty("username"),	properties.getProperty("password"));

		// wait for it to connect
		int i = 10; // wait max ten seconds
		while (i > 0 && !client.isConnected()) {
			Thread.sleep(1000);
			i--;
		}
		if (!client.isConnected()) {
			LOGGER.error("Failed to connect Dukascopy servers");
			System.exit(1);
		}
		durationStats.addStep("Connection to Dukascopy servers", System.currentTimeMillis());
		// set instruments that will be used in testing
		final Set<Instrument> instruments = new HashSet<Instrument>();
		String pair = properties.getProperty("pairsToCheck");
		instrument = Instrument.fromString(pair);
		instruments.add(instrument);
//		instrument = Instrument.DEUIDXEUR;
//		instruments.add(instrument);


		LOGGER.info("Subscribing instruments...");
		client.setCacheDirectory(new File(properties.getProperty("cachedir")));
		client.setSubscribedInstruments(instruments);
		// setting initial deposit
		client.setInitialDeposit(JFCurrency.getInstance("USD"), Double.parseDouble(properties.getProperty("initialdeposit", "100000.0")));
		client.setDataInterval(Period.ONE_MIN, null, InterpolationMethod.FOUR_TICKS, properties.getTestIntervalStart().getMillis(), properties.getTestIntervalEnd().getMillis());
		// load data
		LOGGER.info("Downloading data");
		Future<?> future = client.downloadData(null);
		// wait for downloading to complete
		future.get();
		// start the strategy
		durationStats.addStep("Pairs historical data download", System.currentTimeMillis());

		LOGGER.info("Starting strategy");

		client.startStrategy(
				//new TASituationExplorer(instrument, properties),
				// new TrendLengthExplorer(instrument, properties),
				new FlatCascTest(instrument, properties),
				new LoadingProgressListener() {
					@Override
					public void dataLoaded(long startTime, long endTime, long currentTime, String information) {
						LOGGER.info(information);
					}

					@Override
					public void loadingFinished(boolean allDataLoaded,	long startTime, long endTime, long currentTime) {
					}

					@Override
					public boolean stopJob() {
						return false;
					}
				}, this, this);
		// now it's running
	}

	/**
	 * Center a frame on the screen
	 */
	private void centerFrame() {
		Toolkit tk = Toolkit.getDefaultToolkit();
		Dimension screenSize = tk.getScreenSize();
		int screenHeight = screenSize.height;
		int screenWidth = screenSize.width;
		setSize(screenWidth / 2, screenHeight / 2);
		setLocation(screenWidth / 4, screenHeight / 4);
	}

	/**
	 * Add chart panel to the frame
	 */
	private void addChartPanel(JPanel chartPanel) {
		removecurrentChartPanel();

		this.currentChartPanel = chartPanel;
		chartPanel.setPreferredSize(new Dimension(frameWidth, frameHeight - controlPanelHeight));
		chartPanel.setMinimumSize(new Dimension(frameWidth, 200));
		chartPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
		getContentPane().add(chartPanel);
		this.validate();
		chartPanel.repaint();
	}

	/**
	 * Add buttons to start/pause/continue/cancel actions
	 */
	private void addControlPanel() {
		controlPanel = new JPanel();
		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		controlPanel.setLayout(flowLayout);
		controlPanel.setPreferredSize(new Dimension(frameWidth, controlPanelHeight));
		controlPanel.setMinimumSize(new Dimension(frameWidth, controlPanelHeight));
		controlPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, controlPanelHeight));

		pauseButton = new JButton("Pause");
		pauseButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (executionControl != null) {
					executionControl.pauseExecution();
					updateButtons();
				}
			}
		});

		continueButton = new JButton("Continue");
		continueButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (executionControl != null) {
					executionControl.continueExecution();
					updateButtons();
				}
			}
		});

		cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (executionControl != null) {
					executionControl.cancelExecution();
					updateButtons();
				}
			}
		});

		zoomInButton = new JButton("Zoom in (3x)");
		zoomInButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (executionControl != null) {
					for (int i = 0; i < 3; i++)
						gui.getTesterChartController().zoomIn();
					updateButtons();
				}
			}
		});

		zoomOutButton = new JButton("Zoom out (3x)");
		zoomOutButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (executionControl != null) {
					for (int i = 0; i < 3; i++)
						gui.getTesterChartController().zoomOut();
					updateButtons();
				}
			}
		});

		indisButton = new JButton("Show f(x)");
		indisButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (executionControl != null) {
					for (int i = 0; i < 3; i++)
						gui.getTesterChartController().addIndicators();
					updateButtons();
				}
			}
		});

		controlPanel.add(pauseButton);
		controlPanel.add(continueButton);
		controlPanel.add(cancelButton);
		controlPanel.add(zoomInButton);
		controlPanel.add(zoomOutButton);
		controlPanel.add(indisButton);
		getContentPane().add(controlPanel);

		pauseButton.setEnabled(false);
		continueButton.setEnabled(false);
		cancelButton.setEnabled(false);
	}

	private void updateButtons() {
		if (executionControl != null) {
			pauseButton.setEnabled(!executionControl.isExecutionPaused() && !executionControl.isExecutionCanceled());
			cancelButton.setEnabled(!executionControl.isExecutionCanceled());
			continueButton.setEnabled(executionControl.isExecutionPaused());
		}
	}

	private void resetButtons() {
		pauseButton.setEnabled(false);
		continueButton.setEnabled(false);
		cancelButton.setEnabled(false);
	}

	private void removecurrentChartPanel() {
		if (this.currentChartPanel != null) {
			try {
				SwingUtilities.invokeAndWait(new Runnable() {
					@Override
					public void run() {
						TesterMainGUIMode.this.getContentPane().remove(
								TesterMainGUIMode.this.currentChartPanel);
						TesterMainGUIMode.this.getContentPane().repaint();
					}
				});
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			}
		}
	}

	public void showChartFrame() {
		setSize(frameWidth, frameHeight);
		centerFrame();
		addControlPanel();
		setVisible(true);
		Runnable r = new Runnable() {
			public void run() {
				try {
					startStrategy();
				} catch (Exception e2) {
					LOGGER.error(e2.getMessage(), e2);
					e2.printStackTrace();
					resetButtons();
				}
			}
		};
		Thread t = new Thread(r);
		t.start();		
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			LOGGER.error("One argument needed (name of config file)");
			System.exit(1);
		}

		try {
			properties.load(new FileInputStream(args[0]));
		} catch (IOException e) {
			LOGGER.error("Can't open or can't read properties file " + args[0] + "...");
			System.exit(1);
		}

		properties.validate(LOGGER);
		FXUtils.setDbToUse(properties.getProperty("dbToUse"));

		TesterMainGUIMode testerMainGUI = new TesterMainGUIMode();
		testerMainGUI.showChartFrame();
	}
}