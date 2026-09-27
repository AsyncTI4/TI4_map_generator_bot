package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Scrapyard;

import java.util.ArrayList;
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
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class ScrapyardTechHandler {
    private static final String USE_HOTSWAPPING = "useHotswapping";
    private static final String SELECT_HOTSWAPPING_UNIT = "selectHotswappingUnit_";

    public static List<Button> getHotswappingButton(Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + USE_HOTSWAPPING, "Use Hotswapping", FactionEmojis.scrapyard));
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        return buttons;
    }

    public static void offerHotswapping(Game game, Tile activeSystem, Player activatingPlayer) {
        if (game == null || activeSystem == null || activatingPlayer == null) {
            return;
        }
        for (Player player : game.getRealPlayers()) {
            if (!player.hasTech("thscrapyardr")
                    || (player != activatingPlayer
                            && activeSystem.getUnitHolders().values().stream()
                                    .flatMap(holder -> holder.getUnitKeysForPlayer(player).stream())
                                    .findAny()
                                    .isEmpty())) {
                continue;
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", you may use _Hotswapping_.",
                    getHotswappingButton(player));
        }
    }

    public static List<String> getUnitTypesOnBoard(Game game, Player player) {
        return game.getTileMap().values().stream()
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .flatMap(holder -> holder.getUnitKeysForPlayer(player).stream())
                .map(unitKey -> unitKey.unitType().value)
                .distinct()
                .toList();
    }

    @ButtonHandler(USE_HOTSWAPPING)
    public static void useHotswapping(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasTech("thscrapyardr")) {
            return;
        }

        List<Button> eligibleUnits = new ArrayList<>();
        for (String unit : getUnitTypesOnBoard(game, player)) {
            UnitModel unitModel = player.getPriorityUnitByAsyncID(unit, null);
            UnitModel upgrade = unitModel == null
                    ? null
                    : unitModel.getUpgradesToUnitId().map(Mapper::getUnit).orElse(unitModel);
            String techId = upgrade == null ? null : upgrade.getRequiredTechId().orElse(null);
            if (techId == null || player.hasTech(techId)) {
                continue;
            }
            eligibleUnits.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_HOTSWAPPING_UNIT + techId,
                    unitModel.getName(),
                    unitModel.getUnitEmoji()));
        }
        if (eligibleUnits.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " has no eligible unit upgrades for _Hotswapping_.");
            return;
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", choose a unit type to treat as upgraded until this tactical action ends.",
                eligibleUnits);
    }

    @ButtonHandler(SELECT_HOTSWAPPING_UNIT)
    public static void selectHotswappingUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String techId = buttonID.substring(SELECT_HOTSWAPPING_UNIT.length());
        if (!player.hasTech("thscrapyardr") || Mapper.getTech(techId) == null || player.hasTech(techId)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.addTech(techId);
        game.setStoredValue(hotswappingKey(player), techId);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " treats " + Mapper.getTech(techId).getNameRepresentation()
                        + " as owned via _Hotswapping_ until this tactical action ends.");
    }

    public static void clearHotswapping(Game game) {
        if (game == null) {
            return;
        }
        for (Player hotswappingPlayer : game.getRealPlayers()) {
            if (hotswappingPlayer.hasTech("thscrapyardr")) {
                String techId = game.getStoredValue(hotswappingKey(hotswappingPlayer));
                if (techId.isEmpty()) {
                    continue;
                }
                hotswappingPlayer.removeTech(techId);
                game.removeStoredValue(hotswappingKey(hotswappingPlayer));
            }
        }
    }

    private static String hotswappingKey(Player player) {
        return "scrapyardHotswapping" + player.getFaction();
    }
}
