package ti4.commands.cardspn;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class SendRandomPN extends GameStateSubcommand {

    public SendRandomPN() {
        super("send_random", "Send a random Promissory Note to a player", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player otherPlayer = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (otherPlayer == null) {
            MessageHelper.replyToMessage(event, "Unable to determine who the target player is.");
            return;
        }
        PromissoryNoteHelper.sendRandom(event, game, getPlayer(), otherPlayer);
    }
}
