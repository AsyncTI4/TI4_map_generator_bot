package ti4.commands.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import ti4.helpers.Constants;
import ti4.helpers.ImageHelper;

public class ResetImageCache extends AdminSubcommandData {

    public ResetImageCache() {
        super(Constants.RESET_IMAGE_CACHE, "Reset image cache for map generation");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ImageHelper.resetCache();
    }
}
