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
import ti4.map.Map;
import ti4.map.Player;

public class ClearDebt extends PlayerSubcommandData {
    public ClearDebt() {
        super(Constants.CLEAR_DEBT, "Clear debt tokens (control token) for player/faction");
        addOptions(new OptionData(OptionType.INTEGER, Constants.DEBT_COUNT, "Number of tokens to clear").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color having their debt cleared ").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR_1, "Faction or Color clearing the debt").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player clearingPlayer = activeMap.getPlayer(getUser().getId());
        clearingPlayer = Helper.getGamePlayer(activeMap, clearingPlayer, event, null);
        
        OptionMapping factionColorOption = event.getOption(Constants.FACTION_COLOR_1);
        if (factionColorOption != null) {
            String factionColor = AliasHandler.resolveColor(factionColorOption.getAsString().toLowerCase());
            factionColor = StringUtils.substringBefore(factionColor, " "); //TO HANDLE UNRESOLVED AUTOCOMPLETE
            factionColor = AliasHandler.resolveFaction(factionColor);
            for (Player player_ : activeMap.getPlayers().values()) {
                if (Objects.equals(factionColor, player_.getFaction()) || Objects.equals(factionColor, player_.getColor())) {
                    clearingPlayer = player_;
                    break;
                }
            }
        }
        
        if (clearingPlayer == null) {
            sendMessage("Player could not be found");
            return;
        }

        Player clearedPlayer = Helper.getPlayer(activeMap, clearingPlayer, event);
        if (clearedPlayer == null) {
            sendMessage("Player to have debt cleared could not be found");
            return;
        }

        int debtCountToClear = event.getOption(Constants.DEBT_COUNT, 0, OptionMapping::getAsInt);
        if (debtCountToClear <= 0 ) {
            sendMessage("Debt count must be a positive integer");
            return;
        }

        if (debtCountToClear > clearingPlayer.getDebtTokenCount(clearedPlayer.getColor())) {
            sendMessage("You cannot clear more debt tokens than you have");
            return;
        }

        clearDebt(clearingPlayer, clearedPlayer, debtCountToClear);
        sendMessage(Helper.getPlayerRepresentation(clearingPlayer, activeMap) + " cleared " + debtCountToClear + " debt tokens owned by " + Helper.getPlayerRepresentation(clearedPlayer, activeMap));
    }

    public static void clearDebt(Player clearingPlayer, Player clearedPlayer, int debtCountToClear) {
        String clearedPlayerColour = clearedPlayer.getColor();
        clearingPlayer.removeDebtTokens(clearedPlayerColour, debtCountToClear);
    }
}
