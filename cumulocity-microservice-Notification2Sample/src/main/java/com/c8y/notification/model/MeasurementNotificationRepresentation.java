package com.c8y.notification.model;

import java.util.LinkedHashMap;
import java.util.Map;

import com.cumulocity.model.idtype.GId;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties
public class MeasurementNotificationRepresentation {

	public MeasurementNotificationRepresentation(String self, String type, String time, GId id,
			ManagedObjectRepresentation source, Map<String, Object> attrs) {
		super();
		this.self = self;
		this.type = type;
		this.time = time;
		this.id = id;
		this.source = source;
		this.attrs = attrs;
	}

	public MeasurementNotificationRepresentation() {

	}

	private String self;
	private String type;
	private String time;
	private GId id;
	private ManagedObjectRepresentation source;
	private Map<String, Object> attrs = new LinkedHashMap<>();

	@JsonAnyGetter
	public Map<String, Object> getAttrs() {
		return attrs;
	}

	@JsonAnySetter
	public void setAttrs(String key, Object value) {
		this.attrs.put(key, value);
	}

	@JsonProperty("id")
	public void setId(GId id) {
		this.id = id;
	}

	@JsonProperty("id")
	public GId getId() {
		return id;
	}

	@JsonProperty("type")
	public void setType(String type) {
		this.type = type;
	}

	@JsonProperty("type")
	public String getType() {
		return type;
	}

	@JsonProperty("source")
	public void setSource(ManagedObjectRepresentation source) {
		this.source = source;
	}

	@JsonProperty("source")
	public ManagedObjectRepresentation getSource() {
		return source;
	}

	@JsonProperty("time")
	public String getDateTime() {
		return time;
	}

	@JsonProperty("time")
	public void setDateTime(String time) {
		this.time = time;
	}

	@JsonProperty("self")
	public void setSelf(String self) {
		this.self = self;
	}

	@JsonProperty("self")
	public String getSelf() {
		return self;
	}

}
