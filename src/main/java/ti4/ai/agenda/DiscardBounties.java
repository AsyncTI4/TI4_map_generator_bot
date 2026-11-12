package ti4.ai.agenda;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiGameWrites;
import ti4.ai.AiSeats;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class DiscardBounties {

    public static final String VETO = "Veto";
    public static final String QUASH = "Quash";
    public static final int VETO_CAP = 3;
    public static final int QUASH_CAP = 5;
    public static final double MIN_LOSS = 2.0;
    static final String KEY = "aiDiscardBounties";
    private static final long WAIT_MILLIS = 90_000L;
    private static final String RECORDS = ";";
    private static final String FIELD = "|";
    private static final int FIELDS = 9;
    private static final String SAVE_REASON = "AI discard bounty";

    public enum Status {
        OPEN,
        CLAIMED,
        LAPSED
    }

    public record Bounty(
            int round,
            String agenda,
            String payer,
            int veto,
            int quash,
            int plays,
            String quasher,
            int quashTokens,
            Status status) {

        public Bounty with(Status next) {
            return new Bounty(round, agenda, payer, veto, quash, plays, quasher, quashTokens, next);
        }

        public boolean postedBy(Player player) {
            return player != null && payer.equals(player.getFaction());
        }

        boolean storable() {
            return StringUtils.containsNone(agenda + payer + quasher, FIELD + RECORDS + ":,");
        }

        String encode() {
            return String.join(
                    FIELD,
                    String.valueOf(round),
                    agenda,
                    payer,
                    String.valueOf(veto),
                    String.valueOf(quash),
                    String.valueOf(plays),
                    quasher,
                    String.valueOf(quashTokens),
                    status.name());
        }

        static Optional<Bounty> decode(String encoded) {
            String[] fields = StringUtils.splitPreserveAllTokens(encoded, FIELD);
            if (fields == null || fields.length != FIELDS) return Optional.empty();
            try {
                return Optional.of(new Bounty(
                        Integer.parseInt(fields[0]),
                        fields[1],
                        fields[2],
                        Integer.parseInt(fields[3]),
                        Integer.parseInt(fields[4]),
                        Integer.parseInt(fields[5]),
                        fields[6],
                        Integer.parseInt(fields[7]),
                        Status.valueOf(fields[8])));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    public static boolean active(Player seat) {
        return AiExperiments.enabled(seat, Experiment.DISCARD_BOUNTIES);
    }

    public static List<Bounty> all(Game game) {
        List<Bounty> bounties = new ArrayList<>();
        for (String encoded : StringUtils.split(game.getStoredValue(KEY), RECORDS)) {
            Bounty.decode(encoded).ifPresent(bounties::add);
        }
        return bounties;
    }

    public static void save(Game game, Bounty bounty) {
        if (!bounty.storable()) return;
        List<Bounty> kept = new ArrayList<>(all(game).stream()
                .filter(other -> other.round() == game.getRound())
                .filter(other -> !sameOffer(other, bounty))
                .toList());
        kept.add(bounty);
        game.setStoredValue(KEY, kept.stream().map(Bounty::encode).collect(Collectors.joining(RECORDS)));
    }

    public static void persist(Game game, Bounty bounty) {
        AiGameWrites.persist(game, SAVE_REASON, latest -> save(latest, bounty));
    }

    private static boolean sameOffer(Bounty first, Bounty second) {
        return first.round() == second.round()
                && first.agenda().equals(second.agenda())
                && first.payer().equals(second.payer());
    }

    public static List<Bounty> openOnThisAgenda(Game game) {
        String agenda = PromisedVotes.agendaKey(game);
        if (agenda.isBlank()) return List.of();
        return all(game).stream()
                .filter(bounty -> bounty.round() == game.getRound())
                .filter(bounty -> bounty.status() == Status.OPEN)
                .filter(bounty -> agenda.equals(bounty.agenda()))
                .toList();
    }

    public static Optional<Bounty> postedOnThisAgenda(Game game, Player payer) {
        String agenda = PromisedVotes.agendaKey(game);
        return all(game).stream()
                .filter(bounty -> bounty.round() == game.getRound() && agenda.equals(bounty.agenda()))
                .filter(bounty -> bounty.postedBy(payer))
                .findFirst();
    }

    public static int vetoBounty(Game game, Player holder) {
        return pooled(game, holder, Bounty::veto, VETO_CAP);
    }

    public static int quashBounty(Game game, Player holder) {
        return pooled(game, holder, Bounty::quash, QUASH_CAP);
    }

    private static int pooled(Game game, Player holder, ToIntFunction<Bounty> amount, int cap) {
        if (!active(holder)) return 0;
        int total = openOnThisAgenda(game).stream()
                .filter(bounty -> !bounty.postedBy(holder))
                .mapToInt(amount)
                .sum();
        return Math.min(total, cap);
    }

    public static int pooledFromOthers(Game game, Player payer, ToIntFunction<Bounty> amount) {
        return openOnThisAgenda(game).stream()
                .filter(bounty -> !bounty.postedBy(payer))
                .mapToInt(amount)
                .sum();
    }

    public static Optional<AiDecision> awaitOffers(AiTurnContext context) {
        Game game = context.game();
        Player holder = context.seat();
        if (!active(holder) || !AgendaQueue.whenWindowOpen(game)) return Optional.empty();
        long until = AgendaClock.seenAt(context) + WAIT_MILLIS;
        if (context.now() >= until || !somePayerMayOffer(game, holder)) return Optional.empty();
        return Optional.of(new AiDecision.Wait(until, "wait for a bounty to discard the agenda"));
    }

    private static boolean somePayerMayOffer(Game game, Player holder) {
        AgendaForecast forecast = AgendaForecast.of(game, holder);
        if (forecast.isEmpty()) return false;
        return game.getRealPlayers().stream()
                .filter(payer -> payer != holder && AiSeats.isAiSeat(payer) && active(payer))
                .filter(payer -> payer.getTg() > 0)
                .filter(payer -> postedOnThisAgenda(game, payer).isEmpty())
                .anyMatch(payer -> forecast.expectedUtility(payer) <= -MIN_LOSS);
    }
}
