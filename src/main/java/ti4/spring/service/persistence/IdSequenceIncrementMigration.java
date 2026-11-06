package ti4.spring.service.persistence;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ti4.logging.BotLogger;

@Deprecated(forRemoval = true)
@Component
@RequiredArgsConstructor
class IdSequenceIncrementMigration implements ApplicationRunner {

    private static final List<SequenceSpec> SEQUENCES = List.of(
            new SequenceSpec(PlayerEntity.ID_SEQUENCE, PlayerEntity.ID_ALLOCATION_SIZE),
            new SequenceSpec(TitleEntity.ID_SEQUENCE, TitleEntity.ID_ALLOCATION_SIZE));

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        SEQUENCES.forEach(this::matchIncrementToAllocationSize);
    }

    private void matchIncrementToAllocationSize(SequenceSpec sequence) {
        List<Long> increments = jdbcTemplate.queryForList(
                "select increment_by from pg_sequences where schemaname = current_schema() and sequencename = ?",
                Long.class,
                sequence.name());
        if (increments.isEmpty() || increments.getFirst() == sequence.allocationSize()) return;
        jdbcTemplate.execute("alter sequence " + sequence.name() + " increment by " + sequence.allocationSize());
        BotLogger.info("Raised the increment of `" + sequence.name() + "` from " + increments.getFirst() + " to "
                + sequence.allocationSize() + " so Hibernate can batch its inserts.");
    }

    private record SequenceSpec(String name, int allocationSize) {}
}
