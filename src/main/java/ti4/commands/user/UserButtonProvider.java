package ti4.commands.user;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

public class UserButtonProvider {
    public static void resolveEditUserSettingsButton(ButtonInteractionEvent event) {
        // Button editPreferredColours = Buttons.green("editUserSettingPreferredColours", "Edit Preferred Colours");
        // Button editFunEmoji = Buttons.green("editUserSettingFunEmoji", "Edit Fun Emoji");
        // List<Button> buttons = Arrays.asList(editPreferredColours, editFunEmoji);
        // MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), null, buttons);
    }

    // public static void resolveEditPreferredColoursButton(ButtonInteractionEvent event) {
    //     TextInput colourList = TextInput.create("colourList", "Colour List", TextInputStyle.SHORT)
    //         .setPlaceholder("Ordered comma separated list of your preferred player colours")
    //         .setMinLength(3)
    //         .setMaxLength(1000)
    //         .build();

    //     Modal modal = Modal.create("editPreferredColours", "Edit Preferred Colours")
    //         .addComponents(ActionRow.of(colourList))
    //         .build();

    //     event.replyModal(modal).queue();
    // }

    // public static void resolveEditFunEmojiButton(ButtonInteractionEvent event) {
    //     TextInput funEmoji = TextInput.create("funEmoji", "Fun Emoji", TextInputStyle.SHORT)
    //         .setPlaceholder("Enter your fun emoji")
    //         .setMinLength(1)
    //         .setMaxLength(1000)
    //         .build();

    //     Modal modal = Modal.create("editFunEmoji", "Edit Fun Emoji")
    //         .addComponents(ActionRow.of(funEmoji))
    //         .build();

    //     event.replyModal(modal).queue();
    // }
}
