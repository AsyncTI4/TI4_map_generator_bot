package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.agenda.DrawAgenda;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsac.SentACRandom;
import ti4.commands.cardsso.SOInfo;
import ti4.commands.explore.ExpFrontier;
import ti4.commands.special.NaaluCommander;
import ti4.commands.tokens.AddCC;
import ti4.commands.tokens.RemoveCC;
import ti4.commands.units.AddUnits;
import ti4.commands.units.MoveUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.ExploreModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;

public class ButtonHelperActionCards {

    public static List<Button> getTilesToScuttle(Player player, Game game, GenericInteractionCreateEvent event,
        int tgAlready) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(game.getTileMap()).entrySet()) {
            if (FoWHelper.playerHasShipsInSystem(player, tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                Button validTile = Button.success(finChecker + "scuttleIn_" + tileEntry.getKey() + "_" + tgAlready,
                    tile.getRepresentationForButtons(game, player));
                buttons.add(validTile);
            }
        }
        Button validTile2 = Button.danger(finChecker + "deleteButtons", "Decline");
        buttons.add(validTile2);
        return buttons;
    }

    public static List<Button> getTilesToLuckyShot(Player player, Game game,
        GenericInteractionCreateEvent event) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(game.getTileMap()).entrySet()) {
            if (FoWHelper.otherPlayersHaveShipsInSystem(player, tileEntry.getValue(), game)
                && FoWHelper.playerHasPlanetsInSystem(player, tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                UnitHolder space = tile.getUnitHolders().get("space");
                boolean rightKindPresent = false;
                for (Player p2 : game.getRealPlayers()) {
                    if (space.getUnitCount(UnitType.Dreadnought, p2.getColor()) > 0
                        || space.getUnitCount(UnitType.Cruiser, p2.getColor()) > 0
                        || space.getUnitCount(UnitType.Destroyer, p2.getColor()) > 0) {
                        rightKindPresent = true;
                    }
                }
                if (rightKindPresent) {
                    Button validTile = Button.success(finChecker + "luckyShotIn_" + tileEntry.getKey(),
                        tile.getRepresentationForButtons(game, player));
                    buttons.add(validTile);
                }

            }
        }
        return buttons;
    }

    public static List<Button> getUnitsToScuttle(Player player, Game game, GenericInteractionCreateEvent event,
        Tile tile, int tgAlready) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        Set<UnitType> allowedUnits = Set.of(UnitType.Destroyer, UnitType.Cruiser, UnitType.Carrier,
            UnitType.Dreadnought, UnitType.Flagship, UnitType.Warsun);

        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = unitHolder.getUnits();
            if (unitHolder instanceof Planet)
                continue;

            Map<UnitKey, Integer> tileUnits = new HashMap<>(units);
            for (Map.Entry<UnitKey, Integer> unitEntry : tileUnits.entrySet()) {
                UnitKey unitKey = unitEntry.getKey();
                if (!player.unitBelongsToPlayer(unitKey))
                    continue;

                if (!allowedUnits.contains(unitKey.getUnitType())) {
                    continue;
                }

                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                String prettyName = unitModel == null ? unitKey.getUnitType().humanReadableName() : unitModel.getName();
                String unitName = unitKey.unitName();
                int totalUnits = unitEntry.getValue();
                int damagedUnits = 0;

                if (unitHolder.getUnitDamage() != null) {
                    damagedUnits = unitHolder.getUnitDamage().getOrDefault(unitKey, 0);
                }

                EmojiUnion emoji = Emoji.fromFormatted(unitKey.unitEmoji());
                for (int x = 1; x < damagedUnits + 1 && x < 2; x++) {
                    String buttonID = finChecker + "scuttleOn_" + tile.getPosition() + "_" + unitName + "damaged" + "_"
                        + tgAlready;
                    Button validTile2 = Button.danger(buttonID, "Remove A Damaged " + prettyName);
                    validTile2 = validTile2.withEmoji(emoji);
                    buttons.add(validTile2);
                }
                totalUnits = totalUnits - damagedUnits;
                for (int x = 1; x < totalUnits + 1 && x < 2; x++) {
                    Button validTile2 = Button.danger(
                        finChecker + "scuttleOn_" + tile.getPosition() + "_" + unitName + "_" + tgAlready,
                        "Remove " + x + " " + prettyName);
                    validTile2 = validTile2.withEmoji(emoji);
                    buttons.add(validTile2);
                }
            }
        }
        return buttons;
    }

    public static List<Button> getUnitsToLuckyShot(Player player, Game game, GenericInteractionCreateEvent event,
        Tile tile) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        Set<UnitType> allowedUnits = Set.of(UnitType.Destroyer, UnitType.Cruiser, UnitType.Dreadnought);

        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = unitHolder.getUnits();
            if (unitHolder instanceof Planet)
                continue;

            Map<UnitKey, Integer> tileUnits = new HashMap<>(units);
            for (Map.Entry<UnitKey, Integer> unitEntry : tileUnits.entrySet()) {
                UnitKey unitKey = unitEntry.getKey();
                if (player.unitBelongsToPlayer(unitKey))
                    continue;

                if (!allowedUnits.contains(unitKey.getUnitType())) {
                    continue;
                }
                Player p2 = game.getPlayerFromColorOrFaction(unitKey.getColor());
                if (p2 == null) {
                    continue;
                }

                UnitModel unitModel = p2.getUnitFromUnitKey(unitKey);
                String prettyName = unitModel == null ? unitKey.getUnitType().humanReadableName() : unitModel.getName();
                String unitName = unitKey.unitName();
                int totalUnits = unitEntry.getValue();
                int damagedUnits = 0;

                if (unitHolder.getUnitDamage() != null) {
                    damagedUnits = unitHolder.getUnitDamage().getOrDefault(unitKey, 0);
                }

                EmojiUnion emoji = Emoji.fromFormatted(unitKey.unitEmoji());
                for (int x = 1; x < damagedUnits + 1 && x < 2; x++) {
                    String buttonID = finChecker + "luckyShotOn_" + tile.getPosition() + "_" + unitName + "damaged"
                        + "_" + unitKey.getColor();
                    Button validTile2 = Button.danger(buttonID, "Destroy A Damaged " + prettyName);
                    validTile2 = validTile2.withEmoji(emoji);
                    buttons.add(validTile2);
                }
                totalUnits = totalUnits - damagedUnits;
                for (int x = 1; x < totalUnits + 1 && x < 2; x++) {
                    Button validTile2 = Button.danger(finChecker + "luckyShotOn_" + tile.getPosition() + "_" + unitName
                        + "_" + unitKey.getColor(), "Destroy " + x + " " + prettyName);
                    validTile2 = validTile2.withEmoji(emoji);
                    buttons.add(validTile2);
                }
            }
        }
        return buttons;
    }

    public static void resolveScuttleStart(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        int tgAlready = Integer.parseInt(buttonID.split("_")[1]);
        List<Button> buttons = getTilesToScuttle(player, game, event, tgAlready);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
            player.getRepresentation(true, true) + " Use buttons to select a system to Scuttle in", buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveLuckyShotStart(Player player, Game game, ButtonInteractionEvent event) {

        List<Button> buttons = getTilesToLuckyShot(player, game, event);
        if (buttons.size() == 0) {
            MessageHelper.sendMessageToChannel(event.getChannel(), player.getRepresentation(true, true)
                + " no systems to Lucky Shot in found. Remember you can't Lucky Shot yourself. Report bug if in error. If not an error, please take a different action");
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
            player.getRepresentation(true, true) + " Use buttons to select system to Lucky Shot in", buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveLuckyShotRemoval(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        String pos = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        String color = buttonID.split("_")[3];
        Tile tile = game.getTileByPosition(pos);
        Player p2 = game.getPlayerFromColorOrFaction(color);
        boolean damaged = false;
        if (unit.contains("damaged")) {
            unit = unit.replace("damaged", "");
            damaged = true;
        }
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), p2.getColor());
        new RemoveUnits().removeStuff(event, tile, 1, "space", unitKey, p2.getColor(), damaged, game);
        String msg = (damaged ? "A damaged " : "") + Emojis.getEmojiFromDiscord(unit.toLowerCase()) + " owned by "
            + ButtonHelper.getIdentOrColor(p2, game) + " in tile " + tile.getRepresentationForButtons(game, player)
            + " was removed via the Lucky Shot AC";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg);
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveScuttleEnd(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        int tgAlready = Integer.parseInt(buttonID.split("_")[1]);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " TGs increased by " + tgAlready + " (" + player.getTg() + "->"
                + (player.getTg() + tgAlready) + ")");
        player.setTg(player.getTg() + tgAlready);
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, game, tgAlready);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveScuttleTileSelection(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        int tgAlready = Integer.parseInt(buttonID.split("_")[2]);
        List<Button> buttons = getUnitsToScuttle(player, game, event, tile, tgAlready);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
            player.getRepresentation(true, true) + " Use buttons to select which unit to Scuttle", buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveLuckyShotTileSelection(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        List<Button> buttons = getUnitsToLuckyShot(player, game, event, tile);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
            player.getRepresentation(true, true) + " Use buttons to select which unit to Lucky Shot", buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveScuttleRemoval(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        String pos = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        int tgAlready = Integer.parseInt(buttonID.split("_")[3]);
        Tile tile = game.getTileByPosition(pos);
        List<Button> buttons = new ArrayList<>();

        boolean damaged = false;
        if (unit.contains("damaged")) {
            unit = unit.replace("damaged", "");
            damaged = true;
        }
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor());
        new RemoveUnits().removeStuff(event, tile, 1, "space", unitKey, player.getColor(), damaged, game);
        String msg = (damaged ? "A damaged " : "") + Emojis.getEmojiFromDiscord(unit.toLowerCase()) + " in tile "
            + tile.getRepresentation() + " was removed via the Scuttle AC by "
            + player.getFactionEmoji();

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        UnitModel removedUnit = player.getUnitsByAsyncID(unitKey.asyncID()).get(0);
        if (tgAlready > 0) {
            tgAlready = tgAlready + (int) removedUnit.getCost();
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getFactionEmoji() + " TGs increased by " + tgAlready + " (" + player.getTg() + "->"
                    + (player.getTg() + tgAlready) + ")");
            player.setTg(player.getTg() + tgAlready);
            ButtonHelperAbilities.pillageCheck(player, game);
            ButtonHelperAgents.resolveArtunoCheck(player, game, tgAlready);
        } else {
            tgAlready = tgAlready + (int) removedUnit.getCost();
            buttons.add(Button.success("startToScuttleAUnit_" + tgAlready, "Scuttle another Unit"));
            buttons.add(Button.danger("endScuttle_" + tgAlready, "End Scuttle"));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                player.getRepresentation(true, true) + " Use buttons to Scuttle another unit or end Scuttling.",
                buttons);
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void checkForAllAssignmentACs(Game game, Player player) {
        checkForAssigningCoup(game, player);
        checkForAssigningPublicDisgrace(game, player);
        checkForPlayingManipulateInvestments(game, player);
        checkForPlayingSummit(game, player);
    }

    public static void resolveCounterStroke(Game game, Player player, ButtonInteractionEvent event) {
        RemoveCC.removeCC(event, player.getColor(), game.getTileByPosition(game.getActiveSystem()),
            game);
        String message = player.getFactionEmoji() + " removed their CC from tile " + game.getActiveSystem()
            + " using Counterstroke and gained it to their tactic pool";
        player.setTacticalCC(player.getTacticalCC() + 1);
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveCounterStroke(Game game, Player player, ButtonInteractionEvent event,
        String buttonID) {
        RemoveCC.removeCC(event, player.getColor(), game.getTileByPosition(buttonID.split("_")[1]),
            game);
        String message = player.getFactionEmoji() + " removed their CC from tile " + buttonID.split("_")[1]
            + " using Counterstroke and gained it to their tactic pool";
        player.setTacticalCC(player.getTacticalCC() + 1);
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveSummit(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = ButtonHelper.getGainCCButtons(player);
        String message2 = player.getRepresentation() + "! Your current CCs are " + player.getCCRepresentation()
            + ". Use buttons to gain CCs";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveWarEffort(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>(
            Helper.getTileWithShipsPlaceUnitButtons(player, game, "cruiser", "placeOneNDone_skipbuild"));
        String message = "Use buttons to put 1 cruiser with your ships";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveFreeTrade(Game game, Player player, ButtonInteractionEvent event) {
        Button convert2CommButton = Button.success("convert_2_comms_stay", "Convert 2 Commodities Into TG")
            .withEmoji(Emoji.fromFormatted(Emojis.Wash));
        Button get2CommButton = Button.primary("gain_2_comms_stay", "Gain 2 Commodities")
            .withEmoji(Emoji.fromFormatted(Emojis.comm));
        List<Button> buttons = List.of(convert2CommButton, get2CommButton,
            Button.danger("deleteButtons", "Done resolving"));
        String message = "Use buttons to gain or convert commodities as appropriate. You may trade in this window/in between gaining commodities.";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolvePreparation(Game game, Player player, ButtonInteractionEvent event) {
        String message = "Use button to draw " + (player.hasAbility("scheming") ? "2 ACs" : "1 AC");
        List<Button> buttons = new ArrayList<>();
        if (player.hasAbility("scheming")) {
            buttons.add(Button.success("draw_2_ACDelete", "Draw 2 ACs (With Scheming)"));
        } else {
            buttons.add(Button.success("draw_1_ACDelete", "Draw 1 AC"));
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message, buttons);
    }

    public static void resolveRally(Game game, Player player, ButtonInteractionEvent event) {
        String message = player.getFactionEmoji() + " gained 2 fleet CC (" + player.getFleetCC() + "->"
            + (player.getFleetCC() + 2) + ") using Rally";
        player.setFleetCC(player.getFleetCC() + 2);
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        ButtonHelper.deleteMessage(event);
        if (game.getLaws().keySet().contains("regulations") && player.getFleetCC() > 4) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " reminder that there is Fleet Regulations in place, which is limiting fleet pool to 4");
        }
    }

    public static List<Button> getArcExpButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        Set<String> types = ButtonHelper.getTypesOfPlanetPlayerHas(game, player);
        for (String type : types) {
            if ("industrial".equals(type)) {
                buttons.add(Button.success("arcExp_industrial", "Explore Industrials X 3"));
            }
            if ("cultural".equals(type)) {
                buttons.add(Button.primary("arcExp_cultural", "Explore Culturals X 3"));
            }
            if ("hazardous".equals(type)) {
                buttons.add(Button.danger("arcExp_hazardous", "Explore Hazardous X 3"));
            }
        }
        return buttons;
    }

    public static void resolveArcExpButtons(Game game, Player player, String buttonID,
        ButtonInteractionEvent event, String trueIdentity) {
        String type = buttonID.replace("arcExp_", "");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            String cardID = game.drawExplore(type);
            ExploreModel card = Mapper.getExplore(cardID);
            sb.append(card.textRepresentation()).append(System.lineSeparator());
            String cardType = card.getResolution();
            if (cardType.equalsIgnoreCase(Constants.FRAGMENT)) {
                sb.append(trueIdentity).append(" Gained relic fragment\n");
                player.addFragment(cardID);
                game.purgeExplore(cardID);
            }
        }
        MessageChannel channel = player.getCorrectChannel();
        MessageHelper.sendMessageToChannel(channel, sb.toString());
        if (player.getLeaderIDs().contains("kollecccommander") && !player.hasLeaderUnlocked("kollecccommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "kollecc", event);
        }
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getRepealLawButtons(Game game, Player player) {
        List<Button> lawButtons = new ArrayList<>();
        for (String law : game.getLaws().keySet()) {
            lawButtons.add(Button.success("repealLaw_" + game.getLaws().get(law), Mapper.getAgendaTitle(law)));
        }
        return lawButtons;
    }

    public static List<Button> getDivertFundingLoseTechOptions(Player player, Game game) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (String tech : player.getTechs()) {
            TechnologyModel techM = Mapper.getTech(tech);
            if (!techM.isUnitUpgrade() && (techM.getFaction().isEmpty() || techM.getFaction().orElse("").length() < 1)) {
                buttons.add(Button.secondary(finChecker + "divertFunding@" + tech, techM.getName()));
            }
        }
        return buttons;
    }

    public static void divertFunding(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String techOut = buttonID.split("@")[1];
        player.removeTech(techOut);
        TechnologyModel techM1 = Mapper.getTech(techOut);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " removed the tech " + techM1.getName());
        resolveResearch(game, player, buttonID, event);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveForwardSupplyBaseStep2(Player hacan, Game game, ButtonInteractionEvent event, String buttonID) {
        Player player = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(hacan.getCorrectChannel(), "Could not resolve target player, please resolve manually.");
            return;
        }
        int oldTg = player.getTg();
        player.setTg(oldTg + 1);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            ButtonHelper.getIdentOrColor(player, game) + " gained 1TG due to Forward Supply Base (" + oldTg
                + "->" + player.getTg() + ")");
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(hacan.getCorrectChannel(), ButtonHelper.getIdentOrColor(player, game) + " gained 1TG due to Forward Supply Base");
        }
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveForwardSupplyBaseStep1(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        int oldTg = player.getTg();
        player.setTg(oldTg + 3);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " gained 3TGs (" + oldTg + "->" + player.getTg() + ")");
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, game, 3);
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Button.secondary("forwardSupplyBaseStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("forwardSupplyBaseStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " choose who should get 1TG", buttons);
    }

    public static void resolveReparationsStep1(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {

        String message = player.getRepresentation(true, true) + " Click the names of the planet you wish to ready";

        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getExhaustedPlanets()) {
            buttons.add(Button.secondary("khraskHeroStep4Ready_" + player.getFaction() + "_" + planet,
                Helper.getPlanetRepresentation(planet, game)));
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message,
            buttons);
        buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Button.secondary("reparationsStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("reparationsStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot who took the planet from you", buttons);
    }

    public static void resolveDiplomaticPressureStep1(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Button.secondary("diplomaticPressureStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("diplomaticPressureStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot who you want to force to give you a PN",
            buttons);
    }

    public static void resolveReactorMeltdownStep1(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Button.secondary("reactorMeltdownStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("reactorMeltdownStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot who you want to play Reactor Meltdown on",
            buttons);
    }

    public static void resolveFrontlineDeployment(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        String message = player.getRepresentation(true, true)
            + " Click the names of the planet you wish to drop 3 infantry on";
        List<Button> buttons = new ArrayList<>(
            Helper.getPlanetPlaceUnitButtons(player, game, "3gf", "placeOneNDone_skipbuild"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message,
            buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveUnexpectedAction(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {

        List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "unexpected");
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to remove token.",
            buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveDistinguished(Player player, Game game, ButtonInteractionEvent event) {
        player.addSpentThing("distinguished_5");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " added 5 votes to your vote total");
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveUprisingStep1(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Button.secondary("uprisingStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("uprisingStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot who's planet you want to uprise",
            buttons);
    }

    public static void resolveAssRepsStep1(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Button.secondary("assRepsStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("assRepsStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot who you want to assassinate", buttons);
    }

    public static void resolveSignalJammingStep1(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Button.secondary("signalJammingStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("signalJammingStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot who's CC you want to place down",
            buttons);
    }

    public static void resolveSeizeArtifactStep1(Player player, Game game, ButtonInteractionEvent event,
        String kolleccTech) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player || !player.getNeighbouringPlayers().contains(p2)) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(
                    Button.secondary("seizeArtifactStep2_" + p2.getFaction() + "_" + kolleccTech, p2.getColor()));
            } else {
                Button button = Button.secondary("seizeArtifactStep2_" + p2.getFaction() + "_" + kolleccTech, " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot which neighbor you are stealing from",
            buttons);
    }

    public static void resolvePlagueStep1(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Button.secondary("plagueStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("plagueStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot who's planet you want to Plague.",
            buttons);
    }

    public static void resolveEBSStep1(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        game.setStoredValue("EBSFaction", player.getFaction());
        if (buttonID.contains("_")) {
            ButtonHelper.resolveCombatRoll(player, game, event,
                "combatRoll_" + buttonID.split("_")[1] + "_space_spacecannonoffence");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find active system. You will need to roll using /roll");
        }
        game.setStoredValue("EBSFaction", "");

        ButtonHelper.deleteMessage(event);
    }

    public static void resolveBlitz(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        game.setStoredValue("BlitzFaction", player.getFaction());
        if (buttonID.contains("_")) {
            ButtonHelper.resolveCombatRoll(player, game, event,
                "combatRoll_" + buttonID.split("_")[1] + "_space_bombardment");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find active system. You will need to roll using /roll");
        }
        game.setStoredValue("BlitzFaction", "");
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveMicrometeoroidStormStep1(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Button.secondary("micrometeoroidStormStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("micrometeoroidStormStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot who's fighters you want to hit",
            buttons);
    }

    public static void resolveGhostShipStep1(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        List<Button> buttons = getGhostShipButtons(game, player);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot which tile you wish to place a Ghost Ship in",
            buttons);
    }

    public static void resolveProbeStep1(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        List<Button> buttons = getProbeButtons(game, player);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot which tile you wish to Exploration Probe", buttons);
    }

    public static void resolveGhostShipStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        tile = MoveUnits.flipMallice(event, tile, game);
        new AddUnits().unitParsing(event, player.getColor(), tile, "destroyer", game);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " put 1 destroyer in " + tile.getRepresentation());

        // If Empyrean Commander is in game check if unlock condition exists
        Player p2 = game.getPlayerFromLeader("empyreancommander");
        if (p2 != null) {
            if (!p2.hasLeaderUnlocked("empyreancommander")) {
                ButtonHelper.commanderUnlockCheck(p2, game, "empyrean", event);
            }
        }
    }

    public static void resolveProbeStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        new ExpFrontier().expFront(event, tile, game, player);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " explored the frontier token in " + tile.getRepresentation());
    }

    public static void resolveCrippleDefensesStep1(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Button.secondary("crippleStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("crippleStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot who's planet you want to cripple",
            buttons);
    }

    public static void resolveInfiltrateStep1(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Button.secondary("infiltrateStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("infiltrateStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot who's planet you are infiltrating",
            buttons);
    }

    public static void resolveSpyStep1(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Button.secondary("spyStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("spyStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot who to steal a random AC from",
            buttons);
    }

    public static void resolvePSStep1(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        game.setStoredValue("politicalStabilityFaction", player.getFaction());
        for (Integer sc : game.getSCList()) {
            if (sc <= 0)
                continue; // some older games have a 0 in the list of SCs
            Emoji scEmoji = Emoji.fromFormatted(Emojis.getSCBackEmojiFromInteger(sc));
            Button button;
            String label = Helper.getSCName(sc, game);
            if (scEmoji.getName().contains("SC") && scEmoji.getName().contains("Back")
                && !game.isHomebrewSCMode()) {
                button = Button.secondary("psStep2_" + sc, label).withEmoji(scEmoji);
            } else {
                button = Button.secondary("psStep2_" + sc, "" + sc + " " + label);
            }
            buttons.add(button);
        }
        if (game.getRealPlayers().size() < 5) {
            buttons.add(Button.danger("deleteButtons", "Delete these buttons"));
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot which strategy card(s) you used to have.", buttons);
    }

    public static void resolveImpersonation(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
        String message = player.getFactionEmoji() + " Drew A Secret Objective.";
        game.drawSecretObjective(player.getUserID());
        if (player.hasAbility("plausible_deniability")) {
            game.drawSecretObjective(player.getUserID());
            message = message + " Drew a second SO due to Plausible Deniability.";
        }
        SOInfo.sendSecretObjectiveInfo(game, player, event);
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        buttons.add(Button.danger("deleteButtons_spitItOut", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
            player.getRepresentation() + " Exhaust stuff to pay the 3 influence", buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolvePSStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        int scNum = Integer.parseInt(buttonID.split("_")[1]);
        player.addSC(scNum);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " you retained " + Helper.getSCName(scNum, game) + ".");
        if (game.getRealPlayers().size() < 5) {
            ButtonHelper.deleteTheOneButton(event);
        } else {
            ButtonHelper.deleteMessage(event);
        }
    }

    public static void resolveInsubStep1(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player || p2.getTacticalCC() < 1) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Button.secondary("insubStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("insubStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true)
                + " tell the bot which player you want to subtract a tactical CC from",
            buttons);
    }

    public static void resolveUnstableStep1(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Button.secondary("unstableStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("unstableStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot who's planet you want to Unstable Planet",
            buttons);
    }

    public static void resolveABSStep1(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Button.secondary("absStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("absStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot who's cultural planets you want to exhaust",
            buttons);
    }

    public static void resolveSalvageStep1(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Button.secondary("salvageStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("salvageStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot who you're playing Salvage on", buttons);
    }

    public static void resolveInsubStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        p2.setTacticalCC(p2.getTacticalCC() - 1);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " you subtracted 1 tactical CC from "
                + ButtonHelper.getIdentOrColor(p2, game));
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
            p2.getRepresentation(true, true) + " you lost a tactic CC due to Insubordination ("
                + (p2.getTacticalCC() + 1) + "->" + p2.getTacticalCC() + ").");
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveDiplomaticPressureStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> stuffToTransButtons = ButtonHelper.getForcedPNSendButtons(game, player, p2);
        String message = p2.getRepresentation(true, true)
            + " You have been forced to give a PN. Please select the PN you would like to send";
        MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), message, stuffToTransButtons);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " sent buttons to resolve Diplomatic Pressure to "
                + ButtonHelper.getIdentOrColor(p2, game));
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveABSStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        for (String planet : p2.getPlanetsAllianceMode()) {
            Planet p = (Planet) game.getPlanetsInfo().get(planet);
            if (p != null && p.getPlanetTypes().contains("cultural")) {
                p2.exhaustPlanet(planet);
            }
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " you exhausted all the cultural planets of "
                + ButtonHelper.getIdentOrColor(p2, game));
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
            p2.getRepresentation(true, true) + " your cultural planets were exhausted due to Ancient Burial Sites.");
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveSalvageStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        int comm = p2.getCommodities();
        p2.setCommodities(0);
        player.setTg(player.getTg() + comm);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " stole the commodities (there were " + comm
                + " comms to steal) of " + ButtonHelper.getIdentOrColor(player, game));
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
            p2.getRepresentation(true, true) + " your commodities were stolen due to Salvage.");
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveSpyStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation(true, true)
                + " since stealing 1 AC reveals secret info, extra precaution has been taken and a button has been sent to "
                + ButtonHelper.getIdentOrColor(p2, game)
                + " cards info thread, they may press this button to send a random AC to you.");
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("spyStep3_" + player.getFaction(), "Send random AC"));
        MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(),
            p2.getRepresentation(true, true)
                + " you have been hit by" + (ThreadLocalRandom.current().nextInt(1000) == 0 ? ", you've been struck by" : "") + " an ability which forces you to send a random AC. Press the button to send a random AC to the person.",
            buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveSpyStep3(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        new SentACRandom().sendRandomACPart2(event, game, player, p2);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveReparationsStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (p2.getReadiedPlanets().isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "Chosen player had no readied planets. This is fine and nothing more needs to be done.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getReadiedPlanets()) {
            buttons.add(Button.secondary("reparationsStep3_" + p2.getFaction() + "_" + planet,
                Helper.getPlanetRepresentation(planet, game)));
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " select the planet you want to exhaust", buttons);
    }

    public static void resolveInsiderInformation(Player player, Game game, ButtonInteractionEvent event) {
        NaaluCommander.sendTopAgendaToCardsInfoSkipCovert(game, player);
        MessageHelper.sendMessageToChannel(event.getChannel(),
            "Sent top agenda info to " + ButtonHelper.getIdentOrColor(player, game) + " cards info");
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveEmergencyMeeting(Player player, Game game, ButtonInteractionEvent event) {
        game.shuffleAllAgendasBackIntoDeck();
        DrawAgenda.drawAgenda(event, 3, game, player);
        MessageHelper.sendMessageToChannel(event.getChannel(),
            "Sent top 3 agendas to " + ButtonHelper.getIdentOrColor(player, game) + " cards info");
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveSeizeArtifactStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        List<String> playerFragments = p2.getFragments();
        for (String fragid : playerFragments) {
            if (fragid.contains("crf")) {
                buttons.add(Button.primary("seizeArtifactStep3_" + p2.getFaction() + "_" + fragid,
                    "Seize Cultural (" + fragid + ")"));
            }
            if (fragid.contains("irf")) {
                buttons.add(Button.success("seizeArtifactStep3_" + p2.getFaction() + "_" + fragid,
                    "Seize Industrial (" + fragid + ")"));
            }
            if (fragid.contains("hrf")) {
                buttons.add(Button.danger("seizeArtifactStep3_" + p2.getFaction() + "_" + fragid,
                    "Seize Hazardous (" + fragid + ")"));
            }
            if (fragid.contains("urf")) {
                buttons.add(Button.secondary("seizeArtifactStep3_" + p2.getFaction() + "_" + fragid,
                    "Seize Unknown (" + fragid + ")"));
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " select the relic fragment you want to grab", buttons);
        if (buttonID.split("_").length > 2 && buttonID.split("_")[2].contains("yes")) {
            p2.setTg(p2.getTg() + 2);
            ButtonHelperAbilities.pillageCheck(p2, game);
            ButtonHelperAgents.resolveArtunoCheck(p2, game, 2);
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                p2.getRepresentation() + " you gained 2TGs due to being hit by the tech Seeker Drones");
        }
    }

    public static void resolveUprisingStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (p2.getReadiedPlanets().isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "Chosen player had no readied planets. Nothing has been done.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getReadiedPlanets()) {
            if (game.getTileFromPlanet(planet) != null && game.getTileFromPlanet(planet).isHomeSystem()) {
                continue;
            }
            buttons.add(Button.secondary("uprisingStep3_" + p2.getFaction() + "_" + planet,
                Helper.getPlanetRepresentation(planet, game)));
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " select the planet you want to exhaust", buttons);
    }

    public static void resolveAssRepsStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            ButtonHelper.getIdentOrColor(player, game)
                + " successfully assassinated all the representatives of "
                + ButtonHelper.getIdentOrColor(p2, game));
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
            p2.getRepresentation(true, true) + " your representatives got sent to the headsman");
        game.setStoredValue("AssassinatedReps",
            game.getStoredValue("AssassinatedReps") + p2.getFaction());
    }

    public static void resolveSignalJammingStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasShipsInSystem(player, tile)) {
                buttons.add(Button.secondary("signalJammingStep3_" + p2.getFaction() + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true)
                + " the map is a big place, too big for the bot to offer all the options. Please tell the bot where the origin of the signal jam is coming from, to narrow it down a bit (origin must be a tile where your ships are) \n\n This tile you're selecting is the origin tile, after which you will be offered buttons to select that system or adjacent systems",
            buttons);
    }

    public static void resolveSignalJammingStep3(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String pos = buttonID.split("_")[2];
        List<Button> buttons = new ArrayList<>();
        for (String tilePos : FoWHelper.getAdjacentTilesAndNotThisTile(game, pos, player, false)) {
            Tile tile = game.getTileByPosition(tilePos);
            if (!tile.isHomeSystem()) {
                buttons.add(Button.secondary("signalJammingStep4_" + p2.getFaction() + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
            }
        }
        Tile tile = game.getTileByPosition(pos);
        if (!tile.isHomeSystem()) {
            buttons.add(Button.secondary("signalJammingStep4_" + p2.getFaction() + "_" + tile.getPosition(),
                tile.getRepresentationForButtons(game, player)));
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " select the tile you wish to jam.", buttons);
    }

    public static void resolveSignalJammingStep4(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String pos = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);
        AddCC.addCC(event, p2.getColor(), tile);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " you signal jammed the tile: "
                + tile.getRepresentationForButtons(game, player));
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
            p2.getRepresentation(true, true) + " you were signal jammed in tile: "
                + tile.getRepresentationForButtons(game, p2));

    }

    public static void resolveReactorMeltdownStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getPlanetsAllianceMode()) {
            if (planet.contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
            if (uH.getUnitCount(UnitType.CabalSpacedock, p2.getColor()) > 0
                || uH.getUnitCount(UnitType.Spacedock, p2.getColor()) > 0) {
                if (!game.getTileFromPlanet(planet).isHomeSystem()) {
                    Tile tile = game.getTileFromPlanet(planet);
                    buttons.add(Button.secondary(
                        "reactorMeltdownStep3_" + p2.getFaction() + "_" + tile.getPosition() + "_" + planet,
                        Helper.getPlanetRepresentation(planet, game)));
                }
            }
        }
        if (p2.hasUnit("absol_saar_spacedock") || p2.hasUnit("saar_spacedock") || p2.hasTech("ffac2")
            || p2.hasTech("absol_ffac2")) {
            for (Tile tile : game.getTileMap().values()) {
                if (tile.getUnitHolders().get("space").getUnitCount(UnitType.Spacedock, p2.getColor()) > 0) {
                    buttons.add(Button.secondary(
                        "reactorMeltdownStep3_" + p2.getFaction() + "_" + tile.getPosition() + "_space",
                        tile.getRepresentationForButtons(game, player)));
                }
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " select the space dock you want to melt", buttons);
    }

    public static void resolveReactorMeltdownStep3(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        Tile tile = game.getTileByPosition(buttonID.split("_")[2]);
        String unitHolderName = buttonID.split("_")[3];
        if ("space".equalsIgnoreCase(unitHolderName)) {
            unitHolderName = "";
        }
        new RemoveUnits().unitParsing(event, p2.getColor(), tile, "sd " + unitHolderName, game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " you killed the space dock in " + tile.getRepresentation());
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
            p2.getRepresentation(true, true) + " your space dock in " + tile.getRepresentation() + " was melted.");
        ButtonHelper.checkFleetAndCapacity(p2, game, tile, event);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolvePlagueStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getPlanets()) {
            buttons.add(Button.secondary("plagueStep3_" + p2.getFaction() + "_" + planet,
                Helper.getPlanetRepresentation(planet, game)));
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " select the planet you want to Plague", buttons);
    }

    public static void resolveMicrometeoroidStormStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasFightersInSystem(p2, tile)) {
                buttons.add(Button.secondary("micrometeoroidStormStep3_" + p2.getFaction() + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, p2)));
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " select the tile you want to hit with the AC", buttons);
    }

    public static void resolveRefitTroops(Player player, Game game, ButtonInteractionEvent event, String buttonID,
        String finChecker) {
        List<Button> buttons = new ArrayList<>(
            ButtonHelperAbilities.getPlanetPlaceUnitButtonsForMechMitosis(player, game, finChecker));
        String message = player.getRepresentation(true, true) + " Use buttons to replace 1 infantry with 1 mech";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message,
            buttons);
        List<Button> buttons2 = new ArrayList<>(
            ButtonHelperAbilities.getPlanetPlaceUnitButtonsForMechMitosis(player, game, finChecker));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message,
            buttons2);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveCrippleStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getPlanets()) {
            buttons.add(Button.secondary("crippleStep3_" + p2.getFaction() + "_" + planet,
                Helper.getPlanetRepresentation(planet, game)));
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " select the planet you want to cripple", buttons);
    }

    public static void resolveInfiltrateStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getPlanets()) {
            buttons.add(Button.secondary("infiltrateStep3_" + p2.getFaction() + "_" + planet,
                Helper.getPlanetRepresentation(planet, game)));
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " select the planet you want to Infiltrate", buttons);
    }

    public static void resolveUpgrade(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        new RemoveUnits().unitParsing(event, player.getColor(), tile, "cruiser", game);
        new AddUnits().unitParsing(event, player.getColor(), tile, "dread", game);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " replaced 1 cruiser with 1 dreadnought in " + tile.getRepresentation());
    }

    public static void checkForAssigningCoup(Game game, Player player) {
        if (ButtonHelper.isPlayerElected(game, player, "censure")
            || ButtonHelper.isPlayerElected(game, player, "absol_censure")) {
            return;
        }
        if (player.getActionCards().containsKey("coup")) {
            game.setStoredValue("Coup", "");
            String msg = player.getRepresentation()
                + " you have the option to pre-assign which strategy card you will Coup. Coup is an awkward timing window for async, so if you intend to play it, it's best to pre-play it now. Feel free to ignore this message if you don't intend to play it";
            List<Button> scButtons = new ArrayList<>();
            for (Integer sc : game.getSCList()) {
                if (sc <= 0)
                    continue; // some older games have a 0 in the list of SCs
                Emoji scEmoji = Emoji.fromFormatted(Emojis.getSCBackEmojiFromInteger(sc));
                Button button;
                String label = Helper.getSCName(sc, game);
                if (scEmoji.getName().contains("SC") && scEmoji.getName().contains("Back")
                    && !game.isHomebrewSCMode()) {
                    button = Button.secondary("resolvePreassignment_Coup_" + sc, label).withEmoji(scEmoji);
                } else {
                    button = Button.secondary("resolvePreassignment_Coup_" + sc, "" + sc + " " + label);
                }
                scButtons.add(button);
            }
            scButtons.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, scButtons);
        }

    }

    public static void checkForPlayingSummit(Game game, Player player) {
        if (ButtonHelper.isPlayerElected(game, player, "censure")
            || ButtonHelper.isPlayerElected(game, player, "absol_censure")) {
            return;
        }
        if (player.getActionCards().containsKey("summit")) {
            String msg = player.getRepresentation()
                + " you have the option to pre-play Summit. Start of strategy phase is an awkward timing window for async, so if you intend to play it, it's best to pre-play it now. Feel free to ignore this message if you don't intend to play it";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("resolvePreassignment_Summit", "Pre-play Summit"));
            buttons.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        }
    }

    public static void checkForPlayingManipulateInvestments(Game game, Player player) {
        if (ButtonHelper.isPlayerElected(game, player, "censure")
            || ButtonHelper.isPlayerElected(game, player, "absol_censure")) {
            return;
        }
        if (player.getActionCards().containsKey("investments")) {
            String msg = player.getRepresentation()
                + " you have the option to pre-play Manipulate Investments. Start of strat phase is an awkward timing window for async, so if you intend to play it, it's best to pre-play it now. Feel free to ignore this message if you don't intend to play it";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("resolvePreassignment_Investments", "Pre-play Manipulate Investments"));
            buttons.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        }
    }

    public static void checkForAssigningPublicDisgrace(Game game, Player player) {
        if (ButtonHelper.isPlayerElected(game, player, "censure")
            || ButtonHelper.isPlayerElected(game, player, "absol_censure")) {
            return;
        }
        if (player.getActionCards().containsKey("disgrace")) {
            String msg = player.getRepresentation()
                + " you have the option to pre-assign which strategy card you will Public Disgrace. Public Disgrace is an awkward timing window for async, so if you intend to play it, it's best to pre-play it now."
                + " Feel free to ignore this message if you don't intend to play it or are unsure of the target. You will be given the option for it to only trigger on a particular person";
            List<Button> scButtons = new ArrayList<>();
            for (Integer sc : game.getSCList()) {
                if (sc <= 0)
                    continue; // some older games have a 0 in the list of SCs
                Emoji scEmoji = Emoji.fromFormatted(Emojis.getSCBackEmojiFromInteger(sc));
                Button button;
                String label = Helper.getSCName(sc, game);
                if (scEmoji.getName().contains("SC") && scEmoji.getName().contains("Back")
                    && !game.isHomebrewSCMode()) {
                    button = Button.secondary("resolvePreassignment_Public Disgrace_" + sc, label).withEmoji(scEmoji);
                } else {
                    button = Button.secondary("resolvePreassignment_Public Disgrace_" + sc, "" + sc + " " + label);
                }
                scButtons.add(button);
            }
            scButtons.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, scButtons);
        }
    }

    public static void resolveDecoyOperationStep1(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder uH : tile.getUnitHolders().values()) {
            if (uH instanceof Planet) {
                buttons.add(Button.success("decoyOperationStep2_" + uH.getName(),
                    Helper.getPlanetRepresentation(uH.getName(), game)));
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true)
                + " tell the bot which planet you want to resolve decoy operations on",
            buttons);
    }

    public static void resolveDecoyOperationStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        String planet = buttonID.split("_")[1];
        List<Button> buttons = ButtonHelper.getButtonsForMovingGroundForcesToAPlanet(game, planet, player);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " use buttons to move up to 2 troops", buttons);
    }

    public static void resolveEmergencyRepairs(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        tile.removeAllUnitDamage(player.getColor());
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " repaired all damaged units in " + tile.getRepresentation());
    }

    public static void resolveTacticalBombardmentStep1(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        List<Button> buttons = getTacticalBombardmentButtons(game, player);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot which tile you wish to tactically bombard in",
            buttons);
    }

    public static void resolveTacticalBombardmentStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        for (UnitHolder uH : tile.getUnitHolders().values()) {
            if (uH instanceof Planet) {
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    if (p2.getPlanets().contains(uH.getName())) {
                        p2.exhaustPlanet(uH.getName());
                        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                            p2.getRepresentation() + " Your planets in " + tile.getRepresentation()
                                + " were exhausted");
                    }
                }
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " exhausted all enemy planets in " + tile.getRepresentation());
    }

    public static void resolveUnstableStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getPlanets()) {
            if (ButtonHelper.getTypeOfPlanet(game, planet).contains("hazardous")) {
                buttons.add(Button.secondary("unstableStep3_" + p2.getFaction() + "_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " select the planet you want to exhaust", buttons);
    }

    public static void resolveUnstableStep3(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, game);
        ButtonHelper.deleteMessage(event);
        if (p2.getReadiedPlanets().contains(planet)) {
            p2.exhaustPlanet(planet);
        }
        int amountToKill = 3;
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        amountToKill = uH.getUnitCount(UnitType.Infantry, p2.getColor());
        if (amountToKill > 3) {
            amountToKill = 3;
        }
        if (p2.hasInf2Tech()) {
            ButtonHelper.resolveInfantryDeath(game, p2, amountToKill);
            boolean cabalMech = false;
            Tile tile = game.getTileFromPlanet(planet);
            if (p2.hasAbility("amalgamation")
                && game.getTileFromPlanet(planet).getUnitHolders().get(planet).getUnitCount(UnitType.Mech,
                    p2.getColor()) > 0
                && p2.hasUnit("cabal_mech")
                && !ButtonHelper.isLawInPlay(game, "articles_war")) {
                cabalMech = true;
            }
            if (p2.hasAbility("amalgamation")
                && (ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", p2, tile) || cabalMech)
                && FoWHelper.playerHasUnitsOnPlanet(p2, tile, planet)) {
                ButtonHelperFactionSpecific.cabalEatsUnit(p2, game, p2, amountToKill, "infantry", event);
            }
        }
        if ((p2.getUnitsOwned().contains("mahact_infantry") || p2.hasTech("cl2"))) {
            ButtonHelperFactionSpecific.offerMahactInfButtons(p2, game);
        }
        new RemoveUnits().unitParsing(event, p2.getColor(), game.getTileFromPlanet(planet),
            amountToKill + " inf " + planet, game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " you exhausted " + planetRep
                + " and killed " + amountToKill + " infantry there");
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
            p2.getRepresentation(true, true) + " your planet " + planetRep
                + " was exhausted and " + amountToKill + " infantry were destroyed.");
    }

    public static void resolveSeizeArtifactStep3(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String fragID = buttonID.split("_")[2];
        ButtonHelper.deleteMessage(event);
        p2.removeFragment(fragID);
        player.addFragment(fragID);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " you gained the fragment " + fragID);
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
            p2.getRepresentation(true, true) + " your fragment " + fragID + " was seized.");
    }

    public static void resolveUprisingStep3(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, game);
        ButtonHelper.deleteMessage(event);
        p2.exhaustPlanet(planet);
        int resValue = Helper.getPlanetResources(planet, game);
        int oldTg = player.getTg();
        player.setTg(oldTg + resValue);
        MessageHelper.sendMessageToChannel(event.getChannel(),
            player.getFactionEmoji() + " gained " + resValue + "TG" + (resValue == 1 ? "" : "s") + " (" + oldTg + "->" + player.getTg() + ")");
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, game, resValue);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " you exhausted " + planetRep);
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
            p2.getRepresentation(true, true) + " your planet " + planetRep + " was exhausted.");
    }

    public static void resolvePlagueStep3(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, game);
        ButtonHelper.deleteMessage(event);
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        int amount = uH.getUnitCount(UnitType.Infantry, p2.getColor());
        int hits = 0;
        if (amount > 0) {
            StringBuilder msg = new StringBuilder(Emojis.getEmojiFromDiscord("infantry") + " rolled ");
            for (int x = 0; x < amount; x++) {
                Die d1 = new Die(6);
                msg.append(d1.getResult()).append(", ");
                if (d1.isSuccess()) {
                    hits++;
                }
            }
            msg = new StringBuilder(msg.substring(0, msg.length() - 2) + "\n Total hits were " + hits);
            UnitKey key = Mapper.getUnitKey(AliasHandler.resolveUnit("infantry"), p2.getColor());
            new RemoveUnits().removeStuff(event, game.getTileFromPlanet(planet), hits, planet, key, p2.getColor(),
                false, game);
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg.toString());
            ButtonHelper.resolveInfantryDeath(game, p2, hits);
            if ((p2.getUnitsOwned().contains("mahact_infantry") || p2.hasTech("cl2"))) {
                ButtonHelperFactionSpecific.offerMahactInfButtons(p2, game);
            }
            boolean cabalMech = false;
            Tile tile = game.getTileFromPlanet(planet);
            if (p2.hasAbility("amalgamation")
                && game.getTileFromPlanet(planet).getUnitHolders().get(planet).getUnitCount(UnitType.Mech,
                    p2.getColor()) > 0
                && p2.hasUnit("cabal_mech")
                && !ButtonHelper.isLawInPlay(game, "articles_war")) {
                cabalMech = true;
            }
            if (p2.hasAbility("amalgamation")
                && (ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", p2, tile) || cabalMech)
                && FoWHelper.playerHasUnitsOnPlanet(p2, tile, planet)) {
                ButtonHelperFactionSpecific.cabalEatsUnit(p2, game, p2, hits, "infantry", event);
            }
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " you Plague'd " + planetRep + " and got " + hits + " hit" + (hits == 1 ? "" : ""));
        String adjective = "";
        if (amount <= 3) {
        } else if (hits == 0) {
            adjective = "n inconsequential";
        } else if (hits == amount) {
            adjective = " catastrophic";
        } else if (hits <= amount / 3) {
            adjective = " minor";
        } else if (hits <= 2 * amount / 3) {
            adjective = " major";
        }
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
            p2.getRepresentation(true, true) + " your planet " + planetRep + " suffered a"
                + adjective + " Plague and you lost " + hits + " infantry.");
    }

    public static void resolveMicrometeoroidStormStep3(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String tilePos = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(tilePos);
        ButtonHelper.deleteMessage(event);
        UnitHolder uH = tile.getUnitHolders().get("space");
        int amount = uH.getUnitCount(UnitType.Fighter, p2.getColor());
        int hits = 0;
        if (amount > 0) {
            StringBuilder msg = new StringBuilder(Emojis.getEmojiFromDiscord("fighter") + " rolled ");
            for (int x = 0; x < amount; x++) {
                Die d1 = new Die(6);
                msg.append(d1.getResult()).append(", ");
                if (d1.isSuccess()) {
                    hits++;
                }
            }
            msg = new StringBuilder(msg.substring(0, msg.length() - 2) + "\n Total hits were " + hits);
            UnitKey key = Mapper.getUnitKey(AliasHandler.resolveUnit("fighter"), p2.getColor());
            new RemoveUnits().removeStuff(event, tile, hits, "space", key, p2.getColor(),
                false, game);
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg.toString());

            if (ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", p2, tile)) {
                ButtonHelperFactionSpecific.cabalEatsUnit(p2, game, p2, hits, "fighter", event);
            }
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " you stormed "
                + tile.getRepresentationForButtons(game, player) + " and got " + hits + " hit" + (hits == 1 ? "" : "s"));
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
            p2.getRepresentation(true, true) + " your fighter" + (amount == 1 ? "" : "s") + " in "
                + tile.getRepresentationForButtons(game, player) + " were hit by a storm and you lost "
                + hits + " fighter" + (hits == 1 ? "" : "s") + ".");
    }

    public static void resolveCrippleStep3(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, game);
        ButtonHelper.deleteMessage(event);
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        int amount = uH.getUnitCount(UnitType.Pds, p2.getColor());
        if (amount > 0) {
            UnitKey key = Mapper.getUnitKey(AliasHandler.resolveUnit("pds"), p2.getColor());
            new RemoveUnits().removeStuff(event, game.getTileFromPlanet(planet), amount, planet, key,
                p2.getColor(), false, game);
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " you crippled " + planetRep + " and killed " + amount + " PDS");
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
            p2.getRepresentation(true, true) + " your planet " + planetRep + " was crippled and you lost " + amount
                + " PDS.");
    }

    public static void resolveInfiltrateStep3(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[2];
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        ButtonHelperModifyUnits.infiltratePlanet(player, game, uH, event);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveReparationsStep3(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, game);
        p2.exhaustPlanet(planet);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation(true, true) + " you exhausted " + planetRep);
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), p2.getRepresentation(true, true) + " your planet " + planetRep + " was exhausted.");
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveResearch(Game game, Player player, String buttonID,
        ButtonInteractionEvent event) {
        if (!player.hasAbility("propagation")) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentation(true, true) + " you may use the button to get your tech.",
                List.of(Buttons.GET_A_TECH));
        } else {
            List<Button> buttons = ButtonHelper.getGainCCButtons(player);
            String message2 = player.getRepresentation() + "! Your current CCs are " + player.getCCRepresentation()
                + ". Use buttons to gain CCs";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
            game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void focusedResearch(Game game, Player player, String buttonID,
        ButtonInteractionEvent event) {
        if (player.getTg() < 4) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + "You did not have 4tgs and thus cannot resolve focused research");
            return;
        } else {
            player.setTg(player.getTg() - 4);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation(false, false) + " has spent 4tg (" + (player.getTg() + 4) + " ->" + player.getTg() + ") on focused research");
        }
        if (!player.hasAbility("propagation")) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentation(true, true) + " you may use the button to get your tech.",
                List.of(Buttons.GET_A_TECH));
        } else {
            List<Button> buttons = ButtonHelper.getGainCCButtons(player);
            String message2 = player.getRepresentation() + "! Your current CCs are " + player.getCCRepresentation()
                + ". Use buttons to gain CCs";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
            game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void repealLaw(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String numID = buttonID.split("_")[1];
        String name = "";
        for (String law : game.getLaws().keySet()) {
            if (numID.equalsIgnoreCase("" + game.getLaws().get(law))) {
                name = law;
            }
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " repealed " + Mapper.getAgendaTitle(name));
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                Mapper.getAgendaTitle(name) + " was repealed");
        }
        game.removeLaw(name);
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getPlagiarizeButtons(Game game, Player player) {
        List<String> techToGain = new ArrayList<>();
        for (Player p2 : player.getNeighbouringPlayers()) {
            techToGain = ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(player, p2, techToGain,
                game);
        }
        List<Button> techs = new ArrayList<>();
        for (String tech : techToGain) {
            if ("".equals(Mapper.getTech(AliasHandler.resolveTech(tech)).getFaction().orElse(""))) {
                if (Mapper.getTech(tech).isUnitUpgrade()) {
                    boolean hasSpecialUpgrade = false;
                    for (String factionTech : player.getNotResearchedFactionTechs()) {
                        TechnologyModel fTech = Mapper.getTech(factionTech);
                        if (fTech != null && !fTech.getAlias().equalsIgnoreCase(Mapper.getTech(tech).getAlias())
                            && fTech.isUnitUpgrade()
                            && fTech.getBaseUpgrade().orElse("bleh")
                                .equalsIgnoreCase(Mapper.getTech(tech).getAlias())) {
                            hasSpecialUpgrade = true;
                        }
                    }
                    if (!hasSpecialUpgrade) {
                        techs.add(Button.success("getTech_" + Mapper.getTech(tech).getAlias() + "__noPay",
                            Mapper.getTech(tech).getName()));
                    }
                } else {
                    techs.add(Button.success("getTech_" + Mapper.getTech(tech).getAlias() + "__noPay",
                        Mapper.getTech(tech).getName()));
                }
            }
        }
        return techs;
    }

    public static List<Button> getGhostShipButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.doesTileHaveWHs(game, tile.getPosition())) {
                boolean hasOtherShip = false;
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    if (FoWHelper.playerHasShipsInSystem(p2, tile)) {
                        hasOtherShip = true;
                    }
                }
                if (!hasOtherShip) {
                    buttons.add(Button.success("ghostShipStep2_" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
                }
            }
        }
        return buttons;
    }

    public static List<Button> getTacticalBombardmentButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesOfUnitsWithBombard(player, game)) {
            buttons.add(Button.success("tacticalBombardmentStep2_" + tile.getPosition(),
                tile.getRepresentationForButtons(game, player)));
        }
        return buttons;
    }

    public static List<Button> getProbeButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getUnitHolders().get("space").getTokenList().contains(Mapper.getTokenID(Constants.FRONTIER))) {
                boolean hasShips = false;
                for (String tile2pos : FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player,
                    false)) {
                    if (FoWHelper.playerHasShipsInSystem(player, game.getTileByPosition(tile2pos))) {
                        hasShips = true;
                    }
                }
                if (FoWHelper.playerHasShipsInSystem(player, tile)) {
                    hasShips = true;
                }
                if (hasShips) {
                    buttons.add(Button.success("probeStep2_" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
                }
            }
        }
        return buttons;
    }

    public static void resolveReverse(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String acName = buttonID.split("_")[1];
        List<String> acStrings = new ArrayList<>(game.getDiscardActionCards().keySet());
        for (String acStringID : acStrings) {
            ActionCardModel actionCard = Mapper.getActionCard(acStringID);
            String actionCardTitle = actionCard.getName();
            if (acName.equalsIgnoreCase(actionCardTitle)) {
                boolean picked = game.pickActionCard(player.getUserID(),
                    game.getDiscardActionCards().get(acStringID));
                if (!picked) {
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        "No such Action Card ID found, please retry");
                    return;
                }
                String sb = "Game: " + game.getName() + " " +
                    "Player: " + player.getUserName() + "\n" +
                    "Picked card from Discards: " +
                    Mapper.getActionCard(acStringID).getRepresentation() + "\n";
                MessageHelper.sendMessageToChannel(event.getChannel(), sb);

                ACInfo.sendActionCardInfo(game, player);
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void economicInitiative(Player player, Game game, ButtonInteractionEvent event) {
        for (String planet : player.getPlanetsAllianceMode()) {
            if (ButtonHelper.getTypeOfPlanet(game, planet).contains("cultural")) {
                player.refreshPlanet(planet);
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(),
            player.getFactionEmoji() + " readied each cultural planet");
        ButtonHelper.deleteMessage(event);
    }

    public static void industrialInitiative(Player player, Game game, ButtonInteractionEvent event) {
        int oldTg = player.getTg();
        int count = ButtonHelper.getNumberOfXTypePlanets(player, game, "industrial");
        player.setTg(oldTg + count);
        MessageHelper.sendMessageToChannel(event.getChannel(),
            player.getFactionEmoji() + " gained " + count + "TG" + (count == 1 ? "" : "s") + " (" + oldTg + "->" + player.getTg() + ")");
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, game, count);
        ButtonHelper.deleteMessage(event);
    }

    public static void miningInitiative(Player player, Game game, ButtonInteractionEvent event) {
        int oldTg = player.getTg();
        int count = 0;
        for (String planet : player.getPlanetsAllianceMode()) {
            Planet p = (Planet) game.getPlanetsInfo().get(planet);
            if (p != null && p.getResources() > count) {
                count = p.getResources();
            }
        }
        player.setTg(oldTg + count);
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmoji() + " gained " + count
            + " TG" + (count == 1 ? "" : "s") + " (" + oldTg + "->" + player.getTg() + ") from their highest resource planet");
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, game, count);
        ButtonHelper.deleteMessage(event);
    }
}
