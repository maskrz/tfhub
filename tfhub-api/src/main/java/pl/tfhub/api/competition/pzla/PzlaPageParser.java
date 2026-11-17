package pl.tfhub.api.competition.pzla;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import pl.tfhub.api.competition.enums.CompetitionSeasonType;

public class PzlaPageParser {

	private static final Pattern ATHLETE_ID = Pattern.compile(
			"(?is)ID\\s*zawodnika\\s*<br\\s*/?>\\s*(\\d+)");
	private static final Pattern CURRENT_SEASON = Pattern.compile("(?i)Sezon\\s*:\\s*(20\\d{2})");
	private static final Pattern SEASON_PARAMETER = Pattern.compile(
			"(?i)(?:\\?|&)sezon=(\\d{4})&sezon2=([LZ])(?:&|$)");
	private static final Pattern DATE = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
	private static final Pattern MARK = Pattern.compile(
			"(?i)(?<![\\p{L}\\d])((?:\\d{1,2}:)?\\d{1,2}:\\d{1,2}(?:[.,]\\d+)?|\\d+(?:[.,]\\d+)?)(?![\\p{L}\\d])");
	private static final Pattern WIND = Pattern.compile("\\(([+-]?\\d+(?:[.,]\\d+)?)\\)");
	private static final Pattern MEET_ID = Pattern.compile("(?i)(?:\\?|&)Nr\\[\\]=?(\\d+)");
	private static final Pattern SERIES = Pattern.compile("(?i)Seria\\s*:\\s*(.+)$");
	private static final Pattern PLACE = Pattern.compile("(?i)Miejsce\\s*:\\s*\\d+");
	private static final Pattern TRAILING_COUNT = Pattern.compile("\\s*\\(\\d+\\)\\s*$");

	public PzlaParsedPage parse(
			String html,
			String expectedPzlaId,
			Integer requestedSeason,
			CompetitionSeasonType requestedSeasonType) {
		if (html == null || html.isBlank()) {
			throw invalidPage();
		}
		String normalizedHtml = html.toLowerCase(Locale.ROOT);
		if (normalizedHtml.contains("dostęp zablokowany")
				|| normalizedHtml.contains("captcha")
				|| normalizedHtml.contains("access denied")) {
			throw new PzlaSynchronizationException("PZLA_ACCESS_BLOCKED", "PZLA access was blocked");
		}
		Matcher athleteMatcher = ATHLETE_ID.matcher(html);
		if (!athleteMatcher.find() || !expectedPzlaId.equals(athleteMatcher.group(1))) {
			throw invalidPage();
		}

		HtmlDocument document = parseHtml(html);
		if (!normalize(document.text.toString()).contains("wyniki sezonu")) {
			throw invalidPage();
		}
		int pageCurrentSeason = findCurrentSeason(document.text.toString());
		int resultSeason = requestedSeason == null ? pageCurrentSeason : requestedSeason;
		CompetitionSeasonType seasonType = requestedSeasonType == null
				? CompetitionSeasonType.UNKNOWN
				: requestedSeasonType;

		List<PzlaSeason> availableSeasons = findAvailableSeasons(document.links);
		List<PzlaResult> results = new ArrayList<>();
		for (HtmlRow row : document.rows) {
			PzlaResult result = parseResultRow(row, resultSeason, seasonType);
			if (result != null) {
				results.add(result);
			}
		}
		return new PzlaParsedPage(pageCurrentSeason, availableSeasons, results);
	}

	// The current-season page has no indoor/outdoor marker: April-October is outdoor, otherwise indoor.
	private CompetitionSeasonType seasonTypeFor(LocalDate date) {
		int month = date.getMonthValue();
		return month >= 4 && month <= 10 ? CompetitionSeasonType.OUTDOOR : CompetitionSeasonType.INDOOR;
	}

