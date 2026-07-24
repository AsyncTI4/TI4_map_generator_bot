package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.vyserix;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class VyserixLeaderHandler {

    public static void offerHeroAttachmentButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : game.getPlanets()) {
            Planet planetInfo = game.getPlanetsInfo().get(planet);
            if (planetInfo == null
                    || planetInfo.isHomePlanet()
                    || !planetInfo.getTechSpecialities().isEmpty()) {
                continue;
            }
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + "vyserixHeroAttach_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
        }
        String msg = player.getRepresentation()
                + ", choose a non-home planet without a technology specialty to attach _Titles Are Silly_ to.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("vyserixHeroAttach_")
    public static void resolveHeroAttach(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.replace("vyserixHeroAttach_", "");
        Tile tile = game.getTileFromPlanet(planet);
        UnitHolder unitHolder = game.getPlanetsInfo().get(planet);
        if (tile == null || unitHolder == null) return;
        tile.addToken("attachment_vyserixhero.png", planet);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " attached _Titles Are Silly_ to "
                        + Helper.getPlanetRepresentation(planet, game)
                        + ". It now counts as having all four technology specialties.");
        if (player.getPlanets().contains(planet)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getFactionEmoji() + " placed 1 PDS on " + Helper.getPlanetRepresentation(planet, game)
                            + " due to _Titles Are Silly_. This is optional but was done automatically.");
            AddUnitService.addUnits(event, tile, game, player.getColor(), "pds " + planet);
        }
        ButtonHelper.deleteMessage(event);
    }
}
