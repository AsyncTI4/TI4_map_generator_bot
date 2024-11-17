package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;

class AdjustRoundNumber extends GameStateSubcommand {

    public AdjustRoundNumber() {
        super(Constants.ADJUST_ROUND_NUMBER, "Adjust round number of game", true, false);
        addOptions(new OptionData(OptionType.INTEGER, Constants.ROUND, "Round number").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        game.setRound(event.getOption(Constants.ROUND).getAsInt());
    }
}