	private PzlaResult parseResultRow(
			HtmlRow row,
			int season,
			CompetitionSeasonType seasonType) {
		if (row.cells.size() < 5) {
			return null;
		}
		HtmlCell disciplineCell = row.cells.get(0);
		HtmlCell markCell = row.cells.get(1);
		HtmlCell dateCell = row.cells.stream()
				.filter(cell -> DATE.matcher(cell.text.toString()).find())
				.findFirst()
				.orElse(null);
		if (dateCell == null || disciplineCell.text.toString().isBlank()) {
			return null;
		}
		String discipline = cleanText(disciplineCell.text.toString());
		String normalizedDiscipline = normalize(discipline);
		if (isCrossCountry(normalizedDiscipline)) {
			return null;
		}

		LocalDate resultDate;
		try {
			Matcher dateMatcher = DATE.matcher(dateCell.text.toString());
			if (!dateMatcher.find()) {
				throw invalidPage();
			}
			resultDate = LocalDate.parse(dateMatcher.group(1));
		} catch (DateTimeParseException exception) {
			throw invalidPage();
		}

		String markText = cleanText(markCell.text.toString());
		if (markText.isBlank()) {
			return null;
		}
		Matcher windMatcher = WIND.matcher(markText);
		BigDecimal wind = windMatcher.find() ? decimal(windMatcher.group(1)) : null;
		String originalMark = extractOriginalMark(markText);
		if (originalMark.isBlank() || originalMark.length() > 50) {
			throw invalidPage();
		}
		String unit = unitFor(discipline);
		BigDecimal numericValue = numericValue(originalMark);
		String eventSourceId = digitsOrNull(disciplineCell.title);
		String competitionSourceId = meetId(row.cells.get(4).links);
		String competitionName = firstNonBlank(
				row.cells.get(4).title, row.cells.get(4).text.toString());
		String location = location(row.cells.get(4).links, row.cells.get(4).text.toString());
		if (isCrossCountry(normalize(competitionName))) {
			return null;
		}
		String resultRound = roundFromTitle(markCell.title);
		String identity = eventSourceId != null && competitionSourceId != null
				? String.join("|", eventSourceId, competitionSourceId, resultDate.toString(), normalize(resultRound))
				: String.join("|",
						valueOrEmpty(eventSourceId),
						normalize(discipline),
						resultDate.toString(),
						normalize(resultRound),
						normalize(competitionName),
						normalize(location));
		String fingerprint = sha256(identity);
		String sourceKey = eventSourceId == null || competitionSourceId == null ? null : fingerprint;

		return new PzlaResult(
				eventSourceId,
				discipline,
				unit,
				resultDate,
				originalMark,
				numericValue,
				wind,
				season,
				seasonType == CompetitionSeasonType.UNKNOWN ? seasonTypeFor(resultDate) : seasonType,
				competitionSourceId,
				emptyToNull(competitionName),
				emptyToNull(location),
				emptyToNull(resultRound),
				sourceKey,
				fingerprint);
	}

	private String extractOriginalMark(String markText) {
		String withoutWind = WIND.matcher(markText).replaceFirst("").strip();
		Matcher markMatcher = MARK.matcher(withoutWind);
		if (markMatcher.find()) {
			return markMatcher.group(1);
		}
		return cleanText(withoutWind);
	}

	private BigDecimal numericValue(String mark) {
		Matcher matcher = MARK.matcher(mark);
		if (!matcher.find()) {
			return null;
		}
		String numericMark = matcher.group(1).replace(',', '.');
		if (!numericMark.contains(":")) {
			return decimal(numericMark);
		}
		String[] parts = numericMark.split(":");
		BigDecimal value = BigDecimal.ZERO;
		for (String part : parts) {
			value = value.multiply(BigDecimal.valueOf(60)).add(decimal(part));
		}
		return value;
	}

	private BigDecimal decimal(String value) {
		try {
			return new BigDecimal(value.replace(',', '.'));
		} catch (NumberFormatException exception) {
			throw invalidPage();
		}
	}

	private String unitFor(String discipline) {
		String value = normalize(discipline);
		if (value.contains("wieloboj") || value.contains("piecioboj")
				|| value.contains("siedmioboj") || value.contains("dziesiecioboj")) {
			return "pkt";
		}
		if (value.contains("skok") || value.contains("wzwyz") || value.contains("tyczka")
				|| value.contains("rzut") || value.contains("pchni") || value.contains("w dal")
				|| value.contains("dysk") || value.contains("oszczep") || value.contains("mlot")
				|| value.contains("kula")) {
			return "m";
		}
		return "s";
	}

	private String roundFromTitle(String title) {
		if (title == null) {
			return null;
		}
		Matcher seriesMatcher = SERIES.matcher(title.strip());
		if (seriesMatcher.find()) {
			return cleanText(seriesMatcher.group(1));
		}
		return cleanText(PLACE.matcher(title).replaceAll(""));
	}

	private String location(List<HtmlLink> links, String cellText) {
		for (HtmlLink link : links) {
			if (link.href.toLowerCase(Locale.ROOT).contains("mityng.php")) {
				return cleanText(TRAILING_COUNT.matcher(link.text).replaceFirst(""));
			}
		}
		return cleanText(cellText);
	}

	private String meetId(List<HtmlLink> links) {
		for (HtmlLink link : links) {
			if (link.href.toLowerCase(Locale.ROOT).contains("mityng.php")) {
				Matcher matcher = MEET_ID.matcher(link.href);
				if (matcher.find()) {
					return matcher.group(1);
				}
			}
		}
		return null;
	}

	private List<PzlaSeason> findAvailableSeasons(List<HtmlLink> links) {
		Set<PzlaSeason> seasons = new HashSet<>();
		for (HtmlLink link : links) {
			Matcher matcher = SEASON_PARAMETER.matcher(link.href);
			if (matcher.find()) {
				int year = Integer.parseInt(matcher.group(1));
				CompetitionSeasonType type = matcher.group(2).equalsIgnoreCase("L")
						? CompetitionSeasonType.OUTDOOR
						: CompetitionSeasonType.INDOOR;
				seasons.add(new PzlaSeason(year, type));
			}
		}
		return seasons.stream()
				.sorted((left, right) -> {
					int yearOrder = Integer.compare(right.year(), left.year());
					return yearOrder != 0 ? yearOrder : left.type().compareTo(right.type());
				})
				.toList();
	}

