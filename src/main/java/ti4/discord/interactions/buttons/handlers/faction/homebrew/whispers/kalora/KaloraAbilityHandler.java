package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.kalora;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class KaloraAbilityHandler {

    public static boolean eusocialityRetreat(Player player, GenericInteractionCreateEvent event, Tile activeTile) {
        if (CommandCounterHelper.hasCC(event, player.getColor(), activeTile)) return false;
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getFactionEmoji()
                        + " did not place a command token in system they retreated to due to **Eusociality**.");
        return true;
    }

    public static void carapaceRegeneration(Player player, Tile destTile, GenericInteractionCreateEvent event) {
        String colorID = Mapper.getColorID(player.getColor());
        UnitHolder space = destTile.getSpaceUnitHolder();
        for (UnitType unitType : UnitType.values()) {
            if (unitType == UnitType.Mech) continue;
            int damaged = space.getDamagedUnitCount(unitType, colorID);
            if (damaged > 0) space.removeDamagedUnit(Units.getUnitKey(unitType, colorID), damaged);
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getFactionEmoji()
                        + " repaired all damaged ships that retreated due to **Carapace Regeneration**.");
    }

    public static void primordial(Player player, Game game) {
        int tgAmount = game.getRealPlayers().size() - 1;
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", if you were the first to score this objective, you may gain " + tgAmount
                        + " trade goods via **Primordial**.");
    }

    public static void chitinShielding(Player player, Player opponent, Game game) {
        Button resolve = Buttons.green(
                player.factionButtonChecker() + "kaloraChitinShielding_" + opponent.getFaction(),
                "Resolve Chitin Shielding");
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", if you win this space combat, you may exhaust _Chitin Shielding_ to place 1 of "
                        + opponent.getFactionEmoji() + "'s command tokens into the active system.",
                List.of(resolve));
    }

    @ButtonHandler("kaloraChitinShielding_")
    public static void resolveChitinShielding(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String opponentFaction = buttonID.replace("kaloraChitinShielding_", "");
        Player opponent = game.getPlayerFromColorOrFaction(opponentFaction);
        Tile activeTile = game.getTileByPosition(game.getActiveSystem());
        player.exhaustTech("bakalor");
        CommandCounterHelper.addCC(event, opponent, activeTile);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getFactionEmoji() + " exhausted _Chitin Shielding_ to place " + opponent.getFactionEmoji()
                        + "'s command token in the active system.");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    public static void sharedTreasure(Player player, Game game) {
        int tgAmount = game.getRealPlayers().size() - 1;
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", if you were the first to score this objective (Kalora's prior scores don't count against you),"
                        + " you may gain " + tgAmount + " trade goods via " + FactionEmojis.kalora
                        + " **Shared Treasure**.");
    }
}
