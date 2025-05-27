package ti4.buttons.handlers.unitPickers;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;

import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.helpers.RegexHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.regex.RegexService;
import ti4.service.tactical.TacticalActionOutputService;
import ti4.service.tactical.TacticalActionService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

public class TacticalActionButtonHandlers {

    @ButtonHandler("unitTacticalMove")
    @ButtonHandler("unitTacticalRemove")
    public static void newTacticalMoveUnits(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regexSingleUnit = "unitTactical(?<type>Move|Remove)";
        regexSingleUnit += "_" + RegexHelper.posRegex(game);
        regexSingleUnit += "_" + RegexHelper.intRegex("amt");
        regexSingleUnit += "_" + RegexHelper.unitTypeRegex();
        regexSingleUnit += RegexHelper.optional("_" + RegexHelper.unitStateRegex());
        regexSingleUnit += RegexHelper.optional("_" + RegexHelper.planetNameRegex(game, "planet"));
        regexSingleUnit += RegexHelper.optional("_" + "(?<reverse>reverse)");
        if (RegexService.runMatcher(regexSingleUnit, buttonID, matcher -> {
            String moveOrRemove = matcher.group("type");
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            int amt = Integer.parseInt(matcher.group("amt"));
            UnitType typeToMove = Units.findUnitType(matcher.group("unittype"));
            boolean prefersState = matcher.group("state") != null && StringUtils.isNotBlank(matcher.group("state"));
            UnitState state = prefersState ? Units.findUnitState(matcher.group("state")) : UnitState.none;
            String planetName = matcher.group("planet");
            boolean reverse = StringUtils.isNotBlank(matcher.group("reverse"));

            if (!reverse)
                TacticalActionService.moveSingleUnit(event, game, player, tile, planetName, typeToMove, amt, state,
                    moveOrRemove);
            if (reverse)
                TacticalActionService.reverseSingleUnit(event, game, player, tile, planetName, typeToMove, amt, state,
                    moveOrRemove);
        }, x -> {})) {
            return;
        }

        String regexAllCmd = "unitTactical(?<type>Move|Remove)";
        regexAllCmd += "_" + RegexHelper.posRegex(game);
        regexAllCmd += "_" + "(?<cmd>(moveAll|reverseAll|removeAllShips|removeAll))";
        if (RegexService.runMatcher(regexAllCmd, buttonID, matcher -> {
            String moveOrRemove = matcher.group("type");
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            switch (matcher.group("cmd")) {
                case "moveAll", "removeAll" -> TacticalActionService.moveAllFromTile(event, game, player, tile, moveOrRemove);
                case "reverseAll" -> TacticalActionService.reverseTileUnitMovement(event, game, player, tile, moveOrRemove);
                case "removeAllShips" -> TacticalActionService.moveAllShipsFromTile(event, game, player, tile, moveOrRemove);
            }
        }, x -> {})) {
            return;
        }

        // Refresh buttons if there was an error
        String pos = buttonID.split("_")[1];
        Tile t = game.getTileByPosition(pos);
        String moveRemove = buttonID.split("_")[0].replace("unitTactical", "");
        TacticalActionOutputService.refreshButtonsAndMessageForTile(event, game, player, t, moveRemove);
        BotLogger.error("Error matching regex for tactical action: " + buttonID, game, event);
        MessageHelper.sendEphemeralMessageToEventChannel(event, "Encountered error, refreshed buttons.");
    }

    @ButtonHandler("doneWithOneSystem_")
    public static void finishMovingFromOneTile(
        Player player, Game game, ButtonInteractionEvent event,
        String buttonID
    ) {
        TacticalActionOutputService.refreshButtonsAndMessageForChoosingTile(event, game, player);
    }

