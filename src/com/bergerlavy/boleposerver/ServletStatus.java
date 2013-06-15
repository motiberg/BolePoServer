package com.bergerlavy.boleposerver;

public class ServletStatus {

	private String mAction;
	private String mDescription;
	private int mFailureCode;
	
	private ServletStatus(Builder builder) {
		mAction = builder.action;
		mDescription = builder.description;
		mFailureCode = builder.failureCode;
	}
	
	public String getAction() {
		return mAction;
	}
	
	public String getDescription() {
		return mDescription;
	}
	
	public int getFailureCode() {
		return mFailureCode;
	}
	
	public static class Builder {
		
		/* Optional */
		private String action;
		private String description;
		
		/* hidden */
		private int failureCode = 0;
		
		public Builder failure(int failureCode) {
			this.failureCode = failureCode;
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
