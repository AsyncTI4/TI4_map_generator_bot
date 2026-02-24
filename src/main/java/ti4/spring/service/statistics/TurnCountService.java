package ti4.spring.service.statistics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.spring.context.SpringContext;
import ti4.spring.persistence.PlayerEntity;
import ti4.spring.persistence.PlayerEntityRepository;

@Service
@RequiredArgsConstructor
public class TurnCountService {

    private final PlayerEntityRepository playerEntityRepository;

    @Transactional(readOnly = true)
    public Map<String, Integer> getUserIdsToTurnCounts(List<String> userIds) {
        List<PlayerEntity> players = playerEntityRepository.findAllPlayersForUsers(userIds);
        return getTurnCounts(players);
    }

    private Map<String, Integer> getTurnCounts(List<PlayerEntity> players) {
        Map<String, Integer> usersToTurnCounts = new HashMap<>();

        for (PlayerEntity player : players) {
            if (player.getTotalNumberOfTurns() <= 0) {
                continue;
            }
            usersToTurnCounts.merge(player.getUser().getId(), player.getTotalNumberOfTurns(), Integer::sum);
        }

        return usersToTurnCounts;
    }

    public static TurnCountService getBean() {
        return SpringContext.getBean(TurnCountService.class);
    }
}
