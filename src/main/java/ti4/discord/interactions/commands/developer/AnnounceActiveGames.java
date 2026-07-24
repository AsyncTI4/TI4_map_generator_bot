package ti4.discord.interactions.commands.developer;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.commands.Subcommand;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.logging.BotLogger;

class AnnounceActiveGames extends Subcommand {

    private static final String OPTION_MESSAGE = "message";
    private static final int DISCORD_MESSAGE_LIMIT = 2000;
    private static final long SEND_INTERVAL_MILLISECONDS = 250;

    private final AtomicBoolean announcementInProgress = new AtomicBoolean();

    AnnounceActiveGames() {
        super("announce", "Send a message to the table talk channel of every active game.");
        addOptions(new OptionData(OptionType.STRING, OPTION_MESSAGE, "Message to send to each active game channel.")
                .setRequired(true)
                .setMaxLength(DISCORD_MESSAGE_LIMIT));
    }

    @Override
    public boolean isEphemeral(SlashCommandInteractionEvent event) {
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String message = event.getOption(OPTION_MESSAGE).getAsString();
        if (message.isBlank()) {
            event.getHook()
                    .editOriginal("Announcement message cannot be blank.")
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        List<TextChannel> activeChannels = GameManager.getManagedGames().stream()
                .filter(ManagedGame::isActive)
                .map(ManagedGame::getTableTalkChannel)
                .filter(Objects::nonNull)
                .toList();

        List<TextChannel> channels = activeChannels.stream()
                .filter(AnnounceActiveGames::canSendAnnouncement)
                .toList();
        int skippedChannels = activeChannels.size() - channels.size();

        if (!announcementInProgress.compareAndSet(false, true)) {
            event.getHook()
                    .editOriginal("A developer announcement is already in progress.")
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        event.getHook()
                .editOriginal("Broadcasting announcement to " + channels.size()
                        + " active game channels with a " + SEND_INTERVAL_MILLISECONDS
                        + " ms interval. Minimum estimated duration: " + formatDuration(channels.size()) + ". Skipped "
                        + skippedChannels + " channels without send permission.")
                .queue(Consumers.nop(), BotLogger::catchRestError);

        sendAnnouncement(channels, message, event.getHook(), skippedChannels, 0, 0, 0);
    }

    private void sendAnnouncement(
            List<TextChannel> channels,
            String message,
            InteractionHook hook,
            int skippedChannels,
            int index,
            int sentChannels,
            int failedChannels) {
        if (index >= channels.size()) {
            announcementInProgress.set(false);
            String result = "Developer announcement complete: " + sentChannels + " sent, " + failedChannels
                    + " failed, " + skippedChannels + " skipped.";
            BotLogger.info(result);
            hook.editOriginal(result).queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        TextChannel channel = channels.get(index);
        channel.sendMessage(message)
                .queueAfter(
                        SEND_INTERVAL_MILLISECONDS,
                        TimeUnit.MILLISECONDS,
                        _ -> sendAnnouncement(
                                channels, message, hook, skippedChannels, index + 1, sentChannels + 1, failedChannels),
                        error -> {
                            BotLogger.error(
                                    "Failed to send developer announcement to " + channel.getName() + ".", error);
                            sendAnnouncement(
                                    channels,
                                    message,
                                    hook,
                                    skippedChannels,
                                    index + 1,
                                    sentChannels,
                                    failedChannels + 1);
                        });
    }

    private static boolean canSendAnnouncement(TextChannel channel) {
        try {
            if (channel.canTalk()) return true;
            BotLogger.warning("Skipping developer announcement for " + channel.getAsMention()
                    + " because the bot cannot send messages there.");
        } catch (Exception error) {
            BotLogger.error("Failed to check developer announcement permissions for " + channel.getName() + ".", error);
        }
        return false;
    }

    private static String formatDuration(int channelCount) {
        long totalSeconds = (channelCount * SEND_INTERVAL_MILLISECONDS + 999) / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes == 0 ? seconds + "s" : minutes + "m " + seconds + "s";
    }
}
