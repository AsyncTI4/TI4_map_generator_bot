package ti4.discord.interactions.commands.admin;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.discord.interactions.commands.Subcommand;
import ti4.discord.interactions.commands.SubcommandGroup;
import ti4.helpers.Constants;

class ChannelExportGroup extends SubcommandGroup {

    private static final Map<String, Subcommand> subcommands = Stream.of(
                    new StartChannelExport(), new CancelChannelExport(), new ResumeChannelExport())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    ChannelExportGroup() {
        super(Constants.CHANNEL_EXPORT, "Export a channel's history to the bot server, then cancel or resume it");
    }

    @Override
    public Map<String, Subcommand> getGroupSubcommands() {
        return subcommands;
    }
}
