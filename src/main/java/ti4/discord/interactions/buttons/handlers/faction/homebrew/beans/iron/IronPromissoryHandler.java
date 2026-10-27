package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.iron;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class IronPromissoryHandler {
    private static final String RETROFITTING = "bepniron";
    private static final String RETROFITTING_STATE = "ironRetrofittingHolder";
    private static final String SELECT_SOURCE = "ironRetrofittingSource_";
    private static final String SELECT_REPLACEMENT = "ironRetrofittingReplacement_";
    private static final String RESOLVE_RETROFITTING = "ironResolveRetrofitting_";

    public static void sendRetrofittingCombatButton(
            Game game, Player holder, Player opponent, Tile tile, boolean spaceCombat) {
        Player owner = game.getPNOwner(RETROFITTING);
        if (!spaceCombat
                || owner == null
                || owner == holder
                || owner == opponent
                || !holder.hasPlayablePromissoryInHand(RETROFITTING)) {
            return;
        }

        MessageHelper.sendMessageToChannelWithButton(
                holder.getCardsInfoThread(),
                holder.getRepresentationNoPing() + ", at the start of this space combat, you may use _Retrofitting_.",
                Buttons.gray(
                        holder.factionButtonChecker() + "resolvePNPlay_" + RETROFITTING,
                        "Use Retrofitting",
                        FactionEmojis.iron));
    }

    public static void resolveRetrofittingPlay(ButtonInteractionEvent event, Game game, Player player) {
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (!player.getPromissoryNotesInPlayArea().contains(RETROFITTING)) {
            return;
        }
        String tilePosition =
                combat != null && combat.tilePosition() != null ? combat.tilePosition() : game.getCurrentActiveSystem();
        Tile tile = game.getTileByPosition(tilePosition);
        if (tile == null) return;
        sendRetrofittingSourceButtons(event, game, player, tile);
    }

    @ButtonHandler(SELECT_SOURCE)
    public static void selectRetrofittingSource(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(SELECT_SOURCE.length()));
        if (tile == null) {
            return;
        }
        sendRetrofittingSourceButtons(event, game, player, tile);
        ButtonHelper.deleteTheOneButton(event);
    }

    private static void sendRetrofittingSourceButtons(
            ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        for (UnitKey unitKey : tile.getSpaceUnitHolder().getUnitKeysForPlayer(player)) {
            UnitModel unit = player.getUnitFromUnitKey(unitKey);
            if (unit == null || !unit.isNonFighterShip()) {
                continue;
            }
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + SELECT_REPLACEMENT + tile.getPosition() + "|" + unit.getAsyncId(),
                    "Replace " + unit.getName(),
                    unit.getUnitEmoji()));
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), player.getRepresentationNoPing() + " has no eligible ship to replace.");
            return;
        }

        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose the ship to replace with _Retrofitting_.",
                buttons);
    }

    @ButtonHandler(SELECT_REPLACEMENT)
    public static void selectRetrofittingReplacement(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SELECT_REPLACEMENT.length()).split("\\|", 2);
        if (payload.length != 2) {
            return;
        }

        Tile tile = game.getTileByPosition(payload[0]);
        UnitModel sourceShip = player.getUnitFromAsyncID(payload[1]);
        if (tile == null || sourceShip == null) {
            return;
        }

        List<Button> buttons = new ArrayList<>(player.getUnitModels().stream()
                .filter(UnitModel::isNonFighterShip)
                .filter(unit -> Float.compare(unit.getCost(), sourceShip.getCost() + 1) == 0)
                .sorted(Comparator.comparing(UnitModel::getName))
                .map(unit -> Buttons.green(
                        player.factionButtonChecker()
                                + RESOLVE_RETROFITTING
                                + tile.getPosition()
                                + "|"
                                + sourceShip.getAsyncId()
                                + "|"
                                + unit.getAsyncId(),
                        "Place " + unit.getName(),
                        unit.getUnitEmoji()))
                .toList());

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " has no eligible replacement ship for _Retrofitting_.");
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose the replacement ship.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(RESOLVE_RETROFITTING)
    public static void resolveRetrofitting(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(RESOLVE_RETROFITTING.length()).split("\\|", 3);
        if (payload.length != 3) {
            return;
        }

        Tile tile = game.getTileByPosition(payload[0]);
        UnitModel sourceShip = player.getUnitFromAsyncID(payload[1]);
        UnitModel replacementShip = player.getUnitFromAsyncID(payload[2]);
        if (tile == null || sourceShip == null || replacementShip == null) {
            return;
        }

        RemoveUnitService.removeUnit(event, tile, game, player, tile.getSpaceUnitHolder(), sourceShip.getUnitType(), 1);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + replacementShip.getAsyncId());
        game.setStoredValue(RETROFITTING_STATE, player.getFaction());

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " used _Retrofitting_ to replace 1 " + sourceShip.getName()
                        + " with 1 " + replacementShip.getName() + ".");
        ButtonHelper.deleteMessage(event);
    }

    public static void returnRetrofitting(Game game) {
        String holderFaction = game.getStoredValue(RETROFITTING_STATE);
        if (holderFaction.isBlank()) {
            return;
        }

        Player holder = game.getPlayerFromColorOrFaction(holderFaction);
        Player owner = game.getPNOwner(RETROFITTING);
        game.setStoredValue(RETROFITTING_STATE, "");
        if (holder == null
                || owner == null
                || !holder.getPromissoryNotesInPlayArea().contains(RETROFITTING)) {
            return;
        }

        holder.removePromissoryNote(RETROFITTING);
        owner.setPromissoryNote(RETROFITTING);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, holder, false);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, owner, false);
        MessageHelper.sendMessageToChannel(
                holder.getCorrectChannel(), "_Retrofitting_ was returned to " + owner.getRepresentationNoPing() + ".");
    }
}
