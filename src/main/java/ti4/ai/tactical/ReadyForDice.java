package ti4.ai.tactical;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.ai.AiSeats;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.game.Player;

@UtilityClass
class ReadyForDice {

    static final String PREFIX = "announceReadyForDice_";
    static final long AI_CEILING_MILLIS = TimeUnit.MINUTES.toMillis(3);
    static final long POLL_MILLIS = TimeUnit.SECONDS.toMillis(10);
    private static final String DECLARED = "declared";
    private static final String DECLARATION_TEXT = "no more abilities to resolve";
    private static final long AI_DELAY_MIN_MILLIS = TimeUnit.SECONDS.toMillis(3);
    private static final long AI_DELAY_SPREAD_MILLIS = TimeUnit.SECONDS.toMillis(7) + 1;
    private static final long HUMAN_DELAY_MIN_MILLIS = TimeUnit.SECONDS.toMillis(15);
    private static final long HUMAN_DELAY_SPREAD_MILLIS = TimeUnit.SECONDS.toMillis(30) + 1;

    record Round(
            String memoryKey,
            CombatMoment moment,
            Match declaration,
            String reason,
            String channelId,
            long startedAt,
            long decideAt,
            boolean onlyAiOpponents) {}

    static Optional<Round> forRoll(
            AiTurnContext context,
            List<AiPrompt> prompts,
            AiPrompt rollPrompt,
            CombatMoment moment,
            List<Player> opponents) {
        Optional<Match> ready = readyButton(prompts, context.seat(), opponents, rollPrompt.channelId());
        if (ready.isEmpty()) return Optional.empty();
        long startedAt = startedAt(prompts, rollPrompt.channelId());
        boolean onlyAi = opponents.stream().allMatch(AiSeats::isAiSeat);
        return Optional.of(new Round(
                "ready|" + moment.position() + "@" + moment.holder() + "#" + moment.round(),
                moment,
                ready.get(),
                "declare ready to throw dice",
                rollPrompt.channelId(),
                startedAt,
                startedAt + delayMillis(context, moment, onlyAi),
                onlyAi));
    }

    static Round forAutomation(AiTurnContext context, Match confirmation, CombatMoment moment, List<Player> opponents) {
        boolean onlyAi = opponents.stream().allMatch(AiSeats::isAiSeat);
        long startedAt = confirmation.prompt().createdAtMillis();
        return new Round(
                "automation|" + moment.position() + "@" + moment.holder() + "#" + moment.round(),
                moment,
                confirmation,
                "automate ground combat",
                confirmation.prompt().channelId(),
                startedAt,
                startedAt + delayMillis(context, moment, onlyAi),
                onlyAi);
    }

    static boolean decided(AiTurnContext context, Round round) {
        return context.memory().has(round.memoryKey());
    }

    static Optional<AiDecision> decide(AiTurnContext context, Round round, CombatCardHook hook) {
        if (context.now() < round.decideAt()) {
            return Optional.of(new AiDecision.Wait(round.decideAt(), "its round-start decision"));
        }
        Optional<AiDecision> card = hook.atRoundStart(context, round.moment());
        if (card.isPresent()) return card;
        context.memory().put(round.memoryKey(), DECLARED);
        return Optional.of(round.declaration().press(round.reason()));
    }

    static AiDecision rollOrWait(
            AiTurnContext context, List<AiPrompt> prompts, Round round, AiPrompt rollPrompt, PromptButton roll) {
        long giveUpAt = giveUpAt(prompts, round);
        if (opponentReady(prompts, context.seat(), round) || context.now() >= giveUpAt) {
            return AiDecision.press(rollPrompt, roll, "roll a combat round");
        }
        return new AiDecision.Wait(Math.min(giveUpAt, context.now() + POLL_MILLIS), "the opponent to be ready");
    }

    static boolean isDeclaration(AiPrompt prompt) {
        return prompt.content().contains(DECLARATION_TEXT);
    }

    private static long giveUpAt(List<AiPrompt> prompts, Round round) {
        if (round.onlyAiOpponents()) return round.decideAt() + AI_CEILING_MILLIS;
        return newestOutsideDeclarations(prompts, round.channelId()) + CombatRules.ROLL_GRACE_MILLIS;
    }

    private static Optional<Match> readyButton(
            List<AiPrompt> prompts, Player seat, List<Player> opponents, String channelId) {
        List<AiPrompt> inChannel = prompts.stream()
                .filter(prompt -> prompt.source() == PromptSource.COMBAT_THREAD)
                .filter(prompt -> prompt.channelId().equals(channelId))
                .toList();
        return Prompts.first(inChannel, button -> button.isUnowned() && readyFor(button.handlerId(), seat, opponents));
    }

    private static boolean readyFor(String handlerId, Player seat, List<Player> opponents) {
        String[] parts = handlerId.split("_");
        if (!handlerId.startsWith(PREFIX) || parts.length != 3 || seat.getColor() == null) return false;
        String other = parts[1].equals(seat.getColor()) ? parts[2] : parts[2].equals(seat.getColor()) ? parts[1] : "";
        return opponents.stream().anyMatch(opponent -> Objects.equals(opponent.getColor(), other));
    }

    private static boolean opponentReady(List<AiPrompt> prompts, Player seat, Round round) {
        return prompts.stream()
                .filter(prompt -> prompt.channelId().equals(round.channelId()))
                .filter(prompt -> prompt.createdAtMillis() > round.startedAt())
                .filter(ReadyForDice::isDeclaration)
                .anyMatch(prompt -> Prompts.mentions(prompt, seat));
    }

    private static long startedAt(List<AiPrompt> prompts, String channelId) {
        return prompts.stream()
                .filter(prompt -> prompt.channelId().equals(channelId))
                .filter(prompt -> !isDeclaration(prompt))
                .filter(prompt ->
                        !prompt.content().isBlank() || !prompt.buttons().isEmpty())
                .mapToLong(AiPrompt::createdAtMillis)
                .max()
                .orElse(0L);
    }

    private static long newestOutsideDeclarations(List<AiPrompt> prompts, String channelId) {
        return prompts.stream()
                .filter(prompt -> prompt.channelId().equals(channelId))
                .filter(prompt -> !isDeclaration(prompt))
                .mapToLong(AiPrompt::createdAtMillis)
                .max()
                .orElse(0L);
    }

    private static long delayMillis(AiTurnContext context, CombatMoment moment, boolean onlyAi) {
        long seed = Objects.hash(
                        context.game().getName(), moment.position(), moment.holder(), moment.round(), context.faction())
                ^ context.profile().seed();
        long minimum = onlyAi ? AI_DELAY_MIN_MILLIS : HUMAN_DELAY_MIN_MILLIS;
        long spread = onlyAi ? AI_DELAY_SPREAD_MILLIS : HUMAN_DELAY_SPREAD_MILLIS;
        return minimum + Math.floorMod(seed, spread);
    }
}
