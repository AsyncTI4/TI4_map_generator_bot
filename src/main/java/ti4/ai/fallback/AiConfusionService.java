package ti4.ai.fallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.actuation.AiActuator;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.tactical.TacticalRules;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.StringHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
public class AiConfusionService {

    public static final String LOOP_NOTICE_MARK = "🔁";
    private static final int MAX_OPTIONS = 25;
    private static final int EXCERPT_LENGTH = 300;
    private static final int LABEL_LENGTH = 80;
    private static final List<String> DECLINE_HINTS =
            List.of("decline", "pass", "no_", "not follow", "deletebuttons", "done", "skip", "cancel");
    private static final List<String> HIT_CANCEL_HANDLERS =
            List.of("cancelSpaceHits_", "cancelPdsOffenseHits_", "cancelGroundHits_", "cancelAFBHits_");
    private static final String RING_TILE = "ringTile_";
    private static final String PASS_FOR_ROUND = "passForRound";
    private static final String TURN_END = "turnEnd";
    private static final List<String> VOTE_PREFIXES =
            List.of("outcome_", "planetOutcomes_", "exhaustForVotes_", "resolveAgendaVote_", "proceedToFinalizingVote");
    private static final String VOTE = "vote";
    private static final Set<String> CLAIMED_DELEGATIONS = ConcurrentHashMap.newKeySet();

    public static boolean claim(String delegationMessageId) {
        return CLAIMED_DELEGATIONS.add(delegationMessageId);
    }

    public static boolean delegate(
            Game game, Player seat, AiPrompt prompt, String reason, Predicate<String> offerable) {
        TextChannel table = game.getMainGameChannel();
        GuildMessageChannel source = JdaService.jda == null
                ? null
                : JdaService.jda.getChannelById(GuildMessageChannel.class, prompt.channelId());
        if (table == null || source == null) return false;
        Optional<Message> original = AiActuator.retrieve(source, prompt.messageId());
        if (original.isEmpty()) return false;
        List<Button> options = options(seat, original.get(), offerable);
        if (options.isEmpty()) return false;
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(
                table, delegationText(seat, original.get(), reason), new ArrayList<>(options));
        return true;
    }

    private static List<Button> options(Player seat, Message original, Predicate<String> offerable) {
        List<Button> options = new ArrayList<>();
        int index = 0;
        for (Button button : original.getComponentTree().findAll(Button.class)) {
            String customId = button.getCustomId();
            if (customId != null
                    && !button.isDisabled()
                    && !AiActuator.opensModal(customId)
                    && offerable.test(customId)
                    && options.size() < MAX_OPTIONS) {
                DelegationChoice choice = DelegationChoice.of(
                        seat.getUserID(), original.getChannel().getId(), original.getId(), index, customId);
                options.add(button.withCustomId(choice.customId())
                        .withLabel(StringUtils.abbreviate(button.getLabel(), LABEL_LENGTH)));
            }
            index++;
        }
        return options;
    }

    private static String delegationText(Player seat, Message original, String reason) {
        String excerpt = StringUtils.abbreviate(original.getContentRaw().replace("\n", " "), EXCERPT_LENGTH);
        String chooser = AiExperiments.enabled(seat, Experiment.EXPLOIT_GUARDS)
                ? " **The game owner or a bot developer** may choose for it."
                : " **Any player** may choose for it.";
        return "🤖 " + seat.getRepresentationNoPing() + " isn't sure what to do here (" + reason + ")." + chooser
                + " [Original prompt](" + original.getJumpUrl() + ")"
                + (excerpt.isBlank() ? "" : "\n> " + excerpt);
    }

    public static boolean mayChooseFor(Player seat, String userId, String ownerId, boolean developer) {
        if (!AiExperiments.enabled(seat, Experiment.EXPLOIT_GUARDS)) return true;
        return developer || (userId != null && userId.equals(ownerId));
    }

    public static boolean passesEarly(Game game, Player seat, PromptButton button) {
        if (!AiExperiments.enabled(seat, Experiment.EXPLOIT_GUARDS) || !passesRound(button)) return false;
        return seat.getTacticalCC() > 0 || hasUnplayedStrategyCard(game, seat);
    }

    public static boolean neverDelegated(Game game, Player seat, PromptButton button) {
        if (!AiExperiments.enabled(seat, Experiment.EXPLOIT_GUARDS)) return false;
        return passesEarly(game, seat, button) || endsTurn(button) || isVote(button);
    }

    private static boolean passesRound(PromptButton button) {
        return button.handlerId().startsWith(PASS_FOR_ROUND);
    }

    private static boolean endsTurn(PromptButton button) {
        return button.handlerId().startsWith(TURN_END);
    }

    private static boolean isVote(PromptButton button) {
        return VOTE.equals(button.handlerId()) || VOTE_PREFIXES.stream().anyMatch(button.handlerId()::startsWith);
    }

    private static boolean hasUnplayedStrategyCard(Game game, Player seat) {
        return seat.getSCs().stream().anyMatch(card -> !game.getPlayedSCs().contains(card));
    }

