package ti4.ai.secrets;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ObjectiveValue;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.DeckModel;
import ti4.model.Mapper;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
public class SecretOdds {

    public static final double BASE_RATE = 0.15;
    static final double ONE_STEP_SHORT = 0.5;

    public static List<String> unseenPool(Game game, Player viewer) {
        DeckModel deck = Mapper.getDeck(game.getSoDeckID());
        if (deck == null) return List.of();
        Set<String> seen = new HashSet<>();
        for (Player player : game.getRealPlayers())
            seen.addAll(player.getSecretsScored().keySet());
        seen.addAll(viewer.getSecretsUnscored().keySet());
        return deck.getCardIDs().stream().filter(id -> !seen.contains(id)).toList();
    }

    public static double chanceReady(Game game, Player viewer, Player player) {
        if (player == viewer) return chanceOwnReady(game, viewer);
        int held = player.getSo();
        List<String> pool = unseenPool(game, viewer);
        if (held <= 0 || pool.isEmpty()) return 0;
        ObjectiveValue board = ObjectiveValue.publicOnly(game, player);
        double mean = pool.stream()
                .mapToDouble(id -> readiness(game, board, id))
                .average()
                .orElse(0);
        return 1 - Math.pow(1 - mean, held);
    }

    private static double chanceOwnReady(Game game, Player viewer) {
        ObjectiveValue board = ObjectiveValue.publicOnly(game, viewer);
        double noneReady = 1;
        for (String id : viewer.getSecretsUnscored().keySet()) noneReady *= 1 - readiness(game, board, id);
        return 1 - noneReady;
    }

    static double readiness(Game game, ObjectiveValue board, String id) {
        int threshold = ListPlayerInfoService.getObjectiveThreshold(id, game);
        int progress = threshold > 0 ? board.progress(id, board.before()) : ObjectiveValue.NOT_MODELLED;
        if (progress == ObjectiveValue.NOT_MODELLED) return BASE_RATE;
        if (progress >= threshold) return 1;
        return progress + 1 >= threshold ? ONE_STEP_SHORT : 0;
    }
}
