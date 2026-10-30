package ti4.ai.promissory;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.agenda.AgendaPolicy;
import ti4.ai.agenda.AgendaQueue;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.Mapper;
import ti4.model.PromissoryNoteModel;

@UtilityClass
class AgendaNotes {

    private static final String WHEN_WINDOW_PHASE = "agendawaiting";
    private static final String WHENS_RESOLVED = "whensResolved";
    private static final String DECLINED_WHENS = "declinedWhens";
    private static final String OPEN_WHENS = "queueAWhen";
    private static final String QUEUE_NOTE = "queueWhen_pn_";
    private static final String POLITICAL_SECRET = "_ps";
    private static final String ABSOL = "absol";
    private static final String POLITICAL_FAVOR = "favor";
    private static final double SECRET_MIN_GAIN = 3.0;

    static Optional<AiDecision> queue(AiTurnContext context) {
        Game game = context.game();
        String faction = context.faction();
        if (!inWhenWindow(game)) return Optional.empty();
        if (AgendaQueue.holdsQueuedWhen(game, context.seat())) return Optional.empty();
        if (game.getStoredValue(DECLINED_WHENS).contains(faction + "_")) return Optional.empty();
        Optional<String> note = noteToPlay(game, context.seat());
        if (note.isEmpty()) return Optional.empty();
        String name = nameOf(note.get());
        return pressHidden(context, QUEUE_NOTE + note.get(), "queue " + name)
                .or(() -> pressHidden(context, OPEN_WHENS, "open its when options to play " + name));
    }

    private static boolean inWhenWindow(Game game) {
        return WHEN_WINDOW_PHASE.equalsIgnoreCase(game.getPhaseOfGame())
                && game.getStoredValue(WHENS_RESOLVED).isEmpty();
    }

    private static Optional<String> noteToPlay(Game game, Player seat) {
        if (NoteHand.holdsForeign(seat, POLITICAL_FAVOR) && favorWorthIt(game, seat)) {
            return Optional.of(POLITICAL_FAVOR);
        }
        return seat.getPromissoryNotes().keySet().stream()
                .filter(id -> id.endsWith(POLITICAL_SECRET) && !id.contains(ABSOL))
                .filter(id -> NoteHand.holdsForeign(seat, id))
                .filter(id -> secretWorthIt(game, seat, game.getPNOwner(id)))
                .sorted()
                .findFirst();
    }

    private static boolean favorWorthIt(Game game, Player seat) {
        Player xxcha = game.getPNOwner(POLITICAL_FAVOR);
        return xxcha != null && xxcha.getStrategicCC() > 0 && AgendaPolicy.worthDiscarding(game, seat);
    }

    private static boolean secretWorthIt(Game game, Player seat, Player owner) {
        if (owner == null) return false;
        double silenced = AgendaPolicy.predictedElectionScore(game, seat, owner);
        double expected = AgendaPolicy.predictedElectionScore(game, seat, null);
        return silenced - expected >= SECRET_MIN_GAIN;
    }

    private static String nameOf(String noteId) {
        PromissoryNoteModel model = Mapper.getPromissoryNote(noteId);
        return model == null ? noteId : model.getName();
    }

    private static Optional<AiDecision> pressHidden(AiTurnContext context, String handlerId, String reason) {
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden()) continue;
            Optional<PromptButton> button = prompt.enabledHandler(handlerId);
            if (button.isPresent() && !context.alreadyPressed(prompt, button.get())) {
                return Optional.of(AiDecision.press(prompt, button.get(), reason));
            }
        }
        return Optional.empty();
    }
}
