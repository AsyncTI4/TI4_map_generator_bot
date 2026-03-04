package ti4.spring.service.tigl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.helpers.TIGLHelper.TIGLRank;
import ti4.spring.context.SpringContext;
import ti4.spring.service.persistence.GameEntity;
import ti4.spring.service.persistence.GameEntityRepository;

@Service
@RequiredArgsConstructor
public class TiglGamesInfoService {

    private final GameEntityRepository gameEntityRepository;

    @Transactional(readOnly = true)
    public String getOngoingGamesByRankMessage(boolean showGameIds) {
        List<GameEntity> ongoingTiglGames =
                gameEntityRepository.findByTwilightImperiumGlobalLeagueTrueAndEndedEpochMillisecondsIsNull();

        Map<TIGLRank, List<String>> rankToGames = ongoingTiglGames.stream()
                .collect(Collectors.groupingBy(
                        game -> {
                            TIGLRank rank = TIGLRank.fromString(game.getTwilightImperiumGlobalLeagueRank());
                            return rank == null ? TIGLRank.UNRANKED : rank;
                        },
                        Collectors.mapping(GameEntity::getGameName, Collectors.toList())));

        StringBuilder sb = new StringBuilder("## Ongoing TIGL games by rank\n");
        sb.append("Total ongoing TIGL games: `").append(ongoingTiglGames.size()).append("`\n");

        for (TIGLRank rank : TIGLRank.getSortedRanks()) {
            if (rank == TIGLRank.EMPEROR) {
                continue;
            }
            List<String> gamesForRank = rankToGames.getOrDefault(rank, List.of());
            sb.append("- **")
                    .append(rank.getShortName())
                    .append("**: `")
                    .append(gamesForRank.size())
                    .append("` game");
            if (gamesForRank.size() != 1) {
                sb.append("s");
            }
            if (showGameIds && !gamesForRank.isEmpty()) {
                sb.append(" → ").append(String.join(", ", gamesForRank));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public static TiglGamesInfoService getBean() {
        return SpringContext.getBean(TiglGamesInfoService.class);
    }
}
