package com.bergerlavy.boleposerver;

import com.google.appengine.api.datastore.Key;

public class Participant {

	private String name;
	private Key meetingid;
	private String credentials;
	private String rsvp;
	private String sharelocationstatus;
	private String hash;

	private Participant(Builder builder) {
		this.name = builder.name;
		this.meetingid = builder.meetingid;
		this.credentials = builder.credentials;
		this.rsvp = builder.rsvp;
		this.sharelocationstatus = builder.sharelocationstatus;
	}
	
	public String getName() {
		return name;
	}

	public Key getMeetingID() {
		return meetingid;
	}

	public String getCredentials() {
		return credentials;
	}

	public String getRSVP() {
		return rsvp;
	}

	public String getShareLocationStatus() {
		return sharelocationstatus;
	}

	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}
	
	public static class Builder {
		
		/* Required */
		private String name;
		private Key meetingid;
		
		/* Optional */
		private String credentials = "read";
		private String rsvp = "uknown";
		private String sharelocationstatus = "yes";
		
		public Builder(String name, Key meetingid) {
			this.name = name;
			this.meetingid = meetingid;
		}
		
		public Builder setCredentials(String credentials) {
			this.credentials = credentials;
			return this;
		}
		
		public Builder setRsvp(String rsvp) {
			this.rsvp = rsvp;
			return this;
		}
		
		public Builder setShareLocationStatus(String sharelocationstatus) {
			this.sharelocationstatus = sharelocationstatus;
			return this;
		}
		
		public Participant build() {
			return new Participant(this);
		}
	}

}
