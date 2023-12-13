package com.c8y.notification.util;

import com.cumulocity.sdk.client.Filter;
import com.cumulocity.sdk.client.Param;
import com.cumulocity.sdk.client.QueryParam;

import lombok.Getter;

public enum CustomQueryParam implements Param {
	WITH_CHILDREN("withChildren"), WITH_PARENTS("withParents"), WITH_TOTAL_PAGES("withTotalPages"),
	PAGE_SIZE("pageSize"), QUERY("query"), DEVICE_QUERY("q"), DATE_FROM("dateFrom"), STATUS("status"),
	FRAGMENT_TYPE("fragmentType"), DEVICE_ID("deviceId"), REVERT("revert"),;

	private String name;
	@Getter
	private String value;

	private CustomQueryParam(final String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return name;
	}

	public CustomQueryParam setValue(final String value) {
		this.value = value;
		return this;
	}

	public QueryParam toQueryParam() {
		return new QueryParam(this, Filter.encode(value));
	}
}
