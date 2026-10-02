package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.onyxxa;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.unit.MoveUnitService;

@UtilityClass
public class OnyxxaBreakthroughHandler {

    public static boolean canMoveThroughIngressSystem(Player player, Tile tile) {
        return player.hasUnlockedBreakthrough("onyxxabt")
                && tile.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_INGRESS);
    }

    public static void offerGroundCombatMechButtons(Game game, Player player, UnitHolder unitHolder, Tile tile) {
        String planetName = unitHolder.getName();
        int infantryCount = unitHolder.getUnitCount(UnitType.Infantry, player.getColorID());
        if (infantryCount < 1) return;

        boolean inFracture = tile.isFracture();
        boolean inNexus = "82b".equals(tile.getTileID()) || "82bh".equals(tile.getTileID());
        if (!inFracture && !inNexus) return;

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "onyxxabtMechPlacement_" + tile.getPosition() + "_" + planetName,
                "Replace 1 Infantry with 1 Mech (" + infantryCount + " available)"));
        buttons.add(Buttons.red("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", you may replace any number of your infantry with mechs on "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + " (_Styx and Stones_). Click the button once per infantry.",
                buttons);
    }

    @ButtonHandler("onyxxabtMechPlacement_")
    public static void handleMechPlacement(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace("onyxxabtMechPlacement_", "").split("_", 2);
        String pos = parts[0];
        String planetName = parts[1];
        Tile tile = game.getTileByPosition(pos);
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);

        MoveUnitService.replaceUnit(event, game, player, tile, unitHolder, UnitType.Infantry, UnitType.Mech);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation(false, false) + " replaced 1 infantry with 1 mech on "
                        + Helper.getPlanetRepresentation(planetName, game) + " (_Styx and Stones_).");

        int remaining = unitHolder.getUnitCount(UnitType.Infantry, player.getColorID());
        if (remaining == 0) {
            ButtonHelper.deleteMessage(event);
        } else {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "onyxxabtMechPlacement_" + pos + "_" + planetName,
                    "Replace 1 Infantry with 1 Mech (" + remaining + " remaining)"));
            buttons.add(Buttons.red("deleteButtons", "Done"));
            MessageHelper.editMessageButtons(event, buttons);
        }
    }
}