    @ButtonHandler("resetTacticalMovement")
    public static void resetTacticsMovement(ButtonInteractionEvent event, Player player, Game game) {
        // start over movement
        if (!game.getTacticalActionDisplacement().isEmpty()) {
            TacticalActionService.reverseAllUnitMovement(event, game, player);
            String message = "## All unit movement has been reset.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        }

        String message = "Choose a system to move from, or finalize movement.";
        List<Button> systemButtons = TacticalActionService.getTilesToMoveFrom(player, game, event);
        MessageHelper.sendMessageToEventChannelWithButtons(event, message, systemButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("ChooseDifferentDestination")
    public static void chooseDifferentDestination(ButtonInteractionEvent event, Player player, Game game) {
        // start over movement
        if (!game.getTacticalActionDisplacement().isEmpty()) {
            TacticalActionService.reverseAllUnitMovement(event, game, player);
        }

        String message = "Choosing a different system to activate. Please select the ring of the map that the system you wish to activate is located in.";
        if (!game.isFowMode()) {
            message += " Reminder that a normal 6 player map is 3 rings, with ring 1 being adjacent to Mecatol Rex. The Wormhole Nexus is in the corner.";
        }
        List<Button> ringButtons = ButtonHelper.getPossibleRings(player, game);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ringButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("tacticalMoveFrom_")
    public static void selectTileToMoveFrom(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String pos = buttonID.replace("tacticalMoveFrom_", "");
        Tile tile = game.getTileByPosition(pos);

        String message = TacticalActionOutputService.buildMessageForSingleSystem(game, player, tile);
        List<Button> systemButtons = ButtonHelperTacticalAction.getButtonsForAllUnitsInSystem(player, game, tile,
            "Move");
        MessageHelper.sendMessageToEventChannelWithButtons(event, message, systemButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("concludeMove_")
    public static void concludeTacticalMove(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String rx = "concludeMove_" + RegexHelper.posRegex(game);
        RegexService.runMatcher(rx, buttonID, matcher -> {
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            TacticalActionService.finishMovement(event, game, player, tile);
        });
    }

    @ButtonHandler("spaceUnits_")
    public static void spaceLandedUnits(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "spaceUnits_" + RegexHelper.posRegex(game) + "_" + RegexHelper.intRegex("num")
            + RegexHelper.unitTypeRegex() + "(?<dmg>(damaged)?)_" + RegexHelper.unitHolderRegex(game, "uh");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String pos = matcher.group("pos");
            int amount = Integer.parseInt(matcher.group("num"));
            UnitType type = Units.findUnitType(matcher.group("unittype"));
            boolean damaged = matcher.group("dmg") != null && !matcher.group("dmg").isBlank();
            String planet = matcher.group("uh");

            Tile tile = game.getTileByPosition(pos);
            UnitHolder removeFromHolder = tile.getUnitHolderFromPlanet(planet);
            UnitHolder addToHolder = tile.getSpaceUnitHolder();
            game.setActiveSystem(pos);

            List<RemovedUnit> removed = RemoveUnitService.removeUnit(event, tile, game, player, removeFromHolder, type,
                amount, damaged);
            List<RemovedUnit> toAdd = removed.stream().map(r -> r.onUnitHolder(addToHolder)).toList();
            AddUnitService.addUnits(event, game, toAdd);

            List<Button> systemButtons = TacticalActionService.getLandingTroopsButtons(game, player, tile);

            String planetName = Mapper.getPlanet(planet).getNameNullSafe();
            String undidMsg = player.getFactionEmojiOrColor() + " undid landing of " + amount + " "
                + type.humanReadableName() + " on " + planetName + ".";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), undidMsg);
            event.getMessage().editMessage(event.getMessage().getContentRaw())
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        }, fail -> {
            // Regen buttons
            MessageHelper.sendEphemeralMessageToEventChannel(event,
                "Failed to un-land units, regenerating buttons.\nIf the problem persists please yell at Jazzxhands");
            Tile active = game.getTileByPosition(game.getActiveSystem());
            MessageHelper.editMessageButtons(event, TacticalActionService.getLandingUnitsButtons(game, player, active));
        });
    }

    @ButtonHandler("landUnits_")
    public static void landingUnits(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String regex = "landUnits_" + RegexHelper.posRegex(game) + "_" + RegexHelper.intRegex("num")
            + RegexHelper.unitTypeRegex() + "(?<dmg>(damaged)?)_" + RegexHelper.unitHolderRegex(game, "uh");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String pos = matcher.group("pos");
            int amount = Integer.parseInt(matcher.group("num"));
            UnitType type = Units.findUnitType(matcher.group("unittype"));
            boolean damaged = matcher.group("dmg") != null && !matcher.group("dmg").isBlank();
            String planet = matcher.group("uh");

            Tile tile = game.getTileByPosition(pos);
            UnitHolder removeFromHolder = tile.getSpaceUnitHolder();
            UnitHolder addToHolder = tile.getUnitHolderFromPlanet(planet);
            game.setActiveSystem(pos);

            List<RemovedUnit> removed = RemoveUnitService.removeUnit(event, tile, game, player, removeFromHolder, type,
                amount, damaged);
            List<RemovedUnit> toAdd = removed.stream().map(r -> r.onUnitHolder(addToHolder)).toList();
            AddUnitService.addUnits(event, game, toAdd);

            List<Button> systemButtons = TacticalActionService.getLandingTroopsButtons(game, player, tile);

            String planetName = Mapper.getPlanet(planet).getNameNullSafe();
            String landingMsg = player.fogSafeEmoji() + " landed " + amount + " " + type.humanReadableName() + " on "
                + planetName + ".";
            if (!removed.isEmpty()) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), landingMsg);
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Landing failed for an unknown reason. Regenerated buttons, please ping bothelper if the problem persists.");
            }
            event.getMessage().editMessage(event.getMessage().getContentRaw())
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        }, fail -> {
            // Regen buttons
            MessageHelper.sendEphemeralMessageToEventChannel(event,
                "Failed to land units, regenerating buttons.\nIf the problem persists please yell at Jazzxhands");
            Tile active = game.getTileByPosition(game.getActiveSystem());
            MessageHelper.editMessageButtons(event, TacticalActionService.getLandingUnitsButtons(game, player, active));
        });
    }
}