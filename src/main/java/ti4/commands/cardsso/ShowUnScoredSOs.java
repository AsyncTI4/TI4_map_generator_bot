package ti4.commands.cardsso;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ShowUnScoredSOs extends SOCardsSubcommandData {
    public ShowUnScoredSOs() {
        super(Constants.SHOW_UNSCORED_SOS, "List any SOs that are not scored yet");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();

        if(activeGame.isFoWMode()){
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "This command is disabled for fog mode");
            return;
        }
        List<String> defaultSecrets = Mapper.getDecks().get("secret_objectives_pok").getShuffledCardList();
        List<String> currentSecrets = new ArrayList<>(defaultSecrets);
        for(Player player : activeGame.getPlayers().values()){
            if(player == null){
                continue;
            }
           if(player.getSecretsScored() != null){
                currentSecrets.removeAll(player.getSecretsScored().keySet());
           }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Game: ").append(activeGame.getName()).append("\n");
        sb.append("Unscored Action Phase Secrets: ").append("\n");
        int x= 1;
        for (String id : currentSecrets) {
            
            if(SOInfo.getSecretObjectiveRepresentation(id).contains("Action Phase")){
                sb.append(x).append(SOInfo.getSecretObjectiveRepresentation(id));
                x++;
            }   
        }
        x=1;
        sb.append("\n").append("Unscored Status Phase Secrets: ").append("\n");
        for (String id : currentSecrets) {
            if(SOInfo.getSecretObjectiveRepresentation(id).contains("Status Phase")){
                sb.append(x).append(SOInfo.getSecretObjectiveRepresentation(id));
                x++;
            }   
        }
        x=1;
        sb.append("\n").append("Unscored Agenda Phase Secrets: ").append("\n");
        for (String id : currentSecrets) {
            if(SOInfo.getSecretObjectiveRepresentation(id).contains("Agenda Phase")){
                sb.append(x).append(SOInfo.getSecretObjectiveRepresentation(id));
                x++;
            }   
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }
}
