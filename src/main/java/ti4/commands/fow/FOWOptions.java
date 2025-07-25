package ti4.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.option.FOWOptionService;

class FOWOptions extends GameStateSubcommand {

    public FOWOptions() {
        super(Constants.FOW_OPTIONS, "Change options for FoW game", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        FOWOptionService.offerFOWOptionButtons(getGame());
    }
}
