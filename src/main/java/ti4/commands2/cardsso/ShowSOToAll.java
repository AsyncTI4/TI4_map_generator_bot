package ti4.commands2.cardsso;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;

class ShowSOToAll extends GameStateSubcommand {

    public ShowSOToAll() {
        super(Constants.SHOW_SO_TO_ALL, "Show a Secret Objective to all players", true, false);
        addOptions(new OptionData(OptionType.INTEGER, Constants.SECRET_OBJECTIVE_ID, "Secret objective ID that is sent between ()").setRequired(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ONLY_PHASE, "Show only the phase of the SO (action/agenda/status). Default false"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int soIndex = event.getOption(Constants.SECRET_OBJECTIVE_ID).getAsInt();
        String soID = null;
        boolean scored = false;
        Player player = getPlayer();
        for (Map.Entry<String, Integer> so : player.getSecrets().entrySet()) {
            if (so.getValue().equals(soIndex)) {
                soID = so.getKey();
                break;
            }
        }
        if (soID == null) {
            for (Map.Entry<String, Integer> so : player.getSecretsScored().entrySet()) {
                if (so.getValue().equals(soIndex)) {
                    soID = so.getKey();
                    scored = true;
                    break;
                }
            }
        }
        boolean onlyPhase = event.getOption(Constants.ONLY_PHASE) != null && event.getOption(Constants.ONLY_PHASE).getAsBoolean();
        if (soID == null) {
            MessageHelper.sendMessageToEventChannel(event, "No such Secret Objective ID found, please retry");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Game: ").append(getGame().getName()).append("\n");
        sb.append("Player: ").append(player.getUserName()).append("\n");
        if (scored) {
            sb.append("Showed Scored Secret Objectives:").append("\n");
        } else {
            sb.append("Showed Secret Objectives:").append("\n");
        }
        String info = SOInfo.getSecretObjectiveRepresentation(soID);
        if (onlyPhase) {
            info = Mapper.getSecretObjective(soID).getPhase();
        }
        sb.append(info).append("\n");
        if (!scored) {
            player.setSecret(soID);
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }
}
