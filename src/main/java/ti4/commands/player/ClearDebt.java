package ti4.commands.player;

import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ClearDebt extends PlayerSubcommandData {
    public ClearDebt() {
        super(Constants.CLEAR_DEBT, "Clear debt tokens (control token) for player/faction");
        addOptions(new OptionData(OptionType.INTEGER, Constants.DEBT_COUNT, "Number of tokens to clear").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color having their debt cleared ").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR_1, "Faction or Color clearing the debt").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player clearingPlayer = game.getPlayer(getUser().getId());
        clearingPlayer = Helper.getGamePlayer(game, clearingPlayer, event, null);

        OptionMapping factionColorOption = event.getOption(Constants.FACTION_COLOR_1);
        if (factionColorOption != null) {
            String factionColor = AliasHandler.resolveColor(factionColorOption.getAsString().toLowerCase());
            factionColor = StringUtils.substringBefore(factionColor, " "); //TO HANDLE UNRESOLVED AUTOCOMPLETE
            factionColor = AliasHandler.resolveFaction(factionColor);
            for (Player player_ : game.getPlayers().values()) {
                if (Objects.equals(factionColor, player_.getFaction()) || Objects.equals(factionColor, player_.getColor())) {
                    clearingPlayer = player_;
                    break;
                }
            }
        }

        if (clearingPlayer == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }

        Player clearedPlayer = Helper.getPlayerFromEvent(game, clearingPlayer, event);
        if (clearedPlayer == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player to have debt cleared could not be found");
            return;
        }

        int debtCountToClear = event.getOption(Constants.DEBT_COUNT, 0, OptionMapping::getAsInt);
        if (debtCountToClear <= 0) {
            MessageHelper.sendMessageToEventChannel(event, "Debt count must be a positive integer");
            return;
        }

        if (debtCountToClear > clearingPlayer.getDebtTokenCount(clearedPlayer.getColor())) {
            MessageHelper.sendMessageToEventChannel(event, "You cannot clear more debt tokens than you have");
            return;
        }

        clearDebt(clearingPlayer, clearedPlayer, debtCountToClear);
        MessageHelper.sendMessageToEventChannel(event, clearingPlayer.getRepresentation() + " cleared " + debtCountToClear + " debt tokens owned by " + clearedPlayer.getRepresentation());
    }

    public static void clearDebt(Player clearingPlayer, Player clearedPlayer, int debtCountToClear) {
        String clearedPlayerColor = clearedPlayer.getColor();
        clearingPlayer.removeDebtTokens(clearedPlayerColor, debtCountToClear);
    }
}
