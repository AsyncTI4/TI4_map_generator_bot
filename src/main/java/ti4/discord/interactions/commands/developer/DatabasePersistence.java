package ti4.discord.interactions.commands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.message.MessageHelper;
import ti4.service.persistence.DatabasePersistenceGate;

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
        switch (mode) {
            case "on" -> DatabasePersistenceGate.setDisabled(false);
            case "off" -> DatabasePersistenceGate.setDisabled(true);
            case "status" -> {}
            default -> MessageHelper.sendMessageToEventChannel(event, "Unknown mode: `" + mode + "`");
        }

        MessageHelper.sendMessageToEventChannel(event, DatabasePersistenceGate.statusMessage());
    }
}
