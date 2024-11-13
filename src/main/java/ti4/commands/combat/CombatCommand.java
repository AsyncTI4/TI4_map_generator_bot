package ti4.commands.combat;

import java.util.Collection;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Command;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class CombatCommand implements Command {

    private final Collection<Subcommand> subcommands = List.of(
            new CombatRoll(),
            new StartCombat());

    @Override
    public String getActionId() {
        return Constants.COMBAT;
    }

    @Override
    public String getActionDescription() {
        return "Combat";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return Command.super.accept(event) &&
                SlashCommandAcceptanceHelper.acceptIfPlayerInGame(event);
    }

    @Override
    public Collection<Subcommand> getSubcommands() {
        return subcommands;
    }
}
