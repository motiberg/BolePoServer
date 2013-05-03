package com.bergerlavy.boleposerver;

public class BolePoServerConstans {

	public static final String GCM_SERVER_ADDRESS = "https://android.googleapis.com/gcm/send";
	public static final String GCM_API_KEY = "AIzaSyB-O6Msmg13tAAwVGRYKBysXFEISXYp0MY";

	public enum ACTION {
		CREATE ("create"),
		MODIFY ("modify"),
		REMOVE ("remove"),
		RETRIEVE ("retrieve"),
		ATTEND ("attend"),
		UNATTEND ("unattend"),
		GCM_REGISTRATION ("gcm_register"),
		GCM_UNREGISTRATION ("gcm_unregister");
		
		private final String mStr;
		
		private ACTION(String str) {
			mStr = str;
		}
		
		public String getActionString() {
			return mStr;
		}
	}
	
	public enum RSVP {
		YES ("yes"),
		NO ("no"),
		MAYBE ("maybe");

		private final String mRsvp;

		private RSVP(String rsvp) {
			mRsvp = rsvp;
		}

		@Override
		public String toString() {
			return mRsvp;
		}
	}

	public enum DB_TABLE_MEETING {
		TABLE_NAME ("meeting"),
		MANAGER ("manager"),
		NAME ("name"),
		DATE ("date"),
		TIME ("time"),
		LOCATION ("location"),
		SHARE_LOCATION_TIME ("sharelocationtime"),
		HASH ("hash");

		private final String mStr;

		private DB_TABLE_MEETING(String str) {
			mStr = str;
		}

		@Override
		public String toString() {
			return mStr;
		}
	}

	public enum DB_TABLE_PARTICIPANT {

		TABLE_NAME ("participant"),
		PHONE ("phone"),
		NAME ("name"),
		RSVP ("rsvp"),
		CREDENTIALS ("credentials"),
		MEETING_KEY ("meetingkey"),
		HASH ("hash");

		private final String mStr;

		private DB_TABLE_PARTICIPANT(String str) {
			mStr = str;
		}

		@Override
		public String toString() {
			return mStr;
		}
	}

	public enum DB_TABLE_USER {

		TABLE_NAME ("user"),
		PHONE ("phone"),
		GCM_ID ("gcmid");

		private final String mStr;

		private DB_TABLE_USER(String str) {
			mStr = str;
		}

		@Override
		public String toString() {
			return mStr;
		}
	}
}
