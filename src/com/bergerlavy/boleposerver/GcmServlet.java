package com.bergerlavy.boleposerver;

import java.io.IOException;
import java.io.PrintWriter;

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

		Boolean status = false;
		String responseStr = "";
		/* retrieving the data attached to the HTTP request */
		String phone = req.getParameter("userphone");
		String regid = req.getParameter("gcmid");
		String action = req.getParameter("action");

		if (action != null) {

			/* checking whether the requested action is to register to the GCM service */
			if (action.equalsIgnoreCase("gcm_register")) {

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
					}
					else {
						/* creating record for the new user */
						user = new Entity(BolePoServerConstans.DB_TABLE_USER.TABLE_NAME.toString());
						user.setProperty(BolePoServerConstans.DB_TABLE_USER.PHONE.toString(), phone);
						user.setProperty(BolePoServerConstans.DB_TABLE_USER.GCM_ID.toString(), regid);
					}
					mDatastore.put(user);
					status = true;
				}
				else {
					responseStr = "username or regid is null";
				}
			}
			else if (action.equalsIgnoreCase("gcm_unregister")) {

				/* the data must be not null in order to store it in the database */
				if (phone != null && regid != null) {

					Filter regIdFilter = new FilterPredicate("gcmid", Query.FilterOperator.EQUAL, regid);
					Query regIdQry = new Query("User").setFilter(regIdFilter);

					PreparedQuery pq = mDatastore.prepare(regIdQry);
					Entity usr = pq.asList(FetchOptions.Builder.withLimit(1)).get(0);

					if (phone.equalsIgnoreCase((String) usr.getProperty("phone"))) {
						mDatastore.delete(usr.getKey());	
						status = true;
					}
				}
				else {
					responseStr = "username or regid is null";
				}
			}
			else {
				responseStr = "unknown action";
			}
		}
		else {
			responseStr = "action is null";
		}

		resp.setContentType("text/xml");
		PrintWriter out = resp.getWriter();
		out.println("<?xml version=\"1.0\"?>");
		out.println("<GcmResponse>");
		out.println("<Action>" + action + "</Action>");
		out.println("<State>" + (status?"OK":"Error") + "</State>");
		out.println("<Desc>" + responseStr + "</Desc>");
		out.println("</GcmResponse>");
	}
}
