package pl.tfhub.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.tfhub.api.user.repository.UserRepository;
import pl.tfhub.api.user.service.AuthenticatedUser;

final class ActiveSessionFilter extends OncePerRequestFilter {

	private static final Duration ABSOLUTE_SESSION_LIFETIME = Duration.ofHours(24);

	private final UserRepository userRepository;

	ActiveSessionFilter(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
			var session = request.getSession(false);
			Object authenticatedAt = session == null ? null
					: session.getAttribute(SecurityConfiguration.AUTHENTICATED_AT);
			boolean expired = !(authenticatedAt instanceof Long timestamp)
					|| Instant.now().isAfter(Instant.ofEpochMilli(timestamp).plus(ABSOLUTE_SESSION_LIFETIME));
			boolean active = !expired && userRepository.findById(user.id())
					.map(account -> account.isActive())
					.orElse(false);
			if (!active) {
				if (session != null) {
					session.invalidate();
				}
				SecurityContextHolder.clearContext();
				SecurityErrorResponse.write(response, 401, "Unauthorized", "Authentication is required");
				return;
			}
		}
		filterChain.doFilter(request, response);
	}
}
