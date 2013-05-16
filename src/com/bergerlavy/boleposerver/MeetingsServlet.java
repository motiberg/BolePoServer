package com.bergerlavy.boleposerver;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.bergerlavy.boleposerver.BolePoServerConstans.GCM_NOTIFICATION;
import com.google.android.gcm.server.Message;
import com.google.android.gcm.server.MulticastResult;
import com.google.android.gcm.server.Sender;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.PreparedQuery.TooManyResultsException;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.CompositeFilter;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;

/************************************************************************************************/
/*											TODO LIST											*/
/************************************************************************************************/
/* in DECLINE change the RSVP from NO to DECLINE                                                */

@SuppressWarnings("serial")
public class MeetingsServlet extends HttpServlet {

	private DatastoreService mDatastore;
	private String mHash;


	private boolean hasModifyingCredentials(String participantPhone, String hash) {
		Filter hashFilter = new FilterPredicate(BolePoServerConstans.DB_TABLE_MEETING.HASH.toString(), FilterOperator.EQUAL, hash);
		Query qry = new Query(BolePoServerConstans.DB_TABLE_MEETING.TABLE_NAME.toString()).setFilter(hashFilter);
		PreparedQuery results = mDatastore.prepare(qry);
		Entity meetingEntity = results.asSingleEntity();

		/* if no records found matching to the criteria,
		 * exiting with false since there isn't a meeting associated with the hash */
		if (meetingEntity == null)
			return false;

		Key meetingKey = meetingEntity.getKey();

		Filter keyFilter = new FilterPredicate(BolePoServerConstans.DB_TABLE_PARTICIPANT.MEETING_KEY.toString(), FilterOperator.EQUAL, meetingKey);
		Filter phoneFilter = new FilterPredicate(BolePoServerConstans.DB_TABLE_PARTICIPANT.PHONE.toString(), FilterOperator.EQUAL, participantPhone);
		qry = new Query(BolePoServerConstans.DB_TABLE_PARTICIPANT.TABLE_NAME.toString()).setFilter(keyFilter).setFilter(phoneFilter);
		results = mDatastore.prepare(qry);

		Entity participantEntity = results.asSingleEntity();

		/* if no records found matching to the criteria,
		 * exiting with false since there isn't a participant with the given phone number associated with the meeting */
		if (participantEntity == null)
			return false;

		/* getting the participant's credentials */
		String credentials = (String) participantEntity.getProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.CREDENTIALS.toString());

