package ti4.discord.interactions.buttons.handlers.faction;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperActionCards;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.RandomHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.leader.PurgeHeroService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
class OtherHeroButtonHandler {

    @ButtonHandler("purgeHacanHero")
    public static void purgeHacanHero(ButtonInteractionEvent event, Player player, Game game) { // TODO: add service
        PurgeHeroService.purgeHeroPreamble(event, player, game, "hacanhero", "Harrugh Gefhara, the Hacan hero");
    }

    @ButtonHandler("purgeSardakkHero")
    public static void purgeSardakkHero(ButtonInteractionEvent event, Player player, Game game) { // TODO: add service
        PurgeHeroService.purgeHeroPreamble(event, player, game, "sardakkhero", "Sh'val, Harbinger, the N'orr hero");
        ButtonHelperHeroes.killShipsSardakkHero(player, game, event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", all ships have been removed, please continue to invasion.");
    }

    @ButtonHandler("purgeAtokeraHero")
    public static void purgeAtokeraHero(ButtonInteractionEvent event, Player player, Game game) { // TODO: add service
        PurgeHeroService.purgeHeroPreamble(event, player, game, "atokerahero", "Kapoko Vui, the Atokera hero");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", unfortunately at this time the addition of ships to the ground is not automated."
                        + " `/move units` can place them on the planet however, and they will roll dice as normal once there.");
    }

    @ButtonHandler("utilizePharadnHero_")
    public static void utilizePharadnHero(
            Player player, Game game, String buttonID, ButtonInteractionEvent event) { // TODO: add service
        PurgeHeroService.purgeHeroPreamble(
                event, player, game, "pharadnhero", "Pharad'n the Immortal, the Pharad'n hero");
        String planet = buttonID.split("_")[1];
        List<Button> buttons = new ArrayList<>();
        buttons.add(
                Buttons.red(player.factionButtonChecker() + "pharadnHeroDestroy_" + planet, "Destroy All Infantry"));
        for (int x = 1;
                x < player.getNomboxTile().getSpaceUnitHolder().getUnitCount(UnitType.Infantry, player) + 1;
                x++) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "pharadnHeroCommit_" + planet + "_" + x,
                    "Commit " + x + " Infantry"));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getFactionEmoji() + ", you've chosen to use Pharad'n the Immortal on "
                        + Mapper.getPlanet(planet).getAutoCompleteName()
                        + ". Decide whether to destroy all infantry or commit infantry.",
                buttons);
    }

    @ButtonHandler("pharadnHeroDestroy_")
    public static void pharadnHeroDestroy(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        Planet unitHolder = game.getPlanet(planet);
        Tile tile = game.getTileContainingPlanet(planet);
        if (unitHolder == null || tile == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not locate planet");
            return;
        }

        for (Player p2 : game.getRealPlayers()) {
            if (tile.containsPlayersUnits(p2)) {
                int amountInf = unitHolder.getUnitCount(UnitType.Infantry, p2.getColor());
                UnitKey infKey = Units.getUnitKey(UnitType.Infantry, p2.getColorID());
                DestroyUnitService.destroyUnit(event, tile, game, infKey, amountInf, unitHolder, false);
            }
        }

        String msg = player.getFactionEmoji() + ", you've chosen to use Pharad'n the Immortal on "
                + unitHolder.getRepresentation(game) + " to destroy all infantry.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("pharadnHeroCommit_")
    public static void pharadnHeroCommit(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String amount = buttonID.split("_")[2];
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getFactionEmoji() + " you chose to use the Pharad'n the Immortal on "
                        + Mapper.getPlanet(planet).getAutoCompleteName() + " to commit " + amount + " infantry.");
        ButtonHelper.deleteMessage(event);
        Tile tile = game.getTileContainingPlanet(planet);
        AddUnitService.addUnits(event, tile, game, player.getColor(), amount + " inf " + planet);
        RemoveUnitService.removeUnits(event, player.getNomboxTile(), game, player.getColor(), amount + " inf");
    }

    @ButtonHandler("purgeRohdhnaHero")
    public static void purgeRohdhnaHero(ButtonInteractionEvent event, Player player, Game game) { // TODO: add service
        PurgeHeroService.purgeHeroPreamble(event, player, game, "rohdhnahero", "Roh’Vhin Dhna mk4, the Roh'Dhna hero");
    }

    @ButtonHandler("purgeVaylerianHero")
    public static void purgeVaylerianHero(ButtonInteractionEvent event, Player player, Game game) { // TODO: add service
        PurgeHeroService.purgeHeroPreamble(event, player, game, "vaylerianhero", "Dyln Harthuul, the Vaylerian hero");
        if (!game.isNaaluAgent() && !game.isWarfareAction()) {
            player.setTacticalCC(player.getTacticalCC() - 1);
            CommandCounterHelper.addCC(event, player, game.getTileByPosition(game.getActiveSystem()));
            game.setStoredValue("vaylerianHeroActive", "true");
        }
        List<Tile> gloryTiles = ButtonHelperAgents.getGloryTokenTiles(game);
        for (int i = 0; i < gloryTiles.size(); i++) {
            List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "vaylerianhero");
            if (!buttons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        "Use buttons to remove a command token from the game board.",
                        buttons);
            }
        }
        List<Button> buttons = ButtonHelper.getGainCCButtons(player);
        String message2 =
                player.getRepresentationUnfogged() + ", you may gain 1 command token. Your current command tokens are "
                        + player.getCCRepresentation() + ". Use buttons to gain 1 command token.";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
    }

    @ButtonHandler("purgeUydaiHero")
    public static void purgeUydaiHero(ButtonInteractionEvent event, Player player, Game game) { // TODO: add service
        PurgeHeroService.purgeHeroPreamble(event, player, game, "uydaihero", "Londor II, the Uydai hero");
    }

    @ButtonHandler("purgeKortaliHero_")
    public static void purgeKortaliHero(
            ButtonInteractionEvent event, Player player, String buttonID, Game game) { // TODO: add service
        PurgeHeroService.purgeHeroPreamble(event, player, game, "kortalihero", "Queen Nadalia, the Kortali hero");
        ButtonHelperHeroes.offerStealRelicButtons(game, player, buttonID, event);
        List<Button> buttons = ButtonHelper.getGainCCButtons(player);
        String message2 =
                player.getRepresentationUnfogged() + ", your current command tokens are " + player.getCCRepresentation()
                        + ". Use buttons to gain command tokens equal to your technology specialty and legendary planet count.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message2, buttons);
    }

    @ButtonHandler("purgeOrlandoHero_")
    public static void purgeOrlandoHero(ButtonInteractionEvent event, Player player, Game game) { // TODO: add service
        StringBuilder p = new StringBuilder("p");
        while (RandomHelper.isOneInX(12)) {
            p.append("p");
        }
        PurgeHeroService.purgeHeroPreamble(event, player, game, "orlandohero", "F.S.S. Orlando, the Orlando hero");
        String msg = player.getRepresentationNoPing()
                + ", please choose the unit that recently died, with which you wish to resolve _A" + p
                + "ollo Protocol_.";
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                msg,
                ButtonHelperActionCards.getCourageousOptions(player, game, true, "orlando"));
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("purgeBastionHero_")
    public static void purgeBastionHero(ButtonInteractionEvent event, Player player, Game game) { // TODO: add service
        PurgeHeroService.purgeHeroPreamble(event, player, game, "bastionhero", "Lyra Keen, the Bastion hero");
        String msg = player.getRepresentationNoPing()
                + ", please choose the galvanized unit that recently died, with which you wish to resolve _Intelligence Unshackled_.";
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                msg,
                ButtonHelperActionCards.getCourageousOptions(player, game, true, "bastionhero"));
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("purgeRedCreussHero_")
    public static void purgeRedCreussHero(
            ButtonInteractionEvent event, Player player, String buttonID, Game game) { // TODO: add service
        PurgeHeroService.purgeHeroPreamble(
                event, player, game, "redcreusshero", "Homesick Phantom, the Rebellion hero");
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        UnitHolder captureUnitHolder = player.getNombox();
        UnitHolder spaceUnitHolder = tile.getSpaceUnitHolder();
        if (captureUnitHolder != null && spaceUnitHolder != null) {
            for (UnitKey key : captureUnitHolder.getUnitKeys()) {
                Player player_ = game.getPlayerFromColorOrFaction(key.getColor());
                if (player_ != player) {
                    continue;
                }
                if (!player_.getUnitFromUnitKey(key).getIsShip()) {
                    continue;
                }
                int amt = captureUnitHolder.getUnitCount(key);
                if (player_.getUnitFromUnitKey(key).getUnitType() == UnitType.Fighter && game.isTwilightsFallMode()) {
                    int count = 0;
                    if (!player.getStoredValue("crimsonHeroFF").isEmpty()
                            && (player.hasUnit("tf-vortexer") || player.hasTech("amalgamation"))) {
                        count = Integer.parseInt(player.getStoredValue("crimsonHeroFF"));
                    }
                    if (count > 0) {
                        amt = count;
                    }
                }
                var removed = captureUnitHolder.removeUnit(key, amt);
                spaceUnitHolder.addUnitsWithStates(key, removed);
            }
        }
    }

    @ButtonHandler("ghotiHeroIn_")
    public static void ghotiHeroIn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.substring(buttonID.indexOf('_') + 1);
        List<Button> buttons = ButtonHelperAgents.getUnitsToArboAgent(player, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentationUnfogged() + ", please choose which unit you wish to replace.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }
}
