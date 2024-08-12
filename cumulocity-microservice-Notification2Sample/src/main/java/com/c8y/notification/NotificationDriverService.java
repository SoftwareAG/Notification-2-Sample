package com.c8y.notification;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.websocket.api.UpgradeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.c8y.notification.model.API;
import com.c8y.notification.model.ConnectionStatus;
import com.c8y.notification.model.MeasurementNotificationRepresentation;
import com.c8y.notification.model.WebsocketConnection;
import com.c8y.notification.platform.SubscriptionRepository;
import com.c8y.notification.platform.TokenService;
import com.c8y.notification.service.AlarmService;
import com.c8y.notification.util.CustomQueryParam;
import com.c8y.notification.util.PlatformUtils;
import com.c8y.notification.websocket.Notification;
import com.c8y.notification.websocket.NotificationCallback;
import com.c8y.notification.websocket.jetty.JettyWebSocketClient;
import com.cumulocity.microservice.subscription.model.MicroserviceSubscriptionAddedEvent;
import com.cumulocity.microservice.subscription.model.MicroserviceSubscriptionRemovedEvent;
import com.cumulocity.microservice.subscription.service.MicroserviceSubscriptionsService;
import com.cumulocity.model.idtype.GId;
import com.cumulocity.rest.representation.inventory.ManagedObjectReferenceRepresentation;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.cumulocity.rest.representation.reliable.notification.NotificationSubscriptionFilterRepresentation;
import com.cumulocity.rest.representation.reliable.notification.NotificationSubscriptionRepresentation;
import com.cumulocity.rest.representation.reliable.notification.NotificationTokenRequestRepresentation;
import com.cumulocity.sdk.client.QueryParam;
import com.cumulocity.sdk.client.inventory.InventoryApi;
import com.cumulocity.sdk.client.inventory.InventoryFilter;
import com.cumulocity.sdk.client.inventory.ManagedObjectCollection;
import com.cumulocity.sdk.client.messaging.notifications.NotificationSubscriptionApi;
import com.cumulocity.sdk.client.messaging.notifications.NotificationSubscriptionCollection;
import com.cumulocity.sdk.client.messaging.notifications.NotificationSubscriptionFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author ARSI
 * 
 *         This class handles subscribing to measurements within the managed
 *         object context. You can also subscribe for operations,alarms,events
 *         for a device. The only change you need to make is when creating the
 *         subscription use e.g. notRep = subscribeDevice(tenantId, mor,
 *         API.OPERATION);
 * 
 *         This is the main class
 *
 */
