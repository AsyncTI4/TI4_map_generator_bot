package ti4.discord.interactions.buttons.handlers.relics.bluereverie;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.oblivion.OblivionUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.stoneborn.StonebornLeadersHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.thundersedge.DSHelperBreakthroughs;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.tech.ListTechService;
import ti4.service.tech.PlayerTechService;

@UtilityClass
public class BRLightrailOrdnanceRelicHandler {
    private static final String RELIC = "lightrailordnance_br";
    private static final String PURGE = "purgeLightrailOrdnance";
    private static final String RESEARCH = "researchLightrailOrdnance_";

    public static Button getCardsInfoButton(Game game, Player player) {
        if (!player.hasRelic(RELIC) || getEligibleUnitUpgrades(game, player).isEmpty()) {
            return null;
        }
        return Buttons.gray(
                player.factionButtonChecker() + PURGE, "Purge Lightrail Ordnance To Research", ExploreEmojis.Relic);
    }

    @ButtonHandler(PURGE)
    public static void purgeLightrailOrdnance(ButtonInteractionEvent event, Game game, Player player) {
        List<TechnologyModel> upgrades = getEligibleUnitUpgrades(game, player);
        if (!player.hasRelic(RELIC) || upgrades.isEmpty()) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        String message = player.getRepresentationNoPing()
                + ", choose a non-faction unit upgrade to research with _Lightrail Ordnance_.";
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                message,
                NewStuffHelper.buttonPagination(
                        getUnitUpgradeButtons(player, upgrades), player.factionButtonChecker() + RESEARCH, 0));
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(RESEARCH)
    public static void researchUnitUpgrade(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String techId = buttonID.substring(RESEARCH.length());
        List<TechnologyModel> upgrades = getEligibleUnitUpgrades(game, player);
        String message = player.getRepresentationNoPing()
                + ", choose a non-faction unit upgrade to research with _Lightrail Ordnance_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                getUnitUpgradeButtons(player, upgrades),
                message,
                player.factionButtonChecker() + RESEARCH,
                buttonID)) {
            return;
        }
        TechnologyModel tech = Mapper.getTech(techId);
        if (!player.hasRelic(RELIC) || tech == null || !upgrades.contains(tech)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That unit upgrade is no longer eligible.");
            return;
        }
        player.removeRelic(RELIC);
        player.removeExhaustedRelic(RELIC);
        DSHelperBreakthroughs.doLanefirBtCheck(game, player);
        OblivionUnitHandler.doOblivionMechCheck(game, player);
        StonebornLeadersHandler.resolveTempusAfterNonActionCardComponentPurge(game, player);
        PlayerTechService.addTech(event, game, player, techId);
        RelicModel lro = Mapper.getRelic("lightrailordnance_br");
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " purged _Lightrail Ordnance_.",
                lro.getRepresentationEmbed());
        ButtonHelper.deleteMessage(event);
    }

    private static List<TechnologyModel> getEligibleUnitUpgrades(Game game, Player player) {
        return ListTechService.getAllTechOfAType(game, "unitupgrade", player, false, true).stream()
                .filter(tech -> tech.getFaction().isEmpty())
                .toList();
    }

    private static List<Button> getUnitUpgradeButtons(Player player, List<TechnologyModel> upgrades) {
        List<Button> buttons = new ArrayList<>();
        for (TechnologyModel tech : upgrades) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + RESEARCH + tech.getAlias(),
                    tech.getName(),
                    tech.getCondensedReqsEmojis(true)));
        }
        return buttons;
    }
}
