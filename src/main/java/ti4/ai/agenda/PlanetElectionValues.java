package ti4.ai.agenda;

import static ti4.ai.agenda.AgendaValueMath.lawRounds;
import static ti4.ai.agenda.AgendaValueMath.livePlayers;
import static ti4.ai.agenda.AgendaValueMath.relativeToRivals;
import static ti4.ai.agenda.AgendaValueMath.rivalGainCost;
import static ti4.ai.agenda.AgendaValueMath.unitCost;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.GameHorizon;
import ti4.ai.eval.ValueScale;
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;

@UtilityClass
class PlanetElectionValues {

    private static final double PLANET_HOLD_ROUNDS = 2;
    private static final double INFANTRY_TG = 1.0;
    private static final double MECH_TG = 2.5;
    private static final double PDS_TG = 2.5;
    private static final double SPACE_DOCK_TG = 4.0;
    private static final double EXPOSED_PLANET_SHARE = 0.5;
    private static final double PLANET_USE_SHARE = 0.75;
    private static final int CORE_MINING_RESOURCES = 2;
    private static final double INFANTRY_COST = 0.5;
    private static final double SAFE_PLANET_SHARE = 0.3;
    private static final double PDS_COST = 2;
    private static final double SPACE_DOCK_COST = 4;
    private static final double HOLY_HOLD_SHARE = 0.8;
    private static final double DOCK_ROUND_VALUE = 1.5;
    private static final int HOLY_POINT = 1;
    private static final double PREREQ_SKIP_VALUE = 1.5;
    private static final double RESEARCH_ROUND_SHARE = 0.5;
    private static final int SKIP_ROUNDS_COUNTED = 3;
    private static final double COLOR_SATURATION = 3;
    private static final int SANCTUARY_INFLUENCE = 2;
    private static final double TERRAFORM_FLEXIBILITY = 0.2;

    static double colonialRedistribution(Game game, Player voter, String outcome) {
        Planet planet = game.getPlanetsInfo().get(outcome);
        Player controller = BoardView.controller(game, outcome);
        if (planet == null || controller == null) return 0;
        List<Player> fewest = fewestPointPlayers(game);
        double swing = BoardView.planetValue(planet) * Math.min(GameHorizon.roundsLeft(game), PLANET_HOLD_ROUNDS);
        return relativeToRivals(game, voter, player -> redistributionGain(planet, controller, fewest, swing, player));
    }

    private static double redistributionGain(
            Planet planet, Player controller, List<Player> fewest, double swing, Player player) {
        if (player == controller) return controllerGain(planet, controller, fewest, swing);
        if (fewest.contains(controller) || !fewest.contains(player)) return 0;
        return (swing + INFANTRY_TG) / fewest.size();
    }

    private static double controllerGain(Planet planet, Player controller, List<Player> fewest, double swing) {
        double garrison = garrisonValue(planet, controller);
        if (fewest.contains(controller)) return INFANTRY_TG - garrison;
        return -garrison - swing;
    }

    private static List<Player> fewestPointPlayers(Game game) {
        List<Player> players = livePlayers(game);
        int fewest =
                players.stream().mapToInt(Player::getTotalVictoryPoints).min().orElse(0);
        return players.stream()
                .filter(player -> player.getTotalVictoryPoints() == fewest)
                .toList();
    }

    private static double garrisonValue(Planet planet, Player player) {
        return BoardView.count(planet, player, UnitType.Infantry) * INFANTRY_TG
                + BoardView.count(planet, player, UnitType.Mech) * MECH_TG
                + BoardView.count(planet, player, UnitType.Pds) * PDS_TG
                + BoardView.count(planet, player, UnitType.Spacedock) * SPACE_DOCK_TG;
    }

    static double compensatedDisarmament(Game game, Player voter, String outcome) {
        Planet planet = game.getPlanetsInfo().get(outcome);
        Player controller = BoardView.controller(game, outcome);
        if (planet == null || controller == null) return 0;
        double gain = disarmamentGain(planet, controller);
        return relativeToRivals(game, voter, player -> player == controller ? gain : 0);
    }

    private static double disarmamentGain(Planet planet, Player controller) {
        int infantry = BoardView.count(planet, controller, UnitType.Infantry);
        int mechs = BoardView.count(planet, controller, UnitType.Mech);
        if (infantry + mechs == 0) return 0;
        return (infantry + mechs) * ValueScale.TRADE_GOOD
                - infantry * INFANTRY_TG
                - mechs * MECH_TG
                - EXPOSED_PLANET_SHARE * BoardView.planetValue(planet);
    }

    static double coreMining(Game game, Player voter, String outcome) {
        return electedPlanetGain(
                game, voter, "core_mining", outcome, (owner, planet) -> coreMiningGain(game, owner, planet));
    }

    private static double coreMiningGain(Game game, Player owner, Planet planet) {
        double income = CORE_MINING_RESOURCES * ValueScale.RESOURCE * PLANET_USE_SHARE * lawRounds(game);
        double infantryLost =
                BoardView.count(planet, owner, UnitType.Infantry) > 0 ? INFANTRY_COST * ValueScale.RESOURCE : 0;
        return income - infantryLost;
    }

