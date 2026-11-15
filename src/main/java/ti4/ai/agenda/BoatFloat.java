package ti4.ai.agenda;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiGameWrites;
import ti4.ai.AiSeats;
import ti4.ai.AiSettings;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class BoatFloat {

    static final String ARTICLES = "articles_war";
    static final int FLOAT_PRICE = 1;
    static final int FLOAT_VOTES = 1;
    static final double MAX_LAW_GAIN = 2.0;
    static final String KEY = "aiBoatFloat";
    private static final String SAVE_REASON = "AI boat float";
    private static final String FIELD = "~";
    private static final String MEMBER = "+";
    private static final String NONE = "none";
    private static final String AGAINST = AgendaValueMath.AGAINST;
    private static final String FOR = AgendaValueMath.FOR;
    private static final long PROMISE_MILLIS = 6 * 60 * 60_000L;

    public record Plan(String speaker, String against, List<String> members) {

        boolean member(Player seat) {
            return members.contains(seat.getFaction());
        }
    }

    public static Optional<AiDecision> arrange(AiTurnContext context) {
        Game game = context.game();
        Player speaker = context.seat();
        if (!speaker.getUserID().equals(game.getSpeakerUserID())
                || !ARTICLES.equals(AgendaPolicy.currentAgenda(game))) {
            return Optional.empty();
        }
        String agendaKey = PromisedVotes.agendaKey(game);
        if (agendaKey.isBlank() || recorded(game, agendaKey).isPresent()) return Optional.empty();
        Optional<Plan> plan = propose(game, speaker);
        String record = agendaKey + FIELD + plan.map(BoatFloat::encode).orElse(NONE);
        long now = context.now();
        AiGameWrites.persist(game, SAVE_REASON, latest -> {
            latest.setStoredValue(KEY, record);
            plan.ifPresent(agreed -> promise(latest, agreed, agendaKey, now));
        });
        return Optional.empty();
    }

    public static Optional<Plan> active(Game game) {
        if (!ARTICLES.equals(AgendaPolicy.currentAgenda(game))) return Optional.empty();
        String agendaKey = PromisedVotes.agendaKey(game);
        return recorded(game, agendaKey).flatMap(plan -> plan);
    }

    static Optional<String> memberOutcome(Game game, Player seat) {
        return active(game).filter(plan -> plan.member(seat)).map(plan -> FOR);
    }

    static boolean speakerHonoursAgainst(Game game, Player speaker) {
        return active(game)
                .filter(plan -> plan.speaker().equals(speaker.getFaction()))
                .isPresent();
    }

    static Optional<Plan> propose(Game game, Player speaker) {
        if (!speaker.getUserID().equals(game.getSpeakerUserID())) return Optional.empty();
        if (!AiSettings.isTradingEnabled() || !AiExperiments.enabled(speaker, Experiment.BOAT_FLOAT)) {
            return Optional.empty();
        }
        List<Player> voters = AgendaForecast.votingOrder(game).stream()
                .filter(voter -> !AgendaVoting.cannotVote(game, voter))
                .filter(voter -> VoteCapacity.of(game, voter) > 0)
                .toList();
        if (voters.size() < 2 || !AiSeats.isActiveAiSeat(speaker)) return Optional.empty();
        if (!voters.stream().allMatch(AiSeats::isActiveAiSeat)) return Optional.empty();
        if (voters.stream().anyMatch(voter -> lawGain(game, voter) > MAX_LAW_GAIN)) return Optional.empty();
        Player against = voters.stream()
                .max(Comparator.comparingInt((Player voter) -> AgendaVoting.castableVotes(game, voter))
                        .thenComparingInt(voters::indexOf))
                .orElseThrow();
        List<String> members = voters.stream()
                .filter(voter -> voter != against)
                .map(Player::getFaction)
                .toList();
        if (AgendaVoting.castableVotes(game, against) < members.size() * FLOAT_VOTES) return Optional.empty();
        return Optional.of(new Plan(speaker.getFaction(), against.getFaction(), members));
    }

    static double lawGain(Game game, Player voter) {
        return ExpertMechAgendaValues.articlesOfWar(game, voter, FOR, FOR);
    }

    private static void promise(Game game, Plan plan, String agendaKey, long now) {
        Player against = game.getPlayerFromColorOrFaction(plan.against());
        if (against == null) return;
        for (String faction : plan.members()) {
            Player member = game.getPlayerFromColorOrFaction(faction);
            if (member == null) continue;
            Promise promise = Promise.offer(
                            game,
                            Promise.Term.VOTE_FOR,
                            against,
                            member,
                            against,
                            agendaKey,
                            AGAINST,
                            plan.members().size() * FLOAT_VOTES,
                            FLOAT_PRICE,
                            now,
                            now + PROMISE_MILLIS)
                    .accepted(game);
            PromiseBook.save(game, withId(promise, faction));
        }
    }

    private static Promise withId(Promise promise, String member) {
        return new Promise(
                promise.id() + member,
                promise.term(),
                promise.promiser(),
                promise.promisee(),
                promise.proposer(),
                promise.round(),
                promise.agenda(),
                promise.target(),
                promise.votes(),
                promise.price(),
                promise.status(),
                promise.offeredAt(),
                promise.expiresAt(),
                promise.guarded(),
                promise.held());
    }

    private static Optional<Optional<Plan>> recorded(Game game, String agendaKey) {
        String stored = game.getStoredValue(KEY);
        if (!stored.startsWith(agendaKey + FIELD)) return Optional.empty();
        String body = stored.substring(agendaKey.length() + FIELD.length());
        return Optional.of(decode(body));
    }

    private static String encode(Plan plan) {
        return plan.speaker() + FIELD + plan.against() + FIELD + String.join(MEMBER, plan.members());
    }

    private static Optional<Plan> decode(String body) {
        if (NONE.equals(body)) return Optional.empty();
        String[] fields = StringUtils.splitPreserveAllTokens(body, FIELD);
        if (fields == null || fields.length != 3 || fields[0].isBlank() || fields[1].isBlank()) return Optional.empty();
        List<String> members =
                Arrays.stream(StringUtils.split(fields[2], MEMBER)).toList();
        return Optional.of(new Plan(fields[0], fields[1], members));
    }
}
