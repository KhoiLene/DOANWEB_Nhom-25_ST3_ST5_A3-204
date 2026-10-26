package com.shopchain.config;

import com.shopchain.entity.Role;
import com.shopchain.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RoleDataInitializer implements CommandLineRunner {
    private final RoleRepository roleRepository;

    public RoleDataInitializer(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public void run(String... args) {
        for (String roleName : List.of("ROLE_USER", "ROLE_WAREHOUSE", "ROLE_STORE_STAFF", "ROLE_ADMIN")) {
            if (roleRepository.findByName(roleName).isEmpty()) {
                roleRepository.save(Role.builder().name(roleName).build());
            }
        }
    }
}
