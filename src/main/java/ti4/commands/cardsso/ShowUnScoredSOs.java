package ti4.commands.cardsso;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ShowUnScoredSOs extends SOCardsSubcommandData {
    public ShowUnScoredSOs() {
        super(Constants.SHOW_UNSCORED_SOS, "List any SOs that are not scored yet");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

        if(activeMap.isFoWMode()){
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "This command is disabled for fog mode");
            return;
        }
        List<String> defaultSecrets = Mapper.getDecks().get("secret_objectives_pok").getShuffledCardList();
        List<String> currentSecrets = new ArrayList<String>();
        currentSecrets.addAll(defaultSecrets);
        for(Player player : activeMap.getPlayers().values()){
            if(player == null){
                continue;
            }
           if(player.getSecretsScored() != null){
                currentSecrets.removeAll(player.getSecretsScored().keySet());
           }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Game: ").append(activeMap.getName()).append("\n");
        sb.append("Unscored Action Phase Secrets: ").append("\n");
        int x= 1;
        for (String id : currentSecrets) {
            
            if(SOInfo.getSecretObjectiveRepresentation(id).contains("Action Phase")){
                sb.append(x + SOInfo.getSecretObjectiveRepresentation(id));
                x++;
            }   
        }
        x=1;
        sb.append("\n").append("Unscored Status Phase Secrets: ").append("\n");
        for (String id : currentSecrets) {
            if(SOInfo.getSecretObjectiveRepresentation(id).contains("Status Phase")){
                sb.append(x + SOInfo.getSecretObjectiveRepresentation(id));
                x++;
            }   
        }
        x=1;
        sb.append("\n").append("Unscored Agenda Phase Secrets: ").append("\n");
        for (String id : currentSecrets) {
            if(SOInfo.getSecretObjectiveRepresentation(id).contains("Agenda Phase")){
                sb.append(x+SOInfo.getSecretObjectiveRepresentation(id));
                x++;
            }   
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }
}
