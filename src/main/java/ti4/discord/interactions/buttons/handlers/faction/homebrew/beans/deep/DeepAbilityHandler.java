package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.deep;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.emoji.ExploreEmojis;

@UtilityClass
public class DeepAbilityHandler {

    public static void offerEnergySiphon(Player player) {
        if (!player.hasAbility("energy_siphon")) {
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", if you purged this explore for its effect, you may gain one command token due to _Energy Siphon_.",
                ButtonHelper.getGainCCButtons(player));
    }

    public static void offerDarkMatterLink(Player player, Tile tile) {
        if (!player.hasAbility("dark_matter_link")) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "offerDarkMatterLinkChoices_" + tile.getPosition(),
                "Place a Frontier Token",
                ExploreEmojis.Frontier));
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", after resolving this frontier explore, you may place a frontier token in a different system containing no planets and no frontier token.",
                buttons);
    }

    @ButtonHandler("offerDarkMatterLinkChoices_")
    public static void offerDarkMatterLinkChoices(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String tilePos = buttonID.replace("offerDarkMatterLinkChoices_", "");
        Tile tile = game.getTileByPosition(tilePos);
        List<Button> buttons = new ArrayList<>();
        for (Tile eligibleTile : game.getTileMap().values()) {
            if (tile == null
                    || tile.getPosition().equals(eligibleTile.getPosition())
                    || eligibleTile.getTileModel().isHyperlane()
                    || !eligibleTile.getPlanetUnitHolders().isEmpty()
                    || eligibleTile
                            .getSpaceUnitHolder()
                            .getTokenList()
                            .contains(Mapper.getTokenID(Constants.FRONTIER))) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "echoPlaceFrontier_" + eligibleTile.getPosition(),
                    eligibleTile.getRepresentationForButtons(game, player)));
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + ", there are no eligible systems in which to place a frontier token.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        buttons.add(Buttons.red("deleteButtons", "Done"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please select the system in which to place the frontier token.",
                buttons);

        ButtonHelper.deleteMessage(event);
    }
}
