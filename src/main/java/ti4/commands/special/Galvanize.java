package ti4.commands.special;

import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.GalvanizeService;

public class Galvanize extends GameStateSubcommand {

    public Galvanize() {
        super(Constants.GALVANIZE, "Use the Galvanize ability", true, true);
    }

    @Override
    public void execute(net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent event) {
        GalvanizeService.postToggleGalvanizeTiles(getGame(), getPlayer());
    }
}
