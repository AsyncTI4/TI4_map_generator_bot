package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans;

import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.tech.ListTechService;

@UtilityClass
public class DeepReachesStartingTechHandler {
    private static final String NETRUNNERS = "netrunners";
    private static final String REMAINING_TECHS = "deepReachesNetrunnersStartingTechs_";
    private static final String GET_TECHS = "getDeepReachesNetrunnersStartingTechs";
    private static final String SELECT_TECH = "deepReachesNetrunnersStartingTech_";
    private static final String TECH_NAV = "deepReachesNetrunnersStartingTechPage_";

    public static boolean offerStartingTechButtons(Game game, Player player) {
        return offerStartingTechButtons(game, player, player.getFaction());
    }

    public static boolean offerStartingTechButtons(Game game, Player player, String startingTechFaction) {
        if (!NETRUNNERS.equals(startingTechFaction)) {
            return false;
        }
        offerNetrunnersStartingTechs(game, player);
        return true;
    }

    public static void offerNetrunnersStartingTechs(Game game, Player player) {
        if (game.getStoredValue(REMAINING_TECHS + player.getFaction()).isEmpty()) {
            game.setStoredValue(REMAINING_TECHS + player.getFaction(), "2");
        }
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", after other players have received their starting technologies, press this button to choose 2 non-faction technologies owned by other players.",
                Buttons.green(player.factionButtonChecker() + GET_TECHS, "Choose Net-Runners Starting Technologies"));
    }

    public static void clearNetrunnersStartingTechs(Game game, Player player) {
        game.removeStoredValue(REMAINING_TECHS + player.getFaction());
    }

    @ButtonHandler(GET_TECHS)
    public static void getNetrunnersStartingTechs(ButtonInteractionEvent event, Game game, Player player) {
        if (!isNetrunnersStartingTechPending(game, player)) {
            return;
        }
        if (getEligibleTechs(game, player).isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "No other player currently owns an eligible non-faction technology.");
            return;
        }
        sendTechChoices(game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_TECH)
    public static void selectNetrunnersStartingTech(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String techId = buttonID.substring(SELECT_TECH.length());
        if (!isNetrunnersStartingTechPending(game, player)
                || getEligibleTechs(game, player).stream()
                        .noneMatch(tech -> tech.getAlias().equals(techId))) {
            return;
        }
        player.addTech(techId);
        int remaining = getRemainingTechs(game, player) - 1;
        if (remaining <= 0) {
            game.removeStoredValue(REMAINING_TECHS + player.getFaction());
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " finished choosing Net-Runners starting technologies.");
        } else {
            game.setStoredValue(REMAINING_TECHS + player.getFaction(), Integer.toString(remaining));
            sendTechChoices(game, player);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(TECH_NAV)
    public static void turnNetrunnersStartingTechPage(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!isNetrunnersStartingTechPending(game, player)) {
            return;
        }
        NewStuffHelper.turnPage(
                event,
                buttonID,
                getStartingTechMessage(game, player),
                () -> getTechButtons(game, player),
                List.of(),
                player.factionButtonChecker() + TECH_NAV);
    }

    private static void sendTechChoices(Game game, Player player) {
        List<Button> buttons = getTechButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "No eligible Net-Runners starting technology remains to choose.");
            return;
        }
        NewStuffHelper.sendPaginated(
                player.getCorrectChannel(),
                getStartingTechMessage(game, player),
                buttons,
                List.of(),
                player.factionButtonChecker() + TECH_NAV);
    }

    private static List<Button> getTechButtons(Game game, Player player) {
        return getEligibleTechs(game, player).stream()
                .map(tech -> {
                    String buttonId = player.factionButtonChecker() + SELECT_TECH + tech.getAlias();
                    String emoji = tech.getCondensedReqsEmojis(true);
                    return switch (tech.getFirstType()) {
                        case PROPULSION -> Buttons.blue(buttonId, tech.getName(), emoji);
                        case BIOTIC -> Buttons.green(buttonId, tech.getName(), emoji);
                        case WARFARE -> Buttons.red(buttonId, tech.getName(), emoji);
                        default -> Buttons.gray(buttonId, tech.getName(), emoji);
                    };
                })
                .toList();
    }

    private static List<TechnologyModel> getEligibleTechs(Game game, Player player) {
        return java.util.stream.Stream.of(
                        TechnologyType.PROPULSION,
                        TechnologyType.BIOTIC,
                        TechnologyType.CYBERNETIC,
                        TechnologyType.WARFARE,
                        TechnologyType.UNITUPGRADE)
                .flatMap(
                        type -> ListTechService.getAllTechOfAType(game, type.toString(), player, false, false).stream())
                .filter(tech -> game.getRealPlayersExcludingThis(player).stream()
                        .anyMatch(other -> other.hasTech(tech.getAlias())))
                .distinct()
                .sorted(Comparator.comparing(TechnologyModel::getName))
                .toList();
    }

    private static String getStartingTechMessage(Game game, Player player) {
        return player.getRepresentationNoPing() + ", choose " + getRemainingTechs(game, player)
                + " non-faction starting " + (getRemainingTechs(game, player) == 1 ? "technology" : "technologies")
                + " owned by another player.";
    }

    private static boolean isNetrunnersStartingTechPending(Game game, Player player) {
        return getRemainingTechs(game, player) > 0;
    }

    private static int getRemainingTechs(Game game, Player player) {
        return Integer.parseInt(
                game.getStoredValue(REMAINING_TECHS + player.getFaction()).isEmpty()
                        ? "0"
                        : game.getStoredValue(REMAINING_TECHS + player.getFaction()));
    }
}
