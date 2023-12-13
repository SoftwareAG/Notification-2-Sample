package com.c8y.notification.model;

import com.c8y.notification.websocket.jetty.JettyWebSocketClient;

/*
 * a wrapper class to handle connection status
 * It is difficult to efficiently/reliably manage webscoket connection state directly in jetty.
 */
public class WebsocketConnection {

	private JettyWebSocketClient wsClient;
	private ConnectionStatus connectionStatus;
	private String token;

	public WebsocketConnection(JettyWebSocketClient wsClient, ConnectionStatus connectionStatus, String token) {
		super();
		this.wsClient = wsClient;
		this.connectionStatus = connectionStatus;
		this.token = token;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
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
