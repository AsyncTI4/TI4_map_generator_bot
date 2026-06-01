package ti4.discord.interactions.commands.developer;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.commands.Subcommand;
import ti4.executors.ExecutorServiceManager;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.logging.BotLogger;

class AnnounceActiveGames extends Subcommand {

    private static final String OPTION_MESSAGE = "message";
    private static final int DISCORD_MESSAGE_LIMIT = 2000;
    private static final int BATCH_SIZE = 100;

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

        List<TextChannel> channels = GameManager.getManagedGames().stream()
                .filter(ManagedGame::isActive)
                .map(ManagedGame::getTableTalkChannel)
                .filter(Objects::nonNull)
                .toList();
        event.getHook()
                .editOriginal("Queued announcement for " + channels.size() + " active game channels.")
                .queue(Consumers.nop(), BotLogger::catchRestError);

        ExecutorServiceManager.runAsync(
                "Developer active game announcement", 300, () -> sendAnnouncement(channels, message));
    }

    private static void sendAnnouncement(List<TextChannel> channels, String message) {
        for (int i = 0; i < channels.size(); i++) {
            TextChannel channel = channels.get(i);
            channel.sendMessage(message)
                    .queueAfter(
                            i / BATCH_SIZE,
                            TimeUnit.SECONDS,
                            Consumers.nop(),
                            error -> BotLogger.error(
                                    "Failed to send developer announcement to " + channel.getName() + ".", error));
        }
    }
}
