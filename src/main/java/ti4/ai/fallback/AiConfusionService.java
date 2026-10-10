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
import ti4.ai.actuation.AiActuator;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
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
        return "🤖 " + seat.getRepresentationNoPing() + " isn't sure what to do here (" + reason + ")."
                + " **Any player** may choose for it. [Original prompt](" + original.getJumpUrl() + ")"
                + (excerpt.isBlank() ? "" : "\n> " + excerpt);
    }

    public static Optional<PromptButton> safeDefault(AiPrompt prompt) {
        List<PromptButton> enabled = prompt.enabledButtons().stream()
                .filter(button -> !AiActuator.opensModal(button.customId()))
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

    public static Optional<PromptButton> selfPlayChoice(AiPrompt prompt) {
        return safeDefault(prompt)
                .or(() -> prompt.enabledButtons().stream()
                        .filter(button -> !AiActuator.opensModal(button.customId()))
                        .filter(button -> !button.handlerId().startsWith("ultimateUndo"))
                        .findFirst());
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

    public static void announcePrivateChoice(Game game, Player seat) {
        notice(
                game,
                "🤖 " + seat.getRepresentationNoPing()
                        + " was unsure about a private choice and picked a safe option.");
    }

    public static void announceSelfPlayChoice(Game game, Player seat, PromptButton choice, String reason) {
        notice(
                game,
                "🤖 " + seat.getRepresentationNoPing() + " was unsure (" + reason + "). No human plays in this game, so"
                        + " it picked **" + choice.label() + "**.");
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
