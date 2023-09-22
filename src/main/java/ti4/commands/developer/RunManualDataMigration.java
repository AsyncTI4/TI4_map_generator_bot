package ti4.commands.developer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.DataMigrationManager;
import ti4.commands.admin.AdminSubcommandData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class RunManualDataMigration extends DeveloperSubcommandData {
    public RunManualDataMigration() {
        super(Constants.RUN_MANUAL_DATA_MIGRATION, "Run a manual data migration on a game.");
        addOptions(new OptionData(OptionType.STRING, Constants.MIGRATION_NAME, "migration name").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "The game to run the migration against").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        
        String migrationName = event.getOption(Constants.MIGRATION_NAME).getAsString();
        String gameName = event.getOption(Constants.GAME_NAME).getAsString();
        Game activeGame = GameManager.getInstance().getGame(gameName);
        if(activeGame == null){
            MessageHelper.sendMessageToChannel(event.getChannel(), "Cant find map for game name" + gameName);
            return;
        }
        
        try {
            Class<?>[] paramTypes = {Game.class};
            Method method = DataMigrationManager.class.getMethod(migrationName, paramTypes);
            Boolean changesMade = (Boolean) method.invoke(null, activeGame);
            if(changesMade){
                MessageHelper.sendMessageToChannel(event.getChannel(), "Successfully run migration " + migrationName + " for map " + activeGame.getName());
            }else{
                MessageHelper.sendMessageToChannel(event.getChannel(), "Successfully run migration " + migrationName + " for map " + activeGame.getName() + " but no changes were required.");
            }
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            BotLogger.log("failed to run data migration", e);
            e.printStackTrace();
        }
    }
}
