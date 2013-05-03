package com.bergerlavy.boleposerver;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.android.gcm.server.Message;
import com.google.android.gcm.server.MulticastResult;
import com.google.android.gcm.server.Sender;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.PreparedQuery.TooManyResultsException;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.sun.istack.internal.Builder;

/************************************************************************************************/
/*											TODO LIST											*/
/************************************************************************************************/
/* 1. save phone of participant 																*/

@SuppressWarnings("serial")
public class MeetingsServlet extends HttpServlet {

	private DatastoreService mDatastore;
	private String mHash;


	private boolean hasModifyingCredentials(String user, Entity meetingEntity) {
		String creator = (String) meetingEntity.getProperty("creator");
		if (creator.equals(user))
			return true;
		return false;
	}

	private Key storeMeeting(String actionmaker, String name, String date, String time, String location, List<String> participants, String sharelocationtime) {
		/* creating meeting instance with the meeting details as given by the user */
		Meeting newMeeting = new Meeting(actionmaker, name, date, time, location, participants, sharelocationtime);
		mHash = Hasher.meetingHashGenerator(newMeeting);

		//TODO make the hash calculated on the unique key in DB for "random" element

		Entity meeting = new Entity(BolePoServerConstans.DB_TABLE_MEETING.TABLE_NAME.toString());
		meeting.setProperty(BolePoServerConstans.DB_TABLE_MEETING.NAME.toString(), name);
		meeting.setProperty(BolePoServerConstans.DB_TABLE_MEETING.MANAGER.toString(), actionmaker);
		meeting.setProperty(BolePoServerConstans.DB_TABLE_MEETING.DATE.toString(), date);
		meeting.setProperty(BolePoServerConstans.DB_TABLE_MEETING.TIME.toString(), time);
		meeting.setProperty(BolePoServerConstans.DB_TABLE_MEETING.LOCATION.toString(), location);
		meeting.setProperty(BolePoServerConstans.DB_TABLE_MEETING.SHARE_LOCATION_TIME.toString(), sharelocationtime);
		meeting.setProperty(BolePoServerConstans.DB_TABLE_MEETING.HASH.toString(), mHash);
		return mDatastore.put(meeting);
	}

