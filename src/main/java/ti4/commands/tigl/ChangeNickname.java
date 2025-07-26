package ti4.commands.tigl;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.tigl.TiglUsernameChangeService;

class ChangeNickname extends Subcommand {

    public ChangeNickname() {
        super(Constants.TIGL_CHANGE_NICKNAME, "Change your TIGL nickname");
        addOptions(new OptionData(OptionType.STRING, Constants.TIGL_NICKNAME, "New TIGL nickname")
            .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        TiglUsernameChangeService.changeUsername(event);
    }
}
