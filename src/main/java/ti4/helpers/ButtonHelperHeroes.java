package ti4.helpers;

import static org.apache.commons.lang3.StringUtils.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.PlanetModel;
import ti4.model.PublicObjectiveModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.UnitEmojis;
import ti4.service.fow.BlindSelectionService;
import ti4.service.fow.RiftSetModeService;
import ti4.service.franken.FrankenLeaderService;
import ti4.service.leader.PlayHeroService;
import ti4.service.leader.UnlockLeaderService;
import ti4.service.planet.AddPlanetService;
import ti4.service.planet.FlipTileService;
import ti4.service.planet.PlanetService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.CheckUnitContainmentService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParsedUnit;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class ButtonHelperHeroes {

    public static void argentHeroStep1(Game game, Player player, GenericInteractionCreateEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesWithYourCC(player, game, event)) {
            buttons.add(Buttons.green(
                    "argentHeroStep2_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
        }
        buttons.add(Buttons.red("deleteButtons", "Done resolving"));
        String msg = player.getRepresentation()
                + ", please choose the system you wish to move stuff to using Mirik Aun Sissiri, the Argent hero.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    public static List<Button> getShrineButtons(Player p2, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getPlanets()) {
            UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
            if (uH != null && uH.getTokenList().contains("token_kaltrimshrine1.png")) {
                buttons.add(Buttons.green("shrineView_" + planet, Helper.getPlanetRepresentation(planet, game)));
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Gain 2 CC instead"));

        return buttons;
    }

    @ButtonHandler("shrineView_")
    public static void resolveShrineView(
            Player player, Player p2, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.replace("shrineView_", "");
        ButtonHelper.deleteMessage(event);
        if (planet.equalsIgnoreCase(game.getStoredValue("kaltrimcrownplanet"))) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentation() + ", the planet " + Helper.getPlanetRepresentation(planet, game)
                            + " has the Kaltrim Crown on it.");
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentation() + ", the planet " + Helper.getPlanetRepresentation(planet, game)
                            + " does not have the Kaltrim Crown on it.");
        }
    }

    public static List<Button> argentBreakthroughStep1(Game game, Player player, Tile activeSystem) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "argentHeroStep2_" + activeSystem.getPosition(),
                activeSystem.getRepresentationForButtons(game, player)));
        for (String pos : FoWHelper.getAdjacentTilesAndNotThisTile(game, activeSystem.getPosition(), player, false)) {
            Tile tile = game.getTileByPosition(pos);
            if (CommandCounterHelper.hasCC(player, tile)
                    && !FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game)) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "argentHeroStep2_" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Done resolving"));
        return buttons;
    }

    @ButtonHandler("argentHeroStep2_")
    public static void argentHeroStep2(Game game, Player player, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        String pos1 = buttonID.split("_")[1];
        Tile destination = game.getTileByPosition(pos1);
        for (Tile tile : ButtonHelper.getTilesWithShipsInTheSystem(player, game)) {
            buttons.add(Buttons.green(
                    "argentHeroStep3_" + pos1 + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }
        String msg = player.getRepresentation()
                + ", please choose the system you wish to move stuff from. These will move stuff to "
                + destination.getRepresentationForButtons(game, player) + ".";
        buttons.add(Buttons.red(
                "deleteButtons", "Done moving to " + destination.getRepresentationForButtons(game, player)));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    public static void xxchaHeroTEStart(Player player) {
        List<Button> buttons = new ArrayList<>();

        String msg = player.getRepresentation() + ", please choose whether you want to place a mech or a PDS.";
        buttons.add(Buttons.gray("xxchaHeroTEStep2_mech", "Place Mech", UnitEmojis.mech));
        buttons.add(Buttons.gray("xxchaHeroTEStep2_pds", "Place PDS", UnitEmojis.pds));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("xxchaHeroTEStep2_")
    public static void xxchaHeroTEStep2_(
            Game game, Player player, GenericInteractionCreateEvent event, String buttonID) {
        String unit = buttonID.split("_")[1];
        List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, unit, "placeOneNDone_skipbuildxxcha");

        String msg = player.getRepresentation() + " please choose the planet you wish to put this " + unit + " on.";

        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getArgentHeroStep3Buttons(Game game, Player player, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        String pos1 = buttonID.split("_")[1];
        Tile destination = game.getTileByPosition(pos1);
        String pos2 = buttonID.split("_")[2];
        Tile origin = game.getTileByPosition(pos2);
        for (UnitHolder unitHolder : origin.getUnitHolders().values()) {
            Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null || (unitModel.getIsStructure() && !Objects.equals(unitHolder.getName(), "space")))
                    continue;
                UnitKey unitKey = unitEntry.getKey();
                String unitName = unitKey.unitName();
                int totalUndamagedUnits = unitEntry.getValue();
                int damagedUnits = 0;

                if (unitHolder.getUnitDamage() != null
                        && unitHolder.getUnitDamage().get(unitKey) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                }
                String end = "";
                if (!"space".equalsIgnoreCase(unitHolder.getName())) {
                    end = " from " + Helper.getPlanetRepresentation(unitHolder.getName(), game);
                }
                totalUndamagedUnits -= damagedUnits;
                if (totalUndamagedUnits > 0) {
                    buttons.add(Buttons.green(
                            "argentHeroStep4_" + pos1 + "_" + origin.getPosition() + "_" + unitHolder.getName() + "_"
                                    + unitName,
                            "1 " + unitName + end));
                }
                if (damagedUnits > 0) {
                    buttons.add(Buttons.green(
                            "argentHeroStep4_" + pos1 + "_" + origin.getPosition() + "_" + unitHolder.getName() + "_"
                                    + unitName + "damaged",
                            "1 damaged " + unitName + end));
                }
            }
        }
        buttons.add(Buttons.red(
                "deleteButtons", "Done moving to " + destination.getPosition() + " from " + origin.getPosition()));
        return buttons;
    }

    @ButtonHandler("argentHeroStep3_")
    public static void argentHeroStep3(Game game, Player player, String buttonID) {
        List<Button> buttons = getArgentHeroStep3Buttons(game, player, buttonID);
        String pos1 = buttonID.split("_")[1];
        Tile destination = game.getTileByPosition(pos1);
        if (buttonID.contains("agent")) {
            CommandCounterHelper.addCC(null, player, destination);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Added a command counter to the destination system.");
        }
        String msg =
                player.getRepresentation() + ", please choose the units you wish to move. These will move stuff to "
                        + destination.getRepresentationForButtons(game, player) + ".";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("argentHeroStep4_")
    public static void argentHeroStep4(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos1 = buttonID.split("_")[1];
        Tile destination = game.getTileByPosition(pos1);
        String pos2 = buttonID.split("_")[2];
        Tile origin = game.getTileByPosition(pos2);
        String unitHolderName = buttonID.split("_")[3];
        String unitName = buttonID.split("_")[4];
        boolean damaged = false;
        if (unitName.contains("damaged")) {
            damaged = true;
            unitName = unitName.replace("damaged", "");
        }
        destination = FlipTileService.flipTileIfNeeded(event, destination, game);
        var removed = RemoveUnitService.removeUnits(
                event, origin, game, player.getColor(), unitName + " " + unitHolderName, damaged);
        AddUnitService.addUnits(event, destination, game, player.getColor(), unitName, removed);
        List<Button> buttons = getArgentHeroStep3Buttons(game, player, buttonID);
        String msg2 = player.getFactionEmoji() + " moved 1 " + unitName + " from "
                + origin.getRepresentationForButtons(game, player) + " to "
                + destination.getRepresentationForButtons(game, player);

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2);
        String msg =
                player.getRepresentation() + ", please choose the units you wish to move. These will move stuff to "
                        + destination.getRepresentationForButtons(game, player) + ".";
        event.getMessage()
                .editMessage(msg)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static List<String> getAttachmentsForFlorzenHero(Game game, Player player) {
        List<String> legendaries = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!FoWHelper.playerHasShipsInSystem(player, tile)) {
                continue;
            }
            for (UnitHolder uh : tile.getPlanetUnitHolders()) {
                for (String token : uh.getTokenList()) {
                    if (!token.contains("attachment")) {
                        continue;
                    }
                    token = token.replace(".png", "").replace("attachment_", "");

                    String s = uh.getName() + "_" + token;
                    if (!token.contains("sleeper") && !token.contains("control") && !legendaries.contains(s)) {
                        legendaries.add(s);
                    }
                }
            }
        }
        return legendaries;
    }

    public static void resolveGledgeHero(Player player, Game game) {
        List<Button> buttons = getAttachmentSearchButtons(game, player);
        String msg = player.getRepresentation() + " use these buttons to find attachments. Exploration #";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg + "1", buttons);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg + "2", buttons);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg + "3", buttons);
    }

    public static void resolveKhraskHero(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("khraskHeroStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray(
                        "khraskHeroStep2_" + p2.getFaction(),
                        p2.getFactionModel().getShortName());
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose whose planet you wish to exhaust or ready.",
                buttons);
    }

    @ButtonHandler("khraskHeroStep2_")
    public static void resolveKhraskHeroStep2(Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        String faction = buttonID.split("_")[1];
        buttons.add(Buttons.green("khraskHeroStep3Ready_" + faction, "Ready a Planet"));
        buttons.add(Buttons.red("khraskHeroStep3Exhaust_" + faction, "Exhaust a Planet"));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose if you wish to exhaust or ready a planet.",
                buttons);
    }

    @ButtonHandler("khraskHeroStep3Exhaust_")
    public static void resolveKhraskHeroStep3Exhaust(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (p2.getReadiedPlanets().isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Chosen player had no readied planets. Nothing has been done.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getReadiedPlanets()) {
            buttons.add(Buttons.gray(
                    "khraskHeroStep4Exhaust_" + p2.getFaction() + "_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose the planet you wish to exhaust.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("khraskHeroStep4Exhaust_")
    public static void resolveKhraskHeroStep4Exhaust(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, game);
        p2.exhaustPlanet(planet);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getRepresentationUnfogged() + " you exhausted " + planetRep);
        MessageHelper.sendMessageToChannel(
                p2.getCorrectChannel(),
                p2.getRepresentationUnfogged() + " your planet " + planetRep + " was exhausted.");
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveAxisHeroStep1(Player player) {
        List<Button> buttons = new ArrayList<>();
        String message = player.getRepresentation() + ", please choose _Axis Order_ you wish to send.";
        for (String shipOrder : ButtonHelper.getPlayersShipOrders(player)) {
            Button transact = Buttons.green(
                    "axisHeroStep2_" + shipOrder, Mapper.getRelic(shipOrder).getName());
            buttons.add(transact);
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    private static List<String> getAllRevealedRelics(Game game) {
        List<String> relicsTotal = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            for (String relic : player.getRelics()) {
                if (relic.contains("axisorder")
                        || relic.contains("enigmatic")
                        || relic.contains("shiporder")
                        || relic.contains("starchart")) {
                    continue;
                }
                relicsTotal.add(player.getFaction() + ";" + relic);
            }
        }

        String key = "lanefirRelicReveal";
        for (int x = 1; x < 4; x++) {
            if (!game.getStoredValue(key + x).isEmpty()) {
                relicsTotal.add(key + ";" + game.getStoredValue(key + x));
            }
        }
        return relicsTotal;
    }

    public static void cheiranHeroResolution(Player player, Game game, GenericInteractionCreateEvent event) {
        // "dn,cv,dd,2 ff,mech a,2 inf g,sd a"
        List<Button> buttons = new ArrayList<>(
                Helper.getTileForCheiranHeroPlaceUnitButtons(player, game, "dreadnought", "placeOneNDone_skipbuild"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Place 1 dreadnought", buttons);
        buttons = new ArrayList<>(
                Helper.getTileForCheiranHeroPlaceUnitButtons(player, game, "destroyer", "placeOneNDone_skipbuild"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Place 1 destroyer", buttons);
        buttons = new ArrayList<>(
                Helper.getTileForCheiranHeroPlaceUnitButtons(player, game, "carrier", "placeOneNDone_skipbuild"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Place 1 carrier", buttons);
        buttons = new ArrayList<>(
                Helper.getTileForCheiranHeroPlaceUnitButtons(player, game, "ff", "placeOneNDone_skipbuild"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Place 1 fighter", buttons);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Place 1 fighter", buttons);

        buttons = new ArrayList<>();
        buttons.addAll(Helper.getTileForCheiranHeroPlaceUnitButtons(player, game, "gf", "placeOneNDone_skipbuild"));
        buttons.addAll(Helper.getPlanetPlaceUnitButtons(player, game, "gf", "placeOneNDone_skipbuild"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Place 1 infantry", buttons);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Place 1 infantry", buttons);

        buttons = new ArrayList<>();
        buttons.addAll(Helper.getTileForCheiranHeroPlaceUnitButtons(player, game, "sd", "placeOneNDone_skipbuild"));
        buttons.addAll(Helper.getPlanetPlaceUnitButtons(player, game, "sd", "placeOneNDone_skipbuild"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Place 1 space dock", buttons);

        buttons = new ArrayList<>();
        buttons.addAll(Helper.getTileForCheiranHeroPlaceUnitButtons(player, game, "mech", "placeOneNDone_skipbuild"));
        buttons.addAll(Helper.getPlanetPlaceUnitButtons(player, game, "mech", "placeOneNDone_skipbuild"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Place 1 mech", buttons);
    }

    @ButtonHandler("lizhoHeroFighterResolution")
    public static void lizhoHeroFighterDistribution(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        List<Button> buttons = new ArrayList<>(
                Helper.getTileWithTrapsPlaceUnitButtons(player, game, "2ff", "placeOneNDone_skipbuild"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Place 2 fighters", buttons);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Place 2 fighters", buttons);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Place 2 fighters", buttons);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Place 2 fighters", buttons);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Place 2 fighters", buttons);
        buttons =
                new ArrayList<>(Helper.getTileWithTrapsPlaceUnitButtons(player, game, "ff", "placeOneNDone_skipbuild"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Place 1 fighter", buttons);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Place 1 fighter", buttons);
    }

    public static void resolveLanefirHeroStep1(Player player, Game game) {
        List<String> revealedRelics = getAllRevealedRelics(game);
        String key = "lanefirRelicReveal";
        StringBuilder revealMsg =
                new StringBuilder(player.getRepresentation() + " you revealed the following 3 relics:\n");
        for (int x = 1; x < 4; x++) {
            String relic = game.drawRelic();
            game.setStoredValue(key + x, relic);
            revealMsg
                    .append(x)
                    .append(". ")
                    .append(Mapper.getRelic(relic).getName())
                    .append('\n');
        }
        int size = revealedRelics.size();
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " may gain " + StringHelper.pluralize(size, "command token") + ".");
        List<Button> buttons = ButtonHelper.getGainCCButtons(player);
        String trueIdentity = player.getRepresentationUnfogged();
        String message2 = trueIdentity + ", your current command tokens are " + player.getCCRepresentation()
                + ". Use buttons to gain command tokens.";
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message2, buttons);
        revealedRelics = getAllRevealedRelics(game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), revealMsg.toString());

        List<Button> relicButtons = new ArrayList<>();
        for (String fanctionNRelic : revealedRelics) {
            String relic = fanctionNRelic.split(";")[1];
            relicButtons.add(Buttons.green(
                    "relicSwapStep1_" + fanctionNRelic, Mapper.getRelic(relic).getName()));
        }
        String revealMsg2 = player.getRepresentation()
                + " use buttons to swap any revealed relic or relic in play area with another relic. No Automated effects of a relic gain or loss will be applied. All relics may only move places once.\n";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), revealMsg2, relicButtons);
    }

    @ButtonHandler("relicSwapStep1")
    public static void resolveRelicSwapStep1(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String fanctionNRelic = buttonID.replace("relicSwapStep1_", "");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        String relic = fanctionNRelic.split(";")[1];
        List<String> revealedRelics = getAllRevealedRelics(game);
        String revealMsg = player.getRepresentation() + " you chose to swap the relic "
                + Mapper.getRelic(relic).getName() + ". Choose another relic to swap it with";
        List<Button> relicButtons = new ArrayList<>();
        for (String fanctionNRelic2 : revealedRelics) {
            if (fanctionNRelic.equalsIgnoreCase(fanctionNRelic2)) {
                continue;
            }
            String relic2 = fanctionNRelic2.split(";")[1];
            relicButtons.add(Buttons.green(
                    "relicSwapStep2;" + fanctionNRelic + ";" + fanctionNRelic2,
                    Mapper.getRelic(relic2).getName()));
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), revealMsg, relicButtons);
    }

    @ButtonHandler("relicSwapStep2")
    public static void resolveRelicSwapStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        ButtonHelper.deleteMessage(event);
        String faction = buttonID.split(";")[1];
        String relic = buttonID.split(";")[2];
        String faction2 = buttonID.split(";")[3];
        String relic2 = buttonID.split(";")[4];
        String revealMsg = player.getRepresentation() + " you chose to swap the relic "
                + Mapper.getRelic(relic).getName() + " with the relic "
                + Mapper.getRelic(relic2).getName();
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), revealMsg);
        if (faction.contains("lanefirRelicReveal")) {
            game.removeStoredValue(faction);
        } else {
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            String msg = p2.getRepresentation() + " your relic "
                    + Mapper.getRelic(relic).getName()
                    + " was swapped via The Venerable, the Lanefir hero, with the relic "
                    + Mapper.getRelic(relic2).getName()
                    + ". Please resolve any necessary effects of this transition.";
            p2.removeRelic(relic);
            p2.addRelic(relic2);
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg);
        }
        if (faction2.contains("lanefirRelicReveal")) {
            game.removeStoredValue(faction2);
        } else {
            Player p2 = game.getPlayerFromColorOrFaction(faction2);
            String msg = p2.getRepresentation() + " your relic "
                    + Mapper.getRelic(relic2).getName()
                    + " was swapped via The Venerable, the Lanefir hero, with the relic "
                    + Mapper.getRelic(relic).getName()
                    + ". Please resolve any necessary effects of this transition.";
            p2.removeRelic(relic2);
            p2.addRelic(relic);
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg);
        }
    }

    @ButtonHandler("axisHeroStep2_")
    public static void resolveAxisHeroStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        String shipOrder = buttonID.split("_")[1];
        String message = player.getRepresentation()
                + ", please choose which player you wish to give the Axis Order to, and so force them to give you a promissory note.";
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("axisHeroStep3_" + shipOrder + "_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray(
                        "axisHeroStep3_" + shipOrder + "_" + p2.getFaction(),
                        p2.getFactionModel().getShortName());
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("axisHeroStep3_")
    public static void resolveAxisHeroStep3(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String shipOrder = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        String message = player.getRepresentation() + " sent "
                + Mapper.getRelic(shipOrder).getName() + " to "
                + p2.getFactionEmojiOrColor()
                + ", who now owes a promissory note in return. Buttons have been sent to the players `#cards-info` thread to resolve.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        player.removeRelic(shipOrder);
        p2.addRelic(shipOrder);
        List<Button> stuffToTransButtons = ButtonHelper.getForcedPNSendButtons(game, player, p2);
        String message2 = p2.getRepresentationUnfogged()
                + " You have been given an Axis Order by Demi-Queen Mdcksssk, the Axis hero, and now must send a promissory note in return."
                + " Please choose the promissory note you wish to send.";
        MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), message2, stuffToTransButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("khraskHeroStep3Ready_")
    public static void resolveKhraskHeroStep3Ready(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (p2.getExhaustedPlanets().isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Chosen player had no exhausted planets. Nothing has been done.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getExhaustedPlanets()) {
            buttons.add(Buttons.gray(
                    "khraskHeroStep4Ready_" + p2.getFaction() + "_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose the planet you wish to ready.",
                buttons);
    }

    @ButtonHandler("khraskHeroStep4Ready_")
    public static void resolveKhraskHeroStep4Ready(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, game);
        ButtonHelper.deleteMessage(event);
        p2.refreshPlanet(planet);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getRepresentationUnfogged() + ", you readied " + planetRep + ".");
        if (p2 != player) {
            MessageHelper.sendMessageToChannel(
                    p2.getCorrectChannel(),
                    p2.getRepresentationUnfogged() + " your planet " + planetRep + " was readied.");
            if (player.hasLeader("xxchaagent")) {
                UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
                if (uH != null && uH.getUnitCount(UnitType.Infantry, p2) > 0) {
                    Tile tile = game.getTileFromPlanet(planet);
                    for (String tilePos : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, true)) {
                        Tile tile2 = game.getTileByPosition(tilePos);
                        if (FoWHelper.playerHasShipsInSystem(player, tile2)) {
                            List<Button> buttons = new ArrayList<>();
                            buttons.add(Buttons.red(
                                    "xxchaAgentRemoveInfantry_" + p2.getFaction() + "_" + planet, "Remove Infantry"));
                            buttons.add(Buttons.gray("deleteButtons", "Decline"));
                            MessageHelper.sendMessageToChannelWithButtons(
                                    player.getCorrectChannel(),
                                    player.getRepresentation()
                                            + " you have the opportunity to remove 1 of the infantry on the readied planet if you wish.",
                                    buttons);

                            return;
                        }
                    }
                }
            }
        }
    }

    public static List<Button> getAttachmentSearchButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        Set<String> types = ButtonHelper.getTypesOfPlanetPlayerHas(game, player);
        for (String type : types) {
            if ("industrial".equals(type) && doesExploreDeckHaveAnAttachmentLeft(type, game)) {
                buttons.add(Buttons.green("findAttachmentInDeck_industrial", "Explore Industrials"));
            }
            if ("cultural".equals(type) && doesExploreDeckHaveAnAttachmentLeft(type, game)) {
                buttons.add(Buttons.blue("findAttachmentInDeck_cultural", "Explore Culturals"));
            }
            if ("hazardous".equals(type) && doesExploreDeckHaveAnAttachmentLeft(type, game)) {
                buttons.add(Buttons.red("findAttachmentInDeck_hazardous", "Explore Hazardous"));
            }
        }
        return buttons;
    }

    @ButtonHandler("attachAttachment_")
    public static void resolveAttachAttachment(
            Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        String planet = buttonID.split("_")[1];
        String attachment = buttonID.replace("attachAttachment_" + planet + "_", "");
        Tile tile = game.getTileFromPlanet(planet);
        PlanetModel planetInfo = Mapper.getPlanet(planet);
        if (Optional.ofNullable(planetInfo).isPresent()) {
            if (!Optional.ofNullable(planetInfo.getTechSpecialties())
                            .orElse(new ArrayList<>())
                            .isEmpty()
                    || ButtonHelper.doesPlanetHaveAttachmentTechSkip(tile, planet)) {
                if ((Constants.WARFARE.equals(attachment)
                        || Constants.PROPULSION.equals(attachment)
                        || Constants.CYBERNETIC.equals(attachment)
                        || Constants.BIOTIC.equals(attachment)
                        || Constants.WEAPON.equals(attachment))) {
                    attachment += "stat";
                }
            }
        }
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        uH.addToken("attachment_" + attachment + ".png");
        String msg = player.getRepresentationUnfogged() + " put " + attachment + " on "
                + Helper.getPlanetRepresentation(planet, game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
    }

    private static List<Button> getAttachmentAttach(Game game, Player player, String type, String attachment) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.toLowerCase().contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            Planet p = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
            if (p != null
                    && (type.equalsIgnoreCase(p.getOriginalPlanetType())
                            || p.getTokenList().contains("attachment_titanspn.png"))) {
                buttons.add(Buttons.green(
                        "attachAttachment_" + planet + "_" + attachment, Helper.getPlanetRepresentation(planet, game)));
            }
        }
        return buttons;
    }

    @ButtonHandler("findAttachmentInDeck_")
    public static void findAttachmentInDeck(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String type = buttonID.split("_")[1];
        int counter = 0;
        StringBuilder sb = new StringBuilder();
        MessageChannel channel = player.getCorrectChannel();
        if (!doesExploreDeckHaveAnAttachmentLeft(type, game)) {
            MessageHelper.sendMessageToChannel(
                    channel, "The " + type + " deck doesn't have any attachments left in it.");
            return;
        } else {
            ButtonHelper.deleteMessage(event);
        }
        while (counter < 40) {
            counter++;
            String cardID = game.drawExplore(type);
            ExploreModel explore = Mapper.getExplore(cardID);
            sb.append(explore.textRepresentation()).append(System.lineSeparator());
            if ("attach".equalsIgnoreCase(explore.getResolution())) {
                sb.append(player.getRepresentation())
                        .append(" Found attachment ")
                        .append(explore.getName());
                game.purgeExplore(cardID);
                MessageHelper.sendMessageToChannel(channel, sb.toString());
                String msg = player.getRepresentation() + ", please choose which planet this should be attached to.";
                MessageHelper.sendMessageToChannelWithButtons(
                        channel,
                        msg,
                        getAttachmentAttach(
                                game, player, type, explore.getAttachmentId().get()));
                return;
            }
        }
    }

    private static boolean doesExploreDeckHaveAnAttachmentLeft(String type, Game game) {
        List<String> deck = game.getExploreDeck(type);
        deck.addAll(game.getExploreDiscard(type));
        for (String cardID : deck) {
            ExploreModel explore = Mapper.getExplore(cardID);
            if ("attach".equalsIgnoreCase(explore.getResolution())) {
                return true;
            }
        }

        return false;
    }

    public static void resolveFlorzenHeroStep1(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (String attachment : getAttachmentsForFlorzenHero(game, player)) {
            String planet = substringBefore(attachment, "_");
            String attach = substringAfter(attachment, "_");
            buttons.add(Buttons.green(
                    "florzenHeroStep2_" + attachment, attach + " on " + Helper.getPlanetRepresentation(planet, game)));
        }
        String msg = player.getRepresentationUnfogged() + ", please choose the attachment you wish to steal.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("florzenHeroStep2_")
    public static void resolveFlorzenHeroStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        buttonID = buttonID.replace("florzenHeroStep2_", "");

        String planet = substringBefore(buttonID, "_");
        String attachment = substringAfter(buttonID, "_");
        List<Button> buttons = new ArrayList<>();
        Tile hs = player.getHomeSystemTile();
        for (UnitHolder uh : hs.getPlanetUnitHolders()) {
            String planet2 = uh.getName();
            buttons.add(Buttons.green(
                    "florzenAgentStep3_" + planet + "_" + planet2 + "_" + attachment,
                    Helper.getPlanetRepresentation(planet2, game)));
        }

        String msg = player.getRepresentationUnfogged()
                + ", please choose which of your home system planets you wish to attach" + attachment + "to.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("tnelisHeroAttach_")
    public static void resolveTnelisHeroAttach(
            Player tnelis, Game game, String buttonID, ButtonInteractionEvent event) {
        String soID = buttonID.split("_")[1];
        Map<String, Integer> customPOs = new HashMap<>(game.getRevealedPublicObjectives());
        for (Map.Entry<String, Integer> entry : customPOs.entrySet()) {
            if (entry.getKey().contains("Tnelis Hero")) {
                game.removeCustomPO(entry.getValue());
                String sb = "Removed Turra Sveyar, the Tnelis hero, from a secret objective.";
                MessageHelper.sendMessageToChannel(tnelis.getCorrectChannel(), sb);
            }
        }
        Integer poIndex = game.addCustomPO(
                "Tnelis Hero (" + Mapper.getSecretObjectivesJustNames().get(soID) + ")", 1);
        String sb = tnelis.getRepresentation() + " Attached Turra Sveyar, the Tnelis hero, to a secret objective ("
                + Mapper.getSecretObjectivesJustNames().get(soID)
                + "). This is represented in the bot as a custom objective (" + poIndex
                + ") and should only be scored by them. This custom objective will be removed/changed automatically if the hero is attached to another secret objective via button.";
        MessageHelper.sendMessageToChannel(tnelis.getCorrectChannel(), sb);
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getTilesToGhotiHeroIn(Player player, Game game) {
        String factionChecker = player.factionButtonChecker();
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(game.getTileMap()).entrySet()) {
            if (FoWHelper.playerHasShipsInSystem(player, tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                Button validTile = Buttons.green(
                        factionChecker + "ghotiHeroIn_" + tileEntry.getKey(),
                        tile.getRepresentationForButtons(game, player));
                buttons.add(validTile);
            }
        }
        Button validTile2 = Buttons.red(factionChecker + "deleteButtons", "Done");
        buttons.add(validTile2);
        return buttons;
    }

    public static List<Button> getUnitsToGlimmersHero(Player player, Tile tile) {
        String factionChecker = player.factionButtonChecker();
        Set<UnitType> allowedUnits = Set.of(
                UnitType.Destroyer,
                UnitType.Cruiser,
                UnitType.Carrier,
                UnitType.Dreadnought,
                UnitType.Flagship,
                UnitType.Warsun,
                UnitType.Fighter);

        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = unitHolder.getUnits();
            if (unitHolder instanceof Planet) continue;

            Map<UnitKey, Integer> tileUnits = new HashMap<>(units);
            for (Map.Entry<UnitKey, Integer> unitEntry : tileUnits.entrySet()) {
                UnitKey unitKey = unitEntry.getKey();
                if (!player.unitBelongsToPlayer(unitKey)) continue;
                if (!allowedUnits.contains(unitKey.unitType())) {
                    continue;
                }
                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                String prettyName = unitModel == null ? unitKey.unitType().humanReadableName() : unitModel.getName();
                String unitName = unitKey.unitName();
                Button validTile2 = Buttons.red(
                        factionChecker + "glimmersHeroOn_" + tile.getPosition() + "_" + unitName,
                        "Duplicate " + prettyName,
                        unitKey.unitEmoji());
                buttons.add(validTile2);
            }
        }
        Button validTile2 = Buttons.red(factionChecker + "deleteButtons", "Decline");
        buttons.add(validTile2);
        return buttons;
    }

    public static List<Button> getTilesToGlimmersHeroIn(Player player, Game game) {
        String factionChecker = player.factionButtonChecker();
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(game.getTileMap()).entrySet()) {
            if (FoWHelper.playerHasShipsInSystem(player, tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                Button validTile = Buttons.green(
                        factionChecker + "glimmersHeroIn_" + tileEntry.getKey(),
                        tile.getRepresentationForButtons(game, player));
                buttons.add(validTile);
            }
        }
        Button validTile2 = Buttons.red(factionChecker + "deleteButtons", "Done");
        buttons.add(validTile2);
        return buttons;
    }

    public static void offerFreeSystemsButtons(Player player, Game game, GenericInteractionCreateEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            Planet planetReal = game.getPlanetsInfo().get(planet);
            boolean oneOfThree = planetReal != null
                    && isNotBlank(planetReal.getOriginalPlanetType())
                    && List.of("industrial", "cultural", "hazardous").contains(planetReal.getOriginalPlanetType());
            if (oneOfThree || planet.contains("custodiavigilia") || planet.contains("ghoti")) {
                buttons.add(
                        Buttons.green("freeSystemsHeroPlanet_" + planet, Helper.getPlanetRepresentation(planet, game)));
            }
        }
        String message = "Please choose the planet on which to use Count Otto P’may, the Free Systems hero.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
    }

    @ButtonHandler("freeSystemsHeroPlanet_")
    public static void freeSystemsHeroPlanet(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String planet = buttonID.split("_")[1];
        Planet unitHolder = game.getPlanetsInfo().get(planet);
        unitHolder.addToken("token_dmz.png");
        unitHolder.removeAllUnits(player.getColor());
        if (player.getExhaustedPlanets().contains(planet)) {
            PlanetService.refreshPlanet(player, planet);
        }
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Attached Count Otto P'may, the Free Systems hero, to " + Helper.getPlanetRepresentation(planet, game));
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getButtonsForGheminaLadyHero(Player player, Game game) {
        String factionChecker = player.factionButtonChecker();
        List<Button> buttons = new ArrayList<>();
        List<Tile> tilesWithBombard = CheckUnitContainmentService.getTilesContainingPlayersUnits(
                game, player, UnitType.Lady, UnitType.Flagship);
        Set<String> adjacentTiles = FoWHelper.getAdjacentTilesAndNotThisTile(
                game, tilesWithBombard.getFirst().getPosition(), player, false);
        for (Tile tile : tilesWithBombard) {
            adjacentTiles.addAll(FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false));
        }
        for (String pos : adjacentTiles) {
            Tile tile = game.getTileByPosition(pos);
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder instanceof Planet planet) {
                    if (!player.getPlanetsAllianceMode().contains(planet.getName())
                            && !tile.isHomeSystem(game)
                            && !planet.getName().toLowerCase().contains("rex")) {
                        buttons.add(Buttons.green(
                                factionChecker + "gheminaLadyHero_" + planet.getName(),
                                Helper.getPlanetRepresentation(planet.getName(), game)));
                    }
                }
            }
        }
        return buttons;
    }

    public static List<Button> getButtonsForGheminaLordHero(Player player, Game game) {
        String factionChecker = player.factionButtonChecker();
        List<Button> buttons = new ArrayList<>();
        List<Tile> tilesWithBombard = CheckUnitContainmentService.getTilesContainingPlayersUnits(
                game, player, UnitType.Lady, UnitType.Flagship);
        Set<String> adjacentTiles = FoWHelper.getAdjacentTilesAndNotThisTile(
                game, tilesWithBombard.getFirst().getPosition(), player, false);
        for (Tile tile : tilesWithBombard) {
            adjacentTiles.addAll(FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false));
        }
        for (String pos : adjacentTiles) {
            Tile tile = game.getTileByPosition(pos);
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder instanceof Planet planet) {
                    Map<UnitKey, Integer> units = unitHolder.getUnits();
                    if (!player.getPlanetsAllianceMode().contains(planet.getName())
                            && !tile.isHomeSystem(game)
                            && !planet.getName().toLowerCase().contains("rex")
                            && (units == null || units.isEmpty())) {
                        buttons.add(Buttons.green(
                                factionChecker + "gheminaLordHero_" + planet.getName(),
                                Helper.getPlanetRepresentation(planet.getName(), game)));
                    }
                }
            }
        }
        return buttons;
    }

    @ButtonHandler("gheminaLadyHero_")
    public static void resolveGheminaLadyHero(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planetName = buttonID.split("_")[1];
        UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planetName, game);
        StringBuilder destroyedUnits = new StringBuilder();
        for (Player p2 : game.getRealPlayers()) {
            destroyedUnits.append(unitHolder.getPlayersUnitListEmojisOnHolder(p2));
        }
        DestroyUnitService.destroyAllUnits(event, game.getTileFromPlanet(planetName), game, unitHolder, false);
        String planetRep = Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
        String msg = player.getFactionEmoji() + " destroyed all units (" + destroyedUnits + ") on the planet "
                + planetRep + " using the The Lady, a Ghemina hero.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("gheminaLordHero_")
    public static void resolveGheminaLordHero(String buttonID, Player player, Game game, ButtonInteractionEvent event) {
        String planetID = buttonID.split("_")[1];
        if ("lockedmallice".equalsIgnoreCase(planetID)) {
            planetID = "mallice";
            Tile tile = game.getTileFromPlanet("lockedmallice");
            FlipTileService.flipTileIfNeeded(event, tile, game);
        }
        AddPlanetService.addPlanet(player, planetID, game, event, false);
        PlanetService.refreshPlanet(player, planetID);
        String planetRep = Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetID, game);
        String msg = player.getFactionEmojiOrColor() + " claimed the planet " + planetRep
                + " using The Lord, a Ghemina hero.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getPossibleTechForVeldyrToGainFromPlayer(Player veldyr, Player victim) {
        List<Button> techToGain = new ArrayList<>();
        List<String> techs = new ArrayList<>();
        for (String tech : victim.getTechs()) {
            TechnologyModel techM = Mapper.getTech(tech);
            if (techM.isUnitUpgrade()) {
                if (!techM.getFaction().orElse("").isEmpty()) {
                    techM = Mapper.getTech(techM.getBaseUpgrade().orElse(tech));
                }
                for (String fTech : veldyr.getFactionTechs()) {
                    TechnologyModel techF = Mapper.getTech(fTech);
                    if (techF.isUnitUpgrade()) {
                        if (techF.getBaseUpgrade().orElse("").equalsIgnoreCase(techM.getAlias())) {
                            techM = techF;
                        }
                    }
                }
                if (!veldyr.getTechs().contains(techM.getAlias()) && !techs.contains(techM.getAlias())) {
                    techToGain.add(Buttons.green("getTech_" + techM.getAlias() + "__noPay", techM.getName()));
                    techs.add(techM.getAlias());
                }
            }
        }
        return techToGain;
    }

    public static List<Button> getSaarHeroButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        List<Tile> tilesUsed = new ArrayList<>();
        for (Tile tile1 :
                CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, UnitType.Spacedock)) {
            for (String tile2Pos : FoWHelper.getAdjacentTilesAndNotThisTile(game, tile1.getPosition(), player, false)) {
                Tile tile2 = game.getTileByPosition(tile2Pos);
                if (!tilesUsed.contains(tile2)) {
                    tilesUsed.add(tile2);
                    buttons.add(Buttons.green(
                            "saarHeroResolution_" + tile2.getPosition(),
                            tile2.getRepresentationForButtons(game, player)));
                }
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));
        return buttons;
    }

    @ButtonHandler("saarHeroResolution_")
    public static void resolveSaarHero(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            for (Player p2 : game.getRealPlayersNNeutral()) {
                if (p2 == player) {
                    continue;
                }
                String name = unitHolder.getName().replace("space", "");
                if (tile.containsPlayersUnits(p2)) {
                    int amountInf = unitHolder.getUnitCount(UnitType.Infantry, p2.getColor());
                    if (amountInf > 0) {
                        DestroyUnitService.destroyUnits(
                                event, tile, game, p2.getColor(), amountInf + " inf " + name, false);
                    }
                    int amountFF = unitHolder.getUnitCount(UnitType.Fighter, p2.getColor());
                    if (amountFF > 0) {
                        DestroyUnitService.destroyUnits(event, tile, game, p2.getColor(), amountFF + " ff", false);
                    }
                    if (amountFF + amountInf > 0 && p2.isRealPlayer()) {
                        MessageHelper.sendMessageToChannel(
                                p2.getCardsInfoThread(),
                                p2.getRepresentation()
                                        + " heads up, a tile with your units in it got Armageddon'd by Gurno Aggero, the Saar hero, destroying all fighters and infantry.");
                    }
                }
            }
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getFactionEmoji() + " destroyed all opposing infantry and fighters in "
                        + tile.getRepresentationForButtons(game, player) + " using Gurno Aggero, the Saar hero.");
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getNekroHeroButtons(Player player, Game game) {
        List<Button> techPlanets = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.containsPlayersUnits(player)) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    if (unitHolder instanceof Planet planetHolder) {
                        String planet = planetHolder.getName();
                        if (ButtonHelper.checkForTechSkips(game, planet)) {
                            techPlanets.add(Buttons.gray(
                                    "nekroHeroStep2_" + planet,
                                    Mapper.getPlanet(planet).getName()));
                        }
                    }
                }
            }
        }
        return techPlanets;
    }

    @ButtonHandler("purgeCeldauriHero_")
    public static void purgeCeldauriHero(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Leader playerLeader = player.unsafeGetLeader("celdaurihero");
        if (playerLeader == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), player.getFactionEmoji() + "You don't have Titus Flavius, the Celdauri hero.");
            return;
        }
        StringBuilder message = new StringBuilder(player.getRepresentation())
                .append(" played ")
                .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = PlayHeroService.removeLeader(game, player, playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), message + " - Titus Flavius, the Celdauri hero, has been purged.");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Titus Flavius, the Celdauri hero, was not purged - something went wrong.");
        }
        player.setTg(player.getCommodities() + player.getTg());
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        Button tgButton = Buttons.red("deleteButtons", "Delete Buttons");
        for (UnitHolder uH : tile.getPlanetUnitHolders()) {
            if (player.getPlanets().contains(uH.getName())) {
                String planet = uH.getName();
                Button sdButton = Buttons.green(
                        "winnuStructure_sd_" + planet,
                        "Place 1 space dock on " + Helper.getPlanetRepresentation(planet, game));
                buttons.add(sdButton);
            }
        }
        buttons.add(tgButton);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " Use buttons to place 1 space dock on a planet you control",
                buttons);
        List<Button> buttons2 = Helper.getPlaceUnitButtons(event, player, game, tile, "celdauriHero", "place");
        String message2 = player.getRepresentation() + " Use the buttons to produce units. ";
        String message3 = "You have " + Helper.getProductionValue(player, game, tile, false)
                + " PRODUCTION value in this system\n";
        if (Helper.getProductionValue(player, game, tile, false) > 0
                && game.playerHasLeaderUnlockedOrAlliance(player, "cabalcommander")) {
            message3 +=
                    ". You also have the That Which Molds Flesh, the Vuil'raith commander, which allows you to produce 2 fighters/infantry that don't count towards PRODUCTION limit.\n";
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), message.toString());
        MessageHelper.sendMessageToChannel(
                event.getChannel(), message3 + ButtonHelper.getListOfStuffAvailableToSpend(player, game, true));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons2);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("purgeMentakHero_")
    public static void purgeMentakHero(Player player, Game game, ButtonInteractionEvent event) {
        Leader playerLeader = player.unsafeGetLeader("mentakhero");
        if (playerLeader == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getFactionEmoji() + "You don't have Ipswitch, Loose Cannon, the Mentak hero.");
            return;
        }
        StringBuilder message = new StringBuilder(player.getRepresentation())
                .append(" played ")
                .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = PlayHeroService.removeLeader(game, player, playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    message + " - Ipswitch, Loose Cannon, the Mentak hero, has been purged.");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Ipswitch, Loose Cannon, the Mentak hero, was not purged - something went wrong.");
        }

        MessageHelper.sendMessageToChannel(event.getChannel(), message.toString());
        game.setStoredValue("mentakHero", player.getFaction());
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("refreshBelkoseaHero")
    public static void refreshBelkoseaHero(Player player, ButtonInteractionEvent event) {
        Leader playerLeader = player.unsafeGetLeader("belkoseahero");
        if (playerLeader == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), player.getFactionEmoji() + "You don't have the Belkosea hero.");
            return;
        }
        String message =
                player.getRepresentation() + " refreshed " + Helper.getLeaderFullRepresentation(playerLeader) + ".";
        playerLeader.setExhausted(false);

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("exhaustBelkoseaHero")
    public static void exhaustBelkoseaHero(Player player, ButtonInteractionEvent event) {
        Leader playerLeader = player.unsafeGetLeader("belkoseahero");
        if (playerLeader == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), player.getFactionEmoji() + "You don't have the Belkosea hero.");
            return;
        }
        String message =
                player.getRepresentation() + " exhausted " + Helper.getLeaderFullRepresentation(playerLeader) + ".";
        playerLeader.setExhausted(true);

        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                message
                        + "\nYou will have to use the \"Assign Hits\" button when ANTI-FIGHTER BARRAGE is rolled, since the bot will not know how to auto-assign the produced hits.");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("purgeTech_")
    public static void purgeTech(Player player, ButtonInteractionEvent event, String buttonID) {
        String techID = buttonID.replace("purgeTech_", "");
        player.purgeTech(techID);
        String msg = player.getRepresentationUnfogged() + " purged _"
                + Mapper.getTech(techID).getName() + "_.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("dwsHeroPurge_")
    public static void dwsHeroPurge(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String techID = buttonID.replace("dwsHeroPurge_", "");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " purged "
                        + Mapper.getTech(techID).getName()
                        + " with _Wave Function Collapse_. Those who owned this can now research a replacement technology.");
        for (Player p2 : game.getRealPlayers()) {
            String msg = p2.getRepresentationUnfogged() + ", "
                    + Mapper.getTech(techID).getName() + " was purged with _Wave Function Collapse_.";
            if (p2.getTechs().contains(techID)) {
                msg += " You can (and must) now research a replacement technology.";
                ButtonHelperActionCards.resolveResearch(game, p2, event);
            }
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg);
            p2.purgeTech(techID);
        }

        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getCabalHeroButtons(Player player, Game game) {
        String factionChecker = player.factionButtonChecker();
        List<Button> empties = new ArrayList<>();

        List<Tile> tiles = new ArrayList<>();
        if (game.isTwilightsFallMode()) {
            for (Tile tile : game.getTileMap().values()) {
                if (tile.isGravityRift(game)) {
                    tiles.add(tile);
                }
            }
        } else {
            for (Player p : game.getRealPlayers()) {
                if (p.hasTech("dt2")
                        || p.getUnitsOwned().contains("cabal_spacedock")
                        || p.getUnitsOwned().contains("cabal_spacedock2")
                        || p.hasTech("absol_dt2")
                        || p.getUnitsOwned().contains("absol_cabal_spacedock")
                        || p.getUnitsOwned().contains("absol_cabal_spacedock2")) {
                    tiles.addAll(
                            CheckUnitContainmentService.getTilesContainingPlayersUnits(game, p, UnitType.Spacedock));
                }
            }
        }

        List<Tile> adjTiles = new ArrayList<>();
        if (RiftSetModeService.isActive(game)) {
            tiles = RiftSetModeService.getAllTilesWithRift(game);
            adjTiles.addAll(tiles);
        }
        for (Tile tile : tiles) {
            for (String pos : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, true)) {
                Tile tileToAdd = game.getTileByPosition(pos);
                if (!tileToAdd.getTileModel().isHyperlane()
                        && !adjTiles.contains(tileToAdd)
                        && FoWHelper.otherPlayersHaveShipsInSystem(player, tileToAdd, game)) {
                    adjTiles.add(tileToAdd);
                }
            }
        }

        for (Tile tile : adjTiles) {
            empties.add(Buttons.blue(
                    factionChecker + "cabalHeroTile_" + tile.getPosition(),
                    "Roll For Units In " + tile.getRepresentationForButtons(game, player)));
        }
        SortHelper.sortButtonsByTitle(empties);
        return empties;
    }

    public static List<Button> getEmpyHeroButtons(Player player, Game game) {
        String factionChecker = player.factionButtonChecker();
        var frontierTokenId = Mapper.getTokenID(Constants.FRONTIER);
        return game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.playerHasShipsInSystem(player, tile))
                .filter(tile ->
                        tile.getUnitHolders().get("space").getTokenList().contains(frontierTokenId))
                .map(tile -> Buttons.blue(
                        factionChecker + "exploreFront_" + tile.getPosition(),
                        "Explore " + tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    @ButtonHandler("naaluHeroSend")
    public static void resolveNaaluHeroSend(Player p1, Game game, String buttonID, ButtonInteractionEvent event) {
        buttonID = buttonID.replace("naaluHeroSend_", "");
        String factionToTrans = buttonID.substring(0, buttonID.indexOf('_'));
        String amountToTrans = buttonID.substring(buttonID.indexOf('_') + 1);
        Player p2 = game.getPlayerFromColorOrFaction(factionToTrans);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(
                    p1.getCorrectChannel(), "Could not resolve second player, please resolve manually.");
            return;
        }
        String message2;
        String id = null;
        int pnIndex;
        pnIndex = Integer.parseInt(amountToTrans);
        for (Map.Entry<String, Integer> pn : p1.getPromissoryNotes().entrySet()) {
            if (pn.getValue().equals(pnIndex)) {
                id = pn.getKey();
            }
        }
        if (id == null) {
            MessageHelper.sendMessageToChannel(
                    p1.getCorrectChannel(), "Could not resolve promissory note, so no promissory note was sent.");
            return;
        }
        p1.removePromissoryNote(id);
        p2.setPromissoryNote(id);
        if (id.contains("dspnveld") && !p2.getAllianceMembers().contains(p1.getFaction())) {
            PromissoryNoteHelper.resolvePNPlay(id, p2, game, event);
        }
        boolean sendSftT = false;
        boolean sendAlliance = false;
        String promissoryNoteOwner = Mapper.getPromissoryNote(id).getOwner();
        if ((id.endsWith("_sftt") || id.endsWith("_an"))
                && !promissoryNoteOwner.equals(p2.getFaction())
                && !promissoryNoteOwner.equals(p2.getColor())
                && !p2.isPlayerMemberOfAlliance(game.getPlayerFromColorOrFaction(promissoryNoteOwner))) {
            p2.addPromissoryNoteToPlayArea(id);
            if (id.endsWith("_sftt")) {
                sendSftT = true;
            } else {
                sendAlliance = true;
            }
        }
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, p1, false);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, p2, false);
        if (sendSftT || sendAlliance) {
            String text = sendSftT ? "_Support for the Throne_" : "_Alliance_";
            message2 = p1.getRepresentation() + " sent " + text + " directly to the play area of ";
        } else {
            message2 = p1.getRepresentation() + " sent a promissory note to the hand of ";
        }
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), message2 + p2.getRepresentation() + ".");
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(p1.getCorrectChannel(), message2 + p2.getColorIfCanSeeStats(p1));
        }
        ButtonHelper.deleteMessage(event);
        Helper.checkEndGame(game, p2);
    }

    public static void resolveNivynHeroSustainEverything(Game game, Player nivyn) {
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                Map<UnitKey, Integer> units = unitHolder.getUnits();
                for (Player player : game.getRealPlayers()) {
                    for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                        if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                        UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                        if (unitModel == null) continue;
                        UnitKey unitKey = unitEntry.getKey();
                        int damagedUnits = 0;
                        if (unitHolder.getUnitDamage() != null
                                && unitHolder.getUnitDamage().get(unitKey) != null) {
                            damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                        }
                        int totalUnits = unitEntry.getValue() - damagedUnits;
                        if (totalUnits > 0
                                && unitModel.getSustainDamage()
                                && (player != nivyn || !"mech".equalsIgnoreCase(unitModel.getBaseType()))) {
                            tile.addUnitDamage(unitHolder.getName(), unitKey, totalUnits);
                        }
                    }
                }
            }
        }
    }

    public static void offerStealRelicButtons(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        String faction = buttonID.split("_")[1];
        Player victim = game.getPlayerFromColorOrFaction(faction);
        List<Button> buttons = new ArrayList<>();
        for (String relic : victim.getRelics()) {
            buttons.add(Buttons.green(
                    "stealRelic_" + victim.getFaction() + "_" + relic,
                    "Steal " + Mapper.getRelic(relic).getName()));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        String msg = player.getRepresentationUnfogged() + ", please choose the relic you wish to steal.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("stealRelic_")
    public static void stealRelic(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        String faction = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        String relic = buttonID.replace("stealRelic_" + faction + "_", "");
        String msg = player.getFactionEmojiOrColor() + " stole "
                + Mapper.getRelic(relic).getName() + " from " + p2.getFactionEmojiOrColor();
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg);
        }
        boolean exhausted = p2.getExhaustedRelics().contains(relic);
        p2.removeRelic(relic);
        player.addRelic(relic);
        if (exhausted) {
            p2.removeExhaustedRelic(relic);
            player.addExhaustedRelic(relic);
        }

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
    }

    public static void resolvDihmohnHero(Game game) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        UnitHolder unitHolder = tile.getUnitHolders().get("space");
        Map<UnitKey, Integer> units = unitHolder.getUnits();
        for (Player player : game.getRealPlayers()) {
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;
                UnitKey unitKey = unitEntry.getKey();
                int damagedUnits = 0;
                if (unitHolder.getUnitDamage() != null
                        && unitHolder.getUnitDamage().get(unitKey) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                }
                int totalUnits = unitEntry.getValue() - damagedUnits;
                if (totalUnits > 0 && unitModel.getIsShip()) {
                    tile.addUnitDamage(unitHolder.getName(), unitKey, totalUnits);
                }
            }
        }
    }

    @ButtonHandler("augerHeroSwap.")
    public static void augersHeroSwap(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        buttonID = buttonID.replace("augerHeroSwap.", "");
        String id = substringAfter(buttonID, ".");
        String num = substringBefore(buttonID, ".");
        if ("1".equalsIgnoreCase(num)) {
            game.swapPublicObjectiveOut(1, 0, id);
        } else {
            game.swapPublicObjectiveOut(2, 0, id);
        }
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " put "
                        + Mapper.getPublicObjective(id).getName()
                        + " as next up. Feel free to peek at it to confirm it worked");
        // GameSaveLoadManager.saveMap(game, event);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("augersHeroStart_")
    public static void augersHeroResolution(Player player, Game game, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        if ("1".equalsIgnoreCase(buttonID.split("_")[1])) {
            if (game.getPublicObjectives1Peekable().isEmpty()) {
                game.setUpPeekableObjectives(1, 1);
            }
            for (int x = 0; x < 3; x++) {
                String obj = game.getTopPublicObjective(1);
                PublicObjectiveModel po = Mapper.getPublicObjective(obj);
                buttons.add(Buttons.green("augerHeroSwap.1." + obj, "Put " + po.getName() + " As The Next Objective"));
            }
        } else {
            for (int x = 0; x < 3; x++) {
                String obj = game.getTopPublicObjective(2);
                PublicObjectiveModel po = Mapper.getPublicObjective(obj);
                buttons.add(Buttons.green("augerHeroSwap.2." + obj, "Put " + po.getName() + " As The Next Objective"));
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Decline to change the next objective"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(), player.getRepresentationUnfogged() + " use buttons to resolve", buttons);
    }

    @ButtonHandler("poisonHeroInitiation")
    public static void resolvePoisonHeroInitiation(Player player, Game game, ButtonInteractionEvent event) {
        Leader playerLeader = player.unsafeGetLeader("poisonhero");
        StringBuilder message2 = new StringBuilder(player.getRepresentation())
                .append(" played ")
                .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = PlayHeroService.removeLeader(game, player, playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), message2 + " - The Oracle, the Poison hero, has been purged. \n\n ");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "The Oracle, the Poison hero, was not purged - something went wrong");
        }
        ButtonHelperTwilightsFallActionCards.resolvePoison(game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("kyroHeroInitiation")
    public static void resolveKyroHeroInitiation(Player player, Game game, ButtonInteractionEvent event) {
        Leader playerLeader = player.unsafeGetLeader("kyrohero");
        StringBuilder message2 = new StringBuilder(player.getRepresentation())
                .append(" played ")
                .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = PlayHeroService.removeLeader(game, player, playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), message2 + " - Speygh, the Kyro hero, has been purged. \n\n");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Speygh, the Kyro hero, was not purged - something went wrong");
        }
        int dieResult = player.getLowestSC();
        game.setStoredValue("kyroHeroSC", dieResult + "");
        game.setStoredValue("kyroHeroPlayer", player.getFaction());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                Helper.getSCName(dieResult, game) + ", has been marked with Speygh, the Kyro hero"
                        + (game.isFrankenGame()
                                ? ", and the faction that played the hero as " + player.getFaction()
                                : "")
                        + ".");
        ButtonHelper.deleteMessage(event);
    }

    public static void offerOlradinHeroFlips(Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("olradinHeroFlip_people", "People Policy"));
        buttons.add(Buttons.green("olradinHeroFlip_environment", "Environment Policy"));
        buttons.add(Buttons.green("olradinHeroFlip_economy", "Economy Policy"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        String msg = player.getRepresentation() + " you may flip one policy.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("olradinHeroFlip_")
    public static void olradinHeroFlipPolicy(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        int positivePolicies = 0;
        String policy = buttonID.split("_")[1];
        // go through each option and set the policy accordingly
        String msg = player.getRepresentation() + " ";
        if ("people".equalsIgnoreCase(policy)) {
            if (player.hasAbility("policy_the_people_connect")) {
                player.removeAbility("policy_the_people_connect");
                msg += "removed _Policy - The People: Connect ➕_ and added _Policy - The People: Control ➖_.";
                player.addAbility("policy_the_people_control");
            } else if (player.hasAbility("policy_the_people_control")) {
                player.removeAbility("policy_the_people_control");
                msg += "removed _Policy - The People: Control ➖_ and added _Policy - The People: Connect ➕_.";
                player.addAbility("policy_the_people_connect");
            }
        }
        if ("environment".equalsIgnoreCase(policy)) {
            if (player.hasAbility("policy_the_environment_preserve")) {
                player.removeAbility("policy_the_environment_preserve");
                msg +=
                        "removed _Policy - The Environment: Preserve ➕_ and added _Policy - The Environment: Plunder ➖_.";
                player.addAbility("policy_the_environment_plunder");
            } else if (player.hasAbility("policy_the_environment_plunder")) {
                player.removeAbility("policy_the_environment_plunder");
                msg +=
                        "removed _Policy - The Environment: Plunder ➖_ and added _Policy - The Environment: Preserve ➕_.";
                player.addAbility("policy_the_environment_preserve");
            }
        }
        if ("economy".equalsIgnoreCase(policy)) {
            if (player.hasAbility("policy_the_economy_empower")) {
                player.removeAbility("policy_the_economy_empower");
                msg += "removed _Policy - The Economy: Empower ➕_";
                player.addAbility("policy_the_economy_exploit");
                msg +=
                        " and added _Policy - The Economy: Exploit ➖_. Decreased commodities total by 1 (double check the value is correct).";
            } else if (player.hasAbility("policy_the_economy_exploit")) {
                player.removeAbility("policy_the_economy_exploit");
                msg += "removed _Policy - The Economy: Exploit ➖_";
                player.addAbility("policy_the_economy_empower");
                msg += " and added _Policy - The Economy: Empower ➕_.";
            }
        }
        player.removeOwnedUnitByID("olradin_mech");
        player.removeOwnedUnitByID("olradin_mech_positive");
        player.removeOwnedUnitByID("olradin_mech_negative");
        String unitModelID;
        if (!player.hasAbility("policy_the_economy_exploit")) {
            positivePolicies++;
        }
        if (!player.hasAbility("policy_the_environment_plunder")) {
            positivePolicies++;
        }
        if (player.hasAbility("policy_the_people_connect")) {
            positivePolicies++;
        }
        if (positivePolicies >= 2) {
            unitModelID = "olradin_mech_positive";
        } else {
            unitModelID = "olradin_mech_negative";
        }
        player.addOwnedUnitByID(unitModelID);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        DiscordantStarsHelper.checkOlradinMech(game);
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getGhostHeroTilesStep1(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getPosition().contains("t")
                    || tile.getPosition().contains("b")
                    || tile.getPosition().contains("frac")) {
                continue;
            }
            if (FoWHelper.doesTileHaveWHs(game, tile.getPosition()) || FoWHelper.playerHasUnitsInSystem(player, tile)) {
                buttons.add(Buttons.gray(
                        "creussHeroStep1_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }

    public static List<Button> getJolNarHeroSwapOutOptions(Player player) {
        String factionChecker = player.factionButtonChecker();
        List<Button> buttons = new ArrayList<>();
        for (String tech : player.getTechs()) {
            TechnologyModel techM = Mapper.getTech(tech);
            if (!techM.isUnitUpgrade()) {
                buttons.add(Buttons.gray(factionChecker + "jnHeroSwapOut_" + tech, techM.getName()));
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Done resolving"));
        return buttons;
    }

    public static List<Button> getBenediction1stTileOptions(Player player, Game game) {
        String factionChecker = player.factionButtonChecker();
        List<Button> buttons = new ArrayList<>();
        for (Tile tile1 : game.getTileMap().values()) {
            String pos1 = tile1.getPosition();
            for (Player p2 : game.getRealPlayers()) {
                if (FoWHelper.playerHasShipsInSystem(p2, tile1)) {
                    boolean adjacentPeeps = false;
                    for (String pos2 : FoWHelper.getAdjacentTiles(game, pos1, p2, false)) {
                        if (pos1.equalsIgnoreCase(pos2)) {
                            continue;
                        }
                        Tile tile2 = game.getTileByPosition(pos2);
                        if (FoWHelper.otherPlayersHaveShipsInSystem(p2, tile2, game)) {
                            adjacentPeeps = true;
                        }
                    }
                    if (adjacentPeeps) {
                        buttons.add(Buttons.gray(
                                factionChecker + "benedictionStep1_" + pos1,
                                tile1.getRepresentationForButtons(game, player)));
                    }
                    break;
                }
            }
        }
        BlindSelectionService.filterForBlindPositionSelection(
                game, player, buttons, factionChecker + "benedictionStep1");
        return buttons;
    }

    public static void startVadenHero(Game game, Player vaden) {
        List<Button> buttons = new ArrayList<>();

        for (Player target : game.getRealPlayers()) {
            if (vaden.getDebtTokenCount(target.getColor(), Constants.VADEN_DEBT_POOL) > 0) {
                Button button;
                String prefix = "vadenHeroClearDebt";
                String faction = target.getFaction();
                if (!game.isFowMode() && !faction.contains("franken")) {
                    button = Buttons.gray(
                            prefix + "_" + faction, target.getFactionModel().getShortName());
                    button = button.withEmoji(Emoji.fromFormatted(target.getFactionEmoji()));
                } else {
                    button = Buttons.gray(prefix + "_" + target.getColor(), target.getColor());
                }
                buttons.add(button);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(vaden.getCorrectChannel(), "Use buttons to resolve", buttons);
    }

    @ButtonHandler("vadenHeroClearDebt")
    public static void vadenHeroClearDebt(Game game, Player vaden, ButtonInteractionEvent event, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        if (target.getTg() > 0) {
            buttons.add(Buttons.green("sendVadenHeroSomething_" + vaden.getFaction() + "_tg", "Send 1 Trade Good"));
        }
        if (target.getCommodities() > 1) {
            buttons.add(Buttons.gray("sendVadenHeroSomething_" + vaden.getFaction() + "_comms", "Send 2 Commodities"));
        }
        buttons.add(Buttons.red("sendVadenHeroSomething_" + vaden.getFaction() + "_pn", "Send 1 Promissory Note"));
        vaden.clearDebt(target, 1, Constants.VADEN_DEBT_POOL);
        MessageHelper.sendMessageToChannel(
                vaden.getCorrectChannel(),
                vaden.getRepresentation() + " returned 1 debt tokens owned by " + target.getRepresentation(false, true)
                        + ", from their \"Shark Loans\" pool, using Putriv Sirvonsk, the Vaden hero."
                        + " Buttons have been sent to their `#cards-info` thread to resolve.");
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCardsInfoThread(),
                target.getRepresentationUnfogged()
                        + ", please choose something to give due to Putriv Sirvonsk, the Vaden hero,"
                        + " returning one of your tokens (\"your kneecaps\" are not an option).",
                buttons);
        if (vaden.getDebtTokenCount(target.getColor(), Constants.VADEN_DEBT_POOL) == 0) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        }
    }

    @ButtonHandler("sendVadenHeroSomething_")
    public static void sendVadenHeroSomething(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String tgOrComm = buttonID.split("_")[2];
        Player vaden = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        ButtonHelper.deleteMessage(event);

        String msg = player.getRepresentation(false, true) + " sent ";
        if ("tg".equalsIgnoreCase(tgOrComm)) {
            msg += " 1 trade good to " + vaden.getRepresentation(false, true)
                    + " as a result of playing Putriv Sirvonsk, the Vaden Hero.";
            if (player.getTg() > 0) {
                vaden.setTg(vaden.getTg() + 1);
                player.setTg(player.getTg() - 1);
            } else {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        player.getRepresentationUnfogged()
                                + " you had no trade goods to send, so no no trade goods have been sent.");
                return;
            }
        } else {
            if ("comms".equalsIgnoreCase(tgOrComm)) {
                msg += " 2 commodities to " + vaden.getRepresentation(false, true)
                        + " as a result of playing Putriv Sirvonsk, the Vaden Hero.";
                if (player.getCommodities() > 1) {
                    vaden.setTg(vaden.getTg() + 2);
                    player.setCommodities(player.getCommodities() - 2);
                } else {
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(),
                            player.getRepresentationUnfogged()
                                    + " you didn't have 2 commodities to send, so no commodities were sent.");
                    return;
                }
            } else {
                msg = player.getRepresentation(false, true)
                        + " will send 1 promissory note as a result of playing Putriv Sirvonsk, the Vaden Hero.";
                List<Button> stuffToTransButtons = ButtonHelper.getForcedPNSendButtons(game, vaden, player);
                String message = player.getRepresentationUnfogged()
                        + ", please choose the promissory you are obligated to send.";
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(), message, stuffToTransButtons);
            }
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(vaden.getCorrectChannel(), msg);
        }
    }

    public static void killShipsSardakkHero(Player player, Game game, ButtonInteractionEvent event) {
        String pos1 = game.getActiveSystem();
        Tile tile1 = game.getTileByPosition(pos1);
        for (Map.Entry<String, UnitHolder> entry : tile1.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
            if (unitHolder instanceof Planet) continue;
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;

                UnitKey unitKey = unitEntry.getKey();
                int totalUnits = unitEntry.getValue();
                if (unitKey.unitType() != UnitType.Infantry && unitKey.unitType() != UnitType.Mech) {
                    var parsedUnit = new ParsedUnit(unitKey, totalUnits, Constants.SPACE);
                    RemoveUnitService.removeUnit(event, tile1, game, parsedUnit);
                }
            }
        }
    }

    public static void checkForMykoHero(Game game, String hero, Player player) {
        if (player.hasLeaderUnlocked("mykomentorihero")) {
            List<Leader> leaders = new ArrayList<>(player.getLeaders());
            for (Leader leader : leaders) {
                if (leader.getId().contains("hero")) {
                    player.removeLeader(leader);
                }
            }
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("mykoheroSteal_" + hero, "Copy " + hero));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        for (Player p2 : game.getRealPlayers()) {
            if (p2.hasLeaderUnlocked("mykomentorihero")) {
                String msg = p2.getRepresentationUnfogged()
                        + " you have the opportunity to use Coprinus Comatus, the Myko-Mentori hero, to grab the ability of the hero "
                        + hero
                        + ". Use buttons to resolve";
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg, buttons);
            }
        }
    }

    @ButtonHandler("mykoheroSteal_")
    public static void resolveMykoHero(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String hero = buttonID.split("_")[1];
        PlayHeroService.playHero(event, game, player, player.unsafeGetLeader("mykomentorihero"));
        player.addLeader(hero);
        UnlockLeaderService.unlockLeader(hero, game, player);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), FrankenLeaderService.getAddLeaderText(player, hero));
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveBentorHero(Game game, Player player) {
        for (String planet : player.getPlanetsAllianceMode()) {
            Planet unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
            if (unitHolder == null) {
                continue;
            }
            Planet planetReal = unitHolder;
            List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(game, planetReal, player);
            if (buttons != null && !buttons.isEmpty()) {
                String message = "Click button to explore " + Helper.getPlanetRepresentation(planet, game);
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
            }
        }
    }

    public static void resolveToldarHero(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        String message =
                player.getRepresentation() + ", please choose the objective that you wish to attach your hero to."
                        + " You will draw 1 secret objective whenever another player scores it, and the player(s) with the fewest victory points will not need to meet its requirements in order to score it.";
        for (String obj : game.getRevealedPublicObjectives().keySet()) {
            if (Mapper.getPublicObjective(obj) != null) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + "toldarHero_"
                                + Mapper.getPublicObjective(obj).getName(),
                        Mapper.getPublicObjective(obj).getName()));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("toldarHero_")
    public static void toldarHero(Game game, Player player, String buttonID) {
        String obj = buttonID.replace("toldarHero_", "");
        game.setStoredValue("toldarHeroObj", obj);
        game.setStoredValue("toldarHeroPlayer", player.getFaction());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getRepresentation() + " successfully attached hero to " + obj + ".");
    }

    public static List<Button> getWinnuHeroSCButtons(Game game) {
        List<Button> scButtons = new ArrayList<>();
        for (Integer sc : game.getSCList()) {
            if (sc <= 0) continue; // some older games have a 0 in the list of SCs
            Button button;
            String label = Helper.getSCName(sc, game);
            TI4Emoji scEmoji = CardEmojis.getSCBackFromInteger(sc);
            if (scEmoji != CardEmojis.SCBackBlank && !game.isHomebrewSCMode()) {
                button = Buttons.gray("winnuHero_" + sc, label, scEmoji);
            } else {
                button = Buttons.gray("winnuHero_" + sc, sc + " " + label);
            }
            scButtons.add(button);
        }
        return scButtons;
    }

    public static List<Button> getNRAHeroButtons(Game game) {
        List<Button> scButtons = new ArrayList<>();
        if (game.getScPlayed().get(1) == null || !game.getScPlayed().get(1)) {
            scButtons.add(Buttons.green("leadershipGenerateCCButtons", "Spend & Gain Command Tokens"));
            // scButtons.add(Buttons.red("leadershipExhaust", "Exhaust Planets"));
        }
        if (game.getScPlayed().get(2) == null || !game.getScPlayed().get(2)) {
            scButtons.add(Buttons.green("diploRefresh2", "Ready 2 Planets"));
        }
        if (game.getScPlayed().get(3) == null || !game.getScPlayed().get(3)) {
            scButtons.add(Buttons.gray("draw2 AC", "Draw 2 Action Cards", CardEmojis.getACEmoji(game)));
        }
        if (game.getScPlayed().get(4) == null || !game.getScPlayed().get(4)) {
            scButtons.add(Buttons.green("construction_spacedock", "Place 1 Space Dock", UnitEmojis.spacedock));
            scButtons.add(Buttons.green("construction_pds", "Place 1 PDS", UnitEmojis.pds));
        }
        if (game.getScPlayed().get(5) == null || !game.getScPlayed().get(5)) {
            scButtons.add(Buttons.gray("sc_refresh", "Replenish Commodities", MiscEmojis.comm));
        }
        if (game.getScPlayed().get(6) == null || !game.getScPlayed().get(6)) {
            scButtons.add(Buttons.green("warfareBuild", "Build At Home"));
        }
        if (game.getScPlayed().get(7) == null || !game.getScPlayed().get(7)) {
            scButtons.add(Buttons.GET_A_TECH);
        }
        if (game.getScPlayed().get(8) == null || !game.getScPlayed().get(8)) {
            scButtons.add(Buttons.gray("non_sc_draw_so", "Draw Secret Objective", CardEmojis.SecretObjective));
        }
        scButtons.add(Buttons.red("deleteButtons", "Done resolving"));

        return scButtons;
    }

    public static List<Button> getSecondaryButtons(Game game) {
        List<Button> scButtons = new ArrayList<>();
        scButtons.add(Buttons.green("leadershipGenerateCCButtons", "Spend & Gain Command Tokens"));
        // scButtons.add(Buttons.red("leadershipExhaust", "Exhaust Planets"));

        scButtons.add(Buttons.green("diploRefresh2", "Ready 2 Planets"));

        scButtons.add(Buttons.gray("draw2 AC", "Draw 2 Action Cards", CardEmojis.getACEmoji(game)));

        scButtons.add(Buttons.green("construction_spacedock", "Place 1 space dock", UnitEmojis.spacedock));
        scButtons.add(Buttons.green("construction_pds", "Place 1 PDS", UnitEmojis.pds));

        scButtons.add(Buttons.gray("sc_refresh", "Replenish Commodities", MiscEmojis.comm));

        scButtons.add(Buttons.green("warfareBuild", "Build At Home"));

        scButtons.add(Buttons.GET_A_TECH);

        scButtons.add(Buttons.gray("non_sc_draw_so", "Draw Secret Objective", CardEmojis.SecretObjective));

        scButtons.add(Buttons.red("deleteButtons", "Done resolving"));

        return scButtons;
    }
}
