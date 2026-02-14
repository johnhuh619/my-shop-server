package com.minishop.project.minishop.common.config;

import com.minishop.project.minishop.user.domain.User;
import com.minishop.project.minishop.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile({"dev", "docker"})
@RequiredArgsConstructor
public class AdminDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.init.email:admin@minishop.local}")
    private String adminEmail;

    @Value("${admin.init.password:Admin1234!}")
    private String adminPassword;

    @Value("${admin.init.name:MiniShop Admin}")
    private String adminName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(adminEmail)) {
            return;
        }

        User admin = User.createAdmin(
                adminEmail,
                passwordEncoder.encode(adminPassword),
                adminName
        );
        userRepository.save(admin);
        log.info("Initialized admin account: email={}", adminEmail);
    }
}

