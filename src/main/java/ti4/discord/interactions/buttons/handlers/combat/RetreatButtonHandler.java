package ti4.discord.interactions.buttons.handlers.combat;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.contest.replay.service.CombatReplayService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.BreakthroughModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.fow.FOWCombatThreadMirroring;
import ti4.service.fow.LoreService;
import ti4.service.unit.CheckUnitContainmentService;
import ti4.spring.context.SpringContext;

@UtilityClass
class RetreatButtonHandler {

    @ButtonHandler("retreat_")
    public static void retreat(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        boolean skilled = false;
        boolean feint = false;
        if (buttonID.contains("skilled")) {
            if (game.isTwilightsFallMode()) {
                feint = true;
            }
            skilled = true;
            ButtonHelper.deleteMessage(event);
        }
        if (buttonID.contains("foresight")) {
            if (!game.isTwilightsFallMode()) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        player.getFactionEmojiOrColor()
                                + ", you placed 1 command token from your strategy pool to resolve your "
                                + FactionEmojis.Naalu
                                + "**Foresight** ability.");
                player.setStrategicCC(player.getStrategicCC() - 1);
            }
            skilled = true;
        }

        if (buttonID.contains("gheminabt")) {
            String btID = "gheminabt";
            Player p1 = player;
            BreakthroughModel btModel = Mapper.getBreakthrough(btID);
            p1.getBreakthroughExhausted().put(btID, true);
            String message = p1.getRepresentation() + " exhausted _" + btModel.getName() + "_ to immediately retreat.";
            MessageHelper.sendMessageToChannelWithEmbed(
                    p1.getCorrectChannel(), message, btModel.getRepresentationEmbed());
        }

        String message = player.getRepresentationUnfogged() + ", please choose a system to move to.";
        List<Button> retreatButtons =
                ButtonHelperModifyUnits.getRetreatSystemButtons(player, game, pos, skilled, feint);
        if (retreatButtons.isEmpty()) {
            message = player.getRepresentationUnfogged() + ", there are no valid systems to retreat to.";
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, retreatButtons);

        if (game.getTileByPosition(pos).isGravityRift()
                && !player.hasRelic("circletofthevoid")
                && !player.hasTech("tf-crucible")) {
            Button rift = Buttons.green(
                    player.getFinsFactionCheckerPrefix() + "getRiftButtons_" + pos,
                    "Rift Units",
                    MiscEmojis.GravityRift);
            List<Button> buttons = new ArrayList<>();
            buttons.add(rift);
            String message2 = "## " + player.getRepresentationUnfogged()
                    + ", if applicable, use this button to rift retreating units __before__ choosing where to retreat. It needs to be before you actually select where to retreat.";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message2, buttons);
        }
    }

    @ButtonHandler("retreatUnitsFrom_")
    public static void retreatUnitsFrom(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelperModifyUnits.retreatSpaceUnits(buttonID, event, game, player);
        String both = buttonID.replace("retreatUnitsFrom_", "");
        String pos1 = both.split("_")[0];
        String pos2 = both.split("_")[1];
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " retreated all units in space to "
                        + game.getTileByPosition(pos2).getRepresentationForButtons(game, player) + ".");
        SpringContext.getBean(CombatReplayService.class)
                .mirrorRetreatResolved(
                        game,
                        player,
                        game.getTileByPosition(pos2).getRepresentationForButtons(game, player),
                        event.getChannel().getName());
        LoreService.showSystemLore(player, game, pos2, LoreService.TRIGGER.CONTROLLED);
        FOWCombatThreadMirroring.mirrorMessage(
                event, game, player.getRepresentationNoPing() + " retreated all units in space.");
        String message =
                player.getRepresentationUnfogged() + ", please choose which ground forces you wish to retreat.";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                message,
                ButtonHelperModifyUnits.getRetreatingGroundTroopsButtons(player, game, pos1, pos2));
        Tile oldTile = game.getTileFromPlanet("avernus");
        if (player.hasUnlockedBreakthrough("muaatbt")
                && CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, UnitType.Warsun)
                        .contains(game.getTileByPosition(pos2))
                && !game.getTileByPosition(pos2).isHomeSystem(game)
                && game.getTileByPosition(pos1) == oldTile) {

            List<Button> breakthroughButtons = new ArrayList<>();
            breakthroughButtons.add(
                    Buttons.blue(player.finChecker() + "moveAvernus_" + pos2, "Retreat Avernus", FactionEmojis.Muaat));
            breakthroughButtons.add(Buttons.red("deleteButtons", "Decline"));
            String breakthroughMessage = player.getRepresentationUnfogged() + ", you may move Avernus into "
                    + game.getTileByPosition(pos2).getRepresentationForButtons(game, player) + ".";
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(), breakthroughMessage, breakthroughButtons);
        }

        if (player.ownsUnit("greentf_flagship")
                && CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, UnitType.Flagship)
                        .contains(game.getTileByPosition(pos2))
                && Helper.getProductionValue(player, game, game.getTileByPosition(pos1), false) > 0) {
            List<Button> flagButtons = new ArrayList<>();
            flagButtons.add(Buttons.blue(
                    player.finChecker() + "anarchy7Build_" + pos1, "Build in " + pos1, FactionEmojis.Muaat));
            flagButtons.add(Buttons.red("deleteButtons", "Decline"));
            String flagMessage = player.getRepresentationUnfogged()
                    + ", you may build in the system your flagship is retreating from.";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), flagMessage, flagButtons);
        }

        ButtonHelper.deleteMessage(event);
    }
}
