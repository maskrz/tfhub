package pl.tfhub.api.trainingimport.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import pl.tfhub.api.athlete.service.AthleteService;
import pl.tfhub.api.athlete.service.AthleteService.ImportCandidate;

@Component
class TrainingImportAthleteMatcher {

	private final AthleteService athleteService;

	TrainingImportAthleteMatcher(AthleteService athleteService) {
		this.athleteService = athleteService;
	}

	Match match(TrainingImportRecognitionData.AthleteRow row, List<ImportCandidate> candidates) {
		String name = athleteService.normalizeImportName(row.recognizedName());
		Set<Long> matches = new LinkedHashSet<>();
		if (!name.isEmpty()) {
			for (ImportCandidate candidate : candidates) {
				if (candidate.normalizedAliases().contains(name)
						|| candidate.normalizedFullName().equals(name)) {
					matches.add(candidate.id());
				}
			}
		}
		if (Boolean.TRUE.equals(row.uncertain())) {
			return new Match(null, "The recognized athlete name is uncertain", suggestions(row, candidates));
		}
		if (matches.size() > 1) {
			return new Match(null, "The recognized name is ambiguous", List.of());
		}
		if (matches.size() == 1) {
			return new Match(matches.iterator().next(), null, List.of());
		}
		return new Match(null, "No athlete matched the recognized name", suggestions(row, candidates));
	}

	private List<Long> suggestions(TrainingImportRecognitionData.AthleteRow row, List<ImportCandidate> candidates) {
		if (row.firstName() == null || row.firstName().isBlank()
				|| row.lastName() == null || row.lastName().isBlank()) {
			return List.of();
		}
		String surname = letters(athleteService.normalizeImportName(row.lastName()));
		if (surname.isEmpty()) {
			return List.of();
		}
		int maximumDistance = surname.length() <= 7 ? 1 : 2;
		List<Long> suggestions = new ArrayList<>();
		for (ImportCandidate candidate : candidates) {
			String candidateSurname = letters(athleteService.normalizeImportName(candidate.lastName()));
			if (distance(surname, candidateSurname) <= maximumDistance) {
				suggestions.add(candidate.id());
			}
		}
		return List.copyOf(suggestions);
	}

	private String letters(String value) {
		return value.replaceAll("[^\\p{L}]", "");
	}

	private int distance(String left, String right) {
		int[] previous = new int[right.length() + 1];
		for (int column = 0; column <= right.length(); column++) {
			previous[column] = column;
		}
		for (int row = 1; row <= left.length(); row++) {
			int[] current = new int[right.length() + 1];
			current[0] = row;
			for (int column = 1; column <= right.length(); column++) {
				int substitution = left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1;
				current[column] = Math.min(Math.min(current[column - 1] + 1, previous[column] + 1),
						previous[column - 1] + substitution);
			}
			previous = current;
		}
		return previous[right.length()];
	}

	record Match(Long athleteId, String reason, List<Long> suggestionIds) {
	}
}
