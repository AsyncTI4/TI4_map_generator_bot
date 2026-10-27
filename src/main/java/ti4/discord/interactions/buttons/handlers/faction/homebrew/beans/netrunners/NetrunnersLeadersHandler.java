package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.tech.PlayerTechService;

@UtilityClass
public class NetrunnersLeadersHandler {
    private static final String AGENT = "netrunnersagent";
    private static final String USE_ON_OTHER = "useNetrunnersAgentOnOther";
    private static final String SELECT_TARGET = "netrunnersAgentSelectTarget_";
    private static final String SELECT_SELF_TECH = "netrunnersAgentSelectSelfTech_";
    private static final String SELECT_TARGET_TECH = "netrunnersAgentSelectTargetTech_";
    private static final String GAIN_TECH = "netrunnersAgentGainTech_";
    private static final String SOURCE_TECH_NAV = "netrunnersAgentSourcePage_";
    private static final String GAIN_TECH_NAV = "netrunnersAgentGainPage_";
    private static final String HERO_ACTIVE = "netrunnersHeroActive_";
    private static final String HERO_TARGET = "netrunnersHeroTarget_";
    private static final String HERO_COPY = "netrunnersHeroCopy_";
    private static final String HERO_REMOVE = "netrunnersHeroRemove_";
    private static final String HERO_COPY_TECH = "netrunnersHeroCopyTech_";
    private static final String HERO_REMOVE_TECH = "netrunnersHeroRemoveTech_";
    private static final String HERO_COPY_NAV = "netrunnersHeroCopyPage_";
    private static final String HERO_REMOVE_NAV = "netrunnersHeroRemovePage_";
    private static final String HERO_DONE = "netrunnersHeroDone";

    public static Button getNetrunnersAgentCardsInfoButton(Player player) {
        return Buttons.gray(player.factionButtonChecker() + USE_ON_OTHER, "Use Zor No-ahn", FactionEmojis.netrunners);
    }

