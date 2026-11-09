package pl.tfhub.api.user.service;

import java.io.Console;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

public final class PasswordHashGenerator {

	private PasswordHashGenerator() {
	}

	public static void main(String[] args) {
		Console console = System.console();
		if (console == null) {
			throw new IllegalStateException("Run this utility from an interactive terminal");
		}
		char[] password = console.readPassword("Password to hash: ");
		if (password == null || password.length == 0) {
			throw new IllegalArgumentException("Password must not be empty");
		}
		try {
			Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
			System.out.println(encoder.encode(new String(password)));
		} finally {
			java.util.Arrays.fill(password, '\0');
		}
	}
}
