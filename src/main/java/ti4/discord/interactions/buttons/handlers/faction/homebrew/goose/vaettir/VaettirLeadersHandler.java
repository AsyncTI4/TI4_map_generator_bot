package ti4.discord.interactions.buttons.handlers.faction.homebrew.goose.vaettir;

import java.util.ArrayList;
import java.util.Comparator;
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
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.service.RemoveCommandCounterService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class VaettirLeadersHandler {
    private static final String HERO_RETURN = "vaettirHeroReturn_";
    private static final String HERO_DONE = "vaettirHeroDone";

    public static void resolveCommander(Game game) {
        for (Player player : game.getRealPlayers()) {
            if (!game.playerHasLeaderUnlockedOrAlliance(player, "vaettircommander")) {
                continue;
            }
            int highestTraitCount = VaettirAbilityHandler.getRootTypes().stream()
                    .mapToInt(trait -> countControlledPlanetsWithTrait(game, player, trait))
                    .max()
                    .orElse(0);
            int commodities = highestTraitCount / 2;
            if (commodities < 1) {
                continue;
            }
            int before = player.getCommodities();
            player.gainCommodities(commodities);
            int gained = player.getCommodities() - before;
            ButtonHelperStats.afterGainCommsChecks(game, player, gained);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " gained " + gained + " commodit" + (gained == 1 ? "y" : "ies")
                            + " with _The Hidden Branches, the Vaettir commander_.");
        }
    }

    public static void startHero(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Button> returnButtons = getHeroReturnButtons(game, player);
        List<Button> extraButtons = List.of(Buttons.red(player.factionButtonChecker() + HERO_DONE, "Done"));
        List<Button> buttons = returnButtons.size() <= 24
                ? appendButtons(returnButtons, extraButtons)
                : NewStuffHelper.buttonPagination(
                        returnButtons, extraButtons, player.factionButtonChecker() + HERO_RETURN, 25, 0, false);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", return any number of your command tokens from systems containing Root tokens.",
                buttons);
    }

    @ButtonHandler(HERO_RETURN)
    public static void returnHeroCommandToken(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (NewStuffHelper.turnPage(
                event,
                buttonID,
                player.getRepresentationNoPing()
                        + ", return any number of your command tokens from systems containing Root tokens.",
                () -> getHeroReturnButtons(game, player),
                List.of(Buttons.red(player.factionButtonChecker() + HERO_DONE, "Done")),
                player.factionButtonChecker() + HERO_RETURN)) {
            return;
        }
        Tile tile = game.getTileByPosition(buttonID.substring(HERO_RETURN.length()));
        if (tile == null || !CommandCounterHelper.hasCC(player, tile) || !containsRootToken(tile)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        RemoveCommandCounterService.fromTile(player.getColor(), tile, game);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " returned a command token from "
                        + tile.getRepresentationForButtons(game, player)
                        + " with _The Roused Grove, the Vaettir hero_.");
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(HERO_DONE)
    public static void finishHero(ButtonInteractionEvent event, Game game, Player player) {
        List<Planet> rootedPlanets = player.getPlanets().stream()
                .map(game::getUnitHolderFromPlanet)
                .filter(planet -> planet != null && VaettirAbilityHandler.hasRootToken(planet))
                .toList();
        for (Planet planet : rootedPlanets) {
            player.refreshPlanet(planet.getName());
            Tile tile = game.getTileFromPlanet(planet.getName());
            if (tile != null) {
                AddUnitService.addUnits(event, tile, game, player.getColor(), "1 inf " + planet.getName());
            }
        }
        String planets = rootedPlanets.stream()
                .map(Planet::getName)
                .map(planet -> Helper.getPlanetRepresentation(planet, game))
                .collect(java.util.stream.Collectors.joining(", "));
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " readied "
                        + (planets.isEmpty() ? "no planets" : planets)
                        + " and placed 1 infantry on each rooted planet they control with _The Roused Grove, the Vaettir hero_.");
        ButtonHelper.deleteMessage(event);
    }

    private static int countControlledPlanetsWithTrait(Game game, Player player, String trait) {
        return (int) player.getPlanets().stream()
                .map(game::getUnitHolderFromPlanet)
                .filter(planet -> planet != null
                        && (planet.getPlanetTypes().contains(trait)
                                || planet.getTokenList().contains("attachment_root_" + trait + ".png")))
                .count();
    }

    private static List<Button> getHeroReturnButtons(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> CommandCounterHelper.hasCC(player, tile))
                .filter(VaettirLeadersHandler::containsRootToken)
                .sorted(Comparator.comparing(Tile::getPosition))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + HERO_RETURN + tile.getPosition(),
                        "Return Token from " + tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static boolean containsRootToken(Tile tile) {
        return tile.getPlanetUnitHolders().stream().anyMatch(VaettirAbilityHandler::hasRootToken);
    }

    private static List<Button> appendButtons(List<Button> buttons, List<Button> extraButtons) {
        List<Button> result = new ArrayList<>(buttons);
        result.addAll(extraButtons);
        return result;
    }
}
