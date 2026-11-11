package ti4.ai.agenda;

import static ti4.ai.agenda.AgendaValueMath.electedGain;
import static ti4.ai.agenda.AgendaValueMath.electedPlayer;
import static ti4.ai.agenda.AgendaValueMath.endsSoon;
import static ti4.ai.agenda.AgendaValueMath.livePlayers;
import static ti4.ai.agenda.AgendaValueMath.pointsTo;
import static ti4.ai.agenda.AgendaValueMath.rivalGainCost;
import static ti4.ai.agenda.AgendaValueMath.roundTable;

import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.ActionCardPrior;
import ti4.ai.actioncards.ActionCardValue;
import ti4.ai.actioncards.ExpertTiers;
import ti4.ai.eval.DecidingSeat;
import ti4.ai.eval.GameHorizon;
import ti4.ai.secrets.SecretOdds;
import ti4.ai.secrets.SecretPhase;
import ti4.ai.secrets.SecretValue;
import ti4.ai.strategy.SpeakerTokenValue;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class ExpertElectionValues {

    static final Set<String> KELERES = Set.of("keleresa", "keleresm", "keleresx");
    static final String CREUSS = "ghost";
    static final String YSSARIL = "yssaril";
    static final String JOL_NAR = "jolnar";
    static final String RAL_NEL = "ralnel";

    private static final int POINT = 1;
    private static final double SECRET_DOABLE = 1;
    private static final double SECRET_NEEDED = 4;
    private static final double EASY_SECRET_KEEP = 0.6;
    private static final double LIKELY_READY = 0.5;
    private static final double ARBITER = 5;
    private static final double KELERES_COMMERCE_BONUS = 2;
    private static final double EXPLORATION = 1;
    private static final double INDUSTRY = 1;
    private static final double EARLY_INDUSTRY = 3;
    private static final int EARLY_INDUSTRY_BEFORE_ROUND = 4;
    private static final double PEACE = 5;
    private static final double WAR = 5;
    private static final double POLICY = 1;
    private static final double YSSARIL_POLICY_BONUS = 2;
    private static final double SCIENCES_START = 4;
    private static final double SCIENCES_FLOOR = 1;
    private static final int SCIENCES_FIRST_ROUND = 2;
    private static final double JOL_NAR_SCIENCES_BONUS = 2;
    private static final double ANTIQUITIES = 5;
    private static final double PROPHECY = 1;
    private static final double SHARD = 6;
    private static final double CROWN_OF_EMPHIDIA = 1;
    private static final double CROWN_OF_THALNOS = 0.25;
    private static final double CENSURE_GOOD_HAND = 3;
    private static final double CENSURE_WEAK_HAND = 5;
    private static final double GOOD_HAND_KEEP = 4;
    private static final double TOP_TIER_STRENGTH = 1.4;
    private static final double GRANT_REALLOCATION = 3;
    private static final int SECRET_CAP = 3;
    private static final int WARRANT_DRAWS = 2;
    private static final double WARRANT_PER_SECRET = 2;
    private static final double WARRANT_EASY_REVEALED = 1;
    private static final double WARRANT_ACTION_REVEALED = 1;
    private static final double EXECUTION_SILENCED = 1;
    private static final double EXECUTION_TARGET_BONUS = 1.5;
    private static final Set<String> EXECUTION_TARGETS = Set.of(YSSARIL, RAL_NEL);

    @Nullable
    static Double valueOf(AgendaView view, String agenda, String outcome, String ownVote) {
        return switch (agenda) {
            case "secret" -> elected(view, outcome, holder -> archivedSecretWorth(view, holder));
            case "committee" -> elected(view, outcome, holder -> roundTable(view.game(), 4, 2, 1));
            case "arbiter" -> elected(view, outcome, holder -> ARBITER);
            case "minister_commerce" -> elected(view, outcome, holder -> commerceWorth(view.game(), holder));
            case "minister_exploration" -> elected(view, outcome, holder -> EXPLORATION);
            case "minister_industry" -> elected(view, outcome, holder -> industryWorth(view.game(), holder));
            case "minister_peace" -> elected(view, outcome, holder -> PEACE);
            case "minister_war" -> elected(view, outcome, holder -> WAR);
            case "minister_policy" -> elected(view, outcome, holder -> policyWorth(view.game(), holder));
            case "minister_sciences" -> elected(view, outcome, holder -> sciencesWorth(view.game(), holder));
            case "minister_antiquities" -> elected(view, outcome, holder -> ANTIQUITIES);
            case "prophecy" -> elected(view, outcome, holder -> PROPHECY);
            case "shard_of_the_throne" -> pointElection(view, outcome, holder -> SHARD);
            case "crown_of_emphidia" -> pointElection(view, outcome, holder -> CROWN_OF_EMPHIDIA);
            case "crown_of_thalnos" -> elected(view, outcome, holder -> CROWN_OF_THALNOS);
            case "censure" -> pointElection(view, outcome, holder -> censureWorth(view, holder));
            case "grant_reallocation" -> elected(view, outcome, holder -> GRANT_REALLOCATION);
            case "warrant" -> elected(view, outcome, holder -> searchWarrantWorth(view, holder));
            case "execution" -> publicExecution(view, outcome);
            default -> null;
        };
    }

    private static double elected(AgendaView view, String outcome, ToDoubleFunction<Player> worthToHolder) {
        return electedGain(view.game(), view.voter(), outcome, worthToHolder);
    }

    private static double pointElection(AgendaView view, String outcome, ToDoubleFunction<Player> worthToHolder) {
        Player elected = electedPlayer(view.game(), outcome);
        if (elected == null) return 0;
        if (winsWithPoint(view.game(), elected)) return pointsTo(view.game(), view.voter(), elected, POINT);
        return elected(view, outcome, worthToHolder);
    }

    private static boolean winsWithPoint(Game game, Player player) {
        return player.getTotalVictoryPoints() + POINT >= GameHorizon.goal(game);
    }

    private static boolean isFaction(Player player, String faction) {
        return faction.equals(player.getFaction());
    }

    private static boolean isKeleres(Player player) {
        return KELERES.contains(player.getFaction());
    }

    private static double commerceWorth(Game game, Player holder) {
        double worth = roundTable(game, 3, 2, 1);
        return isKeleres(holder) ? worth + KELERES_COMMERCE_BONUS : worth;
    }

    private static double industryWorth(Game game, Player holder) {
        boolean early = game.getRound() < EARLY_INDUSTRY_BEFORE_ROUND;
        boolean likesIt = isKeleres(holder) || isFaction(holder, CREUSS);
        return early && likesIt ? EARLY_INDUSTRY : INDUSTRY;
    }

    private static double policyWorth(Game game, Player holder) {
        if (endsSoon(game)) return 0;
        return isFaction(holder, YSSARIL) ? POLICY + YSSARIL_POLICY_BONUS : POLICY;
    }

    private static double sciencesWorth(Game game, Player holder) {
        int roundsPast = Math.max(game.getRound(), SCIENCES_FIRST_ROUND) - SCIENCES_FIRST_ROUND;
        double worth = Math.max(SCIENCES_FLOOR, SCIENCES_START - roundsPast);
        return isFaction(holder, JOL_NAR) ? worth + JOL_NAR_SCIENCES_BONUS : worth;
    }

    private static double archivedSecretWorth(AgendaView view, Player holder) {
        if (holder.getSoScored() >= holder.getMaxSOCount()) return SECRET_DOABLE;
        if (view.readsHandOf(holder)) {
            return easySecrets(view.game(), holder).isEmpty() ? SECRET_NEEDED : SECRET_DOABLE;
        }
        return publicChanceReady(view, holder) >= LIKELY_READY ? SECRET_DOABLE : SECRET_NEEDED;
    }

    private static List<String> easySecrets(Game game, Player holder) {
        return holder.getSecretsUnscored().keySet().stream()
                .filter(secret -> SecretValue.keepValue(game, holder, secret) >= EASY_SECRET_KEEP)
                .toList();
    }

    private static double publicChanceReady(AgendaView view, Player holder) {
        Player viewer = DecidingSeat.orSelf(view.game(), view.voter());
        if (viewer == holder) return 1 - Math.pow(1 - SecretOdds.BASE_RATE, holder.getSo());
        return SecretOdds.chanceReady(view.game(), viewer, holder);
    }

    private static double searchWarrantWorth(AgendaView view, Player holder) {
        if (view.readsHandOf(holder)) return ownSearchWarrantWorth(view.game(), holder);
        int held = holder.getSo();
        double chanceReady = held == 0 ? 0 : publicChanceReady(view, holder);
        double easyShare = held == 0 ? 0 : 1 - Math.pow(1 - chanceReady, 1.0 / held);
        double gained = secretsGained(holder.getSoScored() + easyShare * held);
        return WARRANT_PER_SECRET * gained - WARRANT_EASY_REVEALED * chanceReady;
    }

    private static double ownSearchWarrantWorth(Game game, Player holder) {
        List<String> easy = easySecrets(game, holder);
        double worth = WARRANT_PER_SECRET * secretsGained(holder.getSoScored() + easy.size());
        if (easy.isEmpty()) return worth;
        boolean revealsActionSecret = easy.stream().anyMatch(secret -> SecretPhase.of(secret) == SecretPhase.ACTION);
        return worth - WARRANT_EASY_REVEALED - (revealsActionSecret ? WARRANT_ACTION_REVEALED : 0);
    }

    private static double secretsGained(double kept) {
        return Math.clamp(SECRET_CAP - kept, 0, WARRANT_DRAWS);
    }

    private static double censureWorth(AgendaView view, Player holder) {
        return hasGoodHand(view, holder) ? CENSURE_GOOD_HAND : CENSURE_WEAK_HAND;
    }

    private static boolean hasGoodHand(AgendaView view, Player holder) {
        return handWorth(view, holder) >= GOOD_HAND_KEEP || holdsTopTierCard(view, holder);
    }

    private static boolean holdsTopTierCard(AgendaView view, Player holder) {
        if (!view.readsHandOf(holder)) return false;
        return holder.getActionCards().keySet().stream()
                .anyMatch(alias -> ExpertTiers.strengthOfName(ActionCardPrior.nameOf(alias))
                        .map(strength -> strength >= TOP_TIER_STRENGTH)
                        .orElse(false));
    }

    private static double handWorth(AgendaView view, Player holder) {
        if (!view.readsHandOf(holder)) return holder.getAcCount() * ActionCardPrior.AVERAGE_CARD;
        return holder.getActionCards().keySet().stream()
                .mapToDouble(alias -> ActionCardValue.keepValue(view.game(), holder, alias))
                .sum();
    }

    private static double publicExecution(AgendaView view, String outcome) {
        Game game = view.game();
        Player voter = view.voter();
        Player elected = electedPlayer(game, outcome);
        if (elected == null) return 0;
        double loss = executionLoss(view, elected);
        if (elected == voter) return -loss;
        return rivalGainCost(game, voter, elected, loss) + speakerOrderShift(game, voter, elected);
    }

    private static double executionLoss(AgendaView view, Player elected) {
        Game game = view.game();
        double speaker = elected.isSpeaker() ? SpeakerTokenValue.of(game, elected) : 0;
        double silenced = AgendaRound.isFinalAgenda(game) ? 0 : EXECUTION_SILENCED;
        double target = EXECUTION_TARGETS.contains(elected.getFaction()) ? EXECUTION_TARGET_BONUS : 0;
        return handWorth(view, elected) + speaker + silenced + target;
    }

    private static double speakerOrderShift(Game game, Player voter, Player elected) {
        if (!elected.isSpeaker()) return 0;
        int laterSeats = livePlayers(game).size() - 1;
        if (laterSeats <= 0) return 0;
        return SpeakerTokenValue.of(game, voter) / laterSeats;
    }
}
