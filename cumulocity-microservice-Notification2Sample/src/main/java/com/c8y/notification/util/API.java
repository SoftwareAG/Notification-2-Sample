package com.c8y.notification.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum API {
    ALARM("ALARM", "source.id", "alarms"),
    EVENT("EVENT", "source.id", "events"),
    MEASUREMENT("MEASUREMENT", "source.id", "measurements"),
    INVENTORY("INVENTORY", "_DEVICE_IDENT_", "managedObjects"),
    OPERATION("OPERATION", "deviceId", "operations"),
    EMPTY("NN", "nn", "nn"),

    ALL("ALL", "*", "*");

    public final String name;
    public final String identifier;
    public final String notificationFilter;

    private API(String name, String identifier, String notificationFilter) {
        this.name = name;
        this.identifier = identifier;
        this.notificationFilter = notificationFilter;
        this.aliases = Arrays.asList(name, identifier, notificationFilter);
    }

    private List<String> aliases;

    static final private Map<String, API> ALIAS_MAP = new HashMap<String, API>();

    static {
        for (API api : API.values()) {
            ALIAS_MAP.put(api.name(), api);
            for (String alias : api.aliases)
                ALIAS_MAP.put(alias, api);
        }
    }

    static public API fromString(String value) {
        API api = ALIAS_MAP.get(value);
        if (api == null)
            throw new IllegalArgumentException("Not an alias: " + value);
        return api;
    }
}