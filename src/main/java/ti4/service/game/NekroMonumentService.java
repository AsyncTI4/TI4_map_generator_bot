package ti4.service.game;

import java.util.ArrayList;
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
    public static void chooseMonumentToCopy(ButtonInteractionEvent event, Game game, Player nekro) {
        if (!game.isMonumentsMode() || !nekro.hasUnit("nekro_monument")) {
            return;
        }

        List<Button> buttons = game.getRealPlayersExcludingThis(nekro).stream()
                .filter(target -> target.getUnitByBaseType("monument") != null)
                .map(target -> Buttons.green(
                        nekro.factionButtonChecker() + "nekroMonumentTarget_" + target.getFaction(),
                        "Copy " + target.getFactionNameOrColor() + " Monument",
                        target.getFactionEmojiOrColor()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (buttons.isEmpty()) {
            return;
        }
        buttons.add(Buttons.red("declineNekroMonumentCopy", "Decline"));
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

    @ButtonHandler("nekroMonumentTarget_")
    public static void chooseAssimilatorToken(ButtonInteractionEvent event, Game game, Player nekro, String buttonID) {
        String faction = buttonID.substring("nekroMonumentTarget_".length());
        Player target = game.getPlayerFromColorOrFaction(faction);
        if (!nekro.hasUnit("nekro_monument")
                || target == null
                || target == nekro
                || target.getUnitByBaseType("monument") == null) {
            return;
        }

        List<Button> buttons = new java.util.ArrayList<>();
        boolean movingM = !game.getStoredValue(ASSIMILATOR_M).isEmpty();
        buttons.add(Buttons.green(
                nekro.factionButtonChecker() + "nekroMonumentAssimilate_M_" + faction,
                movingM ? "Move Valefar M" : "Place Valefar M",
                nekro.getFactionEmoji()));
        boolean movingZ = !game.getStoredValue(ASSIMILATOR_Z).isEmpty();
        if (nekro.hasUnlockedBreakthrough("nekrobt") && (hasAvailableZ(game, nekro) || movingZ)) {
            buttons.add(Buttons.blue(
                    nekro.factionButtonChecker() + "nekroMonumentAssimilate_Z_" + faction,
                    movingZ ? "Move Valefar Z" : "Place Valefar Z",
                    nekro.getFactionEmoji()));
        } else if (nekro.hasUnlockedBreakthrough("nekrobt")
                && !game.getStoredValue("valefarZ").isEmpty()) {
            buttons.add(Buttons.blue(
                    nekro.factionButtonChecker() + "nekroMonumentMoveZ_" + faction,
                    "Move Valefar Z",
                    nekro.getFactionEmoji()));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(), nekro.getRepresentationUnfogged() + ", choose an assimilator token.", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("nekroMonumentAssimilate_")
    public static void resolveAssimilatorPlacement(
            ButtonInteractionEvent event, Game game, Player nekro, String buttonID) {
        String payload = buttonID.substring("nekroMonumentAssimilate_".length());
        int delimiter = payload.indexOf('_');
        if (delimiter < 1) {
            return;
        }
        String token = payload.substring(0, delimiter);
        String faction = payload.substring(delimiter + 1);
        Player target = game.getPlayerFromColorOrFaction(faction);
        if (!nekro.hasUnit("nekro_monument")
                || target == null
                || target == nekro
                || target.getUnitByBaseType("monument") == null
                || !("M".equals(token) || "Z".equals(token))) {
            return;
        }
        if ("Z".equals(token)
                && (!nekro.hasUnlockedBreakthrough("nekrobt")
                        || (!hasAvailableZ(game, nekro)
                                && game.getStoredValue(ASSIMILATOR_Z).isEmpty()))) {
            return;
        }

        game.setStoredValue("M".equals(token) ? ASSIMILATOR_M : ASSIMILATOR_Z, faction);
        UnitModel monument = target.getUnitByBaseType("monument");
        String text = monument == null ? "" : monument.getAbility().orElse("");
        MessageHelper.sendMessageToChannel(
                nekro.getCorrectChannel(),
                nekro.getRepresentationUnfogged() + " placed Valefar " + token + " on "
                        + target.getRepresentationNoPing() + "'s _"
                        + (monument == null ? "monument" : monument.getName()) + "_."
                        + (text.isEmpty() ? "" : "\n> " + text));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("nekroMonumentMoveZ_")
    public static void chooseValefarZToMove(ButtonInteractionEvent event, Game game, Player nekro, String buttonID) {
        String targetFaction = buttonID.substring("nekroMonumentMoveZ_".length());
        Player target = game.getPlayerFromColorOrFaction(targetFaction);
        if (!nekro.hasUnit("nekro_monument")
                || target == null
                || !nekro.hasUnlockedBreakthrough("nekrobt")
                || target.getUnitByBaseType("monument") == null) {
            return;
        }

        List<Button> buttons = java.util.Arrays.stream(
                        game.getStoredValue("valefarZ").split("\\|"))
                .filter(faction -> !faction.isEmpty())
                .map(game::getPlayerFromColorOrFaction)
                .filter(java.util.Objects::nonNull)
                .map(source -> Buttons.blue(
                        nekro.factionButtonChecker() + "nekroMonumentMoveZFrom_" + source.getFaction() + "|"
                                + targetFaction,
                        "Move Z From " + source.getFactionNameOrColor(),
                        source.getFactionEmojiOrColor()))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                nekro.getRepresentationUnfogged() + ", choose the Valefar Z token to move.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("nekroMonumentMoveZFrom_")
    public static void moveValefarZToMonument(ButtonInteractionEvent event, Game game, Player nekro, String buttonID) {
        String payload = buttonID.substring("nekroMonumentMoveZFrom_".length());
        int delimiter = payload.indexOf('|');
        if (delimiter < 1) {
            return;
        }
        String sourceFaction = payload.substring(0, delimiter);
        String targetFaction = payload.substring(delimiter + 1);
        Player target = game.getPlayerFromColorOrFaction(targetFaction);
        if (!nekro.hasUnit("nekro_monument")
                || target == null
                || !nekro.hasUnlockedBreakthrough("nekrobt")
                || target.getUnitByBaseType("monument") == null
                || !game.getStoredValue("valefarZ").contains(sourceFaction + "|")) {
            return;
        }

        game.setStoredValue("valefarZ", game.getStoredValue("valefarZ").replace(sourceFaction + "|", ""));
        game.setStoredValue(ASSIMILATOR_Z, targetFaction);
        UnitModel monument = target.getUnitByBaseType("monument");
        MessageHelper.sendMessageToChannel(
                nekro.getCorrectChannel(),
                nekro.getRepresentationUnfogged() + " moved Valefar Z from " + sourceFaction + "'s flagship to "
                        + target.getRepresentationNoPing() + "'s _"
                        + (monument == null ? "monument" : monument.getName()) + "_.");
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
        return monumentId.equals(getCopiedMonumentId(game, ASSIMILATOR_M))
                || monumentId.equals(getCopiedMonumentId(game, ASSIMILATOR_Z));
    }

    public static boolean hasAssimilatorOnMonument(Game game, Player player) {
        if (game == null || player == null) {
            return false;
        }
        return player.getFaction().equals(game.getStoredValue(ASSIMILATOR_M))
                || player.getFaction().equals(game.getStoredValue(ASSIMILATOR_Z));
    }

    public static List<UnitModel> getCopiedMonuments(Game game, Player player) {
        if (game == null || player == null || !player.hasUnit("nekro_monument")) {
            return List.of();
        }
        List<UnitModel> monuments = new ArrayList<>();
        for (String token : List.of(ASSIMILATOR_M, ASSIMILATOR_Z)) {
            UnitModel monument = Mapper.getUnit(getCopiedMonumentId(game, token));
            if (monument != null
                    && monuments.stream().noneMatch(existing -> existing.getId().equals(monument.getId()))) {
                monuments.add(monument);
            }
        }
        return monuments;
    }

    private static String getCopiedMonumentId(Game game, String key) {
        Player target = game.getPlayerFromColorOrFaction(game.getStoredValue(key));
        UnitModel monument = target == null ? null : target.getUnitByBaseType("monument");
        return monument == null ? "" : monument.getId();
    }

    private static boolean hasAvailableZ(Game game, Player nekro) {
        int usedOnFlagships =
                (int) java.util.Arrays.stream(game.getStoredValue("valefarZ").split("\\|"))
                        .filter(faction -> !faction.isEmpty())
                        .count();
        int usedOnMonument = game.getStoredValue(ASSIMILATOR_Z).isEmpty() ? 0 : 1;
        return usedOnFlagships + usedOnMonument
                < game.getRealPlayersExcludingThis(nekro).size();
    }
}
