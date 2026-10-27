package ti4.discord.interactions.commands.developer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.commands.Subcommand;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.persistence.DatabasePersistenceGate;
import ti4.spring.service.gameevent.GameEventService;

class ExportFinishedGame extends Subcommand {

    private static final String INCLUDE_MAP_STATE = "include_map_state";

    ExportFinishedGame() {
        super("export_finished_game", "Download a finished game's latest save and its game events.");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "The finished game to export.")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(
                OptionType.BOOLEAN, INCLUDE_MAP_STATE, "Include map snapshots in the events file (default false)."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String gameName = event.getOption(Constants.GAME_NAME).getAsString();
        if (!GameManager.isValid(gameName)) {
            MessageHelper.sendMessageToEventChannel(event, "Can't find game " + gameName + ".");
            return;
        }

        Game game = GameManager.getManagedGame(gameName).getGame();
        if (!game.isHasEnded()) {
            MessageHelper.sendMessageToEventChannel(
                    event, gameName + " has not ended; only finished games can be exported.");
            return;
        }

        File saveFile = Storage.getGameFile(gameName + Constants.TXT);
        if (!saveFile.exists()) {
            MessageHelper.sendMessageToEventChannel(event, "Can't find the save file for " + gameName + ".");
            return;
        }

        if (DatabasePersistenceGate.isDisabled()) {
            MessageHelper.sendMessageToEventChannel(
                    event, "Database persistence is disabled, so game events can't be read.");
            return;
        }

        String events = GameEventService.getBean()
                .formatEventsAsText(game, event.getOption(INCLUDE_MAP_STATE, false, OptionMapping::getAsBoolean));
        FileUpload eventsFile = FileUpload.fromData(events.getBytes(StandardCharsets.UTF_8), gameName + "_events.txt");

        event.getMessageChannel()
                .sendFiles(FileUpload.fromData(saveFile), eventsFile)
                .setContent("Latest save and game events for " + gameName + ".")
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
