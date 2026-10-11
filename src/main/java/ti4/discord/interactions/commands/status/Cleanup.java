package ti4.discord.interactions.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.service.StatusCleanupService;

class Cleanup extends GameStateSubcommand {

    public Cleanup() {
        super(Constants.CLEANUP, "Status Phase cleanup", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm command with YES").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!CommandHelper.confirmedWithYes(event, event.getOption(Constants.CONFIRM))) return;
        Game game = getGame();
        StatusCleanupService.runStatusCleanup(game);
    }
}