	private void storeParticipants(List<Participant> participants) {

		Entity participant;
		String participantHash = null;

		for (Participant p : participants) {
			participant = new Entity(BolePoServerConstans.DB_TABLE_PARTICIPANT.TABLE_NAME.toString());
			participant.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.MEETING_KEY.toString(), p.getMeetingID());
			participant.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.PHONE.toString(), p.getPhone());
			participant.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.NAME.toString(), p.getName());
			participant.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.RSVP.toString(), p.getRSVP());
			participant.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.CREDENTIALS.toString(), p.getCredentials());
			participantHash = Hasher.participantHashGenerator(p);
			p.setHash(participantHash);
			participant.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.HASH.toString(), participantHash);
			mDatastore.put(participant);
		}
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

		//TODO filter for the meeting key
		Query participantsQry = new Query(BolePoServerConstans.DB_TABLE_PARTICIPANT.TABLE_NAME.toString());
		PreparedQuery pq = mDatastore.prepare(participantsQry);

		if (participants != null) {
			for (Entity pe : pq.asIterable()) {
				boolean found = false;
				for (String s : participants) {
					if (((String) pe.getProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.PHONE.toString())).equals(s)) {
						found = true;
						break;
					}
				}
				/* if the participant entity name wasn't found in the participants list, that means
				 * that the participant is no longer invited to the meeting and it must be removed 
				 * from the database. */
				if (!found)
					mDatastore.delete(pe.getKey());
			}

			for (String s : participants) {
				boolean found = false;
				for (Entity pe : pq.asIterable()) {
					if (((String) pe.getProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.PHONE.toString())).equals(s)) {
						found = true;
						break;
					}
				}
				if (!found) {
					Entity participantEntity = new Entity(BolePoServerConstans.DB_TABLE_PARTICIPANT.TABLE_NAME.toString());
					participantEntity.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.MEETING_KEY.toString(), e.getKey());
					participantEntity.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.PHONE.toString(), s);
					participantEntity.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.NAME.toString(), null);
					participantEntity.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.RSVP.toString(), null);
					participantEntity.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.CREDENTIALS.toString(), null);
					mDatastore.put(participantEntity);
				}
			}

		}
		return e;
	}

	private HashMap<String, String> meetingData(Meeting m) {
		HashMap<String, String> values = new HashMap<String, String>();

		return values;
	}

	private int[] notifyParticipants(List<String> participants, String meetingHash) {
		List<String> gcmIds = new ArrayList<String>();
		for (String participant : participants) {

			Filter usrFilter = new FilterPredicate(BolePoServerConstans.DB_TABLE_USER.PHONE.toString(), Query.FilterOperator.EQUAL, participant);

			Query qry = new Query(BolePoServerConstans.DB_TABLE_USER.TABLE_NAME.toString()).setFilter(usrFilter);
			PreparedQuery pq = mDatastore.prepare(qry);
			for (Entity e : pq.asIterable()) {
				if (e.getProperty(BolePoServerConstans.DB_TABLE_USER.GCM_ID.toString()) != null) {
					gcmIds.add((String) e.getProperty(BolePoServerConstans.DB_TABLE_USER.GCM_ID.toString()));
				}
			}
		}

		Sender sender = new Sender(BolePoServerConstans.GCM_API_KEY);
		Message message = new Message.Builder().addData("meeting_hash", meetingHash).build();
		MulticastResult result = null;
		try {
			if (!gcmIds.isEmpty())
				result = sender.send(message, gcmIds, 5);
			else return null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
		Filter phoneFltr = new FilterPredicate(BolePoServerConstans.DB_TABLE_PARTICIPANT.PHONE.toString(), FilterOperator.EQUAL, actionMakerPhone);
		Filter meetingFltr = new FilterPredicate(BolePoServerConstans.DB_TABLE_PARTICIPANT.MEETING_KEY.toString(), FilterOperator.EQUAL, meetingkey);

		Query usrQry = new Query(BolePoServerConstans.DB_TABLE_PARTICIPANT.TABLE_NAME.toString()).setFilter(phoneFltr).setFilter(meetingFltr);

		PreparedQuery pq = mDatastore.prepare(usrQry);

		return pq.asSingleEntity();
	}

	private void storeParticipantAttendingStatus(Entity user, BolePoServerConstans.RSVP rsvp) {
		user.setProperty(BolePoServerConstans.DB_TABLE_PARTICIPANT.RSVP.toString(), rsvp.toString());
		mDatastore.put(user);
	}

	private void printStatusToResponse(PrintWriter out, MeetingsServletStatus status) {
		out.println("<Status>");
		out.println("<Action>" + status.getAction() + "</Action>");
		out.println("<State>" + status.getState() + "</State>");
		out.println("<Desc>" + status.getDescription() + "</Desc>");
		out.println("</Status>");
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		mDatastore = DatastoreServiceFactory.getDatastoreService();

		String actionmaker = null;
		String hashval = null;
		String name = null;
		String date = null;
		String time = null;
		String location = null;
		String sharelocationtime = null;
		String participantsnumber = null;
		
		MeetingsServletStatus.Builder statusBuilder = new MeetingsServletStatus.Builder();

		/* preparing the response in XML format */
		resp.setContentType("text/xml");
		PrintWriter out = resp.getWriter();
		out.println("<?xml version=\"1.0\"?>");
		out.println("<Response>");
		List<String> participantsPhones = new ArrayList<String>();
		BolePoServerConstans.ACTION action = null;
		try {
			action = BolePoServerConstans.ACTION.valueOf(req.getParameter("action"));
		}
		catch (IllegalArgumentException e) {
			printStatusToResponse(out, statusBuilder.setState("error").setDescription("unknown action").build());
			out.println("</Response>");
			return;
		}

		actionmaker = req.getParameter("actionmaker");
		hashval = req.getParameter("hash");
		name = req.getParameter("name");
		date = req.getParameter("date");
		time = req.getParameter("time");
		location = req.getParameter("location");
		sharelocationtime = req.getParameter("sharelocationtime");
		participantsnumber = req.getParameter("participantsnumber");
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
			printStatusToResponse(out, statusBuilder.setAction(action.toString()).setState("error").setDescription("participantsnumber is not numeric").build());
			out.println("</Response>");
			return;
		}			
		
		Filter meetingEqualHashFilter = new FilterPredicate(BolePoServerConstans.DB_TABLE_MEETING.HASH.toString(), FilterOperator.EQUAL, hashval);

		switch (action) {
		case CREATE:
			
			/* storing the meeting data in the server's data-base */
			Key newMeetingKey = storeMeeting(actionmaker, name, date, time, location, participantsPhones, sharelocationtime);

			/* creating a collection of Participant instances from the data received in the HTTP request */
			List<Participant> participantsList = new ArrayList<Participant>();
			for (String phone : participantsPhones) {

				/* building base participant */
				Participant.Builder participantBuilder = new Participant.Builder(phone, newMeetingKey);

				/* if this participant is the meeting's creator, then he should has special properties */
				if (actionmaker.equalsIgnoreCase(phone))
					participantsList.add(participantBuilder
							.setCredentials("root")
							.setRsvp("yes")
							.setShareLocationStatus("yes")
							.build());
				else participantsList.add(participantBuilder.build());
			}
			
			/* storing the participants data in the server's data-base */
			storeParticipants(participantsList);
			
			printStatusToResponse(out, statusBuilder.setAction(action.toString()).setState("OK").build());

			/* sending back the hash calculated on the meeting's details to be used later to check
			 * integrity of the meeting in actions like retrieve, edit and delete */
			out.println("<Meeting>");
			out.println("<MHash>" + mHash + "</MHash>");
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

			int r[] = notifyParticipants(participantsPhones, mHash);
			out.println("<Delivered>" + r[0] +  "</Delivered>");
			out.println("<FailDelivered>" + r[1] +  "</FailDelivered>");
			out.println("<Total>" + r[2] +  "</Total>");
			//				if (r != null) {
			//					if (r.length == 2)
			//						out.println("<Delivered>" + "Delivered: " + r[0] + "Failed: " + r[1] + "</Delivered>");
			//					else out.println("<Delivered>" + "error: " + r[0] + "</Delivered>");
			//				}
			//				else out.println("<Delivered>" + "null" +  "</Delivered>");
			mHash = null;
			break;
		case GCM_REGISTRATION:
			break;
		case GCM_UNREGISTRATION:
			break;
		case MODIFY:
			/* looking for a meeting record with hash value equal to the hash value given by the user 
			 * in the HTTP request */

			Query modifyQry = new Query(BolePoServerConstans.DB_TABLE_MEETING.TABLE_NAME.toString()).setFilter(meetingEqualHashFilter);
			PreparedQuery pq = mDatastore.prepare(modifyQry);

			/* getting the one and only meeting from datastore */
			Entity meeting = pq.asSingleEntity();

			//						/* checking whether the requester has the right credentials to modify the meeting's details */
			//						if (hasModifyingCredentials(actionmaker, meeting)) {
			//
			/* creating new meeting instance with the modified details */
			Entity modifiedMeeting = modifyAndSave(meeting, actionmaker, name, date, time, location, participantsPhones, sharelocationtime);

			/* saving the modified meeting in the datastore */

			/* setting the request status to be OK */
			printStatusToResponse(out, statusBuilder.setAction(action.toString()).setState("OK").build());

			/* sending back the hash calculated on the meeting's details to be used later to check
			 * integrity of the meeting in actions like retrieve, edit and delete */
			out.println("<Meeting>");
			out.println("<Hash>" + modifiedMeeting.getProperty(BolePoServerConstans.DB_TABLE_MEETING.HASH.toString()) + "</Hash>");
			//TODO add here the participants (name, hash) pairs
			out.println("</Meeting>");
			//						}
			//					}
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
				if (hasModifyingCredentials(actionmaker, meetingToRemove)) {

					Filter participantEqualMeetingKeyFilter = new FilterPredicate(BolePoServerConstans.DB_TABLE_PARTICIPANT.MEETING_KEY.toString(),
							FilterOperator.EQUAL,
							meetingToRemove.getKey());

					Query deletedParticipantsQry = new Query(BolePoServerConstans.DB_TABLE_PARTICIPANT.TABLE_NAME.toString()).setFilter(participantEqualMeetingKeyFilter);
					deleteResults = mDatastore.prepare(deletedParticipantsQry);
					List<Entity> participantsToDelete = deleteResults.asList(FetchOptions.Builder.withLimit(1));
					/* removing participants */
					for (Entity e : participantsToDelete)
						mDatastore.delete(e.getKey());

					/* removing entire meeting */
					mDatastore.delete(meetingToRemove.getKey());

					/* setting the request status to be OK */
					printStatusToResponse(out, statusBuilder.setAction(action.toString()).setState("OK").build());
				}
				else {
					/* user doesn't has credentials to perform delete */
					printStatusToResponse(out, statusBuilder.setAction(action.toString()).setState("Error").setDescription("no credentials").build());
				}
			}
			else {
				/* no meeting corresponds to the given hash */
				printStatusToResponse(out, statusBuilder.setAction(action.toString()).setState("Error").setDescription("no such a meeting - " + hashval).build());
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
					//						if (hasReadingCredentials(actionmaker, meeting)) {


					/* setting the request status to be OK */
					printStatusToResponse(out, statusBuilder.setAction(action.toString()).setState("OK").build());

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
			storeParticipantAttendingStatus(getParticipantEntityByPhone(actionmaker, getMeetingKeyByMeetingHash(hashval)), BolePoServerConstans.RSVP.YES);
			break;
		case UNATTEND:
			storeParticipantAttendingStatus(getParticipantEntityByPhone(actionmaker, getMeetingKeyByMeetingHash(hashval)), BolePoServerConstans.RSVP.NO);
			break;
		default:
			break;
		}
		out.println("</Response>");
	}

}