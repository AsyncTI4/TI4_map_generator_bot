package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.*;
import ti4.message.MessageHelper;

public class AdjustRoundNumber extends SpecialSubcommandData {
    public AdjustRoundNumber() {
        super(Constants.ADJUST_ROUND_NUMBER, "Adjust round number of game");
        addOptions(new OptionData(OptionType.INTEGER, Constants.ROUND, "Round number").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        
        OptionMapping roundOption = event.getOption(Constants.ROUND);
        if (roundOption == null){
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify round number");
            return;
        }
        activeMap.setRound(roundOption.getAsInt());
    }
}
