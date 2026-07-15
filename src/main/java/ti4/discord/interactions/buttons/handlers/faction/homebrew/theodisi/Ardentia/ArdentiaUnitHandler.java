package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Ardentia;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.combat.CombatRollService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class ArdentiaUnitHandler {
    private static final String USE_SOVEREIGN = "useSovereignsGavel_";
    private static final String DEPLOY_IRON_CLAW = "deployIronClaw_";
    private static final String DEPLOY_IRON_CLAW_ON = "deployIronClawOn_";

    // Sovereign's Gavel
    public static void offerSovereignsGavelButton(
            GenericInteractionCreateEvent event, Game game, Player player, Player opponent, Tile tile) {
        if (player.getStrategicCC() < 1 || !ButtonHelper.doesPlayerHaveFSHere("ardentia_flagship", player, tile)) {
            return;
        }

        Button useGavel = Buttons.gray(
                player.factionButtonChecker() + USE_SOVEREIGN + tile.getPosition() + "_" + opponent.getFaction(),
                "Use Sovereign's Gavel",
                FactionEmojis.ardentia);
        MessageHelper.sendMessageToEventChannelWithButtons(
                event,
                player.getRepresentation()
                        + ", you may spend 1 command token from your strategy pool to produce 2 hits using _Sovereign's Gavel_.",
                List.of(useGavel, Buttons.gray("deleteButtons", "Decline")));
    }

    @ButtonHandler(USE_SOVEREIGN)
    public static void resolveSovereignsGavel(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace(USE_SOVEREIGN, "").split("_", 2);
        if (parts.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(parts[0]);
        Player opponent = game.getPlayerFromColorOrFaction(parts[1]);
        String factionsInCombat = game.getStoredValue("factionsInCombat");
        if (tile == null
                || opponent == null
                || player.getStrategicCC() < 1
                || !ButtonHelper.doesPlayerHaveFSHere("ardentia_flagship", player, tile)
                || !factionsInCombat.contains(player.getFaction())
                || !factionsInCombat.contains(opponent.getFaction())) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.setStrategicCC(player.getStrategicCC() - 1);
        CombatRollService.sendSpaceAssignHitsButtons(event, game, opponent, tile, 2);
        ButtonHelper.deleteMessage(event);
    }

    // Iron Claw Division
    public static void addIronClawDeployButton(List<Button> buttons, Game game, Player player, Tile tile) {
        if (!canDeployIronClaw(game, player, tile)
                || !game.getStoredValue("ironClawDeployUsed_" + player.getFaction())
                        .isEmpty()) {
            return;
        }
        buttons.add(Buttons.green(
                player.factionButtonChecker() + DEPLOY_IRON_CLAW + tile.getPosition(),
                "Deploy Iron Claw",
                FactionEmojis.ardentia));
    }

    private static boolean canDeployIronClaw(Game game, Player player, Tile tile) {
        if (!player.ownsUnit("ardentia_mech") || !tile.getPosition().equals(game.getActiveSystem())) {
            return false;
        }
        if (tile.getSpaceUnitHolder().getUnitCount(UnitType.Infantry, player) == 0
                && tile.getSpaceUnitHolder().getUnitCount(UnitType.Mech, player) == 0) {
            return false;
        }
        for (String controlledPlanet : player.getPlanets()) {
            Tile controlledPlanetTile = game.getTileFromPlanet(controlledPlanet);
            if (controlledPlanetTile != null
                    && (controlledPlanetTile.getPosition().equals(tile.getPosition())
                            || FoWHelper.getAdjacentTiles(game, controlledPlanetTile.getPosition(), player, false, true)
                                    .contains(tile.getPosition()))) {
                return true;
            }
        }
        return false;
    }

    @ButtonHandler(DEPLOY_IRON_CLAW)
    public static void chooseIronClawPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(DEPLOY_IRON_CLAW.length()));
        if (tile == null || !canDeployIronClaw(game, player, tile)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        game.setStoredValue("ironClawDeployUsed_" + player.getFaction(), "true");
        List<Button> buttons = new ArrayList<>();
        for (var planet : tile.getPlanetUnitHolders()) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + DEPLOY_IRON_CLAW_ON + tile.getPosition() + "|" + planet.getName(),
                    "Deploy on " + Helper.getPlanetRepresentation(planet.getName(), game)));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", choose a planet to commit 1 mech and 1 infantry from your reinforcements to for 3 influence.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(DEPLOY_IRON_CLAW_ON)
    public static void resolveDeployIronClaw(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(DEPLOY_IRON_CLAW_ON.length()).split("\\|", 2);
        if (parts.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(parts[0]);
        String planetName = parts[1];
        if (tile == null
                || tile.getPlanetUnitHolders().stream()
                        .noneMatch(planet -> planet.getName().equals(planetName))
                || !canDeployIronClaw(game, player, tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> paymentButtons = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, player, "inf"));
        paymentButtons.add(Buttons.red("deleteButtons", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + " added 1 mech and 1 infantry to " + Helper.getPlanetRepresentation(planetName, game)
                        + " using _Iron Claw Division's_ **DEPLOY** ability.\n\nPlease use the buttons below to pay 3 influence.",
                paymentButtons);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mf " + planetName + ", 1 inf " + planetName);
        ButtonHelper.deleteMessage(event);
    }

    public static void clearIronClawDeployUsed(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue("ironClawDeployUsed_" + player.getFaction());
        }
    }
}
