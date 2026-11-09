package ti4.ai.actioncards;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.AiCard.KeepFloor;
import ti4.ai.actioncards.AiCard.Window;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.agenda.AgendaClock;
import ti4.ai.agenda.AgendaRound;
import ti4.ai.agenda.AgendaSwing;
import ti4.ai.agenda.ShenanigansRules;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.GameMessageManager;

@UtilityClass
public class ShenaniganCards {

    private static final String MANUAL = "autoresolve_manual";
    private static final String ELECT = "agendaResolution_";
    private static final String NO_EFFECT = "resolveWithNoEffect";
    private static final String MANUAL_PRESSED = "manual";
    private static final String CURRENT_WINNER_LABEL = "Resolve with Current Winner";
    private static final KeepFloor PRIOR = (game, seat, alias) -> ActionCardPrior.keepPrior(alias);
    private static final List<AiCard> CARDS = List.of(
            card(AgendaSwing.CONFOUNDING_LEGAL_TEXT, ShenaniganCards::electItself),
            card(AgendaSwing.DEADLY_PLOT, ShenaniganCards::discardTheAgenda));

    public static Set<String> targetPrefixes() {
        return Set.of(MANUAL, ELECT, NO_EFFECT);
    }

    public static boolean playing(AiTurnContext context, String title) {
        return context.memory()
                .get(PlayMemory.agendaKey(context))
                .flatMap(CardPlay::decode)
                .filter(play -> title.equals(SabotageWindow.title(play.alias())))
                .filter(play ->
                        play.active() || !context.seat().getActionCards().containsKey(play.alias()))
                .isPresent();
    }

    public static Optional<AiDecision> playIfWorthIt(AiTurnContext context, String title) {
        String key = PlayMemory.agendaKey(context);
        if (context.memory().has(key)) return Optional.empty();
        Game game = context.game();
        Player seat = context.seat();
        boolean holds =
                seat.getActionCards().keySet().stream().anyMatch(alias -> title.equals(SabotageWindow.title(alias)));
        if (!holds) return Optional.empty();
        double gain = gainOf(game, seat, title);
        return worthPlaying(game, seat, title, gain)
                .flatMap(alias -> ActionCardRules.startPlay(context, key, alias, Plan.of(gain)));
    }

    static Optional<String> worthPlaying(Game game, Player seat, String title, double gain) {
        if (!ActionCardRules.mayPlayNow(game, seat) || !legal(game, seat, title)) return Optional.empty();
        return seat.getActionCards().keySet().stream()
                .filter(alias -> title.equals(SabotageWindow.title(alias)))
                .sorted()
                .findFirst()
                .filter(alias -> gain >= ActionCardPrior.keepPrior(alias));
    }

    private static double gainOf(Game game, Player seat, String title) {
        double swing = AgendaSwing.forViewer(game, seat, title, seat);
        if (!EarlyDeadlyPlot.applies(game, seat, title)) return swing;
        return swing - EarlyDeadlyPlot.lostSecondAgendaVotes(game, seat);
    }

    static Optional<AiCard> of(String alias) {
        return CARDS.stream().filter(card -> card.matches().test(alias)).findFirst();
    }

    private static boolean legal(Game game, Player seat, String title) {
        Optional<String> winner = AgendaSwing.currentWinner(game, seat);
        if (winner.isEmpty()) return false;
        return switch (title) {
            case AgendaSwing.CONFOUNDING_LEGAL_TEXT ->
                AgendaSwing.isElectable(game, seat) && !winner.get().equals(seat.getFaction());
            case AgendaSwing.DEADLY_PLOT ->
                AgendaSwing.backedAnotherOutcome(game, seat, winner.get())
                        && mayDeadlyPlotNow(game, seat, winner.get());
            default -> false;
        };
    }

    private static boolean mayDeadlyPlotNow(Game game, Player seat, String winner) {
        if (AgendaRound.isFinalAgenda(game)) return true;
        return EarlyDeadlyPlot.applies(game, seat, AgendaSwing.DEADLY_PLOT)
                && EarlyDeadlyPlot.worthDiscarding(game, seat, winner);
    }

    private static AiCard card(String title, TargetStep target) {
        return new AiCard(
                title,
                alias -> title.equals(SabotageWindow.title(alias)),
                Window.AGENDA_AFTER,
                (game, seat) -> Optional.empty(),
                PRIOR,
                null,
                List.of(),
                target);
    }

    private static Optional<AiDecision> electItself(AiTurnContext context, String key, CardPlay play) {
        return finish(context, key, play, ELECT + context.faction(), "elect itself with Confounding Legal Text");
    }

    private static Optional<AiDecision> discardTheAgenda(AiTurnContext context, String key, CardPlay play) {
        return finish(context, key, play, NO_EFFECT, "discard the agenda with Deadly Plot");
    }

    static boolean isShenanigan(AiCard card) {
        return CARDS.stream().anyMatch(shenanigan -> shenanigan.key().equals(card.key()));
    }

    static boolean othersAnswered(AiTurnContext context) {
        Game game = context.game();
        List<String> others = game.getRealPlayers().stream()
                .filter(player -> player != context.seat())
                .map(Player::getFaction)
                .toList();
        return context.prompts().stream()
                .filter(prompt -> !prompt.isHidden() && AgendaClock.isInThisAgenda(context, prompt))
                .filter(prompt -> prompt.enabledButtons().stream().anyMatch(ShenanigansRules::isShenanigansButton))
                .allMatch(prompt -> GameMessageManager.getOne(game.getName(), prompt.messageId())
                        .map(entry -> entry.factionsThatReacted().containsAll(others))
                        .orElse(true));
    }

    private static Optional<AiDecision> finish(
            AiTurnContext context, String key, CardPlay play, String handler, String reason) {
        if (MANUAL_PRESSED.equals(play.extra())) return chooseOutcome(context, key, play, handler, reason);
        Optional<Match> manual = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> !prompt.isHidden() && AgendaClock.isInThisAgenda(context, prompt))
                .filter(ShenaniganCards::offersTheCurrentWinner)
                .map(prompt -> unpressed(context, prompt, MANUAL))
                .flatMap(Optional::stream)
                .findFirst();
        manual.ifPresent(ignored -> PlayMemory.store(context, key, play.withExtra(MANUAL_PRESSED)));
        return manual.map(match -> match.press("resolve the agenda by hand to " + reason));
    }

    private static Optional<AiDecision> chooseOutcome(
            AiTurnContext context, String key, CardPlay play, String handler, String reason) {
        Optional<Match> outcome = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> !prompt.isHidden()
                        && prompt.createdAtMillis() >= play.playedAt() - CardPrompts.CLOCK_SKEW_MILLIS)
                .map(prompt -> unpressed(context, prompt, handler))
                .flatMap(Optional::stream)
                .findFirst();
        outcome.ifPresent(ignored -> PlayMemory.moveTo(context, key, play, Stage.DONE));
        return outcome.map(match -> match.press(reason));
    }

    private static boolean offersTheCurrentWinner(AiPrompt prompt) {
        return prompt.firstEnabled(button -> button.isUnowned()
                        && button.handlerId().startsWith(ELECT)
                        && CURRENT_WINNER_LABEL.equals(button.label()))
                .isPresent();
    }

    private static Optional<Match> unpressed(AiTurnContext context, AiPrompt prompt, String handler) {
        Optional<PromptButton> button = prompt.firstEnabled(candidate -> candidate.isUnowned()
                && handler.equals(candidate.handlerId())
                && !context.alreadyPressed(prompt, candidate));
        return button.map(found -> new Match(prompt, found));
    }
}
