package pl.tfhub.api.backup;

import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

@Component
public class BackupExecutionLock {

	private final ReentrantLock lock = new ReentrantLock();

	public boolean tryAcquire() {
		return lock.tryLock();
	}

	public void release() {
		lock.unlock();
	}
}
