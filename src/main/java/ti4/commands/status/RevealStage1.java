package ti4.commands.status;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;

public class RevealStage1 extends StatusSubcommandData {
    public RevealStage1() {
        super(Constants.REVEAL_STATGE1, "Reveal Stage1 Public Objective");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        revealS1(event, event.getChannel());
    }

    public void revealS1(GenericInteractionCreateEvent event, MessageChannel channel) {
        Map activeMap = MapManager.getInstance().getUserActiveMap(event.getUser().getId());

        java.util.Map.Entry<String, Integer> objective = activeMap.revealState1();


        PublicObjectiveModel po = Mapper.getPublicObjective(objective.getKey());
        String objectiveName = po.name;
        String objectiveDescription = po.text;

        StringBuilder sb = new StringBuilder();
        sb.append(Helper.getGamePing(event, activeMap));
        sb.append(" **Stage 1 Public Objective Revealed**").append("\n");
        sb.append("(").append(objective.getValue()).append(") ");
        sb.append(Emojis.Public1alt).append(" ");
        sb.append("**").append(objectiveName).append("** - ").append(objectiveDescription).append("\n");
        MessageHelper.sendMessageToChannelAndPin(channel, sb.toString());
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Map activeMap = MapManager.getInstance().getUserActiveMap(userID);
        MapSaveLoadManager.saveMap(activeMap, event);
    }
}
