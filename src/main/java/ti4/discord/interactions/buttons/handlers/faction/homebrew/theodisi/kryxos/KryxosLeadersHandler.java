package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Kryxos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
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
    private static final String SELECT_KRYXOS_HERO_UNIT_UPGRADE = "selectKryxosHeroUnitUpgrade_";
    private static final String GAIN_KRYXOS_HERO_TECH = "gainKryxosHeroTech_";

    // Agent
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
                    game.getActionsChannel(),
                    target.getRepresentationUnfogged()
                            + " has no non-faction technology available to return for Dravok Veyl.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                game.getActionsChannel(),
                target.getRepresentationUnfogged()
                        + ", please choose the non-faction technology to return for Dravok Veyl, the Kryxos agent.",
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
        PlayerTechService.getTech(game, player, event, "getTech_" + techId);
    }

    // Hero
    public static void startKryxosHero(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        List<Button> buttons = player.getTechs().stream()
                .map(Mapper::getTech)
                .filter(Objects::nonNull)
                .filter(TechnologyModel::isUnitUpgrade)
                .map(tech -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_KRYXOS_HERO_UNIT_UPGRADE + tech.getAlias(),
                        tech.getName(),
                        tech.getSingleTechEmoji()))
                .toList();

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " has no owned unit upgrade technology to select for Zorath Ultimus.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", choose 1 of your unit upgrade technologies. For each prerequisite on it,"
                        + " gain 1 unowned technology of that color due to Zorath Ultimus, the Kryxos hero.",
                buttons);
    }

    @ButtonHandler(SELECT_KRYXOS_HERO_UNIT_UPGRADE)
    public static void selectKryxosHeroUnitUpgrade(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String techID = buttonID.substring(SELECT_KRYXOS_HERO_UNIT_UPGRADE.length());
        TechnologyModel tech = Mapper.getTech(techID);
        if (game == null || player == null || tech == null || !tech.isUnitUpgrade() || !player.hasTech(techID)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        for (char prerequisite : tech.getRequirements().orElse("").toLowerCase().toCharArray()) {
            String techType =
                    switch (prerequisite) {
                        case 'b' -> TechnologyModel.TechnologyType.PROPULSION.toString();
                        case 'g' -> TechnologyModel.TechnologyType.BIOTIC.toString();
                        case 'y' -> TechnologyModel.TechnologyType.CYBERNETIC.toString();
                        case 'r' -> TechnologyModel.TechnologyType.WARFARE.toString();
                        default -> "";
                    };
            if (techType.isEmpty()) {
                continue;
            }
            List<TechnologyModel> techs =
                    new ArrayList<>(ListTechService.getAllTechOfAType(game, techType, player, false, false));
            List<Button> buttons = new ArrayList<>();
            for (TechnologyModel gainableTech : techs) {
                String techButtonID = player.factionButtonChecker() + GAIN_KRYXOS_HERO_TECH + gainableTech.getAlias();
                String emoji = gainableTech.getCondensedReqsEmojis(true);
                buttons.add(
                        switch (gainableTech.getFirstType()) {
                            case PROPULSION -> Buttons.blue(techButtonID, gainableTech.getName(), emoji);
                            case BIOTIC -> Buttons.green(techButtonID, gainableTech.getName(), emoji);
                            case WARFARE -> Buttons.red(techButtonID, gainableTech.getName(), emoji);
                            default -> Buttons.gray(techButtonID, gainableTech.getName(), emoji);
                        });
            }
            if (buttons.isEmpty()) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        player.getRepresentationNoPing() + " has no unowned " + techType + " technology to gain.");
                continue;
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", gain 1 " + techType + " technology for a prerequisite on "
                            + tech.getNameRepresentation() + ".",
                    buttons);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(GAIN_KRYXOS_HERO_TECH)
    public static void gainKryxosHeroTech(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String techID = buttonID.substring(GAIN_KRYXOS_HERO_TECH.length());
        TechnologyModel tech = Mapper.getTech(techID);
        if (game == null || player == null || tech == null || player.hasTech(techID)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        PlayerTechService.addTech(event, game, player, techID);
        ButtonHelper.deleteMessage(event);
    }
}
