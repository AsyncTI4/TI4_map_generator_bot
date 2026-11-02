package ti4.discord.interactions.commands.bothelper;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.fow.AnonymousCommsService;
import ti4.service.fow.FowCommunicationThreadService;
import ti4.service.game.GameNameService;

class DeleteFOWCommThreads extends Subcommand {

    public DeleteFOWCommThreads() {
        super("delete_fow_comm_threads", "DELETE all player-to-player communication threads for this game.");
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm with 'YES'").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.CONFIRM);
        if (!"YES".equals(option.getAsString())) {
            MessageHelper.replyToMessage(
                    event,
                    "Must confirm with `YES`"
                            + ("YES".equalsIgnoreCase(option.getAsString()) ? " - this is case sensitive" : "") + ".");
            return;
        }

        String gameName = GameNameService.getGameName(event);
        Game game = GameManager.getManagedGame(gameName).getGame();

        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "This is not a FOW game.");
            return;
        }

        List<String> anonymousThreads = AnonymousCommsService.deleteAllThreads(game);
        if (!anonymousThreads.isEmpty()) {
            GameManager.save(game, "Deleted anonymous comms threads");
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Deleted anonymous comms threads: " + String.join(", ", anonymousThreads));
        }

        FowCommunicationThreadService.deleteManagedThreads(game, event.getChannel());
    }
}
