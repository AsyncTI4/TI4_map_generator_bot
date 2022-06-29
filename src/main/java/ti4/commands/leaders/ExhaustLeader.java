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

public class ExhaustLeader extends LeaderAction {
    public ExhaustLeader() {
        super(Constants.EXHAUST_LEADER, "Exhaust leader");
        addOptions(new OptionData(OptionType.STRING, Constants.TG, "TG count to add to leader").setRequired(false));
    }

    @Override
    void action(SlashCommandInteractionEvent event, String leader, Map activeMap, Player player) {
        Leader playerLeader = player.getLeader(leader);
        if (playerLeader != null) {
            if (playerLeader.isLocked()){
                MessageHelper.sendMessageToChannel(event.getChannel(), "Leader is locked");
                return;
            }
            playerLeader.setExhausted(true);
            OptionMapping optionTG = event.getOption(Constants.TG);
            if (optionTG != null) {
                Stats.setValue(event, player, optionTG, playerLeader::setTgCount, playerLeader::getTgCount);
            }
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Leader not found");
        }
    }
}
