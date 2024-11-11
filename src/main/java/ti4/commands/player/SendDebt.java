package ti4.commands.player;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SendDebt extends PlayerSubcommandData {
    public SendDebt() {
        super(Constants.SEND_DEBT, "Send a debt token (control token) to player/faction");
        addOptions(new OptionData(OptionType.INTEGER, Constants.DEBT_COUNT, "Number of tokens to send").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color receiving the debt token").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR_1, "Faction or Color sending the debt token").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player sendingPlayer = game.getPlayer(getUser().getId());
        sendingPlayer = Helper.getGamePlayer(game, sendingPlayer, event, null);

        OptionMapping factionColorOption = event.getOption(Constants.FACTION_COLOR_1);
        if (factionColorOption != null) {
            String factionColor = AliasHandler.resolveColor(factionColorOption.getAsString().toLowerCase());
            factionColor = StringUtils.substringBefore(factionColor, " "); //TO HANDLE UNRESOLVED AUTOCOMPLETE
            factionColor = AliasHandler.resolveFaction(factionColor);
            for (Player player_ : game.getPlayers().values()) {
                if (Objects.equals(factionColor, player_.getFaction()) || Objects.equals(factionColor, player_.getColor())) {
                    sendingPlayer = player_;
                    break;
                }
            }
        }

        if (sendingPlayer == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }

        Player receivingPlayer = Helper.getPlayerFromEvent(game, sendingPlayer, event);
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
