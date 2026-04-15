package ti4.discord.interactions.buttons.handlers.faction.discordantstars.dihmohn;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.Helper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.leader.PurgeHeroService;

@UtilityClass
public class DihmohnButtonHandler {

    @ButtonHandler("dsdihmy_")
    public static void dihmohnYellowTech(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        ButtonHelperFactionSpecific.resolveImpressmentPrograms(buttonID, event, game, player);
    }

    @ButtonHandler("purgeDihmohnHero")
    public static void purgeDihmohnHero(ButtonInteractionEvent event, Player player, Game game) { // TODO: add service
        PurgeHeroService.purgeHeroPreamble(event, player, game, "dihmohnhero", "Verrisus Ypru, the Dih-Mohn");
        ButtonHelperHeroes.resolvDihmohnHero(game);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + " sustained all ships in the active system. Reminder that your ships cannot be destroyed this combat round.");
    }

    @ButtonHandler("dihmohnfs_")
    public static void resolveDihmohnFlagship(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getRepresentation()
                        + " is using the Maximus (the Dih-Mohn flagship) to produce units. They may produce up to 2 units with a combined cost of 4.");
        String pos = buttonID.replace("dihmohnfs_", "");
        List<Button> buttons =
                Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), "muaatagent", "place");
        String message = player.getRepresentation() + ", please use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
