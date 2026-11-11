package ti4.discord.interactions.buttons.handlers.faction.homebrew.frmisc.xyrix;

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
import ti4.game.UnitHolder;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class XyrixUnitHandler {
    private static final String SCHOLARS = "xyrix_mech";
    private static final String USE_SCHOLARS = "xyrixUseScholars_";
    private static final String SUSTAIN_SCHOLARS = "xyrixSustainScholars_";

    public static void addScholarsCombatButton(List<Button> buttons, Player player, Tile tile) {
        if (hasScholar(player, tile)) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + USE_SCHOLARS + tile.getPosition(),
                    "Use Scholars (On Win)",
                    FactionEmojis.xyrix));
        }
    }

    @ButtonHandler(USE_SCHOLARS)
    public static void offerScholarsToSustain(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(USE_SCHOLARS.length()));
        if (!isGroundCombat(game, tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder holder : getEligibleScholars(player, tile)) {
            for (int scholar = 1; scholar <= undamagedScholars(player, holder); scholar++) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + SUSTAIN_SCHOLARS + tile.getPosition() + "|" + holder.getName()
                                + "|" + scholar,
                        "Sustain Scholar on " + holderName(game, holder) + " (" + scholar + ")",
                        FactionEmojis.xyrix));
            }
        }
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose 1 Scholar to sustain and draw 1 action card.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(SUSTAIN_SCHOLARS)
    public static void sustainScholar(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SUSTAIN_SCHOLARS.length()).split("\\|", 3);
        Tile tile = payload.length == 3 ? game.getTileByPosition(payload[0]) : null;
        UnitHolder holder = tile == null || payload.length != 3
                ? null
                : tile.getUnitHolders().get(payload[1]);
        UnitKey mech = Units.getUnitKey(UnitType.Mech, player.getColorID());
        if (!isGroundCombat(game, tile)
                || holder == null
                || !player.hasUnit(SCHOLARS)
                || holder.getUnitCount(mech) <= holder.getDamagedUnitCount(mech)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        holder.addDamagedUnit(mech, 1);
        ActionCardHelper.drawActionCards(player, 1);
        MessageHelper.sendMessageToChannel(
                game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(),
                player.getRepresentationNoPing() + " sustained 1 Scholar on " + holderName(game, holder)
                        + " and drew 1 action card.");
        ButtonHelper.deleteTheOneButton(event);
    }

    private static List<UnitHolder> getEligibleScholars(Player player, Tile tile) {
        if (tile == null || !player.hasUnit(SCHOLARS)) {
            return List.of();
        }
        UnitKey mech = Units.getUnitKey(UnitType.Mech, player.getColorID());
        return tile.getUnitHolders().values().stream()
                .filter(holder -> holder.getUnitCount(mech) > holder.getDamagedUnitCount(mech))
                .toList();
    }

    private static boolean hasScholar(Player player, Tile tile) {
        if (tile == null || !player.hasUnit(SCHOLARS)) {
            return false;
        }
        UnitKey mech = Units.getUnitKey(UnitType.Mech, player.getColorID());
        return tile.getUnitHolders().values().stream().anyMatch(holder -> holder.getUnitCount(mech) > 0);
    }

    private static int undamagedScholars(Player player, UnitHolder holder) {
        UnitKey mech = Units.getUnitKey(UnitType.Mech, player.getColorID());
        return holder.getUnitCount(mech) - holder.getDamagedUnitCount(mech);
    }

    private static boolean isGroundCombat(Game game, Tile tile) {
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        return combat != null
                && tile != null
                && tile.getPosition().equals(combat.tilePosition())
                && !"space".equals(combat.unitHolderName());
    }

    private static String holderName(Game game, UnitHolder holder) {
        return "space".equals(holder.getName()) ? "space" : Helper.getPlanetRepresentation(holder.getName(), game);
    }
}
