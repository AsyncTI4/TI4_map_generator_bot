package ti4.ai.agenda;

import static ti4.ai.agenda.AgendaValueMath.handValue;
import static ti4.ai.agenda.AgendaValueMath.isAgainst;
import static ti4.ai.agenda.AgendaValueMath.isFor;
import static ti4.ai.agenda.AgendaValueMath.lawRounds;
import static ti4.ai.agenda.AgendaValueMath.livePlayers;
import static ti4.ai.agenda.AgendaValueMath.liveRivals;
import static ti4.ai.agenda.AgendaValueMath.ownedPlanets;
import static ti4.ai.agenda.AgendaValueMath.relativeToRivals;
import static ti4.ai.agenda.AgendaValueMath.unitsOnBoard;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.ActionCardPrior;
import ti4.ai.eval.Adjacency;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.GameHorizon;
import ti4.ai.eval.ValueScale;
import ti4.ai.explore.ExploreValues;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.WormholeModel.Wormhole;

@UtilityClass
class ForAgainstLawValues {

    private static final double TECHS_PER_ROUND = 1.0;
    private static final double SHIP_LOSS_PER_TECH = 1.5;
    private static final String PROPAGATION = "propagation";
    private static final double SHELTER_PER_CULTURAL_PLANET = 0.1;
    private static final double BOMBARD_EDGE_PER_SHIP = 0.1;
    private static final String CULTURAL = "cultural";
    private static final double PDS_UNIT = 2.0;
    private static final String CREUSS = "ghost";
    private static final double CREUSS_TRAVEL_EDGE_PER_ROUND = 1.0;
    private static final int SANCTIONED_HAND = 3;
    private static final double HAND_CAP_COST_PER_ROUND = 0.5;
    private static final String YSSARIL = "yssaril";
    private static final int REGULATED_FLEET = 4;
    private static final double FLEET_CAP_COST_PER_ROUND = 0.4;
    private static final double PDS_STACKING_EDGE_PER_ROUND = 0.3;
    private static final int PDS_MINDED = 2;
    private static final double LIGHT_PDS_SHARE = 0.3;
    private static final String WAR_SUN_TECH = "ws";
    private static final double WAR_SUN_SUSTAIN_PER_ROUND = 1.0;
    private static final int SUSTAIN_ROUNDS_CAP = 3;
    private static final double WAR_SUN_ACCESS = 1.0;
    private static final double CHEAP_UNIT_LOSS_PER_ROUND = 1.6;
    private static final double MIN_SWARM_SHARE = 0.3;
    private static final double MAX_SWARM_SHARE = 3.0;
    private static final double NEBULA_MOBILITY_PER_ROUND = 0.2;
    private static final double HOME_LOCK_ONLY_DOCK = 2.5;
    private static final double HOME_LOCK_WITH_FORWARD_DOCK = 1.5;
    private static final Set<Wormhole> RECON_ADJACENT = EnumSet.of(Wormhole.ALPHA, Wormhole.BETA);
    private static final Set<Wormhole> RECON_LOCKED =
            EnumSet.of(Wormhole.ALPHA, Wormhole.BETA, Wormhole.GAMMA, Wormhole.DELTA);
    private static final double WORMHOLE_MOBILITY_PER_ROUND = 0.3;
    private static final double WORMHOLE_LOCK_COST = 1.5;
    private static final double AGENDA_CONTROL_PER_ROUND = 4.0;
    private static final double MECH_ABILITY_PER_ROUND = 0.5;
    private static final int FUTURE_MECHS = 1;
    private static final int ARTICLES_TRADE_GOODS = 3;
    private static final double CHECKS_PER_POINT_PER_ROUND = 0.5;
    private static final int CHECKS_GAP_CAP = 4;
    private static final int READIED_PLANETS = 3;
    private static final double NEXUS_CREUSS_EDGE_PER_ROUND = 0.3;
    private static final double GAMMA_MECATOL_HOLDER_LOSS_PER_ROUND = 0.5;
    private static final double GAMMA_MECATOL_ACCESS_PER_ROUND = 0.15;

    static double antiIntellectualRevolution(Game game, Player voter, String outcome) {
        if (isFor(outcome)) return relativeToRivals(game, voter, player -> -researchShipLosses(game, player));
        if (isAgainst(outcome)) return relativeToRivals(game, voter, player -> -techExhaustLoss(game, player));
        return 0;
    }

