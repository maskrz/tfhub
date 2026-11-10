package pl.tfhub.api.backup;

import java.time.OffsetDateTime;

public record BackupResponse(String fileName, OffsetDateTime executedAt, String warning) {
}
