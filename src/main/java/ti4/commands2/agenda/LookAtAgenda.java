package ti4.commands2.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.agenda.LookAgendaService;

class LookAtAgenda extends GameStateSubcommand {

    public LookAtAgenda() {
        super(Constants.LOOK, "Look at the agenda deck", false, true);
        addOption(OptionType.INTEGER, Constants.COUNT, "Number of agendas to look at");
        addOption(OptionType.BOOLEAN, Constants.LOOK_AT_BOTTOM, "To look at top or bottom");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);
        count = Math.max(count, 1);
        boolean lookAtBottom = event.getOption(Constants.LOOK_AT_BOTTOM, false, OptionMapping::getAsBoolean);

        Game game = getGame();
        Player player = getPlayer();
        LookAgendaService.lookAtAgendas(game, player, count, lookAtBottom);
    }
}
