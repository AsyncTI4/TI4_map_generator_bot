package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ta;

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
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
public class TaPromissoryHandler {
    private static final String PN_ID = "bepnta";
    private static final String ASE_ATTACHMENT_TOKEN = "attachment_ase.png";
    private static final String TA_PN_ATTACH = "taPnAseAttach_";

    public static boolean hasLegalAdvancedStructuralEngineeringTargets(Player player, Game game) {
        return game != null
                && player != null
                && !player.ownsPromissoryNote(PN_ID)
                && !getLegalAttachPlanets(player, game, ASE_ATTACHMENT_TOKEN).isEmpty();
    }

    public static void offerAdvancedStructuralEngineeringButtons(
            GenericInteractionCreateEvent event, Player player, Game game) {
        offerNonHomePlanetAttachButtons(
                event,
                player,
                game,
                PN_ID,
                ASE_ATTACHMENT_TOKEN,
                TA_PN_ATTACH,
                ", there are no legal planets to play _Advanced Structural Engineering_ on.",
                ", please select a non-home planet to attach _Advanced Structural Engineering_ to.");
    }

    @ButtonHandler(TA_PN_ATTACH)
    public static void resolveTaPnAttach(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (attachToNonHomePlanet(event, game, player, PN_ID, ASE_ATTACHMENT_TOKEN, buttonID, TA_PN_ATTACH) == null) {
            return;
        }
        CommanderUnlockCheckService.checkPlayer(player, "ta");
        ButtonHelper.deleteMessage(event);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + " attached _Advanced Structural Engineering_ to "
                        + Helper.getPlanetRepresentation(buttonID.split("\\|", 2)[1], game));
    }

    public static boolean planetHasAdvancedStructuralEngineering(Planet planet) {
        return planet != null && planet.getTokenList().contains(ASE_ATTACHMENT_TOKEN);
    }

    private static List<String> getLegalAttachPlanets(Player player, Game game, String attachmentToken) {
        List<String> legalPlanets = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planet != null
                    && !planet.isHomePlanet()
                    && !planet.getTokenList().contains(attachmentToken)) {
                legalPlanets.add(planetName);
            }
        }
        return legalPlanets;
    }

    public static void offerNonHomePlanetAttachButtons(
            GenericInteractionCreateEvent event,
            Player player,
            Game game,
            String pnId,
            String attachmentToken,
            String buttonPrefix,
            String noLegalPlanetsMessage,
            String prompt) {
        if (game == null
                || player == null
                || player.ownsPromissoryNote(pnId)
                || !player.getPromissoryNotesInPlayArea().contains(pnId)) {
            return;
        }

        List<String> legalPlanets = getLegalAttachPlanets(player, game, attachmentToken);
        if (legalPlanets.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), player.getRepresentation() + noLegalPlanetsMessage);
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String planetName : legalPlanets) {
            Tile tile = game.getTileFromPlanet(planetName);
            if (tile != null) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + buttonPrefix + tile.getPosition() + "|" + planetName,
                        Helper.getPlanetRepresentation(planetName, game)));
            }
        }
        buttons.add(Buttons.DECLINE);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), player.getRepresentation() + prompt, buttons);
    }

    public static Tile attachToNonHomePlanet(
            ButtonInteractionEvent event,
            Game game,
            Player player,
            String pnId,
            String attachmentToken,
            String buttonID,
            String buttonPrefix) {
        if (event == null || game == null || player == null) {
            return null;
        }
        String[] parts = buttonID.substring(buttonPrefix.length()).split("\\|", 2);
        Tile tile = parts.length == 2 ? game.getTileByPosition(parts[0]) : null;
        Planet planet = tile == null ? null : tile.getUnitHolderFromPlanet(parts[1]);
        if (player.ownsPromissoryNote(pnId)
                || !player.getPromissoryNotesInPlayArea().contains(pnId)
                || planet == null
                || !player.getPlanets().contains(parts[1])
                || planet.isHomePlanet()
                || planet.getTokenList().contains(attachmentToken)) {
            ButtonHelper.deleteMessage(event);
            return null;
        }
        planet.addToken(attachmentToken);
        return tile;
    }
}
