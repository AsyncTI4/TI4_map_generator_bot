package ti4.ai.trade;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.AiSeats;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiMemory;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPerception;
import ti4.ai.perception.AiPrompt;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.Mapper;
import ti4.model.PromissoryNoteModel;

@UtilityClass
class OfferRejections {

    static final String NOTICE = AiPerception.OFFER_REJECTED;
    static final String FRAGMENTS_ASKED = "fragments";
    private static final String SEEN_KEY = "offerRejectionsSeenUpTo";
    private static final String REJECTED_ROUND_KEY = "offerRejectedRound|";
    private static final String REJECTED_AT_KEY = "offerRejectedAt|";
    private static final String QUESTION_KEY = "wantQuestionDue|";
    private static final String ASKED_KEY = "wantAskedRound|";
    private static final String DROPPED_KEY = "noteDealDroppedRound|";
    private static final String NOTE_ASK_KEY = "lastNoteAsk|";

    static void observe(AiTurnContext context) {
        AiMemory memory = context.memory();
        long seenUpTo = memory.get(SEEN_KEY).map(Long::parseLong).orElse(Long.MIN_VALUE);
        long newest = seenUpTo;
        for (AiPrompt prompt : context.prompts()) {
            if (!isNotice(prompt) || prompt.createdAtMillis() <= seenUpTo) continue;
            newest = Math.max(newest, prompt.createdAtMillis());
            rejector(context, prompt).ifPresent(partner -> rejected(context, partner, prompt.createdAtMillis()));
        }
        if (newest > seenUpTo) memory.put(SEEN_KEY, String.valueOf(newest));
    }

    static boolean isNotice(AiPrompt prompt) {
        return AiPerception.isRejectionNotice(prompt);
    }

    private static Optional<Player> rejector(AiTurnContext context, AiPrompt prompt) {
        return Standings.others(context.game(), context.seat()).stream()
                .filter(partner -> prompt.content().contains(partner.getRepresentation(false, false)))
                .findFirst();
    }

    static void rejected(AiTurnContext context, Player partner, long at) {
        AiMemory memory = context.memory();
        memory.put(
                REJECTED_ROUND_KEY + partner.getFaction(),
                String.valueOf(context.game().getRound()));
        memory.put(REJECTED_AT_KEY + partner.getFaction(), String.valueOf(at));
        Optional<String> note = memory.get(NOTE_ASK_KEY + partner.getFaction());
        memory.remove(NOTE_ASK_KEY + partner.getFaction());
        if (note.isPresent() && !AiSeats.isAiSeat(partner)) {
            memory.put(QUESTION_KEY + partner.getFaction(), note.get());
        } else if (note.isPresent()) {
            drop(context, partner);
        }
    }

    static Optional<AiDecision> askWhatTheyWant(AiTurnContext context) {
        AiMemory memory = context.memory();
        for (Player partner : Standings.others(context.game(), context.seat())) {
            Optional<String> note = memory.get(QUESTION_KEY + partner.getFaction());
            if (note.isEmpty()) continue;
            memory.remove(QUESTION_KEY + partner.getFaction());
            memory.put(
                    ASKED_KEY + partner.getFaction(),
                    String.valueOf(context.game().getRound()));
            return Optional.of(new AiDecision.Announce(question(context.seat(), partner, note.get())));
        }
        return Optional.empty();
    }

    static String question(Player seat, Player partner, String alias) {
        String note = FRAGMENTS_ASKED.equals(alias) ? "relic fragment" : noteName(alias);
        return AiPrompt.TABLE_TALK + seat.getRepresentationNoPing() + " to " + partner.getRepresentationNoPing()
                + ": what would you want for your " + note + "? Send me an offer.";
    }

    private static String noteName(String alias) {
        PromissoryNoteModel model = Mapper.getPromissoryNote(alias);
        return model == null ? "promissory note" : "_" + model.getName() + "_";
    }

    static void noteAsked(AiMemory memory, Player partner, String alias) {
        memory.put(NOTE_ASK_KEY + partner.getFaction(), alias);
    }

    static boolean coolingDown(AiTurnContext context, Player partner) {
        return roundOf(context.memory(), REJECTED_ROUND_KEY + partner.getFaction())
                == context.game().getRound();
    }

    static boolean rejectedSince(AiMemory memory, Player partner, long sentAt) {
        return memory.get(REJECTED_AT_KEY + partner.getFaction())
                .map(Long::parseLong)
                .filter(at -> at >= sentAt)
                .isPresent();
    }

    static boolean dropped(AiTurnContext context, Player partner) {
        return roundOf(context.memory(), DROPPED_KEY + partner.getFaction())
                >= context.game().getRound();
    }

    static boolean answering(AiTurnContext context, Player offerer) {
        int asked = roundOf(context.memory(), ASKED_KEY + offerer.getFaction());
        return asked >= context.game().getRound() - 1;
    }

    static void answered(AiTurnContext context, Player offerer, boolean taken) {
        context.memory().remove(ASKED_KEY + offerer.getFaction());
        if (!taken) drop(context, offerer);
    }

    private static void drop(AiTurnContext context, Player partner) {
        Game game = context.game();
        context.memory().put(DROPPED_KEY + partner.getFaction(), String.valueOf(game.getRound()));
    }

    private static int roundOf(AiMemory memory, String key) {
        return memory.get(key).map(Integer::parseInt).orElse(Integer.MIN_VALUE);
    }
}
