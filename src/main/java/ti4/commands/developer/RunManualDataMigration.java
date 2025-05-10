package ti4.commands.developer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.manage.GameManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.migration.DataMigrationManager;

class RunManualDataMigration extends Subcommand {

    public RunManualDataMigration() {
        super(Constants.RUN_MANUAL_DATA_MIGRATION, "Run a manual data migration on a game.");
        addOptions(new OptionData(OptionType.STRING, Constants.MIGRATION_NAME, "migration name").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "The game to run the migration against").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String migrationName = event.getOption(Constants.MIGRATION_NAME).getAsString();
        String gameName = event.getOption(Constants.GAME_NAME).getAsString();
        if (!GameManager.isValid(gameName)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Can't find map for game name" + gameName);
            return;
        }

        Game game = GameManager.getManagedGame(gameName).getGame();
        try {
            Class<?>[] paramTypes = { Game.class };
            Method method = DataMigrationManager.class.getMethod(migrationName, paramTypes);
            method.setAccessible(true);
            Boolean changesMade = (Boolean) method.invoke(null, game);
            if (changesMade) {
                game.addMigration(migrationName);
                GameManager.save(game, "Migration ran: " + migrationName);
                MessageHelper.sendMessageToChannel(event.getChannel(), "Successfully ran migration " + migrationName + " for map " + game.getName());
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Successfully ran migration " + migrationName + " for map " + game.getName() + " but no changes were required.");
            }
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(event), "failed to run data migration", e);
        }
    }
}
