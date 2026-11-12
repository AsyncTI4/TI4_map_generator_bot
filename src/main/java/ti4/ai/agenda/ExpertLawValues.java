package ti4.ai.agenda;

import static ti4.ai.agenda.AgendaValueMath.isAgainst;
import static ti4.ai.agenda.AgendaValueMath.isFor;
import static ti4.ai.agenda.AgendaValueMath.lawRounds;
import static ti4.ai.agenda.AgendaValueMath.livePlayers;
import static ti4.ai.agenda.AgendaValueMath.liveRivals;
import static ti4.ai.agenda.AgendaValueMath.relativeToRivals;
import static ti4.ai.agenda.AgendaValueMath.speakerPosition;
import static ti4.ai.agenda.AgendaValueMath.unitsOnBoard;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.ActionCardPrior;
import ti4.ai.actioncards.ActionCardValue;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FactionAggression;
import ti4.ai.eval.GameHorizon;
import ti4.ai.eval.Territory;
import ti4.ai.eval.Territory.Zone;
import ti4.ai.explore.ExploreValues;
import ti4.ai.legendary.LegendaryPlanets;
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.WormholeModel.Wormhole;

@UtilityClass
class ExpertLawValues {

    private static final double RESEARCH_COST_PER_ROUND = 1.5;
    private static final String PROPAGATION = "propagation";
    private static final String CREUSS = "ghost";
    private static final double CREUSS_TRAVEL_EDGE_PER_ROUND = 1.0;
    private static final double SLICE_WORMHOLE_PER_ROUND = 0.25;
    private static final double AGGRESSION_SPREAD = 0.2;
    private static final Set<Zone> OWN_SLICE = EnumSet.of(Zone.HOME, Zone.LANE, Zone.SLICE);
    private static final Set<Wormhole> ALPHA_BETA = EnumSet.of(Wormhole.ALPHA, Wormhole.BETA);
    private static final Set<Wormhole> RECON_LOCKED =
            EnumSet.of(Wormhole.ALPHA, Wormhole.BETA, Wormhole.GAMMA, Wormhole.DELTA);
    private static final int SANCTIONED_HAND = 3;
    private static final String YSSARIL = "yssaril";
    private static final int REGULATED_FLEET = 4;
    private static final double FLEET_CAP_COST_PER_ROUND = 0.4;
    private static final String ARMADA = "armada";
    private static final int ARMADA_ROOM = 2;
    private static final String WAR_SUN_TECH = "ws";
    private static final double WAR_SUN_SUSTAIN_PER_ROUND = 0.3;
    private static final int SUSTAIN_ROUNDS_CAP = 3;
    private static final double WAR_SUN_ACCESS = 1.0;
    private static final double CHEAP_UNIT_LOSS_PER_ROUND = 1.6;
    private static final double MIN_SWARM_SHARE = 0.3;
    private static final double MAX_SWARM_SHARE = 3.0;
    private static final Set<String> CONSCRIPTION_FANS = Set.of("cabal", "l1z1x");
    private static final double CONSCRIPTION_FAN_EDGE_PER_ROUND = 1.0;
    private static final Set<String> FIGHTER_HEAVY = Set.of("naalu", "sol");
    private static final double FIGHTER_HEAVY_SHARE = 2.0;
    private static final String BASTION = "bastion";
    private static final double BASTION_NEBULA_EDGE_PER_ROUND = 1.0;
    private static final double LOCKED_SHIP_SHARE = 0.3;
    private static final double HOME_PRODUCTION_LOCK = 1.0;
    private static final double RECON_PLASTIC_PER_ROUND = 1.0;
    private static final double MAX_REPRESENTATIVE_PER_ROUND = 2.0;
    private static final double AGENDA_CONTROL_PER_ROUND = 4.0;
    private static final double CHECKS_PER_POINT_PER_ROUND = 0.5;
    private static final int CHECKS_GAP_CAP = 4;
    private static final double CHECKS_ORDER_PER_ROUND = 1.0;
    private static final double NEXUS_CREUSS_EDGE_PER_ROUND = 0.3;
    private static final double NEXUS_MALLICE_EDGE_PER_ROUND = 0.5;

