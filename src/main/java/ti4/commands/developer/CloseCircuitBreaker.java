package ti4.commands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.executors.CircuitBreaker;
import ti4.message.MessageHelper;

class CloseCircuitBreaker extends Subcommand {

    CloseCircuitBreaker() {
        super("close_circuit_breaker", "Turn off the circuit breaker immediately.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        CircuitBreaker.closeNow();
        MessageHelper.sendMessageToChannel(event.getChannel(), "Circuit breaker closed.");
    }
}
