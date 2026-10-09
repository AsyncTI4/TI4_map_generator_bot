package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.strategy.CopiedTechPolicy;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelperAbilities;

@UtilityClass
public class SingularityRules {

    static final String BASELINE_KEY = "singularityBaseline|";
    static final String PRESSED_KEY = "singularityPressed";
    private static final String STEAL_PREFIX = "nekroStealTech_";
    private static final String TECH_PREFIX = "getTech_";
    private static final String NO_PAY_SUFFIX = "__noPay";
    private static final long CHOICE_WINDOW_MILLIS = 120_000L;
    private static final long CHOICE_TOLERANCE_MILLIS = 5_000L;

    public static Optional<AiDecision> next(AiTurnContext context) {
        Optional<AiDecision> choice = chooseTech(context);
        if (choice.isPresent()) return choice;
        return steal(context);
    }

    private static Optional<AiDecision> steal(AiTurnContext context) {
        long turnStart = Prompts.turnStart(context);
        List<AiPrompt> reminders = Prompts.newestFirst(context.prompts()).stream()
                .filter(AiPrompt::isHidden)
                .filter(prompt -> prompt.createdAtMillis() >= turnStart)
                .toList();
        Optional<Match> reminder = Prompts.owned(reminders, context.faction(), id -> id.startsWith(STEAL_PREFIX));
        if (reminder.isEmpty()) return Optional.empty();
        Game game = context.game();
        Player victim = victimOf(game, context.seat(), reminder.get().button());
        if (victim == null) return Optional.empty();
        String key = BASELINE_KEY + reminder.get().prompt().messageId();
        int units = unitsOnBoard(game, victim);
        Optional<String> baseline = context.memory().get(key);
        if (baseline.isEmpty()) {
            context.memory().put(key, String.valueOf(units));
            return Optional.empty();
        }
        if (units >= Integer.parseInt(baseline.get())) return Optional.empty();
        context.memory().remove(key);
        context.memory().put(PRESSED_KEY, context.now() + "|" + victim.getUserID());
        return Optional.of(reminder.get().press("copy a technology after destroying an enemy unit"));
    }

    private static Player victimOf(Game game, Player seat, PromptButton reminder) {
        Player victim =
                game.getPlayerFromColorOrFaction(StringUtils.substringAfter(reminder.handlerId(), STEAL_PREFIX));
        return victim == seat ? null : victim;
    }

    private static Optional<AiDecision> chooseTech(AiTurnContext context) {
        Optional<String> pressed = context.memory().get(PRESSED_KEY);
        if (pressed.isEmpty()) return Optional.empty();
        long since = Long.parseLong(StringUtils.substringBefore(pressed.get(), "|"));
        Player victim = context.game().getPlayer(StringUtils.substringAfter(pressed.get(), "|"));
        if (victim == null || context.now() - since > CHOICE_WINDOW_MILLIS) {
            context.memory().remove(PRESSED_KEY);
            return Optional.empty();
        }
        Set<String> copyable = Set.copyOf(ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(
                context.seat(), victim, new ArrayList<>(), context.game()));
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden()
                    || prompt.createdAtMillis() < since - CHOICE_TOLERANCE_MILLIS
                    || !prompt.content().contains(context.seat().getRepresentation())) {
                continue;
            }
            Optional<PromptButton> best = prompt.enabledButtons().stream()
                    .filter(button -> button.isUnowned()
                            && button.handlerId().startsWith(TECH_PREFIX)
                            && button.handlerId().endsWith(NO_PAY_SUFFIX))
                    .filter(button -> copyable.contains(aliasOf(button)))
                    .filter(button -> CopiedTechPolicy.value(context.game(), context.seat(), aliasOf(button)) > 0)
                    .max(Comparator.comparingDouble(
                            button -> CopiedTechPolicy.value(context.game(), context.seat(), aliasOf(button))));
            if (best.isPresent()) {
                context.memory().remove(PRESSED_KEY);
                return Optional.of(AiDecision.press(prompt, best.get(), "choose the technology to copy"));
            }
        }
        return Optional.empty();
    }

    private static String aliasOf(PromptButton button) {
        return StringUtils.removeEnd(StringUtils.removeStart(button.handlerId(), TECH_PREFIX), NO_PAY_SUFFIX);
    }

    private static int unitsOnBoard(Game game, Player player) {
        int units = 0;
        for (Tile tile : game.getTileMap().values()) {
            units += tile.getUnitHolders().values().stream()
                    .flatMap(holder ->
                            holder.getUnitKeysForPlayer(player).stream().map(holder::getUnitCount))
                    .mapToInt(Integer::intValue)
                    .sum();
        }
        return units;
    }
}