@Component
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class NotificationDriverService {

	private final static String WEBSOCKET_URL_PATTERN = "%s/notification2/consumer/?token=%s";
	private static final String MEASUREMENT_SUBSCRIPTION_NAME = "deviceMeasurementSubscription";
	private final Properties properties;
	private List<String> devices = new ArrayList<>();
	// tenantid - websocket connection object
	private HashMap<String, WebsocketConnection> websocketConnections = new HashMap<>();
	// for reconnect thread
	private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
	// tenantid - hostname e.g. t10452223 -
	// https://psfactory.eu-latest.cumulocity.com/
	private HashMap<String, String> tenantIdToHostName = new HashMap<>();

	private static final int RECONNECT_DELAY = 120;

	private boolean reconnectFlag = true;

	private Optional<String> microserviceMOId = Optional.empty();

	@Autowired
	private final TokenService tokenService;

	@Autowired
	private final SubscriptionRepository subscriptionRepository;

	@Autowired
	private NotificationSubscriptionApi subscriptionApi;

	@Autowired
	private InventoryApi inventoryApi;

	@Autowired
	private MicroserviceSubscriptionsService subscriptionsService;

	@Autowired
	private PlatformUtils platformUtils;

	@Autowired
	AlarmService alarmService;

	private ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * 
	 * Upon microservice startup, a subscription event is received. Use this to
	 * initialize your microservice state for that tenant
	 * 
	 * @param event microservice subscription event
	 * 
	 */
	@EventListener
	public void onSubscriptionAdded(MicroserviceSubscriptionAddedEvent event) {
		try {

			if (microserviceMOId.isEmpty()) {
				microserviceMOId = platformUtils.getMicroserviceMOId();
			}
			final String tenantId = event.getCredentials().getTenant();
			log.info("Initializing notification listener for tenant: " + tenantId);

			Optional<String> temp = platformUtils.getHost();
			if (temp.isEmpty()) {
				log.error(
						"Unable to extract base url for the tenant. Will not be able to subscribe to notifications... exiting process initialization for tenant: {}.",
						tenantId);
				return;
			}
			String platformBaseURL = temp.get();

			String websocketUrl = "wss://" + platformBaseURL + ":443";
			tenantIdToHostName.put(tenantId, websocketUrl);

			log.info("WEBSOCKET_URL_Libarry: {}", properties.getWebSocketLibrary());
			log.info("Subscription added for Tenant ID: <{}> ", tenantId);
			log.info("Subscription added for API Key ID: {} ", event.getCredentials().getAppKey());
			log.info("Subscription added for user ID: {} ", event.getCredentials().getUsername());

			// subscribeAllDevices(tenantId);

			if (reconnectFlag) {
				log.info("Starting reconnect thread...");
				executorService.scheduleAtFixedRate(() -> {
					reconnect();
				}, 30, RECONNECT_DELAY, TimeUnit.SECONDS);
				reconnectFlag = false;
			}

		} catch (Exception e) {
			log.error(e.getMessage());
		}

	}

	private void connectAndReceiveNotifications(String tenantId, String token) throws Exception {

		log.info("connect and reveive notificaitons...");
		final URI webSocketUri = getWebSocketUrl(token, tenantId);

		final NotificationCallback callback = new NotificationCallback() {

			@Override
			public void onOpen(String tenantId, URI uri) {
				log.info(
						"WEBSOCKET CONNECTION ESTABLISHED: Connected to Cumulocity notification service over WebSocket "
								+ uri);
				websocketConnections.get(tenantId).setConnectionStatus(ConnectionStatus.CONNECTED);
				if (microserviceMOId.isPresent()) {
					alarmService.clearWebsocketDisconnectAlarm(microserviceMOId.get(),
							"WebsocketDisconnect" + tenantId);
				} else {
					log.warn("Unable to get microservice managed object id. Unable to post alarm");
				}
			}

			@Override
			public void onNotification(String tenantId, Notification notification) {
				log.debug("Notification received: <{}>", notification.getMessage());
				try {
					String header = notification.getNotificationHeaders().get(0);
					int start = header.indexOf("/") + 1;
					int end = header.indexOf("/", start + 1);
					String tenant = header.substring(start, end);

					// Call your service here to handle the message, An example how to extract a
					// measurement
					MeasurementNotificationRepresentation m = objectMapper.readValue(notification.getMessage(),
							MeasurementNotificationRepresentation.class);

				} catch (Exception e) {
					log.error("Error in onNotification.");
					log.error(e.getMessage());
				}
			}

			@Override
			public void onError(String tenantId, Throwable t) {

				log.error("We got an exception: " + t);
				if (t instanceof UpgradeException) {
				
						log.warn(
								"The websocket connection was not successful. Will attempt to reconnect after delay...");
						websocketConnections.get(tenantId).setConnectionStatus(ConnectionStatus.DISCONNECTED);
						if (microserviceMOId.isPresent()) {
							alarmService.createAlarm(microserviceMOId.get(), "WebsocketDisconnect" + tenantId);
						} else {
							log.warn("Unable to get microservice managed object id. Unable to post alarm");
						}
					
				}
			}

			@Override
			public void onClose(String tenantId) {
				log.info("Connection was closed for tenant:" + tenantId);
				websocketConnections.get(tenantId).setConnectionStatus(ConnectionStatus.DISCONNECTED);

				if (microserviceMOId.isPresent()) {
					alarmService.createAlarm(microserviceMOId.get(), "WebsocketDisconnect" + tenantId);
				} else {
					log.warn("Unable to get microservice managed object id. Unable to post alarm");
				}

			}
		};

		final String webSocketLibrary = properties.getWebSocketLibrary();
		if (webSocketLibrary != null && webSocketLibrary.equalsIgnoreCase("jetty")) {
			log.info("WebSocket library: Jetty");

			try {
				JettyWebSocketClient client = new JettyWebSocketClient(webSocketUri, callback, tenantId).connect();
				WebsocketConnection con = new WebsocketConnection(client, ConnectionStatus.INITIALIZING, token);
				websocketConnections.put(tenantId, con);
			} catch (Exception e) {
				log.error("An error occurred while trying to connect the WebSocket. ");

			}
		} else {
			log.error("Expected jetty library in applicaiton settings....");
			log.error("Microservice cant subscribe for notifications");
			throw new RuntimeException("Expected jetty websocket library in applicaiton settings.");
		}
	}

	/*
	 * The reconnect function is called periodically, it loops over the map and
	 * checks if any tenant is disconnected. If yes, tries to reconnect
	 */
	private void reconnect() {

		JettyWebSocketClient wsClient;
		for (String tenant : websocketConnections.keySet()) {
			WebsocketConnection connection = websocketConnections.get(tenant);
			wsClient = connection.getWsClient();
			if (wsClient != null) {
				if (wsClient.getClient().isFailed() || wsClient.getClient().isStopped()
						|| wsClient.getClient().isStopping()
						|| connection.getConnectionStatus().equals(ConnectionStatus.DISCONNECTED)) {
					log.info("Current websocket state:" + wsClient.getClient().getState());
					log.info("Disconnect detected for tenant: {} Reconnecting....", tenant);

					try {
						String token = subscriptionsService.callForTenant(tenant, () -> {
							return createToken(MEASUREMENT_SUBSCRIPTION_NAME, tenant + properties.getSubscriber());
						});
						try {
							/*
							 * An immediate reconnect might result in connection conflict on the server
							 * side. The server maintains the state of the websocket connection. upon
							 * disconnect, it might take up to 5 minutes till this state is cleared. The
							 * reconnect logic should keep trying till successful
							 */
							log.info("Wait 120 seconds before reconnect...");
							Thread.sleep(120000);
						} catch (InterruptedException ex) {
							Thread.currentThread().interrupt();
						}
						log.info("New token created...");
						connection.setConnectionStatus(ConnectionStatus.RECONNECTING);
						connectAndReceiveNotifications(tenant, token);

					} catch (Exception e) {
						log.error("Error occured in notification service: {}", e.getLocalizedMessage());
						log.info("If websocket disconnect is detected, reconnect will be initiated after 30 seconds");
					}
				}
			}
		}
	}

	public URI getWebSocketUrl(String token, String tenantId) throws URISyntaxException {
		return new URI(String.format(WEBSOCKET_URL_PATTERN, tenantIdToHostName.get(tenantId), token));
	}

	private NotificationSubscriptionRepresentation createSubscription(String deviceId) {
		final GId sourceId = GId.asGId(deviceId);
		final String subscriptionName = "measurement" + sourceId.getValue() + "subscription";

		final NotificationSubscriptionCollection notificationSubscriptionCollection = subscriptionRepository
				.getByFilter(new NotificationSubscriptionFilter().bySource(sourceId));
		final List<NotificationSubscriptionRepresentation> subscriptions = notificationSubscriptionCollection.get()
				.getSubscriptions();

		final Optional<NotificationSubscriptionRepresentation> subscriptionRepresentation = subscriptions.stream()
				.filter(subscription -> subscription.getSubscription().equals(subscriptionName)).findFirst();

		if (subscriptionRepresentation.isPresent()) {
			log.info("Reusing existing subscription <{}> on device <{}>", subscriptionName, sourceId.getValue());
			return subscriptionRepresentation.get();
		}

		log.info("Subscription does not exist. Creating ...");
		return subscriptionRepository.create(getSampleSubscriptionRepresentation(subscriptionName, deviceId));
	}

	/*
	 * delete all device/child device subscriptions for a tenant.
	 */
	public void deleteDeviceSubscriptionsBySubscriptionNameForAllDevices(String subscriptionName) {

		Optional<Iterator<ManagedObjectRepresentation>> itOpt = getAllDeviceIterator();
		if (itOpt.isPresent()) {
			Iterator<ManagedObjectRepresentation> it = itOpt.get();
			while (it.hasNext()) {
				ManagedObjectRepresentation device = it.next();
				try {
					unsubscribeDevice(subscriptionsService.getTenant(), device.getId().getValue(), subscriptionName);
					Iterator<ManagedObjectReferenceRepresentation> childDevices = device.getChildDevices().iterator();
					while (childDevices.hasNext()) {
						ManagedObjectRepresentation child = childDevices.next().getManagedObject();
						unsubscribeDevice(subscriptionsService.getTenant(), child.getId().getValue(), subscriptionName);
					}
				} catch (Exception e) {
					log.error(
							"An error occurred while trying to delete subscription for device or its child devices with id: {}",
							device.getId().getValue());
					log.error(e.getMessage());
				}

			}

		}
		log.info("Deleted device subscriptions process complete...");

	}

	public void deleteDeviceSubscriptionsBySubscriptionName(String subscriptionName, String deviceId) {
		try {
			unsubscribeDevice(subscriptionsService.getTenant(), deviceId, subscriptionName);
		} catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	private String createToken(String subscription) {
		final NotificationTokenRequestRepresentation tokenRequestRepresentation = new NotificationTokenRequestRepresentation(
				properties.getSubscriber(), subscription, 1440, false);

		return tokenService.create(tokenRequestRepresentation);
	}

	private NotificationSubscriptionRepresentation getSampleSubscriptionRepresentation(String subscriptionName,
			String sourceId) {
		final ManagedObjectRepresentation source = new ManagedObjectRepresentation();

		final NotificationSubscriptionFilterRepresentation filterRepresentation = new NotificationSubscriptionFilterRepresentation();
		filterRepresentation.setApis(List.of("measurements"));

		final NotificationSubscriptionRepresentation subscriptionRepresentation = new NotificationSubscriptionRepresentation();
		subscriptionRepresentation.setContext("mo");
		subscriptionRepresentation.setSubscription(subscriptionName);
		subscriptionRepresentation.setSource(source);
		subscriptionRepresentation.setSubscriptionFilter(filterRepresentation);

		return subscriptionRepresentation;
	}

	public List<String> getAllDevices() {

		InventoryFilter inventoryFilter = new InventoryFilter();
		inventoryFilter.byFragmentType("c8y_IsDevice");
		ManagedObjectCollection managedObjectsByFilter = inventoryApi.getManagedObjectsByFilter(inventoryFilter);
		List<ManagedObjectRepresentation> allObjects = Lists.newArrayList(managedObjectsByFilter.get().allPages());
		for (ManagedObjectRepresentation managedObjectRepresentation : allObjects) {
			devices.add(managedObjectRepresentation.getId().getValue());
		}
		return devices;
	}

	public String createToken(String subscription, String subscriber) {
		log.info("creating token...");
		final NotificationTokenRequestRepresentation tokenRequestRepresentation = new NotificationTokenRequestRepresentation(
				subscriber, subscription, 1440, false);

		return tokenService.create(tokenRequestRepresentation);
	}

	public void subscribeAllDevices(String tenantId) {
		InventoryFilter filter = new InventoryFilter();
		filter.byFragmentType("c8y_IsDevice");
		// from 1018 onwards the following filter is required since children are not
		// returned by default
		QueryParam withChildren = CustomQueryParam.WITH_CHILDREN.setValue("true").toQueryParam();
		Iterator<ManagedObjectRepresentation> deviceIt = inventoryApi.getManagedObjectsByFilter(filter)
				.get(2000, withChildren).allPages().iterator();
		Optional<NotificationSubscriptionRepresentation> notRep = Optional.empty();
		while (deviceIt.hasNext()) {
			ManagedObjectRepresentation mor = deviceIt.next();
			log.info("Found device " + mor.getName());

			notRep = subscribeDevice(tenantId, mor, API.MEASUREMENT);

			if (notRep.isEmpty()) {
				log.error("Unable to subcribe to the device and child devices for: {}", mor.getId().getValue());
				continue;
			}

			log.info("Checking for subdevices for current device...");
			Iterator<ManagedObjectReferenceRepresentation> childDevices = mor.getChildDevices().iterator();
			while (childDevices.hasNext()) {
				ManagedObjectRepresentation childMo = childDevices.next().getManagedObject();
				subscribeDevice(tenantId, childMo, API.MEASUREMENT);

			}

			break;
		}
		log.info("Created subscription for all devices...");
		log.info("Device Subscription not connected yet. Will connect...");
		String token = createToken(MEASUREMENT_SUBSCRIPTION_NAME, tenantId + properties.getSubscriber());
		try {
			connectAndReceiveNotifications(tenantId, token);
		} catch (Exception e) {
			log.error("Error on connecting to Notification Service: {}", e.getLocalizedMessage());
			throw new RuntimeException(e);
		}
	}

	public Optional<NotificationSubscriptionRepresentation> subscribeDevice(String tenantId,
			ManagedObjectRepresentation mor, API api) {
		String deviceName = mor.getName();
		log.info("Creating new Subscription for Device {} with ID {}", deviceName, mor.getId().getValue());
		return subscriptionsService.callForTenant(tenantId, () -> {
			Optional<NotificationSubscriptionRepresentation> notification = createDeviceSubscription(mor, api);
			return notification;
		});
	}

	public Optional<NotificationSubscriptionRepresentation> createDeviceSubscription(ManagedObjectRepresentation mor,
			API api) {
		final String subscriptionName = MEASUREMENT_SUBSCRIPTION_NAME;

		try {
			Iterator<NotificationSubscriptionRepresentation> subIt = subscriptionApi
					.getSubscriptionsByFilter(new NotificationSubscriptionFilter().bySource(mor.getId())).get()
					.allPages().iterator();
			NotificationSubscriptionRepresentation notification = null;
			while (subIt.hasNext()) {
				notification = subIt.next();
				if (MEASUREMENT_SUBSCRIPTION_NAME.equals(notification.getSubscription())) {
					log.info("Subscription with ID {} and Source {} already exists.", notification.getId().getValue(),
							notification.getSource().getId().getValue());
					return Optional.of(notification);
				}
			}

			log.info("Creating new subcription...");

			NotificationSubscriptionRepresentation newNotification = new NotificationSubscriptionRepresentation();
			newNotification.setSource(mor);
			final NotificationSubscriptionFilterRepresentation filterRepresentation = new NotificationSubscriptionFilterRepresentation();
			filterRepresentation.setApis(List.of(api.notificationFilter));
			newNotification.setContext("mo");
			newNotification.setSubscription(subscriptionName);
			newNotification.setSubscriptionFilter(filterRepresentation);

			newNotification = subscriptionApi.subscribe(newNotification);

			return Optional.of(newNotification);
		} catch (Exception e) {
			log.error("An error occurred while trying to create subscription for the device with id: {}",
					mor.getId().getValue());
			log.error(e.getMessage());
			return Optional.empty();

		}

	}

	/*
	 * The cleanup of the subscriptions should be done elsewhere. This microservice
	 * will be killed before the disconnect function is executed fully
	 */
	@EventListener
	public void destroy(MicroserviceSubscriptionRemovedEvent event) {
		// disconnect(event.getTenant());

		String tenant = event.getTenant();
		log.info("Microservice unsubscribed for tenant {}", tenant);
		if (websocketConnections.containsKey(tenant)) {
			String token = websocketConnections.get(tenant).getToken();
			try {
				/*
				 * To unsubscribe, you can pass any token to the unsubscribe API (even expired
				 * one's). You can generate a fresh token if needed with the matching subscriber
				 * and subscription fields and pass that along to unsubscribe API.
				 */
				tokenService.unsubcribe(token);
				websocketConnections.remove(tenant);
				tenantIdToHostName.remove(tenant);
			} catch (Exception e) {
				log.error(e.getMessage());
			}
		}
	}

	public void unsubscribeDevice(String tenantId, String deviceId, String subscriptionName) {
		subscriptionsService.runForTenant(tenantId, () -> {

			Iterator<NotificationSubscriptionRepresentation> subIt = subscriptionApi
					.getSubscriptionsByFilter(new NotificationSubscriptionFilter().bySource(new GId(deviceId))).get()
					.allPages().iterator();

			NotificationSubscriptionRepresentation notification = null;
			while (subIt.hasNext()) {
				notification = subIt.next();
				if (subscriptionName.equals(notification.getSubscription())) {
					log.info("Subscription with ID {} and Source {} found...Deleting...",
							notification.getId().getValue(), notification.getSource().getId().getValue());
					subscriptionApi.delete(notification);
				}
			}

		});

	}

	private Optional<Iterator<ManagedObjectRepresentation>> getAllDeviceIterator() {
		try {
			InventoryFilter filter = new InventoryFilter();
			filter.byFragmentType("c8y_IsDevice");
			QueryParam withChildren = CustomQueryParam.WITH_CHILDREN.setValue("true").toQueryParam();
			Iterator<ManagedObjectRepresentation> deviceIt = inventoryApi.getManagedObjectsByFilter(filter)
					.get(2000, withChildren).allPages().iterator();
			return Optional.of(deviceIt);
		} catch (Exception e) {
			log.error("An error occurred while trying to fetch device iterator");
			log.error(e.getMessage());
			return Optional.empty();
		}
	}

	/**
	 * 
	 * @param tenantId
	 * 
	 *                 When the microservice unsubsribes,the microservice
	 *                 credentials are invalidated. This means that after a few
	 *                 seconds the unsubscribe device calls will already return
	 *                 unauthorized
	 * 
	 *                 The only thing we can actually do is to remove the tenantId
	 *                 from our map, The websocket will automatically be interrupted
	 *                 and disconnected. Once removed from the map, no reconnect
	 *                 will be attempted
	 * 
	 */
	@Deprecated
	public void disconnect(String tenantId) {
		log.info("Disconnecting websocket...");
		JettyWebSocketClient client = websocketConnections.get(tenantId).getWsClient();
		if (client != null) {
			int retryCount = 0;
			int maxRetries = 3; // set the maximum number of retries here
			while (retryCount < maxRetries) {
				try {
					client.disconnect();
					websocketConnections.remove(tenantId);
					log.info("Disconnected successfully!");
					break; // if successful, exit the loop
				} catch (Exception e) {
					log.error("An error occurred while trying to disconnect websocket...");
					retryCount++;
					if (retryCount == maxRetries) {
						log.error("Failed to disconnect websocket after " + maxRetries + " attempts.");
					} else {
						log.info("Retrying disconnect attempt (" + retryCount + " of " + maxRetries + ")");
					}
				}
			}
		}
		tenantIdToHostName.remove(tenantId);

		log.info("Delete all device subscriptions....");

		Optional<Iterator<ManagedObjectRepresentation>> itOpt = getAllDeviceIterator();
		if (itOpt.isPresent()) {
			Iterator<ManagedObjectRepresentation> it = itOpt.get();
			while (it.hasNext()) {
				ManagedObjectRepresentation device = it.next();
				try {
					unsubscribeDevice(tenantId, device.getId().getValue(), MEASUREMENT_SUBSCRIPTION_NAME);
					Iterator<ManagedObjectReferenceRepresentation> childDevices = device.getChildDevices().iterator();
					while (childDevices.hasNext()) {
						ManagedObjectRepresentation child = childDevices.next().getManagedObject();
						unsubscribeDevice(tenantId, child.getId().getValue(), MEASUREMENT_SUBSCRIPTION_NAME);
					}
				} catch (Exception e) {
					log.error(
							"An error occurred while trying to delete subscription for device or its child devices with id: {}",
							device.getId().getValue());
					log.error(e.getMessage());
				}

			}

		}
		log.info("Sucessfully deleted all device subscriptions....");
	}

	public List<NotificationSubscriptionRepresentation> getAllSubscriptionsForDevice(String deviceId) {
		return subscriptionsService.callForTenant(subscriptionsService.getTenant(), () -> {

			Iterator<NotificationSubscriptionRepresentation> subIt = subscriptionApi
					.getSubscriptionsByFilter(new NotificationSubscriptionFilter().bySource(new GId(deviceId))).get()
					.allPages().iterator();
			ArrayList<NotificationSubscriptionRepresentation> list = new ArrayList();
			while (subIt.hasNext()) {
				list.add(subIt.next());
			}
			return list;
		});

	}

	public void unsubscribeSubscriber() {

		String tenant = subscriptionsService.getTenant();
		log.info("Unsubscribe subscriber for tenant: {}", tenant);

		if (websocketConnections.containsKey(tenant)) {
			String token = websocketConnections.get(tenant).getToken();
			try {
				tokenService.unsubcribe(token);
				websocketConnections.remove(tenant);
				tenantIdToHostName.remove(tenant);
			} catch (Exception e) {
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
			}
		}
	}

}
