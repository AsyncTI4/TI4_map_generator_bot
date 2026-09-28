package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Thrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class ThronesAbilityHandler {
    private static final Set<String> THRONE_PLANETS = Set.of("cineron", "gyraxis", "lethara", "skarnath");
    private static final String CHOOSE_THRONE = "chooseThroneForAbility_";

    public static boolean isThronePlanet(String planetName) {
        return THRONE_PLANETS.contains(planetName);
    }

    public static boolean tracesOfRuinIsActive(Game game) {
        return game.getRealPlayers().stream().anyMatch(player -> player.hasAbility("traces_of_ruin"));
    }

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

        List<MessageEmbed> thronesEmbed = new ArrayList<>();
        for (String planet : THRONE_PLANETS) {
            if (game.getTileFromPlanet(planet) != null) {
                continue;
            }

            thronesEmbed.add(Mapper.getPlanet(planet).getRepresentationEmbed());
        }

        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose the Throne planet to place in your home system.",
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

        ButtonHelper.deleteMessage(event);
    }
}
