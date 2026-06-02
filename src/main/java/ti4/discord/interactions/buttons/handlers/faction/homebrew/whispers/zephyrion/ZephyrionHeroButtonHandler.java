package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.zephyrion;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.leader.PlayHeroService;

@UtilityClass
public class ZephyrionHeroButtonHandler {

    @ButtonHandler("zephHeroRes_")
    public static void resolveZephyrionHero(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("zephHeroRes_", "");
        String unitTypeString = buttonID.split("_")[1].toLowerCase();

        Leader hero = player.getLeader("zephyrionhero").orElse(null);
        if (hero == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " could not find Monturak Homotol, the Zephyrion hero. Please resolve manually.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        PlayHeroService.removeLeader(game, player, hero);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " resolved Monturak Homotol, the Zephyrion hero. Monturak Homotol has been purged.");

        if ("flagship".equalsIgnoreCase(unitTypeString) || "warsun".equalsIgnoreCase(unitTypeString)) {
            Integer poIndex = game.addCustomPO("Zephyrion Hero", 1);
            game.scorePublicObjective(player.getUserID(), poIndex);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " destroyed a " + StringUtils.capitalize(unitTypeString)
                            + " with a bounty token and gained 1 victory point.");
        } else {
            List<Button> buttons = Helper.getTileWithShipsPlaceUnitButtons(player, game, unitTypeString, "place");
            buttons.add(Buttons.red("deleteButtons", "Done"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", use the buttons to place " + StringUtils.capitalize(unitTypeString)
                            + "s in systems that contain your ships.",
                    buttons);
        }

        ButtonHelper.deleteMessage(event);
    }
}
