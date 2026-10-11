package ti4.discord.interactions.commands.special;

import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.unit.GalvanizeService;

class Galvanize extends GameStateSubcommand {

    Galvanize() {
        super(Constants.GALVANIZE, "Use the Galvanize ability", true, true);
        markSuspicious();
    }

    @Override
    public void execute(net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent event) {
        GalvanizeService.postToggleGalvanizeTiles(getGame(), getPlayer());
    }
}