    static double demilitarizedZone(Game game, Player voter, String outcome) {
        return electedPlanetGain(
                game, voter, "demilitarized_zone", outcome, (owner, planet) -> demilitarizedGain(game, owner, planet));
    }

    private static double demilitarizedGain(Game game, Player owner, Planet planet) {
        double safety = SAFE_PLANET_SHARE * BoardView.planetValue(planet);
        double destroyed = ValueScale.RESOURCE * (unitCost(planet, owner) + structureCost(planet, owner));
        return safety - destroyed - blockedProduction(game, owner, planet);
    }

    private static double structureCost(Planet planet, Player owner) {
        return PDS_COST * BoardView.count(planet, owner, UnitType.Pds)
                + SPACE_DOCK_COST * BoardView.count(planet, owner, UnitType.Spacedock);
    }

    static double holyPlanetOfIxth(Game game, Player voter, String outcome) {
        Planet planet = game.getPlanetsInfo().get(outcome);
        if (planet == null || !AgendaPolicy.hasRequiredTrait("holy_planet_of_ixth", planet)) return 0;
        Player owner = BoardView.controller(game, outcome);
        if (owner == null) return 0;
        double lostProduction = blockedProduction(game, owner, planet);
        if (owner == voter) {
            return holdShare(game, voter) * ScoringStakes.scoreValue(game, voter, HOLY_POINT) - lostProduction;
        }
        return -holdShare(game, owner) * ScoringStakes.rivalPointStake(game, owner, HOLY_POINT)
                + rivalGainCost(game, voter, owner, lostProduction);
    }

    private static double holdShare(Game game, Player owner) {
        return owner.getTotalVictoryPoints() + HOLY_POINT >= GameHorizon.goal(game) ? 1 : HOLY_HOLD_SHARE;
    }

    private static double blockedProduction(Game game, Player owner, Planet planet) {
        if (BoardView.count(planet, owner, UnitType.Spacedock) == 0) return 0;
        return DOCK_ROUND_VALUE * lawRounds(game);
    }

    static double researchTeamBiotic(Game game, Player voter, String outcome) {
        return researchTeam(game, voter, "rt_biotic", outcome, TechnologyModel::isBioticTech);
    }

    static double researchTeamCybernetic(Game game, Player voter, String outcome) {
        return researchTeam(game, voter, "rt_cybernetic", outcome, TechnologyModel::isCyberneticTech);
    }

    static double researchTeamPropulsion(Game game, Player voter, String outcome) {
        return researchTeam(game, voter, "rt_propulsion", outcome, TechnologyModel::isPropulsionTech);
    }

    static double researchTeamWarfare(Game game, Player voter, String outcome) {
        return researchTeam(game, voter, "rt_warfare", outcome, TechnologyModel::isWarfareTech);
    }

    private static double researchTeam(
            Game game, Player voter, String agenda, String outcome, Predicate<TechnologyModel> color) {
        return electedPlanetGain(
                game, voter, agenda, outcome, (owner, planet) -> prerequisiteSkipGain(game, owner, color));
    }

    private static double prerequisiteSkipGain(Game game, Player owner, Predicate<TechnologyModel> color) {
        long owned = owner.getTechs().stream()
                .map(Mapper::getTech)
                .filter(Objects::nonNull)
                .filter(color)
                .count();
        double need = Math.max(0, 1 - owned / COLOR_SATURATION);
        int rounds = Math.min(SKIP_ROUNDS_COUNTED, GameHorizon.roundsLeft(game));
        return PREREQ_SKIP_VALUE * RESEARCH_ROUND_SHARE * rounds * need;
    }

    static double senateSanctuary(Game game, Player voter, String outcome) {
        double gain = SANCTUARY_INFLUENCE * ValueScale.INFLUENCE * PLANET_USE_SHARE * lawRounds(game);
        return electedPlanetGain(game, voter, "senate_sanctuary", outcome, (owner, planet) -> gain);
    }

    static double terraformingInitiative(Game game, Player voter, String outcome) {
        double gain = (ValueScale.RESOURCE + TERRAFORM_FLEXIBILITY) * PLANET_USE_SHARE * lawRounds(game);
        return electedPlanetGain(game, voter, "terraforming_initiative", outcome, (owner, planet) -> gain);
    }

    private static double electedPlanetGain(
            Game game, Player voter, String agenda, String outcome, ToDoubleBiFunction<Player, Planet> ownerGain) {
        Planet planet = game.getPlanetsInfo().get(outcome);
        if (planet == null || !AgendaPolicy.hasRequiredTrait(agenda, planet)) return 0;
        Player owner = BoardView.controller(game, outcome);
        if (owner == null || !game.getRealPlayers().contains(owner)) return 0;
        double gain = ownerGain.applyAsDouble(owner, planet);
        if (owner == voter) return gain;
        return -rivalGainCost(game, voter, owner, gain);
    }
}
