package ti4.commands.developer;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.message.MessageHelper;
import ti4.spring.context.SpringContext;
import ti4.spring.service.developer.RunSqlService;

class RunSql extends Subcommand {

    RunSql() {
        super("run_sql", "Run SQL directly against the SQLite database.");
        addOptions(new OptionData(OptionType.STRING, "sql", "The SQL query or statement to execute.")
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String sql = event.getOption("sql").getAsString();
        RunSqlService runSqlService = SpringContext.getBean(RunSqlService.class);
        String result = runSqlService.executeSql(sql);

        String threadName = String.format(
                "%s %s",
                event.getFullCommandName(),
                OffsetDateTime.now().format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")));
        MessageHelper.sendMessageToThread(event.getChannel(), threadName, result);
    }
}
