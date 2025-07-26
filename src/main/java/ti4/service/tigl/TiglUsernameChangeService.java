package ti4.service.tigl;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.website.WebHelper;

public class TiglUsernameChangeService {

    public static void changeUsername(SlashCommandInteractionEvent event) {
        var request = new TiglUsernameChangeRequest();
        request.setDiscordId(event.getUser().getId());
        String newName = event.getOption(Constants.TIGL_NICKNAME, null, OptionMapping::getAsString);
        request.setNewTiglUserName(newName);

        WebHelper.sendTiglUsernameChange(request, event.getMessageChannel());
    }
}
