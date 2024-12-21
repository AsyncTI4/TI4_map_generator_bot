package ti4.commands2.bothelper;

import java.time.Instant;
import java.time.ZoneId;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.ToStringHelper;
import ti4.map.Game;
import ti4.map.manage.GameManager;
import ti4.message.MessageHelper;
import ti4.model.metadata.AutoPingMetadataManager;
import ti4.service.game.GameNameService;

class CheckNextPingTime extends Subcommand {

    public CheckNextPingTime() {
        super("check_next_ping_time", "Check the next time the bot will ping this game");
        addOption(OptionType.STRING, Constants.GAME_NAME, "Game to check", false, true);
    }

    public void execute(SlashCommandInteractionEvent event) {
        String gameName = GameNameService.getGameName(event);
        Game game = GameManager.getManagedGame(gameName).getGame();
        AutoPingMetadataManager.AutoPing latestPing = AutoPingMetadataManager.getLatestAutoPing(gameName);
        String pingInfo = latestPing == null ? "No upcoming ping was found for " + gameName :
            ToStringHelper.of("Ping Info")
                .add("Game name", gameName)
                .add("Ping count", latestPing.pingCount())
                .add("Last ping time", Instant.ofEpochMilli(latestPing.lastPingTimeEpochMilliseconds()).atZone(ZoneId.of("UTC")).toString())
                .add("Quick ping active", latestPing.quickPing())
                .add("Auto ping active", game.getAutoPingStatus())
                .add("Ping interval", game.getAutoPingSpacer())
                .toString();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "```" + pingInfo + "```");
    }
}
