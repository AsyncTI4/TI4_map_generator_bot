package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.lunarium;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.objectives.DrawSecretService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class LunariumAbilityHandler {

    private static final String CC_KEY = "lunariumFactionSheetCCFor";

    public static int getFactionSheetCCs(Game game, Player player) {
        String val = game.getStoredValue(CC_KEY + player.getFaction());
        if (val.isEmpty()) return 0;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void setFactionSheetCCs(Game game, Player player, int count) {
        game.setStoredValue(CC_KEY + player.getFaction(), String.valueOf(count));
    }

    private static String buildButtonMessage(Player player, int sheetCCs) {
        return player.getRepresentationUnfogged() + " You have **" + sheetCCs
                + "** token(s) on your faction sheet (**Multitasking**). Use buttons to move tokens between your fleet pool and faction sheet.";
    }

    public static void offerFactionSheetCCButtons(Game game, Player player) {
        List<Button> buttons = List.of(
                Buttons.green("lunariumMoveCCToSheet", "Move Token to Faction Sheet", FactionEmojis.lunarium),
                Buttons.red("lunariumMoveCCFromSheet", "Move Token from Faction Sheet", FactionEmojis.lunarium),
                Buttons.gray("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(), buildButtonMessage(player, getFactionSheetCCs(game, player)), buttons);
    }

    @ButtonHandler("lunariumMoveCCToSheet")
    public static void moveCCToSheet(ButtonInteractionEvent event, Game game, Player player) {
        if (player.getFleetCC() <= 0) {
            MessageHelper.sendMessageToEventChannel(
                    event, player.getRepresentation() + " has no fleet tokens to move to the faction sheet.");
            return;
        }
        player.setFleetCC(player.getFleetCC() - 1);
        int newSheet = getFactionSheetCCs(game, player) + 1;
        setFactionSheetCCs(game, player, newSheet);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " moved 1 token from fleet pool to faction sheet (**Multitasking**). Fleet: "
                        + player.getFleetCC() + ". Faction sheet: " + newSheet + ".");
        event.getMessage()
                .editMessage(buildButtonMessage(player, newSheet))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("lunariumMoveCCFromSheet")
    public static void moveCCFromSheet(ButtonInteractionEvent event, Game game, Player player) {
        int sheetCCs = getFactionSheetCCs(game, player);
        if (sheetCCs <= 0) {
            MessageHelper.sendMessageToEventChannel(
                    event, player.getRepresentation() + " has no faction sheet tokens to move back to the fleet pool.");
            return;
        }
        setFactionSheetCCs(game, player, sheetCCs - 1);
        player.setFleetCC(player.getFleetCC() + 1);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " moved 1 token from faction sheet to fleet pool (**Multitasking**). Fleet: "
                        + player.getFleetCC() + ". Faction sheet: " + (sheetCCs - 1) + ".");
        event.getMessage()
                .editMessage(buildButtonMessage(player, sheetCCs - 1))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    public static void resolveCrypticHaulerIIFighters(
            ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        int carriers = tile.getSpaceUnitHolder().getUnitCount(UnitType.Carrier, player);
        if (carriers <= 0) return;
        AddUnitService.addUnits(event, tile, game, player.getColor(), carriers + " fighter");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " placed " + carriers + " fighter" + (carriers > 1 ? "s" : "")
                        + " in " + tile.getRepresentationForButtons()
                        + " using Cryptic Hauler II's ability. This is optional, but was done automatically.");
    }

    public static void offerSubliminalIntuitionButtons(MessageChannel channel, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(player.factionButtonChecker() + "lunariumSubliminalSO", "Draw 1 Secret Objective"));
        String msg;
        if (player.hasTechReady("balunag")) {
            buttons.add(Buttons.blue(player.factionButtonChecker() + "lunariumSubliminalCC", "Gain 2 Command Tokens"));
            msg = player.getRepresentationUnfogged()
                    + ", you may use _Subliminal Intuition_ to draw 1 secret objective or gain 2 command tokens.";
        } else {
            msg = player.getRepresentationUnfogged()
                    + ", you may use _Subliminal Intuition_ to draw 1 secret objective (already exhausted, so you cannot gain command tokens with it).";
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(channel, msg, buttons);
    }

    @ButtonHandler("lunariumSubliminalSO")
    public static void resolveSubliminalSO(ButtonInteractionEvent event, Game game, Player player) {
        DrawSecretService.drawSO(event, game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("lunariumSubliminalCC")
    public static void resolveSubliminalCC(ButtonInteractionEvent event, Player player) {
        player.exhaustTech("balunag");
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", use buttons to gain 2 command tokens (_Subliminal Intuition_, now exhausted).",
                ButtonHelper.getGainCCButtons(player));
        ButtonHelper.deleteMessage(event);
    }
}
