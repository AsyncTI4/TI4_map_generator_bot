package ti4.ai.explore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import ti4.ai.actioncards.ActionCardValue;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;

class CodexFlow implements RelicFlow {

    static final String PICK_PREFIX = "codexCardPick_";
    static final String CARD_PREFIX = "pickFromDiscard_";
    private static final String RELIC = "codex";
    private static final String BUTTONS = "buttons";
    private static final String CARD = "card";
    private static final int CARDS = 3;
    private static final double WORTH_TAKING = 0.3;

    @Override
    public String name() {
        return "Codex";
    }

    @Override
    public String relicId() {
        return RELIC;
    }

    @Override
    public double netValue(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        List<Double> held = new ArrayList<>(heldValues(game, seat));
        int room = room(game, seat);
        double gain = 0;
        for (double value : retrievable(game, seat).stream().limit(CARDS).toList()) {
            if (room > 0) {
                room--;
                gain += value;
                continue;
            }
            double worst = held.stream().mapToDouble(Double::doubleValue).min().orElse(Double.MAX_VALUE);
            if (value <= worst + WORTH_TAKING) continue;
            gain += value - worst;
            held.remove(worst);
            held.add(value);
        }
        return gain;
    }

    @Override
    public String firstStage() {
        return BUTTONS;
    }

    @Override
    public Optional<AiDecision> step(AiTurnContext context, List<AiPrompt> visible, List<String> fields) {
        int taken = fields.size() > 2 ? Integer.parseInt(fields.get(2)) : 0;
        if (taken >= CARDS) {
            RelicFlows.advance(context, this, RelicFlows.DONE);
            return Optional.empty();
        }
        return switch (fields.get(1)) {
            case BUTTONS -> openPicker(context, visible, taken);
            case CARD -> takeCard(context, visible, taken);
            default -> Optional.empty();
        };
    }

    private Optional<AiDecision> openPicker(AiTurnContext context, List<AiPrompt> visible, int taken) {
        if (worthTaking(context).isEmpty()) {
            RelicFlows.advance(context, this, RelicFlows.DONE);
            return Optional.empty();
        }
        String handler = PICK_PREFIX + (taken + 1);
        Optional<Match> pick = Prompts.unowned(visible, handler::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        pick.ifPresent(match -> RelicFlows.advance(context, this, CARD, String.valueOf(taken)));
        return pick.map(match -> match.press("retrieve an action card with the Codex"));
    }

    private Optional<AiDecision> takeCard(AiTurnContext context, List<AiPrompt> visible, int taken) {
        Optional<String> wanted = worthTaking(context);
        if (wanted.isEmpty()) {
            RelicFlows.advance(context, this, RelicFlows.DONE);
            return Optional.empty();
        }
        Optional<Match> card = Prompts.unowned(visible, (CARD_PREFIX + wanted.get())::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        card.ifPresent(match -> RelicFlows.advance(context, this, BUTTONS, String.valueOf(taken + 1)));
        return card.map(match -> match.press("retrieve " + wanted.get() + " from the discard pile"));
    }

    private Optional<String> worthTaking(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        double worst = heldValues(game, seat).stream()
                .mapToDouble(Double::doubleValue)
                .min()
                .orElse(Double.MAX_VALUE);
        boolean roomLeft = room(game, seat) > 0;
        return pickable(game, seat).stream()
                .max(Comparator.comparingDouble((String alias) -> ActionCardValue.keepValue(game, seat, alias))
                        .thenComparing(Comparator.naturalOrder()))
                .filter(alias -> roomLeft || ActionCardValue.keepValue(game, seat, alias) > worst + WORTH_TAKING);
    }

    private static int room(Game game, Player seat) {
        return Math.max(0, ButtonHelper.getACLimit(game, seat) - seat.getAcCount());
    }

    private static List<Double> heldValues(Game game, Player seat) {
        return seat.getActionCards().keySet().stream()
                .map(alias -> ActionCardValue.keepValue(game, seat, alias))
                .toList();
    }

    private static List<Double> retrievable(Game game, Player seat) {
        return pickable(game, seat).stream()
                .map(alias -> ActionCardValue.keepValue(game, seat, alias))
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    private static List<String> pickable(Game game, Player seat) {
        return game.getDiscardActionCards().keySet().stream()
                .filter(alias -> game.getDiscardACStatus().get(alias) == null)
                .filter(alias -> ActionCardHelper.isDiscardVisible(game, seat, alias))
                .toList();
    }
}
