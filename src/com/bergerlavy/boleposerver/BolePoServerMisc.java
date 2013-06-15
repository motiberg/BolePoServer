package com.bergerlavy.boleposerver;

import java.io.PrintWriter;

public class BolePoServerMisc {

	public static void printStatusToResponse(PrintWriter out, ServletStatus status) {
		out.println("<Status>");
		out.println("<Action>" + status.getAction() + "</Action>");
		out.println("<FailureCode>" + status.getFailureCode() + "</FailureCode>");
		out.println("<Desc>" + status.getDescription() + "</Desc>");
		out.println("</Status>");
	}
}
