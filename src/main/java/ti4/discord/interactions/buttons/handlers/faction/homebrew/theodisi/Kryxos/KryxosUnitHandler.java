package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Kryxos;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

@UtilityClass
public class KryxosUnitHandler {
    private static final String FLIP_UNIT = "kryxosFlipUnit_";

    public static void offerEvolutionButtons(Player player, Game game, String techID) {
        if (player == null || game == null || techID == null || !player.hasExactTech(techID)) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        if (player.ownsUnit("kryxos_flagship2") && !player.ownsUnit("kryxos_flagship3")) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + FLIP_UNIT + techID + "|kryxos_flagship2|kryxos_flagship3",
                    "Flip Ultimate Evolution II"));
        }
        if (player.ownsUnit("kryxos_mech2") && !player.ownsUnit("kryxos_mech3")) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + FLIP_UNIT + techID + "|kryxos_mech2|kryxos_mech3",
                    "Flip Warspawn Juggernaut II"));
        }

        if (buttons.isEmpty()) {
            return;
        }

        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", after gaining/researching a technology, you may flip one of your Kryxos units.",
                buttons);
    }

    @ButtonHandler(FLIP_UNIT)
    public static void resolveFlipUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null) {
            return;
        }

        String payload = buttonID.substring(FLIP_UNIT.length());
        String[] parts = payload.split("\\|", 3);
        if (parts.length != 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String techID = parts[0];
        String currentUnit = parts[1];
        String flippedUnit = parts[2];
        if (!player.hasExactTech(techID) || !player.ownsUnit(currentUnit) || player.ownsUnit(flippedUnit)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.removeTech(techID);
        player.removeOwnedUnitByID(currentUnit);
        player.addOwnedUnitByID(flippedUnit);
        ButtonHelper.deleteMessage(event);

        UnitModel currentModel = Mapper.getUnit(currentUnit);
        UnitModel flippedModel = Mapper.getUnit(flippedUnit);
        String currentName = currentModel == null ? currentUnit : currentModel.getName();
        String flippedName = flippedModel == null ? flippedUnit : flippedModel.getName();

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + "flipped _" + currentName + "_ into _" + flippedName + "_.");
    }
}
