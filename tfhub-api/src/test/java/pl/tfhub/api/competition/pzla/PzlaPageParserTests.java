package pl.tfhub.api.competition.pzla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import pl.tfhub.api.competition.enums.CompetitionSeasonType;

class PzlaPageParserTests {

	private final PzlaPageParser parser = new PzlaPageParser();

	@Test
	void parsesAvailableSeasonsFinalMarksWindAndCompetitionMetadata() {
		PzlaParsedPage page = parser.parse(
				html(), "163067", 2025, CompetitionSeasonType.OUTDOOR);

		assertThat(page.currentSeason()).isEqualTo(2026);
		assertThat(page.availableSeasons()).containsExactly(
				new PzlaSeason(2026, CompetitionSeasonType.INDOOR),
				new PzlaSeason(2025, CompetitionSeasonType.INDOOR),
				new PzlaSeason(2025, CompetitionSeasonType.OUTDOOR));
		assertThat(page.results()).hasSize(2);

		PzlaResult sprint = page.results().getFirst();
		assertThat(sprint.eventSourceId()).isEqualTo("40159");
		assertThat(sprint.eventName()).isEqualTo("100 m");
		assertThat(sprint.unit()).isEqualTo("s");
		assertThat(sprint.originalMark()).isEqualTo("14.18");
		assertThat(sprint.numericValue()).hasToString("14.18");
		assertThat(sprint.wind()).hasToString("-1.4");
		assertThat(sprint.resultDate()).hasToString("2025-05-24");
		assertThat(sprint.competitionName()).isEqualTo("Mistrzostwa Dolnego Śląska U16 2025");
		assertThat(sprint.location()).isEqualTo("Karpacz");
		assertThat(sprint.resultRound()).isEqualTo("3 s");
		assertThat(sprint.sourceRecordKey()).isNotBlank();
		assertThat(sprint.season()).isEqualTo(2025);
		assertThat(sprint.seasonType()).isEqualTo(CompetitionSeasonType.OUTDOOR);

		PzlaResult longJump = page.results().get(1);
		assertThat(longJump.eventName()).isEqualTo("W dal strefa");
		assertThat(longJump.unit()).isEqualTo("m");
		assertThat(longJump.originalMark()).isEqualTo("4,23");
		assertThat(longJump.numericValue()).hasToString("4.23");
		assertThat(longJump.wind()).hasToString("0.0");
	}

	@Test
	void parsesARequestedSeasonAndPreservesNonnumericMarks() {
		PzlaParsedPage page = parser.parse(
				html().replace("<b>14.18</b>", "<b>DNS</b>"),
				"163067",
				2025,
				CompetitionSeasonType.OUTDOOR);

		assertThat(page.results().getFirst().originalMark()).isEqualTo("DNS");
		assertThat(page.results().getFirst().numericValue()).isNull();
		assertThat(page.results().getFirst().season()).isEqualTo(2025);
		assertThat(page.results().getFirst().seasonType()).isEqualTo(CompetitionSeasonType.OUTDOOR);
	}

	@Test
	void ignoresDatedRowsWithoutAFinalMark() {
		String htmlWithBlankResult = html().replace("</table></body>",
				"""
				<tr><td title="40159">200 m</td><td></td><td></td><td>2025-05-24</td>
				<td title="Meeting"><a href="mityng.php?Nr[]=12345">Karpacz</a></td><td></td></tr>
				</table></body>
				""");

		assertThat(parser.parse(htmlWithBlankResult, "163067", 2025, CompetitionSeasonType.OUTDOOR).results())
				.hasSize(2);
	}

	@Test
	void retainsTheSameSourceIdentityWhenPzlaCorrectsMarkOrMeetMetadata() {
		PzlaResult original = parser.parse(html(), "163067", 2025, CompetitionSeasonType.OUTDOOR)
				.results().getFirst();
		String correctedHtml = html()
				.replace("14.18", "14.12")
				.replace("Mistrzostwa Dolnego Śląska U16 2025", "Mistrzostwa Dolnego Śląska U16")
				.replace(">Karpacz <", ">Nowa miejscowość <");
		PzlaResult corrected = parser.parse(
				correctedHtml, "163067", 2025, CompetitionSeasonType.OUTDOOR).results().getFirst();

		assertThat(corrected.sourceFingerprint()).isEqualTo(original.sourceFingerprint());
		assertThat(corrected.originalMark()).isEqualTo("14.12");
	}

	@Test
	void rejectsAResponseForAnotherAthleteOrBlockedPage() {
		assertThatThrownBy(() -> parser.parse(html(), "999999", null, null))
				.isInstanceOf(PzlaSynchronizationException.class)
				.hasMessageContaining("expected results format");
		assertThatThrownBy(() -> parser.parse(
				"<html><body>Dostęp zablokowany</body></html>", "163067", null, null))
				.isInstanceOf(PzlaSynchronizationException.class)
				.hasMessageContaining("access was blocked");
	}

	private String html() {
		return """
				<html lang="pl"><head><title>Sezon</title></head><body>
				<table><tr><td>ID zawodnika<br>163067</td><td>Sezon: 2026</td></tr></table>
				<p>Wyniki sezonu</p>
				<a href="personal.php?page=sb&amp;nr_zaw=163067&amp;r=2&amp;sezon=2026&amp;sezon2=Z">2026</a>
				<a href="personal.php?page=sb&amp;nr_zaw=163067&amp;r=2&amp;sezon=2025&amp;sezon2=L">2025</a>
				<a href="personal.php?page=sb&amp;nr_zaw=163067&amp;r=2&amp;sezon=2025&amp;sezon2=Z">2025</a>
				<table>
				<tr>
				 <td title="40159">100 m</td>
				 <td title="Miejsce: 1 Seria: 3 s"><font><b>14.18</b> (-1.4)</font></td>
				 <td></td><td>2025-05-24</td>
				 <td title="Mistrzostwa Dolnego Śląska U16 2025">
				  <a href="mityng.php?Sezon=2025L&amp;Nr[]=10312">Karpacz <small>(1)</small></a>
				 </td><td>V</td>
				</tr>
				<tr><td></td><td colspan="5">x 14.01 14.18 14.09</td></tr>
				<tr>
				 <td title="40405">W dal strefa</td>
				 <td title="Miejsce: 7 Seria: 1 f"><font><b>4,23</b> (0.0)</font></td>
				 <td></td><td>2025-05-24</td>
				 <td title="Mistrzostwa Dolnego Śląska U16 2025">
				  <a href="mityng.php?Sezon=2025L&amp;Nr[]=10312">Karpacz <small>(1)</small></a>
				 </td><td>--</td>
				</tr>
				<tr><td></td><td colspan="5">x 4.09 4.14 3.92 4.23 4.20</td></tr>
				<tr>
				 <td title="50999">Bieg przełajowy</td>
				 <td><b>2:14.03</b></td><td></td><td>2025-05-24</td>
				 <td title="Bieg"><a href="mityng.php?Nr[]=12345">Las</a></td><td></td>
				</tr>
				</table></body></html>
				""";
	}
}
