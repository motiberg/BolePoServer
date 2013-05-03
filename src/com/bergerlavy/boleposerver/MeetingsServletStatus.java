package com.bergerlavy.boleposerver;

public class MeetingsServletStatus {

	private String mState;
	private String mAction;
	private String mDescription;
	
	private MeetingsServletStatus(Builder builder) {
		mState = builder.state;
		mAction = builder.action;
		mDescription = builder.description;
	}
	
	public String getState() {
		return mState;
	}
	
	public String getAction() {
		return mAction;
	}
	
	public String getDescription() {
		return mDescription;
	}
	
	public static class Builder {
		
		/* Optional */
		private String state;
		private String action;
		private String description;
		
		public Builder setState(String state) {
			this.state = state;
			return this;
		}
		
		public Builder setAction(String action) {
			this.action = action;
			return this;
		}
		
		public Builder setDescription(String description) {
			this.description = description;
			return this;
		}
		
		public MeetingsServletStatus build() {
			return new MeetingsServletStatus(this);
		}
	}
}
