package ti4.commands2.bothelper;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.batch.AWSBatch;
import com.amazonaws.services.batch.AWSBatchClientBuilder;
import com.amazonaws.services.batch.model.SubmitJobRequest;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class BeginVideoGeneration extends Subcommand {

    public BeginVideoGeneration (){
        super(Constants.BEGIN_VIDEO_GEN, "Kickoff Video Process in AWS");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Name of the Game to video-ize.").setRequired(true).setAutoComplete(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        String game = event.getOption(Constants.GAME_NAME).getAsString();
        MessageHelper.sendMessageToEventChannel(event, "Launching Video Creation for:" + game);
        AWSBatch client = AWSBatchClientBuilder.standard().withRegion("us-east-1").build();
        SubmitJobRequest sjr = new SubmitJobRequest();
        sjr.setJobName("video-" + game );
        sjr.setJobDefinition("getting-started-wizard-job-definition:11");
        sjr.setJobQueue("ti4-video-queue");
        Map<String, String> hm = new HashMap<>();
        hm.put("game", game);
        sjr.setParameters(hm);
        client.submitJob(sjr);
    }

}
