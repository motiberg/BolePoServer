package com.bergerlavy.boleposerver;

import com.google.appengine.api.datastore.Key;

public class Participant {

	private String phone;
	private String name;
	private Key meetingid;
	private String credentials;
	private String rsvp;
	private String sharelocationstatus;
	private String hash;

	private Participant(Builder builder) {
		this.phone = builder.phone;
		this.name = builder.name;
		this.meetingid = builder.meetingid;
		this.credentials = builder.credentials;
		this.rsvp = builder.rsvp;
		this.sharelocationstatus = builder.sharelocationstatus;
	}
	
	public String getPhone() {
		return phone;
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
	
	@Override
	public String toString() {
		return phone + "::" + name + "::" + credentials + "::" + rsvp + "::" + hash;
	}
	
	public static class Builder {
		
		/* Required */
		private String phone;
		private Key meetingid;
		
		/* Optional */
		private String name;
		private String credentials = BolePoServerConstans.CREDENTIALS.REGULAR.toString();
		private String rsvp = BolePoServerConstans.RSVP.UNKNOWN.toString();
		private String sharelocationstatus = "yes";
		
		public Builder(String phone, Key meetingid) {
			this.phone = phone;
			this.meetingid = meetingid;
		}
		
		public Builder setName(String name) {
			if (name != null)
				this.name = name;
			return this;
		}
		
		public Builder setCredentials(String credentials) {
			if (credentials != null)
				this.credentials = credentials;
			return this;
		}
		
		public Builder setRsvp(String rsvp) {
			if (rsvp != null)
				this.rsvp = rsvp;
			return this;
		}
		
		public Builder setShareLocationStatus(String sharelocationstatus) {
			if (sharelocationstatus != null)
				this.sharelocationstatus = sharelocationstatus;
			return this;
		}
		
		public Participant build() {
			return new Participant(this);
		}
	}

}
