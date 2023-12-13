package com.c8y.notification.util;

import com.cumulocity.sdk.client.ParamSource;
import com.cumulocity.sdk.client.inventory.InventoryFilter;

public class CustomInventoryFilter extends InventoryFilter {
	
	@ParamSource
    private String query;

	public CustomInventoryFilter byQuery(String query) {
        this.query = query;
        return this;
    }

}
