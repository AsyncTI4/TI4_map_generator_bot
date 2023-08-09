package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;

public class DebtSend extends PlayerSubcommandData {
    public DebtSend() {
        super(Constants.SEND_DEBT, "Send a debt token (control token) to player/faction");
        addOptions(new OptionData(OptionType.INTEGER, Constants.DEBT_COUNT, "Number of tokens to send").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to which you send Debt").setAutoComplete(true).setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player sendingPlayer = activeMap.getPlayer(getUser().getId());
        sendingPlayer = Helper.getGamePlayer(activeMap, sendingPlayer, event, null);
        if (sendingPlayer == null) {
            sendMessage("Player could not be found");
            return;
        }

        Player receivingPlayer = Helper.getPlayer(activeMap, sendingPlayer, event);
        if (receivingPlayer == null) {
            sendMessage("Player to send Debt could not be found");
            return;
        }

        int debtCountToSend = event.getOption(Constants.DEBT_COUNT, 0, OptionMapping::getAsInt);
        if (debtCountToSend <= 0 ) {
            sendMessage("Debt count must be a positive integer");
            return;
        }

        sendDebt(sendingPlayer, receivingPlayer, debtCountToSend);
        sendMessage(Helper.getPlayerRepresentation(sendingPlayer, activeMap) + " sent " + debtCountToSend + " debt tokens to " + Helper.getPlayerRepresentation(receivingPlayer, activeMap));
        
    }

    public static void sendDebt(Player sendingPlayer, Player receivingPlayer, int debtCountToSend) {
        String sendingPlayerColour = sendingPlayer.getColor();
        receivingPlayer.addDebtTokens(sendingPlayerColour, debtCountToSend);
    }
}
