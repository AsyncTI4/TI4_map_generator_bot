package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.migo;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
public class MigoLeadersHandler {
    private static final String COMMANDER = "migocommander";
    private static final String COMMANDER_ATTACHMENT = "attachment_mcp.png";
    private static final String HERO_TECH_PAGE = "migoHeroTechPage_";

    public static void startMigoHero(GenericInteractionCreateEvent event, Game game, Player player) {
        NewStuffHelper.sendPaginated(
                player.getCorrectChannel(),
                heroMessage(player),
                heroReadyButtons(game, player),
                List.of(Buttons.DONE_DELETE_BUTTONS),
                player.factionButtonChecker() + HERO_TECH_PAGE);
    }

    @ButtonHandler(HERO_TECH_PAGE)
    public static void changeHeroTechPage(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        NewStuffHelper.turnPage(
                event,
                buttonID,
                heroMessage(player),
                () -> heroReadyButtons(game, player),
                List.of(Buttons.DONE_DELETE_BUTTONS),
                player.factionButtonChecker() + HERO_TECH_PAGE);
    }

    private static List<Button> heroReadyButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            Planet planetM = game.getPlanetsInfo().get(planet);
            if (planetM == null
                    || !player.getExhaustedPlanets().contains(planet)
                    || planetM.getTechSpecialities().isEmpty()) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "heroTechReady_" + planet,
                    "Ready " + Helper.getPlanetRepresentation(planet, game)));
        }
        for (String techId : player.getExhaustedTechs()) {
            TechnologyModel tech = Mapper.getTech(techId);
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "heroTechReady_tech_" + techId, "Ready " + tech.getName()));
        }
        return buttons;
    }

    private static String heroMessage(Player player) {
        return player.getRepresentation()
                + ", you may ready any combination of up to 4 technologies or planets with technology specialties using _Y'goloth_, the Mi-Go Hero.";
    }

    @ButtonHandler("heroTechReady_")
    public static void heroTechReady(Game game, GenericInteractionCreateEvent event, Player player, String buttonID) {
        buttonID = buttonID.replace("heroTechReady_", "");
        String last = buttonID.substring(buttonID.lastIndexOf('_') + 1);
        if (buttonID.contains("tech_")) {
            last = buttonID.replace("tech_", "");
            player.refreshTech(last);
            CommanderUnlockCheckService.checkPlayer(player, "kolume");
        } else {
            player.refreshPlanet(last);
        }
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void syncMigoCommanderBonus(Game game) {
        for (Planet planet : game.getPlanetsInfo().values()) {
            Player controller = game.getPlayerThatControlsPlanet(planet.getName(), true);
            boolean shouldHaveBonus = controller != null
                    && game.playerHasLeaderUnlockedOrAlliance(controller, COMMANDER)
                    && !planet.getTechSpecialities().isEmpty();
            if (shouldHaveBonus) {
                planet.addToken(COMMANDER_ATTACHMENT);
            } else {
                planet.removeToken(COMMANDER_ATTACHMENT);
            }
        }
    }
}
