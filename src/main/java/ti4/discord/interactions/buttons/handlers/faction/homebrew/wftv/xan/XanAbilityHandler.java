package ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.xan;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.stoneborn.StonebornUnitHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class XanAbilityHandler {

    public static void offerQuantumFabrication(
            Player player, Game game, GenericInteractionCreateEvent event, Tile tile) {
        String msg = player.getRepresentationUnfogged()
                + ", if you placed this space dock via **Construction**, you may use its PRODUCTION ability immediately in "
                + tile.getRepresentationForButtons(game, player) + " via **Quantum Fabrication**.";
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                msg,
                Helper.getPlaceUnitButtons(event, player, game, tile, "ministerBuild", "place"));
    }

    public static Button getTransformationProtocolTriggerButton(Player player, Game game) {
        if (getTransformationProtocolButtons(player, game).isEmpty()) {
            return null;
        }
        return Buttons.gray(
                "exhaustForVotes_xanTransform", "Sustain up to 2 Ships for 4 Votes Each", FactionEmojis.xan);
    }

    public static List<Button> getTransformationProtocolButtons(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        if (player.getSpentThingCount("xanships") >= 8) {
            return buttons;
        }
        for (Tile tile : game.getTileMap().values()) {
            UnitHolder space = tile.getSpaceUnitHolder();
            if (space == null) {
                continue;
            }
            for (UnitKey key : space.getUnitKeysForPlayer(player)) {
                UnitModel model = player.getPriorityUnitByAsyncID(key.asyncID(), space);
                if (model == null || !ButtonHelper.unitCanSustainDamage(game, player, tile, model)) {
                    continue;
                }
                int undamaged = space.getUnitCount(key) - space.getDamagedUnitCount(key);
                if (undamaged <= 0) {
                    continue;
                }
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + "transformationProtocolDamage_"
                                + key.unitType().name() + "_" + tile.getPosition(),
                        "Damage " + model.getName() + " in " + tile.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }

    @ButtonHandler("transformationProtocolDamage_")
    public static void resolveTransformationProtocolDamage(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace("transformationProtocolDamage_", "").split("_");
        Tile tile = parts.length == 2 ? game.getTileByPosition(parts[1]) : null;
        UnitHolder space = tile == null ? null : tile.getSpaceUnitHolder();
        UnitKey key = parts.length == 2 ? Units.getUnitKey(UnitType.valueOf(parts[0]), player.getColorID()) : null;
        boolean stillUndamaged =
                key != null && space != null && space.getUnitCount(key) - space.getDamagedUnitCount(key) > 0;
        if (space == null || key == null || !stillUndamaged || player.getSpentThingCount("xanships") >= 8) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        space.addDamagedUnit(key, 1);
        StonebornUnitHandler.syncMobileMountainDamage(game, player, key);
        player.increaseSpentThingCount("xanships", 4);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " damaged a ship in "
                        + tile.getRepresentationForButtons(game, player)
                        + " for 4 votes via _Transformation Protocol_.");
        List<Button> remaining = getTransformationProtocolButtons(player, game);
        if (remaining.isEmpty()) {
            ButtonHelper.deleteMessage(event);
        } else {
            MessageHelper.editMessageButtons(event, remaining);
        }
    }

    public static void reminderWarlordsDecree(Player player, Player opponent) {
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", a reminder that you may play _Warlord's Decree_ now to"
                        + " damage up to 3 of " + opponent.getRepresentationNoPing()
                        + "'s ships with SUSTAIN DAMAGE, using the Sustain buttons for their ships in the combat"
                        + " thread.");
    }
}