    private static double researchShipLosses(Game game, Player player) {
        if (player.hasAbility(PROPAGATION)) return 0;
        return TECHS_PER_ROUND * SHIP_LOSS_PER_TECH * lawRounds(game);
    }

    private static double techExhaustLoss(Game game, Player player) {
        return ownedPlanets(game, player).stream()
                .mapToDouble(AgendaValueMath::spendValue)
                .sorted()
                .limit(player.getTechs().size())
                .sum();
    }

    static double conventionsOfWar(Game game, Player voter, String outcome, String ownVote) {
        if (isFor(outcome)) return relativeToRivals(game, voter, player -> bombardmentBanGain(game, player));
        if (isAgainst(outcome) && isAgainst(ownVote)) return -handValue(game, voter, voter);
        return 0;
    }

    private static double bombardmentBanGain(Game game, Player player) {
        long cultural = ownedPlanets(game, player).stream()
                .filter(planet -> planet.getPlanetTypes().contains(CULTURAL))
                .count();
        int bombarders = unitsOnBoard(game, player, UnitType.Dreadnought) + unitsOnBoard(game, player, UnitType.Warsun);
        double perRound = cultural * SHELTER_PER_CULTURAL_PLANET - bombarders * BOMBARD_EDGE_PER_SHIP;
        return perRound * lawRounds(game);
    }

    static double enforcedTravelBan(Game game, Player voter, String outcome) {
        if (isFor(outcome)) return relativeToRivals(game, voter, player -> creussTravelEdge(game, player));
        if (isAgainst(outcome))
            return relativeToRivals(game, voter, player -> -PDS_UNIT * pdsNearWormholes(game, player));
        return 0;
    }

    private static double creussTravelEdge(Game game, Player player) {
        if (!CREUSS.equals(player.getFaction())) return 0;
        return CREUSS_TRAVEL_EDGE_PER_ROUND * lawRounds(game);
    }

