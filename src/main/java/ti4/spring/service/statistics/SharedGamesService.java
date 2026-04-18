package ti4.spring.service.statistics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.spring.context.SpringContext;
import ti4.spring.service.persistence.PlayerEntity;
import ti4.spring.service.persistence.PlayerEntityRepository;

@Service
@RequiredArgsConstructor
public class SharedGamesService {

    private final PlayerEntityRepository playerEntityRepository;

    @Transactional(readOnly = true)
    public Map<String, Integer> getSharedGameCounts(String joiningUserId, List<String> otherUserIds) {
        if (otherUserIds.isEmpty()) {
            return Map.of();
        }

        Set<String> relevantUserIds = new LinkedHashSet<>();
        relevantUserIds.add(joiningUserId);
        relevantUserIds.addAll(otherUserIds);

        List<PlayerEntity> players =
                playerEntityRepository.findAllWithUsersAndGamesByUserIdIn(new ArrayList<>(relevantUserIds));
        Set<String> joiningUserGameNames = new HashSet<>();
        Map<String, Set<String>> participantIdsByGameName = new HashMap<>();

        for (PlayerEntity player : players) {
            if (player.isReplaced()) {
                continue;
            }

            String gameName = player.getGame().getGameName();
            String userId = player.getUser().getId();
            participantIdsByGameName
                    .computeIfAbsent(gameName, ignored -> new HashSet<>())
                    .add(userId);

            if (joiningUserId.equals(userId)) {
                joiningUserGameNames.add(gameName);
            }
        }

        Map<String, Integer> countsByUserId = new LinkedHashMap<>();
        for (String otherUserId : otherUserIds) {
            countsByUserId.put(otherUserId, 0);
        }

        for (String gameName : joiningUserGameNames) {
            Set<String> participantIds = participantIdsByGameName.getOrDefault(gameName, Set.of());
            for (String otherUserId : countsByUserId.keySet()) {
                if (participantIds.contains(otherUserId)) {
                    countsByUserId.computeIfPresent(otherUserId, (ignored, count) -> count + 1);
                }
            }
        }

        countsByUserId.entrySet().removeIf(entry -> entry.getValue() < 1);
        return countsByUserId;
    }

    public static SharedGamesService getBean() {
        return SpringContext.getBean(SharedGamesService.class);
    }
}
