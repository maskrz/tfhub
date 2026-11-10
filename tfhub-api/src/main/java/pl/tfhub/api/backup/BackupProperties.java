package pl.tfhub.api.backup;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "backup")
public class BackupProperties {

	private Path temporaryDirectory = Path.of("/tmp");
	private String rcloneExecutable = "rclone";
	private String rcloneConfig = "/run/secrets/rclone.conf";
	private String rcloneRemote = "tfhub-crypt";
	private String rclonePath = ".";
	private long commandTimeoutSeconds = 600;
	private int retentionCount = 3;

	public Path getTemporaryDirectory() {
		return temporaryDirectory;
	}

	public void setTemporaryDirectory(Path temporaryDirectory) {
		this.temporaryDirectory = temporaryDirectory;
	}

	public String getRcloneExecutable() {
		return rcloneExecutable;
	}

	public void setRcloneExecutable(String rcloneExecutable) {
		this.rcloneExecutable = rcloneExecutable;
	}

	public String getRcloneConfig() {
		return rcloneConfig;
	}

	public void setRcloneConfig(String rcloneConfig) {
		this.rcloneConfig = rcloneConfig;
	}

	public String getRcloneRemote() {
		return rcloneRemote;
	}

	public void setRcloneRemote(String rcloneRemote) {
		this.rcloneRemote = rcloneRemote;
	}

	public String getRclonePath() {
		return rclonePath;
	}

	public void setRclonePath(String rclonePath) {
		this.rclonePath = rclonePath;
	}

	public long getCommandTimeoutSeconds() {
		return commandTimeoutSeconds;
	}

	public void setCommandTimeoutSeconds(long commandTimeoutSeconds) {
		this.commandTimeoutSeconds = commandTimeoutSeconds;
	}

	public int getRetentionCount() {
		return retentionCount;
	}

	public void setRetentionCount(int retentionCount) {
		this.retentionCount = retentionCount;
	}
}
