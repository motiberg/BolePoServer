package com.bergerlavy.boleposerver;

import java.util.ArrayList;
import java.util.List;

import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

import com.google.appengine.api.datastore.Key;

public class Meeting {

	private Key key;
	private String creator;
	private String purpose;
	private String date;
	private String time;
	private String location;
	private List<String> participants;
	private String sharelocationtime;
	private String hash;

	public Meeting(String creator, String purpose, String date, String time, String location, List<String> participants, String sharelocationtime) {
		this.creator = creator;
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

	public String getCreator() {
		return creator;
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
