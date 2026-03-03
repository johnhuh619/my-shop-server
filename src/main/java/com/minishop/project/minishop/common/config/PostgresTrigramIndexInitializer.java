package com.minishop.project.minishop.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

@Slf4j
@Component
@Profile({"default", "dev", "docker", "prod"})
@RequiredArgsConstructor
public class PostgresTrigramIndexInitializer implements ApplicationRunner {
    // TODO(minishop-ddl): Development convenience only.
    // Remove this initializer after pg_trgm extension/index are managed by migration SQL.

    private static final String CREATE_EXTENSION_SQL = "CREATE EXTENSION IF NOT EXISTS pg_trgm";
    private static final String CREATE_INDEX_SQL = """
            CREATE INDEX IF NOT EXISTS idx_products_name_lower_trgm
            ON products USING gin (lower(name) gin_trgm_ops)
            """;

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!isPostgreSql()) {
            return;
        }

        try {
            jdbcTemplate.execute(CREATE_EXTENSION_SQL);
            jdbcTemplate.execute(CREATE_INDEX_SQL);
            log.info("Initialized pg_trgm extension and product name trigram index");
        } catch (Exception e) {
            log.warn("Failed to initialize pg_trgm extension/index. Search still works without index optimization.", e);
        }
    }

    private boolean isPostgreSql() {
        try (Connection connection = dataSource.getConnection()) {
            String databaseProductName = connection.getMetaData().getDatabaseProductName();
            return databaseProductName != null
                    && databaseProductName.toLowerCase(Locale.ROOT).contains("postgresql");
        } catch (SQLException e) {
            log.warn("Failed to inspect database metadata for pg_trgm initialization", e);
            return false;
        }
    }
}
