package ti4.ai.secrets;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.GameHorizon;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.model.TechnologyModel;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
public class SecretValue {

    private static final Set<String> LOSABLE = Set.of("te", "ctr", "btgk", "lsc", "ose", "syc", "csl", "dfat");
    private static final Set<String> COSTLY = Set.of("fsn", "dhw");
    private static final Map<String, Double> STATUS_ACHIEVABILITY = Map.of(
            "sb", 0.1,
            "csl", 0.3,
            "dfat", 0.3,
            "fc", 0.6,
            "pem", 0.6,
            "otf", 0.15,
            "mtm", 0.15);
    private static final String ADAPT_NEW_STRATEGIES = "ans";
    private static final String DICTATE_POLICY = "dp";
    private static final String DRIVE_THE_DEBATE = "dtd";
    private static final Set<String> VALEFAR_ASSIMILATORS = Set.of("vax", "vay");
    private static final int LAWS_FOR_DICTATE_POLICY = 3;
    private static final double READY_VALUE = 2.0;
    private static final double CONGESTION_FACTOR = 0.5;
    private static final double ACTION_PHASE_VALUE = 1.2;
    private static final double DRIVE_THE_DEBATE_CHANCE = 0.1;
    private static final double DRIVE_THE_DEBATE_UNDER_THREAT_CHANCE = 0.02;
    private static final double STATUS_RUNWAY_ROUNDS = 3.0;
    private static final int LAST_EARLY_ROUND = 2;
    private static final int LAST_MIDDLE_ROUND = 4;
    private static final double EARLY_PRIOR_SHARE = 0.5;
    private static final double MIDDLE_PRIOR_SHARE = 0.35;
    private static final double LATE_PRIOR_SHARE = 0.2;

    public static Optional<String> bestScorableStatusSecret(Game game, Player seat) {
        return seat.getSecretsUnscored().keySet().stream()
                .filter(secret -> SecretPhase.of(secret) == SecretPhase.STATUS)
                .filter(secret -> meetsThreshold(game, seat, secret))
                .min(Comparator.comparingInt(SecretValue::scoringOrder).thenComparing(Comparator.naturalOrder()));
    }

    public static Optional<String> lowestValueSecret(Game game, Player seat) {
        return seat.getSecretsUnscored().keySet().stream()
                .min(Comparator.comparingDouble((String secret) -> keepValue(game, seat, secret))
                        .thenComparing(Comparator.naturalOrder()));
    }

    public static double keepValue(Game game, Player seat, String secretId) {
        boolean ready = isReady(game, seat, secretId);
        boolean congested = isCongested(game, seat, secretId);
        if (ready && !congested) return READY_VALUE;
        double chance = ready ? 1.0 : completionChance(game, seat, secretId);
        double value = chance * phaseValue(secretId);
        return congested ? value * CONGESTION_FACTOR : value;
    }

    static boolean meetsThreshold(Game game, Player seat, String secretId) {
        int threshold = ListPlayerInfoService.getObjectiveThreshold(secretId, game);
        return threshold > 0 && progress(game, seat, secretId) >= threshold;
    }

    private static int progress(Game game, Player seat, String secretId) {
        if (ADAPT_NEW_STRATEGIES.equals(secretId)) return ownFactionTechnologies(seat);
        return ListPlayerInfoService.getPlayerProgressOnObjective(secretId, game, seat);
    }

    public static int ownFactionTechnologies(Player seat) {
        int count = 0;
        for (String alias : seat.getTechs()) {
            TechnologyModel tech = Mapper.getTech(alias);
            if (tech == null || VALEFAR_ASSIMILATORS.contains(alias)) continue;
            if (tech.getFaction().filter(seat.getFaction()::equals).isPresent()) count++;
        }
        return count;
    }

    private static int scoringOrder(String secretId) {
        if (LOSABLE.contains(secretId)) return 0;
        return COSTLY.contains(secretId) ? 2 : 1;
    }

    private static boolean isReady(Game game, Player seat, String secretId) {
        return switch (SecretPhase.of(secretId)) {
            case STATUS -> meetsThreshold(game, seat, secretId);
            case AGENDA -> DICTATE_POLICY.equals(secretId) && game.getLaws().size() >= LAWS_FOR_DICTATE_POLICY;
            case ACTION, UNKNOWN -> false;
        };
    }

    private static boolean isCongested(Game game, Player seat, String secretId) {
        if (SecretPhase.of(secretId) != SecretPhase.STATUS) return false;
        long otherReady = seat.getSecretsUnscored().keySet().stream()
                .filter(other -> !other.equals(secretId))
                .filter(other -> SecretPhase.of(other) == SecretPhase.STATUS)
                .filter(other -> meetsThreshold(game, seat, other))
                .count();
        return otherReady >= GameHorizon.roundsLeft(game);
    }

    private static double phaseValue(String secretId) {
        return SecretPhase.of(secretId) == SecretPhase.ACTION ? ACTION_PHASE_VALUE : 1.0;
    }

    private static double completionChance(Game game, Player seat, String secretId) {
        double priorShare = priorShare(game);
        return priorShare * achievableCompletion(secretId) + (1 - priorShare) * situationalChance(game, seat, secretId);
    }

    private static double priorShare(Game game) {
        if (game.getRound() <= LAST_EARLY_ROUND) return EARLY_PRIOR_SHARE;
        return game.getRound() <= LAST_MIDDLE_ROUND ? MIDDLE_PRIOR_SHARE : LATE_PRIOR_SHARE;
    }

    private static double achievableCompletion(String secretId) {
        return SecretStatistics.completion(secretId) * STATUS_ACHIEVABILITY.getOrDefault(secretId, 1.0);
    }

    private static double situationalChance(Game game, Player seat, String secretId) {
        return switch (SecretPhase.of(secretId)) {
            case STATUS -> statusChance(game, seat, secretId);
            case ACTION -> ActionSecretOdds.beforeGameEnds(game, seat, secretId);
            case AGENDA -> agendaChance(game, seat, secretId);
            case UNKNOWN -> achievableCompletion(secretId);
        };
    }

    private static double statusChance(Game game, Player seat, String secretId) {
        int threshold = ListPlayerInfoService.getObjectiveThreshold(secretId, game);
        if (threshold <= 0) return achievableCompletion(secretId);
        double share = Math.min(1.0, (double) progress(game, seat, secretId) / threshold);
        double runway = Math.min(1.0, GameHorizon.roundsLeft(game) / STATUS_RUNWAY_ROUNDS);
        return share + (1 - share) * achievableCompletion(secretId) * runway;
    }

    private static double agendaChance(Game game, Player seat, String secretId) {
        if (DICTATE_POLICY.equals(secretId)) {
            return Math.min(1.0, (double) game.getLaws().size() / LAWS_FOR_DICTATE_POLICY);
        }
        if (DRIVE_THE_DEBATE.equals(secretId)) {
            return seat.hasAbility("galactic_threat") ? DRIVE_THE_DEBATE_UNDER_THREAT_CHANCE : DRIVE_THE_DEBATE_CHANCE;
        }
        return achievableCompletion(secretId);
    }
}
