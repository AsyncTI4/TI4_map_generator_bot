package ti4.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;

class PostgresDatabaseSafetyTest {
    @Test
    void destructiveSpringTestsMustUseTheSharedGuard() {
        var springTests = new Reflections(
                        new ConfigurationBuilder().forPackage("ti4").setScanners(Scanners.TypesAnnotated))
                .getTypesAnnotatedWith(SpringBootTest.class);
        assertThat(springTests)
                .isNotEmpty()
                .filteredOn(type -> Arrays.stream(
                                type.getAnnotation(SpringBootTest.class).properties())
                        .anyMatch(property -> property.contains("create-drop")))
                .isNotEmpty()
                .allSatisfy(type -> assertThat(PostgresDatabaseTest.class.isAssignableFrom(type))
                        .as("%s must extend PostgresDatabaseTest", type.getName())
                        .isTrue());
    }

    @Test
    void productionDatabaseIsRejectedWithoutConnectingOrRegisteringProperties() {
        var registry = mock(DynamicPropertyRegistry.class);
        try (var driver = mockStatic(DriverManager.class)) {
            assertThatThrownBy(() -> PostgresDatabaseTest.configureDatasource(
                            registry, "jdbc:postgresql://localhost/tibot", "tibot_ci_test", "unused"))
                    .isInstanceOf(IllegalArgumentException.class);
            driver.verifyNoInteractions();
            verifyNoInteractions(registry);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-database", "wrong-user", "privileged"})
    void unsafeServerIdentityIsRejectedBeforeHibernatePropertiesAreRegistered(String scenario) throws Exception {
        var registry = mock(DynamicPropertyRegistry.class);
        var connection = mock(Connection.class);
        var statement = mock(Statement.class);
        var result = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getString(1)).thenReturn("wrong-database".equals(scenario) ? "tibot" : "tibot_ci_test");
        when(result.getString(2)).thenReturn("wrong-user".equals(scenario) ? "tibot" : "tibot_ci_test");
        when(result.getBoolean(3)).thenReturn("privileged".equals(scenario));
        String url = "jdbc:postgresql://localhost/tibot_ci_test";
        try (var driver = mockStatic(DriverManager.class)) {
            driver.when(() -> DriverManager.getConnection(url, "tibot_ci_test", "unused"))
                    .thenReturn(connection);
            assertThatThrownBy(() -> PostgresDatabaseTest.configureDatasource(registry, url, "tibot_ci_test", "unused"))
                    .isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(registry);
        }
    }

    @Test
    void acceptsDedicatedTestDatabase() {
        assertThatCode(() -> PostgresDatabaseTest.validateSettings(
                        "jdbc:postgresql://localhost:55432/tibot_ci_test", "tibot_ci_test"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                "jdbc:postgresql://localhost:5432/tibot",
                "jdbc:postgresql://production/tibot_test",
                "jdbc:postgresql://localhost/",
                "jdbc:postgresql:tibot_ci_test",
                "jdbc:postgresql://localhost/tibot_ci_test?PGDBNAME=tibot",
                "jdbc:postgresql://localhost/tibot_ci_test?user=tibot",
                "jdbc:postgresql://localhost/tibot_ci_test#tibot",
                "jdbc:postgresql://tibot:secret@localhost/tibot_ci_test",
                "jdbc:postgresql://localhost/tibot_ci_test/../tibot",
                "jdbc:postgresql://localhost/%74ibot_ci_test",
                "jdbc:postgresql://localhost/tibot_ci_test%3FPGDBNAME=tibot"
            })
    void rejectsUnsafeOrAmbiguousTargetsBeforeConnecting(String url) {
        assertThatThrownBy(() -> PostgresDatabaseTest.validateSettings(url, "tibot_ci_test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "tibot", "postgres"})
    void rejectsOtherUsers(String username) {
        assertThatThrownBy(() ->
                        PostgresDatabaseTest.validateSettings("jdbc:postgresql://localhost/tibot_ci_test", username))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
