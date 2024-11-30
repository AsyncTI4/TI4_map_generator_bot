package ti4.commands2.planet;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.planet.PlanetInfoService;

class PlanetInfo extends GameStateSubcommand {

    public PlanetInfo() {
        super(Constants.PLANET_INFO, "Sends list of owned planets to your Cards-Info thread", false, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        PlanetInfoService.sendPlanetInfo(getPlayer());
    }
}
