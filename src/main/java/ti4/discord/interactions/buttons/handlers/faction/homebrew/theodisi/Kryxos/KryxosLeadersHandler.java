package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Kryxos;

import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.service.tech.ListTechService;
import ti4.service.tech.PlayerTechService;

@UtilityClass
public class KryxosLeadersHandler {
    private static final String RETURN_TECH = "returnTechForKryxosAgent_";
    private static final String RESEARCH_UNIT_UPGRADE = "researchKryxosAgentUUTech_";

    public static void startKryxosAgent(Game game, Player target) {
        if (game == null || target == null) {
            return;
        }

        List<Button> buttons = target.getTechs().stream()
                .map(Mapper::getTech)
                .filter(Objects::nonNull)
                .filter(tech -> tech.getFaction().isEmpty())
                .map(tech ->
                        Buttons.green(target.factionButtonChecker() + RETURN_TECH + tech.getAlias(), tech.getName()))
                .toList();

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    target.getCardsInfoThread(),
                    target.getRepresentationUnfogged()
                            + " has no non-faction technology available to return for Dravok Veyl.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                target.getCardsInfoThread(),
                target.getRepresentationUnfogged()
                        + ", select the non-faction technology you would like to return for Dravok Veyl:",
                buttons);
    }

    @ButtonHandler(RETURN_TECH)
    public static void resolveKryxosAgentReturn(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String techId = buttonID.replace(RETURN_TECH, "");
        TechnologyModel techM = Mapper.getTech(techId);
        if (techM == null || techM.getFaction().isPresent() || !player.hasTech(techId)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Unable to return selected tech.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.removeTech(techId);
        ButtonHelper.deleteMessage(event);

        List<Button> buttons = ListTechService.getAllTechOfAType(
                        game, TechnologyModel.TechnologyType.UNITUPGRADE.toString(), player, false, true)
                .stream()
                .map(tech -> Buttons.gray(
                        player.factionButtonChecker() + RESEARCH_UNIT_UPGRADE + tech.getAlias(), tech.getName()))
                .toList();

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationUnfogged() + " returned " + techM.getNameRepresentation()
                            + ", but has no researchable unit upgrade technology.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + " returned " + techM.getNameRepresentation()
                        + ", and may now research a unit-upgrade technology:",
                buttons);
    }

    @ButtonHandler(RESEARCH_UNIT_UPGRADE)
    public static void researchUnitUpgradeTechKryxosAgent(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String techId = buttonID.replace(RESEARCH_UNIT_UPGRADE, "");
        TechnologyModel techM = Mapper.getTech(techId);
        boolean isResearchableUnitUpgrade = ListTechService.getAllTechOfAType(
                        game, TechnologyModel.TechnologyType.UNITUPGRADE.toString(), player, false, true)
                .stream()
                .anyMatch(tech -> tech.getAlias().equals(techId));
        if (techM == null || !isResearchableUnitUpgrade) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Unable to research technology.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelper.deleteMessage(event);
        PlayerTechService.getTech(game, player, event, "getTech_" + techId + "__noPay");
    }
}
