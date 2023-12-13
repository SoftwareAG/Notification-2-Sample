package com.c8y.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
@Data
public class Properties {

	/*
	 * @Value("${example.source.id}") private String sourceId;
	 */
	@Value("${notification.websocket.url}")
	private String webSocketBaseUrl;

	@Value("${notification.subscriber}")
	private String subscriber;

	@Value("${example.websocket.library:@null}")
	private String webSocketLibrary;

	@Value("${C8Y.bootstrap.tenant}")
	private String tenantId;
}
