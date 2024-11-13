package ti4.commands.ds;

import java.util.Collection;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Command;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class DiscordantStarsCommand implements Command {

    private final Collection<Subcommand> subcommands = List.of(
            new ZelianHero(),
            new TrapToken(),
            new TrapReveal(),
            new TrapSwap(),
            new FlipGrace(),
            new SetPolicy(),
            new DrawBlueBackTile(),
            new DrawRedBackTile(),
            new AddOmenDie(),
            new KyroHero(),
            new ATS());

    @Override
    public String getActionId() {
        return Constants.DS_COMMAND;
    }

    @Override
    public String getActionDescription() {
        return "Discordant Stars Commands";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return SlashCommandAcceptanceHelper.acceptIfPlayerInGame(event);
    }

    @Override
    public Collection<Subcommand> getSubcommands() {
        return subcommands;
    }
}
