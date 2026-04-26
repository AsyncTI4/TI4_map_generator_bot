package ti4.discord.interactions.buttons.handlers.faction.discordantstars.zelian;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.service.leader.ZelianHeroService;

@UtilityClass
class ZelianHeroButtonHandler {

    @ButtonHandler("celestialImpact_")
    public static void celestialImpact(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ZelianHeroService.secondHalfOfCelestialImpact(
                player, event, game.getTileByPosition(buttonID.split("_")[1]), game);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }
}