    @Nullable
    static Double valueOf(AgendaView view, String agenda, String outcome, String ownVote) {
        if (!isFor(outcome) && !isAgainst(outcome)) return null;
        Game game = view.game();
        Player voter = view.voter();
        return switch (agenda) {
            case "revolution" -> antiIntellectualRevolution(game, voter, outcome);
            case "conventions" -> conventionsOfWar(view, outcome, ownVote);
            case "travel_ban" -> enforcedTravelBan(game, voter, outcome);
            case "sanctions" -> executiveSanctions(view, outcome);
            case "regulations" -> fleetRegulations(game, voter, outcome);
            case "defense_act" -> homelandDefenseAct(game, voter, outcome);
            case "schematics" -> publicizeWeaponSchematics(view, outcome);
            case "conscription" -> regulatedConscription(game, voter, outcome);
            case "shared_research" -> sharedResearch(game, voter, outcome);
            case "wormhole_recon" -> wormholeReconstruction(game, voter, outcome);
            case "representative_government", "rep_govt" -> representativeGovernment(game, voter, outcome, ownVote);
            case "checks" -> checksAndBalances(game, voter, outcome);
            case "nexus" -> nexusSovereignty(game, voter, outcome);
            default -> null;
        };
    }

    private static double antiIntellectualRevolution(Game game, Player voter, String outcome) {
        if (isAgainst(outcome)) return ForAgainstLawValues.antiIntellectualRevolution(game, voter, outcome);
        return relativeToRivals(game, voter, player -> -researchCost(game, player));
    }

    static double researchCost(Game game, Player player) {
        if (player.hasAbility(PROPAGATION)) return 0;
        return RESEARCH_COST_PER_ROUND * GameHorizon.roundsLeft(game);
    }

    private static double conventionsOfWar(AgendaView view, String outcome, String ownVote) {
        if (isAgainst(outcome) && isAgainst(ownVote)) return -handWorth(view, view.voter());
        return 0;
    }

    private static double enforcedTravelBan(Game game, Player voter, String outcome) {
        if (isAgainst(outcome)) return ForAgainstLawValues.enforcedTravelBan(game, voter, outcome);
        return relativeToRivals(game, voter, player -> travelBanGain(game, player));
    }

    static double travelBanGain(Game game, Player player) {
        double creuss = CREUSS.equals(player.getFaction()) ? CREUSS_TRAVEL_EDGE_PER_ROUND : 0;
        double weakness = (1 - FactionAggression.of(player).factor()) / AGGRESSION_SPREAD;
        double slice = sliceWormholes(game, player) * SLICE_WORMHOLE_PER_ROUND * weakness;
        return (creuss + slice) * lawRounds(game);
    }

    private static long sliceWormholes(Game game, Player player) {
        Territory territory = Territory.of(game, player);
        return game.getTileMap().values().stream()
                .filter(tile -> OWN_SLICE.contains(territory.zone(tile)))
                .filter(tile -> hasWormhole(game, tile, ALPHA_BETA))
                .count();
    }

    private static double executiveSanctions(AgendaView view, String outcome) {
        Game game = view.game();
        Player voter = view.voter();
        if (isAgainst(outcome)) return ForAgainstLawValues.executiveSanctions(game, voter, outcome);
        return relativeToRivals(game, voter, player -> -sanctionedCards(view, player));
    }

    static double sanctionedCards(AgendaView view, Player player) {
        if (YSSARIL.equals(player.getFaction())) return 0;
        if (!view.readsHandOf(player))
            return Math.max(0, player.getAcCount() - SANCTIONED_HAND) * ActionCardPrior.AVERAGE_CARD;
        List<String> worstFirst = ActionCardValue.discardOrder(view.game(), player);
        return worstFirst.stream()
                .limit(Math.max(0, worstFirst.size() - SANCTIONED_HAND))
                .mapToDouble(alias -> ActionCardValue.keepValue(view.game(), player, alias))
                .sum();
    }

    private static double fleetRegulations(Game game, Player voter, String outcome) {
        if (isAgainst(outcome)) return ForAgainstLawValues.fleetRegulations(game, voter, outcome);
        return relativeToRivals(game, voter, player -> -fleetCapCost(game, player));
    }

