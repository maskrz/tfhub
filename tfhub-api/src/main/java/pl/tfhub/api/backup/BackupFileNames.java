package pl.tfhub.api.backup;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BackupFileNames {

	private static final Pattern FILE_NAME_PATTERN = Pattern.compile(
			"^tfhub_backup_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})\\.xlsx$");
	private static final DateTimeFormatter TIMESTAMP_FORMAT =
			DateTimeFormatter.ofPattern("uuuu-MM-dd_HH-mm-ss").withResolverStyle(ResolverStyle.STRICT);

	private BackupFileNames() {
	}

	static boolean isValid(String fileName) {
		return timestamp(fileName) != null;
	}

	static LocalDateTime timestamp(String fileName) {
		Matcher matcher = FILE_NAME_PATTERN.matcher(fileName);
		if (!matcher.matches()) {
			return null;
		}
		try {
			return LocalDateTime.parse(matcher.group(1), TIMESTAMP_FORMAT);
		} catch (DateTimeParseException exception) {
			return null;
		}
	}
}
