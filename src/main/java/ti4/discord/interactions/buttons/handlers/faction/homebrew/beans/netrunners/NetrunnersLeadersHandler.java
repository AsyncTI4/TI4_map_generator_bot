package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

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
import ti4.helpers.Constants;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.service.tech.PlayerTechService;

@UtilityClass
public class NetrunnersLeadersHandler {
    private static final String HERO_ACTIVE = "netrunnersHeroActive_";
    private static final String HERO_TARGET = "netrunnersHeroTarget_";
    private static final String HERO_COPY = "netrunnersHeroCopy_";
    private static final String HERO_REMOVE = "netrunnersHeroRemove_";
    private static final String HERO_COPY_TECH = "netrunnersHeroCopyTech_";
    private static final String HERO_REMOVE_TECH = "netrunnersHeroRemoveTech_";
    private static final String HERO_COPY_NAV = "netrunnersHeroCopyPage_";
    private static final String HERO_REMOVE_NAV = "netrunnersHeroRemovePage_";
    private static final String HERO_DONE = "netrunnersHeroDone";

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

    public static Button techButton(String buttonId, TechnologyModel technology) {
        String emoji = technology.getCondensedReqsEmojis(true);
        return switch (technology.getFirstType()) {
            case PROPULSION -> Buttons.blue(buttonId, technology.getName(), emoji);
            case BIOTIC -> Buttons.green(buttonId, technology.getName(), emoji);
            case WARFARE -> Buttons.red(buttonId, technology.getName(), emoji);
            default -> Buttons.gray(buttonId, technology.getName(), emoji);
        };
    }
}
