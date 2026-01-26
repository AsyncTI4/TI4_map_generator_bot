package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class ClearDebt extends GameStateSubcommand {

    ClearDebt() {
        super(Constants.CLEAR_DEBT, "Clear debt tokens (control token) for player/faction", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.DEBT_COUNT, "Number of tokens to clear")
                .setRequired(true));
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.TARGET_FACTION_OR_COLOR,
                        "Faction or Color having their debt cleared ")
                .setAutoComplete(true)
                .setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color clearing the debt")
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.DEBT_POOL, "Which debt pool to send to")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int debtCountToClear = event.getOption(Constants.DEBT_COUNT).getAsInt();
        if (debtCountToClear <= 0) {
            MessageHelper.sendMessageToEventChannel(event, "Debt count must be a positive integer");
            return;
        }

        Game game = getGame();
        Player clearingPlayer = getPlayer();
        Player clearedPlayer = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (clearedPlayer == null) {
            MessageHelper.replyToMessage(event, "Unable to determine who the target player is.");
            return;
        }
        String pool = event.getOption(Constants.DEBT_POOL).getAsString();
        if (pool == null) {
            pool = Constants.DEBT_DEFAULT_POOL;
        }

        if (debtCountToClear > clearingPlayer.getDebtTokenCount(clearedPlayer.getColor(), pool)) {
            MessageHelper.sendMessageToEventChannel(event, "You cannot clear more debt tokens than you have.");
            return;
        }

        clearingPlayer.clearDebt(clearedPlayer, debtCountToClear, pool);
        MessageHelper.sendMessageToChannel(
                clearingPlayer.getCorrectChannel(),
                clearingPlayer.getRepresentation() + " cleared " + debtCountToClear + " debt token"
                        + (debtCountToClear == 1 ? "" : "s") + " owned by " + clearedPlayer.getRepresentation() + ".");
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
