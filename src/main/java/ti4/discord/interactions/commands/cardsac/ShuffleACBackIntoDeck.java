package ti4.discord.interactions.commands.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

class ShuffleACBackIntoDeck extends GameStateSubcommand {

    public ShuffleACBackIntoDeck() {
        super(
                Constants.SHUFFLE_AC_BACK_INTO_DECK,
                "Shuffle action card back into deck from the discard pile.",
                true,
                false);
        addOptions(new OptionData(
                        OptionType.INTEGER, Constants.ACTION_CARD_ID, "Action Card ID, which is found between ()")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int acIndex = event.getOption(Constants.ACTION_CARD_ID).getAsInt();
        Game game = getGame();
        String acID = ActionCardHelper.getDiscardedAcID(game, acIndex);
        if (acID == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such action card ID found, please retry.");
            return;
        }
        boolean picked = game.shuffleActionCardBackIntoDeck(acIndex);
        if (!picked) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such action card ID found, please retry.");
            return;
        }
        String sb = "Game: " + game.getName() + " " + "Player: "
                + event.getUser().getName() + "\n" + "Card shuffled back into deck from discards: "
                + Mapper.getActionCard(acID).getRepresentation(game)
                + "\n";
        MessageHelper.sendMessageToChannel(event.getChannel(), sb);
    }
}
