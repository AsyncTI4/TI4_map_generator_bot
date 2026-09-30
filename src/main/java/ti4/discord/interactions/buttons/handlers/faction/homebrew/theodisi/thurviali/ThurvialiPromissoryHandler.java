package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.thurviali;

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
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

@UtilityClass
public class ThurvialiPromissoryHandler {
    private static final String SELECT_STRUCTURE = "selectRadiantAssemblyStructure_";

    public static void resolveRadiantAssembly(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Button> buttons = getStructureButtons(game, player);

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " has no structure available in reinforcements for _Radiant Assembly_.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose 1 structure to place with _Radiant Assembly_.",
                buttons);
    }

    @ButtonHandler(SELECT_STRUCTURE)
    public static void selectRadiantAssemblyStructure(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String asyncId = buttonID.substring(SELECT_STRUCTURE.length());
        UnitModel unit = player.getUnitsByAsyncID(asyncId).stream().findFirst().orElse(null);

        if (unit == null
                || !unit.getIsStructure()
                || ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, asyncId) >= player.getUnitCap(asyncId)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, asyncId, "placeOneNDone_skipbuild");

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " has no eligible controlled planet on which to place "
                            + unit.getName()
                            + " with _Radiant Assembly_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing()
                        + ", choose a controlled planet on which to place "
                        + unit.getName()
                        + " with _Radiant Assembly_.",
                buttons);
    }

    private static List<Button> getStructureButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();

        for (UnitModel unit : player.getUnitModels()) {
            if (!unit.getIsStructure()
                    || ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, unit.getAsyncId())
                            >= player.getUnitCap(unit.getAsyncId())) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_STRUCTURE + unit.getAsyncId(),
                    "Place " + unit.getName(),
                    unit.getUnitEmoji()));
        }

        return buttons;
    }
}
