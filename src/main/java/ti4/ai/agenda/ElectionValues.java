package ti4.ai.agenda;

import static ti4.ai.agenda.AgendaValueMath.electedGain;
import static ti4.ai.agenda.AgendaValueMath.electedPlayer;
import static ti4.ai.agenda.AgendaValueMath.handValue;
import static ti4.ai.agenda.AgendaValueMath.lawRounds;
import static ti4.ai.agenda.AgendaValueMath.pointsTo;
import static ti4.ai.agenda.AgendaValueMath.rivalGainCost;
import static ti4.ai.agenda.AgendaValueMath.unitsOnBoard;

import java.util.Optional;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.ActionCardPrior;
import ti4.ai.actioncards.DrawnCardValue;
import ti4.ai.eval.GameHorizon;
import ti4.ai.eval.RelicValue;
import ti4.ai.eval.ValueScale;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.secrets.SecretDrawValue;
import ti4.ai.strategy.CardValueScale;
import ti4.ai.strategy.CommandTokenValue;
import ti4.ai.strategy.ResearchPolicy;
import ti4.ai.strategy.SpeakerTokenValue;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;

@UtilityClass
class ElectionValues {

    private static final double RIVAL_SECRET_DRAW_POINTS = 0.4;
    private static final double RIVAL_SECRET_SWAP_POINTS = 0.1;
    private static final double ELECT_PLAYER_SHARE = 0.35;
    private static final double STEERED_ELECTION_TG = 6.0;
    private static final int AGENDAS_PER_ROUND = 2;
    private static final double ARBITER_SWAP = 3.0;
    private static final double REPLENISHES_PER_ROUND = 0.8;
    private static final int EARLY_EXPANSION_LAST_ROUND = 3;
    private static final double EARLY_PLANETS_PER_ROUND = 1.5;
    private static final double LATE_PLANETS_PER_ROUND = 1.0;
    private static final double INDUSTRY_PER_ROUND = 0.7;
    private static final double PEACE_VALUE = 6.0;
    private static final double SCIENCES_RESOURCES_PER_ROUND = 3.0;
    private static final double WAR_VALUE = 6.0;
    private static final double PROPHECY_BASE = 0.5;
    private static final double PROPHECY_PER_FIGHTER = 0.3;
    private static final double PROPHECY_ROUND_CAP = 3.0;
    private static final int PROPHECY_HELD_ROUNDS = 2;
    private static final double EXECUTION_SILENCED = 1.0;
    private static final int POINT = 1;
    private static final double SHARD_KEEP = 0.8;
    private static final double CROWN_KEEP = 0.85;
    private static final double THALNOS_PER_ROUND = 1.0;
    private static final int STANDARD_TACTIC = 3;
    private static final int STANDARD_FLEET = 3;
    private static final int STANDARD_STRATEGY = 2;
    private static final double COVERT_ELECTION_GAIN = 2;
    private static final double HAND_LOCKOUT_SHARE = 0.75;
    private static final double ACTION_CARDS_PER_ROUND = 1.0;
    private static final double SECOND_SECRET_SHARE = 0.5;
    private static final double REVEALED_SECRETS_COST_PER_ROUND = 0.5;

    static double archivedSecret(Game game, Player voter, String outcome) {
        Player elected = electedPlayer(game, outcome);
        if (elected == null || hasScoredAllSecrets(elected)) return 0;
        if (elected == voter) return SecretDrawValue.of(game, voter);
        double points = SecretDrawValue.hasRoom(elected) ? RIVAL_SECRET_DRAW_POINTS : RIVAL_SECRET_SWAP_POINTS;
        return -ScoringStakes.rivalPointStake(game, elected, 1) * points;
    }

    private static boolean hasScoredAllSecrets(Player player) {
        return player.getSoScored() >= player.getMaxSOCount();
    }

    static double committeeFormation(Game game, Player voter, String outcome) {
        double worth = committeeWorth(game);
        return electedGain(game, voter, outcome, holder -> worth);
    }

    private static double committeeWorth(Game game) {
        int agendasLeft = AGENDAS_PER_ROUND * (lawRounds(game) - 1) + 1;
        double chance = 1 - Math.pow(1 - ELECT_PLAYER_SHARE, agendasLeft);
        return chance * STEERED_ELECTION_TG;
    }

