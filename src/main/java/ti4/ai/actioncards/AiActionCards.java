package ti4.ai.actioncards;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.AiCard.Window;
import ti4.ai.nekro.CommandTokenPolicy;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;

@UtilityClass
public class AiActionCards {

    static final String SABOTAGE = "sabotage";
    private static final ToDoubleBiFunction<Game, Player> ALWAYS = (game, seat) -> 1.0;
    private static final ToDoubleBiFunction<Game, Player> NEVER = (game, seat) -> 0.0;
    private static final double SUMMIT_TOKEN_VALUE = 2.0;

    private static final List<AiCard> CARDS = List.of(
            economy(ActionCardValue.MINING, "miningInitiative", null),
            economy(ActionCardValue.INDUSTRIAL, "industrialInitiative", null),
            economy(ActionCardValue.ECONOMIC, "economicInitiative", null),
            economy(ActionCardValue.MESSIAH, "riseOfAMessiah", null),
            economy(ActionCardValue.FRONTLINE, "resolveFrontline", EconomyTargets::frontline),
            economy(ActionCardValue.WAR_EFFORT, "resolveWarEffort", EconomyTargets::warEffort),
            new AiCard(
                    ActionCardValue.SUMMIT,
                    ActionCardValue.SUMMIT::equals,
                    Window.PRE_PLAY,
                    AiActionCards::summitValue,
                    ALWAYS,
                    "resolveSummit",
                    EconomyTargets::summitTokens),
            new AiCard(SABOTAGE, ActionCardHelper::isSabotageOrShatter, Window.REACTION, NEVER, ALWAYS, null, null));

    public static boolean usable(String alias) {
        return of(alias).isPresent();
    }

    public static boolean isSabotage(String alias) {
        return of(alias).filter(AiCard::isSabotage).isPresent();
    }

    static Optional<AiCard> of(String alias) {
        return CARDS.stream().filter(card -> card.matches().test(alias)).findFirst();
    }

    static Optional<AiCard> playable(String alias) {
        return of(alias).filter(card -> card.window() != Window.REACTION);
    }

    static List<AiCard> inWindow(Window window) {
        return CARDS.stream().filter(card -> card.window() == window).toList();
    }

    private static AiCard economy(String alias, String resolveHandler, TargetStep target) {
        Predicate<String> matches = alias::equals;
        return new AiCard(
                alias,
                matches,
                Window.COMPONENT_ACTION,
                (game, seat) -> ActionCardValue.playValue(game, seat, alias),
                ALWAYS,
                resolveHandler,
                target);
    }

    private static double summitValue(Game game, Player seat) {
        return SUMMIT_TOKEN_VALUE
                * Math.min(EconomyTargets.SUMMIT_TOKENS, CommandTokenPolicy.gainWithinReinforcements(game, seat));
    }
}
