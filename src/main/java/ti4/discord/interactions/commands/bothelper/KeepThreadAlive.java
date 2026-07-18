package ti4.discord.interactions.commands.bothelper;

import java.util.List;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.Subcommand;
import ti4.message.MessageHelper;
import ti4.service.bothelper.KeepThreadAliveService;

public class KeepThreadAlive extends Subcommand {

    private static final String THREAD = "thread";
    private static final String PIN = "pin";
    private static final String REMOVE = "remove";

    private static final List<Choice> removeOpts = CommandHelper.toChoices("NO", "REMOVE");

    KeepThreadAlive() {
        super("keep_thread_alive", "Keep a thread open and active. Omit options to list kept threads.");
        addOptions(new OptionData(OptionType.CHANNEL, THREAD, "Thread or forum post to keep alive", false)
                .setChannelTypes(ChannelType.GUILD_PUBLIC_THREAD, ChannelType.GUILD_PRIVATE_THREAD));
        addOptions(new OptionData(
                OptionType.BOOLEAN, PIN, "Also keep the post pinned (forum posts only). Default: False", false));
        addOptions(new OptionData(OptionType.STRING, REMOVE, "Remove from the list instead", false)
                .addChoices(removeOpts));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping threadOption = event.getOption(THREAD);
        if (threadOption == null) {
            MessageHelper.sendMessageToEventChannel(event, KeepThreadAliveService.summarize());
            return;
        }
        if (!threadOption.getAsChannel().getType().isThread()) {
            MessageHelper.sendMessageToEventChannel(event, "That channel is not a thread.");
            return;
        }
        ThreadChannel thread = threadOption.getAsChannel().asThreadChannel();

        String removeStr = event.getOption(REMOVE, "no", OptionMapping::getAsString);
        if ("remove".equalsIgnoreCase(removeStr)) {
            KeepThreadAliveService.remove(thread.getId());
            MessageHelper.sendMessageToEventChannel(event, "No longer keeping " + thread.getJumpUrl() + " alive.");
        } else {
            boolean keepPinned = event.getOption(PIN, false, OptionMapping::getAsBoolean);
            KeepThreadAliveService.add(thread, keepPinned);
            KeepThreadAliveService.refreshThread(thread, keepPinned);
            MessageHelper.sendMessageToEventChannel(
                    event, "Now keeping " + thread.getJumpUrl() + " alive" + (keepPinned ? " and pinned" : "") + ".");
        }
        MessageHelper.sendMessageToEventChannel(event, KeepThreadAliveService.summarize());
    }
}
