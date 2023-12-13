package com.c8y.notification.util;

import com.c8y.notification.websocket.jetty.JettyWebSocketClient;

public class WebsocketConnection {

	private JettyWebSocketClient wsClient;
	private ConnectionStatus connectionStatus;

	public WebsocketConnection(JettyWebSocketClient wsClient, ConnectionStatus connectionStatus) {
		super();
		this.wsClient = wsClient;
		this.connectionStatus = connectionStatus;
	}

	public JettyWebSocketClient getWsClient() {
		return wsClient;
	}

	public void setWsClient(JettyWebSocketClient wsClient) {
		this.wsClient = wsClient;
	}

	public ConnectionStatus getConnectionStatus() {
		return connectionStatus;
	}

	public synchronized void setConnectionStatus(ConnectionStatus connectionStatus) {
		this.connectionStatus = connectionStatus;
	}

}
