package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.tyris;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.Helper;
import ti4.helpers.RelicHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;

@UtilityClass
public class TyrisLeaderHandler {

    public static void resolveMechAction(Player player, Game game, ButtonInteractionEvent event) {
        String successMessage = player.getFactionEmoji()
                + " spent 1 strategy token using Chrono-Tactician Xelthar, the Tyris Commander ("
                + player.getStrategicCC() + "->" + (player.getStrategicCC() - 1) + ").";
        if (!player.hasRelicReady("emelpar")) {
            player.setStrategicCC(player.getStrategicCC() - 1);
            ButtonHelperCommanders.resolveMuaatCommanderCheck(
                    player, game, event, FactionEmojis.tyris + " **Tyris Commander**");
        } else {
            player.addExhaustedRelic("emelpar");
            successMessage = player.getFactionEmoji() + " used the _" + RelicHelper.sillySpelling()
                    + "_ to use Chrono-Tactician Xelthar, the Tyris Commander.";
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
        List<Button> buttons = new ArrayList<>(
                Helper.getPlanetPlaceUnitButtons(player, game, "mech", "placeOneNDone_skipbuildcomponent"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "Please choose the planet you wish to place 1 " + UnitEmojis.mech + " mech on.",
                buttons);
    }

    public static void addCommanderActionButton(
            Player p1, String factionChecker, String prefix, List<Button> compButtons) {
        if (p1.getStrategicCC() <= 0 && !p1.hasRelicReady("emelpar")) return;
        compButtons.add(Buttons.green(
                factionChecker + prefix + "ability_tyrisCommanderMech", "Place Mech for 1 Token", FactionEmojis.tyris));
    }

    public static void offerInfantry(Game game, Player player) {
        List<Button> buttons =
                new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, game, "gf", "placeOneNDone_skipbuild"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + " you may use Chrono-Tactician Xelthar to place 1 "
                        + UnitEmojis.infantry + " infantry on a planet you control.",
                buttons);
    }

    public static boolean isHeroActiveThisRound(Game game, Player player) {
        return !game.getStoredValue("tyrisHeroRound" + game.getRound() + "_" + player.getFaction())
                .isEmpty();
    }

    public static void offerHeroAtStartOfTurn(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "leader_tyrishero",
                "Use Fatebreaker Azhurak (Unlimited Actions)",
                FactionEmojis.tyris));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", you may use Fatebreaker Azhurak at the start of this turn for unlimited actions.",
                buttons);
    }
}
