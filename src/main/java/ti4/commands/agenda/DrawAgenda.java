package ti4.commands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;

public class DrawAgenda extends GameStateSubcommand {

    public DrawAgenda() {
        super(Constants.DRAW, "Draw Agenda", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to draw, default 1"));
        addOptions(new OptionData(OptionType.BOOLEAN, "from_bottom", "Whether to draw from bottom, default false"));
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return super.accept(event) && SlashCommandAcceptanceHelper.acceptIfPlayerInGame(event);
    }

    public void execute(SlashCommandInteractionEvent event) {
        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);
        count = Math.max(count, 1);
        OptionMapping fromBottomOption = event.getOption("from_bottom");
        boolean fromBottom = fromBottomOption != null && fromBottomOption.getAsBoolean();
        Game game = getGame();
        Player player = game.getPlayer(event.getUser().getId());
        AgendaHelper.drawAgenda(event, count, fromBottom, game, player);
    }
}
