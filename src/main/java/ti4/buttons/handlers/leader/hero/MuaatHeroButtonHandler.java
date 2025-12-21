package ti4.buttons.handlers.leader.hero;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.leader.MuaatHeroService;

@UtilityClass
class MuaatHeroButtonHandler {

    @ButtonHandler("novaSeed_")
    public static void novaSeed(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        MuaatHeroService.secondHalfOfNovaSeed(player, event, game.getTileByPosition(buttonID.split("_")[1]), game);
        ButtonHelper.deleteTheOneButton(event);
    }
}
