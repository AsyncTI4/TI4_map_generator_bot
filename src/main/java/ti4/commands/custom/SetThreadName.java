package ti4.commands.custom;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class SetThreadName extends CustomSubcommandData {
    public SetThreadName() {
        super(Constants.SET_THREAD_NAME, "Set the name of the thread");
        addOptions(new OptionData(OptionType.STRING, Constants.THREAD_NAME, "New Thread Name").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        OptionMapping soOption = event.getOption(Constants.THREAD_NAME);
        if (soOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify Thread Name");
            return;
        }
        String name = soOption.getAsString();
        if (event.getMessageChannel() instanceof ThreadChannel channel) {
            ThreadChannelManager manager = channel.getManager();
            if(activeGame != null){
                manager.setName(activeGame.getName()+"-"+name).queue();
            }else{
                manager.setName(name).queue();
            }
        }else{
            MessageHelper.sendMessageToChannel(event.getChannel(), "Run this command in the thread you are changing");
        }

    }
}
