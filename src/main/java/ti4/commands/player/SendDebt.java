package ti4.commands.player;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;

public class SendDebt extends PlayerSubcommandData {
    public SendDebt() {
        super(Constants.SEND_DEBT, "Send a debt token (control token) to player/faction");
        addOptions(new OptionData(OptionType.INTEGER, Constants.DEBT_COUNT, "Number of tokens to send").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color receiving the debt token").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR_1, "Faction or Color sending the debt token").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player sendingPlayer = activeGame.getPlayer(getUser().getId());
        sendingPlayer = Helper.getGamePlayer(activeGame, sendingPlayer, event, null);

        OptionMapping factionColorOption = event.getOption(Constants.FACTION_COLOR_1);
        if (factionColorOption != null) {
            String factionColor = AliasHandler.resolveColor(factionColorOption.getAsString().toLowerCase());
            factionColor = StringUtils.substringBefore(factionColor, " "); //TO HANDLE UNRESOLVED AUTOCOMPLETE
            factionColor = AliasHandler.resolveFaction(factionColor);
            for (Player player_ : activeGame.getPlayers().values()) {
                if (Objects.equals(factionColor, player_.getFaction()) || Objects.equals(factionColor, player_.getColor())) {
                    sendingPlayer = player_;
                    break;
                }
            }
        }
        
        if (sendingPlayer == null) {
            sendMessage("Player could not be found");
            return;
        }

        Player receivingPlayer = Helper.getPlayer(activeGame, sendingPlayer, event);
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
        sendMessage(Helper.getPlayerRepresentation(sendingPlayer, activeGame) + " sent " + debtCountToSend + " debt tokens to " + Helper.getPlayerRepresentation(receivingPlayer, activeGame));
        
    }

    public static void sendDebt(Player sendingPlayer, Player receivingPlayer, int debtCountToSend) {
        String sendingPlayerColour = sendingPlayer.getColor();
        receivingPlayer.addDebtTokens(sendingPlayerColour, debtCountToSend);
    }
}
