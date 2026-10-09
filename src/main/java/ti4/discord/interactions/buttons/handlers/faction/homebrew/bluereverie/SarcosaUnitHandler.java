package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.fow.FOWCombatThreadMirroring;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParseUnitService;
import ti4.service.unit.ParsedUnit;

@UtilityClass
public class SarcosaUnitHandler {
    private static final String COLOSSUS_HITS = "sarcosaColossusHits_";
    private static final String DESTROY_COLOSSUS_INFANTRY = "destroyColossusInfantry_";
    private static final String DONE_COLOSSUS = "doneColossusInfantry_";
    private static final String USE_NAUCLIS = "useNauclis";
    private static final String EXPLORE_NAUCLIS = "explorePlanetWithNauclis_";
    private static final String DEPLOY_RAVAGER = "deployRavager";
    private static final String RAVAGER_STEAL = "stealTgRavager_";
    private static final String FINISH_EVASIVE_PAYMENT = "finishEvasivePayment_";

    public static boolean handleEvasiveCoexistence(
            Game game, Player endingPlayer, Player defendingPlayer, Tile tile, UnitHolder planet) {
        List<Player> evasiveOwners = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, planet).stream()
                .filter(owner -> owner.hasAbility("evasive"))
                .filter(owner -> game.getPlanetsPlayerIsCoexistingOn(owner).contains(planet.getName()))
                .toList();
        if (evasiveOwners.contains(endingPlayer)) {
            MessageHelper.sendMessageToChannel(
                    endingPlayer.getCorrectChannel(),
                    endingPlayer.getRepresentationNoPing()
                            + " cannot end coexistence with their own units due to _Evasive_.");
            return true;
        }
        if (evasiveOwners.isEmpty()) {
            return false;
        }

