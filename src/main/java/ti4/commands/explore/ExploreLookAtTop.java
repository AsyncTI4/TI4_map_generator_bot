package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;

import java.util.List;

public class ExploreLookAtTop extends ExploreSubcommandData {

    public ExploreLookAtTop() {
        super(Constants.LOOK_AT_TOP, "Look at the top card of an explore deck. Sends to Cards Info thread.");
        addOptions(typeOption.setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayerFromEvent(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        String trait = event.getOption(Constants.TRAIT, null, OptionMapping::getAsString);
        if (trait == null || trait.isEmpty() || trait.isBlank()) {
            MessageHelper.sendMessageToEventChannel(event, "Trait not found");
            return;
        }

        List<String> deck = game.getExploreDeck(trait);
        List<String> discardPile = game.getExploreDiscard(trait);

        String traitNameWithEmoji = Emojis.getEmojiFromDiscord(trait) + trait;
        String playerFactionNameWithEmoji = player.getFactionEmoji();
        if (deck.isEmpty() && discardPile.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, traitNameWithEmoji + " explore deck & discard is empty - nothing to look at.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("__**Look at Top of ").append(traitNameWithEmoji).append(" Deck**__\n");
        String topCard = deck.getFirst();
        ExploreModel explore = Mapper.getExplore(topCard);
        sb.append(explore.textRepresentation());

        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, sb.toString());
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "top of " + traitNameWithEmoji + " explore deck has been set to " + playerFactionNameWithEmoji
            + " Cards info thread.");

    }
}
