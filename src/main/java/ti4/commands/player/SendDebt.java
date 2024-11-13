package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands2.CommandHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SendDebt extends PlayerSubcommandData {
    public SendDebt() {
        super(Constants.SEND_DEBT, "Send a debt token (control token) to player/faction");
        addOptions(new OptionData(OptionType.INTEGER, Constants.DEBT_COUNT, "Number of tokens to send").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color receiving the debt token").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction or Color sending the debt token").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player sendingPlayer = CommandHelper.getPlayerFromEvent(game, event);
        if (sendingPlayer == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }

        Player receivingPlayer = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (receivingPlayer == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player to send Debt could not be found");
            return;
        }

        int debtCountToSend = event.getOption(Constants.DEBT_COUNT, 0, OptionMapping::getAsInt);
        if (debtCountToSend <= 0) {
            MessageHelper.sendMessageToEventChannel(event, "Debt count must be a positive integer");
            return;
        }

        sendDebt(sendingPlayer, receivingPlayer, debtCountToSend);
        CommanderUnlockCheck.checkPlayer(receivingPlayer, "vaden");
        MessageHelper.sendMessageToEventChannel(event, sendingPlayer.getRepresentation() + " sent " + debtCountToSend + " debt tokens to " + receivingPlayer.getRepresentation());
    }

    public static void sendDebt(Player sendingPlayer, Player receivingPlayer, int debtCountToSend) {
        String sendingPlayerColor = sendingPlayer.getColor();
        receivingPlayer.addDebtTokens(sendingPlayerColor, debtCountToSend);
    }
}
