package ti4.spring.service.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
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
import org.springframework.jdbc.core.JdbcTemplate;
import ti4.test.PostgresDatabaseTest;

// Runs against a real Postgres, e.g. a throwaway database in a local container:
//   TI4_TEST_POSTGRES_URL=jdbc:postgresql://localhost:55432/tibot_ci_test
// Skipped when the variable is unset. CI provides a dedicated test database.
@EnabledIfEnvironmentVariable(named = "TI4_TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(
        classes = IdSequenceIncrementMigrationDatabaseTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class IdSequenceIncrementMigrationDatabaseTest extends PostgresDatabaseTest {

    @Autowired
    private IdSequenceIncrementMigration migration;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Production's player and title ids were IDENTITY columns whose sequences step by 1. Hibernate hands out ids
    // in blocks of the allocation size without checking the sequence, so the step must match before any insert.
    @Test
    void raisesASequenceThatStillStepsByOneToTheAllocationSize() {
        jdbcTemplate.execute("alter sequence " + PlayerEntity.ID_SEQUENCE + " increment by 1");

        migration.run(null);

        assertThat(incrementOf(PlayerEntity.ID_SEQUENCE)).isEqualTo(PlayerEntity.ID_ALLOCATION_SIZE);
        assertThat(incrementOf(TitleEntity.ID_SEQUENCE)).isEqualTo(TitleEntity.ID_ALLOCATION_SIZE);
    }

    @Test
    void leavesSequencesThatAlreadyMatchAlone() {
        migration.run(null);
        migration.run(null);

        assertThat(incrementOf(PlayerEntity.ID_SEQUENCE)).isEqualTo(PlayerEntity.ID_ALLOCATION_SIZE);
    }

    private long incrementOf(String sequenceName) {
        return jdbcTemplate.queryForObject(
                "select increment_by from pg_sequences where schemaname = current_schema() and sequencename = ?",
                Long.class,
                sequenceName);
    }

    @Configuration
    @ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        TransactionAutoConfiguration.class
    })
    @EntityScan(basePackageClasses = GameEntity.class)
    @Import(IdSequenceIncrementMigration.class)
    static class TestConfig {
        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
