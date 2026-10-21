package ti4.ai.tech;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.strategy.ResearchPolicy;
import ti4.ai.strategy.ScarToken;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class EntropicScarRules {

    static final String TECH_PREFIX = "entropicScar_";
    private static final String DECLINE_HANDLER = "deleteButtons";
    private static final String DECLINE_LABEL = "No Thanks";
    private static final Set<String> STATUS_PHASES = Set.of("statusscoring", "statushomework");
    private static final double STRATEGY_TOKEN_VALUE = 1.0;

    public static Optional<AiDecision> next(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        if (!STATUS_PHASES.contains(String.valueOf(game.getPhaseOfGame()).toLowerCase())
                || !hasShipsInAScar(game, seat)) {
            return Optional.empty();
        }
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.firstEnabled(button -> offersTechnology(button, seat)).isEmpty()) continue;
            Optional<PromptButton> choice = prompt.buttons().stream()
                    .filter(button -> !button.disabled() && offersTechnology(button, seat))
                    .filter(button -> !context.alreadyPressed(prompt, button))
                    .filter(button -> worthIt(game, seat, alias(button)))
                    .max(Comparator.comparingDouble(button -> ResearchPolicy.value(game, seat, alias(button))));
            if (choice.isPresent()) {
                return choice.map(button -> AiDecision.press(
                        prompt, button, "gain a faction technology from the Entropic Scar for a strategy token"));
            }
            return prompt.buttons().stream()
                    .filter(button -> !button.disabled() && isDecline(button))
                    .filter(button -> !context.alreadyPressed(prompt, button))
                    .findFirst()
                    .map(button -> AiDecision.press(prompt, button, "decline the Entropic Scar technology"));
        }
        return Optional.empty();
    }

    static boolean hasShipsInAScar(Game game, Player seat) {
        return game.getTileMap().values().stream()
                .anyMatch(tile -> inAScar(tile) && Tile.tileHasPlayerShips(seat).test(tile));
    }

    private static boolean inAScar(Tile tile) {
        return tile.isScar()
                || tile.getSpaceUnitHolder().getUnitKeys().stream()
                        .anyMatch(key -> key.unitType() == UnitType.Aurelion);
    }

    private static boolean offersTechnology(PromptButton button, Player seat) {
        return button.isOwnedBy(seat.getFaction())
                && button.handlerId().startsWith(TECH_PREFIX)
                && !seat.hasTech(alias(button));
    }

    private static boolean isDecline(PromptButton button) {
        return button.isUnowned() && DECLINE_HANDLER.equals(button.handlerId()) && DECLINE_LABEL.equals(button.label());
    }

    private static String alias(PromptButton button) {
        return StringUtils.removeStart(button.handlerId(), TECH_PREFIX);
    }

    private static boolean worthIt(Game game, Player seat, String alias) {
        if (!ScarToken.canPay(seat)) return false;
        return ScarToken.completesAdaptNewStrategies(game, seat, alias)
                || ResearchPolicy.TECH_TEMPO * ResearchPolicy.value(game, seat, alias) >= STRATEGY_TOKEN_VALUE;
    }
}
