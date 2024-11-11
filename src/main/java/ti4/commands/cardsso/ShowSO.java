package ti4.commands.cardsso;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.PlayerGameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ShowSO extends PlayerGameStateSubcommand {

    public ShowSO() {
        super(Constants.SHOW_SO, "Show a Secret Objective to a player", true, false);
        addOptions(new OptionData(OptionType.INTEGER, Constants.SECRET_OBJECTIVE_ID, "Secret objective ID that is sent between ()").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ONLY_PHASE, "Show only the phase of the SO (action/agenda/status). Default false"));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
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

        Player player_ = Helper.getPlayerFromEvent(game, event);
        if (player_ == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player not found");
            return;
        }

        MessageHelper.sendMessageToEventChannel(event, "SO shown to player");
        SOInfo.sendSecretObjectiveInfo(game, player);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player_, game, sb);
    }
}
