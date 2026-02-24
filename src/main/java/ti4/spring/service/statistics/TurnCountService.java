package ti4.spring.service.statistics;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.spring.context.SpringContext;
import ti4.spring.persistence.PlayerEntity;
import ti4.spring.persistence.PlayerEntityRepository;
import ti4.spring.persistence.UserEntity;

@Service
@RequiredArgsConstructor
public class TurnCountService {

    private final PlayerEntityRepository playerEntityRepository;

    @Transactional(readOnly = true)
    public Map<String, Integer> getUserIdsToTurnCounts(List<String> userIds) {
        List<PlayerEntity> players = playerEntityRepository.findAllPlayersForUsers(userIds);

        List<UserTurnCountAccumulator> turnCounts = getTurnCounts(players);

        return turnCounts.stream()
                .collect(Collectors.toMap(
                        acc -> acc.userId, acc -> acc.totalTurns, (existing, replacement) -> existing));
    }

    private List<UserTurnCountAccumulator> getTurnCounts(List<PlayerEntity> players) {
        Map<UserEntity, UserTurnCountAccumulator> statsMap = new HashMap<>();

        for (PlayerEntity player : players) {
            if (player.getTotalNumberOfTurns() <= 0) {
                continue;
            }
            statsMap.computeIfAbsent(player.getUser(), user -> new UserTurnCountAccumulator(user.getId()))
                    .addTurns(player.getTotalNumberOfTurns());
        }

        return statsMap.values().stream()
                .sorted(Comparator.comparingInt(UserTurnCountAccumulator::getTotalTurns)
                        .reversed())
                .toList();
    }

    public static TurnCountService getBean() {
        return SpringContext.getBean(TurnCountService.class);
    }

    private static class UserTurnCountAccumulator {
        String userId;
        int totalTurns;

        UserTurnCountAccumulator(String userId) {
            this.userId = userId;
        }

        void addTurns(int turns) {
            totalTurns += turns;
        }

        int getTotalTurns() {
            return totalTurns;
        }
    }
}
