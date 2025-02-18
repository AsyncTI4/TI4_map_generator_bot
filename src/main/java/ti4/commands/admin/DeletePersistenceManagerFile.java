package ti4.commands.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.json.PersistenceManager;

public class DeletePersistenceManagerFile extends Subcommand {

    DeletePersistenceManagerFile() {
        super("delete_pm_file", "Deletes a persistence manager file.");
        addOptions(new OptionData(OptionType.STRING, "file_name", "File name (e.g. TechSummaries.json").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String filename = event.getOption("file_name").getAsString();
        PersistenceManager.deleteJsonFile(filename);
    }
}
