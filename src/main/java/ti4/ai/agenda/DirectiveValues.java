package ti4.ai.agenda;

import static ti4.ai.agenda.AgendaValueMath.controlsHomePlanet;
import static ti4.ai.agenda.AgendaValueMath.handValue;
import static ti4.ai.agenda.AgendaValueMath.isAgainst;
import static ti4.ai.agenda.AgendaValueMath.isFor;
import static ti4.ai.agenda.AgendaValueMath.livePlayers;
import static ti4.ai.agenda.AgendaValueMath.liveRivals;
import static ti4.ai.agenda.AgendaValueMath.ownedPlanets;
import static ti4.ai.agenda.AgendaValueMath.pointsTo;
import static ti4.ai.agenda.AgendaValueMath.relativeToRivals;
import static ti4.ai.agenda.AgendaValueMath.unitCost;
import static ti4.ai.agenda.AgendaValueMath.unitsOnBoard;

import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.ai.actioncards.ActionCardPrior;
import ti4.ai.actioncards.DrawnCardValue;
import ti4.ai.eval.Adjacency;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.GameHorizon;
import ti4.ai.eval.ValueScale;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.strategy.CardValueScale;
import ti4.ai.strategy.CommandTokenValue;
import ti4.ai.strategy.ResearchPolicy;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;
import ti4.model.WormholeModel.Wormhole;

@UtilityClass
class DirectiveValues {

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
    private static final double LOST_UNIT = 1.5;
    private static final int ADJACENT_LOSSES = 3;
    private static final int SEED_POINT = 1;
    private static final double INFANTRY_BOARD_WORTH = 0.8;
    private static final int INFANTRY_SUPPLY = 12;
    private static final int UNCONVENTIONAL_DRAW = 2;
    private static final String VOTE_SEPARATOR = ";";
    private static final int ONE_TOKEN = 1;
    private static final int CLANDESTINE_TOKENS = 2;
    private static final int SPARE_FLEET_POOL = 3;
    private static final double MECH_WORTH = 2.0;
    private static final double INFANTRY_WORTH = 0.4;

