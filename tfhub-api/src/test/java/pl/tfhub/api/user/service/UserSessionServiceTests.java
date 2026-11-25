package pl.tfhub.api.user.service;

import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import pl.tfhub.api.user.enums.UserRole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserSessionServiceTests {

	private final UserSessionService userSessionService = new UserSessionService();
	private final AuthenticatedUser user = new AuthenticatedUser(7, "coach", "hash", UserRole.COACH, true);

	@Test
	void removesSessionsThatHaveBeenIdleForFourteenDays() {
		HttpSession session = session("idle-session", Instant.now().minus(Duration.ofDays(16)),
				Instant.now().minus(Duration.ofDays(15)));
		userSessionService.register(session, user);

		assertTrue(userSessionService.getSessions(user.id(), null).isEmpty());
		verify(session).invalidate();
	}

	@Test
	void keepsAnOldSessionWhenItsLastActivityIsRecent() {
		Instant now = Instant.now();
		HttpSession session = session("active-session", now.minus(Duration.ofDays(30)), now);
		userSessionService.register(session, user);

		var result = userSessionService.getSessions(user.id(), null);

		assertEquals(1, result.size());
		assertEquals(now.minus(Duration.ofDays(30)).toEpochMilli(),
				result.getFirst().createdAt().toEpochMilli(), 5_000);
	}

	private HttpSession session(String servletId, Instant createdAt, Instant lastActivityAt) {
		HttpSession session = mock(HttpSession.class);
		when(session.getId()).thenReturn(servletId);
		when(session.getCreationTime()).thenReturn(createdAt.toEpochMilli());
		when(session.getLastAccessedTime()).thenReturn(lastActivityAt.toEpochMilli());
		return session;
	}
}
