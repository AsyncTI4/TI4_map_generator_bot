package ti4.commands.cardsso;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.cardsac.ACInfo_Legacy;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class DiscardSO extends SOCardsSubcommandData {
    public DiscardSO() {
        super(Constants.DISCARD_SO, "Discard Secret Objective");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SECRET_OBJECTIVE_ID, "Secret objective ID that is sent between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        OptionMapping option = event.getOption(Constants.SECRET_OBJECTIVE_ID);
        if (option == null) {
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap,"Please select what Secret Objective to discard");
            return;
        }
        discardSO(event, player, option.getAsInt(), activeMap);
    }
    public void discardSO(GenericInteractionCreateEvent event, Player player, int SOID, Map activeMap) {
       
        boolean removed = activeMap.discardSecretObjective(player.getUserID(), SOID);
        if (!removed) {
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap,"No such Secret Objective ID found, please retry");
            return;
        }
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap,"SO Discarded");
        SOInfo.sendSecretObjectiveInfo(activeMap, player);
    }
}
