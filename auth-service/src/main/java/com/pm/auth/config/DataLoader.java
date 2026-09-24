package com.pm.auth.config;

import com.pm.auth.model.User;
import com.pm.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds a default admin user on first startup so the system is usable out of the box.
 * Credentials are configurable via env; defaults are for local/dev use only.
 */
@Component
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String seedEmail;
    private final String seedPassword;

    public DataLoader(UserRepository userRepository,
                      PasswordEncoder passwordEncoder,
                      @org.springframework.beans.factory.annotation.Value(
                              "${auth.seed.email:testuser@test.com}") String seedEmail,
                      @org.springframework.beans.factory.annotation.Value(
                              "${auth.seed.password:password123}") String seedPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedEmail = seedEmail;
        this.seedPassword = seedPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(seedEmail)) {
            return;
        }
        User admin = new User();
        admin.setEmail(seedEmail);
        admin.setPassword(passwordEncoder.encode(seedPassword));
        admin.setRole("ADMIN");
        userRepository.save(admin);
        log.info("Seeded default admin user: {}", seedEmail);
    }
}
