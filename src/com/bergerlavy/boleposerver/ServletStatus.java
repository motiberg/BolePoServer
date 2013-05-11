package com.bergerlavy.boleposerver;

public class ServletStatus {

	private String mState;
	private String mAction;
	private String mDescription;
	
	private ServletStatus(Builder builder) {
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
			if (state != null)
				this.state = state;
			return this;
		}
		
		public Builder setAction(String action) {
			if (action != null)
				this.action = action;
			return this;
		}
		
		public Builder setDescription(String description) {
			if (description != null)
				this.description = description;
			return this;
		}
		
		public ServletStatus build() {
			return new ServletStatus(this);
		}
	}
}
