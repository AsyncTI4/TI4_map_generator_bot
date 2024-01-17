package ti4.commands.bothelper;

import java.io.File;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.helpers.WebHelper;
import ti4.map.GameManager;
import ti4.message.BotLogger;

public class SendJsonToS3 extends BothelperSubcommandData {
    public SendJsonToS3(){
        super(Constants.SEND_JSON_TO_S3, "Send all finished game json to s3");
    }

    public void execute(SlashCommandInteractionEvent event) {
        String JSON = ".json";
        GameManager.getInstance().getGameNameToGame().values().parallelStream()
        .forEach(game -> {
            boolean isWon = game.getWinner().isPresent() && game.isHasEnded();
            if (isWon) {
                try {
                    File jsonGameFile = Storage.getMapsJSONStorage(game.getName() + JSON);
                    WebHelper.putFile(game.getName(), jsonGameFile);
                } catch (Exception e) {
                    BotLogger.log("Error sending json file to s3: " + game.getName(), e);
                }
            }
        });
    }
}
