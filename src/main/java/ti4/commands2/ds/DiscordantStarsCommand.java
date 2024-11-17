package ti4.commands2.ds;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.CommandHelper;
import ti4.commands2.ParentCommand;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;

public class DiscordantStarsCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
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
                    new ATS())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.DS_COMMAND;
    }

    @Override
    public String getDescription() {
        return "Discordant Stars Commands";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return ParentCommand.super.accept(event) &&
            CommandHelper.acceptIfPlayerInGameAndGameChannel(event);
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
