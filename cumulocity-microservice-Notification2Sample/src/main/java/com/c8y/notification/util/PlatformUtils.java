package com.c8y.notification.util;

import java.util.Iterator;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cumulocity.microservice.context.ContextService;
import com.cumulocity.microservice.context.credentials.MicroserviceCredentials;
import com.cumulocity.microservice.subscription.model.core.PlatformProperties;
import com.cumulocity.microservice.subscription.repository.application.ApplicationApi;
import com.cumulocity.rest.representation.CumulocityMediaType;
import com.cumulocity.rest.representation.application.ApplicationRepresentation;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.cumulocity.sdk.client.Platform;
import com.cumulocity.sdk.client.RestConnector;
import com.cumulocity.sdk.client.SDKException;
import com.cumulocity.sdk.client.inventory.InventoryFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

/*
 * 
 * This class makes a call to the /tenant/currentTenant endpoint.
 * It then extracts the tenant host name. This is required for the websocket connection.
 */
@Service
@Slf4j
public class PlatformUtils {
	@Autowired
	private RestConnector restConnector;

	@Autowired
	private ContextService<MicroserviceCredentials> contextService;
	@Autowired
	private PlatformProperties platformProperties;

	@Autowired
	private ApplicationApi appApi;

	@Autowired
	private Platform platform;

	public String host;
	private ObjectMapper objectMapper = new ObjectMapper();
	private static final String DOMAIN_NAME_FRAGMENT = "domainName";

	public Optional<String> getMicroserviceMOId() {
		ApplicationRepresentation applicationObj = getCurrentApplication();
		ManagedObjectRepresentation applicationMO = getAppMO(applicationObj.getId());

		if (applicationMO == null) {
			return Optional.empty();
		}
		return Optional.of(applicationMO.getId().getValue());
	}

	public ApplicationRepresentation getCurrentApplication() {
		return contextService
				.callWithinContext((MicroserviceCredentials) platformProperties.getMicroserviceBoostrapUser(), () -> {
					return appApi.currentApplication().get();
				});
	}

	/**
	 * function to get the managed object representing the current micorservice
	 * 
	 * @param appId
	 * @return managed object for application
	 */

	public ManagedObjectRepresentation getAppMO(String appId) {
		InventoryFilter filter = new InventoryFilter();
		filter.byType("c8y_Application_" + appId);
		Iterator<ManagedObjectRepresentation> i = platform.getInventoryApi().getManagedObjectsByFilter(filter).get()
				.iterator();
		ManagedObjectRepresentation mo = i.hasNext() ? i.next() : null;
		if (i.hasNext())
			log.warn("Multiple Managed Objects for Application " + appId
					+ " exist in the Inventory. Using the first one returned.");
		return mo;
	}

	public Optional<String> getHost() {
		if (this.host == null || this.host.isEmpty()) {
			log.info("Resolving domain name...");
			Optional<String> tenantStats = getCurrentTenantInfo();
			if (tenantStats.isPresent()) {
				try {
					ObjectNode node = objectMapper.readValue(tenantStats.get(), ObjectNode.class);
					host = node.get(DOMAIN_NAME_FRAGMENT).asText();
				} catch (Exception e) {
					return Optional.empty();
				}
			} else {
				log.error("Could not resolve tenant host");
			}
		}
		return Optional.of(this.host);
	}

	public Optional<String> getCurrentTenantInfo() {
		try {
			return Optional.ofNullable(restConnector.get("/tenant/currentTenant",
					CumulocityMediaType.APPLICATION_JSON_TYPE, String.class));
		} catch (final SDKException e) {
			log.error("Tenant#getCurrentTenant operation resulted in " + e.getMessage(), e);
		}
		return Optional.empty();
	}

	/*
	 * private Optional<TenantRepresentation> getCurrentTenant() { try {
	 * TenantRepresentation tenantRepresentation =
	 * restConnector.get("/tenant/currentTenant",
	 * CumulocityMediaType.APPLICATION_JSON_TYPE, TenantRepresentation.class);
	 * return Optional.ofNullable(tenantRepresentation); } catch (final SDKException
	 * e) { log.error("Tenant#getCurrentTenant operation resulted in " +
	 * e.getMessage(), e); } return Optional.empty(); }
	 */
}