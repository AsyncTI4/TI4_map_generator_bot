package ti4.ai.tactical;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ti4.ai.brain.StrategyCard;
import ti4.ai.eval.BoardView;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.Wallet;
import ti4.ai.scoring.WinRace;
import ti4.ai.tactical.IncomingForces.Horizon;
import ti4.ai.tactical.IncomingForces.IncomingForce;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

final class Standings {

    static final double SECRET_CHANCE = 0.35;
    private static final double WIN_WINDOW = 1.5;
    private static final double HURT_PER_LEAD_POINT = 0.25;
    private static final double SOLE_LEADER_HURT = 0.25;
    private static final double MIN_HURT = 0.5;
    private static final double MAX_HURT = 1.5;
    private static final double CALM_DENIAL = 0.4;
    private static final double WINNING_DENIAL = 2.0;
    private static final double CUSTODIANS_CHANCE = 0.5;
    private static final int CUSTODIANS_INFLUENCE = 6;

    private final Game game;
    private final Player seat;
    private final IncomingForces incoming;
    private final Map<String, List<String>> ready = new HashMap<>();
    private final Map<String, Double> projections = new HashMap<>();
    private List<Player> winnersBefore;

    Standings(Game game, Player seat, IncomingForces incoming) {
        this.game = game;
        this.seat = seat;
        this.incoming = incoming;
    }

    int goal() {
        return game.getVp();
    }

    int vp(Player player) {
        return player.getTotalVictoryPoints();
    }

    // TODO: ready (engine progress) and ObjectiveValue.brokenReady (footprint model) can disagree; two spend objectives
    // under Imperial may not both be affordable
    List<String> ready(Player player) {
        if (player.isNeutral()) return List.of();
        return ready.computeIfAbsent(player.getFaction(), faction -> ObjectivePolicy.scorablePublics(game, player));
    }

    boolean holdsImperial(Player player) {
        return holdsUnplayed(player, StrategyCard.IMPERIAL);
    }

    boolean holdsUnplayedDiplomacy(Player player) {
        return holdsUnplayed(player, StrategyCard.DIPLOMACY);
    }

    private boolean holdsUnplayed(Player player, StrategyCard card) {
        return player.getSCs().stream()
                .anyMatch(initiative -> StrategyCard.of(game, initiative) == card
                        && !game.getPlayedSCs().contains(initiative));
    }

    Optional<Player> imperialHolder() {
        return game.getRealPlayers().stream().filter(this::holdsImperial).findFirst();
    }

    int publicScores(Player player) {
        return 1 + (holdsImperial(player) ? 1 : 0);
    }

    double publicPoints(Player player) {
        return publicPoints(player, ready(player));
    }

    double publicPoints(Player player, Collection<String> readyIds) {
        return readyIds.stream()
                .map(ObjectivePolicy::victoryPoints)
                .sorted(Comparator.reverseOrder())
                .limit(publicScores(player))
                .mapToInt(Integer::intValue)
                .sum();
    }

    double imperialPoint(Player player) {
        return holdsImperial(player) && player.controlsMecatol(false) ? 1 : 0;
    }

    double custodiansPoint(Player player) {
        Tile mecatol = game.getMecatolTile();
        if (mecatol == null || mecatol.getPlanetUnitHolders().stream().noneMatch(BoardView::hasCustodians)) return 0;
        IncomingForce force = incoming.of(player, mecatol, Horizon.IMMEDIATE);
        boolean landsGroundForces = !force.isEmpty() && force.carriesGround();
        boolean canPay = Wallet.of(game, player).influence() >= CUSTODIANS_INFLUENCE;
        return landsGroundForces && canPay ? CUSTODIANS_CHANCE : 0;
    }

    double secretPoint(Player player) {
        return player.getSo() > 0 ? SECRET_CHANCE : 0;
    }

    double projected(Player player) {
        if (player.isNeutral()) return 0;
        Double known = projections.get(player.getFaction());
        if (known != null) return known;
        double projected = vp(player)
                + publicPoints(player)
                + imperialPoint(player)
                + custodiansPoint(player)
                + secretPoint(player);
        projections.put(player.getFaction(), projected);
        return projected;
    }

    double winThreat(Player player) {
        if (winsBeforeTheSeat(player)) return 1;
        return clamp((projected(player) - (goal() - WIN_WINDOW)) / WIN_WINDOW, 0, 1);
    }

    boolean winsBeforeTheSeat(Player player) {
        if (winnersBefore == null) winnersBefore = WinRace.winnersBefore(game, seat);
        return winnersBefore.contains(player);
    }

    double hurtWeight(Player target) {
        if (target.isNeutral()) return 0;
        List<Player> others = game.getRealPlayers().stream()
                .filter(player -> player != target)
                .toList();
        double mean = others.stream().mapToInt(this::vp).average().orElse(vp(target));
        boolean soleLeader = vp(target) >= 1 && others.stream().allMatch(player -> vp(player) < vp(target));
        double weight = 1 + HURT_PER_LEAD_POINT * (vp(target) - mean) + (soleLeader ? SOLE_LEADER_HURT : 0);
        return clamp(weight, MIN_HURT, MAX_HURT);
    }

    double denialWeight(Player target) {
        double threat = winThreat(target);
        return CALM_DENIAL * hurtWeight(target) * (1 - threat) + WINNING_DENIAL * threat;
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }
}
