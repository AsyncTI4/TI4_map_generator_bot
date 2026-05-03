package ti4.discord.interactions.commands.cardsac;

import java.util.Objects;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.decks.ShowActionCardsService;

class ShowGarboziaActionCards extends GameStateSubcommand {

    public ShowGarboziaActionCards() {
        super(Constants.SHOW_GARBOZIA_AC, "Show Garbozia Action Cards", false, false);
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHOW_FULL_TEXT, "'true' to show full card text"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        boolean showFullText = event.getOption(Constants.SHOW_FULL_TEXT, false, OptionMapping::getAsBoolean);
        String garboziaText = ShowActionCardsService.getGarboziaDiscardText(game, showFullText);
        MessageHelper.sendMessageToChannel(
                event.getChannel(), Objects.requireNonNullElse(garboziaText, "No Action Cards on Garbozia."));
    }
}
