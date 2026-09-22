package com.qryde.fooddelivery.config;

import com.qryde.fooddelivery.domain.Role;
import com.qryde.fooddelivery.domain.User;
import com.qryde.fooddelivery.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way an ADMIN account can come into existence: self-registration
 * deliberately rejects the ADMIN role (see AuthService#register). Set
 * ADMIN_BOOTSTRAP_EMAIL/ADMIN_BOOTSTRAP_PASSWORD to provision the first admin
 * on startup; leave them unset (the default) and this does nothing. Safe to
 * leave configured across restarts - it no-ops once that email already
 * exists.
 */
@Component
@Slf4j
public class AdminBootstrapRunner implements ApplicationRunner {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final String bootstrapEmail;
	private final String bootstrapPassword;

	public AdminBootstrapRunner(UserRepository userRepository,
								 PasswordEncoder passwordEncoder,
								 @Value("${app.admin.bootstrap-email:}") String bootstrapEmail,
								 @Value("${app.admin.bootstrap-password:}") String bootstrapPassword) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.bootstrapEmail = bootstrapEmail;
		this.bootstrapPassword = bootstrapPassword;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (bootstrapEmail.isBlank() || bootstrapPassword.isBlank()) {
			return;
		}
		if (userRepository.existsByEmail(bootstrapEmail)) {
			return;
		}

		User admin = User.builder()
				.email(bootstrapEmail)
				.passwordHash(passwordEncoder.encode(bootstrapPassword))
				.fullName("Platform Admin")
				.phone("+00000000000")
				.role(Role.ADMIN)
				.active(true)
				.build();
		userRepository.save(admin);
		log.info("Bootstrapped initial ADMIN account for {}", bootstrapEmail);
	}
}
