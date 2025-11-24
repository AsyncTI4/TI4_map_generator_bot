package ti4.commands.bothelper;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.message.MessageHelper;
import ti4.service.fow.FowCommunicationThreadService;
import ti4.service.game.GameNameService;

class DeleteFOWCommThreads extends Subcommand {

    public DeleteFOWCommThreads() {
        super("delete_fow_comm_threads", "DELETE all player-to-player communication threads for this game.");
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm with 'YES'").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!"YES".equalsIgnoreCase(event.getOption(Constants.CONFIRM).getAsString())) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Must confirm with YES. (case sensitive/full uppercase YES)");
            return;
        }

        String gameName = GameNameService.getGameName(event);
        Game game = GameManager.getManagedGame(gameName).getGame();

        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "This is not a FOW game.");
            return;
        }

        FowCommunicationThreadService.getGameThreadChannels(game).thenAccept(threads -> {
            for (ThreadChannel thread : threads) {
                Matcher matcher = FowCommunicationThreadService.THREAD_NAME_PATTERN.matcher(thread.getName());
                if (!matcher.find()) {
                    continue;
                }

                String threadName = thread.getName();
                thread.delete()
                        .onSuccess(v -> {})
                        .onErrorFlatMap(err -> {
                            MessageHelper.sendMessageToChannel(
                                    event.getChannel(),
                                    "Error deleting thread: " + threadName + " : " + err.getMessage());
                            return null;
                        })
                        .queueAfter(1, TimeUnit.SECONDS);
                MessageHelper.sendMessageToChannel(event.getChannel(), "Deleted thread: " + threadName);
            }
        });
    }
}
