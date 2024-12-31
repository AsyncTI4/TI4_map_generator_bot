package ti4.commands.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.image.Mapper;
import ti4.helpers.Constants;

class ReloadMapperObjects extends Subcommand {

    public ReloadMapperObjects() {
        super(Constants.RELOAD_MAPPER_OBJECTS, "Reload static objects in mapper");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Mapper.init();
    }
}
