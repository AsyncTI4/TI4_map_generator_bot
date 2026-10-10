package ti4.ai.selfplay;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import ti4.ai.AiSeats;
import ti4.ai.AiSettings;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class RefereeRules {

    public record RefereePress(Player seat, AiPrompt prompt, PromptButton button, String reason) {}

    private record Step(Predicate<String> handler, String reason) {
        boolean reveals() {
            return handler.test("reveal_stage_1")
                    || handler.test("reveal_stage_2")
                    || handler.test("reveal_stage_1position_1")
                    || handler.test("reveal_stage_2position_1");
        }
    }

    private static final List<Step> STEPS = List.of(
            new Step("deal2SOToAll"::equals, "deal the starting secret objectives"),
            new Step("startOfGameObjReveal"::equals, "reveal the first objectives"),
            new Step(id -> id.startsWith("reveal_stage_1position_"), "reveal a stage 1 objective"),
            new Step(id -> id.startsWith("reveal_stage_2position_"), "reveal a stage 2 objective"),
            new Step("reveal_stage_1"::equals, "reveal a stage 1 objective"),
            new Step("reveal_stage_2"::equals, "reveal a stage 2 objective"),
            new Step("startStrategyPhase"::equals, "start the strategy phase"),
            new Step("flip_agenda"::equals, "flip the agenda"),
            new Step(id -> id.startsWith("agendaResolution_"), "resolve the agenda"),
            new Step(id -> id.startsWith("resolveAgendaVote_outcomeTie"), "break the agenda tie"),
            new Step("proceed_to_strategy"::equals, "move on to the strategy phase"));

    public static boolean trackExhausted(Game game) {
        if (game.isOmegaPhaseMode()) return game.getPublicObjectives1Peekable().isEmpty();
        return game.getPublicObjectives2Peekable().isEmpty();
    }

    public static boolean offersGameEnd(List<AiPrompt> publicPrompts) {
        return publicPrompts.stream()
                .anyMatch(prompt -> prompt.firstEnabled(button -> "gameEnd".equals(button.handlerId()))
                        .isPresent());
    }

    public static int stateFingerprint(Game game) {
        return Objects.hash(
                game.getRound(),
                game.getPhaseOfGame(),
                game.getSpeakerUserID(),
                game.getRealPlayers().stream()
                        .map(player -> player.getSecretsUnscored().size())
                        .toList(),
                new TreeMap<>(game.getStoredValueMap()));
    }

    public static Predicate<PromptButton> anyStep() {
        return button -> STEPS.stream().anyMatch(step -> step.handler().test(button.handlerId()));
    }

    public static Optional<RefereePress> next(
            Game game, List<Player> seats, List<AiPrompt> publicPrompts, Predicate<String> mayPress, long now) {
        if (seats.isEmpty() || !AiSeats.isSelfPlay(game)) return Optional.empty();
        long settledBefore = now - AiSettings.refereeDelay(game).toMillis();
        boolean everySeatFilled = game.getRealPlayers().size() >= game.getPlayerCountForMap();
        for (Step step : STEPS) {
            if (step == STEPS.getFirst() && !everySeatFilled) continue;
            if (step.reveals() && trackExhausted(game)) continue;
            for (AiPrompt prompt : publicPrompts) {
                if (prompt.createdAtMillis() > settledBefore) continue;
                Optional<PromptButton> button =
                        prompt.firstEnabled(candidate -> step.handler().test(candidate.handlerId())
                                && mayPress.test(AiTurnContext.pressKey(prompt, candidate)));
                if (button.isEmpty()) continue;
                Optional<Player> presser = presser(game, seats, button.get());
                if (presser.isPresent()) {
                    return Optional.of(new RefereePress(presser.get(), prompt, button.get(), step.reason()));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Player> presser(Game game, List<Player> seats, PromptButton button) {
        if (!button.isUnowned()) {
            return seats.stream()
                    .filter(seat -> button.isOwnedBy(seat.getFaction()))
                    .findFirst();
        }
        return seats.stream()
                .filter(seat -> seat.getUserID().equals(game.getSpeakerUserID()))
                .findFirst()
                .or(() -> seats.stream().findFirst());
    }
}
