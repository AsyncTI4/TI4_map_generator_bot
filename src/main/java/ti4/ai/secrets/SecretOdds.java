package ti4.ai.secrets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.AiExperiments;
import ti4.ai.eval.DecidingSeat;
import ti4.ai.scoring.Footprint;
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
        return poolWithout(game, viewer.getSecretsUnscored().keySet());
    }

    private static List<String> poolWithout(Game game, Set<String> hand) {
        DeckModel deck = Mapper.getDeck(game.getSoDeckID());
        if (deck == null) return List.of();
        Set<String> seen = new HashSet<>(hand);
        for (Player player : game.getRealPlayers())
            seen.addAll(player.getSecretsScored().keySet());
        return deck.getCardIDs().stream().filter(id -> !seen.contains(id)).toList();
    }

    public static double chanceReady(Game game, Player viewer, Player player) {
        if (AiExperiments.enabled(viewer, AiExperiments.Experiment.WIN_THREAT_FEASIBLE)) {
            return measuredChanceReady(game, viewer, player);
        }
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

    private static double measuredChanceReady(Game game, Player viewer, Player player) {
        boolean knowsHand = DecidingSeat.knowsHandOf(viewer);
        if (player == viewer && knowsHand) return measuredOwnReady(game, viewer);
        int held = player.getSo();
        List<String> pool =
                poolWithout(game, knowsHand ? viewer.getSecretsUnscored().keySet() : Set.of());
        if (held <= 0 || pool.isEmpty()) return 0;
        ObjectiveValue board = ObjectiveValue.publicOnly(game, player);
        double mean = pool.stream()
                .mapToDouble(id -> measuredReadiness(game, board, player, id))
                .average()
                .orElse(0);
        return 1 - Math.pow(1 - mean, held);
    }

    public record CardChance(double status, double action) {}

    public static List<CardChance> readyByCard(Game game, Player viewer, Player player) {
        boolean knowsHand = DecidingSeat.knowsHandOf(viewer);
        if (player == viewer && knowsHand) return ownReadyByCard(game, viewer);
        int held = player.getSo();
        List<String> pool =
                poolWithout(game, knowsHand ? viewer.getSecretsUnscored().keySet() : Set.of());
        if (held <= 0 || pool.isEmpty()) return List.of();
        ObjectiveValue board = ObjectiveValue.publicOnly(game, player);
        return unseenReadyByCard(game, board, player, board.before(), pool, held);
    }

    public static List<CardChance> readyByCard(
            Game game, Player viewer, Player player, ObjectiveValue board, Footprint footprint) {
        boolean knowsHand = DecidingSeat.knowsHandOf(viewer);
        if (player == viewer && knowsHand) return ownReadyByCard(game, viewer);
        int held = player.getSo();
        List<String> pool =
                poolWithout(game, knowsHand ? viewer.getSecretsUnscored().keySet() : Set.of());
        if (held <= 0 || pool.isEmpty()) return List.of();
        return unseenReadyByCard(game, board, player, footprint, pool, held);
    }

    private static List<CardChance> unseenReadyByCard(
            Game game, ObjectiveValue board, Player player, Footprint footprint, List<String> pool, int held) {
        double status = 0;
        double action = 0;
        for (String id : pool) {
            double readiness = measuredReadiness(game, board, player, id, footprint);
            SecretPhase phase = SecretPhase.of(id);
            if (phase == SecretPhase.ACTION) action += readiness;
            else if (phase != SecretPhase.AGENDA) status += readiness;
        }
        CardChance unseen = new CardChance(status / pool.size(), action / pool.size());
        return Collections.nCopies(held, unseen);
    }

    private static List<CardChance> ownReadyByCard(Game game, Player viewer) {
        ObjectiveValue board = ObjectiveValue.publicOnly(game, viewer);
        List<CardChance> cards = new ArrayList<>();
        for (String id : viewer.getSecretsUnscored().keySet()) {
            double readiness = ownReadiness(game, board, viewer, id);
            SecretPhase phase = SecretPhase.of(id);
            if (phase == SecretPhase.AGENDA) continue;
            boolean action = phase == SecretPhase.ACTION;
            cards.add(new CardChance(action ? 0 : readiness, action ? readiness : 0));
        }
        return cards;
    }

    private static double measuredOwnReady(Game game, Player viewer) {
        ObjectiveValue board = ObjectiveValue.publicOnly(game, viewer);
        double noneReady = 1;
        for (String id : viewer.getSecretsUnscored().keySet()) noneReady *= 1 - ownReadiness(game, board, viewer, id);
        return 1 - noneReady;
    }

    private static double ownReadiness(Game game, ObjectiveValue board, Player viewer, String id) {
        return SecretValue.meetsThreshold(game, viewer, id) ? 1 : measuredReadiness(game, board, viewer, id);
    }

    static double readiness(Game game, ObjectiveValue board, String id) {
        int threshold = ListPlayerInfoService.getObjectiveThreshold(id, game);
        int progress = threshold > 0 ? board.progress(id, board.before()) : ObjectiveValue.NOT_MODELLED;
        if (progress == ObjectiveValue.NOT_MODELLED) return BASE_RATE;
        return readinessAt(progress, threshold);
    }

    private static double measuredReadiness(Game game, ObjectiveValue board, Player player, String id) {
        return measuredReadiness(game, board, player, id, board.before());
    }

    private static double measuredReadiness(
            Game game, ObjectiveValue board, Player player, String id, Footprint footprint) {
        int threshold = ListPlayerInfoService.getObjectiveThreshold(id, game);
        if (threshold <= 0) return BASE_RATE;
        int progress = board.progress(id, footprint);
        if (progress == ObjectiveValue.NOT_MODELLED) {
            progress = ListPlayerInfoService.getPlayerProgressOnObjective(id, game, player);
        }
        return readinessAt(progress, threshold);
    }

    public static double readinessAt(int progress, int threshold) {
        if (progress >= threshold) return 1;
        return progress + 1 >= threshold ? ONE_STEP_SHORT : 0;
    }
}
