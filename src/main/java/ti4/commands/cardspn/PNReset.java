package ti4.commands.cardspn;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.image.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class PNReset extends GameStateSubcommand {

    public PNReset() {
        super(Constants.PN_RESET, "Reset your promissory notes and send details to your #cards-info thread", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        String playerColor = AliasHandler.resolveColor(player.getColor());
        String playerFaction = player.getFaction();
        if (Mapper.isValidColor(playerColor) && Mapper.isValidFaction(playerFaction)) {
            List<String> promissoryNotes = new ArrayList<>(Mapper.getColorPromissoryNoteIDs(game, playerColor));
            for (String promissoryNote : promissoryNotes) {
                game.removePurgedPN(promissoryNote);
            }
        }
        game.checkPromissoryNotes();
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, true, event);
        MessageHelper.sendMessageToEventChannel(event, "Promissory note information sent.");
    }
}