    static double imperialArbiter(Game game, Player voter, String outcome) {
        return electedGain(game, voter, outcome, holder -> ARBITER_SWAP);
    }

    static double ministerOfCommerce(Game game, Player voter, String outcome) {
        return electedGain(
                game,
                voter,
                outcome,
                holder -> holder.getNeighbourCount() * REPLENISHES_PER_ROUND * ValueScale.TRADE_GOOD * lawRounds(game));
    }

    static double ministerOfExploration(Game game, Player voter, String outcome) {
        double worth = planetsPerRound(game) * lawRounds(game) * ValueScale.TRADE_GOOD;
        return electedGain(game, voter, outcome, holder -> worth);
    }

    private static double planetsPerRound(Game game) {
        return game.getRound() <= EARLY_EXPANSION_LAST_ROUND ? EARLY_PLANETS_PER_ROUND : LATE_PLANETS_PER_ROUND;
    }

    static double ministerOfIndustry(Game game, Player voter, String outcome) {
        return electedGain(game, voter, outcome, holder -> INDUSTRY_PER_ROUND * lawRounds(game));
    }

    static double ministerOfPeace(Game game, Player voter, String outcome) {
        return electedGain(game, voter, outcome, holder -> PEACE_VALUE);
    }

    static double ministerOfPolicy(Game game, Player voter, String outcome) {
        return electedGain(game, voter, outcome, holder -> DrawnCardValue.of(game, holder, lawRounds(game)));
    }

    static double ministerOfSciences(Game game, Player voter, String outcome) {
        double worth = SCIENCES_RESOURCES_PER_ROUND * ValueScale.RESOURCE * lawRounds(game);
        return electedGain(game, voter, outcome, holder -> worth);
    }

    static double ministerOfWar(Game game, Player voter, String outcome) {
        return electedGain(game, voter, outcome, holder -> WAR_VALUE);
    }

    static double ministerOfAntiquities(Game game, Player voter, String outcome) {
        return electedGain(game, voter, outcome, holder -> RelicValue.draw(game, holder));
    }

    static double prophecyOfIxth(Game game, Player voter, String outcome) {
        return electedGain(game, voter, outcome, holder -> prophecyWorth(game, holder));
    }

    private static double prophecyWorth(Game game, Player holder) {
        double perRound = Math.min(
                PROPHECY_ROUND_CAP,
                PROPHECY_BASE + PROPHECY_PER_FIGHTER * unitsOnBoard(game, holder, UnitType.Fighter));
        return perRound * Math.min(GameHorizon.roundsLeft(game), PROPHECY_HELD_ROUNDS);
    }

    static double publicExecution(Game game, Player voter, String outcome) {
        Player elected = electedPlayer(game, outcome);
        if (elected == null) return 0;
        Player heir = speakerHeir(game, elected);
        double loss = executionLoss(game, voter, elected, heir);
        if (elected == voter) return -loss;
        double inheritance = heir == voter ? SpeakerTokenValue.of(game, voter) : 0;
        return rivalGainCost(game, voter, elected, loss) + inheritance;
    }

    private static double executionLoss(Game game, Player voter, Player elected, @Nullable Player heir) {
        double speaker = heir == null ? 0 : SpeakerTokenValue.of(game, heir);
        double silenced = AgendaRound.isFinalAgenda(game) ? 0 : EXECUTION_SILENCED;
        return handValue(game, elected, voter) + speaker + silenced;
    }

    @Nullable
    private static Player speakerHeir(Game game, Player elected) {
        if (!elected.isSpeaker()) return null;
        return Helper.getSpeakerOrFullPriorityOrderFromPlayer(elected, game).stream()
                .filter(player -> player != elected)
                .findFirst()
                .orElse(null);
    }

    static double shardOfTheThrone(Game game, Player voter, String outcome) {
        return pointLaw(game, voter, outcome, holder -> SHARD_KEEP);
    }

    static double crownOfEmphidia(Game game, Player voter, String outcome) {
        return pointLaw(game, voter, outcome, ElectionValues::crownKeep);
    }

