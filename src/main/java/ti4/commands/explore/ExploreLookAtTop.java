package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.ArrayList;

public class ExploreLookAtTop extends ExploreSubcommandData {

    public ExploreLookAtTop() {
        super(Constants.LOOK_AT_TOP, "Look at the top card of an explore deck. Sends to Cards Info thread.");
        addOptions(typeOption.setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        String trait = event.getOption(Constants.TRAIT, null, OptionMapping::getAsString);
        if (trait == null || trait.isEmpty() || trait.isBlank()) {
            sendMessage("Trait not found");
            return;
        }

        ArrayList<String> deck = activeGame.getExploreDeck(trait);
        ArrayList<String> discardPile = activeGame.getExploreDiscard(trait);

        String traitNameWithEmoji = Helper.getEmojiFromDiscord(trait) + trait;
        String playerFactionNameWithEmoji = Helper.getFactionIconFromDiscord(player.getFaction());
        if (deck.isEmpty() && discardPile.isEmpty()) {
            sendMessage(traitNameWithEmoji + " explore deck & discard is empty - nothing to look at.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("__**Look at Top of ").append(traitNameWithEmoji).append(" Deck**__\n");
        String topCard = deck.get(0);
        sb.append(displayExplore(topCard));

        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, sb.toString());
        sendMessage("top of " + traitNameWithEmoji + " explore deck has been set to " + playerFactionNameWithEmoji
                + " Cards info thread.");

    }
}
