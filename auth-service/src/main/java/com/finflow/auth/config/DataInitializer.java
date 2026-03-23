package com.finflow.auth.config;

import com.finflow.auth.entity.Role;
import com.finflow.auth.repository.RoleRepository;
import com.finflow.auth.entity.User;
import com.finflow.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        createRoleIfAbsent("ROLE_APPLICANT", "Regular loan applicant");
        createRoleIfAbsent("ROLE_ADMIN", "System administrator");
        createRoleIfAbsent("ROLE_VERIFIER", "Document verifier");
        log.info("Default roles initialized successfully.");
        
        createDefaultAdmin();
    }

    private void createRoleIfAbsent(String name, String desc) {
        if (roleRepository.findByRoleName(name).isEmpty()) {
            roleRepository.save(Role.builder().roleName(name).description(desc).build());
            log.info("Created role: {}", name);
        }
    }

    private void createDefaultAdmin() {
        if (!userRepository.existsByEmail("admin@finflow.com")) {
            Role adminRole = roleRepository.findByRoleName("ROLE_ADMIN").get();
            User admin = User.builder()
                    .fullName("System Administrator")
                    .email("admin@finflow.com")
                    .mobileNumber("9999999999")
                    .passwordHash(passwordEncoder.encode("Admin@123"))
                    .status(User.UserStatus.ACTIVE)
                    .build();
            admin.getRoles().add(adminRole);
            userRepository.save(admin);
            log.info("Default admin user created automatically.");
        }
    }
}
