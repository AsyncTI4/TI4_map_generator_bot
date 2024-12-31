package ti4.commands.user;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.Subcommand;
import ti4.service.player.PingOnNextTurn;

class SetPingOnNextTurn extends Subcommand {

    public SetPingOnNextTurn() {
        super("set_ping_on_next_turn", "Set whether to ping you when the player before you starts their turn");
        addOption(OptionType.BOOLEAN, "ping", "True to ping you, false to not", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean ping = event.getOption("ping", false, OptionMapping::getAsBoolean);
        PingOnNextTurn.set(event, ping);
    }
}
