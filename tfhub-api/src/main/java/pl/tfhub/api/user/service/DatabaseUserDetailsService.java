package pl.tfhub.api.user.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import pl.tfhub.api.user.entity.User;
import pl.tfhub.api.user.repository.UserRepository;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	public DatabaseUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		User user = userRepository.findByNormalizedUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
		return new AuthenticatedUser(user.getId(), user.getUsername(), user.getPasswordHash(),
				user.getRole(), user.isActive());
	}
}
