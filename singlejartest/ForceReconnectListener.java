package singlejartest;

import org.slf4j.Logger;

import com.dukascopy.api.system.IClient;
import com.dukascopy.api.system.ISystemListener;

public class ForceReconnectListener implements ISystemListener {

	private String username, password;
	private Logger LOGGER;
	private IClient client;
	private int lightReconnects = 10;
	private String jnlpUrl;

	public ForceReconnectListener(String username, String password,
			Logger pLOGGER, IClient client, String jnlpUrl) {
		super();
		this.username = username;
		this.password = password;
		LOGGER = pLOGGER;
		this.client = client;
		this.jnlpUrl = jnlpUrl;
	}

	@Override
	public void onStart(long processId) {
		LOGGER.info("Strategy started: " + processId);
	}

	@Override
	public void onStop(long processId) {
		LOGGER.info("Strategy stopped: " + processId);
		if (client.getStartedStrategies().size() == 0) {
			System.exit(0);
		}
	}

	@Override
	public void onConnect() {
		LOGGER.info("Connected");
		lightReconnects = 10;
	}

	@Override
	public void onDisconnect() {
		LOGGER.warn("Disconnected");
		if (lightReconnects > 0) {
			client.reconnect();
			--lightReconnects;
		} else {
			try {
				// sleep for 10 seconds before attempting to reconnect
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				// ignore
			}
			try {
				client.connect(jnlpUrl, username, password);
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			}
		}
	}

}
