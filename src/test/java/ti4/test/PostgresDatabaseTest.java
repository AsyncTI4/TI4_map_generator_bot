package ti4.test;

import java.net.URI;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class PostgresDatabaseTest {
    private static final String TEST_DATABASE = "tibot_ci_test";

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) throws SQLException {
        String url = System.getenv("TI4_TEST_POSTGRES_URL");
        String username = System.getenv().getOrDefault("TI4_TEST_POSTGRES_USER", TEST_DATABASE);
        String password = System.getenv().getOrDefault("TI4_TEST_POSTGRES_PASSWORD", TEST_DATABASE);
        configureDatasource(registry, url, username, password);
    }

    static void configureDatasource(DynamicPropertyRegistry registry, String url, String username, String password)
            throws SQLException {
        validateSettings(url, username);
        try (var connection = DriverManager.getConnection(url, username, password);
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT current_database(), current_user,
                               rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls
                        FROM pg_roles WHERE rolname = current_user
                        """)) {
            if (!result.next()
                    || !TEST_DATABASE.equals(result.getString(1))
                    || !TEST_DATABASE.equals(result.getString(2))
                    || result.getBoolean(3)) {
                throw new IllegalStateException(
                        "Database tests require the unprivileged tibot_ci_test role and database");
            }
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
    }

    static void validateSettings(String url, String username) {
        if (url == null || !url.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("Database tests require an explicit PostgreSQL URL for tibot_ci_test");
        }
        URI target = URI.create(url.substring("jdbc:".length()));
        if (target.getHost() == null
                || !target.getRawPath().equals("/" + TEST_DATABASE)
                || target.getRawQuery() != null
                || target.getRawFragment() != null
                || target.getRawUserInfo() != null
                || !TEST_DATABASE.equals(username)) {
            throw new IllegalArgumentException(
                    "Database tests require database and user tibot_ci_test, with no URL parameters or credentials");
        }
    }
}
