package com.bergerlavy.boleposerver;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;

@SuppressWarnings("serial")
public class GcmServlet extends HttpServlet {

	private DatastoreService mDatastore;

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		mDatastore = DatastoreServiceFactory.getDatastoreService();

		ServletStatus.Builder statusBuilder = new ServletStatus.Builder();

		/* retrieving the data attached to the HTTP request */
		String phone = req.getParameter("userphone");
		String regid = req.getParameter("gcmid");

		resp.setContentType("text/xml");
		PrintWriter out = resp.getWriter();
		out.println("<?xml version=\"1.0\"?>");
		out.println("<GcmResponse>");

		BolePoServerConstans.ACTION action = null;
		try {
			action = BolePoServerConstans.ACTION.getEnum(req.getParameter("action"));
		}
		catch (IllegalArgumentException e) {
			BolePoServerMisc.printStatusToResponse(out, statusBuilder
					.setState("error")
					.setDescription("unknown action: " + req.getParameter("action"))
					.build());
			out.println("</GcmResponse>");
			return;
		}

		switch (action) {
		case GCM_REGISTRATION:
			/* the data must be not null in order to store it in the database */
			if (phone != null && regid != null) {

				Entity user = null;
				Filter usrExists = new FilterPredicate(BolePoServerConstans.DB_TABLE_USER.PHONE.toString(),
						FilterOperator.EQUAL,
						phone);

				Query qry = new Query(BolePoServerConstans.DB_TABLE_USER.TABLE_NAME.toString()).setFilter(usrExists);
				PreparedQuery result = mDatastore.prepare(qry);
				if (result.asIterable().iterator().hasNext()) {
					/* updating an existing user */
					user = result.asSingleEntity();
					user.setProperty(BolePoServerConstans.DB_TABLE_USER.GCM_ID.toString(), regid);
					user.setProperty(BolePoServerConstans.DB_TABLE_USER.TIME.toString(), getLocalTime());
				}
				else {
					/* creating record for the new user */
					user = new Entity(BolePoServerConstans.DB_TABLE_USER.TABLE_NAME.toString());
					user.setProperty(BolePoServerConstans.DB_TABLE_USER.PHONE.toString(), phone);
					user.setProperty(BolePoServerConstans.DB_TABLE_USER.GCM_ID.toString(), regid);
					user.setProperty(BolePoServerConstans.DB_TABLE_USER.TIME.toString(), getLocalTime());
				}
				mDatastore.put(user);
				BolePoServerMisc.printStatusToResponse(out, statusBuilder
						.setAction(action.toString())
						.setState("OK")
						.build());
			}
			else {
				BolePoServerMisc.printStatusToResponse(out, statusBuilder
						.setState("error")
						.setDescription("username or regid is null")
						.build());
			}
			break;
		case GCM_UNREGISTRATION:
			/* the data must be not null in order to store it in the database */
			if (phone != null && regid != null) {

				Filter regIdFilter = new FilterPredicate(BolePoServerConstans.DB_TABLE_USER.GCM_ID.toString(), Query.FilterOperator.EQUAL, regid);
				Query regIdQry = new Query(BolePoServerConstans.DB_TABLE_USER.TABLE_NAME.toString()).setFilter(regIdFilter);

				PreparedQuery pq = mDatastore.prepare(regIdQry);
				Entity usr = pq.asList(FetchOptions.Builder.withLimit(1)).get(0);

				if (phone.equalsIgnoreCase((String) usr.getProperty(BolePoServerConstans.DB_TABLE_USER.PHONE.toString()))) {
					mDatastore.delete(usr.getKey());	
					BolePoServerMisc.printStatusToResponse(out, statusBuilder
							.setAction(action.toString())
							.setState("OK")
							.build());
				}
			}
			else {
				BolePoServerMisc.printStatusToResponse(out, statusBuilder
						.setState("error")
						.setDescription("username or regid is null")
						.build());
			}
			break;
		case GCM_CHECK_REGISTRATION:
			String contactsCountStr = req.getParameter("contactsCount");
			int contactsCount = 0;
			try { contactsCount = Integer.parseInt(contactsCountStr); }
			catch (NumberFormatException e) {
				BolePoServerMisc.printStatusToResponse(out, statusBuilder
						.setAction(action.toString())
						.setState("error")
						.setDescription("participantsnumber is not numeric")
						.build());
				break;
			}
			for (int i = 1 ; i <= contactsCount ; i++) {
				String contact = req.getParameter("contact_" + i);
				if (contact == null) {
					BolePoServerMisc.printStatusToResponse(out, statusBuilder
							.setAction(action.toString())
							.setState("error")
							.setDescription("contact (" + i + " out of " + contactsCount + ") phone is missing")
							.build());
					break;
				}
				else {
					Filter userFilter = new FilterPredicate(BolePoServerConstans.DB_TABLE_USER.PHONE.toString(),
							FilterOperator.EQUAL,
							contact);

					Query qry = new Query(BolePoServerConstans.DB_TABLE_USER.TABLE_NAME.toString()).setFilter(userFilter);
					PreparedQuery result = mDatastore.prepare(qry);
					Entity user = result.asSingleEntity();

					out.println("<Contact>");
					if (user != null)
						out.println("<Phone>" + contact + "</Phone>");
					out.write("</Contact>");
				}
			}
			BolePoServerMisc.printStatusToResponse(out, statusBuilder
					.setAction(action.toString())
					.setState("OK")
					.build());
			break;
		default:
			BolePoServerMisc.printStatusToResponse(out, statusBuilder.setState("error")
					.setDescription("unknown action")
					.build());
		}
		out.println("</GcmResponse>");
	}

	private String getLocalTime() {
		DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy");
		Date date = new Date();
		return dateFormat.format(date);
	}
}
