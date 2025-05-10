package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class SCCount extends GameStateSubcommand {

    public SCCount() {
        super(Constants.SC_COUNT, "Strategy cards count in game", false, false);
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Strategy card count").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        OptionMapping scOption = event.getOption(Constants.STRATEGY_CARD);
        if (scOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify strategy card.");
            return;

        }

        int sc = scOption.getAsInt();
        if (sc < 8) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Strategy card count is by default 8, if you want more, please select number greater that 8.");
            return;
        }
        for (int i = 8; i < sc; i++) {
            game.setScTradeGood(i + 1, 0);
        }
    }
}