    private static int pdsNearWormholes(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> BoardView.countInTile(tile, player, UnitType.Pds) > 0)
                .filter(tile -> nearWormhole(game, player, tile))
                .mapToInt(tile -> BoardView.countInTile(tile, player, UnitType.Pds))
                .sum();
    }

    private static boolean nearWormhole(Game game, Player player, Tile tile) {
        return Adjacency.withSystem(game, player, tile.getPosition()).stream()
                .map(game::getTileByPosition)
                .anyMatch(near -> near != null && !near.getWormholes(game).isEmpty());
    }

    static double executiveSanctions(Game game, Player voter, String outcome) {
        if (isFor(outcome)) return relativeToRivals(game, voter, player -> -handCapCost(game, player));
        if (isAgainst(outcome)) return relativeToRivals(game, voter, ForAgainstLawValues::randomDiscardCost);
        return 0;
    }

    private static double handCapCost(Game game, Player player) {
        if (YSSARIL.equals(player.getFaction())) return 0;
        int excess = Math.max(0, player.getAcCount() - SANCTIONED_HAND);
        return excess * ActionCardPrior.AVERAGE_CARD + HAND_CAP_COST_PER_ROUND * lawRounds(game);
    }

    private static double randomDiscardCost(Player player) {
        return player.getAcCount() > 0 ? -ActionCardPrior.AVERAGE_CARD : 0;
    }

    static double fleetRegulations(Game game, Player voter, String outcome) {
        if (isFor(outcome)) return relativeToRivals(game, voter, player -> -fleetCapCost(game, player));
        if (isAgainst(outcome)) return relativeToRivals(game, voter, player -> ExploreValues.COMMAND_TOKEN);
        return 0;
    }

    private static double fleetCapCost(Game game, Player player) {
        int fleet = player.getEffectiveFleetCC();
        int excess = Math.max(0, fleet - REGULATED_FLEET);
        double pressure = fleet >= REGULATED_FLEET ? FLEET_CAP_COST_PER_ROUND : FLEET_CAP_COST_PER_ROUND / 2;
        return excess * ExploreValues.COMMAND_TOKEN + pressure * lawRounds(game);
    }

    static double homelandDefenseAct(Game game, Player voter, String outcome) {
        if (isFor(outcome)) return relativeToRivals(game, voter, player -> pdsStackingEdge(game, player));
        if (isAgainst(outcome))
            return relativeToRivals(
                    game, voter, player -> unitsOnBoard(game, player, UnitType.Pds) > 0 ? -PDS_UNIT : 0);
        return 0;
    }

    private static double pdsStackingEdge(Game game, Player player) {
        double share = unitsOnBoard(game, player, UnitType.Pds) >= PDS_MINDED ? 1.0 : LIGHT_PDS_SHARE;
        return PDS_STACKING_EDGE_PER_ROUND * share * lawRounds(game);
    }

    static double publicizeWeaponSchematics(Game game, Player voter, String outcome) {
        if (isFor(outcome)) return relativeToRivals(game, voter, player -> schematicsFor(game, player));
        if (isAgainst(outcome)) return relativeToRivals(game, voter, player -> schematicsAgainst(game, voter, player));
        return 0;
    }

    private static double schematicsFor(Game game, Player player) {
        int rounds = Math.min(GameHorizon.roundsLeft(game), SUSTAIN_ROUNDS_CAP);
        int warSuns = Math.max(unitsOnBoard(game, player, UnitType.Warsun), ownsWarSunTech(player) ? 1 : 0);
        return warSunAccess(game, player) - WAR_SUN_SUSTAIN_PER_ROUND * rounds * warSuns;
    }

    private static double warSunAccess(Game game, Player player) {
        if (ownsWarSunTech(player)) return 0;
        boolean anyOwner = game.getRealPlayers().stream().anyMatch(ForAgainstLawValues::ownsWarSunTech);
        return anyOwner ? WAR_SUN_ACCESS : 0;
    }

    private static double schematicsAgainst(Game game, Player voter, Player player) {
        if (!ownsWarSunTech(player)) return 0;
        return -handValue(game, player, voter);
    }

    private static boolean ownsWarSunTech(Player player) {
        return player.hasUpgradedUnit(WAR_SUN_TECH);
    }

    static double regulatedConscription(Game game, Player voter, String outcome) {
        if (!isFor(outcome)) return 0;
        double mean = livePlayers(game).stream()
                .mapToInt(player -> cheapUnits(game, player))
                .average()
                .orElse(0);
        return relativeToRivals(game, voter, player -> -conscriptionLoss(game, player, mean));
    }

    private static double conscriptionLoss(Game game, Player player, double meanCheapUnits) {
        double share =
                Math.clamp((cheapUnits(game, player) + 1) / (meanCheapUnits + 1), MIN_SWARM_SHARE, MAX_SWARM_SHARE);
        return CHEAP_UNIT_LOSS_PER_ROUND * share * lawRounds(game);
    }

    private static int cheapUnits(Game game, Player player) {
        return unitsOnBoard(game, player, UnitType.Fighter) + unitsOnBoard(game, player, UnitType.Infantry);
    }

    static double sharedResearch(Game game, Player voter, String outcome) {
        if (isFor(outcome)) return relativeToRivals(game, voter, player -> nebulaMobility(game));
        if (isAgainst(outcome)) return relativeToRivals(game, voter, player -> -homeLockCost(game, player));
        return 0;
    }

    private static double nebulaMobility(Game game) {
        boolean anyNebula = game.getTileMap().values().stream().anyMatch(tile -> tile.isNebula(game));
        return anyNebula ? NEBULA_MOBILITY_PER_ROUND * lawRounds(game) : 0;
    }

    private static double homeLockCost(Game game, Player player) {
        Tile home = player.getHomeSystemTile();
        if (home == null) return 0;
        return hasForwardDock(game, player, home) ? HOME_LOCK_WITH_FORWARD_DOCK : HOME_LOCK_ONLY_DOCK;
    }

    private static boolean hasForwardDock(Game game, Player player, Tile home) {
        return game.getTileMap().values().stream()
                .filter(tile -> tile != home)
                .anyMatch(tile -> BoardView.countInTile(tile, player, UnitType.Spacedock) > 0);
    }

    static double wormholeReconstruction(Game game, Player voter, String outcome) {
        if (isFor(outcome)) return relativeToRivals(game, voter, player -> wormholeMobility(game, player));
        if (isAgainst(outcome))
            return relativeToRivals(game, voter, player -> -wormholeLocks(game, player) * WORMHOLE_LOCK_COST);
        return 0;
    }

    private static double wormholeMobility(Game game, Player player) {
        Tile home = player.getHomeSystemTile();
        boolean homeLinked = home != null && hasWormhole(game, home, RECON_ADJACENT);
        boolean shipsLinked = game.getTileMap().values().stream()
                .anyMatch(tile -> hasWormhole(game, tile, RECON_ADJACENT) && hasShips(tile, player));
        if (!homeLinked && !shipsLinked) return 0;
        return WORMHOLE_MOBILITY_PER_ROUND * lawRounds(game);
    }

    private static long wormholeLocks(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> hasWormhole(game, tile, RECON_LOCKED))
                .filter(tile -> hasShips(tile, player))
                .filter(tile -> !tile.hasPlayerCC(player))
                .count();
    }

    private static boolean hasWormhole(Game game, Tile tile, Set<Wormhole> kinds) {
        return tile.getWormholes(game).stream().anyMatch(kinds::contains);
    }

    private static boolean hasShips(Tile tile, Player player) {
        return !BoardView.ships(BoardView.space(tile), player).isEmpty();
    }

    static double representativeGovernment(Game game, Player voter, String outcome, String ownVote) {
        if (isFor(outcome)) return voteShareSwing(game, voter) * AGENDA_CONTROL_PER_ROUND * lawRounds(game);
        if (isAgainst(outcome) && isAgainst(ownVote)) return -culturalExhaustion(game, voter);
        return 0;
    }

    private static double voteShareSwing(Game game, Player voter) {
        List<Player> seats = livePlayers(game);
        int total =
                seats.stream().mapToInt(player -> totalInfluence(game, player)).sum();
        if (total <= 0) return 0;
        double current = totalInfluence(game, voter) / (double) total;
        return 1.0 / seats.size() - current;
    }

    private static int totalInfluence(Game game, Player player) {
        return ownedPlanets(game, player).stream()
                .mapToInt(Planet::getInfluence)
                .sum();
    }

    private static double culturalExhaustion(Game game, Player voter) {
        return ownedPlanets(game, voter).stream()
                .filter(planet -> planet.getPlanetTypes().contains(CULTURAL))
                .mapToDouble(AgendaValueMath::spendValue)
                .sum();
    }

    static double articlesOfWar(Game game, Player voter, String outcome, String ownVote) {
        if (isFor(outcome)) return -relativeToRivals(game, voter, player -> mechAbilities(game, player));
        if (isAgainst(outcome) && isFor(ownVote)) return ARTICLES_TRADE_GOODS * ValueScale.TRADE_GOOD;
        return 0;
    }

    private static double mechAbilities(Game game, Player player) {
        int mechs = unitsOnBoard(game, player, UnitType.Mech);
        return MECH_ABILITY_PER_ROUND * (mechs + FUTURE_MECHS) * lawRounds(game);
    }

    static double checksAndBalances(Game game, Player voter, String outcome) {
        if (isFor(outcome)) return pointGapBehind(game, voter) * CHECKS_PER_POINT_PER_ROUND * lawRounds(game);
        if (isAgainst(outcome)) return -relativeToRivals(game, voter, player -> unreadiedPlanets(game, player));
        return 0;
    }

    private static double pointGapBehind(Game game, Player voter) {
        int own = voter.getTotalVictoryPoints();
        double others = liveRivals(game, voter).stream()
                .mapToInt(Player::getTotalVictoryPoints)
                .average()
                .orElse(own);
        return Math.clamp(others - own, -CHECKS_GAP_CAP, CHECKS_GAP_CAP);
    }

    private static double unreadiedPlanets(Game game, Player player) {
        double[] uses = player.getExhaustedPlanets().stream()
                .map(name -> game.getPlanetsInfo().get(name))
                .filter(Objects::nonNull)
                .mapToDouble(AgendaValueMath::spendValue)
                .sorted()
                .toArray();
        double lost = 0;
        for (int i = 0; i < uses.length - READIED_PLANETS; i++) lost += uses[i];
        return lost;
    }

    static double nexusSovereignty(Game game, Player voter, String outcome) {
        if (isFor(outcome)) return closedNexusPerRound(voter) * lawRounds(game);
        if (isAgainst(outcome)) return gammaMecatolPerRound(voter) * lawRounds(game);
        return 0;
    }

    private static double closedNexusPerRound(Player voter) {
        return CREUSS.equals(voter.getFaction()) ? NEXUS_CREUSS_EDGE_PER_ROUND : 0;
    }

    private static double gammaMecatolPerRound(Player voter) {
        return voter.controlsMecatol(true) ? -GAMMA_MECATOL_HOLDER_LOSS_PER_ROUND : GAMMA_MECATOL_ACCESS_PER_ROUND;
    }
}