    static double fleetCapCost(Game game, Player player) {
        int fleet = player.getEffectiveFleetCC();
        int excess = Math.max(0, fleet - REGULATED_FLEET);
        double pressure = fleet >= REGULATED_FLEET ? FLEET_CAP_COST_PER_ROUND : FLEET_CAP_COST_PER_ROUND / 2;
        int room = player.hasAbility(ARMADA) ? ARMADA_ROOM : 0;
        double shipsPerToken = REGULATED_FLEET / (double) (REGULATED_FLEET + room);
        return (excess * ExploreValues.COMMAND_TOKEN + pressure * lawRounds(game)) * shipsPerToken;
    }

    private static double homelandDefenseAct(Game game, Player voter, String outcome) {
        if (isFor(outcome)) return 0;
        return ForAgainstLawValues.homelandDefenseAct(game, voter, outcome);
    }

    private static double publicizeWeaponSchematics(AgendaView view, String outcome) {
        Game game = view.game();
        Player voter = view.voter();
        if (isFor(outcome)) return relativeToRivals(game, voter, player -> schematicsFor(game, player));
        return relativeToRivals(game, voter, player -> ownsWarSunTech(player) ? -handWorth(view, player) : 0);
    }

    private static double schematicsFor(Game game, Player player) {
        int rounds = Math.min(GameHorizon.roundsLeft(game), SUSTAIN_ROUNDS_CAP);
        int warSuns = Math.max(unitsOnBoard(game, player, UnitType.Warsun), ownsWarSunTech(player) ? 1 : 0);
        return warSunAccess(game, player) - WAR_SUN_SUSTAIN_PER_ROUND * rounds * warSuns;
    }

    private static double warSunAccess(Game game, Player player) {
        if (ownsWarSunTech(player)) return 0;
        boolean anyOwner = game.getRealPlayers().stream().anyMatch(ExpertLawValues::ownsWarSunTech);
        return anyOwner ? WAR_SUN_ACCESS : 0;
    }

    private static boolean ownsWarSunTech(Player player) {
        return player.hasUpgradedUnit(WAR_SUN_TECH);
    }

    private static double regulatedConscription(Game game, Player voter, String outcome) {
        if (isAgainst(outcome)) return 0;
        double mean = livePlayers(game).stream()
                .mapToInt(player -> cheapUnits(game, player))
                .average()
                .orElse(0);
        return relativeToRivals(game, voter, player -> conscriptionGain(game, player, mean));
    }

    static double conscriptionGain(Game game, Player player, double meanCheapUnits) {
        if (CONSCRIPTION_FANS.contains(player.getFaction())) return CONSCRIPTION_FAN_EDGE_PER_ROUND * lawRounds(game);
        double share =
                Math.clamp((cheapUnits(game, player) + 1) / (meanCheapUnits + 1), MIN_SWARM_SHARE, MAX_SWARM_SHARE);
        if (FIGHTER_HEAVY.contains(player.getFaction())) share = Math.max(share, FIGHTER_HEAVY_SHARE);
        return -CHEAP_UNIT_LOSS_PER_ROUND * share * lawRounds(game);
    }

    private static int cheapUnits(Game game, Player player) {
        return unitsOnBoard(game, player, UnitType.Fighter) + unitsOnBoard(game, player, UnitType.Infantry);
    }

    private static double sharedResearch(Game game, Player voter, String outcome) {
        if (isFor(outcome)) return relativeToRivals(game, voter, player -> bastionNebulaEdge(game, player));
        return relativeToRivals(game, voter, player -> -homeLockCost(game, player));
    }

    static double bastionNebulaEdge(Game game, Player player) {
        return BASTION.equals(player.getFaction()) ? BASTION_NEBULA_EDGE_PER_ROUND * lawRounds(game) : 0;
    }

    static double homeLockCost(Game game, Player player) {
        Tile home = player.getHomeSystemTile();
        if (home == null) return 0;
        double ships = BoardView.shipCost(BoardView.space(home), player) * LOCKED_SHIP_SHARE;
        return ships + (hasForwardDock(game, player, home) ? 0 : HOME_PRODUCTION_LOCK);
    }

    private static boolean hasForwardDock(Game game, Player player, Tile home) {
        return game.getTileMap().values().stream()
                .filter(tile -> tile != home)
                .anyMatch(tile -> BoardView.countInTile(tile, player, UnitType.Spacedock) > 0);
    }

