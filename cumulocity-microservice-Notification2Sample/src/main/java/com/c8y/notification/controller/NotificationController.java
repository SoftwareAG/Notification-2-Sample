package com.c8y.notification.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.c8y.notification.NotificationDriverService;
import com.c8y.notification.service.ExampleService;
import com.cumulocity.rest.representation.reliable.notification.NotificationSubscriptionRepresentation;

/**
 * This is an example controller.
 * 
 * @author ARSI
 *
 */

@RestController
@RequestMapping("/api")
public class NotificationController {

	private ExampleService deviceService;
	private NotificationDriverService nds;
	private static final Logger LOG = LoggerFactory.getLogger(NotificationController.class);

	@Autowired
	public NotificationController(ExampleService deviceService, NotificationDriverService notifictionDriverService) {
		this.deviceService = deviceService;
		this.nds = notifictionDriverService;
	}

	/**
	 * 
	 * @param deviceId
	 * @return Get all subscribtions for a given device
	 */
	@GetMapping(path = "/device/subscription", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> getAllSubscriptionsForDevice(@RequestParam String deviceId) {
		List<NotificationSubscriptionRepresentation> response = nds.getAllSubscriptionsForDevice(deviceId);
		return new ResponseEntity<List<NotificationSubscriptionRepresentation>>(response, HttpStatus.OK);
	}

	@PostMapping(path = "/unsubscribe/subscriber", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> unsubscribeSuscriber() {
		nds.unsubscribeSubscriber();
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	@GetMapping(path = "/hello/devices", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<String>> getAllDeviceNames() {
		List<String> response = deviceService.getAllDeviceNames();
		return new ResponseEntity<List<String>>(response, HttpStatus.OK);
	}

	/**
	 * Delete all subscriptions for a given name for all devices. Can be used when
	 * subscription is made for all devices for a given subscription name
	 */
	@DeleteMapping(value = "/devices/subscriptions")
	public ResponseEntity<?> deleteAllSubscriptions(@RequestParam String subscriptionName) {
		nds.deleteDeviceSubscriptionsBySubscriptionNameForAllDevices(subscriptionName);
		// TODO handle response
		return new ResponseEntity<>(HttpStatus.OK);
	}

	/**
	 * Delete a specific subscription for a given device
	 */
	@DeleteMapping(value = "/device/subscription")
	public ResponseEntity<?> deleteSubscrptionForDevice(@RequestParam String subscriptionName,
			@RequestParam String deviceId) {
		nds.deleteDeviceSubscriptionsBySubscriptionName(subscriptionName, deviceId);
		return new ResponseEntity<>(HttpStatus.OK);
	}

}
