package ti4.commands.bothelper;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.modals.Modal;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.settings.users.UserSettingsManager;

class EditTrackRecord extends Subcommand {

    public EditTrackRecord() {
        super(Constants.EDIT_TRACK_RECORD, "Edit a users track record");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player @playername").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        String userId = event.getOption(Constants.PLAYER).getAsUser().getId();

        String modalId = "finishTrackRecord_" + userId;
        var userSettings = UserSettingsManager.get(userId);
        String prevRecord = userSettings.getTrackRecord();
        if (prevRecord.isEmpty()) {
            prevRecord = "Nothing was previously here.";
        }
        String fieldID = "record";
        TextInput summary = TextInput.create(fieldID, "Edit user's track record", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Edit the user's track record here.")
                .setValue(prevRecord)
                .build();
        Modal modal =
                Modal.create(modalId, "Track Record").addComponents(ActionRow.of(summary)).build();
        event.replyModal(modal).queue();
    }
}
