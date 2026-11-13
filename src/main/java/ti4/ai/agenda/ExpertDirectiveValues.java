package ti4.ai.agenda;

import static ti4.ai.agenda.AgendaValueMath.isAgainst;
import static ti4.ai.agenda.AgendaValueMath.isFor;
import static ti4.ai.agenda.AgendaValueMath.livePlayers;
import static ti4.ai.agenda.AgendaValueMath.liveRivals;
import static ti4.ai.agenda.AgendaValueMath.ownedPlanets;
import static ti4.ai.agenda.AgendaValueMath.relativeToRivals;
import static ti4.ai.agenda.AgendaValueMath.threat;
import static ti4.ai.agenda.AgendaValueMath.unitCost;
import static ti4.ai.agenda.AgendaValueMath.unitsOnBoard;
import static ti4.ai.agenda.AgendaValueMath.votedOn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.Adjacency;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FleetLimit;
import ti4.ai.eval.GameHorizon;
import ti4.ai.eval.PlanetStake;
import ti4.ai.eval.ValueScale;
import ti4.ai.scoring.ObjectiveCatalog;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.strategy.CardValueScale;
import ti4.ai.strategy.CommandTokenValue;
import ti4.ai.strategy.ResearchPolicy;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;
import ti4.model.WormholeModel.Wormhole;

@UtilityClass
class ExpertDirectiveValues {

    static final double LEADER_FOCUS = 1.5;
    static final double EQUALITY_FOR_BIAS = 0.5;
    static final double EQUALITY_BADLY_HURT = 4.0;
    static final double ARTIFACT_FOR_BIAS = 0.5;
    static final double ARTIFACT_DEVASTATING = 6.0;
    static final double INCENTIVE_STAGE_TWO_FACTION_BONUS = 1.5;
    static final double INCENTIVE_LEAD_PER_POINT = 0.5;
    static final int INCENTIVE_LEAD_CAP = 3;
    static final Set<String> STAGE_TWO_FACTIONS = Set.of("jolnar", "deepwrought", "hacan");
    static final double PLOWSHARES_LEADER_BONUS = 1.5;
    static final double PLOWSHARES_PER_SPEND_OBJECTIVE = 0.5;
    static final double EMPTY_TACTIC_POOL = CardValueScale.HELD_TOKEN;
    static final double EMPTY_STRATEGY_POOL = 3.0;

    private static final int KEPT_DREADNOUGHTS = 2;
    private static final int KEPT_CRUISERS = 4;
    private static final int EQUALITY_TRADE_GOODS = 5;
    private static final String PILLAGE = "pillage";
    private static final int STAGE_ONE_POINTS = 1;
    private static final int STAGE_TWO_POINTS = 2;
    private static final double STAGE_ONE_SCORE_CHANCE = 0.5;
    private static final double STAGE_TWO_SCORE_CHANCE = 0.3;
    private static final double SCORING_RUNWAY_ROUNDS = 3.0;
    private static final double MIN_STRENGTH = 0.5;
    private static final double MAX_STRENGTH = 1.5;
    private static final double TECH_ROLL_CHANCE = 0.5;
    private static final double TWO_TECHS = 1.8;
    static final double BLAST_UNIT_LOSS = 0.5;
    static final double MECATOL_CONTROL_LOSS = 4.0;
    static final double ARTIFACT_POINT_SHARE = 0.5;
    private static final int ADJACENT_LOSSES = 3;
    private static final double INFANTRY_BOARD_WORTH = 0.8;
    private static final int INFANTRY_SUPPLY = 12;
    private static final int ONE_TOKEN = 1;
    private static final int CLANDESTINE_TOKENS = 2;
    private static final int SPARE_FLEET_POOL = 3;

    @Nullable
    static Double valueOf(AgendaView view, String agenda, String outcome, String ownVote) {
        Game game = view.game();
        Player voter = view.voter();
        return switch (agenda) {
            case "arms_reduction" -> armsReduction(game, voter, outcome);
            case "economic_equality" -> economicEquality(game, voter, outcome);
            case "incentive" -> incentiveProgram(game, voter, outcome);
            case "artifact" -> ixthianArtifact(view, outcome);
            case "plowshares" -> swordsToPlowshares(game, voter, outcome);
            case "wormhole_research" -> wormholeResearch(view, outcome, ownVote);
            case "cladenstine" -> clandestineOperations(game, voter, outcome);
            default -> null;
        };
    }

