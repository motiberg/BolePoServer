package com.bergerlavy.boleposerver;

import java.util.UUID;

public class Hasher {
	public static String meetingHashGenerator(Meeting meeting) {
		String uuid = UUID.randomUUID().toString();
		return "meeting_bla_bla" + uuid;
	}
	
	public static String participantHashGenerator(Participant participant) {
		return "participant_bla_bla";
	}

}
