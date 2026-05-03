package ti4.discord.interactions.buttons.handlers.combat;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.contest.replay.service.CombatReplayService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.TemporaryCombatModifierModel;
import ti4.service.fow.FOWCombatThreadMirroring;
import ti4.spring.context.SpringContext;

@UtilityClass
class CombatButtonHandler {

    @ButtonHandler("automateGroundCombat_")
    public static void automateGroundCombat(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String faction1 = buttonID.split("_")[1];
        String faction2 = buttonID.split("_")[2];
        Player p1 = game.getPlayerFromColorOrFaction(faction1);
        Player p2 = game.getPlayerFromColorOrFaction(faction2);
        String planet = buttonID.split("_")[3];
        String confirmed = buttonID.split("_")[4];
        if (player != p1 && player != p2) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "This button is only for combat participants.");
            return;
        }
        Player opponent;
        if (player == p2) {
            opponent = p1;
        } else {
            opponent = p2;
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        if (opponent == null || opponent.isDummy() || "confirmed".equalsIgnoreCase(confirmed)) {
            ButtonHelperModifyUnits.automateGroundCombat(p1, p2, planet, game, event);
        } else if (p1 != null && p2 != null) {
            Button automate = Buttons.green(
                    opponent.factionButtonChecker() + "automateGroundCombat_" + p1.getFaction() + "_" + p2.getFaction()
                            + "_" + planet + "_confirmed",
                    "Automate Combat");
            MessageHelper.sendMessageToChannelWithButton(
                    event.getMessageChannel(),
                    opponent.getRepresentation()
                            + " Your opponent has voted to automate the entire combat. Press to confirm:",
                    automate);
        }
    }

    @ButtonHandler("declinePDS_")
    public static void declinePDS(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTile(buttonID.split("_")[1]);
        String msg = player.getRepresentationNoPing() + " officially declines to fire SPACE CANNON"
                + (tile != null ? " at " + tile.getRepresentation() : "") + ".";
        if (game.isFowMode()) {
            String targetFaction = buttonID.split("_")[2];
            Player target = game.getPlayerFromColorOrFaction(targetFaction);
            if (target != null && target.getPrivateChannel() != null) {
                MessageHelper.sendMessageToChannel(
                        target.getPrivateChannel(), target.getRepresentationUnfogged() + " " + msg);
            }
        }
        MessageHelper.sendMessageToChannel(
                game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(), msg);
    }

    @ButtonHandler("applytempcombatmod__" + Constants.AC + "__")
    static void applyTemporaryCombatMod(ButtonInteractionEvent event, Player player, String buttonID) {
        String acAlias = buttonID.substring(buttonID.lastIndexOf("__") + 2);
        TemporaryCombatModifierModel combatModAC =
                CombatTempModHelper.getPossibleTempModifier(Constants.AC, acAlias, player.getNumberOfTurns());
        if (combatModAC != null) {
            player.addNewTempCombatMod(combatModAC);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Combat modifier will be applied next time you push the combat roll button.");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("applytempcombatmod__tech__")
    public static void applyTempCombatModTech(ButtonInteractionEvent event, Player player) {
        String acAlias = "sc";
        TemporaryCombatModifierModel combatModAC =
                CombatTempModHelper.getPossibleTempModifier("tech", acAlias, player.getNumberOfTurns());
        if (combatModAC != null) {
            player.addNewTempCombatMod(combatModAC);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getFactionEmoji()
                            + ", +1 modifier will be applied the next time you push the combat roll button due to _Supercharge_.");
        }
        player.exhaustTech("sc");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("pay1tgToAnnounceARetreat")
    public static void pay1tgToAnnounceARetreat(ButtonInteractionEvent event, Player player) {
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getFactionEmojiOrColor() + " paid 1 trade good to announce a retreat " + player.gainTG(-1)
                        + ".");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("announceReadyForDice_")
    public static void announceReadyForDice(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String p1Color = buttonID.split("_")[1];
        Player p1 = game.getPlayerFromColorOrFaction(p1Color);
        if (p1 == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Unable to determine player for color or faction `" + p1Color + "`.");
            return;
        }
        String p2Color = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(p2Color);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Unable to determine player for color or faction `" + p2Color + "`.");
            return;
        }
        if (player != p1 && player != p2) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", don't press buttons that aren't meant for you.");
            return;
        }
        String msg =
                ", your opponent has declared they have no more abilities to resolve at this step and are ready to proceed to dice rolling when you are.";
        if (player == p1 || player.getAllianceMembers().contains(p1.getFaction())) {
            msg = p2.getRepresentation(false, true) + msg;
        } else {
            msg = p1.getRepresentation(false, true) + msg;
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
    }

    @ButtonHandler("announceARetreat")
    public static void announceARetreat(ButtonInteractionEvent event, Player player, Game game) {
        String msg = "## " + player.getRepresentationNoPing() + " has announced a retreat.";
        if (game.playerHasLeaderUnlockedOrAlliance(player, "nokarcommander")) {
            msg +=
                    "\n> Since they have Jack Hallard, the Nokar commander, this means they may cancel 2 hits in this coming combat round.";
        }
        String combatName =
                "combatRoundTracker" + game.getActivePlayer().getFaction() + game.getActiveSystem() + "space";
        if (game.getActivePlayer() != null
                && game.getActivePlayer() != player
                && game.getActivePlayer().hasAbility("cargo_raiders")
                && game.getStoredValue(combatName).isEmpty()) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("pay1tgToAnnounceARetreat", "Pay 1 Trade Good"));
            buttons.add(Buttons.red("deleteButtons", "I Don't Have to Pay"));
            String raiders = "\n" + player.getRepresentation()
                    + ", a reminder that your opponent has the **Cargo Raiders** ability,"
                    + " which means you might have to pay 1 trade good to announce a retreat if they choose.";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg + raiders, buttons);
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        }
        FOWCombatThreadMirroring.mirrorMessage(event, game, msg.replace("## ", ""));
        SpringContext.getBean(CombatReplayService.class)
                .mirrorRetreatDeclared(game, player, event.getChannel().getName());

        if (Helper.getCCCount(game, player.getColor()) > 15) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", a reminder that you are at the command token limit right now,"
                            + " so may need to pull a command token off your command sheet in order to retreat (unless you retreat to a system that already has one).");
        }
        if (ButtonHelperModifyUnits.getRetreatSystemButtons(player, game, game.getActiveSystem(), false, false)
                .isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "## However, there are no valid systems to retreat to!");
        }
    }

    @ButtonHandler("autoAssignAFBHits_")
    public static void autoAssignAFBHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelperModifyUnits.autoAssignAntiFighterBarrageHits(
                player, game, buttonID.split("_")[1], Integer.parseInt(buttonID.split("_")[2]), event);
    }

    @ButtonHandler("cancelAFBHits_")
    public static void cancelAFBHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        int h = Integer.parseInt(buttonID.split("_")[2]) - 1;
        String msg = "\n" + player.getRepresentationUnfogged() + " canceled 1 hit with an ability.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        List<Button> buttons = new ArrayList<>();
        String factionChecker = "FFCC_" + player.getFaction() + "_";
        buttons.add(Buttons.green(
                factionChecker + "autoAssignAFBHits_" + tile.getPosition() + "_" + h,
                "Auto-assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.red(
                "getDamageButtons_" + tile.getPosition() + "_afb", "Manually Assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.gray("cancelAFBHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
        String msg2 = "You may automatically assign " + h + " ANTI-FIGHTER BARRAGE hit" + (h == 1 ? "" : "s") + ".";
        event.getMessage()
                .editMessage(msg2)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("cancelPdsOffenseHits_")
    public static void cancelPDSOffenseHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        int h = Integer.parseInt(buttonID.split("_")[2]) - 1;
        String msg = "\n" + player.getRepresentationUnfogged() + " canceled 1 hit with an ability.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        List<Button> buttons = new ArrayList<>();
        String factionChecker = "FFCC_" + player.getFaction() + "_";
        buttons.add(Buttons.green(
                factionChecker + "autoAssignSpaceCannonOffenceHits_" + tile.getPosition() + "_" + h,
                "Auto-Assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.red(
                "getDamageButtons_" + tile.getPosition() + "_pds", "Manually Assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.gray("cancelPdsOffenseHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
        String msg2 = player.getRepresentationNoPing() + ", you may automatically assign "
                + (h == 1 ? "the hit" : "hits") + ". "
                + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, h, event, true, true);
        event.getMessage()
                .editMessage(msg2)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("cancelGroundHits_")
    public static void cancelGroundHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        int h = Integer.parseInt(buttonID.split("_")[2]) - 1;
        String msg = "\n" + player.getRepresentationUnfogged() + " canceled 1 hit with an ability";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        List<Button> buttons = new ArrayList<>();
        String factionChecker = "FFCC_" + player.getFaction() + "_";
        buttons.add(Buttons.green(
                factionChecker + "autoAssignGroundHits_" + tile.getPosition() + "_" + h,
                "Auto-assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.red(
                "getDamageButtons_" + tile.getPosition() + "_groundcombat",
                "Manually Assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.gray("cancelGroundHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
        String msg2 = player.getRepresentation() + " you may autoassign " + h + " hit" + (h == 1 ? "" : "s") + ".";
        event.getMessage()
                .editMessage(msg2)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("cancelSpaceHits_")
    public static void cancelSpaceHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        int h = Integer.parseInt(buttonID.split("_")[2]) - 1;
        String msg = "\n" + player.getRepresentationUnfogged() + " canceled 1 hit with an ability";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        List<Button> buttons = new ArrayList<>();
        String factionChecker = "FFCC_" + player.getFaction() + "_";
        buttons.add(Buttons.green(
                factionChecker + "autoAssignSpaceHits_" + tile.getPosition() + "_" + h,
                "Auto-assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.red(
                "getDamageButtons_" + tile.getPosition() + "_spacecombat",
                "Manually Assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.gray("cancelSpaceHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
        String msg2 = player.getRepresentationNoPing() + ", you may automatically assign "
                + (h == 1 ? "the hit" : "hits") + ". "
                + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, h, event, true);
        event.getMessage()
                .editMessage(msg2)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("autoAssignSpaceCannonOffenceHits_")
    public static void autoAssignSpaceCannonOffenceHits(
            ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(
                        player,
                        game,
                        game.getTileByPosition(buttonID.split("_")[1]),
                        Integer.parseInt(buttonID.split("_")[2]),
                        event,
                        false,
                        true));
    }

    @ButtonHandler("autoAssignSpaceHits_")
    public static void autoAssignSpaceHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(
                        player,
                        game,
                        game.getTileByPosition(buttonID.split("_")[1]),
                        Integer.parseInt(buttonID.split("_")[2]),
                        event,
                        false));
    }
}
