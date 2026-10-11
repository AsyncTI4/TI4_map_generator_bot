package ti4.discord.interactions.commands.agenda;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Player;
import ti4.helpers.Constants;

class Cleanup extends GameStateSubcommand {

    public Cleanup() {
        super(Constants.CLEANUP, "Agenda Phase cleanup", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm command with YES").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!CommandHelper.confirmedWithYes(event, event.getOption(Constants.CONFIRM))) return;

        Map<String, Player> players = getGame().getPlayers();
        for (Player player : players.values()) {
            player.clearExhaustedPlanets(false);
        }
    }
}
