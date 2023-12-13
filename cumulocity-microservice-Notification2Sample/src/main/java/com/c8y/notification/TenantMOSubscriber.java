package com.c8y.notification;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.websocket.api.UpgradeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.c8y.notification.model.ConnectionStatus;
import com.c8y.notification.model.WebsocketConnection;
import com.c8y.notification.platform.TokenService;
import com.c8y.notification.websocket.Notification;
import com.c8y.notification.websocket.NotificationCallback;
import com.c8y.notification.websocket.jetty.JettyWebSocketClient;
import com.cumulocity.microservice.subscription.model.MicroserviceSubscriptionRemovedEvent;
import com.cumulocity.microservice.subscription.service.MicroserviceSubscriptionsService;
import com.cumulocity.rest.representation.reliable.notification.NotificationSubscriptionFilterRepresentation;
import com.cumulocity.rest.representation.reliable.notification.NotificationSubscriptionRepresentation;
import com.cumulocity.sdk.client.messaging.notifications.NotificationSubscriptionApi;
import com.cumulocity.sdk.client.messaging.notifications.NotificationSubscriptionFilter;

/**
 * 
 * @author ARSI
 * 
 *         The subscription with tenant context provides the following
 *         possibilities: 1- get notified upon creation of new managed objects
 *         2- get all alarms from the platform 3- get all events from the
 *         platform
 */
@Component
public class TenantMOSubscriber {

	private static final Logger logger = LoggerFactory.getLogger(TenantMOSubscriber.class);
	private static final String TENANT_SUBSCRIBER = "TenantSubscriber";
	private static final String TENANT_SUBSCRIPTION = "TenantSubscriptionName";
	private HashMap<String, WebsocketConnection> websocketConnections = new HashMap<>();
	private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

	private boolean reconnectFlag = true;

	@Autowired
	private Properties properties;

	@Autowired
	TokenService tokenService;

	@Autowired
	private MicroserviceSubscriptionsService subscriptionsService;

	@Autowired
	private NotificationDriverService notificationDriverService;

	@Autowired
	private NotificationSubscriptionApi subscriptionApi;

	public void initTenantClient(String tenantId) {
		// Subscribe on Tenant do get informed when devices get

		// deleted/added

		logger.info("Initializing MO create/update/delete subscription...");

		subscribeTenant(tenantId);
	}

	private void subscribeTenant(String tenantId) {
		logger.info("Creating new Subscription for Tenant " + tenantId);
		NotificationSubscriptionRepresentation notification = createTenantSubscription();
		String tenantToken = notificationDriverService.createToken(notification.getSubscription(), TENANT_SUBSCRIBER);

		// call websocket reconnect every 30 seconds if the connection is disconnected.

		try {
			connect(tenantId, tenantToken);
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}

		if (reconnectFlag) {
			executorService.scheduleAtFixedRate(() -> {
				reconnect();
			}, 30, 30, TimeUnit.SECONDS);
			this.reconnectFlag = false;
		}

	}

	private void connect(String tenantId, String token) throws URISyntaxException {
		try {

			NotificationCallback tenantCallback = new NotificationCallback() {

				@Override
				public void onOpen(String tenantId, URI serverUri) {
					logger.info("Connected to Cumulocity notification service over WebSocket " + serverUri);
					websocketConnections.get(tenantId).setConnectionStatus(ConnectionStatus.CONNECTED);
				}

				@Override
				public void onNotification(String tenantId, Notification notification) {
					// TODO Auto-generated method stub

				}

				@Override
				public void onError(String tenantId, Throwable t) {
					if (t instanceof UpgradeException) {
						if (t.getMessage().contains("Unexpected HTTP Response Status Code: 409 Conflict")) {
							logger.warn(
									"The websocket connection was not successful. Wait till the platfrom clears previous websocket state...");
							websocketConnections.get(tenantId).setConnectionStatus(ConnectionStatus.DISCONNECTED);
						}
					}
				}

				@Override
				public void onClose(String tenantId) {
					websocketConnections.get(tenantId).setConnectionStatus(ConnectionStatus.DISCONNECTED);

				}
			};

			URI webSocketUrl = notificationDriverService.getWebSocketUrl(token, tenantId);

			final String webSocketLibrary = properties.getWebSocketLibrary();
			if (webSocketLibrary != null && webSocketLibrary.equalsIgnoreCase("jetty")) {
				logger.info("WebSocket library: Jetty");
				try {
					logger.info("Initiate websocket connect...");
					logger.info("TENANT SCOPE CONNECTION... ");

					JettyWebSocketClient client = new JettyWebSocketClient(webSocketUrl, tenantCallback, tenantId)
							.connect();
					WebsocketConnection con = new WebsocketConnection(client, ConnectionStatus.INITIALIZING, token);
					websocketConnections.put(tenantId, con);
				} catch (Exception e) {
					e.printStackTrace();
					logger.info("Websocket connection failed....");
				}

			} else {
				throw new RuntimeException(
						"No matching client found for the given library specified in the application properties");
			}
		} catch (Exception e) {
			logger.error("Error on connect to WS {}", e.getLocalizedMessage());
		}
	}

