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
            summariesByGameName.forEach(this::upsert);
        });
    }

    private void upsert(String gameName, RoundTechSummaries summaries) {
        String json = JSON_MAPPER.writeValueAsString(summaries.techSummaries());
        repository
                .findById(gameName)
                .ifPresentOrElse(
                        entity -> {
                            entity.setRound(summaries.round());
                            entity.setFactionSummariesJson(json);
                        },
                        () -> repository.save(new TechSummaryEntity(gameName, summaries.round(), json)));
    }

    private static RoundTechSummaries toRoundTechSummaries(TechSummaryEntity entity) {
        List<FactionTechSummary> factionSummaries =
                JSON_MAPPER.readValue(entity.getFactionSummariesJson(), FACTION_SUMMARIES);
        return new RoundTechSummaries(entity.getRound(), new ArrayList<>(factionSummaries));
    }
}
