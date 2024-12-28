package ti4.commands2.cardsso;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.info.SecretObjectiveInfoService;

class ShowSO extends GameStateSubcommand {

    public ShowSO() {
        super(Constants.SHOW_SO, "Show a Secret Objective to a player", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.SECRET_OBJECTIVE_ID, "Secret objective ID, which is found between ()").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Target faction or color").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color (defaults to you)").setAutoComplete(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ONLY_PHASE, "Show only the phase of the secret objective (action/agenda/status). Default false"));

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
            MessageHelper.sendMessageToEventChannel(event, "No such secret objective ID found, please retry.");
            return;
        }

        String info = SecretObjectiveInfoService.getSecretObjectiveRepresentation(soID);
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

        Player targetPlayer = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (targetPlayer == null) {
            MessageHelper.replyToMessage(event, "Unable to determine who the target player is.");
            return;
        }

        MessageHelper.sendMessageToEventChannel(event, "Secret objective shown to player.");
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
        MessageHelper.sendMessageToPlayerCardsInfoThread(targetPlayer, sb);
    }
}
