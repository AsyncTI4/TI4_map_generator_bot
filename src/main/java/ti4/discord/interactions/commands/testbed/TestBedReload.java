package ti4.discord.interactions.commands.testbed;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.message.MessageHelper;
import ti4.service.testbed.TestBedReloadService;
import ti4.service.testbed.TestBedReloadService.Report;

class TestBedReload extends Subcommand {

    private static final int MAX_LISTED = 15;

    TestBedReload() {
        super("reload", "Re-read presets, scripts and test button files (yours in data/testbed/local too)");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Report report = TestBedReloadService.reload();
        StringBuilder reply = new StringBuilder("Reloaded ")
                .append(report.files())
                .append(" test bed files (")
                .append(report.localFiles())
                .append(" from `data/testbed/local`).");
        if (report.problems().isEmpty()) {
            reply.append(" All valid.");
        } else {
            reply.append(' ').append(report.problems().size()).append(" problem(s):");
            report.problems().stream()
                    .limit(MAX_LISTED)
                    .forEach(problem -> reply.append("\n- ").append(problem));
            if (report.problems().size() > MAX_LISTED) reply.append("\n- …");
        }
        MessageHelper.replyToMessage(event, reply.toString());
    }
}