	private NotificationSubscriptionRepresentation createTenantSubscription() {

		final String subscriptionName = TENANT_SUBSCRIPTION;
		Iterator<NotificationSubscriptionRepresentation> subIt = subscriptionApi
				.getSubscriptionsByFilter(
						new NotificationSubscriptionFilter().bySubscription(subscriptionName).byContext("tenant"))
				.get().allPages().iterator();
		NotificationSubscriptionRepresentation notification = null;
		while (subIt.hasNext()) {
			notification = subIt.next();
			if (TENANT_SUBSCRIPTION.equals(notification.getSubscription())) {
				logger.info("Subscription with ID {} already exists.", notification.getId().getValue());
				return notification;
			}
		}
		if (notification == null) {
			notification = new NotificationSubscriptionRepresentation();
			final NotificationSubscriptionFilterRepresentation filterRepresentation = new NotificationSubscriptionFilterRepresentation();
			filterRepresentation.setApis(List.of("managedobjects"));
			notification.setContext("tenant");
			notification.setSubscription(subscriptionName);
			notification.setSubscriptionFilter(filterRepresentation);
			notification = subscriptionApi.subscribe(notification);
		}
		return notification;
	}

	private void reconnect() {

		JettyWebSocketClient wsClient;
		for (String tenant : websocketConnections.keySet()) {
			WebsocketConnection connection = websocketConnections.get(tenant);
			wsClient = connection.getWsClient();
			if (wsClient != null) {
				if (wsClient.getClient().isFailed() || wsClient.getClient().isStopped()
						|| wsClient.getClient().isStopping()
						|| connection.getConnectionStatus().equals(ConnectionStatus.DISCONNECTED)) {
					logger.info("Current websocket state:" + wsClient.getClient().getState());
					logger.info("Disconnect detected for tenant: {} Reconnecting....", tenant);

					try {
						String token = subscriptionsService.callForTenant(tenant, () -> {
							return notificationDriverService.createToken(TENANT_SUBSCRIPTION, TENANT_SUBSCRIBER);
						});
						try {
							/*
							 * An immediate reconnect might result in connection conflict on the server
							 * side. The server maintains the state of the websocket connection. upon
							 * disconnect, it might take up to 5 minutes till this state is cleared. The
							 * reconnect logic should keep trying till successful
							 */
							logger.info("Wait 120 seconds before reconnect...");
							Thread.sleep(120000);
						} catch (InterruptedException ex) {
							Thread.currentThread().interrupt();
						}
						logger.info("New token created...");
						connection.setConnectionStatus(ConnectionStatus.RECONNECTING);
						connect(tenant, token);

					} catch (Exception e) {
						logger.error("Error occured in notification service: {}", e.getLocalizedMessage());
						logger.info(
								"If websocket disconnect is detected, reconnect will be initiated after 30 seconds");
					}
				}
			}
		}
	}

	@EventListener
	public void destroy(MicroserviceSubscriptionRemovedEvent event) {
		// disconnect(event.getTenant());

		String tenant = event.getTenant();
		logger.info("Microservice unsubscribed for tenant {}", tenant);
		if (websocketConnections.containsKey(tenant)) {
			String token = websocketConnections.get(tenant).getToken();
			try {
				tokenService.unsubcribe(token);
				websocketConnections.remove(tenant);
			} catch (Exception e) {
				logger.error(e.getMessage());
			}
		}
	}
}
