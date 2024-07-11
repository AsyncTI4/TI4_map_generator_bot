package ti4.commands.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;

public class ReloadMapperObjects extends AdminSubcommandData {

    public ReloadMapperObjects() {
        super(Constants.RELOAD_MAPPER_OBJECTS, "Reload static objects in mapper");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Mapper.init();
    }
}
