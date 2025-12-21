package ti4.service.breakthrough;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.commands.tokens.AddTokenCommand;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.RegexHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;
import ti4.service.map.TokenPlanetService;
import ti4.service.planet.AddPlanetService;

@UtilityClass
public class StellarGenesisService {

    public static void serveAvernusButtons(Game game, Player player) {
        List<Tile> playerPlanetTiles = player.getPlanets().stream()
                .map(game::getTileFromPlanet)
                .filter(Objects::nonNull)
                .toList();
        Set<Tile> adjToPlanetTiles = playerPlanetTiles.stream()
                .flatMap(t -> FoWHelper.getAdjacentTiles(game, t.getPosition(), player, false).stream()
                        .map(game::getTileByPosition))
                .collect(Collectors.toSet());

        Predicate<Tile> nonHome = tile -> !tile.isHomeSystem(game);
        Predicate<Tile> nonHomeAndAdj = nonHome.and(adjToPlanetTiles::contains);
        List<Button> avernusLocations =
                ButtonHelper.getTilesWithPredicateForAction(player, game, "placeAvernus", nonHomeAndAdj, false);
        String message = "Choose a tile to place Avernus:";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, avernusLocations);
    }

    @ButtonHandler("placeAvernus_")
    public void resolvePlaceAvernus(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "placeAvernus_" + RegexHelper.posRegex(game);
        Matcher matcher = Pattern.compile(regex).matcher(buttonID);
        if (matcher.matches()) {
            String pos = matcher.group("pos");
            Tile tile = game.getTileByPosition(pos);
            AddTokenCommand.addToken(event, tile, Constants.AVERNUS, game);
            game.clearPlanetsCache();

            AddPlanetService.addPlanet(player, Constants.AVERNUS, game, event, false);
            player.getExhaustedPlanets().remove(Constants.AVERNUS);
            String message = player.getRepresentation() + " placed Avernus in "
                    + tile.getRepresentationForButtons(game, player) + ".";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            ButtonHelper.deleteMessage(event);
        }
    }

    @ButtonHandler("moveAvernus_")
    public void resolveMoveAvernus(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "moveAvernus_" + RegexHelper.posRegex(game, "destination");
        Matcher matcher = Pattern.compile(regex).matcher(buttonID);
        if (matcher.matches()) {
            String destination = matcher.group("destination");
            Tile tile = game.getTileByPosition(destination);
            TokenPlanetService.moveTokenPlanet(game, player, tile, Constants.AVERNUS);
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            List<Player> playersWithPds2 = ButtonHelper.tileHasPDS2Cover(player, game, tile.getPosition());
            if (playersWithPds2.contains(player)) {
                List<Button> spaceCannonButtons = StartCombatService.getSpaceCannonButtons(game, player, tile);
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        "If avernus had PDS on it, you can fire the PDS with this button if this is the appropriate time to do so.",
                        spaceCannonButtons);
            }
        }
    }
}
