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

    private record ParsedTiglGame(String gameName, TIGLRank rank, boolean fractured) {}

    private final GameEntityRepository gameEntityRepository;
    private static final List<TIGLRank> BASE_TIGL_GAMES_DISPLAY_ORDER =
            List.of(TIGLRank.MINISTER, TIGLRank.AGENT, TIGLRank.COMMANDER, TIGLRank.HERO);
    private static final List<TIGLRank> FRACTURED_TIGL_GAMES_DISPLAY_ORDER = List.of(
            TIGLRank.THRALL,
            TIGLRank.ACOLYTE,
            TIGLRank.LEGIONNAIRE,
            TIGLRank.STARLANCER,
            TIGLRank.GENESORCERER,
            TIGLRank.IXTHLORD,
            TIGLRank.ARCHON);

    @Transactional(readOnly = true)
    public String getOngoingGamesByRankMessage(boolean showGameIds) {
        List<GameEntity> ongoingTiglGames =
                gameEntityRepository.findByTwilightImperiumGlobalLeagueTrueAndEndedEpochMillisecondsIsNull();

        List<ParsedTiglGame> parsedGames = ongoingTiglGames.stream()
                .map(game -> {
                    TIGLRank rank = TIGLRank.fromString(game.getTwilightImperiumGlobalLeagueRank());
                    if (rank == null) {
                        rank = TIGLRank.UNRANKED;
                    }
                    return new ParsedTiglGame(game.getGameName(), rank, game.isTwilightImperiumGlobalLeagueFractured());
                })
                .toList();

        Map<TIGLRank, List<String>> baseRankToGames = parsedGames.stream()
                .filter(game -> !game.fractured())
                .collect(Collectors.groupingBy(
                        ParsedTiglGame::rank, Collectors.mapping(ParsedTiglGame::gameName, Collectors.toList())));

        Map<TIGLRank, List<String>> fracturedRankToGames = parsedGames.stream()
                .filter(ParsedTiglGame::fractured)
                .collect(Collectors.groupingBy(
                        ParsedTiglGame::rank, Collectors.mapping(ParsedTiglGame::gameName, Collectors.toList())));

        List<String> unrankedBaseGames = parsedGames.stream()
                .filter(game -> game.rank() == TIGLRank.UNRANKED)
                .filter(game -> !game.fractured())
                .map(ParsedTiglGame::gameName)
                .toList();

        List<String> unrankedFracturedGames = parsedGames.stream()
                .filter(game -> game.rank() == TIGLRank.UNRANKED)
                .filter(ParsedTiglGame::fractured)
                .map(ParsedTiglGame::gameName)
                .toList();

        StringBuilder sb = new StringBuilder("## Ongoing TIGL games by rank\n");
        sb.append("Total ongoing TIGL games: `").append(ongoingTiglGames.size()).append("`\n");

        appendRankLine(sb, "Unranked (Base)", unrankedBaseGames, showGameIds);
        for (TIGLRank rank : BASE_TIGL_GAMES_DISPLAY_ORDER) {
            List<String> gamesForRank = baseRankToGames.getOrDefault(rank, List.of());
            appendRankLine(sb, rank.getShortName(), gamesForRank, showGameIds);
        }

        appendRankLine(sb, "Unranked (Fractured)", unrankedFracturedGames, showGameIds);
        for (TIGLRank rank : FRACTURED_TIGL_GAMES_DISPLAY_ORDER) {
            List<String> gamesForRank = fracturedRankToGames.getOrDefault(rank, List.of());
            appendRankLine(sb, rank.getShortName(), gamesForRank, showGameIds);
        }

        return sb.toString();
    }

    private static void appendRankLine(StringBuilder sb, String label, List<String> gamesForRank, boolean showGameIds) {
        sb.append("- **")
                .append(label)
                .append("**: `")
                .append(gamesForRank.size())
                .append("` game");
        if (gamesForRank.size() != 1) {
            sb.append("s");
        }
        if (showGameIds && !gamesForRank.isEmpty()) {
            sb.append(" → ").append(String.join(", ", gamesForRank));
        }
        sb.append('\n');
    }

    public static TiglGamesInfoService getBean() {
        return SpringContext.getBean(TiglGamesInfoService.class);
    }
}
