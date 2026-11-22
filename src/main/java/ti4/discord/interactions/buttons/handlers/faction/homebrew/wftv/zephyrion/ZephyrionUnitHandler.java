package ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.zephyrion;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class ZephyrionUnitHandler {

    private static final String CYCLONE_MECH = "zephyrion_mech";
    private static final String CYCLONE_START = "zephyrionCycloneStart";
    private static final String CYCLONE_DAMAGE = "zephyrionCycloneDamage_";

    public static boolean hasUndamagedCyclone(Game game, Player player) {
        return player.ownsUnit(CYCLONE_MECH) && !cycloneButtons(game, player).isEmpty();
    }

    public static Button cycloneButton(Player player) {
        return Buttons.gray(player.factionButtonChecker() + CYCLONE_START, "Use Cyclone Mech", FactionEmojis.zephyrion);
    }

    @ButtonHandler(CYCLONE_START)
    public static void offerCycloneDamage(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = cycloneButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You have no undamaged Cyclone mechs.");
            return;
        }
        buttons.add(Buttons.DECLINE_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose a Cyclone to damage. It applies +1 to the move value"
                        + " of 1 ship moving through its system, and cannot be applied to the same ship twice.",
                buttons);
    }

    @ButtonHandler(CYCLONE_DAMAGE)
    public static void damageCyclone(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace(CYCLONE_DAMAGE, "").split("_", 2);
        Tile tile = parts.length == 2 ? game.getTileByPosition(parts[0]) : null;
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(parts[1]);
        UnitKey mech = Units.getUnitKey(UnitType.Mech, player.getColorID());
        if (holder == null || undamagedCount(holder, mech) <= 0) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        holder.addDamagedUnit(mech, 1);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " damaged a Cyclone " + describe(game, tile, holder)
                        + " to apply +1 to the move value of 1 ship moving through that system.");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> cycloneButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        UnitKey mech = Units.getUnitKey(UnitType.Mech, player.getColorID());
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                if (undamagedCount(holder, mech) <= 0) {
                    continue;
                }
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + CYCLONE_DAMAGE + tile.getPosition() + "_" + holder.getName(),
                        "Damage Cyclone " + describe(game, tile, holder),
                        FactionEmojis.zephyrion));
            }
        }
        return buttons;
    }

    private static int undamagedCount(UnitHolder holder, UnitKey mech) {
        return holder.getUnitCount(mech) - holder.getDamagedUnitCount(mech);
    }

    private static String describe(Game game, Tile tile, UnitHolder holder) {
        if (holder instanceof Planet) {
            return "on " + Helper.getPlanetRepresentation(holder.getName(), game) + " (" + tile.getPosition() + ")";
        }
        return "in the space of " + tile.getPosition();
    }
}