    public static void startNetrunnersHero(Game game, Player player) {
        List<Button> buttons = game.getRealPlayers().stream()
                .filter(target -> player.getDebtTokenCount(target.getColor(), Constants.NETRUNNERS_DEBT_POOL) > 0)
                .map(target -> Buttons.gray(
                        player.factionButtonChecker() + HERO_TARGET + target.getFaction(),
                        target.getFactionNameOrColor(),
                        target.getFactionEmojiOrColor()))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "There are no Hacker Man pool control tokens to resolve.");
            return;
        }
        game.setStoredValue(HERO_ACTIVE + player.getFaction(), "active");
        List<Button> buttonsWithDone = new java.util.ArrayList<>(buttons);
        buttonsWithDone.add(Buttons.red(player.factionButtonChecker() + HERO_DONE, "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", resolve each Hacker Man pool control token with _Power Surge - Network Overload_.",
                buttonsWithDone);
    }

    @ButtonHandler(HERO_TARGET)
    public static void resolveNetrunnersHeroTarget(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(HERO_TARGET.length()));
        if (!isNetrunnersHeroActive(game, player)
                || target == null
                || player.getDebtTokenCount(target.getColor(), Constants.NETRUNNERS_DEBT_POOL) < 1) {
            return;
        }
        List<Button> buttons = new java.util.ArrayList<>();
        if (!getCopyableTechs(player, target).isEmpty()) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + HERO_COPY + target.getFaction(), "Copy a Technology"));
        }
        if (!target.getTechs().isEmpty()) {
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + HERO_REMOVE + target.getFaction(), "Remove a Technology"));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), target.getRepresentationNoPing() + " has no technology to resolve.");
            return;
        }
        player.removeDebtTokens(target.getColor(), 1, Constants.NETRUNNERS_DEBT_POOL);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose how to resolve " + target.getFactionEmojiOrColor()
                        + "'s control token.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(HERO_COPY)
    public static void chooseNetrunnersHeroCopy(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(HERO_COPY.length()));
        if (!isNetrunnersHeroActive(game, player)
                || target == null
                || getCopyableTechs(player, target).isEmpty()) {
            return;
        }
        NewStuffHelper.sendPaginated(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose a technology to copy from "
                        + target.getFactionEmojiOrColor() + ".",
                getCopyTechButtons(player, target),
                List.of(),
                player.factionButtonChecker() + HERO_COPY_NAV + target.getFaction() + "|");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(HERO_REMOVE)
    public static void chooseNetrunnersHeroRemove(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(HERO_REMOVE.length()));
        if (!isNetrunnersHeroActive(game, player)
                || target == null
                || target.getTechs().isEmpty()) {
            return;
        }
        NewStuffHelper.sendPaginated(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose a technology for " + target.getFactionEmojiOrColor()
                        + " to return to their technology deck.",
                getRemoveTechButtons(player, target),
                List.of(),
                player.factionButtonChecker() + HERO_REMOVE_NAV + target.getFaction() + "|");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(HERO_COPY_TECH)
    public static void copyNetrunnersHeroTech(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(HERO_COPY_TECH.length()).split("\\|", 2);
        Player target = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        String tech = payload.length == 2 ? payload[1] : null;
        if (!isNetrunnersHeroActive(game, player)
                || target == null
                || tech == null
                || getCopyableTechs(player, target).stream()
                        .noneMatch(model -> model.getAlias().equals(tech))) {
            return;
        }
        PlayerTechService.addTech(event, game, player, tech);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(HERO_REMOVE_TECH)
    public static void removeNetrunnersHeroTech(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(HERO_REMOVE_TECH.length()).split("\\|", 2);
        Player target = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        String tech = payload.length == 2 ? payload[1] : null;
        TechnologyModel model = tech == null ? null : Mapper.getTech(tech);
        if (!isNetrunnersHeroActive(game, player) || target == null || model == null || !target.hasTech(tech)) {
            return;
        }
        target.removeTech(tech);
        MessageHelper.sendMessageToChannel(
                target.getCorrectChannel(),
                target.getRepresentationNoPing() + " returned " + model.getNameRepresentation()
                        + " to their technology deck due to _Power Surge - Network Overload_.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(HERO_COPY_NAV)
    public static void turnNetrunnersHeroCopyPage(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = getHeroTargetFromNavButton(game, buttonID, HERO_COPY_NAV);
        if (!isNetrunnersHeroActive(game, player) || target == null) {
            return;
        }
        NewStuffHelper.turnPage(
                event,
                buttonID,
                player.getRepresentationNoPing() + ", choose a technology to copy from "
                        + target.getFactionEmojiOrColor() + ".",
                () -> getCopyTechButtons(player, target),
                List.of(),
                player.factionButtonChecker() + HERO_COPY_NAV + target.getFaction() + "|");
    }

    @ButtonHandler(HERO_REMOVE_NAV)
    public static void turnNetrunnersHeroRemovePage(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = getHeroTargetFromNavButton(game, buttonID, HERO_REMOVE_NAV);
        if (!isNetrunnersHeroActive(game, player) || target == null) {
            return;
        }
        NewStuffHelper.turnPage(
                event,
                buttonID,
                player.getRepresentationNoPing() + ", choose a technology for " + target.getFactionEmojiOrColor()
                        + " to return to their technology deck.",
                () -> getRemoveTechButtons(player, target),
                List.of(),
                player.factionButtonChecker() + HERO_REMOVE_NAV + target.getFaction() + "|");
    }

    @ButtonHandler(HERO_DONE)
    public static void finishNetrunnersHero(ButtonInteractionEvent event, Game game, Player player) {
        game.removeStoredValue(HERO_ACTIVE + player.getFaction());
        ButtonHelper.deleteMessage(event);
    }

    public static void offerZorNoAhnForOwner(
            GenericInteractionCreateEvent event, Game game, Player player, String gainedTech) {
        if (!player.hasUnexhaustedLeader(AGENT)
                || !isEligibleSourceTechnology(player, gainedTech)
                || Mapper.getTech(gainedTech) == null) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", you may exhaust Zor No-ahn after gaining "
                        + Mapper.getTech(gainedTech).getNameRepresentation() + ".",
                List.of(
                        Buttons.gray(
                                player.factionButtonChecker() + SELECT_SELF_TECH + gainedTech,
                                "Use Zor No-ahn",
                                FactionEmojis.netrunners),
                        Buttons.red("deleteButtons", "Decline")));
    }

    @ButtonHandler(USE_ON_OTHER)
    public static void useZorNoAhnOnOther(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasUnexhaustedLeader(AGENT)) {
            return;
        }
        List<Button> buttons = game.getRealPlayersExcludingThis(player).stream()
                .filter(target -> target.getTechs().stream().anyMatch(tech -> isEligibleSourceTechnology(target, tech)))
                .map(target -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_TARGET + target.getFaction(), target.getUserName()))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "There are no eligible players to target.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose a player for Zor No-ahn.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(SELECT_TARGET)
    public static void selectZorNoAhnTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasUnexhaustedLeader(AGENT)) {
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_TARGET.length()));
        if (target == null
                || target == player
                || target.getTechs().stream().noneMatch(tech -> isEligibleSourceTechnology(target, tech))) {
            return;
        }
        sendSourceTechButtons(player, target);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_SELF_TECH)
    public static void selectZorNoAhnSelfTech(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String sourceTech = buttonID.substring(SELECT_SELF_TECH.length());
        if (!player.hasUnexhaustedLeader(AGENT)
                || !isEligibleSourceTechnology(player, sourceTech)
                || !player.hasTech(sourceTech)) {
            return;
        }
        sendGainTechButtons(event, game, player, player, sourceTech);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_TARGET_TECH)
    public static void selectZorNoAhnTargetTech(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SELECT_TARGET_TECH.length()).split("\\|", 2);
        Player agentOwner = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        String sourceTech = payload.length == 2 ? payload[1] : null;
        if (agentOwner == null
                || !agentOwner.hasUnexhaustedLeader(AGENT)
                || sourceTech == null
                || !isEligibleSourceTechnology(player, sourceTech)
                || !player.hasTech(sourceTech)) {
            return;
        }
        sendGainTechButtons(event, game, agentOwner, player, sourceTech);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SOURCE_TECH_NAV)
    public static void turnZorNoAhnSourceTechPage(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String agentFaction = buttonID.substring(SOURCE_TECH_NAV.length()).replaceFirst("\\|page\\d+$", "");
        Player agentOwner = game.getPlayerFromColorOrFaction(agentFaction);
        if (agentOwner == null || !agentOwner.hasUnexhaustedLeader(AGENT)) {
            return;
        }
        NewStuffHelper.turnPage(
                event,
                buttonID,
                sourceTechMessage(player),
                () -> getSourceTechButtons(agentOwner, player),
                List.of(),
                player.factionButtonChecker() + SOURCE_TECH_NAV + agentFaction + "|");
    }

    @ButtonHandler(GAIN_TECH)
    public static void gainZorNoAhnTech(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(GAIN_TECH.length()).split("\\|", 3);
        Player agentOwner = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        String sourceTech = payload.length == 3 ? payload[1] : null;
        String gainedTech = payload.length == 3 ? payload[2] : null;
        if (agentOwner == null
                || sourceTech == null
                || gainedTech == null
                || !agentOwner.hasUnexhaustedLeader(AGENT)
                || !isEligibleSourceTechnology(player, sourceTech)
                || !player.hasTech(sourceTech)
                || !isEligibleGain(game, player, sourceTech, gainedTech)) {
            return;
        }
        ExhaustLeaderService.exhaustLeader(game, agentOwner, agentOwner.unsafeGetLeader(AGENT));
        player.removeTech(sourceTech);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " returned "
                        + Mapper.getTech(sourceTech).getNameRepresentation()
                        + " to the technology deck due to Zor No-ahn.");
        PlayerTechService.addTech(event, game, player, gainedTech);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(GAIN_TECH_NAV)
    public static void turnZorNoAhnGainTechPage(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(GAIN_TECH_NAV.length()).split("\\|", 3);
        Player agentOwner = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        String sourceTech = payload.length == 3 ? payload[1] : null;
        if (agentOwner == null
                || sourceTech == null
                || !agentOwner.hasUnexhaustedLeader(AGENT)
                || !isEligibleSourceTechnology(player, sourceTech)
                || !player.hasTech(sourceTech)) {
            return;
        }
        NewStuffHelper.turnPage(
                event,
                buttonID,
                gainTechMessage(player),
                () -> getGainTechButtons(game, agentOwner, player, sourceTech),
                List.of(),
                player.factionButtonChecker() + GAIN_TECH_NAV + agentOwner.getFaction() + "|" + sourceTech + "|");
    }

    private static void sendSourceTechButtons(Player agentOwner, Player target) {
        List<Button> buttons = getSourceTechButtons(agentOwner, target);
        NewStuffHelper.sendPaginated(
                target.getCardsInfoThread(),
                sourceTechMessage(target),
                buttons,
                List.of(),
                target.factionButtonChecker() + SOURCE_TECH_NAV + agentOwner.getFaction() + "|");
    }

    private static void sendGainTechButtons(
            ButtonInteractionEvent event, Game game, Player agentOwner, Player target, String sourceTech) {
        List<TechnologyModel> eligibleTechs = getEligibleGains(game, target, sourceTech);
        if (eligibleTechs.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "There is no eligible technology for Zor No-ahn to grant.");
            return;
        }
        List<Button> buttons = getGainTechButtons(game, agentOwner, target, sourceTech);
        NewStuffHelper.sendPaginated(
                target.getCorrectChannel(),
                gainTechMessage(target),
                buttons,
                List.of(),
                target.factionButtonChecker() + GAIN_TECH_NAV + agentOwner.getFaction() + "|" + sourceTech + "|");
    }

    private static List<Button> getSourceTechButtons(Player agentOwner, Player target) {
        return target.getTechs().stream()
                .filter(tech -> isEligibleSourceTechnology(target, tech))
                .map(Mapper::getTech)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(TechnologyModel::getName))
                .map(tech -> techButton(
                        target.factionButtonChecker() + SELECT_TARGET_TECH + agentOwner.getFaction() + "|"
                                + tech.getAlias(),
                        tech))
                .toList();
    }

    private static List<Button> getGainTechButtons(Game game, Player agentOwner, Player target, String sourceTech) {
        return getEligibleGains(game, target, sourceTech).stream()
                .map(tech -> techButton(
                        target.factionButtonChecker() + GAIN_TECH + agentOwner.getFaction() + "|" + sourceTech + "|"
                                + tech.getAlias(),
                        tech))
                .toList();
    }

    private static String sourceTechMessage(Player target) {
        return target.getRepresentationNoPing()
                + ", choose the technology Zor No-ahn will return to the technology deck.";
    }

    private static String gainTechMessage(Player target) {
        return target.getRepresentationNoPing() + ", choose the technology to gain with Zor No-ahn.";
    }

    private static boolean isNetrunnersHeroActive(Game game, Player player) {
        return "active".equals(game.getStoredValue(HERO_ACTIVE + player.getFaction()));
    }

    private static Player getHeroTargetFromNavButton(Game game, String buttonID, String prefix) {
        String targetFaction = buttonID.substring(prefix.length()).replaceFirst("\\|page\\d+$", "");
        return game.getPlayerFromColorOrFaction(targetFaction);
    }

    private static List<TechnologyModel> getCopyableTechs(Player player, Player target) {
        return target.getTechs().stream()
                .map(Mapper::getTech)
                .filter(java.util.Objects::nonNull)
                .filter(technology -> !player.hasTech(technology.getAlias()))
                .sorted(Comparator.comparing(TechnologyModel::getName))
                .toList();
    }

    private static List<Button> getCopyTechButtons(Player player, Player target) {
        return getCopyableTechs(player, target).stream()
                .map(technology -> techButton(
                        player.factionButtonChecker() + HERO_COPY_TECH + target.getFaction() + "|"
                                + technology.getAlias(),
                        technology))
                .toList();
    }

    private static List<Button> getRemoveTechButtons(Player player, Player target) {
        return target.getTechs().stream()
                .map(Mapper::getTech)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(TechnologyModel::getName))
                .map(technology -> techButton(
                        player.factionButtonChecker() + HERO_REMOVE_TECH + target.getFaction() + "|"
                                + technology.getAlias(),
                        technology))
                .toList();
    }

    private static Button techButton(String buttonId, TechnologyModel technology) {
        String emoji = technology.getCondensedReqsEmojis(true);
        return switch (technology.getFirstType()) {
            case PROPULSION -> Buttons.blue(buttonId, technology.getName(), emoji);
            case BIOTIC -> Buttons.green(buttonId, technology.getName(), emoji);
            case WARFARE -> Buttons.red(buttonId, technology.getName(), emoji);
            default -> Buttons.gray(buttonId, technology.getName(), emoji);
        };
    }

    private static List<TechnologyModel> getEligibleGains(Game game, Player target, String sourceTech) {
        TechnologyModel source = Mapper.getTech(sourceTech);
        if (source == null) {
            return List.of();
        }
        return game.getTechnologyDeck().stream()
                .map(Mapper::getTech)
                .filter(java.util.Objects::nonNull)
                .filter(tech ->
                        tech.getFaction().map(target.getFaction()::equals).orElse(true))
                .filter(tech -> !target.hasTech(tech.getAlias()))
                .filter(tech -> tech.getRequirements().orElse("").length()
                        == source.getRequirements().orElse("").length())
                .filter(tech -> Collections.disjoint(tech.getTypes(), source.getTypes()))
                .sorted(Comparator.comparing(TechnologyModel::getName))
                .toList();
    }

    private static boolean isEligibleGain(Game game, Player target, String sourceTech, String gainedTech) {
        return getEligibleGains(game, target, sourceTech).stream()
                .anyMatch(technology -> technology.getAlias().equals(gainedTech));
    }

    private static boolean isEligibleSourceTechnology(Player player, String tech) {
        TechnologyModel technology = Mapper.getTech(tech);
        return technology != null
                && technology.getFaction().map(player.getFaction()::equals).orElse(true);
    }
}
