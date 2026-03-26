package com.finflow.auth.config;

import com.finflow.auth.entity.Role;
import com.finflow.auth.entity.User;
import com.finflow.auth.repository.RoleRepository;
import com.finflow.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private DataInitializer dataInitializer;

    @Test
    void run_WhenDataAbsent_ShouldCreateData() {
        Role adminRole = Role.builder().id(2).roleName("ROLE_ADMIN").build();

        when(roleRepository.findByRoleName("ROLE_APPLICANT")).thenReturn(Optional.empty());
        when(roleRepository.findByRoleName("ROLE_VERIFIER")).thenReturn(Optional.empty());
        when(roleRepository.findByRoleName("ROLE_ADMIN"))
                .thenReturn(Optional.empty()) // First call for createRoleIfAbsent
                .thenReturn(Optional.of(adminRole)); // Second call for createDefaultAdmin

        when(userRepository.existsByEmail("admin@finflow.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
        
        dataInitializer.run();

        verify(roleRepository, times(3)).save(any(Role.class));
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void run_WhenDataPresent_ShouldNotCreateData() {
        Role adminRole = Role.builder().id(2).roleName("ROLE_ADMIN").build();

        when(roleRepository.findByRoleName("ROLE_APPLICANT")).thenReturn(Optional.of(Role.builder().build()));
        when(roleRepository.findByRoleName("ROLE_VERIFIER")).thenReturn(Optional.of(Role.builder().build()));
        when(roleRepository.findByRoleName("ROLE_ADMIN")).thenReturn(Optional.of(adminRole)); 
        when(userRepository.existsByEmail("admin@finflow.com")).thenReturn(true);
        
        dataInitializer.run();

        verify(roleRepository, never()).save(any(Role.class));
        verify(userRepository, never()).save(any(User.class));
    }
}
