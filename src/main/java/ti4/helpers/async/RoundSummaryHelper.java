package ti4.helpers.async;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import ti4.buttons.Buttons;
import ti4.helpers.RegexHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.annotations.ModalHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.TI4Emoji;

public class RoundSummaryHelper {

    @ButtonHandler("editEndOfRoundSummaries")
    public static void serveEditSummaryButtons(Game game, Player player, MessageChannel eventChannel) {
        List<Button> buttons = new ArrayList<>();
        for (int x = 1; x <= game.getRound(); x++)
            buttons.add(editSummaryButton(game, player, x));
        MessageChannel playerChannel = player.isRealPlayer() ? player.getCardsInfoThread() : eventChannel;
        MessageHelper.sendMessageToChannelWithButtons(playerChannel, "Choose a round summary to view/edit/create:", buttons);
    }

    public static Button editSummaryButton(Game game, Player player, int round) {
        String roundSummary = game.getStoredValue(resolveRoundSummaryKey(player, String.valueOf(round)));
        String buttonID = "editRoundSummary_" + round + "~MDL";
        if (roundSummary.isEmpty()) {
            return Buttons.green(buttonID, "Create round " + round + " summary");
        } else {
            return Buttons.blue(buttonID, "Edit round " + round + " summary");
        }
    }

    @ButtonHandler("editRoundSummary_")
    public static void handleEditRoundSummary(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String roundNum = buttonID.replace("editRoundSummary_", "").replace("~MDL", "");

        String modalId = "finishEditRoundSummary_" + roundNum;
        String currentSummary = game.getStoredValue(resolveRoundSummaryKey(player, roundNum));
        if (currentSummary.isBlank()) currentSummary = null;

        String fieldID = "summary";
        TextInput summary = TextInput.create(fieldID, "Edit summary", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Edit your round summary here. Or, leave blank to delete it")
            .setValue(currentSummary)
            .build();
        Modal modal = Modal.create(modalId, "End of Round " + roundNum + " Summary").addActionRow(summary).build();
        event.replyModal(modal).queue();
        //ButtonHelper.deleteMessage(event); Breaks submiting the summary for some reason
    }

    @ModalHandler("finishEditRoundSummary_")
    public static void finishEditRoundSummary(ModalInteractionEvent event, Game game, Player player, String modalID) {
        String regex = "finishEditRoundSummary_" + RegexHelper.intRegex("round");
        Matcher matcher = Pattern.compile(regex).matcher(modalID);
        if (matcher.matches()) {
            ModalMapping mapping = event.getValue("summary");
            String thoughts = mapping.getAsString();
            storeEndOfRoundSummary(game, player, matcher.group("round"), thoughts, false, event.getChannel());
        }
    }

    public static void storeEndOfRoundSummary(Game game, Player player, String roundNum, String thoughts, boolean append, MessageChannel eventChannel) {
        roundNum = roundNum.replaceAll("[^0-9]", ""); // I only want the digits
        String roundKey = resolveRoundSummaryKey(player, roundNum);
        String previousThoughts = "";
        if (append && !game.getStoredValue(roundKey).isEmpty()) {
            previousThoughts = game.getStoredValue(roundKey);
            previousThoughts = previousThoughts.replaceFirst("\\.? ?$", "") + "\n";
        }
        game.setStoredValue(roundKey, previousThoughts + thoughts);

        MessageChannel playerChannel = player.isRealPlayer() ? player.getCardsInfoThread() : eventChannel;
        MessageHelper.sendMessageToChannelWithButton(playerChannel, resolvePlayerEmoji(player) + " stored a round summary.", Buttons.EDIT_SUMMARIES);
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), 
          (game.getPlayersWithGMRole().contains(player) ? "GM" : "A player") + " has stored a round summary.");
    }

    public static String resolveRoundSummaryKey(Player player, String roundNum) {
        String keySuffix = player.isRealPlayer() || player.isEliminated() ? player.getFaction() : player.getUserName();
        return "endofround" + roundNum + keySuffix;
    }

    public static String resolvePlayerEmoji(Player player) {
        return player.isRealPlayer() || player.isEliminated() ? player.getFactionEmoji() : TI4Emoji.getRandomGoodDog(player.getUserID()).toString();
    }
}
