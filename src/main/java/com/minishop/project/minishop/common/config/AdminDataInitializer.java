package com.minishop.project.minishop.common.config;

import com.minishop.project.minishop.user.domain.User;
import com.minishop.project.minishop.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String ADMIN_EMAIL = "admin@minishop.com";

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) {
            return;
        }

        User admin = User.createAdmin(
                ADMIN_EMAIL,
                passwordEncoder.encode("admin1234"),
                "Admin"
        );
        userRepository.save(admin);
        log.info("Default admin account created: {}", ADMIN_EMAIL);
    }
}
