package ti4.commands2.cardsso;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.SecretObjectiveHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class ShowSO extends GameStateSubcommand {

    public ShowSO() {
        super(Constants.SHOW_SO, "Show a Secret Objective to a player", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.SECRET_OBJECTIVE_ID, "Secret objective ID that is sent between ()").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ONLY_PHASE, "Show only the phase of the SO (action/agenda/status). Default false"));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        int soIndex = event.getOption(Constants.SECRET_OBJECTIVE_ID).getAsInt();
        String soID = null;
        for (Map.Entry<String, Integer> so : player.getSecrets().entrySet()) {
            if (so.getValue().equals(soIndex)) {
                soID = so.getKey();
            }
        }

        if (soID == null) {
            MessageHelper.sendMessageToEventChannel(event, "No such Secret Objective ID found, please retry");
            return;
        }

        String info = SecretObjectiveHelper.getSecretObjectiveRepresentation(soID);
        boolean onlyPhase = event.getOption(Constants.ONLY_PHASE, false, OptionMapping::getAsBoolean);
        if (onlyPhase) {
            info = Mapper.getSecretObjective(soID).getPhase();
        }
        Game game = getGame();
        String sb = "Game: " + game.getName() + "\n" +
            "Player: " + player.getUserName() + "\n" +
            "Showed Secret Objectives:" + "\n" +
            info + "\n";

        player.setSecret(soID);

        Player targetPlayer = CommandHelper.getPlayerFromEvent(game, event);
        if (targetPlayer == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player not found");
            return;
        }

        MessageHelper.sendMessageToEventChannel(event, "SO shown to player");
        SecretObjectiveHelper.sendSecretObjectiveInfo(game, player);
        MessageHelper.sendMessageToPlayerCardsInfoThread(targetPlayer, game, sb);
    }
}
