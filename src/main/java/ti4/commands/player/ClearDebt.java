package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class ClearDebt extends GameStateSubcommand {

    public ClearDebt() {
        super(Constants.CLEAR_DEBT, "Clear debt tokens (control token) for player/faction", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.DEBT_COUNT, "Number of tokens to clear").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction or Color having their debt cleared ").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color clearing the debt").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int debtCountToClear = event.getOption(Constants.DEBT_COUNT).getAsInt();
        if (debtCountToClear <= 1) {
            MessageHelper.sendMessageToEventChannel(event, "Debt count must be a positive integer");
            return;
        }

        Game game = getGame();
        Player clearingPlayer = getPlayer();
        Player clearedPlayer = CommandHelper.getOtherPlayerFromEvent(game, event);
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
