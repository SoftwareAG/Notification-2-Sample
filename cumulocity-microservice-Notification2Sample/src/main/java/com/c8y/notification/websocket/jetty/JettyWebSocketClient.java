package com.c8y.notification.websocket.jetty;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketFrame;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.common.frames.PongFrame;

import com.c8y.notification.websocket.Notification;
import com.c8y.notification.websocket.NotificationCallback;

import lombok.extern.slf4j.Slf4j;

@WebSocket
@Slf4j
public class JettyWebSocketClient {

	private final NotificationCallback callback;
	private Session session = null;
	private URI serverUri;
	private WebSocketClient client;
	private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
	private String tenantId;

	public JettyWebSocketClient(URI serverUri, NotificationCallback callback, String tenant) {
		this.serverUri = serverUri;
		this.callback = callback;
		this.tenantId = tenant;
	}

	public URI getURI() {
		return serverUri;
	}

	// connect is async. Once this functions executes successfully, does not
	// guarantee
	// successful connection is established
	public JettyWebSocketClient connect() throws Exception {
		log.info("Websocket client.connect()...");
		client = new WebSocketClient();
		client.start();
		client.connect(this, this.serverUri, new ClientUpgradeRequest());
		log.info("client.connect() complete...");
		return this;
	}

	public WebSocketClient getClient() {
		return this.client;
	}

	public Session getSession() {
		return session;
	}

	public String getTenant() {
		return tenantId;
	}

	public void disconnect() throws Exception {
		log.info("Disconnect called...");
		if (session != null && session.isOpen()) {
			log.info("Closing open session...");
			session.close();
		}

		if (client != null) {
			log.info("Current state of the client: " + client.getState());
			client.stop();
		}

	}
	/*
	 * Called once websocket connection is established. Start a thread once
	 * connection is opened. This keeps sending ping messages every 10 seconds. This
	 * will ensure that the connection is not closed when idle.
	 */

	@OnWebSocketConnect
	public void onOpen(Session session) throws URISyntaxException {
		log.info("web socket open...");

		this.session = session;
		this.callback.onOpen(tenantId, new URI("ws", session.getRemoteAddress().getHostName(), null));
		ScheduledFuture<?> result = executorService.scheduleAtFixedRate(() -> {

			try {
				ByteBuffer payload = ByteBuffer.allocate(0);
				this.session.getRemote().sendPing(payload);
				log.debug("web socket keep alive ping...");
			} catch (IOException e) {
				e.printStackTrace();
			}

		}, 10, 10, TimeUnit.SECONDS);

	}

	@OnWebSocketMessage
	public void onMessage(String message) {
		Notification notification = Notification.parse(message);
		this.callback.onNotification(tenantId, notification);
		if (notification.getAckHeader() != null) {
			try {
				session.getRemote().sendString(notification.getAckHeader()); // ack message
			} catch (Exception e) {
				log.error("Failed to ack message " + notification.getAckHeader(), e);
			}
		} else {
			log.warn("No message id found for ack");
		}
	}

	/*
	 * Shutdown the ping thread once websocket is closed.
	 */
	@OnWebSocketClose
	public void onClose(int statusCode, String reason) {
		log.info("WebSocket closed. Code:" + statusCode + ", reason: " + reason);
		if (executorService != null && !executorService.isShutdown()) {
			executorService.shutdownNow();
		}

		this.callback.onClose(tenantId);
	}

	@OnWebSocketError
	public void onError(Throwable t) {
		log.error("WebSocket error:" + t);
		this.callback.onError(tenantId, t);
	}

	@OnWebSocketFrame
	@SuppressWarnings("unused")
	public void onFrame(Frame pong) {
		if (pong instanceof PongFrame) {
			log.debug("pong message recieved");
		}
	}
}
