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
    private static final List<String> THRONE_PLANETS = List.of("cineron", "gyraxis", "lethara", "skarnath");
    private static final String CHOOSE_THRONE = "chooseThroneForAbility_";

    public static boolean isThronePlanet(String planetName) {
        return THRONE_PLANETS.contains(planetName);
    }

    public static boolean tracesOfRuinIsActive(Game game) {
        return game.getRealPlayers().stream().anyMatch(player -> player.hasAbility("traces_of_ruin"));
    }

    public static void getUnplacedThronePlanetButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        sendUnplacedThronePlanetButtons(
                event,
                game,
                player,
                CHOOSE_THRONE,
                ", please choose the Throne planet to place in your home system.",
                List.of());
    }

    static void sendUnplacedThronePlanetButtons(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            String choosePrefix,
            String prompt,
            List<Button> extraButtons) {
        if (game == null || player == null) {
            return;
        }
        List<String> unplaced = THRONE_PLANETS.stream()
                .filter(planet -> game.getTileFromPlanet(planet) == null)
                .toList();
        List<Button> buttons = new ArrayList<>();
        for (String planet : unplaced) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + choosePrefix + planet,
                    "Place " + Mapper.getPlanet(planet).getName(),
                    FactionEmojis.thrones));
        }
        buttons.addAll(extraButtons);
        List<MessageEmbed> thronesEmbed = unplaced.stream()
                .map(planet -> Mapper.getPlanet(planet).getRepresentationEmbed())
                .toList();
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                event.getMessageChannel(), player.getRepresentation() + prompt, thronesEmbed, buttons);
    }

    static boolean placeThronePlanetInHomeSystem(
            ButtonInteractionEvent event, Game game, Player player, String planet) {
        if (game == null || player == null) {
            return false;
        }
        Tile homeSystem = player.getHomeSystemTile();
        String tokenID = Mapper.getTokenID(planet);
        if (!THRONE_PLANETS.contains(planet)
                || homeSystem == null
                || tokenID == null
                || game.getTileFromPlanet(planet) != null) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return false;
        }
        homeSystem.addToken(tokenID, Constants.SPACE);
        Helper.addTokenPlanetToTile(game, homeSystem, planet);
        game.clearPlanetsCache();
        player.addPlanet(planet);
        return true;
    }

    static void announceThronePlanetPlacement(ButtonInteractionEvent event, Player player, String planet) {
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " placed "
                        + Mapper.getPlanet(planet).getName() + " in their home system and gained control of it.");
    }

    @ButtonHandler(CHOOSE_THRONE)
    public static void placeChosenThronePlanetInHs(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planet = buttonID.replace(CHOOSE_THRONE, "");
        if (placeThronePlanetInHomeSystem(event, game, player, planet)) {
            announceThronePlanetPlacement(event, player, planet);
            ButtonHelper.deleteMessage(event);
        }
    }
}
