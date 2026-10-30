package ti4.ai.promissory;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;

@UtilityClass
public class PromissoryRules {

    public static final String GREYFIRE_TARGET = "greyfire_";

    public static Optional<AiDecision> observe(AiTurnContext context) {
        NoteHand.rememberPlayButtons(context);
        CeasefireRules.observe(context);
        PlayAreaNotes.observe(context);
        return Optional.empty();
    }

    public static Optional<AiDecision> continuePlay(AiTurnContext context) {
        return CombatNotes.chooseGreyfireTarget(context).or(() -> NoteOffers.placeMilitarySupport(context));
    }

    public static Optional<AiDecision> answerOffers(AiTurnContext context) {
        return NoteOffers.answer(context);
    }

    public static Optional<AiDecision> playCeasefire(AiTurnContext context) {
        return CeasefireRules.playAsHolder(context);
    }

    public static Optional<AiDecision> playCombatNotes(AiTurnContext context) {
        return CombatNotes.play(context);
    }

    public static Optional<AiDecision> queueAgendaNote(AiTurnContext context) {
        return AgendaNotes.queue(context);
    }

    public static Optional<AiDecision> holdForCeasefire(AiTurnContext context) {
        return CeasefireRules.holdForHolder(context);
    }
}
