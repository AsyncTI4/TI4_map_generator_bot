package ti4.commands.bothelper;

import java.util.HashMap;

import com.amazonaws.services.batch.AWSBatch;
import com.amazonaws.services.batch.AWSBatchClientBuilder;
import com.amazonaws.services.batch.model.SubmitJobRequest;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;

public class BeginVideoGeneration extends BothelperSubcommandData {
    public BeginVideoGeneration (){
        super(Constants.BEGIN_VIDEO_GEN, "Archive a number of the oldest active threads");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Name of the Game to video-ize.").setRequired(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        String game = event.getOption(Constants.GAME_NAME).getAsString();
        sendMessage("Launching Video Creation for:" + game);
        AWSBatch client = AWSBatchClientBuilder.standard().withRegion("us-east-1").build();
        SubmitJobRequest sjr = new SubmitJobRequest();
        sjr.setJobName("video-" + game );
        sjr.setJobDefinition("getting-started-wizard-job-definition:10");
        sjr.setJobQueue("ti4-video-queue");
        HashMap<String, String> hm = new HashMap<String,String>();
        hm.put("game", game);
        sjr.setParameters(hm);
        client.submitJob(sjr);
    }

}
