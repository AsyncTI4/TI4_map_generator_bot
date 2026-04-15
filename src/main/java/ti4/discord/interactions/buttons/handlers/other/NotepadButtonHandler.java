package ti4.discord.interactions.buttons.handlers.other;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInput.Builder;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.game.Player;
import ti4.helpers.StringHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
class NotepadButtonHandler {
    private static String getNotes(Player player) {
        return StringHelper.unescape(player.getNotes());
    }

    private static void saveNotes(Player player, String notes) {
        player.setNotes(StringHelper.escape(notes));
    }

    @ButtonHandler(value = "notepadPost", save = false)
    public static void postNotepad(Player player) {
        String notes = "__Here are your notes:__\n" + getNotes(player);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), notes);
    }

    @ButtonHandler(value = "notepadEdit~MDL", save = false)
    public static void editNotepad(ButtonInteractionEvent event, Player player) {
        String modalID = "notepadModal";
        String fieldID = "notes";
        String notes = getNotes(player);
        Builder textInputBuilder =
                TextInput.create(fieldID, TextInputStyle.PARAGRAPH).setPlaceholder("Start typing your notes...");
        if (!notes.isBlank()) {
            textInputBuilder.setValue(notes);
        }
        String title = player.getFlexibleDisplayName() + "'s Notepad";
        title = title.substring(0, Math.min(title.length(), 45));
        Modal modal = Modal.create(modalID, title)
                .addComponents(Label.of("Edit summary", textInputBuilder.build()))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler("notepadModal")
    public static void finishEditNotepad(ModalInteractionEvent event, Player player) {
        String newNotes = event.getValue("notes").getAsString();
        saveNotes(player, newNotes);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Successfully saved your notes!");
    }
}
