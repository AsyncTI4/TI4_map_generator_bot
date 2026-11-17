package ti4.spring.service.messagecache;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ti4.test.PostgresDatabaseTest;

// Runs against a real Postgres, e.g. a throwaway database in a local container:
//   TI4_TEST_POSTGRES_URL=jdbc:postgresql://localhost:55432/tibot_ci_test
// Skipped when the variable is unset. CI provides a dedicated test database.
@EnabledIfEnvironmentVariable(named = "TI4_TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(
        classes = BotDiscordMessageCleanupDatabaseTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class BotDiscordMessageCleanupDatabaseTest extends PostgresDatabaseTest {

    @Autowired
    private BotDiscordMessageEntityRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearTable() {
        repository.deleteAll();
    }

    @Test
    void bulkDeleteRemovesOnlyMessagesOlderThanTheCutoff() {
        repository.save(new BotDiscordMessageEntity(1, "old", 100));
        repository.save(new BotDiscordMessageEntity(2, "also old", 199));
        repository.save(new BotDiscordMessageEntity(3, "kept", 200));

        int deleted = new TransactionTemplate(transactionManager)
                .execute(_ -> repository.deleteByCreatedAtEpochMillisLessThan(200));

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.findAll())
                .extracting(BotDiscordMessageEntity::getMessageId)
                .containsExactly(3L);
    }

    @Configuration
    @ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        TransactionAutoConfiguration.class
    })
    @EntityScan(basePackageClasses = BotDiscordMessageEntity.class)
    @EnableJpaRepositories(basePackageClasses = BotDiscordMessageEntityRepository.class)
    static class TestConfig {}
}
