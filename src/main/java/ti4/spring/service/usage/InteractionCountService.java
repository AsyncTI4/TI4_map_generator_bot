package ti4.spring.service.usage;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;

@Service
public class InteractionCountService {

    public static final String SLASH_COMMAND = "SLASH_COMMAND";
    public static final String BUTTON_HANDLER = "BUTTON_HANDLER";

    private final InteractionCountRepository repository;

    public InteractionCountService(InteractionCountRepository repository) {
        this.repository = repository;
    }

    public static InteractionCountService get() {
        return SpringContext.getBean(InteractionCountService.class);
    }

    @Transactional
    public void incrementSlashCommand(String commandName) {
        try {
            repository.incrementCount(SLASH_COMMAND, commandName);
        } catch (Exception e) {
            BotLogger.error("Failed to increment slash command count for: " + commandName, e);
        }
    }

    @Transactional
    public void incrementButtonHandler(String handlerId) {
        try {
            repository.incrementCount(BUTTON_HANDLER, handlerId);
        } catch (Exception e) {
            BotLogger.error("Failed to increment button handler count for: " + handlerId, e);
        }
    }

    public long getTotalSlashCommandCount() {
        Long total = repository.sumCountByType(SLASH_COMMAND);
        return total == null ? 0L : total;
    }

    public long getTotalButtonHandlerCount() {
        Long total = repository.sumCountByType(BUTTON_HANDLER);
        return total == null ? 0L : total;
    }

    public Map<String, Long> getSlashCommandCounts() {
        return repository.findAllByType(SLASH_COMMAND).stream()
                .collect(Collectors.toMap(InteractionCountEntity::getName, InteractionCountEntity::getCount));
    }

    public Map<String, Long> getButtonHandlerCounts() {
        return repository.findAllByType(BUTTON_HANDLER).stream()
                .collect(Collectors.toMap(InteractionCountEntity::getName, InteractionCountEntity::getCount));
    }
}
