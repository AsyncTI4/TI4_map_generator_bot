package ti4.buttons.handlers.edict.resolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.RegexHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Space;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.UnitEmojis;
import ti4.service.regex.RegexService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParseUnitService;

public class TkEnfiladeResolver implements EdictResolver {

    @Getter
    public String edict = "tk-enfilade";

    private static List<Button> tyrantButtons(Player player) {
        String id = player.finChecker() + "enfilade_";
        List<Button> buttons = new ArrayList<>();
        buttons.addAll(placeButtons(player));
        buttons.add(Buttons.red(id + "destroy", "Destroy 1 Structure", "💥"));
        return buttons;
    }

    private static List<Button> placeButtons(Player player) {
        String id = (player != null ? player.finChecker() : "") + "enfilade_";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(id + "sd", "Place 1 Space Dock", UnitEmojis.spacedock));
        buttons.add(Buttons.green(id + "pds", "Place 1 PDS", UnitEmojis.pds));
        return buttons;
    }

    public void handle(ButtonInteractionEvent event, Game game, Player player) {
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), playerPing(player), tyrantButtons(player));

        String addl = "-# Note this happens after the tyrant would choose to destroy a stucture,";
        addl += " so you may choose to wait until they resolve that step";
        MessageHelper.sendMessageToChannelWithButtons(
                game.getMainGameChannel(), gamePing(game, addl), placeButtons(null));
    }

    @ButtonHandler("enfilade_")
    private static void resolveEnfilade(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        boolean primaryResolved = false;
        if (event.getButton().getCustomId().contains("FFCC")) {
            primaryResolved = true;
            ButtonHelper.deleteMessage(event);
        }
        String ident = player.getRepresentationUnfogged();
        switch (buttonID) {
            case "enfilade_sd" -> {
                List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, "sd", "placeOneNDone_skipbuild");
                String message = ident + ", choose the planet you wish to place a Space Dock on:";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
            }
            case "enfilade_pds" -> {
                List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, "pds", "placeOneNDone_skipbuild");
                String message = ident + ", choose the planet you wish to place a PDS on:";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
            }
            case "enfilade_destroy" -> {
                List<Button> buttons = getTargetButtonsForEnfiladeDestroy(game, player);
                String message = ident + ", choose the player whose structure you wish to destroy:";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
                primaryResolved = false;
            }
        }
        if (primaryResolved && game.isFowMode()) {
            String msg = "The tyrant has resolved _Enfilade_, if anyone was waiting on that.";
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg);
        }
    }

    private static List<Button> getTargetButtonsForEnfiladeDestroy(Game game, Player player) {
        Map<String, Integer> visibleTargets = new HashMap<>();
        for (Tile t : game.getTileMap().values()) {
            if (t.hasFog(player) || t.isHomeSystem(game)) continue;
            for (Player p2 : game.getRealPlayersExcludingThis(player)) {
                int amt = t.getUnitHolders().values().stream()
                        .map(uh -> uh.countPlayersUnitsWithModelCondition(player, UnitModel::getIsStructure))
                        .collect(Collectors.summingInt(i -> i));
                visibleTargets.put(p2.getFaction(), amt);
            }
        }

        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayersExcludingThis(player)) {
            int targets = visibleTargets.get(p2.getFaction());
            if (targets == 0) continue;

            String id = player.finChecker() + "enfiladeTarget_" + p2.getFaction();
            String label = "Target " + targets + " " + p2.getFactionNameOrColor() + " structures";
            buttons.add(Buttons.red(id, label, p2.fogSafeEmoji()));
        }
        return buttons;
    }

    @ButtonHandler("enfiladeTarget_")
    private static void enfiladeTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String victimFaction = buttonID.replace("enfiladeTarget_", "");
        Player victim = game.getPlayerFromColorOrFaction(victimFaction);

        List<Button> buttons = new ArrayList<>();
        for (Tile t : game.getTileMap().values()) {
            if (t.hasFog(player) || t.isHomeSystem(game)) continue;

            for (UnitHolder uh : t.getUnitHolders().values()) {
                String partialID = player.finChecker() + "enfiladeDestroy_" + victim.getFaction();
                partialID += "_" + t.getPosition() + "_" + uh.getName() + "_";

                for (UnitKey key : uh.getUnitKeysForPlayer(victim)) {
                    UnitModel model = victim.getUnitFromUnitKey(key);
                    if (!model.getIsStructure()) continue;

                    for (UnitState state : UnitState.values()) {
                        if (uh.getUnitCountForState(key, state) == 0) continue;
                        String stateDescr = state.humanDescr() + (state.equals(UnitState.none) ? "" : " ");

                        String id = partialID + key.getUnitType().getValue() + "_" + state.name();
                        String label = "Destroy " + stateDescr + key.humanReadableName();
                        if (uh instanceof Space) {
                            label += " in tile " + t.getPosition();
                        } else {
                            label += " on " + uh.getRepresentation(game);
                        }
                        buttons.add(Buttons.red(id, label, key.unitEmoji()));
                    }
                }
            }
        }
        String msg = player.getRepresentationUnfogged() + ", choose one of " + victim.fogSafeEmoji()
                + "'s structures to destroy:";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("enfiladeDestroy_")
    private static void enfiladeDestroy(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String rx = "enfiladeDestroy_" + RegexHelper.colorRegex(game) + "_" + RegexHelper.posRegex() + "_"
                + RegexHelper.unitHolderRegex(game, "uh");
        rx += "_" + RegexHelper.unitTypeRegex() + "_" + RegexHelper.unitStateRegex();
        RegexService.runMatcher(rx, buttonID, matcher -> {
            Player target = game.getPlayerFromColorOrFaction(matcher.group("color"));
            UnitType type = Units.findUnitType(matcher.group("unittype"));
            UnitState state = Units.findUnitState(matcher.group("state"));
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            UnitHolder uh = tile.getUnitHolders().get(matcher.group("uh"));

            var unit = ParseUnitService.simpleParsedUnit(target, type, uh, 1);
            DestroyUnitService.destroyUnit(event, tile, game, unit, false, state);

            String msgFormat = "%s destroyed the " + type.getUnitTypeEmoji() + " belonging to %s ";
            if (uh instanceof Space) {
                msgFormat += " in tile " + tile.getPosition();
            } else {
                msgFormat += " on " + uh.getRepresentation(game);
            }

            if (game.isFowMode()) {
                String msg = String.format(msgFormat, player.getRepresentationUnfogged(), target.getRepresentation());
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            }
            String msg = String.format(msgFormat, player.getRepresentationNoPing(), target.getRepresentationUnfogged());
            MessageHelper.sendMessageToChannel(target.getCorrectChannel(), msg);

            ButtonHelper.deleteMessage(event);
        });
    }
}
