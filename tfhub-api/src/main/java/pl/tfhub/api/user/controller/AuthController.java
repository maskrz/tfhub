package pl.tfhub.api.user.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.tfhub.api.config.SecurityErrorResponse;
import pl.tfhub.api.config.SecurityConfiguration;
import pl.tfhub.api.user.dto.request.LoginRequest;
import pl.tfhub.api.user.dto.response.AuthenticatedUserResponse;
import pl.tfhub.api.user.service.AuthenticatedUser;
import pl.tfhub.api.user.service.LoginAttemptService;
import pl.tfhub.api.user.enums.UserRole;

@RestController
@RequestMapping("/api")
public class AuthController {

	private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository;
	private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
	private final CsrfTokenRepository csrfTokenRepository;
	private final LoginAttemptService loginAttemptService;
	private final JdbcClient jdbcClient;

	public AuthController(
			AuthenticationManager authenticationManager,
			SecurityContextRepository securityContextRepository,
			SessionAuthenticationStrategy sessionAuthenticationStrategy,
			CsrfTokenRepository csrfTokenRepository,
			LoginAttemptService loginAttemptService,
			JdbcClient jdbcClient) {
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
		this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
		this.csrfTokenRepository = csrfTokenRepository;
		this.loginAttemptService = loginAttemptService;
		this.jdbcClient = jdbcClient;
	}

	@GetMapping("/csrf")
	public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
		csrfToken.getToken();
		return ResponseEntity.ok().build();
	}

	@PostMapping("/auth/login")
	public ResponseEntity<?> login(
			@Valid @RequestBody LoginRequest request,
			HttpServletRequest servletRequest,
			HttpServletResponse servletResponse) throws Exception {
		String address = servletRequest.getRemoteAddr();
		if (loginAttemptService.isBlocked(address)) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
					.body(new LoginError("Too many failed login attempts"));
		}

		Authentication authentication;
		try {
			authentication = authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
		} catch (AuthenticationException exception) {
			loginAttemptService.recordFailure(address);
			LOGGER.warn("Failed login attempt");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new LoginError("Invalid username or password"));
		}

		loginAttemptService.recordSuccess(address);
		LOGGER.info("User {} authenticated", ((AuthenticatedUser) authentication.getPrincipal()).id());
		HttpSession session = servletRequest.getSession(true);
		sessionAuthenticationStrategy.onAuthentication(authentication, servletRequest, servletResponse);
		session.setAttribute(SecurityConfiguration.AUTHENTICATED_AT, Instant.now().toEpochMilli());
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, servletRequest, servletResponse);
		csrfTokenRepository.saveToken(null, servletRequest, servletResponse);
		return ResponseEntity.ok(toResponse((AuthenticatedUser) authentication.getPrincipal()));
	}

	@GetMapping("/auth/session")
	public AuthenticatedUserResponse session(Authentication authentication) {
		return toResponse((AuthenticatedUser) authentication.getPrincipal());
	}

	@PostMapping("/auth/logout")
	public ResponseEntity<Void> logout(
			HttpServletRequest request,
			HttpServletResponse response,
			Authentication authentication) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		SecurityContextHolder.clearContext();
		securityContextRepository.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
		csrfTokenRepository.saveToken(null, request, response);
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
			LOGGER.info("User {} logged out", user.id());
		}
		return ResponseEntity.noContent().build();
	}

	private AuthenticatedUserResponse toResponse(AuthenticatedUser user) {
		Long athleteId = null;
		if (user.role() == UserRole.ATHLETE) {
			List<Long> athleteIds = jdbcClient.sql("""
							SELECT athlete_id
							FROM user_athlete_access
							WHERE user_id = :userId
							ORDER BY athlete_id
							""")
					.param("userId", user.id())
					.query(Long.class)
					.list();
			if (athleteIds.size() != 1) {
				throw new IllegalStateException("ATHLETE accounts must be linked to exactly one athlete");
			}
			athleteId = athleteIds.getFirst();
		}
		return new AuthenticatedUserResponse(true, user.id(), user.username(), user.role(), athleteId);
	}

	private record LoginError(String title) {
	}
}
