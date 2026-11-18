package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.migo;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;

@UtilityClass
public class MigoBreakthroughHandler {
    private static final String YTHIC_SYMBIOSIS = "migobt";
    private static final String USE_YTHIC_SYMBIOSIS = "migoUseYthicSymbiosis_";
    private static final String EXHAUST_TECH = "migoYthicSymbiosisExhaust_";
    private static final String PAGE = "migoYthicSymbiosisPage_";
    private static final String CONTEXT_SEPARATOR = "~";

    public static boolean canUseYthicSymbiosis(Game game, Player player, String planetName) {
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        return planet != null
                && player.hasUnlockedBreakthrough(YTHIC_SYMBIOSIS)
                && player.hasPlanet(planetName)
                && !planet.getTechSpecialities().isEmpty()
                && player.getTechs().stream().anyMatch(player::hasTechReady);
    }

    public static Button getYthicSymbiosisButton(Player player, String planetName) {
        return Buttons.green(player.factionButtonChecker() + USE_YTHIC_SYMBIOSIS + planetName, "Use Ythic Symbiosis");
    }

    @ButtonHandler(USE_YTHIC_SYMBIOSIS)
    public static void useYthicSymbiosis(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.substring(USE_YTHIC_SYMBIOSIS.length());
        if (!canUseYthicSymbiosis(game, player, planetName)) {
            return;
        }
        showTechnologyChoices(event, game, player, planetName, 0);
    }

    @ButtonHandler(EXHAUST_TECH)
    public static void exhaustTechForYthicSymbiosis(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] context = buttonID.substring(EXHAUST_TECH.length()).split(CONTEXT_SEPARATOR, 2);
        if (context.length != 2
                || !canUseYthicSymbiosis(game, player, context[0])
                || !player.hasTechReady(context[1])) {
            return;
        }
        Planet planet = game.getUnitHolderFromPlanet(context[0]);
        Player committingPlayer = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, planet).stream()
                .filter(otherPlayer -> otherPlayer != player && !player.isPlayerMemberOfAlliance(otherPlayer))
                .findFirst()
                .orElse(null);
        if (committingPlayer == null
                || !ButtonHelperAbilities.resolveCoexistence(event, game, committingPlayer, planet.getName())) {
            return;
        }
        player.exhaustTech(context[1]);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                committingPlayer.getRepresentation() + ", " + player.getRepresentationNoPing() + " exhausted _"
                        + Mapper.getTech(context[1]).getName()
                        + "_ to resolve _Ythic Symbiosis_ on "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + ".");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PAGE)
    public static void changeYthicSymbiosisPage(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int pageIndex = buttonID.lastIndexOf("_page");
        if (pageIndex < 0) {
            return;
        }
        String planetName = buttonID.substring(PAGE.length(), pageIndex);
        if (!canUseYthicSymbiosis(game, player, planetName)) {
            return;
        }
        NewStuffHelper.turnPage(
                event,
                buttonID,
                getYthicSymbiosisMessage(game, player, planetName),
                () -> getTechnologyButtons(player, planetName),
                List.of(Buttons.DECLINE_DELETE_BUTTONS),
                player.factionButtonChecker() + PAGE + planetName + "_");
    }

    private static void showTechnologyChoices(
            ButtonInteractionEvent event, Game game, Player player, String planetName, int page) {
        MessageHelper.editMessageWithButtons(
                event,
                getYthicSymbiosisMessage(game, player, planetName),
                NewStuffHelper.paginateWithPinnedButtons(
                        getTechnologyButtons(player, planetName),
                        List.of(Buttons.DECLINE_DELETE_BUTTONS),
                        player.factionButtonChecker() + PAGE + planetName + "_",
                        25,
                        page));
    }

    private static List<Button> getTechnologyButtons(Player player, String planetName) {
        List<Button> buttons = new ArrayList<>();
        for (String techId : player.getTechs()) {
            if (!player.hasTechReady(techId)) {
                continue;
            }
            TechnologyModel technology = Mapper.getTech(techId);
            if (technology != null) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + EXHAUST_TECH + planetName + CONTEXT_SEPARATOR + techId,
                        "Exhaust " + technology.getName()));
            }
        }
        return buttons;
    }

    private static String getYthicSymbiosisMessage(Game game, Player player, String planetName) {
        return player.getRepresentationNoPing() + ", choose a technology to exhaust to force units committed to "
                + Helper.getPlanetRepresentation(planetName, game) + " into coexistence with _Ythic Symbiosis_.";
    }
}
