package ti4.discord.interactions.slashcommands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.discord.interactions.slashcommands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
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
        boolean lookAtBottom = event.getOption(Constants.LOOK_AT_BOTTOM, Boolean.FALSE, OptionMapping::getAsBoolean);

        Game game = getGame();
        Player player = getPlayer();
        LookAgendaService.lookAtAgendas(game, player, count, lookAtBottom);
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
