package ti4.helpers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ColorEmojis;
import ti4.service.game.ManagedGameService;

@UtilityClass
public class SearchGameForFactionHelper {

    public static int searchGames(String faction, GenericInteractionCreateEvent event, boolean includeEndedGames) {

        Predicate<ManagedGame> factionFilter = game -> game.getGame().getPlayerFromColorOrFaction(faction) != null;
        Predicate<ManagedGame> endedGamesFilter =
                includeEndedGames ? game -> true : game -> !game.isHasEnded() && !game.isFowMode();
        Predicate<ManagedGame> onlyEndedFoWGames = game -> !game.isFowMode() || game.isHasEnded();
        Predicate<ManagedGame> allFilterPredicates =
                endedGamesFilter.and(onlyEndedFoWGames).and(factionFilter);

        var filteredManagedGames = GameManager.getManagedGames().stream()
                .filter(allFilterPredicates)
                .sorted(Comparator.comparing(ManagedGameService::getGameNameForSorting))
                .toList();

        int index = 1;

        StringBuilder sb = new StringBuilder("**__")
                .append(Mapper.getFaction(faction).getFactionName())
                .append("'s Games__**\n");
        for (var managedGame : filteredManagedGames) {
            sb.append("`").append(Helper.leftpad("" + index, 2)).append(".`");
            var game = managedGame.getGame();
            sb.append(getPlayerMapListRepresentation(
                    game, game.getPlayerFromColorOrFaction(faction).getUserID(), false, false, false));
            sb.append("\n");
            index++;
        }
        if (event instanceof SlashCommandInteractionEvent slash) {
            MessageHelper.sendMessageToThread(
                    slash.getChannel(), Mapper.getFaction(faction).getFactionName() + "'s Game List", sb.toString());
        }
        if (event instanceof ButtonInteractionEvent butt) {
            MessageHelper.sendMessageToThread(
                    butt.getChannel(), Mapper.getFaction(faction).getFactionName() + "'s Game List", sb.toString());
        }
        return filteredManagedGames.size();
    }

    public static String getPlayerMapListRepresentation(
            Game game, String userID, boolean showAverageTurnTime, boolean showSecondaries, boolean showGameModes) {
        Player player = game.getPlayer(userID);
        if (player == null) return "";
        String gameChannelLink =
                game.getActionsChannel() == null ? "" : game.getActionsChannel().getAsMention();
        StringBuilder sb = new StringBuilder();
        if (Mapper.isValidFaction(player.getFaction())) sb.append(player.getFactionEmoji());
        if (player.getColor() != null && !"null".equals(player.getColor()))
            sb.append(ColorEmojis.getColorEmoji(player.getColor()));
        sb.append("**").append(game.getName()).append("**");
        sb.append(gameChannelLink);
        if (game.hasWinner()) {
            for (Player winner : game.getWinners()) {
                if (winner.getUserID().equals(player.getUserID())) sb.append(" **👑WINNER👑**");
            }
        }
        if (game.getActivePlayerID() != null && game.getActivePlayerID().equals(userID) && !game.isHasEnded())
            sb.append(" - __It is your turn__)");
        if (showSecondaries && !game.isHasEnded()) {
            List<String> secondaries = new ArrayList<>();
            for (int sc : game.getPlayedSCs()) {
                if (!player.hasFollowedSC(sc) && !player.getSCs().contains(sc)) {
                    secondaries.add(CardEmojis.getSCBackFromInteger(sc).toString());
                }
            }
            if (!secondaries.isEmpty()) {
                sb.append("\n> Please follow: ").append(String.join(" ", secondaries));
            }
        }
        if (showGameModes) sb.append(" | Game Modes: ").append(game.getGameModesText());
        if (game.isHasEnded()) sb.append(" [GAME IS OVER]");
        return sb.toString();
    }
}
