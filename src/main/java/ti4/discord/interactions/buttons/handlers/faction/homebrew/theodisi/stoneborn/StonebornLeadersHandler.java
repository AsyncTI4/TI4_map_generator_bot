package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.stoneborn;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperExplore;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.RelicHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.ExhaustLeaderService;

@UtilityClass
public class StonebornLeadersHandler {
    private static final String PURGE_HAZORUS_FRAGMENT = "purgeHazorusFragment_";
    private static final String USE_HAZORUS_ON_SELF = "useHazorusOnSelf_";
    private static final String SELECT_HAZORUS_TARGET = "selectHazorusTarget";
    private static final String USE_HAZORUS_ON_OTHER = "useHazorusOnOther_";
    private static final String GAIN_TEMPUS_UNLEASHED_TG = "gainTempusUnleashedTG";
    private static final String EXPLORE_TEMPUS_UNLEASHED = "exploreTempusUnleashed";
    private static final String CHOOSE_METEORIME_RELIC = "chooseMeteorimeRelic_";
    private static final String METEORIME_DRAWN_RELICS = "stonebornMeteorimeDrawnRelics_";
    private static final List<String> HAZORUS_AGENTS = List.of("stonebornagentdormant", "stonebornagentunleashed");
    private static final Pattern EXTRA_RELIC_SUFFIX_PATTERN = Pattern.compile("extra\\d+$");

    public static String tempusUsedKey(Player player) {
        return "stonebornTempusUsedAction_" + player.getFaction();
    }

