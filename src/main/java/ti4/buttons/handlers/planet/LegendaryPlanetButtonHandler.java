package ti4.buttons.handlers.planet;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.explore.ExploreService;

@UtilityClass
class LegendaryPlanetButtonHandler {

    @ButtonHandler("garboziaAbilityExhaust_")
    static void garboziaAbilityExhaust(ButtonInteractionEvent event, Player player, Game game) {
        String planet = "garbozia";
        player.exhaustPlanetAbility(planet);
        ExploreService.explorePlanet(
                event, game.getTileFromPlanet(planet), planet, "INDUSTRIAL", player, true, game, 1, false);
    }
}
