package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.StatusCleanupService;

class Cleanup extends GameStateSubcommand {

    public Cleanup() {
        super(Constants.CLEANUP, "Status Phase cleanup", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm command with YES").setRequired(true));
    }

    @Override

    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.CONFIRM);
        if (option == null || !"YES".equals(option.getAsString())) {
            MessageHelper.replyToMessage(event, "Must confirm with YES");
            return;
        }
        Game game = getGame();
        StatusCleanupService.runStatusCleanup(game);
    }
}
