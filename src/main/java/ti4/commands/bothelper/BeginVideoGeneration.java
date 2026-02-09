package ti4.commands.bothelper;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.batch.model.SubmitJobRequest;
import software.amazon.awssdk.services.batch.model.SubmitJobResponse;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class BeginVideoGeneration extends Subcommand {

    BeginVideoGeneration() {
        super(Constants.BEGIN_VIDEO_GEN, "Kickoff Video Process in AWS");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Name of the Game to video-ize.")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String game = event.getOption(Constants.GAME_NAME).getAsString();
        MessageHelper.sendMessageToEventChannel(event, "Launching Video Creation for: " + game);

        // In SDK v2, clients are built using static builder methods
        // For performance, you should ideally move this client to a Spring @Bean
        try (BatchClient client = BatchClient.builder().region(Region.US_EAST_1).build()) {

            // Requests are now immutable and built via builders
            SubmitJobRequest sjr = SubmitJobRequest.builder()
                    .jobName("video-" + game)
                    .jobDefinition("getting-started-wizard-job-definition:11")
                    .jobQueue("ti4-video-queue")
                    .parameters(Map.of("game", game)) // Java 21 Map.of is cleaner
                    .build();

            SubmitJobResponse response = client.submitJob(sjr);

            // Optional: Log the Job ID for debugging
            System.out.println("Submitted Job ID: " + response.jobId());
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "Failed to launch AWS job: " + e.getMessage());
        }
    }
}
