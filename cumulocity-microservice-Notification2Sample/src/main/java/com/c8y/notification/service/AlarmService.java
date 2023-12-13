package com.c8y.notification.service;

import java.util.Iterator;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cumulocity.model.event.CumulocityAlarmStatuses;
import com.cumulocity.model.event.CumulocitySeverities;
import com.cumulocity.model.idtype.GId;
import com.cumulocity.rest.representation.alarm.AlarmRepresentation;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.cumulocity.sdk.client.alarm.AlarmApi;
import com.cumulocity.sdk.client.alarm.AlarmFilter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AlarmService {

	@Autowired
	private AlarmApi alarmApi;

	public void createAlarm(String sourceId, String alarmType) {

		try {
			ManagedObjectRepresentation source = new ManagedObjectRepresentation();
			source.setId(GId.asGId(sourceId));
			AlarmRepresentation alarm = new AlarmRepresentation();
			alarm.setSource(source);
			alarm.setText("Websocket disconnect detected");
			alarm.setType(alarmType);
			alarm.setDateTime(new DateTime());
			alarm.setSeverity(CumulocitySeverities.CRITICAL.toString());
			alarm.setStatus(CumulocityAlarmStatuses.ACTIVE.toString());

			AlarmRepresentation resp = alarmApi.create(alarm);
			log.info(resp.toJSON());
		} catch (Exception e) {
			log.error("Unable to create alarm");
			log.error(e.getMessage());
		}

	}

	public Iterator<AlarmRepresentation> getDisconnectAlarmsIt(String sourceId, String type) {

		AlarmFilter af = new AlarmFilter().bySource(GId.asGId(sourceId)).bySeverity(CumulocitySeverities.CRITICAL)
				.byType(type);
		return alarmApi.getAlarmsByFilter(af).get().allPages().iterator();

	}

	public void clearWebsocketDisconnectAlarm(String sourceId, String alarmType) {
		try {
			Iterator<AlarmRepresentation> it = getDisconnectAlarmsIt(sourceId, alarmType);
			while (it.hasNext()) {
				AlarmRepresentation alarm = it.next();
				clearAlarm(alarm);
			}
		} catch (Exception e) {
			log.error("An error occurred while trying to clear alarm");
			log.error(e.getMessage());
		}
	}

	private void clearAlarm(AlarmRepresentation alarm) {
		try {
			alarm.setStatus(CumulocityAlarmStatuses.CLEARED.toString());
			AlarmRepresentation resp = alarmApi.update(alarm);
			log.info("Alarm cleared: " + resp.toJSON());
		} catch (Exception e) {
			log.error("Unable to update alarm");
			log.error(e.getMessage());
		}
	}

}
