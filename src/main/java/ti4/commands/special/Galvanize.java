package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.unit.GalvanizeService;

class Galvanize extends GameStateSubcommand {

    Galvanize() {
        super(Constants.GALVANIZE, "Use the Galvanize ability", true, true);
    }

    @Override
    public void execute(net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent event) {
        GalvanizeService.postToggleGalvanizeTiles(getGame(), getPlayer());
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
