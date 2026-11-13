package pl.tfhub.api.training.controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.tfhub.api.training.dto.response.AthleteTrainingHistoryResponse;
import pl.tfhub.api.training.exception.InvalidTrainingRequestException;
import pl.tfhub.api.training.service.AthleteTrainingHistoryService;

@RestController
@RequestMapping("/api/athletes/{athleteId}/training-history")
public class AthleteTrainingHistoryController {

    private final AthleteTrainingHistoryService service;

    public AthleteTrainingHistoryController(AthleteTrainingHistoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<AthleteTrainingHistoryResponse> getHistory(
            @PathVariable long athleteId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) {
        return service.getHistory(athleteId, parseDate(fromDate), parseDate(toDate));
    }

    private LocalDate parseDate(String value) {
        if (value == null) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new InvalidTrainingRequestException("Date must use the ISO calendar date format");
        }
    }
}
