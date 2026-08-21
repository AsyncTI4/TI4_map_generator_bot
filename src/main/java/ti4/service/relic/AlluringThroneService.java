package ti4.service.relic;

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
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.tokens.AddTokenCommand;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.FoWHelper;
import ti4.helpers.RegexHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;
import ti4.service.map.TokenPlanetService;
import ti4.service.planet.AddPlanetService;
import ti4.service.turn.EndTurnService;

@UtilityClass
public class AlluringThroneService {
    private static final String ILLUSTRION = "illustrion";
    private static final String USE_ILLUSTRION = "useIllustrionLegendary_";

    public static void serveIllustrionButtons(Game game, Player player) {
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
        List<Button> illustrionLocations =
                ButtonHelper.getTilesWithPredicateForAction(player, game, "placeIllustrion", nonHomeAndAdj, false);
        String message = "Please choose which system you wish to place Illustrion in.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, illustrionLocations);
    }

    @ButtonHandler("placeIllustrion_")
    public void resolvePlaceIllustrion(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "placeIllustrion_" + RegexHelper.posRegex(game);
        Matcher matcher = Pattern.compile(regex).matcher(buttonID);
        if (matcher.matches()) {
            String pos = matcher.group("pos");
            Tile tile = game.getTileByPosition(pos);
            AddTokenCommand.addToken(event, tile, ILLUSTRION, game);
            game.clearPlanetsCache();

            AddPlanetService.addPlanet(player, ILLUSTRION, game, event, false);
            player.getExhaustedPlanets().remove(ILLUSTRION);
            String message = player.getRepresentation() + " placed Illustrion in "
                    + tile.getRepresentationForButtons(game, player) + ".";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            ButtonHelper.deleteMessage(event);
        }
    }

    @ButtonHandler("moveIllustrion_")
    public void resolveMoveIllustrion(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "moveIllustrion_" + RegexHelper.posRegex(game, "destination");
        Matcher matcher = Pattern.compile(regex).matcher(buttonID);
        if (matcher.matches()) {
            String destination = matcher.group("destination");
            Tile tile = game.getTileByPosition(destination);
            TokenPlanetService.moveTokenPlanet(game, player, tile, ILLUSTRION);
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            List<Player> playersWithPds2 = ButtonHelper.getPlayersWithPds2Cover(player, game, tile.getPosition());
            if (playersWithPds2.contains(player)) {
                List<Button> spaceCannonButtons = StartCombatService.getSpaceCannonButtons(game, player, tile);
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        "If Illustrion had PDS on it, you may fire the PDS with this button if this is the appropriate time to do so.",
                        spaceCannonButtons);
            }
        }
    }

    public static void offerIllustrionLegendaryAbility(Game game, Tile activeSystem, Player activePlayer) {
        if (game == null
                || activeSystem == null
                || activePlayer == null
                || activeSystem.getUnitHolderFromPlanet(ILLUSTRION) == null
                || activeSystem.getPlanetUnitHolders().stream()
                                .filter(Planet::isLegendary)
                                .count()
                        != 1) {
            return;
        }

        for (Player player : game.getRealPlayers()) {
            if (!player.getPlanets().contains(ILLUSTRION)
                    || player.getExhaustedPlanetsAbilities().contains(ILLUSTRION)
                    || player.getStrategicCC() < 1) {
                continue;
            }
            List<Button> buttons = List.of(
                    Buttons.red(
                            player.factionButtonChecker() + USE_ILLUSTRION + activeSystem.getPosition() + "|"
                                    + activePlayer.getFaction(),
                            "Use Alluring Throne"),
                    Buttons.gray(player.factionButtonChecker() + "deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", you may exhaust _Alluring Throne_ and spend 1 command token from your strategy pool to immediately end "
                            + activePlayer.getRepresentationUnfogged() + "'s turn.",
                    buttons);
        }
    }

    @ButtonHandler(USE_ILLUSTRION)
    public void useIllustrionLegendaryAbility(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(USE_ILLUSTRION.length()).split("\\|", 2);
        Tile activeSystem = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        Player activePlayer = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[1]) : null;
        if (activeSystem == null
                || activePlayer == null
                || !activeSystem.getPosition().equals(game.getActiveSystem())
                || activePlayer != game.getActivePlayer()
                || activeSystem.getUnitHolderFromPlanet(ILLUSTRION) == null
                || activeSystem.getPlanetUnitHolders().stream()
                                .filter(Planet::isLegendary)
                                .count()
                        != 1
                || !player.getPlanets().contains(ILLUSTRION)
                || player.getExhaustedPlanetsAbilities().contains(ILLUSTRION)
                || player.getStrategicCC() < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.exhaustPlanetAbility(ILLUSTRION);
        player.setStrategicCC(player.getStrategicCC() - 1);
        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "_Alluring Throne_");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation()
                        + " exhausted _Alluring Throne_ and spent 1 command token from their strategy pool to end "
                        + activePlayer.getRepresentationUnfogged() + "'s turn.");
        ButtonHelper.deleteMessage(event);
        EndTurnService.endTurnAndUpdateMap(event, game, activePlayer);
    }

    public static boolean illustrionFlagshipIgnoresAnomalies(Game game, Player player, Tile tile) {
        return game != null
                && player != null
                && tile != null
                && player.getPlanets().contains(ILLUSTRION)
                && tile.getSpaceUnitHolder().getUnitCount(UnitType.Flagship, player) > 0;
    }
}