		/* checking if the participant's credentials are of a manager */
		if (BolePoServerConstans.CREDENTIALS.getEnum(credentials) == BolePoServerConstans.CREDENTIALS.MANAGER)
			return true;
		return false;
	}

	private Participant entityToParticipant(Entity e) {
		if (e.getKind().equals(BolePoServerConstans.DB_TABLE_PARTICIPANT.TABLE_NAME.toString())) {
			return new Participant.Builder((String) e.getProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.PHONE.toString()),
					(Key) e.getProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.MEETING_KEY.toString()))
			.setName((String) e.getProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.NAME.toString()))
			.setCredentials((String) e.getProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.CREDENTIALS.toString()))
			.setRsvp((String) e.getProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.RSVP.toString()))
			.build();
		}
		throw new IllegalArgumentException();
	}

	private Key storeMeeting(String actionmakerphone, String name, String date, String time, String location, List<String> participants, String sharelocationtime) {
		/* creating meeting instance with the meeting details as given by the user */
		Meeting newMeeting = new Meeting(actionmakerphone, name, date, time, location, participants, sharelocationtime);

		//TODO make the hash calculated on the unique key in DB for "random" element

		Entity meeting = new Entity(BolePoServerConstans.DB_TABLE_MEETING.TABLE_NAME.toString());
		meeting.setProperty(BolePoServerConstans.DB_TABLE_MEETING.NAME.toString(), name);
		meeting.setProperty(BolePoServerConstans.DB_TABLE_MEETING.MANAGER.toString(), actionmakerphone);
		meeting.setProperty(BolePoServerConstans.DB_TABLE_MEETING.DATE.toString(), date);
		meeting.setProperty(BolePoServerConstans.DB_TABLE_MEETING.TIME.toString(), time);
		meeting.setProperty(BolePoServerConstans.DB_TABLE_MEETING.LOCATION.toString(), location);
		meeting.setProperty(BolePoServerConstans.DB_TABLE_MEETING.SHARE_LOCATION_TIME.toString(), sharelocationtime);
		meeting.setProperty(BolePoServerConstans.DB_TABLE_MEETING.HASH.toString(), Hasher.meetingHashGenerator(newMeeting));
		return mDatastore.put(meeting);
	}

	private void storeParticipants(List<Participant> participants) {

		for (Participant p : participants) {
			Entity participant = new Entity(BolePoServerConstans.DB_TABLE_PARTICIPANT.TABLE_NAME.toString());
			participant.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.MEETING_KEY.toString(), p.getMeetingID());
			participant.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.PHONE.toString(), p.getPhone());
			participant.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.NAME.toString(), p.getName());
			participant.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.RSVP.toString(), p.getRSVP());
			participant.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.CREDENTIALS.toString(), p.getCredentials());
			participant.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.HASH.toString(), Hasher.participantHashGenerator(p));
			mDatastore.put(participant);
		}
	}

	private List<Participant> updateParticipants(Key meetingKey, List<Participant> participants) {
		Filter participantsOfMeetingFilter = new FilterPredicate(BolePoServerConstans.DB_TABLE_PARTICIPANT.MEETING_KEY.toString(), FilterOperator.EQUAL, meetingKey);
		Query qry = new Query(BolePoServerConstans.DB_TABLE_PARTICIPANT.TABLE_NAME.toString()).setFilter(participantsOfMeetingFilter);
		PreparedQuery results = mDatastore.prepare(qry);

		/* saves the participants that are no longer invited to the meeting with the key that received as a parameter */
		List<Participant> oldParticipants = new ArrayList<Participant>();

		boolean exists;
		/* for every participant, checking if he already invited to the meeting, even after the modification of the meeting */
		for (Participant p : participants) {
			exists = false;
			for (Entity e : results.asIterable()) {
				if (p.getPhone().equals(e.getProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.PHONE.toString()))) {
					exists = true;
					break;
				}
			}
			/* if the participant is new to the meeting, than adding it */
			if (!exists) {
				Entity entity = new Entity(BolePoServerConstans.DB_TABLE_PARTICIPANT.TABLE_NAME.toString());
				entity.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.MEETING_KEY.toString(), meetingKey);
				entity.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.PHONE.toString(), p.getPhone());
				entity.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.NAME.toString(), p.getName());
				entity.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.CREDENTIALS.toString(), p.getCredentials());
				entity.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.RSVP.toString(), p.getRSVP());
				entity.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.HASH.toString(), Hasher.participantHashGenerator(p));
				mDatastore.put(entity);
			}
		}

		/* for every participant that has been exist before the modification, checking if he still invited to the meeting */
		for (Entity e : results.asIterable()) {
			exists = false;
			for (Participant p : participants) {
				if (p.getPhone().equals(e.getProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.PHONE.toString()))) {
					exists = true;
					break;
				}
			}
			/* if the participant is not included in the modified participants list, removing him */
			if (!exists) {
				oldParticipants.add(convertEntityToParticipant(e));
				mDatastore.delete(e.getKey());
			}
		}
		return oldParticipants;
	}

	private Entity modifyAndSave(Entity e, String actionmaker, String name, String date, String time, String location, List<String> participants, String sharelocationtime) {
		Meeting modifiedMeeting = new Meeting(actionmaker, name, date, time, location, participants, sharelocationtime);
		String meetingHash = Hasher.meetingHashGenerator(modifiedMeeting);

		e.setProperty(BolePoServerConstans.DB_TABLE_MEETING.NAME.toString(), name);
		e.setProperty(BolePoServerConstans.DB_TABLE_MEETING.MANAGER.toString(), actionmaker);
		e.setProperty(BolePoServerConstans.DB_TABLE_MEETING.DATE.toString(), date);
		e.setProperty(BolePoServerConstans.DB_TABLE_MEETING.TIME.toString(), time);
		e.setProperty(BolePoServerConstans.DB_TABLE_MEETING.LOCATION.toString(), location);
		e.setProperty(BolePoServerConstans.DB_TABLE_MEETING.SHARE_LOCATION_TIME.toString(), sharelocationtime);
		e.setProperty(BolePoServerConstans.DB_TABLE_MEETING.HASH.toString(), meetingHash);
		mDatastore.put(e);

		//		//TODO filter for the meeting key
		//		Query participantsQry = new Query(BolePoServerConstans.DB_TABLE_PARTICIPANT.TABLE_NAME.toString());
		//		PreparedQuery pq = mDatastore.prepare(participantsQry);
		//
		//		if (participants != null) {
		//			for (Entity pe : pq.asIterable()) {
		//				boolean found = false;
		//				for (String s : participants) {
		//					if (((String) pe.getProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.PHONE.toString())).equals(s)) {
		//						found = true;
		//						break;
		//					}
		//				}
		//				/* if the participant entity name wasn't found in the participants list, that means
		//				 * that the participant is no longer invited to the meeting and it must be removed 
		//				 * from the database. */
		//				if (!found)
		//					mDatastore.delete(pe.getKey());
		//			}
		//
		//			for (String s : participants) {
		//				boolean found = false;
		//				for (Entity pe : pq.asIterable()) {
		//					if (((String) pe.getProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.PHONE.toString())).equals(s)) {
		//						found = true;
		//						break;
		//					}
		//				}
		//				if (!found) {
		//					Entity participantEntity = new Entity(BolePoServerConstans.DB_TABLE_PARTICIPANT.TABLE_NAME.toString());
		//					participantEntity.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.MEETING_KEY.toString(), e.getKey());
		//					participantEntity.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.PHONE.toString(), s);
		//					participantEntity.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.NAME.toString(), null);
		//					participantEntity.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.RSVP.toString(), null);
		//					participantEntity.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.CREDENTIALS.toString(), null);
		//					mDatastore.put(participantEntity);
		//				}
		//			}
		//
		//		}
		return e;
	}

	private HashMap<String, String> meetingData(Meeting m) {
		HashMap<String, String> values = new HashMap<String, String>();

		return values;
	}

	/**
	 * Notifies the participants of a meeting about a certain action using the GCM service.
	 * @param actionMakerPhone the user who made the action
	 * @param gcmNotification the action to be notified about
	 * @param participants list of participants to be notified
	 * @param meetingKey key of the meeting that an action has been made as a part of it
	 * @return 3-element array of integers representing the number of successful message delivers,
	 *  the number of failed message delivers and the total number of messages that have been planned to be delivered
	 */
	private int[] notifyParticipants(String actionMakerPhone, BolePoServerConstans.GCM_NOTIFICATION gcmNotification, List<Participant> participants, Key meetingKey) {
		List<String> gcmIds = new ArrayList<String>();
		for (Participant p : participants) {
			/* not notifying the action maker about the action he did */
			if (p.getPhone().equals(actionMakerPhone))
				continue;

			/* filtering to get the entity of the user with the given phone number */
			Filter usrFilter = new FilterPredicate(BolePoServerConstans.DB_TABLE_USER.PHONE.toString(), Query.FilterOperator.EQUAL, p.getPhone());

			/* defining the query instance to be on the users table using the filter defined above */
			Query qry = new Query(BolePoServerConstans.DB_TABLE_USER.TABLE_NAME.toString()).setFilter(usrFilter);

			/* running the query in the datastore */
			PreparedQuery pq = mDatastore.prepare(qry);

			/* getting the one and only entity result of the query */
			Entity userEntity = pq.asSingleEntity();

			/* getting the user's GCM ID */
			String userGcmId = (String) userEntity.getProperty(BolePoServerConstans.DB_TABLE_USER.GCM_ID.toString());

			/* if the user has a GCM ID, then adding it to the list of the users that will be notified for the new meeting */
			if (userGcmId != null)
				gcmIds.add(userGcmId);
			else {
				/* an invited user does not register to the GCM service */
				//TODO consider what to do in this case, maybe notifying the manager of the meeting about this
			}

		}

		Sender sender = new Sender(BolePoServerConstans.GCM_API_KEY);

		/* building the message with pairs of key-value according to the operation performed */
		Message.Builder messageBuilder = new Message.Builder().addData(BolePoServerConstans.GCM_DATA.MESSAGE_TYPE.toString(), gcmNotification.toString());

		Filter keyFltr = new FilterPredicate(Entity.KEY_RESERVED_PROPERTY, FilterOperator.EQUAL, meetingKey);
		Query qry = new Query(BolePoServerConstans.DB_TABLE_MEETING.TABLE_NAME.toString()).setFilter(keyFltr);
		PreparedQuery qryResult = mDatastore.prepare(qry);
		Entity meeting = qryResult.asSingleEntity();
		if (meeting == null)
			return new int[] { -1, -1 ,-1 };

		switch (gcmNotification) {
		case MEETING_CANCLED:
			break;
		case NEW_MANAGER:
			break;
		case NEW_MEETING:

			messageBuilder.addData(BolePoServerConstans.GCM_DATA.MEETING_NAME.toString(), (String) meeting.getProperty(BolePoServerConstans.DB_TABLE_MEETING.NAME.toString()))
			.addData(BolePoServerConstans.GCM_DATA.MEETING_DATE.toString(), (String) meeting.getProperty(BolePoServerConstans.DB_TABLE_MEETING.DATE.toString()))
			.addData(BolePoServerConstans.GCM_DATA.MEETING_TIME.toString(), (String) meeting.getProperty(BolePoServerConstans.DB_TABLE_MEETING.TIME.toString()))
			.addData(BolePoServerConstans.GCM_DATA.MEETING_LOCATION.toString(), (String) meeting.getProperty(BolePoServerConstans.DB_TABLE_MEETING.LOCATION.toString()))
			.addData(BolePoServerConstans.GCM_DATA.MEETING_MANAGER.toString(), (String) meeting.getProperty(BolePoServerConstans.DB_TABLE_MEETING.MANAGER.toString()))
			.addData(BolePoServerConstans.GCM_DATA.MEETING_SHARE_LOCATION_TIME.toString(), (String) meeting.getProperty(BolePoServerConstans.DB_TABLE_MEETING.SHARE_LOCATION_TIME.toString()))
			.addData(BolePoServerConstans.GCM_DATA.MEETING_PARTICIPANTS_COUNT.toString(), Integer.toString(participants.size()))
			.addData(BolePoServerConstans.GCM_DATA.MEETING_HASH.toString(), (String) meeting.getProperty(BolePoServerConstans.DB_TABLE_MEETING.HASH.toString()));
			int counter = 0;
			for (Participant p : participants) {
				messageBuilder.addData(BolePoServerConstans.GCM_DATA.PARTICIPANT_DATA.toString() + counter++, p.toString());
			}
			break;
		case UPDATED_MEETING:
			messageBuilder.addData("meeting_hash", "blaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
			break;
		case PARTICIPANT_ATTENDED:
			messageBuilder
			.addData(BolePoServerConstans.GCM_DATA.PARTICIPANT_ATTENDANCE.toString(), actionMakerPhone)
			.addData(BolePoServerConstans.GCM_DATA.MEETING_HASH.toString(), (String) meeting.getProperty(BolePoServerConstans.DB_TABLE_MEETING.HASH.toString()))
			.addData(BolePoServerConstans.GCM_DATA.MEETING_NAME.toString(), (String) meeting.getProperty(BolePoServerConstans.DB_TABLE_MEETING.NAME.toString()));
			break;
		case PARTICIPANT_DECLINED:
			messageBuilder
			.addData(BolePoServerConstans.GCM_DATA.PARTICIPANT_DECLINING.toString(), actionMakerPhone)
			.addData(BolePoServerConstans.GCM_DATA.MEETING_HASH.toString(), (String) meeting.getProperty(BolePoServerConstans.DB_TABLE_MEETING.HASH.toString()))
			.addData(BolePoServerConstans.GCM_DATA.MEETING_NAME.toString(), (String) meeting.getProperty(BolePoServerConstans.DB_TABLE_MEETING.NAME.toString()));
			break;
		case REMOVED_FROM_MEETING:
			messageBuilder
			.addData(BolePoServerConstans.GCM_DATA.MEETING_HASH.toString(), (String) meeting.getProperty(BolePoServerConstans.DB_TABLE_MEETING.HASH.toString()))
			.addData(BolePoServerConstans.GCM_DATA.MEETING_NAME.toString(), (String) meeting.getProperty(BolePoServerConstans.DB_TABLE_MEETING.NAME.toString()));
			
			//TODO consider sending informative message about the reason that the users have been removed
			break;
		default:
			break;
		}



		MulticastResult result = null;
		try {
			/* sending the message to the list of users with maximum of 5 tries in case of failure */
			result = sender.send(messageBuilder.build(), gcmIds, 5);
		} catch (IOException e) {
			return new int[] { -1, -1, -1 };
		}
		return new int[] { result.getSuccess(), result.getFailure(), gcmIds.size() };
	}

	private Key getMeetingKeyByMeetingHash(String meetingHash) {
		Filter hashFltr = new FilterPredicate(BolePoServerConstans.DB_TABLE_MEETING.HASH.toString(), FilterOperator.EQUAL, meetingHash);

		Query meetingQry = new Query(BolePoServerConstans.DB_TABLE_MEETING.TABLE_NAME.toString()).setFilter(hashFltr);

		PreparedQuery pq = mDatastore.prepare(meetingQry);

		Entity meeting = pq.asSingleEntity();

		return meeting.getKey();
	}

	private Entity getParticipantEntityByPhone(String actionMakerPhone, Key meetingkey) {
		CompositeFilter c = new CompositeFilter(CompositeFilterOperator.AND, Arrays.<Filter>asList(new FilterPredicate(BolePoServerConstans.DB_TABLE_PARTICIPANT.PHONE.toString(), FilterOperator.EQUAL, actionMakerPhone),
				new FilterPredicate(BolePoServerConstans.DB_TABLE_PARTICIPANT.MEETING_KEY.toString(), FilterOperator.EQUAL, meetingkey)));
		Query usrQry = new Query(BolePoServerConstans.DB_TABLE_PARTICIPANT.TABLE_NAME.toString()).setFilter(c);

		PreparedQuery pq = mDatastore.prepare(usrQry);

		return pq.asSingleEntity();
	}

	private Participant getMeetingManager(Key meetingKey) {
		Filter meetingKeyFilter = new FilterPredicate(Entity.KEY_RESERVED_PROPERTY, FilterOperator.EQUAL, meetingKey);
		Query qry = new Query(BolePoServerConstans.DB_TABLE_MEETING.TABLE_NAME.toString()).setFilter(meetingKeyFilter);
		PreparedQuery result = mDatastore.prepare(qry);
		Entity meetingEntity = result.asSingleEntity();
		String meetingManager = (String) meetingEntity.getProperty(BolePoServerConstans.DB_TABLE_MEETING.MANAGER.toString());

		Entity managerEntity = getParticipantEntityByPhone(meetingManager, meetingKey);
		return convertEntityToParticipant(managerEntity);
	}

	private Participant convertEntityToParticipant(Entity e) {
		return new Participant.Builder((String) e.getProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.PHONE.toString()),
				(Key) e.getProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.MEETING_KEY.toString()))
		.setName((String) e.getProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.NAME.toString()))
		.setCredentials((String) e.getProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.CREDENTIALS.toString()))
		.setRsvp((String) e.getProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.RSVP.toString()))
		.build();
	}

	private void storeParticipantAttendingStatus(Entity participant, BolePoServerConstans.RSVP rsvp) {
		participant.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.RSVP.toString(), rsvp.toString());
		mDatastore.put(participant);
	}

	private void printNotificationResultsToResponse(PrintWriter out, int[] notificationResults) {
		out.println("<Delivered>" + notificationResults[0] +  "</Delivered>");
		out.println("<FailDelivered>" + notificationResults[1] +  "</FailDelivered>");
		out.println("<Total>" + notificationResults[2] +  "</Total>");
	}

	private String getMeetingHash(Key meetingKey) {
		Filter keyFilter = new FilterPredicate(Entity.KEY_RESERVED_PROPERTY, FilterOperator.EQUAL, meetingKey);
		Query qry = new Query(BolePoServerConstans.DB_TABLE_MEETING.TABLE_NAME.toString()).setFilter(keyFilter);
		PreparedQuery result = mDatastore.prepare(qry);
		return (String) result.asSingleEntity().getProperty(BolePoServerConstans.DB_TABLE_MEETING.HASH.toString());
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		mDatastore = DatastoreServiceFactory.getDatastoreService();

		String actionmakerphone = null;
		String hashval = null;
		String name = null;
		String date = null;
		String time = null;
		String location = null;
		String sharelocationtime = null;
		String participantsnumber = null;
		String meetingHash = null;
		String oldManagerHash = null;
		String newManagerHash = null;
		String participantHash = null;
		String attendedUser = null;

		ServletStatus.Builder statusBuilder = new ServletStatus.Builder();

		/* preparing the response in XML format */
		resp.setContentType("text/xml");
		PrintWriter out = resp.getWriter();
		out.println("<?xml version=\"1.0\"?>");
		out.println("<Response>");
		List<String> participantsPhones = new ArrayList<String>();
		BolePoServerConstans.ACTION action = null;
		try {
			action = BolePoServerConstans.ACTION.getEnum(req.getParameter("action"));
		}
		catch (IllegalArgumentException e) {
			BolePoServerMisc.printStatusToResponse(out, statusBuilder.setState("error").setDescription("unknown action: " + req.getParameter("action")).build());
			out.println("</Response>");
			return;
		}

		actionmakerphone = req.getParameter("actionmaker");
		hashval = req.getParameter("hash");
		name = req.getParameter("name");
		date = req.getParameter("date");
		time = req.getParameter("time");
		location = req.getParameter("location");
		sharelocationtime = req.getParameter("sharelocationtime");
		participantsnumber = req.getParameter("participantsnumber");
		meetingHash = req.getParameter("meeting_hash");
		oldManagerHash = req.getParameter("old_manager_hash");
		newManagerHash = req.getParameter("new_manager_hash");
		participantHash = req.getParameter("participant_hash");
		attendedUser = req.getParameter("user");

		int participantsNum = 0;
		try {
			/* extracting participants only if the parameter for the number of participants has been attached to the request */
			if (participantsnumber != null) {
				participantsNum = Integer.parseInt(participantsnumber);

				for (int i = 0 ; i < participantsNum ; i++) {
					participantsPhones.add(req.getParameter("participant_" + i));
				}
			}
		} catch (NumberFormatException e) {
			/* the parameter for the number of participants doesn't contain a parsable integer */
			BolePoServerMisc.printStatusToResponse(out, statusBuilder.setAction(action.toString()).setState("error").setDescription("participantsnumber is not numeric").build());
			out.println("</Response>");
			return;
		}			

		Filter meetingEqualHashFilter = new FilterPredicate(BolePoServerConstans.DB_TABLE_MEETING.HASH.toString(), FilterOperator.EQUAL, hashval);

		switch (action) {
		case CREATE:

			/* checking if one of the parameters is missing and if so exiting with informative status */
			if (actionmakerphone == null || name == null || date == null || time == null || location == null || sharelocationtime == null) {
				BolePoServerMisc.printStatusToResponse(out, statusBuilder.setAction(action.toString()).setState("Error").setDescription("one or more parameters are missing").build());
				break;
			}

			/* storing the meeting data in the server's data-base */
			Key newMeetingKey = storeMeeting(actionmakerphone, name, date, time, location, participantsPhones, sharelocationtime);

			/* creating a collection of Participant instances from the data received in the HTTP request */
			List<Participant> participantsList = new ArrayList<Participant>();
			for (String phone : participantsPhones) {

				/* building base participant */
				Participant.Builder participantBuilder = new Participant.Builder(phone, newMeetingKey);

				/* if this participant is the meeting's creator, then he should has special properties */
				if (actionmakerphone.equalsIgnoreCase(phone))
					participantsList.add(participantBuilder
							.setCredentials(BolePoServerConstans.CREDENTIALS.MANAGER.toString())
							.setRsvp(BolePoServerConstans.RSVP.YES.toString())
							.setShareLocationStatus("yes")
							.build());
				else participantsList.add(participantBuilder.build());
			}

			/* storing the participants data in the server's data-base */
			storeParticipants(participantsList);

			BolePoServerMisc.printStatusToResponse(out, statusBuilder.setAction(action.toString()).setState("OK").build());



			/* sending back the hash calculated on the meeting's details to be used later to check
			 * integrity of the meeting in actions like retrieve, edit and delete */
			out.println("<Meeting>");
			out.println("<MHash>" + getMeetingHash(newMeetingKey) + "</MHash>");
			out.println("<Participants>");
			for (Participant p : participantsList) {
				out.println("<Participant>");
				out.println("<Phone>" + p.getPhone() + "</Phone>");
				out.println("<Name>" + p.getName() + "</Name>");
				out.println("<RSVP>" + p.getRSVP() + "</RSVP>");
				out.println("<Credentials>" + p.getCredentials() + "</Credentials>");
				out.println("<PHash>" + p.getHash() + "</PHash>");
				out.println("</Participant>");
			}
			out.println("</Participants>");
			out.println("</Meeting>");

			int notificationResults[] = notifyParticipants(actionmakerphone, BolePoServerConstans.GCM_NOTIFICATION.NEW_MEETING, participantsList, newMeetingKey);
			printNotificationResultsToResponse(out, notificationResults);

			//TODO is this line necessary ??
			mHash = null;
			break;
		case MODIFY:

			/* checking whether the requester has the right credentials to modify the meeting's details */
			//			if (hasModifyingCredentials(actionmakerphone, hashval)) {

			/* creating Query instance on the meetings table and filtering the entities only to the entity with the hash value as received in the parameters */
			Query modifyQry = new Query(BolePoServerConstans.DB_TABLE_MEETING.TABLE_NAME.toString()).setFilter(meetingEqualHashFilter);

			/* running the query in the datastore */
			PreparedQuery pq = mDatastore.prepare(modifyQry);

			/* getting the one and only result from datastore */
			Entity meeting = pq.asSingleEntity();

			/* checking if there is a meeting associated with the given hash value */
			if (meeting != null) {
				/* creating new meeting instance with the modified details and getting back the modified meeting entity */
				Entity modifiedMeeting = modifyAndSave(meeting, actionmakerphone, name, date, time, location, participantsPhones, sharelocationtime);

				/* creating a collection of Participant instances from the data received in the HTTP request */
				participantsList = new ArrayList<Participant>();
				for (String phone : participantsPhones) {

					/* building base participant */
					Participant.Builder participantBuilder = new Participant.Builder(phone, modifiedMeeting.getKey());

					/* if this participant is the meeting's creator, then he should has special properties */
					if (actionmakerphone.equalsIgnoreCase(phone))
						participantsList.add(participantBuilder
								.setCredentials(BolePoServerConstans.CREDENTIALS.MANAGER.toString())
								.setRsvp(BolePoServerConstans.RSVP.YES.toString())
								.setShareLocationStatus("yes")
								.build());
					else participantsList.add(participantBuilder.build());
				}

				List<Participant> oldParticipants = updateParticipants(modifiedMeeting.getKey(), participantsList);

				/* setting the request status to be OK */
				BolePoServerMisc.printStatusToResponse(out, statusBuilder.setAction(action.toString()).setState("OK").build());

				/* sending back the hash calculated on the meeting's details to be used later to check
				 * integrity of the meeting in actions like retrieve, edit and delete */
				out.println("<Meeting>");
				out.println("<Hash>" + modifiedMeeting.getProperty(BolePoServerConstans.DB_TABLE_MEETING.HASH.toString()) + "</Hash>");
				out.println("<Participants>");
				for (Participant p : participantsList) {
					out.println("<Participant>");
					out.println("<Phone>" + p.getPhone() + "</Phone>");
					out.println("<Name>" + p.getName() + "</Name>");
					out.println("<RSVP>" + p.getRSVP() + "</RSVP>");
					out.println("<Credentials>" + p.getCredentials() + "</Credentials>");
					out.println("<PHash>" + p.getHash() + "</PHash>");
					out.println("</Participant>");
				}
				out.println("</Participants>");
				out.println("</Meeting>");

				/* notify only if after the modification there are more participants than the meeting manager */
				if (participantsList.size() > 1) {
					notificationResults = notifyParticipants(actionmakerphone, BolePoServerConstans.GCM_NOTIFICATION.UPDATED_MEETING,
							participantsList,
							modifiedMeeting.getKey());
					
					printNotificationResultsToResponse(out, notificationResults);
				}

				/* notify to the users that are no longer invited that they are no longer invited to the meeting */
				int[] removedParticipantsNotificationResults = notifyParticipants(actionmakerphone, BolePoServerConstans.GCM_NOTIFICATION.REMOVED_FROM_MEETING,
						oldParticipants,
						modifiedMeeting.getKey());

				printNotificationResultsToResponse(out, removedParticipantsNotificationResults);
			}
			/* query for a meeting with the given hash ended with no results.
			 * the meaning of this is that there is no such a meeting, hence there's nothing to modify */
			else {
				BolePoServerMisc.printStatusToResponse(out, statusBuilder.setAction(action.toString()).setState("Error").setDescription("no such a meeting - " + hashval).build());
			}
			//			}
			//			else {
			//				/* the user doesn't has the right credentials to modify the meeting */
			//				printStatusToResponse(out, statusBuilder.setAction(action.toString()).setState("Error").setDescription("no permission to modify the meeting").build());
			//			}
			//TODO notify about modifying meeting's details
			break;
		case REMOVE:
			/* looking for a meeting record with hash value equal to the hash value given by the user 
			 * in the HTTP request */
			Query deleteQry = new Query(BolePoServerConstans.DB_TABLE_MEETING.TABLE_NAME.toString()).setFilter(meetingEqualHashFilter);
			PreparedQuery deleteResults = mDatastore.prepare(deleteQry);

			Entity meetingToRemove = deleteResults.asSingleEntity();

			if (meetingToRemove != null) {

				/* checking whether the requester has the right credentials to modify the meeting's details */
				//				if (hasModifyingCredentials(actionmakerphone, hashval)) {

				Filter participantEqualMeetingKeyFilter = new FilterPredicate(BolePoServerConstans.DB_TABLE_PARTICIPANT.MEETING_KEY.toString(),
						FilterOperator.EQUAL,
						meetingToRemove.getKey());

				Query deletedParticipantsQry = new Query(BolePoServerConstans.DB_TABLE_PARTICIPANT.TABLE_NAME.toString()).setFilter(participantEqualMeetingKeyFilter);
				deleteResults = mDatastore.prepare(deletedParticipantsQry);

				/* removing all the participants linked to the meeting */
				for (Entity e : deleteResults.asIterable())
					mDatastore.delete(e.getKey());

				/* removing entire meeting */
				mDatastore.delete(meetingToRemove.getKey());

				/* setting the request status to be OK */
				BolePoServerMisc.printStatusToResponse(out, statusBuilder.setAction(action.toString()).setState("OK").build());
				//				}
				//				else {
				//					/* user doesn't has credentials to perform delete */
				//					printStatusToResponse(out, statusBuilder.setAction(action.toString()).setState("Error").setDescription("no credentials").build());
				//				}
			}
			else {
				/* no meeting corresponds to the given hash */
				BolePoServerMisc.printStatusToResponse(out, statusBuilder.setAction(action.toString()).setState("Error").setDescription("no such a meeting - " + hashval).build());
			}
			//TODO notify about delete
			break;
		case RETRIEVE: 
			Filter hashFilter = new FilterPredicate(BolePoServerConstans.DB_TABLE_MEETING.HASH.toString(),
					Query.FilterOperator.EQUAL,
					hashval);

			Query retrieveQry = new Query(BolePoServerConstans.DB_TABLE_MEETING.TABLE_NAME.toString()).setFilter(hashFilter);
			PreparedQuery retrieveResults = mDatastore.prepare(retrieveQry);

			try {
				/* retrieving the one and only result from the above query */
				Entity meetingToRetrieve = retrieveResults.asSingleEntity();

				out.println("<here>OK</here>");
				if (meetingToRetrieve != null) {
					/* checking if the user is the creator or one of the participants */
					//						if (hasReadingCredentials(actionmakerphone, meeting)) {


					/* setting the request status to be OK */
					BolePoServerMisc.printStatusToResponse(out, statusBuilder.setAction(action.toString()).setState("OK").build());

					/* filling the meeting details */
					out.println("<Meeting>");
					out.println("<Manager>" + (String) meetingToRetrieve.getProperty(BolePoServerConstans.DB_TABLE_MEETING.MANAGER.toString()) + "</Manager>");
					out.println("<Name>" + (String) meetingToRetrieve.getProperty(BolePoServerConstans.DB_TABLE_MEETING.NAME.toString()) + "</Name>");
					out.println("<Date>" + (String) meetingToRetrieve.getProperty(BolePoServerConstans.DB_TABLE_MEETING.DATE.toString()) + "</Date>");
					out.println("<Time>" + (String) meetingToRetrieve.getProperty(BolePoServerConstans.DB_TABLE_MEETING.TIME.toString()) + "</Time>");
					out.println("<Location>" + (String) meetingToRetrieve.getProperty(BolePoServerConstans.DB_TABLE_MEETING.LOCATION.toString()) + "</Location>");
					out.println("<ShareLocationTime>" + (String) meetingToRetrieve.getProperty(BolePoServerConstans.DB_TABLE_MEETING.SHARE_LOCATION_TIME.toString()) + "</ShareLocationTime>");

					Key meetingKey = meetingToRetrieve.getKey();

					Filter meetingIdFltr = new FilterPredicate(BolePoServerConstans.DB_TABLE_PARTICIPANT.MEETING_KEY.toString(), FilterOperator.EQUAL, meetingKey);
					Query partsQry = new Query(BolePoServerConstans.DB_TABLE_PARTICIPANT.TABLE_NAME.toString()).setFilter(meetingIdFltr);

					PreparedQuery pqParts = mDatastore.prepare(partsQry);
					out.println("<Participants>");
					for (Entity e : pqParts.asIterable()) {
						out.println("<Participant>");
						out.println("<ParticipantName>" + (String) e.getProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.NAME.toString()) + "</ParticipantName>");
						out.println("<RSVP>" + (String) e.getProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.RSVP.toString()) + "</RSVP>");
						out.println("<Credentials>" + (String) e.getProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.CREDENTIALS.toString()) + "</Credentials>");
						out.println("<ParticipantHash>" + (String) e.getProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.HASH.toString()) + "</ParticipantHash>");
						out.println("</Participant>");
					}

					out.println("</Participants>");
					out.println("</Meeting>");
					//						}
				}
				out.println("<Here>" + hashval + "</Here>");
			} catch (TooManyResultsException e) {
				e.printStackTrace();
			}
			break;
		case ATTEND:

			/* updating the user RSVP status */
			storeParticipantAttendingStatus(getParticipantEntityByPhone(actionmakerphone, getMeetingKeyByMeetingHash(hashval)), BolePoServerConstans.RSVP.YES);

			participantsList = new ArrayList<Participant>();

			/* getting the key of the meeting by it's hash value */
			Key AttendedMeetingKey = getMeetingKeyByMeetingHash(hashval);

			/* populating the participants list with the meeting's manager only because he is the only one who will be notified about the 
			 * attendance of the participants of the meeting */
			participantsList.add(getMeetingManager(AttendedMeetingKey));

			//			participantsList.add(convertEntityToParticipant(getParticipantEntityByPhone(attendedUser, meetingKey)));

			/* notifying the manager */
			notifyParticipants(actionmakerphone, GCM_NOTIFICATION.PARTICIPANT_ATTENDED, participantsList, AttendedMeetingKey);

			/* setting the request status to be OK */
			BolePoServerMisc.printStatusToResponse(out, statusBuilder.setAction(action.toString()).setState("OK").build());
			break;
		case DECLINE:

			/* updating the user RSVP status */
			storeParticipantAttendingStatus(getParticipantEntityByPhone(actionmakerphone, getMeetingKeyByMeetingHash(hashval)), BolePoServerConstans.RSVP.NO);

			participantsList = new ArrayList<Participant>();

			/* getting the key of the meeting by it's hash value */
			Key declinedMeetingKey = getMeetingKeyByMeetingHash(hashval);

			/* populating the participants list with the meeting's manager only because he is the only one who will be notified about the 
			 * attendance of the participants of the meeting */
			participantsList.add(getMeetingManager(declinedMeetingKey));

			/* notifying the manager */
			notifyParticipants(actionmakerphone, GCM_NOTIFICATION.PARTICIPANT_DECLINED, participantsList, declinedMeetingKey);

			/* setting the request status to be OK */
			BolePoServerMisc.printStatusToResponse(out, statusBuilder.setAction(action.toString()).setState("OK").build());
			break;
		case REPLACE_MANAGER:
			/* filtering the Meeting table by the input hash value */
			Filter meetingHashFilter = new FilterPredicate(BolePoServerConstans.DB_TABLE_MEETING.HASH.toString(), FilterOperator.EQUAL, meetingHash);
			Query qry = new Query(BolePoServerConstans.DB_TABLE_MEETING.TABLE_NAME.toString()).setFilter(meetingHashFilter);
			PreparedQuery result = mDatastore.prepare(qry);
			Entity m = result.asSingleEntity();

			/* checking if query didn't succeed. i.e. there isn't a meeting record with the same hash value as the input */
			if (m == null) {
				BolePoServerMisc.printStatusToResponse(out, new ServletStatus.Builder()
				.setAction(action.toString())
				.setState("Error")
				.setDescription("no such meeting - " + meetingHash)
				.build());
				break;
			}

			/* filtering the Participant table by the hash value of the going-to-be-replaced meeting's manager */
			Filter oldParticipantHashFilter = new FilterPredicate(BolePoServerConstans.DB_TABLE_PARTICIPANT.HASH.toString(),
					FilterOperator.EQUAL,
					oldManagerHash);
			qry = new Query(BolePoServerConstans.DB_TABLE_PARTICIPANT.TABLE_NAME.toString()).setFilter(oldParticipantHashFilter);
			result = mDatastore.prepare(qry);
			Entity oldManager = result.asSingleEntity();

			/* checking if query didn't succeed. i.e. there isn't a participant record with the same hash value as the input */
			if (oldManager == null) {
				BolePoServerMisc.printStatusToResponse(out, new ServletStatus.Builder()
				.setAction(action.toString())
				.setState("Error")
				.setDescription("no such participant (old manager) - " + oldManagerHash)
				.build());
				break;
			}

			/* filtering the Participant table by the hash value of the going-to-be meeting's manager */
			Filter newParticipantHashFilter = new FilterPredicate(BolePoServerConstans.DB_TABLE_PARTICIPANT.HASH.toString(),
					FilterOperator.EQUAL,
					newManagerHash);
			qry = new Query(BolePoServerConstans.DB_TABLE_PARTICIPANT.TABLE_NAME.toString()).setFilter(newParticipantHashFilter);
			result = mDatastore.prepare(qry);
			Entity newManager = result.asSingleEntity();

			/* checking if query didn't succeed. i.e. there isn't a participant record with the same hash value as the input */
			if (newManager == null) {
				BolePoServerMisc.printStatusToResponse(out, new ServletStatus.Builder()
				.setAction(action.toString())
				.setState("Error")
				.setDescription("no such participant (new manager) - " + newManagerHash)
				.build());
				break;
			}

			/* updating the meeting entity with new manager and as a result - a new hash */
			m.setProperty(BolePoServerConstans.DB_TABLE_MEETING.MANAGER.toString(), newManager.getProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.PHONE.toString()));
			String updatedMeetingHash = Hasher.meetingHashGenerator(m);
			m.setProperty(BolePoServerConstans.DB_TABLE_MEETING.HASH.toString(), updatedMeetingHash);
			mDatastore.put(m);
			out.println("<MeetingHash>" + updatedMeetingHash + "</MeetingHash>");

			/* updating the participant which used to be the meeting' manager with new credentials and as a result - a new hash */
			oldManager.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.CREDENTIALS.toString(), BolePoServerConstans.CREDENTIALS.REGULAR.toString());
			String updatedOldManagerHash = Hasher.participantHashGenerator(oldManager);
			oldManager.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.HASH.toString(), updatedOldManagerHash);
			mDatastore.put(oldManager);
			out.println("<OldParticipantHash>" + updatedOldManagerHash + "</OldParticipantHash>");

			/* updating the participant which is now the new meeting's manager with new credentials and as a result - a new hash */
			newManager.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.CREDENTIALS.toString(), BolePoServerConstans.CREDENTIALS.MANAGER.toString());
			String updatedNewManagerHash = Hasher.participantHashGenerator(newManager);
			newManager.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.HASH.toString(), updatedNewManagerHash);
			mDatastore.put(newManager);
			out.println("<NewParticipantHash>" + updatedNewManagerHash + "</NewParticipantHash>");

			/* setting the request status to be OK */
			BolePoServerMisc.printStatusToResponse(out, statusBuilder.setAction(action.toString()).setState("OK").build());
			break;
		case REMOVE_PARTICIPANT:
			/* searching for participant with hash value as received as input */
			Filter participantHashFilter = new FilterPredicate(BolePoServerConstans.DB_TABLE_PARTICIPANT.HASH.toString(), FilterOperator.EQUAL, participantHash);
			qry = new Query(BolePoServerConstans.DB_TABLE_PARTICIPANT.TABLE_NAME.toString()).setFilter(participantHashFilter);
			result = mDatastore.prepare(qry);
			Entity participantToRemove = result.asSingleEntity();

			/* checking if the query didn't succeed. i.e. there isn't a participant with this value */
			if (participantToRemove == null) {
				BolePoServerMisc.printStatusToResponse(out, new ServletStatus.Builder()
				.setAction(action.toString())
				.setState("Error")
				.setDescription("no such participant - " + newManagerHash)
				.build());
			}
			else {

				/* removing the participant */
				mDatastore.delete(participantToRemove.getKey());

				/* setting the request status to be OK */
				BolePoServerMisc.printStatusToResponse(out, statusBuilder.setAction(action.toString()).setState("OK").build());
			}
			break;
		default:
			break;
		}
		out.println("</Response>");
	}

}