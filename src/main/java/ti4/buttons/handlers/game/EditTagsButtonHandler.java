package ti4.buttons.handlers.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.annotations.ModalHandler;
import ti4.map.Game;
import ti4.message.MessageHelper;

@UtilityClass
class EditTagsButtonHandler {

    @ButtonHandler("editTags~MDL")
    public static void handleEditTags(ButtonInteractionEvent event, Game game) {
        String modalId = "editTags";
        String currentTags = String.join(";", game.getTags());
        if (currentTags.isBlank()) currentTags = null;

        String fieldID = "tags";
        TextInput tags = TextInput.create(fieldID, "Edit Tags", TextInputStyle.SHORT)
                .setPlaceholder("Add tags here, separated by semicolons. Leave blank to delete all tags.")
                .setValue(currentTags)
                .build();
        Modal modal = Modal.create(modalId, "Tags for Game " + game.getName())
            .addComponents(ActionRow.of(tags))
                .build();
        event.replyModal(modal).queue();
    }

    @ModalHandler("editTags")
    public static void finishEditTags(ModalInteractionEvent event, Game game) {
        ModalMapping mapping = event.getValue("tags");
        String tagsRaw = mapping.getAsString();
        List<String> currentTags = game.getTags();
        if (tagsRaw.isEmpty()) {
            game.setTags(new ArrayList<>());
        } else {
            List<String> tags = new ArrayList<>(Arrays.asList((tagsRaw.split(";"))));
            game.setTags(tags);
        }
        MessageHelper.sendMessageToEventChannel(
                event, "Changed tags from `" + currentTags + "` to `" + game.getTags() + "`");
    }
}
