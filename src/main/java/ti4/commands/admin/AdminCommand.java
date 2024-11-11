package ti4.commands.admin;

import java.util.Collection;
import java.util.List;

import ti4.commands.Command;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class AdminCommand implements Command {

    private final Collection<Subcommand> subcommands = List.of(
            new DeleteGame(),
            new ResetEmojiCache(),
            new ReloadMap(),
            new ReloadMapperObjects(),
            new RestoreGame(),
            new CardsInfoForPlayer(),
            new UpdateThreadArchiveTime(),
            new UploadStatistics());

    @Override
    public String getActionId() {
        return Constants.ADMIN;
    }

    @Override
    public String getActionDescription() {
        return "Admin";
    }

    @Override
    public Collection<Subcommand> getSubcommands() {
        return subcommands;
    }
}
