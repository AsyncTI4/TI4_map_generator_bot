package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.kairn;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ta.TaPromissoryHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ta.TaUnitHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.explore.ExploreService;

@UtilityClass
public class KairnPromissoryHandler {
    private static final String PN_ID = "thpnkairn";
    private static final String ATTACHMENT_TOKEN = "attachment_kairnoutpost.png";
    private static final String KAIRN_PN_ATTACH = "kairnPnAttach_";
    private static final String KAIRN_PN_EXPLORE = "kairnPnExplore_";

    public static void offerArchaeologicalOutpostButtons(
            GenericInteractionCreateEvent event, Player player, Game game) {
        TaPromissoryHandler.offerNonHomePlanetAttachButtons(
                event,
                player,
                game,
                PN_ID,
                ATTACHMENT_TOKEN,
                KAIRN_PN_ATTACH,
                ", there are no legal planets to attach _Archaeological Outpost_ to.",
                ", please choose a non-home planet to attach _Archaeological Outpost_ to.");
    }

    @ButtonHandler(KAIRN_PN_ATTACH)
    public static void resolveKairnPnAttach(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = TaPromissoryHandler.attachToNonHomePlanet(
                event, game, player, PN_ID, ATTACHMENT_TOKEN, buttonID, KAIRN_PN_ATTACH);
        if (tile == null) {
            return;
        }
        String planetName = buttonID.split("\\|", 2)[1];
        TaUnitHandler.offerTaMechDeploy(event, player, game, tile, planetName);
        ButtonHelper.deleteMessage(event);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + " attached _Archaeological Outpost_ to "
                        + Helper.getPlanetRepresentation(planetName, game) + ".");
    }

    public static void offerArchaeologicalOutpostExplore(Game game, Tile tile) {
        if (game == null || tile == null) {
            return;
        }

        for (Planet planet : tile.getPlanetUnitHolders()) {
            if (!planet.getAttachments().contains(ATTACHMENT_TOKEN)) {
                continue;
            }
            Player player = game.getPlayerThatControlsPlanet(planet.getName());
            if (player == null) {
                continue;
            }
            List<Button> buttons = new ArrayList<>();
            for (String trait : planet.getPlanetTypes()) {
                if (List.of(Constants.CULTURAL, Constants.HAZARDOUS, Constants.INDUSTRIAL)
                        .contains(trait)) {
                    buttons.add(Buttons.gray(
                            player.factionButtonChecker() + KAIRN_PN_EXPLORE + tile.getPosition() + "|"
                                    + planet.getName() + "|" + trait,
                            "Explore " + Helper.getPlanetRepresentation(planet.getName(), game) + " As " + trait,
                            ExploreEmojis.getTraitEmoji(trait)));
                }
            }

            if (!buttons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentation()
                                + ", a player activated a system containing _Archaeological Outpost_. Explore that planet.",
                        buttons);
            }
        }
    }

    @ButtonHandler(KAIRN_PN_EXPLORE)
    public static void resolveArchaeologicalOutpostExplore(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(KAIRN_PN_EXPLORE.length()).split("\\|", 3);
        Tile tile = parts.length == 3 && game != null ? game.getTileByPosition(parts[0]) : null;
        Planet planet = tile == null ? null : tile.getUnitHolderFromPlanet(parts[1]);
        if (player == null
                || tile == null
                || planet == null
                || !planet.getAttachments().contains(ATTACHMENT_TOKEN)
                || game.getPlayerThatControlsPlanet(planet.getName()) != player
                || !planet.getPlanetTypes().contains(parts[2])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        ExploreService.explorePlanet(event, tile, planet.getName(), parts[2], player, false, game, 1, true);
        ButtonHelper.deleteMessage(event);
    }
}