    private static double leaderFocused(Game game, Player voter, ToDoubleFunction<Player> gain) {
        double own = gain.applyAsDouble(voter);
        List<Player> rivals = liveRivals(game, voter);
        if (rivals.isEmpty()) return own;
        double rivalGain = rivals.stream()
                .mapToDouble(rival -> gain.applyAsDouble(rival) * threat(game, rival) * leaderFocus(game, voter, rival))
                .sum();
        return own - rivalGain / rivals.size();
    }

    private static double leaderFocus(Game game, Player voter, Player rival) {
        return ScoringStakes.contender(game, voter, rival) ? LEADER_FOCUS : 1.0;
    }

    private static double armsReduction(Game game, Player voter, String outcome) {
        if (isFor(outcome)) return leaderFocused(game, voter, player -> -excessFleetValue(game, player));
        if (isAgainst(outcome)) return leaderFocused(game, voter, player -> -specialtyExhaustLoss(game, player));
        return 0;
    }

    private static double excessFleetValue(Game game, Player player) {
        return excessShipValue(game, player, UnitType.Dreadnought, KEPT_DREADNOUGHTS)
                + excessShipValue(game, player, UnitType.Cruiser, KEPT_CRUISERS);
    }

    private static double excessShipValue(Game game, Player player, UnitType type, int kept) {
        int excess = unitsOnBoard(game, player, type) - kept;
        if (excess <= 0) return 0;
        double cost = BoardView.model(player, type).map(UnitModel::getCost).orElse(0f);
        return excess * cost * ValueScale.RESOURCE;
    }

    private static double specialtyExhaustLoss(Game game, Player player) {
        return ownedPlanets(game, player).stream()
                .filter(planet -> !planet.getTechSpecialities().isEmpty())
                .mapToDouble(AgendaValueMath::spendValue)
                .sum();
    }

    private static double economicEquality(Game game, Player voter, String outcome) {
        if (isFor(outcome)) {
            double value = relativeToRivals(game, voter, ExpertDirectiveValues::equalityGain);
            return equalityGain(voter) > -EQUALITY_BADLY_HURT ? value + EQUALITY_FOR_BIAS : value;
        }
        if (isAgainst(outcome)) return relativeToRivals(game, voter, player -> -player.getTg() * ValueScale.TRADE_GOOD);
        return 0;
    }

    private static double equalityGain(Player player) {
        return (EQUALITY_TRADE_GOODS - player.getTg() + pillageSwing(player)) * ValueScale.TRADE_GOOD;
    }

    private static int pillageSwing(Player player) {
        if (player.hasAbility(PILLAGE))
            return player.getNeighbouringPlayers(true).size();
        boolean pillaged =
                player.getNeighbouringPlayers(true).stream().anyMatch(neighbour -> neighbour.hasAbility(PILLAGE));
        return pillaged ? -1 : 0;
    }

    private static double incentiveProgram(Game game, Player voter, String outcome) {
        if (isFor(outcome))
            return newObjectiveRace(game, voter, STAGE_ONE_POINTS * STAGE_ONE_SCORE_CHANCE) + leadTerm(game, voter);
        if (isAgainst(outcome))
            return newObjectiveRace(game, voter, STAGE_TWO_POINTS * STAGE_TWO_SCORE_CHANCE)
                    - leadTerm(game, voter)
                    + stageTwoFactionBonus(voter);
        return 0;
    }

    private static double leadTerm(Game game, Player voter) {
        double rivals = liveRivals(game, voter).stream()
                .mapToInt(Player::getTotalVictoryPoints)
                .average()
                .orElse(voter.getTotalVictoryPoints());
        double lead = Math.clamp(voter.getTotalVictoryPoints() - rivals, -INCENTIVE_LEAD_CAP, INCENTIVE_LEAD_CAP);
        return INCENTIVE_LEAD_PER_POINT * lead;
    }

