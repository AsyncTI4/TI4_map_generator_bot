package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.bloom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.thurviali.ThurvialiAbilityHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitType;
import ti4.helpers.adjacency.Adjacency;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class BloomLeadersHandler {
    private static final String COMMANDER = "bloomcommander";
    private static final String COMMANDER_USE = "bloomCommanderUse_";
    private static final String COMMANDER_MOVE = "bloomCommanderMove_";
    private static final String COMMANDER_TARGET = "bloomCommanderTarget_";
    private static final String HERO_SOURCE = "bloomHeroSource_";
    private static final String HERO_PLACE = "bloomHeroPlace_";
    private static final String HERO_DONE = "bloomHeroDone";
    private static final String HERO_SOURCES = "bloomHeroSources_";
    private static final String HERO_USED_SOURCES = "bloomHeroUsedSources_";

    public static void offerCommander(Game game, Player activatingPlayer, Tile activatedTile) {
        for (Player owner : game.getRealPlayers()) {
            if (owner == activatingPlayer
                    || !game.playerHasLeaderUnlockedOrAlliance(owner, COMMANDER)
                    || getCommanderSourceButtons(game, owner, activatedTile).isEmpty()) {
                continue;
            }

            String activatingPlayerName =
                    game.isFowMode() ? "another player" : activatingPlayer.getRepresentationNoPing();
            MessageHelper.sendMessageToChannelWithButtons(
                    owner.getCorrectChannel(),
                    owner.getRepresentationNoPing() + ", " + activatingPlayerName
                            + " activated a system containing a planet you are coexisting on. You may move 1 infantry there using _Vaa'kul_.",
                    List.of(
                            Buttons.green(
                                    owner.factionButtonChecker() + COMMANDER_USE + activatedTile.getPosition(),
                                    "Use Vaa'kul"),
                            Buttons.DECLINE_DELETE_BUTTONS));
        }
    }

    @ButtonHandler(COMMANDER_USE)
    public static void useCommander(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile activeTile = game.getTileByPosition(buttonID.substring(COMMANDER_USE.length()));
        List<Button> buttons = getCommanderSourceButtons(game, player, activeTile);
        if (activeTile == null
                || !activeTile.getPosition().equals(game.getActiveSystem())
                || !game.playerHasLeaderUnlockedOrAlliance(player, COMMANDER)
                || buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        NewStuffHelper.sendPaginated(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose a planet to move 1 infantry from with _Vaa'kul_.",
                buttons,
                List.of(Buttons.DECLINE_DELETE_BUTTONS),
                player.factionButtonChecker() + COMMANDER_MOVE + activeTile.getPosition() + "|");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(COMMANDER_MOVE)
    public static void chooseCommanderSource(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] pageValues = buttonID.substring(COMMANDER_MOVE.length()).split("\\|", 2);
        if (pageValues.length < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile activeTile = game.getTileByPosition(pageValues[0]);
        if (NewStuffHelper.turnPage(
                event,
                buttonID,
                player.getRepresentationNoPing() + ", you may move 1 infantry using _Vaa'kul_.",
                () -> getCommanderSourceButtons(game, player, activeTile),
                List.of(Buttons.DECLINE_DELETE_BUTTONS),
                player.factionButtonChecker() + COMMANDER_MOVE + pageValues[0] + "|")) {
            return;
        }
        String[] values = buttonID.substring(COMMANDER_MOVE.length()).split("\\|", 3);
        if (values.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String sourcePlanet = values[1];
        if (activeTile == null
                || !activeTile.getPosition().equals(game.getActiveSystem())
                || !game.playerHasLeaderUnlockedOrAlliance(player, COMMANDER)
                || getCommanderTargetButtons(game, player, activeTile, sourcePlanet)
                        .isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        NewStuffHelper.sendPaginated(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose the planet to move the infantry to with _Vaa'kul_.",
                getCommanderTargetButtons(game, player, activeTile, sourcePlanet),
                List.of(Buttons.DECLINE_DELETE_BUTTONS),
                player.factionButtonChecker() + COMMANDER_TARGET + activeTile.getPosition() + "|" + sourcePlanet + "|");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(COMMANDER_TARGET)
    public static void moveCommanderInfantry(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] pageValues = buttonID.substring(COMMANDER_TARGET.length()).split("\\|", 3);
        if (pageValues.length < 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile activeTile = game.getTileByPosition(pageValues[0]);
        String sourcePlanet = pageValues[1];
        if (NewStuffHelper.turnPage(
                event,
                buttonID,
                player.getRepresentationNoPing() + ", choose the planet to move the infantry to with _Vaa'kul_.",
                () -> getCommanderTargetButtons(game, player, activeTile, sourcePlanet),
                List.of(Buttons.DECLINE_DELETE_BUTTONS),
                player.factionButtonChecker() + COMMANDER_TARGET + pageValues[0] + "|" + sourcePlanet + "|")) {
            return;
        }
        String[] values = buttonID.substring(COMMANDER_TARGET.length()).split("\\|", 3);
        if (values.length != 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String targetPlanet = values[2];
        Planet source = game.getUnitHolderFromPlanet(sourcePlanet);
        Planet target = game.getUnitHolderFromPlanet(targetPlanet);
        Tile sourceTile = game.getTileFromPlanet(sourcePlanet);
        if (activeTile == null
                || !activeTile.getPosition().equals(game.getActiveSystem())
                || source == null
                || target == null
                || sourceTile == null
                || !game.playerHasLeaderUnlockedOrAlliance(player, COMMANDER)
                || sourcePlanet.equals(targetPlanet)
                || !game.getPlanetsPlayerIsCoexistingOn(player).contains(sourcePlanet)
                || !game.getPlanetsPlayerIsCoexistingOn(player).contains(targetPlanet)
                || !activeTile.getPlanetUnitHolders().contains(target)
                || source.getUnitCount(UnitType.Infantry, player) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        var removed = RemoveUnitService.removeUnit(event, sourceTile, game, player, source, UnitType.Infantry, 1);
        AddUnitService.addUnits(
                event,
                activeTile,
                game,
                player.getColor(),
                removed.stream()
                        .map(unit -> unit.onUnitHolder(activeTile, targetPlanet))
                        .toList());
        BloomAbilityHandler.syncCoexistingAbilities(game, player);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " moved 1 infantry from "
                        + Helper.getPlanetRepresentation(sourcePlanet, game) + " to "
                        + Helper.getPlanetRepresentation(targetPlanet, game) + " with _Vaa'kul_.");
        ButtonHelper.deleteMessage(event);
    }

    public static void startFungalBloom(GenericInteractionCreateEvent event, Game game, Player player) {
        List<String> sourcePlanets =
                game.getPlanetsPlayerIsCoexistingOn(player).stream().sorted().toList();
        game.setStoredValue(getHeroSourcesKey(player), String.join("|", sourcePlanets));
        game.removeStoredValue(getHeroUsedSourcesKey(player));
        for (String planetName : sourcePlanets) {
            placeCoexistingInfantry(event, game, player, planetName);
        }
        BloomAbilityHandler.syncCoexistingAbilities(game, player);
        offerHeroSources(event, game, player);
    }

    @ButtonHandler(HERO_SOURCE)
    public static void chooseHeroSource(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (NewStuffHelper.turnPage(
                event,
                buttonID,
                heroSourcePrompt(player),
                () -> getHeroSourceButtons(game, player),
                List.of(Buttons.red(player.factionButtonChecker() + HERO_DONE, "Done")),
                player.factionButtonChecker() + HERO_SOURCE)) {
            return;
        }
        String sourcePlanet = buttonID.substring(HERO_SOURCE.length());
        if (!getHeroSourcePlanets(game, player).contains(sourcePlanet)
                || getUsedHeroSources(game, player).contains(sourcePlanet)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        NewStuffHelper.sendPaginated(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose an eligible planet for _Fungal Bloom_.",
                getHeroTargetButtons(game, player, sourcePlanet),
                List.of(Buttons.DECLINE_DELETE_BUTTONS),
                player.factionButtonChecker() + HERO_PLACE + sourcePlanet + "|");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(HERO_PLACE)
    public static void placeHeroInfantry(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(HERO_PLACE.length()).split("\\|", 2);
        if (values.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String sourcePlanet = values[0];
        if (NewStuffHelper.turnPage(
                event,
                buttonID,
                player.getRepresentationNoPing() + ", choose an eligible planet for _Fungal Bloom_.",
                () -> getHeroTargetButtons(game, player, sourcePlanet),
                List.of(Buttons.DECLINE_DELETE_BUTTONS),
                player.factionButtonChecker() + HERO_PLACE + sourcePlanet + "|")) {
            return;
        }
        String targetPlanet = values[1];
        if (!getHeroSourcePlanets(game, player).contains(sourcePlanet)
                || getUsedHeroSources(game, player).contains(sourcePlanet)
                || !getEligibleHeroTargets(game, player, sourcePlanet).contains(targetPlanet)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        placeCoexistingInfantry(event, game, player, targetPlanet);
        addUsedHeroSource(game, player, sourcePlanet);
        BloomAbilityHandler.syncCoexistingAbilities(game, player);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed 1 infantry into coexistence on "
                        + Helper.getPlanetRepresentation(targetPlanet, game) + " with _Fungal Bloom_.");
        ButtonHelper.deleteMessage(event);
        offerHeroSources(event, game, player);
    }

    @ButtonHandler(HERO_DONE)
    public static void finishFungalBloom(ButtonInteractionEvent event, Game game, Player player) {
        game.removeStoredValue(getHeroSourcesKey(player));
        game.removeStoredValue(getHeroUsedSourcesKey(player));
        ButtonHelper.deleteMessage(event);
    }

    private static void offerHeroSources(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Button> buttons = getHeroSourceButtons(game, player);
        if (buttons.isEmpty()) {
            game.removeStoredValue(getHeroSourcesKey(player));
            game.removeStoredValue(getHeroUsedSourcesKey(player));
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), player.getRepresentationNoPing() + " resolved _Fungal Bloom_.");
            return;
        }
        NewStuffHelper.sendPaginated(
                player.getCorrectChannel(),
                heroSourcePrompt(player),
                buttons,
                List.of(Buttons.red(player.factionButtonChecker() + HERO_DONE, "Done")),
                player.factionButtonChecker() + HERO_SOURCE);
    }

    private static List<Button> getCommanderSourceButtons(Game game, Player player, Tile activeTile) {
        if (activeTile == null) {
            return List.of();
        }
        Set<String> coexistingPlanets = Set.copyOf(game.getPlanetsPlayerIsCoexistingOn(player));
        List<Button> buttons = new ArrayList<>();
        for (String sourcePlanet : coexistingPlanets) {
            Planet source = game.getUnitHolderFromPlanet(sourcePlanet);
            if (source == null
                    || source.getUnitCount(UnitType.Infantry, player) < 1
                    || getCommanderTargetButtons(game, player, activeTile, sourcePlanet)
                            .isEmpty()) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + COMMANDER_MOVE + activeTile.getPosition() + "|" + sourcePlanet,
                    "Move from " + Helper.getPlanetRepresentation(sourcePlanet, game)));
        }
        return buttons;
    }

    private static List<Button> getCommanderTargetButtons(
            Game game, Player player, Tile activeTile, String sourcePlanet) {
        if (activeTile == null) {
            return List.of();
        }
        return activeTile.getPlanetUnitHolders().stream()
                .map(Planet::getName)
                .filter(targetPlanet -> !targetPlanet.equals(sourcePlanet))
                .filter(targetPlanet ->
                        game.getPlanetsPlayerIsCoexistingOn(player).contains(targetPlanet))
                .map(targetPlanet -> Buttons.green(
                        player.factionButtonChecker() + COMMANDER_TARGET + activeTile.getPosition() + "|" + sourcePlanet
                                + "|" + targetPlanet,
                        "Move to " + Helper.getPlanetRepresentation(targetPlanet, game)))
                .toList();
    }

    private static List<Button> getHeroSourceButtons(Game game, Player player) {
        return getHeroSourcePlanets(game, player).stream()
                .filter(source -> !getUsedHeroSources(game, player).contains(source))
                .filter(source -> getEligibleHeroTargets(game, player, source).size() > 0)
                .map(source -> Buttons.green(
                        player.factionButtonChecker() + HERO_SOURCE + source,
                        "Use " + Helper.getPlanetRepresentation(source, game)))
                .toList();
    }

    private static List<Button> getHeroTargetButtons(Game game, Player player, String sourcePlanet) {
        return getEligibleHeroTargets(game, player, sourcePlanet).stream()
                .map(target -> Buttons.green(
                        player.factionButtonChecker() + HERO_PLACE + sourcePlanet + "|" + target,
                        Helper.getPlanetRepresentation(target, game)))
                .toList();
    }

    private static List<String> getEligibleHeroTargets(Game game, Player player, String sourcePlanet) {
        Tile sourceTile = game.getTileFromPlanet(sourcePlanet);
        Planet source = game.getUnitHolderFromPlanet(sourcePlanet);
        if (sourceTile == null || source == null || getGroundForceCount(source, player) < 2) {
            return List.of();
        }
        return game.getTileMap().values().stream()
                .filter(tile -> tile == sourceTile
                        || Adjacency.of(game, player)
                                .forEffects()
                                .from(sourceTile.getPosition())
                                .contains(tile.getPosition()))
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .map(Planet::getName)
                .filter(planet -> {
                    Player controller = game.getPlayerThatControlsPlanet(planet, true);
                    Tile tile = game.getTileFromPlanet(planet);
                    return controller != null && controller != player && tile != null && !tile.isHomeSystem(game);
                })
                .sorted()
                .toList();
    }

    private static List<String> getHeroSourcePlanets(Game game, Player player) {
        String storedSources = game.getStoredValue(getHeroSourcesKey(player));
        if (storedSources.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(storedSources.split("\\|"))
                .filter(planet -> !planet.isEmpty())
                .filter(planet -> game.getPlanetsPlayerIsCoexistingOn(player).contains(planet))
                .filter(planet -> {
                    Planet holder = game.getUnitHolderFromPlanet(planet);
                    return holder != null && getGroundForceCount(holder, player) >= 2;
                })
                .toList();
    }

    private static Set<String> getUsedHeroSources(Game game, Player player) {
        String storedSources = game.getStoredValue(getHeroUsedSourcesKey(player));
        return storedSources.isEmpty() ? Set.of() : Set.copyOf(Arrays.asList(storedSources.split("\\|")));
    }

    private static void addUsedHeroSource(Game game, Player player, String sourcePlanet) {
        List<String> usedSources = new ArrayList<>(getUsedHeroSources(game, player));
        usedSources.add(sourcePlanet);
        game.setStoredValue(getHeroUsedSourcesKey(player), String.join("|", usedSources));
    }

    private static void placeCoexistingInfantry(
            GenericInteractionCreateEvent event, Game game, Player player, String planetName) {
        Tile tile = game.getTileFromPlanet(planetName);
        if (tile == null) {
            return;
        }
        String coexistenceFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        try {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planetName);
        } finally {
            if (coexistenceFlag.isEmpty()) {
                game.removeStoredValue("coexistFlag");
            } else {
                game.setStoredValue("coexistFlag", coexistenceFlag);
            }
        }
        ButtonHelperAbilities.oceanBoundCheck(game);
        ThurvialiAbilityHandler.checkRadiantGrafting(game);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " placed an infantry from their reinforcements onto each planet they are coexisting on.");
    }

    private static int getGroundForceCount(Planet planet, Player player) {
        return planet.getUnitCount(UnitType.Infantry, player) + planet.getUnitCount(UnitType.Mech, player);
    }

    private static String getHeroSourcesKey(Player player) {
        return HERO_SOURCES + player.getFaction();
    }

    private static String getHeroUsedSourcesKey(Player player) {
        return HERO_USED_SOURCES + player.getFaction();
    }

    private static String heroSourcePrompt(Player player) {
        return player.getRepresentationNoPing()
                + ", choose each coexisting planet that has 2 or more of your ground forces to place 1 infantry into coexistence on an eligible planet.";
    }
}
