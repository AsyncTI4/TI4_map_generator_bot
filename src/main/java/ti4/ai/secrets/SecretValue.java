package ti4.ai.secrets;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.GameHorizon;
import ti4.ai.strategy.TechPath;
import ti4.ai.tactical.GroundCampaign;
import ti4.ai.tactical.SeatStrike;
import ti4.ai.tactical.SupplyLines;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.model.Mapper;
import ti4.model.TokenModel;
import ti4.model.WormholeModel.Wormhole;
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
            "pem", 0.6);
    private static final double REACHABLE_SUPPLY_LINES = 0.6;
    private static final String SEIZE_AN_ICON = "sai";
    private static final String DEFY_SPACE_AND_TIME = "dfat";
    private static final String OCCUPY_THE_SEAT = "ose";
    private static final String MALLICE = "mallice";
    private static final String LOCKED_MALLICE = "lockedmallice";
    private static final String GHOST_SHIP = "ghost_ship";
    private static final String CREUSS = "ghost";
    private static final double LOCKED_NEXUS = 0.12;
    private static final double OFF_THE_BOARD = 0.1;
    private static final double RIVAL_HELD_LEGENDARY = 0.4;
    private static final int OPENING_ROUND = 1;
    private static final String DICTATE_POLICY = "dp";
    private static final String DRIVE_THE_DEBATE = "dtd";
    private static final int LAWS_FOR_DICTATE_POLICY = 3;
    private static final double READY_VALUE = 2.0;
    private static final double CONGESTION_FACTOR = 0.5;
    private static final double ACTION_PHASE_VALUE = 1.2;
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
        double chance = ready ? 1.0 : completionChance(game, seat, secretId) * boardFeasibility(game, seat, secretId);
        double value = chance * phaseValue(secretId) + openingTempo(game, seat, secretId);
        return congested ? value * CONGESTION_FACTOR : value;
    }

    private static double openingTempo(Game game, Player seat, String secretId) {
        if (game.getRound() > OPENING_ROUND) return 0;
        return SecretStatistics.openingCompletion(secretId) * boardFeasibility(game, seat, secretId);
    }

    private static double boardFeasibility(Game game, Player seat, String secretId) {
        return switch (secretId) {
            case SEIZE_AN_ICON -> legendaryReach(game, seat);
            case DEFY_SPACE_AND_TIME -> nexusReach(game, seat);
            case OCCUPY_THE_SEAT -> SeatStrike.feasibility(game, seat);
            case GroundCampaign.OCCUPY_THE_FRINGE, GroundCampaign.MECHANIZE_THE_MILITARY ->
                GroundCampaign.of(game, seat).eta(secretId).feasibility();
            default -> TraitReach.applies(secretId) ? TraitReach.feasibility(game, seat, secretId, OFF_THE_BOARD) : 1.0;
        };
    }

    private static double legendaryReach(Game game, Player seat) {
        List<Player> holders = game.getTileMap().values().stream()
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .filter(Planet::isLegendary)
                .map(planet -> BoardView.controller(game, planet.getName()))
                .toList();
        if (holders.isEmpty()) return OFF_THE_BOARD;
        boolean free = holders.stream().anyMatch(controller -> controller == null || controller == seat);
        return free ? 1.0 : RIVAL_HELD_LEGENDARY;
    }

    private static double nexusReach(Game game, Player seat) {
        Optional<Tile> nexus = nexusTile(game);
        if (nexus.isEmpty()) return OFF_THE_BOARD;
        if (!isLocked(nexus.get()) || canOpenNexus(game, seat, nexus.get())) return 1.0;
        return LOCKED_NEXUS;
    }

    private static Optional<Tile> nexusTile(Game game) {
        return game.getTileMap().values().stream()
                .filter(tile -> tile.getPlanetUnitHolders().stream()
                        .anyMatch(planet -> planet.getName().contains(MALLICE)))
                .findFirst();
    }

    private static boolean isLocked(Tile nexus) {
        return nexus.getPlanetUnitHolders().stream()
                .anyMatch(planet -> planet.getName().contains(LOCKED_MALLICE));
    }

    private static boolean canOpenNexus(Game game, Player seat, Tile nexus) {
        return seat.getActionCards().containsKey(GHOST_SHIP)
                || game.getRealPlayers().stream().anyMatch(player -> CREUSS.equals(player.getFaction()))
                || gammaTokenOnBoard(game, nexus);
    }

    private static boolean gammaTokenOnBoard(Game game, Tile nexus) {
        return game.getTileMap().values().stream()
                .filter(tile -> tile != nexus)
                .flatMap(tile -> tile.getSpaceUnitHolder().getTokenList().stream())
                .map(Mapper::getToken)
                .anyMatch(SecretValue::isOfficialGammaWormhole);
    }

    private static boolean isOfficialGammaWormhole(TokenModel token) {
        return token != null
                && token.getSource() != null
                && token.getSource().isOfficial()
                && token.getWormholes() != null
                && token.getWormholes().contains(Wormhole.GAMMA);
    }

    static boolean meetsThreshold(Game game, Player seat, String secretId) {
        int threshold = ListPlayerInfoService.getObjectiveThreshold(secretId, game);
        return threshold > 0 && progress(game, seat, secretId) >= threshold;
    }

    private static int progress(Game game, Player seat, String secretId) {
        return ListPlayerInfoService.getPlayerProgressOnObjective(secretId, game, seat);
    }

    public static int ownFactionTechnologies(Player seat) {
        return ListPlayerInfoService.countAdaptNewStrategiesTechnologies(seat, false);
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
        return priorShare * achievableCompletion(game, seat, secretId)
                + (1 - priorShare) * situationalChance(game, seat, secretId);
    }

    private static double priorShare(Game game) {
        if (game.getRound() <= LAST_EARLY_ROUND) return EARLY_PRIOR_SHARE;
        return game.getRound() <= LAST_MIDDLE_ROUND ? MIDDLE_PRIOR_SHARE : LATE_PRIOR_SHARE;
    }

    private static double achievableCompletion(Game game, Player seat, String secretId) {
        return SecretStatistics.completion(secretId) * achievability(game, seat, secretId);
    }

    private static double achievability(Game game, Player seat, String secretId) {
        if (SupplyLines.CUT_SUPPLY_LINES.equals(secretId)) {
            return SupplyLines.targetInReach(game, seat) ? REACHABLE_SUPPLY_LINES : STATUS_ACHIEVABILITY.get(secretId);
        }
        return STATUS_ACHIEVABILITY.getOrDefault(secretId, 1.0);
    }

    private static double situationalChance(Game game, Player seat, String secretId) {
        return switch (SecretPhase.of(secretId)) {
            case STATUS -> statusChance(game, seat, secretId);
            case ACTION -> ActionSecretOdds.beforeGameEnds(game, seat, secretId);
            case AGENDA -> agendaChance(game, seat, secretId);
            case UNKNOWN -> achievableCompletion(game, seat, secretId);
        };
    }

    private static double statusChance(Game game, Player seat, String secretId) {
        int threshold = ListPlayerInfoService.getObjectiveThreshold(secretId, game);
        if (threshold <= 0) return achievableCompletion(game, seat, secretId);
        double share = Math.min(1.0, (double) progress(game, seat, secretId) / threshold);
        if (TechPath.follows(secretId) && !TechPath.reachable(game, seat, secretId)) return share >= 1.0 ? 1.0 : 0.0;
        double runway = Math.min(1.0, GameHorizon.roundsLeft(game) / STATUS_RUNWAY_ROUNDS);
        return share + (1 - share) * achievableCompletion(game, seat, secretId) * runway;
    }

    private static double agendaChance(Game game, Player seat, String secretId) {
        if (DICTATE_POLICY.equals(secretId)) {
            return Math.min(1.0, (double) game.getLaws().size() / LAWS_FOR_DICTATE_POLICY);
        }
        if (DRIVE_THE_DEBATE.equals(secretId)) {
            if (seat.hasAbility("galactic_threat")) return DRIVE_THE_DEBATE_UNDER_THREAT_CHANCE;
            return DriveTheDebateOdds.chance(game, seat);
        }
        return achievableCompletion(game, seat, secretId);
    }
}
