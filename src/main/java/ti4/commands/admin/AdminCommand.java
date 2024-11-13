package ti4.commands.admin;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class AdminCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new DeleteGame(),
                    new ResetEmojiCache(),
                    new ReloadMap(),
                    new ReloadMapperObjects(),
                    new RestoreGame(),
                    new CardsInfoForPlayer(),
                    new UpdateThreadArchiveTime(),
                    new UploadStatistics())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.ADMIN;
    }

    @Override
    public String getDescription() {
        return "Admin";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
