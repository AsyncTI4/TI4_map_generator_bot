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
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.fow.PlanetTargetService;
import ti4.service.fow.PlanetTargetService.PlanetTargetSpec;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class VyserixLeaderHandler {

    public static void offerHeroAttachmentButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        String message = player.getRepresentation()
                + ", choose a non-home planet without a technology specialty to attach _Titles Are Silly_ to.";
        if (game.isFowMode()) {
            // Unlike most of these flows this leaks map inventory rather than ownership: which planets exist
            // and which carry a tech specialty, including on systems the player has never seen.
            buttons = PlanetTargetService.targetButtons(
                    game,
                    player,
                    PlanetTargetSpec.of(player.factionButtonChecker() + "vyserixHeroAttach")
                            .where(p -> !p.isHomePlanet()
                                    && !p.isFake()
                                    && p.getTechSpecialities().isEmpty()),
                    buttons);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
            return;
        }
        for (String planet : game.getPlanets()) {
            Planet planetInfo = game.getPlanetsInfo().get(planet);
            if (planetInfo == null
                    || planetInfo.isHomePlanet()
                    || planetInfo.isFake()
                    || game.getTileFromPlanet(planet) == null
                    || !planetInfo.getTechSpecialities().isEmpty()) {
                continue;
            }
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + "vyserixHeroAttach_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
    }

    @ButtonHandler("vyserixHeroAttach_")
    public static void resolveHeroAttach(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.replace("vyserixHeroAttach_", "");
        Tile tile = game.getTileFromPlanet(planet);
        Planet unitHolder = game.getPlanetsInfo().get(planet);
        // These were builder-only filters; a blind-typed target never passed through the list, so without
        // re-checking here the hero could attach all four specialties to a home planet or to one that
        // already has a specialty.
        if (tile == null
                || unitHolder == null
                || unitHolder.isHomePlanet()
                || unitHolder.isFake()
                || !unitHolder.getTechSpecialities().isEmpty()) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        tile.addToken("attachment_biotic.png", planet);
        tile.addToken("attachment_cybernetic.png", planet);
        tile.addToken("attachment_propulsion.png", planet);
        tile.addToken("attachment_warfare.png", planet);
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
