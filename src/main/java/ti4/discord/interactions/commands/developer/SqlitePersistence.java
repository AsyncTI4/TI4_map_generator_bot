package ti4.discord.interactions.commands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.message.MessageHelper;
import ti4.service.persistence.SqlitePersistenceGate;

class SqlitePersistence extends Subcommand {

    private static final String OPTION_MODE = "mode";

    SqlitePersistence() {
        super("sqlite_persistence", "Temporarily no-op auxiliary SQLite persistence during PostgreSQL migration.");
        addOptions(new OptionData(OptionType.STRING, OPTION_MODE, "Whether SQLite-backed auxiliary persistence is on.")
                .setRequired(true)
                .addChoice("on", "on")
                .addChoice("off", "off")
                .addChoice("status", "status"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String mode = event.getOption(OPTION_MODE).getAsString();
        switch (mode) {
            case "on" -> SqlitePersistenceGate.setDisabled(false);
            case "off" -> SqlitePersistenceGate.setDisabled(true);
            case "status" -> {}
            default -> MessageHelper.sendMessageToEventChannel(event, "Unknown mode: `" + mode + "`");
        }

        MessageHelper.sendMessageToEventChannel(event, SqlitePersistenceGate.statusMessage());
    }
}
