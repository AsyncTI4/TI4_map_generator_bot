package ti4.discord.interactions.commands.homebrew.monuments;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.discord.interactions.commands.Subcommand;
import ti4.discord.interactions.commands.SubcommandGroup;

public class MonumentsGroup extends SubcommandGroup {

    private static final Map<String, Subcommand> subcommands = Stream.of(
                    new MonumentReady(),
                    new MonumentExhaust(),
                    new BelkoseaToken(BelkoseaToken.Action.ADD),
                    new BelkoseaToken(BelkoseaToken.Action.REMOVE),
                    new BelkoseaToken(BelkoseaToken.Action.MOVE))
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    public MonumentsGroup() {
        super("monument", "Monuments commands");
    }

    @Override
    public Map<String, Subcommand> getGroupSubcommands() {
        return subcommands;
    }
}
