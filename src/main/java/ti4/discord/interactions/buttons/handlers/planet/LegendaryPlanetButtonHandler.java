package ti4.discord.interactions.buttons.handlers.planet;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.explore.ExploreService;

@UtilityClass
class LegendaryPlanetButtonHandler {

    @ButtonHandler("bozgarbiaAbilityExhaust_")
    static void bozgarbiaAbilityExhaust(ButtonInteractionEvent event, Player player, Game game) {
        String planet = "bozgarbia";
        player.exhaustPlanetAbility(planet);
        ExploreService.explorePlanet(
                event, game.getTileContainingPlanet(planet), planet, "INDUSTRIAL", player, true, game, 1, false);
    }
}
