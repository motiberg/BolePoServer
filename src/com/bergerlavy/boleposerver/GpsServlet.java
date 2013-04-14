package com.bergerlavy.boleposerver;

import java.io.IOException;

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
import com.google.appengine.api.datastore.Query.FilterPredicate;

@SuppressWarnings("serial")
public class GpsServlet extends HttpServlet {

	private DatastoreService mDatastore;
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		
		mDatastore = DatastoreServiceFactory.getDatastoreService();
		
		String user = req.getParameter("user");
		String lat = req.getParameter("lat");
		String lon = req.getParameter("lon");
		
		Filter userFilter = new FilterPredicate("name",
				Query.FilterOperator.EQUAL,
				user);
		
		Query qry = new Query("Participant").setFilter(userFilter);
		PreparedQuery pq = mDatastore.prepare(qry);
		
		Entity participant = pq.asList(FetchOptions.Builder.withLimit(1)).get(0);
		participant.setProperty("lat", lat);
		participant.setProperty("lon", lon);
		mDatastore.put(participant);
	}
}
