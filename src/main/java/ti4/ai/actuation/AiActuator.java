package ti4.ai.actuation;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.ButtonProcessor;
import ti4.logging.BotLogger;
import ti4.service.testbed.TestBedPress;

@UtilityClass
public class AiActuator {

    private static final String PRIVATE_PROMPT_HEADER = "-# 🤖 private prompt for this AI seat";
    private static final String MODAL_MARKER = "~MDL";
    private static final Duration PENDING_EDIT_TIMEOUT = Duration.ofSeconds(15);
    private static final List<String> REJECTION_REPLIES =
            List.of("is not a player of the game", "these buttons are for someone else");

    public enum Outcome {
        PRESSED,
        STALE,
        REJECTED,
        HANDLER_FAILED,
        ERROR
    }

    public record PressTarget(String channelId, String messageId, String customId) {}

    public record SeatIdentity(
            String userId,
            String faction,
            String displayName,
            @Nullable String privateThreadId) {}

    public record Result(Outcome outcome, String detail) {
        public boolean pressed() {
            return outcome == Outcome.PRESSED;
        }
    }

    public static boolean opensModal(String customId) {
        return customId.contains(MODAL_MARKER);
    }

    public static Result press(SeatIdentity seat, PressTarget target) {
        if (JdaService.jda == null) return new Result(Outcome.ERROR, "Discord is not connected");
        if (opensModal(target.customId())) return new Result(Outcome.REJECTED, "the button opens a form");
        GuildMessageChannel channel = JdaService.jda.getChannelById(GuildMessageChannel.class, target.channelId());
        if (channel == null) return new Result(Outcome.STALE, "channel is gone");
        Optional<Message> message = retrieve(channel, target.messageId());
        if (message.isEmpty()) return new Result(Outcome.STALE, "message is gone");
        Optional<Button> button = enabledButton(message.get(), target.customId());
        if (button.isEmpty()) return new Result(Outcome.STALE, "button is gone or disabled");
        return pressFound(seat, message.get(), button.get());
    }

    public static Optional<Message> retrieve(MessageChannel channel, String messageId) {
        try {
            return Optional.ofNullable(channel.retrieveMessageById(messageId).complete());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public static Optional<Button> enabledButton(Message message, String customId) {
        return message.getComponentTree().findAll(Button.class).stream()
                .filter(button -> customId.equals(button.getCustomId()))
                .filter(button -> !button.isDisabled())
                .findFirst();
    }

    private static Result pressFound(SeatIdentity seat, Message message, Button button) {
        Member actor = AiActor.member(message.getGuild(), seat.userId(), seat.displayName());
        TestBedPress.Recorder recorder = new TestBedPress.Recorder();
        SyncMessage syncMessage = new SyncMessage(message);
        try {
            ButtonInteractionEvent event =
                    TestBedPress.standInEvent(syncMessage.proxy(), button, actor, recorder, repostTarget(seat));
            ButtonProcessor.processNow(event);
        } catch (RuntimeException e) {
            BotLogger.error("AI press of `" + button.getCustomId() + "` threw", e);
            return new Result(Outcome.ERROR, e.getClass().getSimpleName());
        }
        syncMessage.awaitPendingEdits(PENDING_EDIT_TIMEOUT);
        if (syncMessage.handlerFailed()) return new Result(Outcome.HANDLER_FAILED, "the button handler failed");
        if (recorder.modalId() != null) return new Result(Outcome.REJECTED, "the button opened a form");
        Optional<String> rejection = recorder.replies().stream()
                .filter(reply -> REJECTION_REPLIES.stream().anyMatch(reply::contains))
                .findFirst();
        return rejection
                .map(reply -> new Result(Outcome.REJECTED, reply))
                .orElseGet(() -> new Result(Outcome.PRESSED, "pressed `" + button.getLabel() + "`"));
    }

    @Nullable
    private static TestBedPress.Repost repostTarget(SeatIdentity seat) {
        if (seat.privateThreadId() == null || JdaService.jda == null) return null;
        MessageChannel thread = JdaService.jda.getThreadChannelById(seat.privateThreadId());
        return thread == null ? null : new TestBedPress.Repost(thread, seat.faction(), PRIVATE_PROMPT_HEADER);
    }
}
