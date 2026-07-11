package ti4.discord.interactions.buttons.handlers.faction.base.l1z1x;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;

@UtilityClass
class L1Z1XButtonHandler {

    @ButtonHandler("assimilate_")
    public static void assimilate(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        UnitHolder uH = game.getPlanet(buttonID.split("_")[1]);
        ButtonHelperModifyUnits.infiltratePlanet(player, game, uH, event);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }
}
