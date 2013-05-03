package com.bergerlavy.boleposerver;

import java.util.List;

import com.google.appengine.api.datastore.Key;

public class Meeting {

	private Key key;
	private String manager;
	private String purpose;
	private String date;
	private String time;
	private String location;
	private List<String> participants;
	private String sharelocationtime;
	private String hash;

	public Meeting(String manager, String purpose, String date, String time, String location, List<String> participants, String sharelocationtime) {
		this.manager = manager;
		this.purpose = purpose;
		this.date = date;
		this.time = time;
		this.location = location;
		this.participants = participants;
		this.sharelocationtime = sharelocationtime;
	}

	public Key getKey() {
		return key;
	}

	public String getManager() {
		return manager;
	}

	public String getPurpose() {
		return purpose;
	}

	public String getDate() {
		return date;
	}

	public String getTime() {
		return time;
	}

	public String getLocation() {
		return location;
	}

	public List<String> getParticipants() {
		return participants;
	}

	//	public List<String> getParticipantsHashes() {
	//		List<String> participantsHashesList = new ArrayList<String>();
	//		String tmp = participantshashes;
	//		while (tmp != "") {
	//			participantsHashesList.add(tmp.substring(0, tmp.indexOf('%')));
	//			if (tmp.indexOf('%') + 1 >= tmp.length())
	//				tmp = "";
	//			else tmp = tmp.substring(tmp.indexOf('%') + 1);
	//		}
	//		return participantsHashesList;
	//	}

	public String getSharelocationtime() {
		return sharelocationtime;
	}

	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}

	//	public void setParticipantsHashes(String hashes) {
	//		this.participantshashes = hashes;
	//	}
}
