package ti4.discord.interactions.commands.megagame;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.discord.interactions.commands.ParentCommand;
import ti4.discord.interactions.commands.Subcommand;

public class MegagameCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.<Subcommand>of(
                    new MegagameEnable(),
                    new MegagameDisable(),
                    new MegagameInit(),
                    new MegagameShow(),
                    new MegagameSetSpeaker(),
                    new MegagameMove(),
                    new MegagameRotate(),
                    new MegagameClear(),
                    new MegagameGm(),
                    new MegagameCardsInfo(),
                    new MegagameShowGameAs(),
                    new MegagameChannels())
            .collect(Collectors.toMap(Subcommand::getName, Function.identity(), (a, b) -> a, LinkedHashMap::new));

    @Override
    public String getName() {
        return "megagame";
    }

    @Override
    public String getDescription() {
        return "Megagame GM tools";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