    static double armsReduction(Game game, Player voter, String outcome) {
        if (isFor(outcome)) return relativeToRivals(game, voter, player -> -excessFleetValue(game, player));
        if (isAgainst(outcome)) return relativeToRivals(game, voter, player -> -specialtyExhaustLoss(game, player));
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

    static double economicEquality(Game game, Player voter, String outcome) {
        if (isFor(outcome)) return relativeToRivals(game, voter, DirectiveValues::equalityGain);
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

    static double incentiveProgram(Game game, Player voter, String outcome) {
        if (isFor(outcome)) return newObjectiveRace(game, voter, STAGE_ONE_POINTS * STAGE_ONE_SCORE_CHANCE);
        if (isAgainst(outcome)) return newObjectiveRace(game, voter, STAGE_TWO_POINTS * STAGE_TWO_SCORE_CHANCE);
        return 0;
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

    static double ixthianArtifact(Game game, Player voter, String outcome) {
        if (!isFor(outcome)) return 0;
        double research =
                relativeToRivals(game, voter, player -> TWO_TECHS * ResearchPolicy.freeResearchWorth(game, player));
        double blast = relativeToRivals(game, voter, player -> -mecatolBlastLoss(game, player));
        return TECH_ROLL_CHANCE * research + (1 - TECH_ROLL_CHANCE) * blast;
    }

    private static double mecatolBlastLoss(Game game, Player player) {
        Tile mecatol = game.getMecatolTile();
        if (mecatol == null) return 0;
        int adjacent = Adjacency.withSystem(game, player, mecatol.getPosition()).stream()
                .map(game::getTileByPosition)
                .filter(tile -> tile != null && tile != mecatol)
                .mapToInt(tile -> Math.min(ADJACENT_LOSSES, unitsIn(tile, player)))
                .sum();
        return LOST_UNIT * (unitsIn(mecatol, player) + adjacent);
    }

    private static int unitsIn(Tile tile, Player player) {
        return tile.getUnitHolders().values().stream()
                .mapToInt(holder -> holder.getUnitCount(player.getColorID()))
                .sum();
    }

    static double mutiny(Game game, Player voter, String outcome, String ownVote) {
        int direction = mutinyDirection(outcome);
        if (direction == 0) return 0;
        double own = isFor(ownVote) ? ScoringStakes.scoreValue(game, voter, pointChange(voter, direction)) : 0;
        double rivals = forVotingRivals(game, voter).stream()
                .mapToDouble(rival -> ScoringStakes.rivalStake(game, rival, pointChange(rival, direction)))
                .sum();
        return own - rivals;
    }

    private static int mutinyDirection(String outcome) {
        if (isFor(outcome)) return 1;
        if (isAgainst(outcome)) return -1;
        return 0;
    }

    private static int pointChange(Player player, int direction) {
        if (direction < 0 && player.getTotalVictoryPoints() <= 0) return 0;
        return direction;
    }

    private static List<Player> forVotingRivals(Game game, Player voter) {
        return AgendaHelper.getWinningVoters(AgendaValueMath.FOR, game).stream()
                .filter(player -> player != voter)
                .distinct()
                .toList();
    }

    static double seedOfAnEmpire(Game game, Player voter, String outcome) {
        if (isFor(outcome)) return pointToEach(game, voter, seedRecipients(game, true));
        if (isAgainst(outcome)) return pointToEach(game, voter, seedRecipients(game, false));
        return 0;
    }

    static List<Player> seedRecipients(Game game, boolean most) {
        List<Player> players = livePlayers(game);
        int target = most
                ? players.stream().mapToInt(Player::getTotalVictoryPoints).max().orElse(0)
                : players.stream().mapToInt(Player::getTotalVictoryPoints).min().orElse(0);
        return players.stream()
                .filter(player -> player.getTotalVictoryPoints() == target)
                .toList();
    }

    private static double pointToEach(Game game, Player voter, List<Player> recipients) {
        if (recipients.size() == 1) return pointsTo(game, voter, recipients.getFirst(), SEED_POINT);
        return recipients.stream()
                .mapToDouble(player -> player == voter
                        ? ScoringStakes.scoreValue(game, voter, SEED_POINT)
                        : -ScoringStakes.rivalStake(game, player, SEED_POINT))
                .sum();
    }

    static double swordsToPlowshares(Game game, Player voter, String outcome) {
        if (isFor(outcome)) return relativeToRivals(game, voter, player -> plowsharesFor(game, player));
        if (isAgainst(outcome)) return relativeToRivals(game, voter, player -> plowsharesAgainst(game, player));
        return 0;
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

    static double unconventionalMeasures(Game game, Player voter, String outcome, String ownVote) {
        if (!isFor(outcome) && !isAgainst(outcome)) return 0;
        boolean forWins = isFor(outcome);
        return relativeToRivals(game, voter, player -> unconventionalEffect(game, voter, player, forWins, ownVote));
    }

    private static double unconventionalEffect(
            Game game, Player voter, Player player, boolean forWins, String ownVote) {
        if (!votedFor(game, voter, player, ownVote)) return 0;
        if (!forWins) return -handValue(game, player, voter);
        if (player == voter) return DrawnCardValue.of(game, voter, UNCONVENTIONAL_DRAW);
        return UNCONVENTIONAL_DRAW * ActionCardPrior.AVERAGE_CARD;
    }

    private static boolean votedFor(Game game, Player voter, Player player, String ownVote) {
        if (player == voter) return isFor(ownVote);
        return game.getCurrentAgendaVotes().entrySet().stream()
                .filter(entry -> isFor(entry.getKey()))
                .flatMap(entry -> List.of(entry.getValue().split(VOTE_SEPARATOR)).stream())
                .anyMatch(piece -> isVoteBy(game, player, piece));
    }

    private static boolean isVoteBy(Game game, Player player, String piece) {
        int split = piece.indexOf('_');
        if (split < 0) return false;
        if (!NumberUtils.isDigits(piece.substring(split + 1))) return false;
        return game.getPlayerFromColorOrFaction(piece.substring(0, split).toLowerCase()) == player;
    }

    static double wormholeResearch(Game game, Player voter, String outcome, String ownVote) {
        if (isAgainst(outcome)) {
            return isAgainst(ownVote) ? -CommandTokenValue.tactic(game, voter, ONE_TOKEN) : 0;
        }
        if (!isFor(outcome)) return 0;
        return relativeToRivals(game, voter, player -> wormholeResearchGain(game, player));
    }

    private static double wormholeResearchGain(Game game, Player player) {
        double research = hasShipsAtAnyWormhole(game, player) ? ResearchPolicy.freeResearchWorth(game, player) : 0;
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

    static double clandestineOperations(Game game, Player voter, String outcome) {
        if (isFor(outcome)) return -relativeToRivals(game, voter, player -> sheetTokenLoss(player, CLANDESTINE_TOKENS));
        if (isAgainst(outcome)) return -relativeToRivals(game, voter, DirectiveValues::fleetTokenLoss);
        return 0;
    }

    private static double sheetTokenLoss(Player player, int tokens) {
        int onSheet = player.getTacticalCC() + player.getFleetCC() + player.getStrategicCC();
        int cheap = Math.min(tokens, Math.max(0, player.getFleetCC() - SPARE_FLEET_POOL));
        int costly = Math.max(0, Math.min(tokens, onSheet) - cheap);
        return cheap * CardValueScale.TOKEN_FLOOR + costly * CardValueScale.HELD_TOKEN;
    }

    private static double fleetTokenLoss(Player player) {
        int fleet = player.getFleetCC();
        if (fleet <= 0) return 0;
        return fleet > SPARE_FLEET_POOL ? CardValueScale.TOKEN_FLOOR : CardValueScale.HELD_TOKEN;
    }

    static double rearmamentAgreement(Game game, Player voter, String outcome) {
        if (isFor(outcome)) return relativeToRivals(game, voter, player -> controlsHomePlanet(player) ? MECH_WORTH : 0);
        if (isAgainst(outcome)) return relativeToRivals(game, voter, player -> -mechDowngrades(game, player));
        return 0;
    }

    private static double mechDowngrades(Game game, Player player) {
        return unitsOnBoard(game, player, UnitType.Mech) * (MECH_WORTH - INFANTRY_WORTH);
    }
}
