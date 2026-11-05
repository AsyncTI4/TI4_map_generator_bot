package ti4.discord.interactions.commands.developer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.commands.Subcommand;
import ti4.executors.RunningTaskRegistry;
import ti4.executors.RunningTaskRegistry.RunningTask;
import ti4.logging.BotLogger;

class RunningTasks extends Subcommand {

    RunningTasks() {
        super("running_tasks", "Show currently running worker tasks, crons, and scheduled jobs on this bot instance.");
    }

    @Override
    public boolean isEphemeral(SlashCommandInteractionEvent event) {
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String report = formatReport(RunningTaskRegistry.snapshot());
        String content = "```\n" + report.replace("`", "'") + "\n```";
        if (content.length() <= 2000) {
            event.getHook()
                    .editOriginal(content)
                    .setAllowedMentions(List.of())
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }
        event.getHook()
                .editOriginal("Currently running tasks are attached.")
                .setFiles(FileUpload.fromData(report.getBytes(StandardCharsets.UTF_8), "running-tasks.txt"))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    static String formatReport(List<RunningTask> tasks) {
        if (tasks.isEmpty()) return "No tracked tasks are currently running on this bot instance.";
        StringBuilder report = new StringBuilder("Running tasks on this bot instance: ")
                .append(tasks.size())
                .append("\nElapsed | Thread state | Thread | Task\n");
        for (RunningTask task : tasks) {
            report.append(task.elapsed().toMillis())
                    .append(" ms | ")
                    .append(task.threadState())
                    .append(" | ")
                    .append(task.threadName())
                    .append(" | ")
                    .append(task.name())
                    .append('\n');
        }
        return report.toString();
    }
}
