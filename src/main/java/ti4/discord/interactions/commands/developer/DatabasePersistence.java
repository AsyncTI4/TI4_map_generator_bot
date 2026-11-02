package ti4.discord.interactions.commands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.message.MessageHelper;
import ti4.service.persistence.DatabasePersistenceGate;
import ti4.service.persistence.GameDatabaseReconciler;
import ti4.spring.service.deploy.ActiveLeaseService;

class DatabasePersistence extends Subcommand {

    private static final String OPTION_MODE = "mode";

    DatabasePersistence() {
        super("database_persistence", "Temporarily pause database-backed features during maintenance.");
        addOptions(
                new OptionData(OptionType.STRING, OPTION_MODE, "Set database-backed features on, off, or show status.")
                        .setRequired(true)
                        .addChoice("on", "on")
                        .addChoice("off", "off")
                        .addChoice("status", "status"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String mode = event.getOption(OPTION_MODE).getAsString();
        String catchUpMessage = "";
        switch (mode) {
            case "on" -> catchUpMessage = enablePersistence();
            case "off" -> DatabasePersistenceGate.setDisabled(true);
            case "status" -> {}
            default -> MessageHelper.sendMessageToEventChannel(event, "Unknown mode: `" + mode + "`");
        }

        MessageHelper.sendMessageToEventChannel(event, DatabasePersistenceGate.statusMessage() + catchUpMessage);
    }

    private static String enablePersistence() {
        boolean wasDisabled = DatabasePersistenceGate.isDisabled();
        DatabasePersistenceGate.setDisabled(false);
        if (!wasDisabled) return "";
        return "\n" + queueCatchUpReconciliation();
    }

    private static String queueCatchUpReconciliation() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) {
            return "This process is not running scheduled work, so no catch-up reconciliation was queued."
                    + " The nightly reconciliation will catch up on game syncs skipped during maintenance.";
        }
        if (GameDatabaseReconciler.queueCatchUpReconciliation()) {
            return "Queued a full database reconciliation after any current run to catch up on game syncs skipped during maintenance.";
        }
        return "No catch-up reconciliation was queued because the bot is shutting down.";
    }
}
