package ti4.spring.service.autoping;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ti4.model.metadata.AutoPingMetadataManager.AutoPing;
import ti4.spring.context.SpringContext;

@Service
@RequiredArgsConstructor
public class AutoPingService {

    private final AutoPingEntityRepository repository;
    private final TransactionTemplate transactionTemplate;

    public static AutoPingService getBean() {
        return SpringContext.getBean(AutoPingService.class);
    }

    public Optional<AutoPing> find(String gameName) {
        return repository.findById(gameName).map(AutoPingEntity::toAutoPing);
    }

    public Set<String> gameNames() {
        return Set.copyOf(repository.findAllGameNames());
    }

    public void compute(String gameName, UnaryOperator<AutoPing> change) {
        transactionTemplate.executeWithoutResult(_ -> {
            Optional<AutoPingEntity> existing = repository.findById(gameName);
            AutoPing updated =
                    change.apply(existing.map(AutoPingEntity::toAutoPing).orElse(null));
            if (updated == null) return;
            existing.ifPresentOrElse(
                    entity -> entity.apply(updated), () -> repository.save(new AutoPingEntity(gameName, updated)));
        });
    }

    public void updateIfPresent(String gameName, UnaryOperator<AutoPing> change) {
        transactionTemplate.executeWithoutResult(_ ->
                repository.findById(gameName).ifPresent(entity -> entity.apply(change.apply(entity.toAutoPing()))));
    }

    public void delete(Collection<String> gameNames) {
        Set<String> stored = new HashSet<>(repository.findAllGameNames());
        stored.retainAll(new HashSet<>(gameNames));
        if (stored.isEmpty()) return;
        transactionTemplate.executeWithoutResult(_ -> repository.deleteAllByIdInBatch(stored));
    }
}
