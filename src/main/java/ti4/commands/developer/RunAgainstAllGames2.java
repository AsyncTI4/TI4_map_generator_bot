package ti4.commands.developer;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.image.Mapper;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;
import ti4.model.MapTemplateModel;

class RunAgainstAllGames2 extends Subcommand {

    RunAgainstAllGames2() {
        super("run_against_all_games2", "Lists games with mismatched player counts.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Running custom command against all games.");

        List<String> mismatchedGames = new ArrayList<>();
        GamesPage.consumeAllGames(game -> {
            if (game.isHasEnded() && game.getWinner().isEmpty()) {
                return;
            }
            if (game.getRound() < 2) {
                return;
            }
            if (game.isFowMode()) {
                return;
            }

            String mapTemplateId = game.getMapTemplateID();
            int playerCountForMap = 0;
            if (isNotBlank(mapTemplateId)) {
                MapTemplateModel mapTemplateModel = Mapper.getMapTemplate(mapTemplateId);
                if (mapTemplateModel != null) {
                    Integer templatePlayerCount = mapTemplateModel.getPlayerCount();
                    if (templatePlayerCount != null) {
                        playerCountForMap = templatePlayerCount;
                    }
                }
            }

            boolean foundMapTemplate = playerCountForMap != 0;
            if (!foundMapTemplate) {
                playerCountForMap = game.getPlayerCountForMap();
            }

            int realPlayerCount = game.getRealAndEliminatedPlayers().size();
            if (playerCountForMap != realPlayerCount) {
                mismatchedGames.add(game.getName() + " (player count: "
                        + playerCountForMap + ", real player count: "
                        + realPlayerCount + ", found map template: "
                        + foundMapTemplate + ")");
            }
        });
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Found " + mismatchedGames.size() + " games with mismatched player counts out of "
                        + GameManager.getGameCount() + " games:\n " + String.join("\n", mismatchedGames));
        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games.");
    }
}
