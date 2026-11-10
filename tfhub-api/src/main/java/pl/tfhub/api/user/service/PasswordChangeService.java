package pl.tfhub.api.user.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.user.dto.request.ChangePasswordRequest;
import pl.tfhub.api.user.entity.User;
import pl.tfhub.api.user.exception.IncorrectCurrentPasswordException;
import pl.tfhub.api.user.repository.UserRepository;

@Service
public class PasswordChangeService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public PasswordChangeService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public void changePassword(long userId, ChangePasswordRequest request) {
		User user = userRepository.findById(userId).orElseThrow();
		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new IncorrectCurrentPasswordException();
		}

		user.changePassword(
				passwordEncoder.encode(request.newPassword()),
				OffsetDateTime.now(ZoneOffset.UTC));
		userRepository.save(user);
	}
}
