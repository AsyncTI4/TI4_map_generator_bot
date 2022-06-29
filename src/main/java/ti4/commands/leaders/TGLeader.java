package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.player.Stats;
import ti4.helpers.Constants;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class TGLeader extends LeaderAction {
    public TGLeader() {
        super(Constants.TG, "Add/Remove TG to/from leader");
    }

    @Override
    protected void options() {
        addOptions(new OptionData(OptionType.STRING, Constants.LEADER, "Leader for which to do action")
                .setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TG, "TG count to add to leader").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats").setRequired(false));
    }

    @Override
    void action(SlashCommandInteractionEvent event, String leader, Map activeMap, Player player) {
        Leader playerLeader = player.getLeader(leader);
        if (playerLeader != null) {
            OptionMapping optionTG = event.getOption(Constants.TG);
            if (optionTG != null) {
                Stats.setValue(event, player, optionTG, playerLeader::setTgCount, playerLeader::getTgCount);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "TG count is invalid");
            }
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Leader not found");
        }
    }
}