    public static void offerHazorusFragmentPurge(Game game, Player agentOwner, Player target, String agent) {
        if (!HAZORUS_AGENTS.contains(agent)) {
            return;
        }
        List<Button> buttons = getHazorusFragmentButtons(target, agentOwner, agent);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    target.getCorrectChannel(),
                    target.getRepresentationNoPing() + " has no relic fragments to purge for Hazorus.");
            return;
        }
        NewStuffHelper.sendPaginated(
                target.getCorrectChannel(),
                getHazorusMessage(target),
                buttons,
                List.of(),
                target.factionButtonChecker() + PURGE_HAZORUS_FRAGMENT + agentOwner.getFaction() + "|" + agent + "|");
    }

    public static Button getHazorusStartTurnButton(Player player) {
        String agent = getReadyHazorusAgent(player);
        if (agent == null || player.getFragments().isEmpty()) {
            return null;
        }
        return Buttons.gray(
                player.factionButtonChecker() + USE_HAZORUS_ON_SELF + agent,
                "Use Hazorus Agent",
                FactionEmojis.stoneborn);
    }

    public static Button getHazorusCardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + SELECT_HAZORUS_TARGET,
                "Use Hazorus Agent on Another Player",
                FactionEmojis.stoneborn);
    }

    @ButtonHandler(USE_HAZORUS_ON_SELF)
    public static void useHazorusOnSelf(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        useHazorus(game, player, player, event, buttonID.substring(USE_HAZORUS_ON_SELF.length()));
    }

    @ButtonHandler(SELECT_HAZORUS_TARGET)
    public static void selectHazorusTarget(Game game, Player player, ButtonInteractionEvent event) {
        if (getReadyHazorusAgent(player) == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Button> buttons = game.getRealPlayers().stream()
                .filter(target -> target != player)
                .map(target -> FoWHelper.fogSafeTargetButton(
                        player.factionButtonChecker() + USE_HAZORUS_ON_OTHER + target.getFaction(),
                        "gray",
                        target,
                        player))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                getOutputChannel(game, player, event),
                player.getRepresentationNoPing() + ", choose a player for Hazorus.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(USE_HAZORUS_ON_OTHER)
    public static void useHazorusOnOther(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(USE_HAZORUS_ON_OTHER.length()));
        if (target == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        useHazorus(game, player, target, event, getReadyHazorusAgent(player));
    }

    private static void useHazorus(
            Game game, Player agentOwner, Player target, ButtonInteractionEvent event, String agentID) {
        if (!HAZORUS_AGENTS.contains(agentID)
                || (agentOwner == target && target.getFragments().isEmpty())) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Leader agent = agentOwner.getLeader(agentID).orElse(null);
        if (agent == null || agent.isExhausted()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        ExhaustLeaderService.exhaustLeader(game, agentOwner, agent);
        offerHazorusFragmentPurge(game, agentOwner, target, agentID);
        ButtonHelper.deleteTheOneButton(event);
    }

    private static String getReadyHazorusAgent(Player player) {
        return HAZORUS_AGENTS.stream()
                .filter(player::hasUnexhaustedLeader)
                .findFirst()
                .orElse(null);
    }

    public static void offerTempusUnleashedChoice(Game game, Player player, ButtonInteractionEvent event) {
        offerTempusUnleashedChoice(game, player);
    }

    public static boolean offerTempusUnleashedChoice(Game game, Player player) {
        if (!game.playerHasLeaderUnlockedOrAlliance(player, "stoneborncommanderunleashed")
                || !game.getStoredValue(tempusUsedKey(player)).isEmpty()) {
            return false;
        }
        game.setStoredValue(tempusUsedKey(player), "used");
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose how to resolve _Tempus Unleashed_.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + GAIN_TEMPUS_UNLEASHED_TG,
                                "Gain 1 Trade Good",
                                FactionEmojis.stoneborn),
                        Buttons.green(
                                player.factionButtonChecker() + EXPLORE_TEMPUS_UNLEASHED,
                                "Explore 1 Planet You Control",
                                FactionEmojis.stoneborn)));
        return true;
    }

    @ButtonHandler(GAIN_TEMPUS_UNLEASHED_TG)
    public static void gainTempusUnleashedTradeGood(Game game, Player player, ButtonInteractionEvent event) {
        if (!game.playerHasLeaderUnlockedOrAlliance(player, "stoneborncommanderunleashed")) {
            return;
        }
        ButtonHelperStats.gainTGs(event, game, player, 1, false);
        ButtonHelper.deleteAllButtons(event);
    }

    @ButtonHandler(EXPLORE_TEMPUS_UNLEASHED)
    public static void exploreTempusUnleashed(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        if (!game.playerHasLeaderUnlockedOrAlliance(player, "stoneborncommanderunleashed")) {
            return;
        }
        List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, game);
        String message = player.getRepresentation() + ", choose 1 planet to explore with _Tempus Unleashed_.";
        if (NewStuffHelper.turnPage(
                event,
                buttonID,
                message,
                () -> ButtonHelper.getButtonsToExploreAllPlanets(player, game),
                List.of(),
                player.factionButtonChecker() + EXPLORE_TEMPUS_UNLEASHED)) {
            return;
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " has no eligible planet to explore with _Tempus Unleashed_.");
        } else {
            NewStuffHelper.sendPaginated(
                    player.getCorrectChannel(),
                    message,
                    buttons,
                    List.of(),
                    player.factionButtonChecker() + EXPLORE_TEMPUS_UNLEASHED);
        }
        ButtonHelper.deleteAllButtons(event);
    }

    public static void resolveTempusAfterNonActionCardComponentPurge(Game game, Player player) {
        CommanderUnlockCheckService.checkPlayer(player, "stoneborn");
        if (game.playerHasLeaderUnlockedOrAlliance(player, "stoneborncommanderdormant")
                && game.getStoredValue(tempusUsedKey(player)).isEmpty()) {
            player.gainCommodities(1);
            game.setStoredValue(tempusUsedKey(player), "used");
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " gained 1 commodity with _Tempus Dormant_.");
            return;
        }
        offerTempusUnleashedChoice(game, player);
    }

    public static boolean drawThreeRelicsAndOfferOne(Game game, Player player) {
        if (game.getAllRelics().size() < 3) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " cannot resolve _Meteorime Dormant_ because fewer than 3 relics remain.");
            return false;
        }
        if (!game.getStoredValue(METEORIME_DRAWN_RELICS + player.getFaction()).isEmpty()) {
            return false;
        }

        List<String> relics = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            relics.add(game.drawRelic());
        }
        game.setStoredValue(METEORIME_DRAWN_RELICS + player.getFaction(), String.join(",", relics));
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", choose 1 relic to gain with _Meteorime Dormant_. The rest will be shuffled back into the relic deck.",
                getRelicEmbeds(relics),
                getMeteorimeRelicButtons(player, relics));
        return true;
    }

    @ButtonHandler(CHOOSE_METEORIME_RELIC)
    public static void chooseMeteorimeRelic(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        int relicIndex = getRelicIndex(buttonID.substring(CHOOSE_METEORIME_RELIC.length()));
        List<String> relics = getMeteorimeDrawnRelics(game, player);
        if (relicIndex < 0 || relicIndex >= relics.size()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String relic = relics.remove(relicIndex);
        game.getAllRelics().addAll(relics);
        game.shuffleRelics();
        game.setStoredValue(METEORIME_DRAWN_RELICS + player.getFaction(), "");
        RelicHelper.gainRelicAndNotify(player, event, game, normalizeRelicID(relic), "gained");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getMeteorimeRelicButtons(Player player, List<String> relics) {
        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < relics.size(); i++) {
            var relic = Mapper.getRelic(normalizeRelicID(relics.get(i)));
            if (relic != null) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + CHOOSE_METEORIME_RELIC + i, "Choose " + relic.getName()));
            }
        }
        return buttons;
    }

    private static List<MessageEmbed> getRelicEmbeds(List<String> relics) {
        return relics.stream()
                .map(StonebornLeadersHandler::normalizeRelicID)
                .map(Mapper::getRelic)
                .filter(java.util.Objects::nonNull)
                .map(relic -> relic.getRepresentationEmbed(false, true))
                .toList();
    }

    private static List<String> getMeteorimeDrawnRelics(Game game, Player player) {
        String drawnRelics = game.getStoredValue(METEORIME_DRAWN_RELICS + player.getFaction());
        return drawnRelics.isEmpty() ? new ArrayList<>() : new ArrayList<>(List.of(drawnRelics.split(",")));
    }

    private static String normalizeRelicID(String relicID) {
        return EXTRA_RELIC_SUFFIX_PATTERN.matcher(relicID).replaceFirst("");
    }

    private static int getRelicIndex(String indexText) {
        try {
            return Integer.parseInt(indexText);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @ButtonHandler(PURGE_HAZORUS_FRAGMENT)
    public static void purgeHazorusFragment(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String[] payload = buttonID.substring(PURGE_HAZORUS_FRAGMENT.length()).split("\\|", 3);
        if (payload.length != 3) {
            return;
        }
        Player agentOwner = game.getPlayerFromColorOrFaction(payload[0]);
        if (agentOwner == null
                || !HAZORUS_AGENTS.contains(payload[1])
                || agentOwner.getLeader(payload[1]).filter(Leader::isExhausted).isEmpty()) {
            return;
        }
        if (NewStuffHelper.turnPage(
                event,
                buttonID,
                getHazorusMessage(player),
                () -> getHazorusFragmentButtons(player, agentOwner, payload[1]),
                List.of(),
                player.factionButtonChecker() + PURGE_HAZORUS_FRAGMENT + payload[0] + "|" + payload[1] + "|")) {
            return;
        }
        if (!player.getFragments().contains(payload[2])) {
            return;
        }
        int commoditiesBefore = player.getCommodities();
        ButtonHelperExplore.resolvePurgedFragments(game, player, event, List.of(payload[2]));
        ButtonHelperStats.replenishComms(event, game, player, false);
        if ("stonebornagentunleashed".equals(payload[1])) {
            ButtonHelperStats.gainTGs(
                    event, game, agentOwner, (player.getCommodities() - commoditiesBefore) / 2, false);
        }
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getHazorusFragmentButtons(Player target, Player agentOwner, String agent) {
        return target.getFragments().stream()
                .map(Mapper::getExplore)
                .filter(fragment -> fragment != null)
                .map(fragment -> Buttons.green(
                        target.factionButtonChecker() + PURGE_HAZORUS_FRAGMENT + agentOwner.getFaction() + "|" + agent
                                + "|" + fragment.getAlias(),
                        "Purge " + fragment.getName(),
                        ExploreEmojis.getFragEmoji(fragment.getType())))
                .toList();
    }

    private static String getHazorusMessage(Player target) {
        return target.getRepresentation()
                + ", you may purge 1 relic fragment to replenish your commodities with Hazorus.";
    }

    private static MessageChannel getOutputChannel(Game game, Player player, ButtonInteractionEvent event) {
        return game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel();
    }
}
