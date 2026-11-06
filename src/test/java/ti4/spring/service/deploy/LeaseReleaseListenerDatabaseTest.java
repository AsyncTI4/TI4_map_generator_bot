package ti4.spring.service.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ti4.test.PostgresDatabaseTest;

// Runs against a real Postgres, e.g. a throwaway database in a local container:
//   TI4_TEST_POSTGRES_URL=jdbc:postgresql://localhost:55432/tibot_ci_test
// Skipped when the variable is unset. CI provides a dedicated test database.
@EnabledIfEnvironmentVariable(named = "TI4_TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(
        classes = LeaseReleaseListenerDatabaseTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class LeaseReleaseListenerDatabaseTest extends PostgresDatabaseTest {

    // Well under the listener's 5 s wait, so a pass proves the wake-up came from NOTIFY and not from the timeout.
    private static final Duration PROMPT = Duration.ofMillis(1500);

    @Autowired
    private LeaseReleaseListener listener;

    @Autowired
    private ActiveLeaseTransactionService transactionService;

    @Autowired
    private ActiveLeaseRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final Semaphore callbacks = new Semaphore(0);
    private final List<String> sources = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startListening() throws InterruptedException {
        repository.deleteAll();
        listener.start(source -> {
            sources.add(source);
            callbacks.release();
        });
        assertThat(callbacks.tryAcquire(5, TimeUnit.SECONDS))
                .as("catch-up callback after LISTEN")
                .isTrue();
    }

    @AfterEach
    void stopListening() {
        listener.stop();
    }

    @Test
    void releasingTheLeaseWakesTheListenerWithoutWaitingForAPoll() throws InterruptedException {
        assertThat(transactionService.tryAcquireLease("old-instance")).isTrue();

        long startedAt = System.nanoTime();
        transactionService.releaseLease("old-instance");

        assertThat(callbacks.tryAcquire(PROMPT.toMillis(), TimeUnit.MILLISECONDS))
                .isTrue();
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(PROMPT);
        assertThat(sources)
                .containsExactly(LeaseReleaseListener.SOURCE_CATCH_UP, LeaseReleaseListener.SOURCE_NOTIFICATION);
    }

    @Test
    void aReleaseThatRollsBackSendsNoNotification() throws InterruptedException {
        assertThat(transactionService.tryAcquireLease("old-instance")).isTrue();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            transactionService.releaseLease("old-instance");
            status.setRollbackOnly();
        });

        assertThat(callbacks.tryAcquire(500, TimeUnit.MILLISECONDS)).isFalse();
    }

    @Test
    void aStoppedListenerIgnoresLaterReleases() throws InterruptedException {
        assertThat(transactionService.tryAcquireLease("old-instance")).isTrue();
        listener.stop();

        transactionService.releaseLease("old-instance");

        assertThat(callbacks.tryAcquire(500, TimeUnit.MILLISECONDS)).isFalse();
    }

    @Test
    void releaseByAnInstanceThatDoesNotOwnTheLeaseSendsNoNotification() throws InterruptedException {
        assertThat(transactionService.tryAcquireLease("old-instance")).isTrue();

        transactionService.releaseLease("someone-else");

        assertThat(callbacks.tryAcquire(500, TimeUnit.MILLISECONDS)).isFalse();
    }

    @Configuration
    @ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        TransactionAutoConfiguration.class
    })
    @EntityScan(basePackageClasses = ActiveLeaseEntity.class)
    @EnableJpaRepositories(basePackageClasses = ActiveLeaseRepository.class)
    @Import({ActiveLeaseTransactionService.class, LeaseProperties.class, LeaseReleaseListener.class})
    static class TestConfig {

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
