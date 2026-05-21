package ti4.spring.service.usage;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;

@Service
public class InteractionCountService {

    private final SlashCommandCountRepository repository;

    public InteractionCountService(SlashCommandCountRepository repository) {
        this.repository = repository;
    }

    public static InteractionCountService get() {
        return SpringContext.getBean(InteractionCountService.class);
    }

    @Transactional
    public synchronized void incrementSlashCommand(String commandName) {
        try {
            String today = LocalDate.now().toString();
            int updatedRows = repository.incrementExisting(commandName, today);
            if (updatedRows == 0) {
                repository.insertCount(commandName, today);
            }
        } catch (Exception e) {
            BotLogger.error("Failed to increment slash command count for: " + commandName, e);
        }
    }

    public long getTotalSlashCommandCount(@Nullable LocalDate since) {
        Long total = since == null ? repository.sumAllCounts() : repository.sumCountsSince(since.toString());
        return total == null ? 0L : total;
    }

    public Map<String, Long> getSlashCommandCounts(@Nullable LocalDate since) {
        List<Object[]> rows = since == null ? repository.sumAllByName() : repository.sumByNameSince(since.toString());
        Map<String, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((String) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }
}
