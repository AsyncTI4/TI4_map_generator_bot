package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SendDebt extends GameStateSubcommand {

    public SendDebt() {
        super(Constants.SEND_DEBT, "Send a debt token (control token) to player/faction", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.DEBT_COUNT, "Number of tokens to send").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction or Color receiving the debt token").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color sending the debt token").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int debtCountToSend = event.getOption(Constants.DEBT_COUNT).getAsInt();
        if (debtCountToSend <= 0) {
            MessageHelper.sendMessageToEventChannel(event, "Debt count must be a positive integer");
            return;
        }

        Game game = getGame();
        Player sendingPlayer = getPlayer();
        Player receivingPlayer = CommandHelper.getOtherPlayerFromEvent(game, event);
        sendDebt(sendingPlayer, receivingPlayer, debtCountToSend);
        CommanderUnlockCheck.checkPlayer(receivingPlayer, "vaden");
        MessageHelper.sendMessageToEventChannel(event, sendingPlayer.getRepresentation() + " sent " + debtCountToSend + " debt tokens to " + receivingPlayer.getRepresentation());
    }

    public static void sendDebt(Player sendingPlayer, Player receivingPlayer, int debtCountToSend) {
        String sendingPlayerColor = sendingPlayer.getColor();
        receivingPlayer.addDebtTokens(sendingPlayerColor, debtCountToSend);
    }
}
