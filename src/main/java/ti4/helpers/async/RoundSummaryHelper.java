package ti4.helpers.async;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.RegexHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.annotations.ModalHandler;
import ti4.listeners.context.ButtonContext;
import ti4.listeners.context.ModalContext;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class RoundSummaryHelper {

    @ButtonHandler("editEndOfRoundSummaries")
    public static void serveEditSummaryButtons(ButtonContext context) {
        Game game = context.getGame();
        Player player = context.getPlayer();
        List<Button> buttons = new ArrayList<>();
        for (int x = 1; x <= game.getRound(); x++)
            buttons.add(editSummaryButton(game, player, x));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), "Choose a round summary to view/edit/create:", buttons);
    }

    public static Button editSummaryButton(Game game, Player player, int round) {
        String roundSummary = game.getStoredValue("endofround" + round + player.getFaction());
        String buttonID = "editRoundSummary_" + round + "~MDL";
        if (roundSummary.isEmpty()) {
            return Buttons.green(buttonID, "Create round " + round + " summary");
        } else {
            return Buttons.blue(buttonID, "Edit round " + round + " summary");
        }
    }

    @ButtonHandler("editRoundSummary_")
    public static void handleEditRoundSummary(ButtonContext context) {
        Game game = context.getGame();
        Player player = context.getPlayer();
        String buttonID = context.getButtonID();
        String roundNum = buttonID.replace("editRoundSummary_", "").replace("~MDL", "");
        ButtonInteractionEvent event = context.getEvent();

        String modalId = "finishEditRoundSummary_" + roundNum;
        String currentSummary = game.getStoredValue("endofround" + roundNum + player.getFaction());
        if (currentSummary.isBlank()) currentSummary = null;

        String fieldID = "summary";
        TextInput summary = TextInput.create(fieldID, "Edit summary", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Edit your round summary here. Or, leave blank to delete it")
            .setValue(currentSummary)
            .build();
        Modal modal = Modal.create(modalId, "End of Round " + roundNum + " Summary").addActionRow(summary).build();
        event.replyModal(modal).queue();
        ButtonHelper.deleteMessage(event);
    }

    @ModalHandler("finishEditRoundSummary_")
    public static void finishEditRoundSummary(ModalContext context) {
        String modalID = context.getModalID();
        String regex = "finishEditRoundSummary_" + RegexHelper.intRegex("round");
        Matcher matcher = Pattern.compile(regex).matcher(modalID);
        if (matcher.matches()) {
            ModalMapping mapping = context.getEvent().getValue("summary");
            String thoughts = mapping.getAsString();
            storeEndOfRoundSummary(context.getGame(), context.getPlayer(), matcher.group("round"), thoughts, false);
        }
    }

    public static void storeEndOfRoundSummary(Game game, Player player, String roundNum, String thoughts, boolean append) {
        roundNum = roundNum.replaceAll("[^0-9]", ""); // I only want the digits
        String roundKey = "endofround" + roundNum + player.getFaction();
        String previousThoughts = "";
        if (append && !game.getStoredValue(roundKey).isEmpty()) {
            previousThoughts = game.getStoredValue(roundKey);
            previousThoughts = previousThoughts.replaceFirst("\\.? ?$", "") + "\n";
        }
        game.setStoredValue(roundKey, previousThoughts + thoughts);

        MessageHelper.sendMessageToChannelWithButton(player.getCardsInfoThread(), player.getFactionEmoji() + " stored an end of round summary", Buttons.EDIT_SUMMARIES);
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Someone stored an end of round summary");
    }
}
