package ti4.helpers.async;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import ti4.helpers.StringHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.annotations.ModalHandler;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class NotepadHelper {
    private static String getNotes(Player player) {
        return StringHelper.unescape(player.getNotes());
    }

    private static void saveNotes(Player player, String notes) {
        player.setNotes(StringHelper.escape(notes));
    }

    @ButtonHandler("notepadPost")
    public static void postNotepad(Player player) {
        String notes = "__Here are your notes:__\n" + getNotes(player);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), notes);
    }

    @ButtonHandler("notepadEdit~MDL")
    public static void editNotepad(ButtonInteractionEvent event, Player player) {
        String modalID = "notepadModal";
        String fieldID = "notes";
        String notes = getNotes(player);
        TextInput.Builder bob = TextInput.create(fieldID, "Edit summary", TextInputStyle.PARAGRAPH).setPlaceholder("Start typing your notes...");
        if (!notes.isBlank()) bob.setValue(notes);
        Modal modal = Modal.create(modalID, player.getFlexibleDisplayName() + "'s Notepad").addActionRow(bob.build()).build();
        event.replyModal(modal).queue();
    }

    @ModalHandler("notepadModal")
    public static void finishEditNotepad(ModalInteractionEvent event, Player player) {
        String newNotes = event.getValue("notes").getAsString();
        saveNotes(player, newNotes);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Successfully saved your notes!");
    }
}
