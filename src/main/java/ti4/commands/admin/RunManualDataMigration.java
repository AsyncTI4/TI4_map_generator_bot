package ti4.commands.admin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel.AutoArchiveDuration;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.DataMigrationManager;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.message.BotLogger;

public class RunManualDataMigration extends AdminSubcommandData {
    public RunManualDataMigration() {
        super(Constants.RUN_MANUAL_DATA_MIGRATION, "Run a manual data migration on a game.");
        addOptions(new OptionData(OptionType.STRING, Constants.MIGRATION_NAME, "migration name").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "The game to run the migration against").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        
        String migrationName = event.getOption(Constants.MIGRATION_NAME).getAsString();
        String gameName = event.getOption(Constants.GAME_NAME).getAsString();
        Map activeMap = MapManager.getInstance().getMap(gameName);
        if(activeMap == null){
            sendMessage("Cant find map for game name" + gameName);
            return;
        }
        
        try {
            Class<?>[] paramTypes = {Map.class};
            Method method = DataMigrationManager.class.getMethod(migrationName, paramTypes);
            Boolean changesMade = (Boolean) method.invoke(null, activeMap);
            if(changesMade){
                sendMessage("Successfully run migration " + migrationName + " for map " + activeMap.getName());
            }else{
                sendMessage("Successfully run migration " + migrationName + " for map " + activeMap.getName() + " but no changes were required.");
            }
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            BotLogger.log("failed to run data migration", e);
            e.printStackTrace();
        }
    }
}
