package ti4.message.componentsV2;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.ModalTopLevelComponent;
import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.logging.BotLogger;

@UtilityClass
public class PanelForms {

    public static Label field(String label, String id, String value, boolean required) {
        TextInput.Builder input = TextInput.create(id, TextInputStyle.SHORT).setRequired(required);
        if (StringUtils.isNotBlank(value)) input.setValue(value);
        return Label.of(label, input.build());
    }

    public static void open(ButtonInteractionEvent event, String id, String title, ModalTopLevelComponent... parts) {
        event.replyModal(Modal.create(id, title).addComponents(parts).build())
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    public static String value(ModalInteractionEvent event, String field) {
        ModalMapping mapping = event.getValue(field);
        return mapping == null ? "" : mapping.getAsString().trim();
    }

    public static boolean checked(ModalInteractionEvent event, String field) {
        ModalMapping mapping = event.getValue(field);
        return mapping != null && mapping.getAsBoolean();
    }

    public static Integer parseInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Modal urlOrFileModal(String modalId, String title, String explanation, String urlPlaceholder) {
        TextInput url = TextInput.create("url", TextInputStyle.SHORT)
                .setRequired(false)
                .setPlaceholder(urlPlaceholder)
                .build();
        AttachmentUpload file = AttachmentUpload.create("file")
                .setRequiredRange(0, 1)
                .setRequired(false)
                .build();
        return Modal.create(modalId, title)
                .addComponents(
                        TextDisplay.of(explanation),
                        Label.of("JSON URL", url),
                        Label.of("Or upload the .json file", file))
                .build();
    }

    public static String urlOrFile(ModalInteractionEvent event) {
        ModalMapping file = event.getValue("file");
        List<Message.Attachment> attachments = file == null ? List.of() : file.getAsAttachmentList();
        if (!attachments.isEmpty()) {
            return attachments.getFirst().getUrl();
        }
        ModalMapping url = event.getValue("url");
        return url == null ? "" : url.getAsString().trim();
    }
}
