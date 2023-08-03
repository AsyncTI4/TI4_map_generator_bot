package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;

public class RevealSpecificStage2 extends CustomSubcommandData {
    public RevealSpecificStage2() {
        super(Constants.REVEAL_SPECIFIC_STATGE2, "PO to reveal");
        addOptions(new OptionData(OptionType.STRING, Constants.PO_ID, "Public ID").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        OptionMapping soOption = event.getOption(Constants.PO_ID);
        if (soOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify PO");
            return;
        }
        java.util.Map.Entry<String, Integer> objective = activeMap.revealSpecificStage2(soOption.getAsString());
        PublicObjectiveModel po = Mapper.getPublicObjective(objective.getKey());
        StringBuilder sb = new StringBuilder();
        sb.append(Helper.getGamePing(event, activeMap));
        sb.append(" **Stage 2 Public Objective Revealed**").append("\n");
        sb.append("(").append(objective.getValue()).append(") ");
        sb.append(po.getRepresentation()).append("\n");
        MessageHelper.sendMessageToChannelAndPin(activeMap.getMainGameChannel(), sb.toString());
    }
}