    private static double wormholeReconstruction(Game game, Player voter, String outcome) {
        if (isFor(outcome)) {
            double mean = livePlayers(game).stream()
                    .mapToDouble(player -> fleetCost(game, player))
                    .average()
                    .orElse(0);
            return relativeToRivals(game, voter, player -> plasticEdge(game, player, mean));
        }
        return relativeToRivals(game, voter, player -> -lockedShipCost(game, player));
    }

    static double plasticEdge(Game game, Player player, double meanFleetCost) {
        if (meanFleetCost <= 0) return 0;
        double strength = Math.clamp(fleetCost(game, player) / meanFleetCost - 1, -1, 1);
        return strength * RECON_PLASTIC_PER_ROUND * lawRounds(game);
    }

    private static double fleetCost(Game game, Player player) {
        return game.getTileMap().values().stream()
                .mapToDouble(tile -> BoardView.shipCost(BoardView.space(tile), player))
                .sum();
    }

    static double lockedShipCost(Game game, Player player) {
        return game.getTileMap().values().stream()
                        .filter(tile -> hasWormhole(game, tile, RECON_LOCKED))
                        .filter(tile -> !tile.hasPlayerCC(player))
                        .mapToDouble(tile -> BoardView.shipCost(BoardView.space(tile), player))
                        .sum()
                * LOCKED_SHIP_SHARE;
    }

    private static boolean hasWormhole(Game game, Tile tile, Set<Wormhole> kinds) {
        return tile.getWormholes(game).stream().anyMatch(kinds::contains);
    }

    private static double representativeGovernment(Game game, Player voter, String outcome, String ownVote) {
        if (isAgainst(outcome)) return ForAgainstLawValues.representativeGovernment(game, voter, outcome, ownVote);
        double perRound = Math.clamp(
                VoteCapacity.shareSwing(game, voter) * AGENDA_CONTROL_PER_ROUND,
                -MAX_REPRESENTATIVE_PER_ROUND,
                MAX_REPRESENTATIVE_PER_ROUND);
        return perRound * lawRounds(game);
    }

    private static double checksAndBalances(Game game, Player voter, String outcome) {
        if (isAgainst(outcome)) return ForAgainstLawValues.checksAndBalances(game, voter, outcome);
        double behind = pointGapBehind(game, voter) * CHECKS_PER_POINT_PER_ROUND;
        return (behind + speakerOrderLean(game, voter) * CHECKS_ORDER_PER_ROUND) * lawRounds(game);
    }

    private static double pointGapBehind(Game game, Player voter) {
        int own = voter.getTotalVictoryPoints();
        double others = liveRivals(game, voter).stream()
                .mapToInt(Player::getTotalVictoryPoints)
                .average()
                .orElse(own);
        return Math.clamp(others - own, -CHECKS_GAP_CAP, CHECKS_GAP_CAP);
    }

    static double speakerOrderLean(Game game, Player voter) {
        List<Player> order = livePlayers(game);
        int position = speakerPosition(game, voter);
        if (position < 0 || order.size() < 2) return 0;
        double middle = (order.size() - 1) / 2.0;
        double lean = (position - middle) / middle;
        if (lean < 0 && fearsSpeaker(game, voter)) return 0;
        return lean;
    }

    private static boolean fearsSpeaker(Game game, Player voter) {
        Player speaker = game.getSpeaker();
        return speaker != null && speaker != voter && ScoringStakes.contender(game, voter, speaker);
    }

    private static double nexusSovereignty(Game game, Player voter, String outcome) {
        if (isAgainst(outcome)) return ForAgainstLawValues.nexusSovereignty(game, voter, outcome);
        double creuss = CREUSS.equals(voter.getFaction()) ? NEXUS_CREUSS_EDGE_PER_ROUND : 0;
        double mallice = voter.getPlanets().contains(LegendaryPlanets.MALLICE) ? NEXUS_MALLICE_EDGE_PER_ROUND : 0;
        return (creuss + mallice) * lawRounds(game);
    }

    private static double handWorth(AgendaView view, Player holder) {
        if (!view.readsHandOf(holder)) return holder.getAcCount() * ActionCardPrior.AVERAGE_CARD;
        return holder.getActionCards().keySet().stream()
                .mapToDouble(alias -> ActionCardValue.keepValue(view.game(), holder, alias))
                .sum();
    }
}
