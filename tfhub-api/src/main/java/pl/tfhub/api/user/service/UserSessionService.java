package pl.tfhub.api.user.service;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;
import pl.tfhub.api.user.dto.response.UserSessionResponse;

@Service
public class UserSessionService implements HttpSessionListener {

	private static final Duration IDLE_TIMEOUT = Duration.ofDays(14);

	private final ConcurrentMap<String, SessionEntry> sessionsByHandle = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, String> handlesByServletId = new ConcurrentHashMap<>();

	public void register(HttpSession session, AuthenticatedUser user) {
		removeByServletId(session.getId());
		String handle = UUID.randomUUID().toString();
		SessionEntry entry = new SessionEntry(handle, session.getId(), user.id(), session);
		sessionsByHandle.put(handle, entry);
		handlesByServletId.put(entry.servletId(), handle);
	}

	public List<UserSessionResponse> getSessions(long userId, HttpSession currentSession) {
		removeExpiredSessions();
		return sessionsByHandle.values().stream()
				.filter(entry -> entry.userId() == userId)
				.map(entry -> toResponse(entry, currentSession))
				.filter(Objects::nonNull)
				.sorted(Comparator.comparing(UserSessionResponse::createdAt).reversed())
				.toList();
	}

	public boolean isRegistered(HttpSession session, long userId) {
		if (session == null) {
			return false;
		}
		String handle = handlesByServletId.get(session.getId());
		SessionEntry entry = handle == null ? null : sessionsByHandle.get(handle);
		return entry != null && entry.userId() == userId;
	}

	public Optional<Boolean> revoke(long userId, String handle, HttpSession currentSession) {
		SessionEntry entry = sessionsByHandle.get(handle);
		if (entry == null || entry.userId() != userId) {
			return Optional.empty();
		}

		boolean current = entry.session() == currentSession;
		try {
			entry.session().invalidate();
		} catch (IllegalStateException ignored) {
			// A concurrent timeout or logout has already invalidated this session.
		}
		remove(entry.servletId());
		return Optional.of(current);
	}

	public void unregister(HttpSession session) {
		if (session != null) {
			removeByServletId(session.getId());
		}
	}

	@Override
	public void sessionDestroyed(HttpSessionEvent event) {
		removeByServletId(event.getSession().getId());
	}

	private UserSessionResponse toResponse(SessionEntry entry, HttpSession currentSession) {
		try {
			long creationTime = entry.session().getCreationTime();
			Instant createdAt = Instant.ofEpochMilli(creationTime);
			long lastAccessedTime = entry.session().getLastAccessedTime();
			Instant lastActivityAt = Instant.ofEpochMilli(lastAccessedTime > 0
					? lastAccessedTime
					: creationTime);
			return new UserSessionResponse(entry.handle(), createdAt, lastActivityAt,
					entry.session() == currentSession);
		} catch (IllegalStateException ignored) {
			remove(entry.servletId());
			return null;
		}
	}

	private void removeExpiredSessions() {
		Instant now = Instant.now();
		for (SessionEntry entry : sessionsByHandle.values()) {
			try {
				long lastAccessedTime = entry.session().getLastAccessedTime();
				Instant lastActivity = Instant.ofEpochMilli(lastAccessedTime > 0
						? lastAccessedTime
						: entry.session().getCreationTime());
				if (now.isAfter(lastActivity.plus(IDLE_TIMEOUT))) {
					entry.session().invalidate();
					remove(entry.servletId());
				}
			} catch (IllegalStateException ignored) {
				remove(entry.servletId());
			}
		}
	}

	private void removeByServletId(String servletId) {
		String handle = handlesByServletId.get(servletId);
		if (handle != null) {
			remove(servletId);
		}
	}

	private void remove(String servletId) {
		String handle = handlesByServletId.remove(servletId);
		if (handle != null) {
			sessionsByHandle.remove(handle);
		}
	}

	private record SessionEntry(String handle, String servletId, long userId, HttpSession session) {
	}
}
