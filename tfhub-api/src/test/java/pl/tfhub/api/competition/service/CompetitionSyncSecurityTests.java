package pl.tfhub.api.competition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import pl.tfhub.api.athlete.dto.response.AthleteResponse;
import pl.tfhub.api.athlete.enums.Gender;
import pl.tfhub.api.athlete.service.AthleteService;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.competition.entity.CompetitionSyncRun;
import pl.tfhub.api.competition.enums.CompetitionSyncMode;
import pl.tfhub.api.competition.pzla.PzlaPageParser;
import pl.tfhub.api.competition.pzla.PzlaPageSource;
import pl.tfhub.api.user.enums.UserRole;
import pl.tfhub.api.user.service.AuthenticatedUser;

class CompetitionSyncSecurityTests {

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void workerReceivesAdminContextForIndividualAndGroupSyncAndClearsIt(boolean groupSync) throws Exception {
		AthleteService athletes = mock(AthleteService.class);
		CompetitionSyncStateService state = mock(CompetitionSyncStateService.class);
		PzlaPageSource pages = mock(PzlaPageSource.class);
		AccessScopeService access = new AccessScopeService(mock(JdbcClient.class));
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.initialize();
		CountDownLatch releaseWorker = new CountDownLatch(1);
		CountDownLatch workerBlocked = new CountDownLatch(1);
		try {
			executor.execute(() -> {
				workerBlocked.countDown();
				try {
					releaseWorker.await();
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
				}
			});
			assertThat(workerBlocked.await(5, TimeUnit.SECONDS)).isTrue();
			CompetitionSyncService service = new CompetitionSyncService(
					athletes, access, state, pages, new PzlaPageParser(), executor);
			CompetitionSyncRun run = new CompetitionSyncRun(8L, CompetitionSyncMode.CURRENT_SEASON);
			ReflectionTestUtils.setField(run, "id", 72L);
			when(state.createRun(8L, CompetitionSyncMode.CURRENT_SEASON)).thenReturn(run);
			when(state.markRunning(72L)).thenAnswer(invocation -> {
				run.start(Instant.now());
				return run;
			});
			when(state.lastSuccessfulRun(8L)).thenReturn(Optional.empty());
			when(athletes.getAthlete(8L)).thenAnswer(invocation -> {
				access.requireAdmin();
				return new AthleteResponse(8L, "Jan", "Kowalski", 2010, Gender.MALE, true, null,
						List.of(), "163067");
			});
			when(pages.fetchCurrentSeason("163067")).thenReturn("""
					<html><body>ID zawodnika<br>163067 Sezon: 2026
					<p>Wyniki sezonu</p></body></html>
					""");
			setUser(UserRole.ADMIN);
			if (groupSync) {
				when(athletes.getPzlaSyncTargetsForGroup(3L))
						.thenReturn(List.of(new AthleteService.PzlaSyncTarget(8L, "163067")));
				service.startGroupSync(3L, CompetitionSyncMode.CURRENT_SEASON);
			} else {
				service.startSync(8L, CompetitionSyncMode.CURRENT_SEASON);
			}
			SecurityContextHolder.clearContext();
			releaseWorker.countDown();

			var remainingAuthentication = executor.submit(
					() -> SecurityContextHolder.getContext().getAuthentication()).get(5, TimeUnit.SECONDS);
			assertThat(remainingAuthentication).isNull();
			verify(state).complete(eq(72L), any());
			verify(state, never()).fail(eq(72L), any(), any());
			verify(pages).fetchCurrentSeason("163067");
		} finally {
			releaseWorker.countDown();
			executor.shutdown();
		}
	}

	@ParameterizedTest
	@EnumSource(value = UserRole.class, names = {"COACH", "ATHLETE"})
	void nonAdminsCannotStartIndividualOrGroupSynchronization(UserRole role) {
		AthleteService athletes = mock(AthleteService.class);
		CompetitionSyncStateService state = mock(CompetitionSyncStateService.class);
		CompetitionSyncService service = new CompetitionSyncService(
				athletes, new AccessScopeService(mock(JdbcClient.class)), state,
				mock(PzlaPageSource.class), new PzlaPageParser(), mock(ThreadPoolTaskExecutor.class));
		setUser(role);

		assertThatThrownBy(() -> service.startSync(8L, CompetitionSyncMode.CURRENT_SEASON))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> service.startGroupSync(3L, CompetitionSyncMode.CURRENT_SEASON))
				.isInstanceOf(AccessDeniedException.class);
		verify(state, never()).createRun(anyLong(), any());
	}

	private void setUser(UserRole role) {
		AuthenticatedUser user = new AuthenticatedUser(1, "test", "", role, true);
		SecurityContextHolder.getContext().setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
	}
}
