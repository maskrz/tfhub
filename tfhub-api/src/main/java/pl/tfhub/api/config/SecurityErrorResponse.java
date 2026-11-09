package pl.tfhub.api.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public final class SecurityErrorResponse {

	private SecurityErrorResponse() {
	}

	public static void write(HttpServletResponse response, int status, String title, String detail) throws IOException {
		response.setStatus(status);
		response.setContentType("application/problem+json");
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title
				+ "\",\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
	}
}