    public static Optional<PromptButton> safeDefault(Game game, Player seat, AiPrompt prompt) {
        List<PromptButton> enabled = prompt.enabledButtons().stream()
                .filter(button -> !AiActuator.opensModal(button.customId()))
                .filter(button -> !cancelsHits(button))
                .filter(button -> !passesEarly(game, seat, button))
                .toList();
        for (String hint : DECLINE_HINTS) {
            Optional<PromptButton> declining = enabled.stream()
                    .filter(button ->
                            button.handlerId().toLowerCase(Locale.ROOT).contains(hint)
                                    || button.label().toLowerCase(Locale.ROOT).contains(hint))
                    .findFirst();
            if (declining.isPresent()) return declining;
        }
        return Optional.empty();
    }

    public static Optional<PromptButton> selfPlayChoice(Game game, Player seat, AiPrompt prompt) {
        return safeDefault(game, seat, prompt)
                .or(() -> prompt.enabledButtons().stream()
                        .filter(button -> !AiActuator.opensModal(button.customId()))
                        .filter(button -> !button.handlerId().startsWith("ultimateUndo"))
                        .filter(button -> !cancelsHits(button))
                        .filter(button -> !passesEarly(game, seat, button))
                        .findFirst());
    }

    public static Optional<PromptButton> stalledChoice(Game game, Player seat, AiPrompt prompt) {
        if (!TacticalRules.isSystemPicker(prompt)) return selfPlayChoice(game, seat, prompt);
        return prompt.enabledButtons().stream()
                .filter(button -> button.handlerId().startsWith(RING_TILE))
                .filter(button -> button.isUnowned() || button.isOwnedBy(seat.getFaction()))
                .filter(button -> holdsOnlyOwnUnits(game, seat, StringUtils.removeStart(button.handlerId(), RING_TILE)))
                .findFirst()
                .or(() -> selfPlayChoice(game, seat, prompt));
    }

    private static boolean holdsOnlyOwnUnits(Game game, Player seat, String position) {
        Tile tile = game.getTileByPosition(position);
        return tile != null && tile.containsPlayersUnits(seat) && !BoardView.hasEnemyUnits(game, seat, tile);
    }

    private static boolean cancelsHits(PromptButton button) {
        return HIT_CANCEL_HANDLERS.stream()
                .anyMatch(prefix -> button.handlerId().startsWith(prefix));
    }

    public static void announceLoop(Game game, Player seat, String reason, int times, boolean selfPlay) {
        notice(
                game,
                LOOP_NOTICE_MARK + " " + seat.getRepresentationNoPing() + " was unsure about the same thing " + times
                        + " times in a row this turn (" + reason + "), so it has stopped choosing on its own. "
                        + (selfPlay
                                ? "No human plays in this game, so it cannot go on by itself: pause it with"
                                        + " `/ai pause` and choose for it with `/ai delegate`, or finish the game"
                                        + " with `/game end`."
                                : "Use `/ai delegate` to choose for it."));
    }

    public static void announcePrivateChoice(Game game, Player seat, int tokensRestored) {
        notice(
                game,
                "🤖 " + seat.getRepresentationNoPing()
                        + " was unsure about a private choice and picked a safe option."
                        + restoredSuffix(tokensRestored));
    }

    public static void announceSelfPlayChoice(
            Game game, Player seat, PromptButton choice, String reason, int tokensRestored) {
        notice(
                game,
                "🤖 " + seat.getRepresentationNoPing() + " was unsure (" + reason + "). No human plays in this game, so"
                        + " it picked **" + choice.label() + "**." + restoredSuffix(tokensRestored));
    }

    private static String restoredSuffix(int tokensRestored) {
        return tokensRestored == 0
                ? ""
                : " It first put " + StringHelper.pluralize(tokensRestored, "command token") + " back.";
    }

    public static void announceStuck(Game game, Player seat, String reasons) {
        notice(
                game,
                "🤖 " + seat.getRepresentationNoPing() + " is stuck (" + reasons + ") and has no option it can hand"
                        + " over. Please resolve it for the AI.");
    }

    public static void notice(Game game, String text) {
        TextChannel table = game.getMainGameChannel();
        if (table != null) MessageHelper.sendMessageToChannel(table, text);
    }

    public static void resolve(DelegatedPress press, String seatName, boolean succeeded) {
        if (JdaService.jda == null) return;
        GuildMessageChannel channel =
                JdaService.jda.getChannelById(GuildMessageChannel.class, press.delegationChannelId());
        if (channel == null) return;
        String outcome = succeeded
                ? "✅ " + press.chooserName() + " chose **" + press.label() + "** for " + seatName + "."
                : "⚠️ " + press.chooserName() + " chose **" + press.label() + "**, but that option is no longer"
                        + " available.";
        channel.retrieveMessageById(press.delegationMessageId())
                .queue(
                        message -> message.editMessage(message.getContentRaw() + "\n" + outcome)
                                .setComponents(List.of())
                                .queue(null, BotLogger::catchRestError),
                        BotLogger::catchRestError);
    }
}
