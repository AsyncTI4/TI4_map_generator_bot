package ti4.spring.service.techsummary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ti4.json.JsonMapperManager;
import ti4.model.metadata.TechSummariesMetadataManager.FactionTechSummary;
import ti4.model.metadata.TechSummariesMetadataManager.RoundTechSummaries;
import ti4.spring.context.SpringContext;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class TechSummaryService {

    private static final JsonMapper JSON_MAPPER = JsonMapperManager.basic();
    private static final TypeReference<List<FactionTechSummary>> FACTION_SUMMARIES = new TypeReference<>() {};

    private final TechSummaryEntityRepository repository;
    private final TransactionTemplate transactionTemplate;

    public static TechSummaryService getBean() {
        return SpringContext.getBean(TechSummaryService.class);
    }

    public Map<String, RoundTechSummaries> findAll() {
        Map<String, RoundTechSummaries> summariesByGameName = new HashMap<>();
        for (TechSummaryEntity entity : repository.findAll()) {
            summariesByGameName.put(entity.getGameName(), toRoundTechSummaries(entity));
        }
        return summariesByGameName;
    }

    public Optional<RoundTechSummaries> find(String gameName) {
        return repository.findById(gameName).map(TechSummaryService::toRoundTechSummaries);
    }

    public void save(String gameName, RoundTechSummaries summaries) {
        transactionTemplate.executeWithoutResult(_ -> upsert(gameName, summaries));
    }

    public void replaceAll(Set<String> previousGameNames, Map<String, RoundTechSummaries> summariesByGameName) {
        transactionTemplate.executeWithoutResult(_ -> {
            Set<String> removed = new HashSet<>(previousGameNames);
            removed.removeAll(summariesByGameName.keySet());
            if (!removed.isEmpty()) repository.deleteAllByIdInBatch(removed);
            Map<String, TechSummaryEntity> existingByGameName = new HashMap<>();
            repository
                    .findAllById(summariesByGameName.keySet())
                    .forEach(entity -> existingByGameName.put(entity.getGameName(), entity));
            List<TechSummaryEntity> created = new ArrayList<>();
            summariesByGameName.forEach((gameName, summaries) -> {
                TechSummaryEntity entity = existingByGameName.get(gameName);
                if (entity == null) {
                    created.add(newEntity(gameName, summaries));
                } else {
                    applyTo(entity, summaries);
                }
            });
            repository.saveAll(created);
        });
    }

    private void upsert(String gameName, RoundTechSummaries summaries) {
        repository
                .findById(gameName)
                .ifPresentOrElse(
                        entity -> applyTo(entity, summaries), () -> repository.save(newEntity(gameName, summaries)));
    }

    private static void applyTo(TechSummaryEntity entity, RoundTechSummaries summaries) {
        entity.setRound(summaries.round());
        entity.setFactionSummariesJson(toJson(summaries));
    }

    private static TechSummaryEntity newEntity(String gameName, RoundTechSummaries summaries) {
        return new TechSummaryEntity(gameName, summaries.round(), toJson(summaries));
    }

    private static String toJson(RoundTechSummaries summaries) {
        return JSON_MAPPER.writeValueAsString(summaries.techSummaries());
    }

    private static RoundTechSummaries toRoundTechSummaries(TechSummaryEntity entity) {
        List<FactionTechSummary> factionSummaries =
                JSON_MAPPER.readValue(entity.getFactionSummariesJson(), FACTION_SUMMARIES);
        return new RoundTechSummaries(entity.getRound(), new ArrayList<>(factionSummaries));
    }
}
