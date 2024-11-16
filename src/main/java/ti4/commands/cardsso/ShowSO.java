package ti4.commands.cardsso;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ShowSO extends SOCardsSubcommandData {
    public ShowSO() {
        super(Constants.SHOW_SO, "Show a Secret Objective to a player");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SECRET_OBJECTIVE_ID, "Secret objective ID that is sent between ()").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Target faction or color").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Source faction or color (default is you)").setAutoComplete(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ONLY_PHASE, "Show only the phase of the SO (action/agenda/status). Default false"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        OptionMapping option = event.getOption(Constants.SECRET_OBJECTIVE_ID);
        if (option == null) {
            MessageHelper.sendMessageToEventChannel(event, "Please select what Secret Objective to show");
            return;
        }

        int soIndex = option.getAsInt();
        String soID = null;
        for (Map.Entry<String, Integer> so : player.getSecrets().entrySet()) {
            if (so.getValue().equals(soIndex)) {
                soID = so.getKey();
            }
        }
        boolean onlyPhase = event.getOption(Constants.ONLY_PHASE) != null && event.getOption(Constants.ONLY_PHASE).getAsBoolean();

        if (soID == null) {
            MessageHelper.sendMessageToEventChannel(event, "No such Secret Objective ID found, please retry");
            return;
        }
        String info = SOInfo.getSecretObjectiveRepresentation(soID);
        if (onlyPhase) {
            info = Mapper.getSecretObjective(soID).getPhase();
        }
        String sb = "Game: " + game.getName() + "\n" +
            "Player: " + player.getUserName() + "\n" +
            "Showed Secret Objectives:" + "\n" +
            info + "\n";

        player.setSecret(soID);

        Player otherPlayer = CommandCommandHelper.getOtherPlayerFromEvent(game, event);
        if (otherPlayer == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player not found");
            return;
        }

        MessageHelper.sendMessageToEventChannel(event, "SO shown to player");
        SOInfo.sendSecretObjectiveInfo(game, player);
        MessageHelper.sendMessageToPlayerCardsInfoThread(otherPlayer, game, sb);
    }
}