	private int findCurrentSeason(String text) {
		Matcher matcher = CURRENT_SEASON.matcher(text);
		if (!matcher.find()) {
			throw invalidPage();
		}
		return Integer.parseInt(matcher.group(1));
	}

	private String digitsOrNull(String value) {
		return value != null && value.matches("\\d+") ? value : null;
	}

	private String firstNonBlank(String preferred, String fallback) {
		return preferred == null || preferred.isBlank() ? cleanText(fallback) : cleanText(preferred);
	}

	private String cleanText(String value) {
		return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").strip();
	}

	private String emptyToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private String valueOrEmpty(String value) {
		return value == null ? "" : value;
	}

	private String normalize(String value) {
		return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
				.replaceAll("\\p{M}+", "")
				.toLowerCase(Locale.ROOT)
				.replace('ł', 'l')
				.replaceAll("\\s+", " ")
				.strip();
	}

	private boolean isCrossCountry(String normalizedName) {
		return normalizedName.contains("przelaj")
				|| normalizedName.contains("cross country")
				|| normalizedName.contains("cross-country");
	}

	private String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				hex.append(String.format(Locale.ROOT, "%02x", b));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
		}
	}

	private HtmlDocument parseHtml(String html) {
		HtmlDocument document = new HtmlDocument();
		try {
			new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
				private boolean ignored;
				private HtmlRow currentRow;
				private HtmlCell currentCell;
				private HtmlLinkBuilder currentLink;

				@Override
				public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
					if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) {
						ignored = true;
					} else if (tag == HTML.Tag.TR) {
						closeCell();
						closeRow();
						currentRow = new HtmlRow();
					} else if (tag == HTML.Tag.TD || tag == HTML.Tag.TH) {
						closeCell();
						if (currentRow != null) {
							currentCell = new HtmlCell(attribute(attributes, HTML.Attribute.TITLE));
						}
					} else if (tag == HTML.Tag.A) {
						currentLink = new HtmlLinkBuilder(attribute(attributes, HTML.Attribute.HREF));
					}
				}

				@Override
				public void handleEndTag(HTML.Tag tag, int position) {
					if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) {
						ignored = false;
					} else if (tag == HTML.Tag.A) {
						closeLink();
					} else if (tag == HTML.Tag.TD || tag == HTML.Tag.TH) {
						closeCell();
					} else if (tag == HTML.Tag.TR) {
						closeCell();
						closeRow();
					}
				}

				@Override
				public void handleText(char[] data, int position) {
					if (ignored) {
						return;
					}
					String text = new String(data);
					document.text.append(text).append(' ');
					if (currentCell != null) {
						currentCell.text.append(text).append(' ');
					}
					if (currentLink != null) {
						currentLink.text.append(text).append(' ');
					}
				}

				private String attribute(MutableAttributeSet attributes, HTML.Attribute attribute) {
					Object value = attributes.getAttribute(attribute);
					return value == null ? null : value.toString();
				}

				private void closeLink() {
					if (currentLink != null) {
						HtmlLink link = new HtmlLink(
								currentLink.href == null ? "" : currentLink.href,
								cleanText(currentLink.text.toString()));
						document.links.add(link);
						if (currentCell != null) {
							currentCell.links.add(link);
						}
						currentLink = null;
					}
				}

				private void closeCell() {
					if (currentCell != null && currentRow != null) {
						currentCell.finish();
						currentRow.cells.add(currentCell);
						currentCell = null;
					}
				}

				private void closeRow() {
					if (currentRow != null) {
						document.rows.add(currentRow);
						currentRow = null;
					}
				}
			}, true);
		} catch (IOException exception) {
			throw new PzlaSynchronizationException("PZLA_PAGE_INVALID", "PZLA page could not be parsed", exception);
		}
		return document;
	}

	private PzlaSynchronizationException invalidPage() {
		return new PzlaSynchronizationException(
				"PZLA_PAGE_INVALID", "PZLA page does not match the expected results format");
	}

	private static final class HtmlDocument {
		private final StringBuilder text = new StringBuilder();
		private final List<HtmlRow> rows = new ArrayList<>();
		private final List<HtmlLink> links = new ArrayList<>();
	}

	private static final class HtmlRow {
		private final List<HtmlCell> cells = new ArrayList<>();
	}

	private static final class HtmlCell {
		private final String title;
		private final StringBuilder text = new StringBuilder();
		private final List<HtmlLink> links = new ArrayList<>();

		private HtmlCell(String title) {
			this.title = title;
		}

		private void finish() {
			String normalized = text.toString().replace('\u00a0', ' ').replaceAll("\\s+", " ").strip();
			text.setLength(0);
			text.append(normalized);
		}
	}

	private record HtmlLink(String href, String text) {
	}

	private static final class HtmlLinkBuilder {
		private final String href;
		private final StringBuilder text = new StringBuilder();

		private HtmlLinkBuilder(String href) {
			this.href = href;
		}
	}
}
