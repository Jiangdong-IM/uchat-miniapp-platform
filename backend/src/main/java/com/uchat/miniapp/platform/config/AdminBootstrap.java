package com.uchat.miniapp.platform.config;

import com.uchat.miniapp.platform.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final PlatformProperties properties;
    private final Environment environment;

    public AdminBootstrap(AccountRepository accounts, PasswordEncoder passwordEncoder,
                          PlatformProperties properties, Environment environment) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (accounts.findByUsername("admin").isPresent()) {
            return;
        }
        String password = properties.bootstrapAdminPassword();
        if (password == null || password.isBlank()) {
            if (environment.acceptsProfiles(Profiles.of("local"))) {
                password = "local-admin-12345";
                log.warn("LOCAL DEVELOPMENT ONLY: using default admin password 'local-admin-12345'; set MINIAPP_BOOTSTRAP_ADMIN_PASSWORD to override it");
            } else {
                log.warn("Admin bootstrap skipped: MINIAPP_BOOTSTRAP_ADMIN_PASSWORD is not set");
                return;
            }
        }
        if (password.length() < 12 || password.length() > 72) {
            throw new IllegalStateException("MINIAPP_BOOTSTRAP_ADMIN_PASSWORD must contain 12 to 72 characters");
        }
        accounts.insert("admin", passwordEncoder.encode(password), "ADMIN", "APPROVED",
                "平台管理", "审核开发者与小程序版本", "UChat 平台管理员",
                "admin@localhost", null);
        log.info("Platform admin account created");
    }
}
