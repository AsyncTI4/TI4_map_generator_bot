package ti4.ai.secrets;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;
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
    private static final double UNMEASURED_VALUE = 0.1;
    private static final String ADAPT_NEW_STRATEGIES = "ans";
    private static final Set<String> VALEFAR_ASSIMILATORS = Set.of("vax", "vay");
    private static final int LAWS_FOR_DICTATE_POLICY = 3;

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
        return switch (SecretPhase.of(secretId)) {
            case STATUS -> statusValue(game, seat, secretId);
            case ACTION -> actionFeasibility(game, seat, secretId);
            case AGENDA -> agendaFeasibility(game, seat, secretId);
            case UNKNOWN -> UNMEASURED_VALUE;
        };
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

    private static double statusValue(Game game, Player seat, String secretId) {
        int threshold = ListPlayerInfoService.getObjectiveThreshold(secretId, game);
        if (threshold <= 0) return UNMEASURED_VALUE;
        int progress = progress(game, seat, secretId);
        double share = Math.min(1.0, (double) progress / threshold);
        return share * STATUS_ACHIEVABILITY.getOrDefault(secretId, 1.0);
    }

    private static double agendaFeasibility(Game game, Player seat, String secretId) {
        if ("dp".equals(secretId)) {
            return Math.min(1.0, (double) game.getLaws().size() / LAWS_FOR_DICTATE_POLICY);
        }
        if ("dtd".equals(secretId)) return seat.hasAbility("galactic_threat") ? 0.2 : 0.6;
        return UNMEASURED_VALUE;
    }

    private static double actionFeasibility(Game game, Player seat, String secretId) {
        return switch (secretId) {
            case "pe" -> 0.5;
            case "dyp" -> 0.6;
            case "sar" -> 0.5;
            case "uf" -> hasOnBoard(game, seat, "fs") ? 0.5 : 0.35;
            case "btv" -> 0.35;
            case "dts" -> 0.3;
            case "dtgs" -> enemyHeavyShipsExist(game, seat) ? 0.35 : 0.1;
            case "fwp" -> hasOnBoard(game, seat, "dd") ? 0.25 : 0.1;
            case "ttfd" -> hasOnBoard(game, seat, "pds") ? 0.25 : 0.05;
            case "bam" -> 0.1;
            default -> 0.05;
        };
    }

    private static boolean hasOnBoard(Game game, Player seat, String asyncId) {
        return ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, asyncId) > 0;
    }

    private static boolean enemyHeavyShipsExist(Game game, Player seat) {
        for (Tile tile : game.getTileMap().values()) {
            UnitHolder space = BoardView.space(tile);
            if (space == null) continue;
            for (Player other : game.getRealPlayers()) {
                if (other == seat) continue;
                if (BoardView.count(space, other, UnitType.Flagship) + BoardView.count(space, other, UnitType.Warsun)
                        > 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
