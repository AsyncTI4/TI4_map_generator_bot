package ti4.discord.interactions.commands.fow;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.discord.interactions.commands.ParentCommand;
import ti4.discord.interactions.commands.Subcommand;
import ti4.discord.interactions.commands.SubcommandGroup;
import ti4.helpers.Constants;

public class FOWCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = inOrder(Stream.of(
            new Whisper(),
            new Announce(),
            new CheckChannels(),
            new PrivateCommunicationsCheck(),
            new AddFogTile(),
            new RemoveFogTile(),
            new GMCommand(),
            new SetupWizard(),
            new SetupWizardBeta(),
            new FOWOptions(),
            new AddVisionToken(),
            new RemoveVisionToken(),
            new CreateFoWGameButton(),
            new FowInfo(),
            new FowEndedStats()));

    private final Map<String, SubcommandGroup> subcommandGroups = Stream.of(new FowPingGroup(), new FowMapGroup())
            .collect(Collectors.toMap(SubcommandGroup::getName, Function.identity(), (a, b) -> a, LinkedHashMap::new));

    static Map<String, Subcommand> inOrder(Stream<Subcommand> subcommands) {
        return subcommands.collect(
                Collectors.toMap(Subcommand::getName, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    @Override
    public String getName() {
        return Constants.FOW;
    }

    public String getDescription() {
        return "Fog of War";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }

    @Override
    public Map<String, SubcommandGroup> getSubcommandGroups() {
        return subcommandGroups;
    }
}
