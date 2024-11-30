package ti4.commands2.custom;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import ti4.commands2.CommandHelper;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

class SetThreadName extends Subcommand {

    public SetThreadName() {
        super(Constants.SET_THREAD_NAME, "Set the name of the thread");
        addOptions(new OptionData(OptionType.STRING, Constants.THREAD_NAME, "New Thread Name").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String gameName = CommandHelper.getGameName(event);
        String name = event.getOption(Constants.THREAD_NAME).getAsString();
        if (event.getMessageChannel() instanceof ThreadChannel channel) {
            ThreadChannelManager manager = channel.getManager();
            if (GameManager.isValidGame(gameName)) {
                manager.setName(gameName + "-" + name).queue();
            } else {
                manager.setName(name).queue();
            }
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Run this command in the thread you are changing");
        }

    }
}
