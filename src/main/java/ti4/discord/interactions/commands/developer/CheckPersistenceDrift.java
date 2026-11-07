package ti4.discord.interactions.commands.developer;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.executors.ExecutorServiceManager;
import ti4.game.persistence.DatabasePersistenceGate;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.GamePersistenceDriftCheck;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

class CheckPersistenceDrift extends Subcommand {

    private static final int EXECUTION_TIME_WARNING_THRESHOLD_SECONDS = 120;

    CheckPersistenceDrift() {
        super(
                "check_persistence_drift",
                "Compare every game file's revision with its database row and ManagedGame, and repair drift.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (DatabasePersistenceGate.isDisabled()) {
            MessageHelper.sendMessageToEventChannel(
                    event, "Database maintenance mode is on, so the database rows cannot be checked.");
            return;
        }
        if (!GameManager.isWarmupFinished()) {
            MessageHelper.sendMessageToEventChannel(
                    event, "ManagedGames are still being built, so try again once warmup has finished.");
            return;
        }
        MessageChannel channel = event.getChannel();
        MessageHelper.sendMessageToEventChannel(event, "Checking every game for persistence drift...");
        ExecutorServiceManager.runAsync(
                "check persistence drift", EXECUTION_TIME_WARNING_THRESHOLD_SECONDS, () -> report(channel));
    }

    private static void report(MessageChannel channel) {
        try {
            MessageHelper.sendMessageToChannel(channel, GamePersistenceDriftCheck.checkAndRepair());
        } catch (Exception e) {
            BotLogger.error("The persistence drift check failed.", e);
            MessageHelper.sendMessageToChannel(channel, "The persistence drift check failed. See the bot log.");
        }
    }
}
