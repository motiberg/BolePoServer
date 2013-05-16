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
		DECLINE ("decline"),
		REPLACE_MANAGER ("replace_manager"),
		REMOVE_PARTICIPANT ("remove_participant"),
		GCM_REGISTRATION ("gcm_register"),
		GCM_UNREGISTRATION ("gcm_unregister"),
		GCM_CHECK_REGISTRATION ("gcm_check_registration");
		
		private final String mStr;
		
		private ACTION(String str) {
			mStr = str;
		}
		
		@Override
		public String toString() {
			return mStr;
		}
		
		public static ACTION getEnum(String str) {
			for (ACTION action : values())
				if (action.toString().equalsIgnoreCase(str))
					return action;
			throw new IllegalArgumentException();
		}
	}
	
	public enum RSVP {
		YES ("yes"),
		NO ("no"),
		MAYBE ("maybe"),
		UNKNOWN ("unknown"),
		DECLINE ("decline");

		private final String mRsvp;

		private RSVP(String rsvp) {
			mRsvp = rsvp;
		}

		@Override
		public String toString() {
			return mRsvp;
		}
	}
	
	public enum CREDENTIALS {
		REGULAR ("regular"),
		MANAGER ("manager");
		
		private final String mStr;

		private CREDENTIALS(String str) {
			mStr = str;
		}

		@Override
		public String toString() {
			return mStr;
		}
		
		public static CREDENTIALS getEnum(String str) {
			for (CREDENTIALS c : values())
				if (c.toString().equalsIgnoreCase(str))
					return c;
			throw new IllegalArgumentException();
		}
	}
	
	public enum GCM_NOTIFICATION {
		NEW_MEETING ("new_meeting"),
		UPDATED_MEETING ("updated_meeting"),
		MEETING_CANCLED ("meeting_cancled"),
		NEW_MANAGER ("new_manager"),
		REMOVED_FROM_MEETING ("removed_from_meeting"),
		PARTICIPANT_ATTENDED ("participant_attended"),
		PARTICIPANT_DECLINED ("participant_declined");
		
		private final String mStr;

		private GCM_NOTIFICATION(String str) {
			mStr = str;
		}

		@Override
		public String toString() {
			return mStr;
		}
	}
	
	public enum GCM_DATA {
		MESSAGE_TYPE ("bolepo_message_type"),
		MEETING_NAME ("meeting_name"),
		MEETING_MANAGER ("meeting_manager"),
		MEETING_DATE ("meeting_date"),
		MEETING_TIME ("meeting_time"),
		MEETING_LOCATION ("meeting_location"),
		MEETING_SHARE_LOCATION_TIME ("meeting_share_location_time"),
		MEETING_PARTICIPANTS_COUNT ("meeting_participants_count"),
		MEETING_HASH ("meeting_hash"),
		PARTICIPANT_DATA ("participant_data"),
		PARTICIPANT_ATTENDANCE ("participant_attends"),
		PARTICIPANT_DECLINING ("participant_declining"),
		REMOVED_BY_MANAGER ("removed_by_manager");
//		PARTICIPANT_PHONE ("participant_phone"),
//		PARTICIPANT_NAME ("participant_name"),
//		PARTICIPANT_RSVP ("participant_rsvp"),
//		PARTICIPANT_CREDENTIALS ("participant_credentials"),
//		PARTICIPANT_HASH ("participant_hash");
		
		private final String mStr;

		private GCM_DATA(String str) {
			mStr = str;
		}

		@Override
		public String toString() {
			return mStr;
		}
	}

	public enum DB_TABLE_MEETING {
		TABLE_NAME ("Meeting"),
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

		TABLE_NAME ("Participant"),
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

		TABLE_NAME ("User"),
		PHONE ("phone"),
		GCM_ID ("gcmid"),
		TIME ("time");

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
