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
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.RegexHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.regex.RegexService;

@UtilityClass
public class DeorbitBarrageService {

    private String deorbitRep(boolean includeCardText) {
        return Mapper.getBreakthrough("saarbt").getRepresentation(includeCardText);
    }

    private List<Planet> getAllPlanetsInRange(Game game, Player player) {
        Predicate<Tile> asteroidWithUnit = Tile.tileHasPlayerShips(player).and(Tile::isAsteroidField);
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
        Player player2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String target = Mapper.getColorID(matcher.group("faction"));
            String prefixID = player.finChecker() + "deorbitBarragePlanet_";
            List<Button> buttons = getAllPlanetsInRange(game, player).stream()
                    .filter(planet -> planet.getUnitCount(player2.getColorID()) > 0)
                    .map(pl -> Buttons.gray(prefixID + pl.getName(), "Target " + pl.getName()))
                    .toList();

            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(), "Choose a planet to target:", buttons);
            ButtonHelper.deleteMessage(event);
        });
    }

    @ButtonHandler("deorbitBarragePlanet_")
    private static void deorbitBarrageStep2(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        String planet = buttonID.split("_")[1];
        for (int x = 0; x < Helper.getPlayerResourcesAvailable(player, game) + player.getTg() + 1; x++) {
            buttons.add(Buttons.gray("deorbitBarrageResource_" + planet + "_" + x, "" + x));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                "Choose how many resources you would like to spend (can spend tgs)",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("deorbitBarrageResource_")
    private static void deorbitBarrageStep3(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        String planet = buttonID.split("_")[1];
        int resources = Integer.parseInt(buttonID.split("_")[2]);
        Player p2 = game.getPlanetOwner(planet);
        String planetRep = Helper.getPlanetRepresentation(planet, game);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " will target " + planetRep + " and spend " + resources
                        + " resources to roll " + resources + " dice hitting on a 4+");
        ButtonHelper.deleteMessage(event);
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        int amount = resources;
        int hits = 0;
        if (amount > 0) {
            StringBuilder msg = new StringBuilder(FactionEmojis.Saar + " rolled ");
            for (int x = 0; x < amount; x++) {
                Die d1 = new Die(4);
                msg.append(d1.getResult()).append(", ");
                if (d1.isSuccess()) {
                    hits++;
                }
            }
            msg = new StringBuilder(msg.substring(0, msg.length() - 2) + "\n Total hits were " + hits);
            // bombard msg
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg.toString());
            if (hits > 0) {
                if (p2.hasAbility("data_recovery")) {
                    ButtonHelperAbilities.dataRecovery(p2, game, event, "dataRecovery_" + player.getColor());
                }
            }
            buttons.add(Buttons.red(
                    "getDamageButtons_" + game.getTileFromPlanet(planet).getPosition() + "_bombardment",
                    "Assign Hit" + (hits == 1 ? "" : "s")));
            MessageHelper.sendMessageToChannelWithButtons(
                    game.isFowMode() ? p2.getCorrectChannel() : event.getMessageChannel(),
                    p2.getRepresentation() + ", please assign the hits" + (hits == 1 ? "" : "s") + ".",
                    buttons);
            buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
            Button DoneExhausting = Buttons.red("finishComponentAction_spitItOut", "Done Exhausting Planets");
            buttons.add(DoneExhausting);
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(), "Use Buttons to Pay For The Rolled Dice", buttons);
        }
    }
}
