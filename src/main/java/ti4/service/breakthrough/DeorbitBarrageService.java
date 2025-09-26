package ti4.service.breakthrough;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.RegexHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.regex.RegexService;

@UtilityClass
public class DeorbitBarrageService {

    private String deorbitRep(boolean includeCardText) {
        return Mapper.getBreakthrough("saarbt").getRepresentation(includeCardText);
    }

    private List<Planet> getAllPlanetsInRange(Game game, Player player) {
        Predicate<Tile> asteroidWithUnit = Tile.tileHasPlayerShips(player).and(tile -> tile.isAsteroidField());
        List<Tile> asteroids =
                game.getTileMap().values().stream().filter(asteroidWithUnit).toList();

        List<Planet> eligibleTargets = asteroids.stream()
                .map(Tile::getPosition)
                .flatMap(pos -> FoWHelper.getAdjacentTiles(game, pos, player, false).stream())
                .flatMap(pos -> FoWHelper.getAdjacentTiles(game, pos, player, false).stream())
                .collect(Collectors.toSet())
                .stream()
                .map(game::getTileByPosition)
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .filter(Planet::hasUnits)
                .toList();
        return eligibleTargets;
    }

    public void postInitialButtons(Game game, Player player) {
        Set<String> colorIDsInRange = getAllPlanetsInRange(game, player).stream()
                .flatMap(planet -> planet.getUnitColorsOnHolder().stream())
                .collect(Collectors.toSet());
        List<Button> buttons = new ArrayList<>();
        for (String colorID : colorIDsInRange) {
            Player p2 = game.getPlayerFromColorOrFaction(colorID);
            if (p2 == null || p2.is(player)) continue;

            buttons.add(Buttons.red("deorbitBarrageTarget_" + p2.getFaction(), null, p2.fogSafeEmoji()));
        }
        String msg = player.getRepresentation() + " Choose a player whose planet you want to target with "
                + deorbitRep(true);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("deorbitBarrageTarget_")
    private static void deorbitBarrageStep1(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "deorbitBarrageTarget_" + RegexHelper.factionRegex(game);
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String target = Mapper.getColorID(matcher.group("faction"));
            String prefixID = player.finChecker() + "deorbitBarragePlanet_";
            List<Button> buttons = getAllPlanetsInRange(game, player).stream()
                    .filter(planet -> planet.getUnitCount(target) > 0)
                    .map(pl -> Buttons.gray(prefixID + pl.getName(), "Target " + pl.getName()))
                    .toList();

            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(), "Choose a planet to target:", buttons);
            ButtonHelper.deleteMessage(event);
        });
    }

    @ButtonHandler("deorbitBarragePlanet_")
    private static void deorbitBarrageStep2(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "deorbitBarragePlanet_" + RegexHelper.planetNameRegex(game, "planet");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String msg = "Target is " + Helper.getPlanetRepresentationPlusEmoji(matcher.group("planet"))
                    + "\nPlease yell at jazz and resolve manually ";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            ButtonHelper.deleteMessage(event);
        });
    }
}
