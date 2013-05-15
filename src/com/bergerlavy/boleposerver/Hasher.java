package com.bergerlavy.boleposerver;

import java.util.UUID;

import com.google.appengine.api.datastore.Entity;

public class Hasher {
	public static String meetingHashGenerator(Meeting meeting) {
		String uuid = UUID.randomUUID().toString();
		return "meeting_bla_bla" + uuid;
	}
	
	public static String meetingHashGenerator(Entity meeting) {
		String uuid = UUID.randomUUID().toString();
		return "meeting_bla_bla" + uuid;
	}
	
	public static String participantHashGenerator(Participant participant) {
		String uuid = UUID.randomUUID().toString();
		return "participant_bla_bla" + uuid;
	}

	public static String participantHashGenerator(Entity participant) {
		String uuid = UUID.randomUUID().toString();
		return "participant_bla_bla" + uuid;
	}
}
