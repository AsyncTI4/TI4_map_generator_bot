package ti4.commands2.explore;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;

class ExploreLookAtTop extends GameStateSubcommand {

    public ExploreLookAtTop() {
        super(Constants.LOOK_AT_TOP, "Look at the top card of an explore deck. Sends to Cards Info thread.", false, true);
        new OptionData(OptionType.STRING, Constants.TRAIT, "Cultural, Industrial, Hazardous, or Frontier.").setAutoComplete(true).setRequired(true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String trait = event.getOption(Constants.TRAIT).getAsString();
        if (trait.isBlank()) {
            MessageHelper.sendMessageToEventChannel(event, "Trait not found");
            return;
        }

        Game game = getGame();
        List<String> deck = game.getExploreDeck(trait);
        List<String> discardPile = game.getExploreDiscard(trait);

        String traitNameWithEmoji = Emojis.getEmojiFromDiscord(trait) + trait;
        Player player = getPlayer();
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
