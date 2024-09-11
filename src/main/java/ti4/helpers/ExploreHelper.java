package ti4.helpers;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.units.AddUnits;
import ti4.generator.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ExploreHelper {

    @ButtonHandler("resolveLocalFab_")
    public static void resolveLocalFabricators(String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        String planetName = buttonID.split("_")[1];
        String commOrTg;
        if (player.getCommodities() > 0) {
            player.setCommodities(player.getCommodities() - 1);
            commOrTg = Emojis.comm;
        } else if (player.getTg() > 0) {
            player.setTg(player.getTg() - 1);
            commOrTg = Emojis.tg;
        } else {
            ButtonHelper.addReaction(event, false, false, "Didn't have any Comms/TGs to spend, no mech placed", "");
            return;
        }
        new AddUnits().unitParsing(event, player.getColor(), game.getTile(AliasHandler.resolveTile(planetName)), "mech " + planetName, game);
        planetName = Mapper.getPlanet(planetName) == null ? "`error?`" : Mapper.getPlanet(planetName).getName();
        ButtonHelper.addReaction(event, false, false, "Spent a " + commOrTg + " for a Mech on " + planetName, "");
        ButtonHelper.deleteMessage(event);
        if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
            String pF = player.getFactionEmoji();
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), pF + " Spent a " + commOrTg + " for a Mech on " + planetName);
        }
    }

    @ButtonHandler("resolveVolatile_")
    public static void resolveVolatileFuelSource(String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        String planetID = StringUtils.substringAfter(buttonID, "_");
        String mechOrInfCheckMessage = ButtonHelper.mechOrInfCheck(planetID, game, player);
        boolean failed = mechOrInfCheckMessage.contains("Please try again.");

        if (!failed) {
            String message = player.getRepresentation() + " the " + mechOrInfCheckMessage + " Please gain one CC. Your current CCs are " + player.getCCRepresentation();
            game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
            List<Button> buttons = ButtonHelper.getGainCCButtons(player);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        }

        if (!failed && !event.getMessage().getContentRaw().contains("fragment")) {
            ButtonHelper.deleteMessage(event);
            if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " " + mechOrInfCheckMessage);
            }
        }
    }
}
