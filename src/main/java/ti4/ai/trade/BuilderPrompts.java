package ti4.ai.trade;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Player;

@UtilityClass
class BuilderPrompts {

    static final long SAME_STEP_TOLERANCE_MILLIS = 5_000L;
    static final String SEND = "sendOffer_";
    static final String NEW_ITEM = "newTransact_";
    static final String MODE = "getNewTransaction_";
    static final String RESET = "resetOffer_";
    static final String DELETE = "deleteButtons";
    private static final String PLAYER_PICK = "transactWith_";
    private static final String PICK = "offerToTransact_";
    private static final String DEBT_ROW = NEW_ITEM + ItemType.SEND_DEBT.token() + "_";
    private static final String SEPARATOR = "_";
    private static final int PICK_FIELDS = 4;

    enum Kind {
        PLAYER_PICKER,
        BUILDER,
        PICKER
    }

    record Step(Kind kind, AiPrompt prompt, String type, String sender, String receiver) {

        boolean isFor(String wantedType, String wantedSender, String wantedReceiver) {
            return type.equals(wantedType) && sender.equals(wantedSender) && receiver.equals(wantedReceiver);
        }

        Optional<PromptButton> button(String handlerId) {
            return prompt.firstEnabled(button -> button.isUnowned() && handlerId.equals(button.handlerId()));
        }
    }

    record Pick(PromptButton button, String type, String sender, String receiver, String detail) {

        static Optional<Pick> of(PromptButton button) {
            if (!button.isUnowned() || !button.handlerId().startsWith(PICK)) return Optional.empty();
            String[] fields = button.handlerId().substring(PICK.length()).split(SEPARATOR, PICK_FIELDS);
            if (fields.length != PICK_FIELDS) return Optional.empty();
            return Optional.of(new Pick(button, fields[0], fields[1], fields[2], fields[3]));
        }

        List<String> shape() {
            return List.of(type, sender, receiver);
        }
    }

    static Optional<Step> newest(AiTurnContext context, Player partner, long since) {
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden() || prompt.createdAtMillis() < since || touched(context, prompt)) continue;
            Optional<Step> step = builder(context.seat(), partner, prompt)
                    .or(() -> picker(context.seat(), partner, prompt))
                    .or(() -> playerPicker(context.seat(), partner, prompt));
            if (step.isPresent()) return step;
        }
        return Optional.empty();
    }

    static Optional<Step> newestBuilder(AiTurnContext context, Player partner, long since) {
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden() || prompt.createdAtMillis() < since || touched(context, prompt)) continue;
            Optional<Step> step = builder(context.seat(), partner, prompt);
            if (step.isPresent()) return step;
        }
        return Optional.empty();
    }

    static long newestBuildAt(AiTurnContext context, Player partner) {
        return context.prompts().stream()
                .filter(AiPrompt::isHidden)
                .filter(prompt -> builder(context.seat(), partner, prompt)
                        .or(() -> picker(context.seat(), partner, prompt))
                        .isPresent())
                .mapToLong(AiPrompt::createdAtMillis)
                .max()
                .orElse(Long.MIN_VALUE);
    }

    static List<Pick> picks(Step picker) {
        return picker.prompt().enabledButtons().stream()
                .map(Pick::of)
                .flatMap(Optional::stream)
                .toList();
    }

    static Optional<PromptButton> partnerButton(Step playerPicker, Player seat, Player partner) {
        return playerPicker.prompt().firstEnabled(picksPartner(seat, partner));
    }

    private static boolean touched(AiTurnContext context, AiPrompt prompt) {
        return prompt.buttons().stream().anyMatch(button -> context.alreadyPressed(prompt, button));
    }

    private static Optional<Step> builder(Player seat, Player partner, AiPrompt prompt) {
        String partnerColor = partner.getColor();
        String send = SEND + partnerColor;
        if (prompt.firstEnabled(button -> button.isUnowned() && send.equals(button.handlerId()))
                .isEmpty()) {
            return Optional.empty();
        }
        String offerRow = DEBT_ROW + seat.getColor() + SEPARATOR + partnerColor;
        String requestRow = DEBT_ROW + partnerColor + SEPARATOR + seat.getColor();
        Optional<PromptButton> row = prompt.firstEnabled(button ->
                button.isUnowned() && (offerRow.equals(button.handlerId()) || requestRow.equals(button.handlerId())));
        if (row.isEmpty()) return Optional.empty();
        boolean offerMode = offerRow.equals(row.get().handlerId());
        String sender = offerMode ? seat.getColor() : partnerColor;
        String receiver = offerMode ? partnerColor : seat.getColor();
        return Optional.of(new Step(Kind.BUILDER, prompt, "", sender, receiver));
    }

    private static Optional<Step> picker(Player seat, Player partner, AiPrompt prompt) {
        if (prompt.hasHandlerPrefix(SEND)) return Optional.empty();
        List<PromptButton> candidates = prompt.enabledButtons().stream()
                .filter(button -> button.handlerId().startsWith(PICK))
                .toList();
        List<Pick> picks =
                candidates.stream().map(Pick::of).flatMap(Optional::stream).toList();
        if (picks.isEmpty() || picks.size() != candidates.size()) return Optional.empty();
        Set<List<String>> shapes = picks.stream().map(Pick::shape).collect(Collectors.toSet());
        if (shapes.size() != 1) return Optional.empty();
        Pick shape = picks.getFirst();
        Set<String> parties = Set.of(seat.getColor(), partner.getColor());
        if (shape.sender().equals(shape.receiver())
                || !parties.contains(shape.sender())
                || !parties.contains(shape.receiver())) {
            return Optional.empty();
        }
        return Optional.of(new Step(Kind.PICKER, prompt, shape.type(), shape.sender(), shape.receiver()));
    }

    private static Optional<Step> playerPicker(Player seat, Player partner, AiPrompt prompt) {
        return prompt.firstEnabled(picksPartner(seat, partner))
                .map(button -> new Step(Kind.PLAYER_PICKER, prompt, "", "", ""));
    }

    private static Predicate<PromptButton> picksPartner(Player seat, Player partner) {
        String byFaction = PLAYER_PICK + partner.getFaction();
        String byColor = PLAYER_PICK + partner.getColor();
        return button -> button.isOwnedBy(seat.getFaction())
                && (byFaction.equals(button.handlerId()) || byColor.equals(button.handlerId()));
    }
}
