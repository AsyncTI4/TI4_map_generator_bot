package ti4.commands.cardsso;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.SecretObjectiveHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class ShowAllSO extends GameStateSubcommand {

    public ShowAllSO() {
        super(Constants.SHOW_ALL, "Show all Secret Objectives to one player", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Source faction or color (default is you)").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player targetPlayer = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (targetPlayer == null) {
            MessageHelper.replyToMessage(event, "Unable to determine who the target player is.");
            return;
        }
        SecretObjectiveHelper.showAll(getPlayer(), targetPlayer, game);
    }


}
