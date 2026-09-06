package ti4.service.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import ti4.model.UnitModel;

@UtilityClass
public class NekroMonumentService {
    private static final String ASSIMILATOR_M = "nekroMonumentAssimilatorM";
    private static final String ASSIMILATOR_Z = "nekroMonumentAssimilatorZ";

    public static Button getCopyMonumentButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + "nekroMonumentCopy", "Copy Monument", player.getFactionEmoji());
    }

    @ButtonHandler("nekroMonumentCopy")
    public static void chooseCopyMethod(ButtonInteractionEvent event, Game game, Player nekro) {
        if (!game.isMonumentsMode() || !nekro.hasUnit("nekro_monument")) return;
        List<Button> buttons = List.of(
                Buttons.green(nekro.factionButtonChecker() + "nekroMonumentPlace", "Place Token"),
                Buttons.blue(nekro.factionButtonChecker() + "nekroMonumentMove", "Move Token"),
                Buttons.red("declineNekroMonumentCopy", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                nekro.getRepresentationUnfogged()
                        + ", if you just destroyed another player's structure, you may copy their monument.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("declineNekroMonumentCopy")
    public static void declineCopyMonument(ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("nekroMonumentPlace")
    public static void chooseMonumentToPlaceOn(ButtonInteractionEvent event, Game game, Player nekro) {
        if (!game.isMonumentsMode() || !nekro.hasUnit("nekro_monument")) return;
        List<Button> buttons = getMonumentTargetButtons(game, nekro, "nekroMonumentPlaceTarget_");
        if (buttons.isEmpty()) return;
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(), nekro.getRepresentationUnfogged() + ", choose a monument to copy.", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("nekroMonumentPlaceTarget_")
    public static void chooseTokenToPlace(ButtonInteractionEvent event, Game game, Player nekro, String buttonID) {
        if (!game.isMonumentsMode() || !nekro.hasUnit("nekro_monument")) return;
        String faction = buttonID.substring("nekroMonumentPlaceTarget_".length());
        if (getMonumentTarget(game, nekro, faction) == null) return;
        List<Button> buttons = new ArrayList<>();
        if (game.getStoredValue(ASSIMILATOR_M).isEmpty()) {
            buttons.add(
                    Buttons.green(nekro.factionButtonChecker() + "nekroMonumentPlace_M_" + faction, "Place Valefar M"));
        }
        if (hasAvailableZ(game, nekro) && !getMonumentZFactions(game).contains(faction)) {
            buttons.add(
                    Buttons.blue(nekro.factionButtonChecker() + "nekroMonumentPlace_Z_" + faction, "Place Valefar Z"));
        }
        if (buttons.isEmpty()) return;
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                nekro.getRepresentationUnfogged() + ", choose an assimilator token to place.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("nekroMonumentPlace_")
    public static void placeAssimilator(ButtonInteractionEvent event, Game game, Player nekro, String buttonID) {
        if (!game.isMonumentsMode() || !nekro.hasUnit("nekro_monument")) return;
        String payload = buttonID.substring("nekroMonumentPlace_".length());
        int delimiter = payload.indexOf('_');
        if (delimiter < 1) return;
        String token = payload.substring(0, delimiter);
        String faction = payload.substring(delimiter + 1);
        Player target = getMonumentTarget(game, nekro, faction);
        if (target == null || !("M".equals(token) || "Z".equals(token))) return;
        if ("M".equals(token) && !game.getStoredValue(ASSIMILATOR_M).isEmpty()) return;
        if ("Z".equals(token)
                && (!hasAvailableZ(game, nekro) || getMonumentZFactions(game).contains(faction))) return;
        if ("M".equals(token)) {
            game.setStoredValue(ASSIMILATOR_M, faction);
        } else {
            game.setStoredValue(ASSIMILATOR_Z, game.getStoredValue(ASSIMILATOR_Z) + faction + "|");
        }
        sendAssimilatorMessage(nekro, target, "placed Valefar " + token + " on");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("nekroMonumentMove")
    public static void chooseAssimilatorToMove(ButtonInteractionEvent event, Game game, Player nekro) {
        if (!game.isMonumentsMode() || !nekro.hasUnit("nekro_monument")) return;
        List<Button> buttons = new ArrayList<>();
        String mFaction = game.getStoredValue(ASSIMILATOR_M);
        Player mSource = game.getPlayerFromColorOrFaction(mFaction);
        if (mSource != null) {
            buttons.add(Buttons.green(
                    nekro.factionButtonChecker() + "nekroMonumentMoveTarget_M|" + mFaction,
                    "Move Valefar M From " + mSource.getFactionNameOrColor()));
        }
        for (String faction : getMonumentZFactions(game)) {
            Player source = game.getPlayerFromColorOrFaction(faction);
            if (source != null) {
                buttons.add(Buttons.blue(
                        nekro.factionButtonChecker() + "nekroMonumentMoveTarget_ZM|" + faction,
                        "Move Valefar Z From " + source.getFactionNameOrColor()));
            }
        }
        for (String faction : getFlagshipZFactions(game)) {
            Player source = game.getPlayerFromColorOrFaction(faction);
            if (source != null) {
                buttons.add(Buttons.blue(
                        nekro.factionButtonChecker() + "nekroMonumentMoveTarget_ZF|" + faction,
                        "Move Valefar Z From " + source.getFactionNameOrColor() + " Flagship"));
            }
        }
        if (buttons.isEmpty()) return;
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                nekro.getRepresentationUnfogged() + ", choose an assimilator token to move.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("nekroMonumentMoveTarget_")
    public static void chooseMonumentToMoveTo(ButtonInteractionEvent event, Game game, Player nekro, String buttonID) {
        if (!game.isMonumentsMode() || !nekro.hasUnit("nekro_monument")) return;
        String payload = buttonID.substring("nekroMonumentMoveTarget_".length());
        int delimiter = payload.indexOf('|');
        if (delimiter < 1) return;
        String token = payload.substring(0, delimiter);
        String source = payload.substring(delimiter + 1);
        List<Button> buttons =
                getMonumentTargetButtons(game, nekro, "nekroMonumentRelocate_" + token + "|" + source + "|");
        if (buttons.isEmpty()) return;
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(), nekro.getRepresentationUnfogged() + ", choose where to move that token.", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("nekroMonumentRelocate_")
    public static void moveAssimilator(ButtonInteractionEvent event, Game game, Player nekro, String buttonID) {
        if (!game.isMonumentsMode() || !nekro.hasUnit("nekro_monument")) return;
        String[] parts = buttonID.substring("nekroMonumentRelocate_".length()).split("\\|", 3);
        if (parts.length != 3) return;
        String token = parts[0];
        String source = parts[1];
        String targetFaction = parts[2];
        Player target = getMonumentTarget(game, nekro, targetFaction);
        if (target == null) return;
        if ("M".equals(token) && source.equals(game.getStoredValue(ASSIMILATOR_M))) {
            game.setStoredValue(ASSIMILATOR_M, targetFaction);
        } else if ("ZM".equals(token) && getMonumentZFactions(game).contains(source)) {
            game.setStoredValue(
                    ASSIMILATOR_Z,
                    game.getStoredValue(ASSIMILATOR_Z).replaceFirst(source + "\\|", "") + targetFaction + "|");
        } else if ("ZF".equals(token) && getFlagshipZFactions(game).contains(source)) {
            game.setStoredValue("valefarZ", game.getStoredValue("valefarZ").replaceFirst(source + "\\|", ""));
            game.setStoredValue(ASSIMILATOR_Z, game.getStoredValue(ASSIMILATOR_Z) + targetFaction + "|");
        } else {
            return;
        }
        sendAssimilatorMessage(nekro, target, "moved an assimilator token to");
        ButtonHelper.deleteMessage(event);
    }

    public static boolean hasCopiedMonument(Game game, Player player, String monumentId) {
        if (game == null
                || player == null
                || !game.isMonumentsMode()
                || !player.hasUnit("nekro_monument")
                || game.getTileMap().values().stream()
                        .noneMatch(tile -> ButtonHelper.doesPlayerHaveUnitHere("nekro_monument", player, tile))) {
            return false;
        }
        return getCopiedMonuments(game, player).stream().anyMatch(monument -> monumentId.equals(monument.getId()));
    }

    public static boolean hasAssimilatorOnMonument(Game game, Player player) {
        return game != null
                && player != null
                && game.isMonumentsMode()
                && game.getRealPlayers().stream().anyMatch(nekro -> nekro.hasUnit("nekro_monument"))
                && (player.getFaction().equals(game.getStoredValue(ASSIMILATOR_M))
                        || getMonumentZFactions(game).contains(player.getFaction()));
    }

    public static List<UnitModel> getCopiedMonuments(Game game, Player player) {
        if (game == null || player == null || !game.isMonumentsMode() || !player.hasUnit("nekro_monument"))
            return List.of();
        List<UnitModel> monuments = new ArrayList<>();
        List<String> factions = new ArrayList<>(getMonumentZFactions(game));
        if (!game.getStoredValue(ASSIMILATOR_M).isEmpty()) factions.add(game.getStoredValue(ASSIMILATOR_M));
        for (String faction : factions) {
            Player target = game.getPlayerFromColorOrFaction(faction);
            UnitModel monument = target == null ? null : target.getUnitByBaseType("monument");
            if (monument != null
                    && monuments.stream().noneMatch(existing -> existing.getId().equals(monument.getId()))) {
                monuments.add(Mapper.getUnit(monument.getId()));
            }
        }
        return monuments;
    }

    private static List<Button> getMonumentTargetButtons(Game game, Player nekro, String prefix) {
        if (game == null || nekro == null || !game.isMonumentsMode() || !nekro.hasUnit("nekro_monument")) {
            return List.of();
        }
        return game.getRealPlayersExcludingThis(nekro).stream()
                .filter(target -> target.getUnitByBaseType("monument") != null)
                .map(target -> Buttons.green(
                        nekro.factionButtonChecker() + prefix + target.getFaction(),
                        "Copy " + target.getFactionNameOrColor() + " Monument",
                        target.getFactionEmojiOrColor()))
                .toList();
    }

    private static Player getMonumentTarget(Game game, Player nekro, String faction) {
        Player target = game.getPlayerFromColorOrFaction(faction);
        if (!game.isMonumentsMode()
                || !nekro.hasUnit("nekro_monument")
                || target == null
                || target == nekro
                || target.getUnitByBaseType("monument") == null) return null;
        return target;
    }

    private static List<String> getMonumentZFactions(Game game) {
        return Arrays.stream(game.getStoredValue(ASSIMILATOR_Z).split("\\|"))
                .filter(faction -> !faction.isEmpty())
                .toList();
    }

    private static List<String> getFlagshipZFactions(Game game) {
        return Arrays.stream(game.getStoredValue("valefarZ").split("\\|"))
                .filter(faction -> !faction.isEmpty())
                .toList();
    }

    private static boolean hasAvailableZ(Game game, Player nekro) {
        int maxTokens = game.isMonumentsMode() && nekro.hasUnit("nekro_monument")
                ? 7
                : game.getRealPlayersExcludingThis(nekro).size();
        return getFlagshipZFactions(game).size() + getMonumentZFactions(game).size() < maxTokens;
    }

    private static void sendAssimilatorMessage(Player nekro, Player target, String action) {
        UnitModel monument = target.getUnitByBaseType("monument");
        String ability = monument.getAbility().orElse("");
        MessageHelper.sendMessageToChannel(
                nekro.getCorrectChannel(),
                nekro.getRepresentationUnfogged() + " " + action + " " + target.getRepresentationNoPing() + "'s _"
                        + monument.getName() + "_." + (ability.isEmpty() ? "" : "\n> " + ability));
    }
}
