package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

import java.util.Collection;
import java.util.HashSet;

public class PlayerCommand implements Command {
    @Override
    public String getActionID() {
        return Constants.PLAYER;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionID());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        if (Constants.INFO.equals(subcommandName)){
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player info received");
        } else if (Constants.TECH.equals(subcommandName)){
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player Tech info received");
        }
    }


    protected String getActionDescription() {
        return "Player";
    }


    private Collection<? extends SubcommandData> getSubcommands()
    {
        Collection<SubcommandData> subcommands = new HashSet<>();
        subcommands.add(new SubcommandData(Constants.INFO, "Player info: CC,TG,Comms,")
                .addOptions(new OptionData(OptionType.STRING, Constants.CC, "CC's Example: 3/3/2"))
                .addOptions(new OptionData(OptionType.STRING, Constants.TG, "Trade goods count"))
                .addOptions(new OptionData(OptionType.STRING, Constants.COMMODITIES, "Commodity count")));
        subcommands.add(new SubcommandData(Constants.TECH, "Player Tech info,"));
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addSubcommands(getSubcommands()));
    }
}
