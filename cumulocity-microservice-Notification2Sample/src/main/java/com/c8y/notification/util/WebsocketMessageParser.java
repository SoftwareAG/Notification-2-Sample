package com.c8y.notification.util;

import org.svenson.JSON;
import org.svenson.JSONParser;
import com.cumulocity.model.JSONBase;
public class WebsocketMessageParser {
    private final static JSONParser jsonParser = JSONBase.getJSONParser();
    private final static JSON json = JSON.defaultJSON();
    public static <T> T parseMessageInto(final Object data, final Class<T> targetType) {
        final String jsondata = json.forValue(data);
        if (jsondata == null) {
            throw new IllegalArgumentException("Converting RealTimeData to json returned null");
        } else {
            final T parsed = jsonParser.parse(targetType, jsondata);
            return parsed;
        }
    }
}