    private static double crownKeep(Player holder) {
        return AgendaValueMath.controlsHomePlanet(holder) ? CROWN_KEEP : 1;
    }

    private static double pointLaw(Game game, Player voter, String outcome, ToDoubleFunction<Player> keepChance) {
        Player elected = electedPlayer(game, outcome);
        if (elected == null) return 0;
        double keep = winsWithPoint(game, elected) ? 1 : keepChance.applyAsDouble(elected);
        return pointsTo(game, voter, elected, POINT) * keep;
    }

    private static boolean winsWithPoint(Game game, Player player) {
        return player.getTotalVictoryPoints() + POINT >= GameHorizon.goal(game);
    }

    static double crownOfThalnos(Game game, Player voter, String outcome) {
        double worth = THALNOS_PER_ROUND * lawRounds(game);
        return electedGain(game, voter, outcome, holder -> worth);
    }

    static double armedForcesStandardization(Game game, Player voter, String outcome) {
        return electedGain(game, voter, outcome, ElectionValues::standardizedTokens);
    }

    private static double standardizedTokens(Player player) {
        return (STANDARD_TACTIC - player.getTacticalCC()) * CardValueScale.HELD_TOKEN
                + standardizedFleet(player.getFleetCC())
                + (STANDARD_STRATEGY - player.getStrategicCC()) * CardValueScale.HELD_TOKEN;
    }

    private static double standardizedFleet(int fleet) {
        if (fleet < STANDARD_FLEET) return (STANDARD_FLEET - fleet) * CardValueScale.HELD_TOKEN;
        return (STANDARD_FLEET - fleet) * CardValueScale.TOKEN_FLOOR;
    }

    static double covertLegislation(Game game, Player voter, String outcome) {
        return electedGain(game, voter, outcome, holder -> COVERT_ELECTION_GAIN);
    }

    static double politicalCensure(Game game, Player voter, String outcome) {
        Player elected = electedPlayer(game, outcome);
        if (elected == null) return 0;
        if (elected == voter) return censuredSelf(game, voter);
        return censuredRival(game, voter, elected);
    }

    private static double censuredSelf(Game game, Player voter) {
        double point = ScoringStakes.scoreValue(game, voter, POINT);
        if (point >= ScoringStakes.WIN_VALUE) return point;
        return point - actionCardLockout(game, voter);
    }

    private static double censuredRival(Game game, Player voter, Player rival) {
        double stake = ScoringStakes.rivalPointStake(game, rival, POINT);
        if (stake >= ScoringStakes.WIN_VALUE) return -stake;
        return -stake + rivalGainCost(game, voter, rival, actionCardLockout(game, rival));
    }

    private static double actionCardLockout(Game game, Player player) {
        double hand = player.getAcCount() * ActionCardPrior.AVERAGE_CARD * HAND_LOCKOUT_SHARE;
        double future = lawRounds(game) * ACTION_CARDS_PER_ROUND * ActionCardPrior.AVERAGE_CARD;
        return hand + future;
    }

    static double researchGrantReallocation(Game game, Player voter, String outcome) {
        return electedGain(game, voter, outcome, holder -> grantWorth(game, holder));
    }

    private static double grantWorth(Game game, Player player) {
        Optional<String> tech = ResearchPolicy.bestResearchable(game, player);
        if (tech.isEmpty()) return 0;
        int lost = Math.min(player.getFleetCC(), prerequisites(tech.get()));
        double tokenLoss = lost > 0 ? CommandTokenValue.tactic(game, player, lost) : 0;
        return Math.max(0, ResearchPolicy.freeResearchWorth(game, player) - tokenLoss);
    }

    private static int prerequisites(String alias) {
        TechnologyModel tech = Mapper.getTech(alias);
        return tech == null ? 0 : tech.getRequirements().orElse("").length();
    }

    static double searchWarrant(Game game, Player voter, String outcome) {
        return electedGain(game, voter, outcome, holder -> warrantWorth(game, holder));
    }

    private static double warrantWorth(Game game, Player player) {
        double draws = SecretDrawValue.of(game, player) * (1 + SECOND_SECRET_SHARE);
        return draws - REVEALED_SECRETS_COST_PER_ROUND * lawRounds(game);
    }
}
