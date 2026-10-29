package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.thrones;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class ThronesLeadersHandler {
    private static final String CHOOSE_THRONE = "chooseThronePlanetForHero_";
    private static final List<String> THRONE_PLANETS = List.of("cineron", "gyraxis", "lethara", "skarnath");
    private static final String DONE_CHOOSING_THRONE = "doneChoosingThronePlanets";

    // Veythros
    public static boolean veythrosIgnoresAnomalies(Game game, Player player) {
        return game != null && player != null && game.playerHasLeaderUnlockedOrAlliance(player, "thronescommander");
    }

    // Seraphane the Eternal
    public static void getUnplacedThronePlanetButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        List<Button> buttons = new ArrayList<>();

        for (String planet : THRONE_PLANETS) {
            if (game.getTileFromPlanet(planet) != null) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + CHOOSE_THRONE + planet,
                    "Place " + Mapper.getPlanet(planet).getName(),
                    FactionEmojis.thrones));
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + DONE_CHOOSING_THRONE, "Done Choosing Thrones"));

        List<MessageEmbed> thronesEmbed = new ArrayList<>();
        for (String planet : THRONE_PLANETS) {
            if (game.getTileFromPlanet(planet) != null) {
                continue;
            }

            thronesEmbed.add(Mapper.getPlanet(planet).getRepresentationEmbed());
        }

        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose the 2 Throne planets to place in your home system.",
                thronesEmbed,
                buttons);
    }

    @ButtonHandler(CHOOSE_THRONE)
    public static void placeChosenThronePlanetInHs(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String planet = buttonID.replace(CHOOSE_THRONE, "");
        if (planet == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile homeSystem = player.getHomeSystemTile();
        String tokenID = Mapper.getTokenID(planet);
        if (!THRONE_PLANETS.contains(planet)
                || homeSystem == null
                || tokenID == null
                || game.getTileFromPlanet(planet) != null) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        homeSystem.addToken(tokenID, Constants.SPACE);
        Helper.addTokenPlanetToTile(game, homeSystem, planet);
        game.clearPlanetsCache();
        player.addPlanet(planet);

        String planetName = Mapper.getPlanet(planet).getName();
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " placed " + planetName
                        + " in their home system and gained control of it.");

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(DONE_CHOOSING_THRONE)
    public static void resolveThronesHeroFinalStep(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        Tile homeSystem = player.getHomeSystemTile();

        if (homeSystem != null) {
            for (Planet planet : homeSystem.getPlanetUnitHolders()) {
                player.refreshPlanet(planet.getName());
            }
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Readied all planets in your home system.");

        ButtonHelper.deleteMessage(event);
    }
}