    private static double stageTwoFactionBonus(Player voter) {
        return STAGE_TWO_FACTIONS.contains(voter.getFaction()) ? INCENTIVE_STAGE_TWO_FACTION_BONUS : 0;
    }

    private static double newObjectiveRace(Game game, Player voter, double expectedPoints) {
        double runway = Math.min(1.0, GameHorizon.roundsLeft(game) / SCORING_RUNWAY_ROUNDS);
        double own = ScoringStakes.scoreValue(game, voter, expectedPoints * runway * scoringStrength(game, voter));
        double rivals = liveRivals(game, voter).stream()
                .mapToDouble(rival ->
                        ScoringStakes.rivalStake(game, rival, expectedPoints * runway * scoringStrength(game, rival)))
                .sum();
        return own - rivals;
    }

    private static double scoringStrength(Game game, Player player) {
        double average = livePlayers(game).stream()
                .mapToInt(other -> other.getPlanets().size())
                .average()
                .orElse(0);
        if (average <= 0) return 1.0;
        return Math.clamp(player.getPlanets().size() / average, MIN_STRENGTH, MAX_STRENGTH);
    }

    record ArtifactParts(
            double ownResearch,
            double rivalResearch,
            double ownBlast,
            double rivalBlast,
            int mecatolUnits,
            int adjacentUnits,
            double forBias,
            String ownTech,
            int ownTechPoints) {

        double research() {
            return ownResearch - rivalResearch;
        }

        double blast() {
            return ownBlast - rivalBlast;
        }

        double value() {
            return TECH_ROLL_CHANCE * research() + (1 - TECH_ROLL_CHANCE) * blast() + forBias;
        }
    }

    record MecatolBlast(int mecatolUnits, int adjacentUnits, double loss) {}

    private static double ixthianArtifact(AgendaView view, String outcome) {
        return isFor(outcome) ? artifactParts(view).value() : 0;
    }

    static ArtifactParts artifactParts(AgendaView view) {
        Game game = view.game();
        Player voter = view.voter();
        ToDoubleFunction<Player> research = player -> ixthianResearchWorth(view, player);
        ToDoubleFunction<Player> blast = player -> -mecatolBlast(game, player).loss();
        double ownResearch = research.applyAsDouble(voter);
        double ownBlast = blast.applyAsDouble(voter);
        MecatolBlast units = mecatolBlast(game, voter);
        Optional<ResearchPolicy.FreeTech> ownTech = ixthianTech(view, voter);
        return new ArtifactParts(
                ownResearch,
                ownResearch - relativeToRivals(game, voter, research),
                ownBlast,
                ownBlast - relativeToRivals(game, voter, blast),
                units.mecatolUnits(),
                units.adjacentUnits(),
                -ownBlast < ARTIFACT_DEVASTATING ? ARTIFACT_FOR_BIAS : 0,
                ownTech.map(ResearchPolicy.FreeTech::alias).orElse("-"),
                ownTech.map(ResearchPolicy.FreeTech::points).orElse(0));
    }

    static double researchWorth(AgendaView view, Player player) {
        return view.readsHandOf(player)
                ? ResearchPolicy.freeResearchWorth(view.game(), player)
                : ResearchPolicy.publicResearchWorth(view.game(), player);
    }

    static double ixthianResearchWorth(AgendaView view, Player player) {
        return ixthianTech(view, player)
                .map(ExpertDirectiveValues::ixthianTechWorth)
                .orElse(0.0);
    }

    private static Optional<ResearchPolicy.FreeTech> ixthianTech(AgendaView view, Player player) {
        return ResearchPolicy.bestFreeTech(
                view.game(), player, view.readsHandOf(player), ExpertDirectiveValues::ixthianTechWorth);
    }

    static double ixthianTechWorth(ResearchPolicy.FreeTech tech) {
        return TWO_TECHS * tech.tempo() + ARTIFACT_POINT_SHARE * tech.pointValue();
    }