        Player evasiveOwner = evasiveOwners.getFirst();
        List<Button> buttons = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, endingPlayer, "inf"));
        buttons.add(Buttons.green(
                endingPlayer.factionButtonChecker() + FINISH_EVASIVE_PAYMENT + tile.getPosition() + "|"
                        + planet.getName() + "|" + defendingPlayer.getFaction(),
                "Pay 2 Influence and Start Combat",
                FactionEmojis.sarcosa));
        buttons.add(Buttons.red(endingPlayer.factionButtonChecker() + "deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                endingPlayer.getCorrectChannel(),
                endingPlayer.getRepresentationNoPing() + ", spend 2 influence to end coexistence with "
                        + evasiveOwner.getRepresentationNoPing() + "'s units on "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + " due to _Evasive_.\n"
                        + "Only decline if Engage in Combat was pressed in error, or if you are engaging a player other than "
                        + evasiveOwner.getRepresentationNoPing() + ".\n"
                        + Helper.buildSpentThingsMessage(endingPlayer, game, "inf"),
                buttons);
        return true;
    }

    @ButtonHandler(FINISH_EVASIVE_PAYMENT)
    public static void finishEvasivePayment(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(FINISH_EVASIVE_PAYMENT.length()).split("\\|", 3);
        Tile tile = values.length == 3 ? game.getTileByPosition(values[0]) : null;
        UnitHolder planet = tile == null || values.length != 3
                ? null
                : tile.getUnitHolders().get(values[1]);
        Player defender = values.length == 3 ? game.getPlayerFromColorOrFaction(values[2]) : null;
        if (tile == null || planet == null || defender == null || player == defender) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        StartCombatService.startGroundCombat(player, defender, game, event, planet, tile);
        ButtonHelper.deleteMessage(event);
    }

    public static void offerColossusInfantryDestruction(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, UnitHolder planet, int hits) {
        if (hits < 1) {
            return;
        }
        List<Button> buttons = getColossusInfantryButtons(game, player, tile, planet);
        if (buttons.isEmpty()) {
            return;
        }
        game.setStoredValue(colossusHitsKey(player, tile, planet), Integer.toString(hits));
        buttons.add(Buttons.gray(
                player.factionButtonChecker() + DONE_COLOSSUS + tile.getPosition() + "|" + planet.getName(), "Done"));
        MessageChannel channel = game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel();
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                player.getRepresentationNoPing() + ", choose up to " + hits + " infantry to destroy with **Colossus**.",
                buttons);
    }

    @ButtonHandler(DESTROY_COLOSSUS_INFANTRY)
    public static void destroyColossusInfantry(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(DESTROY_COLOSSUS_INFANTRY.length()).split("\\|", 3);
        if (values.length != 3) {
            return;
        }
        Tile tile = game.getTileByPosition(values[0]);
        UnitHolder planet = tile == null ? null : tile.getUnitHolderFromPlanet(values[1]);
        Player target = game.getPlayerFromColorOrFaction(values[2]);
        if (tile == null
                || planet == null
                || target == null
                || getColossusHitsRemaining(game, player, tile, planet) < 1) {
            return;
        }
        UnitState state = infantryState(planet, target);
        if (state == null) {
            return;
        }
        ParsedUnit infantry = ParseUnitService.simpleParsedUnit(target, UnitType.Infantry, planet, 1);
        DestroyUnitService.destroyUnit(event, tile, game, infantry, true, state);
        String summary =
                target.getRepresentationNoPing() + " destroyed 1 infantry on " + planet.getRepresentation(game) + ".";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), summary);
        FOWCombatThreadMirroring.mirrorMessage(event, game, summary);
        int remainingHits = getColossusHitsRemaining(game, player, tile, planet) - 1;
        List<Button> buttons = getColossusInfantryButtons(game, player, tile, planet);
        if (remainingHits < 1 || buttons.isEmpty()) {
            game.removeStoredValue(colossusHitsKey(player, tile, planet));
            ButtonHelper.deleteMessage(event);
        } else {
            game.setStoredValue(colossusHitsKey(player, tile, planet), Integer.toString(remainingHits));
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + DONE_COLOSSUS + tile.getPosition() + "|" + planet.getName(),
                    "Done"));
            MessageHelper.editMessageButtons(event, buttons);
        }
    }

    @ButtonHandler(DONE_COLOSSUS)
    public static void finishColossusInfantry(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(DONE_COLOSSUS.length()).split("\\|", 2);
        if (values.length != 2) {
            return;
        }
        Tile tile = game.getTileByPosition(values[0]);
        UnitHolder planet = tile == null ? null : tile.getUnitHolderFromPlanet(values[1]);
        if (planet != null) {
            game.removeStoredValue(colossusHitsKey(player, tile, planet));
        }
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getColossusInfantryButtons(Game game, Player player, Tile tile, UnitHolder planet) {
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayersNNeutral()) {
            if (infantryState(planet, target) == null || !isColossusTargetVisible(game, player, target, tile, planet)) {
                continue;
            }
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + DESTROY_COLOSSUS_INFANTRY + tile.getPosition() + "|"
                            + planet.getName() + "|" + target.getFaction(),
                    "Destroy 1 Infantry: " + target.getFaction(),
                    target.getFactionEmoji()));
        }
        return buttons;
    }

    private static boolean isColossusTargetVisible(
            Game game, Player player, Player target, Tile tile, UnitHolder planet) {
        if (!game.isFowMode() || player == target) {
            return true;
        }
        var combat = StartCombatService.getCurrentCombat(game);
        return tile.getPosition().equals(combat.tilePosition())
                && planet.getName().equals(combat.unitHolderName())
                && combat.factions().contains(target.getFaction());
    }

    private static UnitState infantryState(UnitHolder planet, Player player) {
        var infantry = ti4.helpers.Units.getUnitKey(UnitType.Infantry, player.getColorID());
        return UnitState.defaultRemoveOrder().stream()
                .filter(state -> planet.getUnitCountForState(infantry, state) > 0)
                .findFirst()
                .orElse(null);
    }

    private static int getColossusHitsRemaining(Game game, Player player, Tile tile, UnitHolder planet) {
        String value = game.getStoredValue(colossusHitsKey(player, tile, planet));
        return value.isBlank() ? 0 : Integer.parseInt(value);
    }

    private static String colossusHitsKey(Player player, Tile tile, UnitHolder planet) {
        return COLOSSUS_HITS + player.getFaction() + "|" + tile.getPosition() + "|" + planet.getName();
    }

    public static Button offerNauclisExplore(Player player) {
        return Buttons.gray(player.factionButtonChecker() + USE_NAUCLIS, "Use Nauclis", FactionEmojis.sarcosa);
    }

    public static boolean canUseNauclisExplore(Game game, Player player) {
        return getNauclisSystem(game, player) != null;
    }

    @ButtonHandler(USE_NAUCLIS)
    public static void offerNauclisPlanets(ButtonInteractionEvent event, Game game, Player player) {
        Tile flagshipSystem = getNauclisSystem(game, player);
        if (flagshipSystem == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Nauclis is not available right now.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Planet planet : flagshipSystem.getPlanetUnitHolders()) {
            if (!FoWHelper.playerHasUnitsOnPlanet(player, flagshipSystem, planet.getName())) {
                continue;
            }

            if (ButtonHelper.getPlanetExplorationButtons(game, planet, player, false, true)
                    .isEmpty()) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + EXPLORE_NAUCLIS + planet.getName(),
                    "Explore " + planet.getRepresentation(game)));
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "You have no units on a planet in this system to explore.");
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        flagshipSystem.getSpaceUnitHolder().addDamagedUnit(Units.getUnitKey(UnitType.Flagship, player.getColorID()), 1);

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please select which planet you would like to explore using _Nauclis_.",
                buttons);

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(EXPLORE_NAUCLIS)
    public static void exploreNauclisPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.replace(EXPLORE_NAUCLIS, "");
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (planet == null || tile == null || !FoWHelper.playerHasUnitsOnPlanet(player, tile, planetName)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(game, planet, player, false, true);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", please choose how to explore the selected planet.",
                buttons);

        ButtonHelper.deleteMessage(event);
    }

    private static Tile getNauclisSystem(Game game, Player player) {
        if (!player.ownsUnit("sarcosa_flagship")) {
            return null;
        }
        UnitKey flagship = Units.getUnitKey(UnitType.Flagship, player.getColorID());
        return game.getTileMap().values().stream()
                .filter(tile -> ButtonHelper.doesPlayerHaveFSHere("sarcosa_flagship", player, tile))
                .filter(tile -> tile.getSpaceUnitHolder().getUnitCount(flagship)
                        > tile.getSpaceUnitHolder().getDamagedUnitCount(flagship))
                .filter(tile -> tile.getPlanetUnitHolders().stream()
                        .anyMatch(planet -> FoWHelper.playerHasUnitsOnPlanet(player, tile, planet.getName())
                                && !ButtonHelper.getPlanetExplorationButtons(game, planet, player, false, true)
                                        .isEmpty()))
                .findFirst()
                .orElse(null);
    }

    public static Button offerRavagerDeploy(Player player, Tile combatTile) {
        return Buttons.green(
                player.factionButtonChecker() + DEPLOY_RAVAGER + combatTile.getPosition(),
                "Deploy Ravager",
                FactionEmojis.sarcosa);
    }

    public static Button sendRavagerTGSteal(Player player, Player opponent, Tile tile) {
        int ravagerIIs = getRavagerIICount(player, tile);
        return Buttons.green(
                player.factionButtonChecker() + RAVAGER_STEAL + opponent.getFaction() + "|" + tile.getPosition(),
                "Take " + ravagerIIs + " Trade Good" + (ravagerIIs == 1 ? "" : "s"),
                FactionEmojis.sarcosa);
    }

    public static int getRavagerIICount(Player player, Tile tile) {
        return player.hasTech("dssarcdd") ? tile.getSpaceUnitHolder().getUnitCount(UnitType.Destroyer, player) : 0;
    }

    @ButtonHandler(DEPLOY_RAVAGER)
    public static void resolveRavagerDeploy(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String tilePos = buttonID.replace(DEPLOY_RAVAGER, "");
        Tile activeSystem = game.getTileByPosition(tilePos);
        if (activeSystem == null || !ButtonHelper.doesPlayerHaveUnitHere("sarcosa_destroyer", player, activeSystem)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        AddUnitService.addUnits(event, activeSystem, game, player.getColor(), "1 dd");

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " placed 1 _Ravager_ in " + activeSystem.getRepresentation()
                        + " using its DEPLOY ability.");

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(RAVAGER_STEAL)
    public static void resolveRavageTGSteal(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(RAVAGER_STEAL.length()).split("\\|", 2);
        Player target = values.length == 2 ? game.getPlayerFromColorOrFaction(values[0]) : null;
        Tile tile = values.length == 2 ? game.getTileByPosition(values[1]) : null;
        int ravagerIIs = tile == null ? 0 : getRavagerIICount(player, tile);
        if (target == null || ravagerIIs < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int stolen = Math.min(ravagerIIs, target.getTg());
        if (stolen < 1) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", you tried to steal a trade good but your opponent did not have one!");
            ButtonHelper.deleteMessage(event);
            return;
        }
        target.setTg(target.getTg() - stolen);
        player.gainTG(stolen);

        MessageHelper.sendMessageToChannel(
                target.getCorrectChannel(),
                target.getRepresentation()
                        + ", " + player.getRepresentationNoPing() + " has stolen " + stolen + " of your trade good"
                        + (stolen == 1 ? "" : "s") + " using _Ravager_. "
                        + " you now have " + target.getTg() + " trade good" + (target.getTg() == 1 ? "." : "s."));

        ButtonHelper.deleteMessage(event);
    }
}
