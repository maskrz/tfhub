package pl.tfhub.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.tfhub.api.user.repository.UserRepository;
import pl.tfhub.api.user.service.AuthenticatedUser;
import pl.tfhub.api.user.service.UserSessionService;

final class ActiveSessionFilter extends OncePerRequestFilter {

	private final UserRepository userRepository;
	private final UserSessionService userSessionService;

	ActiveSessionFilter(UserRepository userRepository, UserSessionService userSessionService) {
		this.userRepository = userRepository;
		this.userSessionService = userSessionService;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
			var session = request.getSession(false);
			boolean valid = userSessionService.isRegistered(session, user.id())
					&& userRepository.findById(user.id())
					.map(account -> account.isActive()
							&& account.getRole() == user.role())
					.orElse(false);
			if (!valid) {
				if (session != null) {
					userSessionService.unregister(session);
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
