package com.bergerlavy.boleposerver;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
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


@SuppressWarnings("serial")
public class MeetingsServlet extends HttpServlet {

	//	private final static String ACTION_SEND = "send";
	//	private final static String ACTION_READ = "read";
	private final static String selectStringStart = "select from "+ Meeting.class.getName() + " where hashval =='";
	private DatastoreService mDatastore;
	private String mHash;

	private String buildRetrieveQuery(String hashval) {
		return selectStringStart + hashval + "'";
	}

	private boolean hasReadingCredentials(String user, Entity meetingEntity) {
		//		String creator = (String) meetingEntity.getProperty("creator");
		//		if (creator.equals(user))
		//			return true;
		//		@SuppressWarnings("unchecked")
		//		List<Participant> parts =  (List<Participant>) meetingEntity.getProperty("participants");
		//		for (Participant participant : parts) {
		//			if (participant.equals(user))
		//				return true;
		//		}
		//		return false;
		return true;
	}

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

		Entity meeting = new Entity("Meeting");
		meeting.setProperty("name", name);
		meeting.setProperty("creator", actionmaker);
		meeting.setProperty("date", date);
		meeting.setProperty("time", time);
		meeting.setProperty("location", location);
		meeting.setProperty("sharelocationtime", sharelocationtime);
		meeting.setProperty("hash", mHash);
		return mDatastore.put(meeting);
	}

	private void storeParticipants(List<Participant> participants) {

		Entity participant = new Entity("Participant");
		String participantHash = null;

		for (Participant p : participants) {
			participant = new Entity("Participant");
			participant.setProperty("meetingkey", p.getMeetingID());
			participant.setProperty("name", p.getName());
			participant.setProperty("rsvp", p.getRSVP());
			participant.setProperty("credentials", p.getCredentials());
			participantHash = Hasher.participantHashGenerator(p);
			p.setHash(participantHash);
			participant.setProperty("hash", participantHash);
			mDatastore.put(participant);
		}
	}

	private Entity modifyAndSave(Entity e, String actionmaker, String name, String date, String time, String location, List<String> participants, String sharelocationtime) {
		Meeting modifiedMeeting = new Meeting(actionmaker, name, date, time, location, participants, sharelocationtime);
		String meetingHash = Hasher.meetingHashGenerator(modifiedMeeting);

		e.setProperty("name", name);
		e.setProperty("creator", actionmaker);
		e.setProperty("date", date);
		e.setProperty("time", time);
		e.setProperty("location", location);
		e.setProperty("sharelocationtime", sharelocationtime);
		e.setProperty("hash", meetingHash);
		mDatastore.put(e);

		//TODO filter for the meeting key
		Query participantsQry = new Query("Participant");
		PreparedQuery pq = mDatastore.prepare(participantsQry);

		if (participants != null) {
			for (Entity pe : pq.asIterable()) {
				boolean found = false;
				for (String s : participants) {
					if (((String) pe.getProperty("name")).equals(s)) {
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
					if (((String) pe.getProperty("name")).equals(s)) {
						found = true;
						break;
					}
				}
				if (!found) {
					Entity participantEntity = new Entity("Participant");
					participantEntity.setProperty("meetingkey", e.getKey());
					participantEntity.setProperty("name", s);
					participantEntity.setProperty("rsvp", null);
					participantEntity.setProperty("credentials", null);
					//					Participant p = new Participant(s, e.getKey(), null, null, null);
					//					String participantHash = Hasher.participantHashGenerator(p);
					//					participantEntity.setProperty("hash", participantHash);
					mDatastore.put(participantEntity);
				}
			}

		}
		return e;
	}

	private int notifyParticipants(List<String> participants, String meetingHash) {
		List<String> gcmIds = new ArrayList<String>();
		//		participants.add("0546469478");
		for (String participant : participants) {

			Filter usrFilter = new FilterPredicate("name", Query.FilterOperator.EQUAL, participant);

			Query qry = new Query("User").setFilter(usrFilter);
			PreparedQuery pq = mDatastore.prepare(qry);
			for (Entity e : pq.asIterable()) {
				//			List<Entity> userLst = pq.asList(FetchOptions.Builder.withLimit(1));

				if (e.getProperty("gcmid") != null) {
					gcmIds.add((String) e.getProperty("gcmid"));
				}
			}
		}

		Sender sender = new Sender(BolePoServerConstans.GCM_API_KEY);
		Message message = new Message.Builder().addData("meeting_hash", meetingHash).build();
		MulticastResult result = null;
		try {
			if (!gcmIds.isEmpty())
				result = sender.send(message, gcmIds, 5);
			else return 0;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result.getSuccess();


		//
		//	HttpClient httpclient = new DefaultHttpClient();
		//	HttpPost httppost = new HttpPost(BolePoServerConstans.GCM_SERVER_ADDRESS);
		//	httppost.getParams().setParameter(CoreProtocolPNames.USER_AGENT, "BolePo user-agent");
		//	httppost.setHeader("Authorization", "key=" + BolePoServerConstans.GCM_API_KEY);
		//	httppost.setHeader("Content-Type", "application/json");
		//
		//	String httpRequestBody = "{ \"collapse_key\": \"meeting_invitation\"" + "," + 
		//			"\"time_to_live\": " + 3600 + "," + 
		//			"\"delay_while_idle\": " + "false" + "," + 
		//			"\"data\": {" +
		//			"  \"meeting_hash\": " + "\"" + meetingHash + "\"" + 
		//			"}," +  
		//			"\"registration_ids\": [";
		//
		//	for (String id : gcmIds) {
		//		httpRequestBody += "\"" + id + "\", ";
		//	}
		//
		//	httpRequestBody = httpRequestBody.substring(0, httpRequestBody.length() - 2);
		//
		//	httpRequestBody += "]";
		//
		//	httpRequestBody += "}";
		//
		//	StringEntity entity;
		//	try {
		//		entity = new StringEntity(httpRequestBody);
		//		entity.setContentType(new BasicHeader("Content-Type",
		//				"application/json"));
		//		httppost.setEntity(entity);
		//	} catch (UnsupportedEncodingException e) {
		//		e.printStackTrace();
		//	}
		//	
		//
		//
		//	try {
		//		//					/* Execute HTTP Post Request */
		//		HttpResponse response = httpclient.execute(httppost);
		//////							JSONObject json = new JSONObject(response.getEntity().getContent());
		//////							try {
		//////								int successedMsgs = json.getInt("success");
		//////								int failureMsgs = json.getInt("failure");
		//////								int canonicalMsgs = json.getInt("canonical_ids");
		//////								JSONArray results = json.getJSONArray("results");
		//////								if (results != null) {
		//////									for (int i = 0 ; i < results.length() ; i++) {
		//////										JSONObject msgResult = results.getJSONObject(i);
		//////										if (msgResult.getJSONObject("message_id ") != null) {
		//////											if (msgResult.getJSONObject("registration_id") != null) {
		//////												String newRegId = msgResult.getString("registration_id");
		//////											}
		//////										}
		//////				
		//////									}
		//////								}
		//////								return new int[] { successedMsgs, failureMsgs };
		//////							} catch (JSONException e) {
		//////								// TODO Auto-generated catch block
		//////								
		//////								e.printStackTrace();
		//////								return new int[] { 1 };
		//////							}
		//	} catch (ClientProtocolException e) {
		//		e.printStackTrace();
		////		return new int[] { 2 };
		//		return e.getMessage();
		//	} catch (IOException e) {
		//		e.printStackTrace();
		////		return new int[] { 3 };
		//		return e.getMessage();
		//	}


	}

	@SuppressWarnings("unchecked")
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		mDatastore = DatastoreServiceFactory.getDatastoreService();

		String hashval = null;
		String name = null;
		String date = null;
		String time = null;
		String location = null;
		String sharelocationtime = null;
		String participantsnumber = null;

		boolean error = false;

		/* preparing the response in XML format */
		resp.setContentType("text/xml");
		PrintWriter out = resp.getWriter();
		out.println("<?xml version=\"1.0\"?>");
		out.println("<Response>");
		List<String> participants = new ArrayList<String>();
		String action = req.getParameter("action");
		String actionmaker = req.getParameter("actionmaker");
		if (action.equals("create") || action.equals("modify")) {
			name = req.getParameter("name");
			date = req.getParameter("date");
			time = req.getParameter("time");
			location = req.getParameter("location");
			sharelocationtime = req.getParameter("sharelocationtime");
			participantsnumber = req.getParameter("participantsnumber");
			int participantsNum = 0;
			try {
				participantsNum = Integer.parseInt(participantsnumber);
			} catch (Exception e) {
				out.println("<Status>");
				out.println("<Action>" + action + "</Action>");
				out.println("<State>Error</State>");
				out.println("<Desc>participantsnumber is not numeric</Desc>");
				out.println("</Status>");
			}

			//			participants.add(req.getParameter("participant_0"));

			for (int i = 0 ; i < participantsNum ; i++) {
				participants.add(req.getParameter("participant_" + i));
			}
		}
//		if (action.equals("modify"))
			hashval = req.getParameter("hash");

		if (!error) {
			/* using PersistenceManager to store, update, and delete data objects,
			 * and to perform datastore queries */

			try {

				/* checking whether the user wanted to retrieve an existing meeting's details */
				if (action.equals("retrieve")) {

					Filter hashFilter = new FilterPredicate("hash",
							Query.FilterOperator.EQUAL,
							hashval);

					Query retrieveQry = new Query("Meeting").setFilter(hashFilter);
					PreparedQuery pq = mDatastore.prepare(retrieveQry);

					try {
						/* retrieving the one and only result from the above query */
						Entity meeting = pq.asSingleEntity();

						out.println("<here>OK</here>");
						if (meeting != null) {
							/* checking if the user is the creator or one of the participants */
							//						if (hasReadingCredentials(actionmaker, meeting)) {


							/* setting the request status to be OK */
							out.println("<Status>");
							out.println("<Action>" + action + "</Action>");
							out.println("<State>OK</State>");
							out.println("<Desc></Desc>");
							out.println("</Status>");

							/* filling the meeting details */
							out.println("<Meeting>");
							out.println("<Creator>" + (String) meeting.getProperty("creator") + "</Creator>");
							out.println("<Name>" + (String) meeting.getProperty("name") + "</Name>");
							out.println("<Date>" + (String) meeting.getProperty("date") + "</Date>");
							out.println("<Time>" + (String) meeting.getProperty("time") + "</Time>");
							out.println("<Location>" + (String) meeting.getProperty("location") + "</Location>");
							out.println("<ShareLocationTime>" + (String) meeting.getProperty("sharelocationtime") + "</ShareLocationTime>");

							Key meetingKey = meeting.getKey();

							Filter meetingIdFltr = new FilterPredicate("meetingid", FilterOperator.EQUAL, meetingKey);
							Query partsQry = new Query("Participant").setFilter(meetingIdFltr);

							PreparedQuery pqParts = mDatastore.prepare(partsQry);
							out.println("<Participants>");
							for (Entity e : pqParts.asIterable()) {
								out.println("<Participant>");
								out.println("<ParticipantName>" + (String) e.getProperty("name") + "</ParticipantName>");
								out.println("<RSVP>" + (String) e.getProperty("rsvp") + "</RSVP>");
								out.println("<Credentials>" + (String) e.getProperty("credentials") + "</Credentials>");
								out.println("<ParticipantHash>" + (String) e.getProperty("hash") + "</ParticipantHash>");
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

				}

				/* checking whether the user wanted to create a new meeting */
				else if (action.equals("create")) {
					Key newMeetingKey = storeMeeting(actionmaker, name, date, time, location, participants, sharelocationtime);

					/* creating a collection of Participant instances from the data received in the HTTP request */
					List<Participant> participantsList = new ArrayList<Participant>();
					for (String s : participants) {

						/* building base participant */
						Participant.Builder participantBuilder = new Participant.Builder(s, newMeetingKey);

						/* if this participant is the meeting's creator, then he should has special properties */
						if (actionmaker.equalsIgnoreCase(s))
							participantsList.add(participantBuilder
									.setCredentials("root")
									.setRsvp("yes")
									.setShareLocationStatus("yes")
									.build());
						else participantsList.add(participantBuilder.build());
					}

					storeParticipants(participantsList);

					out.println("<Status>");
					out.println("<Action>" + action + "</Action>");
					out.println("<State>OK</State>");
					out.println("<Desc></Desc>");
					out.println("</Status>");

					/* sending back the hash calculated on the meeting's details to be used later to check
					 * integrity of the meeting in actions like retrieve, edit and delete */
					out.println("<Meeting>");
					out.println("<MHash>" + mHash + "</MHash>");
					out.println("<Participants>");
					for (Participant p : participantsList) {
						out.println("<Participant>");
						out.println("<Name>" + p.getName() + "</Name>");
						out.println("<RSVP>" + p.getRSVP() + "</RSVP>");
						out.println("<Credentials>" + p.getCredentials() + "</Credentials>");
						out.println("<PHash>" + p.getHash() + "</PHash>");
						out.println("</Participant>");
					}
					out.println("</Participants>");
					out.println("</Meeting>");

					int r = notifyParticipants(participants, mHash);
					out.println("<Delivered>" + r +  "</Delivered>");
					//				if (r != null) {
					//					if (r.length == 2)
					//						out.println("<Delivered>" + "Delivered: " + r[0] + "Failed: " + r[1] + "</Delivered>");
					//					else out.println("<Delivered>" + "error: " + r[0] + "</Delivered>");
					//				}
					//				else out.println("<Delivered>" + "null" +  "</Delivered>");
					mHash = null;
				}
				/* checking whether the user wanted to edit an existing meeting */
				else if (action.equals("modify")) {

					/* looking for a meeting record with hash value equal to the hash value given by the user 
					 * in the HTTP request */

					Filter hashFilter = new FilterPredicate("hash",
							Query.FilterOperator.EQUAL,
							hashval);
					//
					Query modifyQry = new Query("Meeting").setFilter(hashFilter);
					PreparedQuery pq = mDatastore.prepare(modifyQry);
					////
					int qryEntityResultCount = 0;
					for (Entity e : pq.asIterable()) {
						qryEntityResultCount++;
					}

					//
					//					if (qryEntityResultCount == 1) {
					//
					/* getting the one and only meeting from datastore */
					Entity meeting = pq.asList(FetchOptions.Builder.withLimit(1)).get(0);
					//						//						Meeting meeting = meetings.get(0);
					//
					//						/* checking whether the requester has the right credentials to modify the meeting's details */
					//						if (hasModifyingCredentials(actionmaker, meeting)) {
					//
					/* creating new meeting instance with the modified details */
					Entity modifiedMeeting = modifyAndSave(meeting, actionmaker, name, date, time, location, participants, sharelocationtime);

					/* saving the modified meeting in the datastore */

					/* setting the request status to be OK */
					out.println("<Status>");
					out.println("<Action>" + action + "</Action>");
					out.println("<State>OK</State>");
					out.println("<Desc>" + qryEntityResultCount + "</Desc>");
					out.println("</Status>");

					/* sending back the hash calculated on the meeting's details to be used later to check
					 * integrity of the meeting in actions like retrieve, edit and delete */
					out.println("<Meeting>");
					out.println("<Hash>" + modifiedMeeting.getProperty("hash") + "</Hash>");
					//TODO add here the participants (name, hash) pairs
					out.println("</Meeting>");
					//						}
					//					}
					//TODO notify about modifying meeting's details
				}
				//			/* checking whether the user wanted to delete an existing meeting */
				//			else if (action.equals("delete")) {
				//				/* looking for a meeting record with hash value equal to the hash value given by the user 
				//				 * in the HTTP request */
				//				List<Meeting> meetings = (List<Meeting>) pm.newQuery(buildRetrieveQuery(hashval)).execute();
				//				
				//				if (meetings != null && meetings.size() == 1) {
				//					
				//					/* getting the one and only meeting from datastore */
				//					Meeting meeting = meetings.get(0);
				//					
				//					/* checking whether the requester has the right credentials to modify the meeting's details */
				//					if (hasModifyingCredentials(actionmaker, meeting)) {
				//						pm.deletePersistent(meeting);
				//						
				//						/* setting the request status to be OK */
				//						out.println("<Status>");
				//						out.println("<Action>" + action + "</Action>");
				//						out.println("<State>OK</State>");
				//						out.println("<Desc></Desc>");
				//						out.println("</Status>");
				//					}
				//					else {
				//						/* user doesn't has credentials to perform delete */
				//						out.println("<Status>");
				//						out.println("<Action>" + action + "</Action>");
				//						out.println("<State>Error</State>");
				//						out.println("<Desc>no credentials</Desc>");
				//						out.println("</Status>");
				//					}
				//				}
				//				else {
				//					/*  */
				//				}
				//				//TODO notify about delete
				//			}
				//			else {
				//				out.println("<Status>");
				//				out.println("<Action>" + action + "</Action>");
				//				out.println("<State>Error</State>");
				//				out.println("<Desc>unknown action</Desc>");
				//				out.println("</Status>");
				//			}
				//			out.println("</Response>");
				out.println("</Response>");
			}
			finally {
				//				pm.close();
			}
		}
	}
}