    static MecatolBlast mecatolBlast(Game game, Player player) {
        Tile mecatol = game.getMecatolTile();
        if (mecatol == null) return new MecatolBlast(0, 0, 0);
        List<Double> inMecatol = unitWorths(mecatol, player);
        List<Double> adjacent = Adjacency.withSystem(game, player, mecatol.getPosition()).stream()
                .map(game::getTileByPosition)
                .filter(tile -> tile != null && tile != mecatol)
                .flatMap(tile -> unitWorths(tile, player).stream().sorted().limit(ADJACENT_LOSSES))
                .toList();
        double worth = inMecatol.stream().mapToDouble(Double::doubleValue).sum()
                + adjacent.stream().mapToDouble(Double::doubleValue).sum();
        int lost = inMecatol.size() + adjacent.size();
        double control = controlsMecatol(mecatol, player) ? MECATOL_CONTROL_LOSS : 0;
        return new MecatolBlast(inMecatol.size(), adjacent.size(), worth + BLAST_UNIT_LOSS * lost + control);
    }

    private static List<Double> unitWorths(Tile tile, Player player) {
        List<Double> worths = new ArrayList<>();
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            for (UnitKey key : holder.getUnitKeysForPlayer(player)) {
                double worth = unitWorth(player, key);
                for (int i = 0; i < holder.getUnitCount(key); i++) worths.add(worth);
            }
        }
        return worths;
    }

    private static double unitWorth(Player player, UnitKey key) {
        if (key.unitType() == UnitType.Spacedock) return PlanetStake.SPACE_DOCK;
        if (key.unitType() == UnitType.Pds) return PlanetStake.PDS;
        UnitModel model = player.getUnitFromUnitKey(key);
        return model == null ? 0 : ValueScale.RESOURCE * model.getCost();
    }

    private static boolean controlsMecatol(Tile mecatol, Player player) {
        return mecatol.getPlanetUnitHolders().stream()
                .anyMatch(planet -> player.getPlanets().contains(planet.getName()));
    }

    private static double swordsToPlowshares(Game game, Player voter, String outcome) {
        if (isFor(outcome))
            return relativeToRivals(game, voter, player -> plowsharesFor(game, player))
                    + plowsharesForBias(game, voter);
        if (isAgainst(outcome)) return relativeToRivals(game, voter, player -> plowsharesAgainst(game, player));
        return 0;
    }

    private static double plowsharesForBias(Game game, Player voter) {
        double leader = ScoringStakes.contender(game, voter, voter) ? PLOWSHARES_LEADER_BONUS : 0;
        long spendObjectives = game.getRevealedPublicObjectives().keySet().stream()
                .filter(ObjectiveCatalog::isSpend)
                .count();
        return leader + PLOWSHARES_PER_SPEND_OBJECTIVE * spendObjectives;
    }

    private static double plowsharesFor(Game game, Player player) {
        int destroyed = player.getPlanets().stream()
                .mapToInt(planet -> (infantryOn(game, player, planet) + 1) / 2)
                .sum();
        return destroyed * (ValueScale.TRADE_GOOD - INFANTRY_BOARD_WORTH);
    }

    private static double plowsharesAgainst(Game game, Player player) {
        int reinforcements = Math.max(0, INFANTRY_SUPPLY - unitsOnBoard(game, player, UnitType.Infantry));
        int placed = Math.min(player.getPlanets().size(), reinforcements);
        return placed * INFANTRY_BOARD_WORTH;
    }

    private static int infantryOn(Game game, Player player, String planet) {
        return BoardView.count(game.getUnitHolderFromPlanet(planet), player, UnitType.Infantry);
    }

    private static double wormholeResearch(AgendaView view, String outcome, String ownVote) {
        Game game = view.game();
        Player voter = view.voter();
        if (isAgainst(outcome))
            return relativeToRivals(game, voter, player -> -againstVoterTokenLoss(game, voter, player, ownVote));
        if (!isFor(outcome)) return 0;
        return relativeToRivals(game, voter, player -> wormholeResearchGain(view, player));
    }

    private static double againstVoterTokenLoss(Game game, Player voter, Player player, String ownVote) {
        if (player == voter) return isAgainst(ownVote) ? CommandTokenValue.tactic(game, voter, ONE_TOKEN) : 0;
        return votedOn(game, player, AgendaValueMath::isAgainst) ? CardValueScale.HELD_TOKEN : 0;
    }

    private static double wormholeResearchGain(AgendaView view, Player player) {
        Game game = view.game();
        double research = hasShipsAtAnyWormhole(game, player) ? researchWorth(view, player) : 0;
        return research - ValueScale.RESOURCE * unitsLostAtAlphaOrBeta(game, player);
    }

    private static boolean hasShipsAtAnyWormhole(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> !tile.getWormholes(game).isEmpty())
                .anyMatch(tile ->
                        !BoardView.ships(tile.getSpaceUnitHolder(), player).isEmpty());
    }

    private static double unitsLostAtAlphaOrBeta(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> hasAlphaOrBeta(game, tile))
                .mapToDouble(tile -> unitCost(tile.getSpaceUnitHolder(), player))
                .sum();
    }

    private static boolean hasAlphaOrBeta(Game game, Tile tile) {
        List<Wormhole> wormholes = tile.getWormholes(game);
        return wormholes.contains(Wormhole.ALPHA) || wormholes.contains(Wormhole.BETA);
    }

    private static double clandestineOperations(Game game, Player voter, String outcome) {
        if (isFor(outcome)) return relativeToRivals(game, voter, player -> -sheetTokenLoss(game, player));
        if (isAgainst(outcome)) return relativeToRivals(game, voter, player -> -fleetTokenLoss(game, player));
        return 0;
    }

    static double sheetTokenLoss(Game game, Player player) {
        int tactic = player.getTacticalCC();
        int fleet = player.getFleetCC();
        int strategy = player.getStrategicCC();
        double loss = 0;
        for (int token = 0; token < CLANDESTINE_TOKENS; token++) {
            double fleetCost = fleet > 0 ? fleetRemovalCost(game, player, fleet) : Double.MAX_VALUE;
            double tacticCost = tactic > 0 ? poolRemovalCost(tactic, EMPTY_TACTIC_POOL) : Double.MAX_VALUE;
            double strategyCost = strategy > 0 ? poolRemovalCost(strategy, EMPTY_STRATEGY_POOL) : Double.MAX_VALUE;
            double cheapest = Math.min(fleetCost, Math.min(tacticCost, strategyCost));
            if (cheapest == Double.MAX_VALUE) break;
            loss += cheapest;
            if (cheapest == fleetCost) {
                fleet--;
            } else if (cheapest == tacticCost) {
                tactic--;
            } else {
                strategy--;
            }
        }
        return loss;
    }

    private static double poolRemovalCost(int pool, double emptyPoolCost) {
        return CardValueScale.HELD_TOKEN + (pool == 1 ? emptyPoolCost : 0);
    }

    static double fleetTokenLoss(Game game, Player player) {
        int fleet = player.getFleetCC();
        if (fleet <= 0) return 0;
        return fleetRemovalCost(game, player, fleet);
    }

    private static double fleetRemovalCost(Game game, Player player, int fleet) {
        double token = fleet > SPARE_FLEET_POOL ? CardValueScale.TOKEN_FLOOR : CardValueScale.HELD_TOKEN;
        return token + shipsOverLimitValue(game, player, fleet - 1) - shipsOverLimitValue(game, player, fleet);
    }

    static double shipsOverLimitValue(Game game, Player player, int fleetTokens) {
        if (FleetLimit.heroLiftsLimit(player)) return 0;
        int limit = fleetTokens + FleetLimit.bonus(player);
        return game.getTileMap().values().stream()
                .mapToDouble(tile -> cheapestExcessShips(player, tile, limit))
                .sum();
    }

    private static double cheapestExcessShips(Player player, Tile tile, int limit) {
        List<Double> costs = new ArrayList<>();
        for (Map.Entry<UnitType, Integer> entry :
                BoardView.ships(tile.getSpaceUnitHolder(), player).entrySet()) {
            if (entry.getKey() == UnitType.Fighter) continue;
            double cost = BoardView.model(player, entry.getKey())
                    .map(UnitModel::getCost)
                    .orElse(0f);
            for (int ship = 0; ship < entry.getValue(); ship++) costs.add(cost);
        }
        int excess = costs.size() - Math.max(0, limit);
        if (excess <= 0) return 0;
        return costs.stream()
                        .sorted()
                        .limit(excess)
                        .mapToDouble(Double::doubleValue)
                        .sum()
                * ValueScale.RESOURCE;
    }
}
