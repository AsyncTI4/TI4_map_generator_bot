package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.deep;

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
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.RemoveCommandCounterService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.CheckUnitContainmentService;

@UtilityClass
public class DeepUnitHandler {
    public static final String DEEP = "deep_thedeep";
    public static final String ASCENDED = "deep_thedeepascended";
    public static final String DEEP_PLANET = "thedeep";
    public static final String ASCENDED_PLANET = "thedeepascended";
    private static final String FRANKEN_SETUP = "setupDeepFranken";
    private static final String FINISH_NULL_PAYMENT = "deepNullFinishPayment";

    public static void setup(Game game, Player player, Tile tile, GenericInteractionCreateEvent event) {
        if (!player.hasAbility("origin_unknown") || tile == null || player.hasPlanet(DEEP_PLANET)) {
            return;
        }
        player.addPlanet(DEEP_PLANET);
        player.refreshPlanet(DEEP_PLANET);
        UnitKey key = getUnitKey(player);
        if (tile.getSpaceUnitHolder().getUnitCount(key) == 0) {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "1 thedeep");
        }
    }

    public static void offerFrankenSetup(Player player) {
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", after choosing your home system and placing your starting fleet, press this button to set up The Deep.",
                Buttons.green(player.factionButtonChecker() + FRANKEN_SETUP, "Set Up The Deep"));
    }

    @ButtonHandler(FRANKEN_SETUP)
    public static void setupFranken(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasAbility("origin_unknown") || player.hasPlanet(DEEP_PLANET)) {
            return;
        }
        Tile homeSystem = game.getTileByPosition(player.getHomeSystemPosition());
        if (homeSystem == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Choose your home system before setting up The Deep.");
            return;
        }
        setup(game, player, homeSystem, event);
        ButtonHelper.deleteMessage(event);
    }

    public static boolean isDeepPlayer(Player player) {
        return player != null && (player.hasUnit(DEEP) || player.hasUnit(ASCENDED));
    }

    public static boolean isDeepUnit(Player player, UnitKey unitKey) {
        return isDeepPlayer(player) && unitKey != null && unitKey.unitType() == UnitType.Deep;
    }

    public static boolean hasArbiterInSystem(Player player, Tile tile) {
        return player != null
                && tile != null
                && player.ownsUnit("deep_flagship")
                && tile.getSpaceUnitHolder().getUnitCount(UnitType.Flagship, player.getColor()) > 0;
    }

    public static UnitKey getUnitKey(Player player) {
        return Units.getUnitKey(UnitType.Deep, player.getColorID());
    }

    public static String getPlanet(Player player) {
        return player.hasUnit(ASCENDED) ? ASCENDED_PLANET : DEEP_PLANET;
    }

    public static Tile getUnitTile(Game game, Player player) {
        if (!isDeepPlayer(player)) {
            return null;
        }
        UnitKey key = getUnitKey(player);
        return CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, UnitType.Deep).stream()
                .filter(tile -> tile.getSpaceUnitHolder().getUnitCount(key) > 0)
                .findFirst()
                .orElse(null);
    }

    public static boolean isOnBoard(Game game, Player player) {
        return getUnitTile(game, player) != null;
    }

    public static void transform(Player player) {
        if (!isDeepPlayer(player) || player.hasUnit(ASCENDED)) {
            return;
        }
        player.getPlanets().remove(DEEP_PLANET);
        player.getExhaustedPlanets().remove(DEEP_PLANET);
        player.getExhaustedPlanetsAbilities().remove(DEEP_PLANET);
        player.removeOwnedUnitByID(DEEP);
        player.addOwnedUnitByID(ASCENDED);
        player.addPlanet(ASCENDED_PLANET);
        player.refreshPlanet(ASCENDED_PLANET);
        player.refreshPlanetAbility(ASCENDED_PLANET);
    }

    public static void offerNullDeploy(Game game, Player player) {
        if (!player.hasUnit("deep_mech") || ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", true) >= 4) {
            return;
        }
        List<Button> buttons = List.of(
                Buttons.green(player.factionButtonChecker() + "deepNullDeploy", "Deploy Null"),
                Buttons.DECLINE_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", after exploring a frontier token, you may pay 1 influence to DEPLOY _Null_.",
                buttons);
    }

    @ButtonHandler("deepNullDeploy")
    public static void deployNull(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasUnit("deep_mech") || ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", true) >= 4) {
            return;
        }
        List<Button> paymentButtons = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, player, "inf"));
        paymentButtons.add(
                Buttons.green(player.factionButtonChecker() + FINISH_NULL_PAYMENT, "Done Paying 1 Influence"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", pay 1 influence to DEPLOY _Null_, then press done.\n"
                        + Helper.buildSpentThingsMessage(player, game, "inf"),
                paymentButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(FINISH_NULL_PAYMENT)
    public static void finishNullPayment(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasUnit("deep_mech") || ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", true) >= 4) {
            return;
        }
        List<Button> buttons =
                new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, game, "mech", "placeOneNDone_skipbuild"));
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + ", you control no eligible planets.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        buttons.add(Buttons.DECLINE_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", select a planet on which to DEPLOY _Null_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void offerDrown(Game game, Player activatingPlayer, Tile tile) {
        for (Player deepPlayer : game.getRealPlayers()) {
            if (deepPlayer == activatingPlayer
                    || !deepPlayer.hasUnit(ASCENDED)
                    || !deepPlayer.hasPlanet(ASCENDED_PLANET)
                    || deepPlayer.getExhaustedPlanetsAbilities().contains(ASCENDED_PLANET)
                    || getUnitTile(game, deepPlayer) != tile
                    || !tile.hasPlayerCC(deepPlayer)) {
                continue;
            }
            MessageHelper.sendMessageToChannelWithButton(
                    deepPlayer.getCorrectChannel(),
                    deepPlayer.getRepresentation()
                            + ", you may exhaust _Drown_ to remove your command token from this system.",
                    Buttons.green(deepPlayer.factionButtonChecker() + "deepDrown_" + tile.getPosition(), "Use Drown"));
        }
    }

    @ButtonHandler("deepDrown_")
    public static void drown(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.replace("deepDrown_", "");
        Tile tile = game.getTileByPosition(position);
        if (tile == null
                || !player.hasUnit(ASCENDED)
                || player.getExhaustedPlanetsAbilities().contains(ASCENDED_PLANET)
                || getUnitTile(game, player) != tile
                || !tile.hasPlayerCC(player)) {
            return;
        }
        player.exhaustPlanetAbility(ASCENDED_PLANET);
        RemoveCommandCounterService.fromTile(event, player, tile);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " exhausted _Drown_ and removed their command token.");
        event.getMessage().delete().queue();
    }
}
