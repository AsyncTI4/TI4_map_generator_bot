package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import ti4.commands.explore.ExploreAndDiscard;
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

    public static List<Button> getTilesToScuttle(Player player, Game activeGame, GenericInteractionCreateEvent event,
            int tgAlready) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(activeGame.getTileMap()).entrySet()) {
            if (FoWHelper.playerHasShipsInSystem(player, tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                Button validTile = Button.success(finChecker + "scuttleIn_" + tileEntry.getKey() + "_" + tgAlready,
                        tile.getRepresentationForButtons(activeGame, player));
                buttons.add(validTile);
            }
        }
        Button validTile2 = Button.danger(finChecker + "deleteButtons", "Decline");
        buttons.add(validTile2);
        return buttons;
    }

    public static List<Button> getTilesToLuckyShot(Player player, Game activeGame,
            GenericInteractionCreateEvent event) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(activeGame.getTileMap()).entrySet()) {
            if (FoWHelper.otherPlayersHaveShipsInSystem(player, tileEntry.getValue(), activeGame)
                    && FoWHelper.playerHasPlanetsInSystem(player, tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                UnitHolder space = tile.getUnitHolders().get("space");
                boolean rightKindPresent = false;
                for (Player p2 : activeGame.getRealPlayers()) {
                    if (space.getUnitCount(UnitType.Dreadnought, p2.getColor()) > 0
                            || space.getUnitCount(UnitType.Cruiser, p2.getColor()) > 0
                            || space.getUnitCount(UnitType.Destroyer, p2.getColor()) > 0) {
                        rightKindPresent = true;
                    }
                }
                if (rightKindPresent) {
                    Button validTile = Button.success(finChecker + "luckyShotIn_" + tileEntry.getKey(),
                            tile.getRepresentationForButtons(activeGame, player));
                    buttons.add(validTile);
                }

            }
        }
        return buttons;
    }

    public static List<Button> getUnitsToScuttle(Player player, Game activeGame, GenericInteractionCreateEvent event,
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

    public static List<Button> getUnitsToLuckyShot(Player player, Game activeGame, GenericInteractionCreateEvent event,
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
                Player p2 = activeGame.getPlayerFromColorOrFaction(unitKey.getColor());
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

    public static void resolveScuttleStart(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        int tgAlready = Integer.parseInt(buttonID.split("_")[1]);
        List<Button> buttons = getTilesToScuttle(player, activeGame, event, tgAlready);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                player.getRepresentation(true, true) + " Use buttons to select tile to scuttle in", buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveLuckyShotStart(Player player, Game activeGame, ButtonInteractionEvent event) {

        List<Button> buttons = getTilesToLuckyShot(player, activeGame, event);
        if (buttons.size() == 0) {
            MessageHelper.sendMessageToChannel(event.getChannel(), player.getRepresentation(true, true)
                    + " no tiles to lucky shot in found. Remember you cant lucky shot yourself. Report bug if in error. If not an error, please take a different action");
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                player.getRepresentation(true, true) + " Use buttons to select tile to lucky shot in", buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveLuckyShotRemoval(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        String pos = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        String color = buttonID.split("_")[3];
        Tile tile = activeGame.getTileByPosition(pos);
        Player p2 = activeGame.getPlayerFromColorOrFaction(color);
        boolean damaged = false;
        if (unit.contains("damaged")) {
            unit = unit.replace("damaged", "");
            damaged = true;
        }
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), p2.getColor());
        new RemoveUnits().removeStuff(event, tile, 1, "space", unitKey, p2.getColor(), damaged, activeGame);
        String msg = (damaged ? "A damaged " : "") + Emojis.getEmojiFromDiscord(unit.toLowerCase()) + " owned by "
                + ButtonHelper.getIdentOrColor(p2, activeGame) + " in tile " + tile.getRepresentation()
                + " was removed via the Lucky Shot AC";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), msg);
        }
        event.getMessage().delete().queue();
    }

    public static void resolveScuttleEnd(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        int tgAlready = Integer.parseInt(buttonID.split("_")[1]);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                ButtonHelper.getIdent(player) + " tgs increased by " + tgAlready + " (" + player.getTg() + "->"
                        + (player.getTg() + tgAlready) + ")");
        player.setTg(player.getTg() + tgAlready);
        ButtonHelperAbilities.pillageCheck(player, activeGame);
        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, tgAlready);
        event.getMessage().delete().queue();
    }

    public static void resolveScuttleTileSelection(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = activeGame.getTileByPosition(pos);
        int tgAlready = Integer.parseInt(buttonID.split("_")[2]);
        List<Button> buttons = getUnitsToScuttle(player, activeGame, event, tile, tgAlready);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                player.getRepresentation(true, true) + " Use buttons to select unit to scuttle", buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveLuckyShotTileSelection(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = activeGame.getTileByPosition(pos);
        List<Button> buttons = getUnitsToLuckyShot(player, activeGame, event, tile);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                player.getRepresentation(true, true) + " Use buttons to select unit to lucky shot", buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveScuttleRemoval(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        String pos = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        int tgAlready = Integer.parseInt(buttonID.split("_")[3]);
        Tile tile = activeGame.getTileByPosition(pos);
        List<Button> buttons = new ArrayList<>();

        boolean damaged = false;
        if (unit.contains("damaged")) {
            unit = unit.replace("damaged", "");
            damaged = true;
        }
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor());
        new RemoveUnits().removeStuff(event, tile, 1, "space", unitKey, player.getColor(), damaged, activeGame);
        String msg = (damaged ? "A damaged " : "") + Emojis.getEmojiFromDiscord(unit.toLowerCase()) + " in tile "
                + tile.getRepresentation() + " was removed via the Scuttle AC by "
                + ButtonHelper.getIdent(player);

        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
        UnitModel removedUnit = player.getUnitsByAsyncID(unitKey.asyncID()).get(0);
        if (tgAlready > 0) {
            tgAlready = tgAlready + (int) removedUnit.getCost();
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                    ButtonHelper.getIdent(player) + " tgs increased by " + tgAlready + " (" + player.getTg() + "->"
                            + (player.getTg() + tgAlready) + ")");
            player.setTg(player.getTg() + tgAlready);
            ButtonHelperAbilities.pillageCheck(player, activeGame);
            ButtonHelperAgents.resolveArtunoCheck(player, activeGame, tgAlready);
        } else {
            tgAlready = tgAlready + (int) removedUnit.getCost();
            buttons.add(Button.success("startToScuttleAUnit_" + tgAlready, "Scuttle another Unit"));
            buttons.add(Button.danger("endScuttle_" + tgAlready, "End Scuttle"));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                    player.getRepresentation(true, true) + " Use buttons to scuttle another unit or end scuttling.",
                    buttons);
        }
        event.getMessage().delete().queue();
    }

    public static void checkForAllAssignmentACs(Game activeGame, Player player) {
        checkForAssigningCoup(activeGame, player);
        checkForAssigningPublicDisgrace(activeGame, player);
        checkForPlayingManipulateInvestments(activeGame, player);
        checkForPlayingSummit(activeGame, player);
    }

    public static void resolveCounterStroke(Game activeGame, Player player, ButtonInteractionEvent event) {
        RemoveCC.removeCC(event, player.getColor(), activeGame.getTileByPosition(activeGame.getActiveSystem()),
                activeGame);
        String message = ButtonHelper.getIdent(player) + " removed their CC from tile " + activeGame.getActiveSystem()
                + " using counterstroke and gained it to their tactics";
        player.setTacticalCC(player.getTacticalCC() + 1);
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        event.getMessage().delete().queue();
    }

    public static void resolveSummit(Game activeGame, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = ButtonHelper.getGainCCButtons(player);
        String message2 = player.getRepresentation() + "! Your current CCs are " + player.getCCRepresentation()
                + ". Use buttons to gain CCs";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        activeGame.setCurrentReacts("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        event.getMessage().delete().queue();
    }

    public static void resolveWarEffort(Game activeGame, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>(
                Helper.getTileWithShipsPlaceUnitButtons(player, activeGame, "cruiser", "placeOneNDone_skipbuild"));
        String message = "Use buttons to put 1 cruiser with your ships";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveHarnessEnergy(Game activeGame, Player player, ButtonInteractionEvent event) {
        String message = ButtonHelper.getIdent(player) + " Replenished Commodities (" + player.getCommodities() + "->"
                + player.getCommoditiesTotal()
                + ").";
        player.setCommodities(player.getCommoditiesTotal());
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, player, event);
        ButtonHelperAgents.cabalAgentInitiation(activeGame, player);
        if (player.hasAbility("military_industrial_complex")
                && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1) {
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                    player.getRepresentation(true, true) + " you have the opportunity to buy axis orders",
                    ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
        }
        if (player.getLeaderIDs().contains("mykomentoricommander")
                && !player.hasLeaderUnlocked("mykomentoricommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "mykomentori", event);
        }
        event.getMessage().delete().queue();
    }

    public static void resolveRally(Game activeGame, Player player, ButtonInteractionEvent event) {
        String message = ButtonHelper.getIdent(player) + " gained 2 fleet CC (" + player.getFleetCC() + "->"
                + (player.getFleetCC() + 2) + ") using rally";
        player.setFleetCC(player.getFleetCC() + 2);
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        event.getMessage().delete().queue();
    }

    public static List<Button> getArcExpButtons(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        List<String> types = ButtonHelper.getTypesOfPlanetPlayerHas(activeGame, player);
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

    public static void resolveArcExpButtons(Game activeGame, Player player, String buttonID,
            ButtonInteractionEvent event, String trueIdentity) {
        String type = buttonID.replace("arcExp_", "");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            String cardID = activeGame.drawExplore(type);
            sb.append(new ExploreAndDiscard().displayExplore(cardID)).append(System.lineSeparator());
            ExploreModel card = Mapper.getExplore(cardID);
            String cardType = card.getResolution();
            if (cardType.equalsIgnoreCase(Constants.FRAGMENT)) {
                sb.append(trueIdentity).append(" Gained relic fragment\n");
                player.addFragment(cardID);
                activeGame.purgeExplore(cardID);
            }
        }
        MessageChannel channel = ButtonHelper.getCorrectChannel(player, activeGame);
        MessageHelper.sendMessageToChannel(channel, sb.toString());
        if (player.getLeaderIDs().contains("kollecccommander") && !player.hasLeaderUnlocked("kollecccommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "kollecc", event);
        }
        event.getMessage().delete().queue();
    }

    public static List<Button> getRepealLawButtons(Game activeGame, Player player) {
        List<Button> lawButtons = new ArrayList<>();
        for (String law : activeGame.getLaws().keySet()) {
            lawButtons.add(Button.success("repealLaw_" + activeGame.getLaws().get(law), Mapper.getAgendaTitle(law)));
        }
        return lawButtons;
    }

    public static List<Button> getDivertFundingLoseTechOptions(Player player, Game activeGame) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (String tech : player.getTechs()) {
            TechnologyModel techM = Mapper.getTech(tech);
            if (!"unitupgrade".equalsIgnoreCase(techM.getType().toString())
                    && (techM.getFaction().isEmpty() || techM.getFaction().orElse("").length() < 1)) {
                buttons.add(Button.secondary(finChecker + "divertFunding@" + tech, techM.getName()));
            }
        }
        return buttons;
    }

    public static void divertFunding(Game activeGame, Player player, String buttonID, ButtonInteractionEvent event) {
        String techOut = buttonID.split("@")[1];
        player.removeTech(techOut);
        TechnologyModel techM1 = Mapper.getTech(techOut);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                ButtonHelper.getIdent(player) + " removed the tech " + techM1.getName());
        resolveFocusedResearch(activeGame, player, buttonID, event);
        event.getMessage().delete().queue();
    }

    public static void resolveForwardSupplyBaseStep2(Player hacan, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player player = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(null, activeGame),
                    "Could not resolve target player, please resolve manually.");
            return;
        }
        int oldTg = player.getTg();
        player.setTg(oldTg + 1);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                ButtonHelper.getIdentOrColor(player, activeGame) + " gained 1tg due to forward supply base (" + oldTg
                        + "->" + player.getTg() + ")");
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(hacan, activeGame),
                    ButtonHelper.getIdentOrColor(player, activeGame) + " gained 1tg due to forward supply base");
        }
        ButtonHelperAbilities.pillageCheck(player, activeGame);
        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
        event.getMessage().delete().queue();
    }

    public static void resolveForwardSupplyBaseStep1(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        int oldTg = player.getTg();
        player.setTg(oldTg + 3);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                ButtonHelper.getIdent(player) + " gained 3tg (" + oldTg + "->" + player.getTg() + ")");
        ButtonHelperAbilities.pillageCheck(player, activeGame);
        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 3);
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("forwardSupplyBaseStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("forwardSupplyBaseStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " choose who should get 1tg", buttons);
    }

    public static void resolveReparationsStep1(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        List<Button> buttons;
        String message = player.getRepresentation(true, true) + " Click the names of the planet you wish to ready";
        buttons = Helper.getPlanetRefreshButtons(event, player, activeGame);
        Button DoneRefreshing = Button.danger("deleteButtons", "Done Readying Planets");
        buttons.add(DoneRefreshing);
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message,
                buttons);
        buttons = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("reparationsStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("reparationsStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " tell the bot who took the planet from you", buttons);
    }

    public static void resolveDiplomaticPressureStep1(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("diplomaticPressureStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("diplomaticPressureStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " tell the bot who you want to diplo pressure",
                buttons);
    }

    public static void resolveReactorMeltdownStep1(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("reactorMeltdownStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("reactorMeltdownStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " tell the bot who you want to play reactor meltdown on",
                buttons);
    }

    public static void resolveFrontlineDeployment(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        String message = player.getRepresentation(true, true)
                + " Click the names of the planet you wish to drop 3 infantry on";
        List<Button> buttons = new ArrayList<>(
                Helper.getPlanetPlaceUnitButtons(player, activeGame, "3gf", "placeOneNDone_skipbuild"));
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message,
                buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveUnexpectedAction(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {

        List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, activeGame, event, "unexpected");
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to remove token.",
                buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveDistinguished(Player player, Game activeGame, ButtonInteractionEvent event) {
        player.addSpentThing("distinguished_5");
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " added 5 votes to your vote total");
        event.getMessage().delete().queue();
    }

    public static void resolveUprisingStep1(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("uprisingStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("uprisingStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " tell the bot who's planet you want to uprise",
                buttons);
    }

    public static void resolveAssRepsStep1(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("assRepsStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("assRepsStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " tell the bot who you want to assassinate", buttons);
    }

    public static void resolveSignalJammingStep1(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("signalJammingStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("signalJammingStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " tell the bot who's cc you want to place down",
                buttons);
    }

    public static void resolveSeizeArtifactStep1(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player || !player.getNeighbouringPlayers().contains(p2)) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("seizeArtifactStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("seizeArtifactStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " tell the bot which neighbor you are stealing from",
                buttons);
    }

    public static void resolvePlagueStep1(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("plagueStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("plagueStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " tell the bot who's planet you want to plague",
                buttons);
    }

    public static void resolveGhostShipStep1(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        List<Button> buttons = getGhostShipButtons(activeGame, player);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " tell the bot which tile you wish to place a ghost ship in",
                buttons);
    }

    public static void resolveProbeStep1(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        List<Button> buttons = getProbeButtons(activeGame, player);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " tell the bot which tile you wish to probe", buttons);
    }

    public static void resolveGhostShipStep2(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Tile tile = activeGame.getTileByPosition(buttonID.split("_")[1]);
        tile = MoveUnits.flipMallice(event, tile, activeGame);
        new AddUnits().unitParsing(event, player.getColor(), tile, "destroyer", activeGame);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                ButtonHelper.getIdent(player) + " put a destroyer in " + tile.getRepresentation());
    }

    public static void resolveProbeStep2(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Tile tile = activeGame.getTileByPosition(buttonID.split("_")[1]);
        new ExpFrontier().expFront(event, tile, activeGame, player);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                ButtonHelper.getIdent(player) + " explored the DET in " + tile.getRepresentation());
    }

    public static void resolveCrippleDefensesStep1(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("crippleStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("crippleStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " tell the bot who's planet you want to cripple",
                buttons);
    }

    public static void resolveInfiltrateStep1(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("infiltrateStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("infiltrateStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " tell the bot who's planet you are infiltrating",
                buttons);
    }

    public static void resolveSpyStep1(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("spyStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("spyStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " tell the bot who you want to resolve spy on",
                buttons);
    }

    public static void resolvePSStep1(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        activeGame.setCurrentReacts("politicalStabilityFaction", player.getFaction());
        for (Integer sc : activeGame.getSCList()) {
            if (sc <= 0)
                continue; // some older games have a 0 in the list of SCs
            Emoji scEmoji = Emoji.fromFormatted(Emojis.getSCBackEmojiFromInteger(sc));
            Button button;
            String label = " ";
            if (scEmoji.getName().contains("SC") && scEmoji.getName().contains("Back")
                    && !activeGame.isHomeBrewSCMode()) {
                button = Button.secondary("psStep2_" + sc, label).withEmoji(scEmoji);
            } else {
                button = Button.secondary("psStep2_" + sc, "" + sc + label);
            }
            buttons.add(button);
        }
        if (activeGame.getRealPlayers().size() < 5) {
            buttons.add(Button.danger("deleteButtons", "Delete these buttons"));
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " tell the bot which SC(s) you used to have", buttons);
    }

    public static void resolveImpersonation(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, "inf");
        String message = ButtonHelper.getIdent(player) + " Drew Secret Objective";
        activeGame.drawSecretObjective(player.getUserID());
        if (player.hasAbility("plausible_deniability")) {
            activeGame.drawSecretObjective(player.getUserID());
            message = message + ". Drew a second SO due to plausible deniability.";
        }
        SOInfo.sendSecretObjectiveInfo(activeGame, player, event);
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        buttons.add(Button.danger("deleteButtons_spitItOut", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                player.getRepresentation() + " Exhaust stuff to pay the 3 influence", buttons);
        event.getMessage().delete().queue();
    }

    public static void resolvePSStep2(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        int scNum = Integer.parseInt(buttonID.split("_")[1]);
        player.addSC(scNum);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " you retained the SC " + scNum);
        if (activeGame.getRealPlayers().size() < 5) {
            ButtonHelper.deleteTheOneButton(event);
        } else {
            event.getMessage().delete().queue();
        }
    }

    public static void resolveInsubStep1(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player || p2.getTacticalCC() < 1) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("insubStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("insubStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true)
                        + " tell the bot which player you want to subtract a tactical cc from",
                buttons);
    }

    public static void resolveUnstableStep1(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("unstableStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("unstableStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " tell the bot who's planet you want to unstable planet",
                buttons);
    }

    public static void resolveABSStep1(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("absStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("absStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " tell the bot who's cultural planet you want to exhaust",
                buttons);
    }

    public static void resolveSalvageStep1(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("salvageStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("salvageStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " tell the bot who youre playing salvage on", buttons);
    }

    public static void resolveInsubStep2(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        p2.setTacticalCC(p2.getTacticalCC() - 1);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " you subtracted 1 tactical cc from "
                        + ButtonHelper.getIdentOrColor(p2, activeGame));
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame),
                p2.getRepresentation(true, true) + " you lost a tactic cc due to insubordination ("
                        + (p2.getTacticalCC() + 1) + "->" + p2.getTacticalCC() + ").");
        event.getMessage().delete().queue();
    }

    public static void resolveDiplomaticPressureStep2(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> stuffToTransButtons = ButtonHelper.getForcedPNSendButtons(activeGame, player, p2);
        String message = p2.getRepresentation(true, true)
                + " You have been diplo pressured. Please select the PN you would like to send";
        MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), message, stuffToTransButtons);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " sent buttons to resolve diplo pressure to "
                        + ButtonHelper.getIdentOrColor(p2, activeGame));
        event.getMessage().delete().queue();
    }

    public static void resolveABSStep2(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        for (String planet : p2.getPlanetsAllianceMode()) {
            if (planet.toLowerCase().contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            Planet p = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
            if (p != null && ("cultural".equalsIgnoreCase(p.getOriginalPlanetType())
                    || p.getTokenList().contains("attachment_titanspn.png"))) {
                p2.exhaustPlanet(planet);
            }
        }
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " you exhausted all the cultural planets of "
                        + ButtonHelper.getIdentOrColor(p2, activeGame));
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame),
                p2.getRepresentation(true, true) + " your cultural planets were exhausted due to ABS.");
        event.getMessage().delete().queue();
    }

    public static void resolveSalvageStep2(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        int comm = p2.getCommodities();
        p2.setCommodities(0);
        player.setTg(player.getTg() + comm);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " stole the commodities (there were " + comm
                        + " comms to steal )of " + ButtonHelper.getIdentOrColor(player, activeGame));
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame),
                p2.getRepresentation(true, true) + " your commodities were stolen due to salvage.");
        event.getMessage().delete().queue();
    }

    public static void resolveSpyStep2(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true)
                        + " since spy is such a frequently sabod card, and it contains secret info, extra precaution has been taken with its resolution. A button has been sent to "
                        + ButtonHelper.getIdentOrColor(p2, activeGame)
                        + " cards info thread, they can press this button to send a random AC to you.");
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("spyStep3_" + player.getFaction(), "Send random AC"));
        MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(),
                p2.getRepresentation(true, true)
                        + " you have been hit with the Spy AC. Press the button to send a random AC to the person.",
                buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveSpyStep3(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        new SentACRandom().sendRandomACPart2(event, activeGame, player, p2);
        event.getMessage().delete().queue();
    }

    public static void resolveReparationsStep2(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (p2.getReadiedPlanets().isEmpty()) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                    "Chosen player had no readied planets. This is fine and nothing more needs to be done.");
            event.getMessage().delete().queue();
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getReadiedPlanets()) {
            buttons.add(Button.secondary("reparationsStep3_" + p2.getFaction() + "_" + planet,
                    Helper.getPlanetRepresentation(planet, activeGame)));
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " select the planet you want to exhaust", buttons);
    }

    public static void resolveInsiderInformation(Player player, Game activeGame, ButtonInteractionEvent event) {
        NaaluCommander.sendTopAgendaToCardsInfoSkipCovert(activeGame, player);
        MessageHelper.sendMessageToChannel(event.getChannel(),
                "Sent top agenda info to " + ButtonHelper.getIdentOrColor(player, activeGame) + " cards info");
        event.getMessage().delete().queue();
    }

    public static void resolveEmergencyMeeting(Player player, Game activeGame, ButtonInteractionEvent event) {
        activeGame.shuffleAllAgendasBackIntoDeck();
        DrawAgenda.drawAgenda(event, 3, activeGame, player);
        MessageHelper.sendMessageToChannel(event.getChannel(),
                "Sent top 3 agendas to " + ButtonHelper.getIdentOrColor(player, activeGame) + " cards info");
        event.getMessage().delete().queue();
    }

    public static void resolveSeizeArtifactStep2(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
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
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " select the relic frag you want to grab", buttons);
    }

    public static void resolveUprisingStep2(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (p2.getReadiedPlanets().isEmpty()) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                    "Chosen player had no readied planets. Nothing has been done.");
            event.getMessage().delete().queue();
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getReadiedPlanets()) {
            buttons.add(Button.secondary("uprisingStep3_" + p2.getFaction() + "_" + planet,
                    Helper.getPlanetRepresentation(planet, activeGame)));
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " select the planet you want to exhaust", buttons);
    }

    public static void resolveAssRepsStep2(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                ButtonHelper.getIdentOrColor(player, activeGame)
                        + " successfully assassinated all the representatives of "
                        + ButtonHelper.getIdentOrColor(p2, activeGame));
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                p2.getRepresentation(true, true) + " your representatives got sent to the headsman");
        activeGame.setCurrentReacts("AssassinatedReps",
                activeGame.getFactionsThatReactedToThis("AssassinatedReps") + p2.getFaction());
    }

    public static void resolveSignalJammingStep2(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : activeGame.getTileMap().values()) {
            if (FoWHelper.playerHasShipsInSystem(player, tile)) {
                buttons.add(Button.secondary("signalJammingStep3_" + p2.getFaction() + "_" + tile.getPosition(),
                        tile.getRepresentationForButtons(activeGame, player)));
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true)
                        + " the map is a big place. Please tell the bot where the origin of the signal jam is coming from (a tile where your ships are)",
                buttons);
    }

    public static void resolveSignalJammingStep3(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String pos = buttonID.split("_")[2];
        List<Button> buttons = new ArrayList<>();
        for (String tilePos : FoWHelper.getAdjacentTilesAndNotThisTile(activeGame, pos, player, false)) {
            Tile tile = activeGame.getTileByPosition(tilePos);
            if (!tile.isHomeSystem()) {
                buttons.add(Button.secondary("signalJammingStep4_" + p2.getFaction() + "_" + tile.getPosition(),
                        tile.getRepresentationForButtons(activeGame, player)));
            }
        }
        Tile tile = activeGame.getTileByPosition(pos);
        if (!tile.isHomeSystem()) {
            buttons.add(Button.secondary("signalJammingStep4_" + p2.getFaction() + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(activeGame, player)));
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " select the tile you wish to jam.", buttons);
    }

    public static void resolveSignalJammingStep4(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String pos = buttonID.split("_")[2];
        Tile tile = activeGame.getTileByPosition(pos);
        AddCC.addCC(event, p2.getColor(), tile);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " you signal jammed the tile: "
                        + tile.getRepresentationForButtons(activeGame, player));
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame),
                p2.getRepresentation(true, true) + " you were signal jammed in tile: "
                        + tile.getRepresentationForButtons(activeGame, p2));

    }

    public static void resolveReactorMeltdownStep2(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getPlanetsAllianceMode()) {
            if (planet.contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
            if (uH.getUnitCount(UnitType.CabalSpacedock, p2.getColor()) > 0
                    || uH.getUnitCount(UnitType.Spacedock, p2.getColor()) > 0) {
                if (!activeGame.getTileFromPlanet(planet).isHomeSystem()) {
                    Tile tile = activeGame.getTileFromPlanet(planet);
                    buttons.add(Button.secondary(
                            "reactorMeltdownStep3_" + p2.getFaction() + "_" + tile.getPosition() + "_" + planet,
                            Helper.getPlanetRepresentation(planet, activeGame)));
                }
            }
        }
        if (p2.hasUnit("absol_saar_spacedock") || p2.hasUnit("saar_spacedock") || p2.hasTech("ffac2") || p2.hasTech("absol_ffac2")) {
            for (Tile tile : activeGame.getTileMap().values()) {
                if (tile.getUnitHolders().get("space").getUnitCount(UnitType.Spacedock, p2.getColor()) > 0) {
                    buttons.add(Button.secondary(
                            "reactorMeltdownStep3_" + p2.getFaction() + "_" + tile.getPosition() + "_space",
                            tile.getRepresentationForButtons(activeGame, player)));
                }
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " select the SD you want to melt", buttons);
    }

    public static void resolveReactorMeltdownStep3(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        Tile tile = activeGame.getTileByPosition(buttonID.split("_")[2]);
        String unitHolderName = buttonID.split("_")[3];
        if ("space".equalsIgnoreCase(unitHolderName)) {
            unitHolderName = "";
        }
        new RemoveUnits().unitParsing(event, p2.getColor(), tile, "sd " + unitHolderName, activeGame);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " you killed the space dock in " + tile.getRepresentation());
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame),
                p2.getRepresentation(true, true) + " your space dock in " + tile.getRepresentation() + " was melted.");
        event.getMessage().delete().queue();
    }

    public static void resolvePlagueStep2(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getPlanets()) {
            buttons.add(Button.secondary("plagueStep3_" + p2.getFaction() + "_" + planet,
                    Helper.getPlanetRepresentation(planet, activeGame)));
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " select the planet you want to plague", buttons);
    }

    public static void resolveRefitTroops(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID,
            String finChecker) {
        List<Button> buttons = new ArrayList<>(
                ButtonHelperAbilities.getPlanetPlaceUnitButtonsForMechMitosis(player, activeGame, finChecker));
        String message = player.getRepresentation(true, true) + " Use buttons to replace 1 infantry with a mech";
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message,
                buttons);
        List<Button> buttons2 = new ArrayList<>(
                ButtonHelperAbilities.getPlanetPlaceUnitButtonsForMechMitosis(player, activeGame, finChecker));
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message,
                buttons2);
        event.getMessage().delete().queue();
    }

    public static void resolveCrippleStep2(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getPlanets()) {
            buttons.add(Button.secondary("crippleStep3_" + p2.getFaction() + "_" + planet,
                    Helper.getPlanetRepresentation(planet, activeGame)));
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " select the planet you want to cripple", buttons);
    }

    public static void resolveInfiltrateStep2(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getPlanets()) {
            buttons.add(Button.secondary("infiltrateStep3_" + p2.getFaction() + "_" + planet,
                    Helper.getPlanetRepresentation(planet, activeGame)));
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " select the planet you want to infiltrate", buttons);
    }

    public static void resolveUpgrade(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Tile tile = activeGame.getTileByPosition(buttonID.split("_")[1]);
        new RemoveUnits().unitParsing(event, player.getColor(), tile, "cruiser", activeGame);
        new AddUnits().unitParsing(event, player.getColor(), tile, "dread", activeGame);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                ButtonHelper.getIdent(player) + " replaced a cruiser with a dread in " + tile.getRepresentation());
    }

    public static void checkForAssigningCoup(Game activeGame, Player player) {
        if (ButtonHelper.isPlayerElected(activeGame, player, "censure")
                || ButtonHelper.isPlayerElected(activeGame, player, "absol_censure")) {
            return;
        }
        if (player.getActionCards().containsKey("coup")) {
            activeGame.setCurrentReacts("Coup", "");
            String msg = player.getRepresentation()
                    + " you have the option to pre-assign which SC you will coup. Coup is an awkward timing window for async, so if you intend to play it, its best to pre-play it now. Feel free to ignore this message if you dont intend to play it";
            List<Button> scButtons = new ArrayList<>();
            for (Integer sc : activeGame.getSCList()) {
                if (sc <= 0)
                    continue; // some older games have a 0 in the list of SCs
                Emoji scEmoji = Emoji.fromFormatted(Emojis.getSCBackEmojiFromInteger(sc));
                Button button;
                String label = " ";
                if (scEmoji.getName().contains("SC") && scEmoji.getName().contains("Back")
                        && !activeGame.isHomeBrewSCMode()) {
                    button = Button.secondary("resolvePreassignment_Coup_" + sc, label).withEmoji(scEmoji);
                } else {
                    button = Button.secondary("resolvePreassignment_Coup_" + sc, "" + sc + label);
                }
                scButtons.add(button);
            }
            scButtons.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, scButtons);
        }

    }

    public static void checkForPlayingSummit(Game activeGame, Player player) {
        if (ButtonHelper.isPlayerElected(activeGame, player, "censure")
                || ButtonHelper.isPlayerElected(activeGame, player, "absol_censure")) {
            return;
        }
        if (player.getActionCards().containsKey("summit")) {
            String msg = player.getRepresentation()
                    + " you have the option to pre-play summit. Start of strat phase is an awkward timing window for async, so if you intend to play it, its best to pre-play it now. Feel free to ignore this message if you dont intend to play it";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("resolvePreassignment_Summit", "Pre-play Summit"));
            buttons.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        }
    }

    public static void checkForPlayingManipulateInvestments(Game activeGame, Player player) {
        if (ButtonHelper.isPlayerElected(activeGame, player, "censure")
                || ButtonHelper.isPlayerElected(activeGame, player, "absol_censure")) {
            return;
        }
        if (player.getActionCards().containsKey("investments")) {
            String msg = player.getRepresentation()
                    + " you have the option to pre-play manipulate investments. Start of strat phase is an awkward timing window for async, so if you intend to play it, its best to pre-play it now. Feel free to ignore this message if you don't intend to play it";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("resolvePreassignment_Investments", "Pre-play Manipulate Investments"));
            buttons.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        }
    }

    public static void checkForAssigningPublicDisgrace(Game activeGame, Player player) {
        if (ButtonHelper.isPlayerElected(activeGame, player, "censure")
                || ButtonHelper.isPlayerElected(activeGame, player, "absol_censure")) {
            return;
        }
        if (player.getActionCards().containsKey("disgrace")) {
            String msg = player.getRepresentation()
                    + " you have the option to pre-assign which SC you will public disgrace. Public disgrace is an awkward timing window for async, so if you intend to play it, its best to pre-play it now. Feel free to ignore this message if you dont intend to play it or are unsure of the target. You will be given the option for it to only trigger on a particular person";
            List<Button> scButtons = new ArrayList<>();
            for (Integer sc : activeGame.getSCList()) {
                if (sc <= 0)
                    continue; // some older games have a 0 in the list of SCs
                Emoji scEmoji = Emoji.fromFormatted(Emojis.getSCBackEmojiFromInteger(sc));
                Button button;
                String label = " ";
                if (scEmoji.getName().contains("SC") && scEmoji.getName().contains("Back")
                        && !activeGame.isHomeBrewSCMode()) {
                    button = Button.secondary("resolvePreassignment_Public Disgrace_" + sc, label).withEmoji(scEmoji);
                } else {
                    button = Button.secondary("resolvePreassignment_Public Disgrace_" + sc, "" + sc + label);
                }
                scButtons.add(button);
            }
            scButtons.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, scButtons);
        }
    }

    public static void resolveDecoyOperationStep1(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Tile tile = activeGame.getTileByPosition(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder uH : tile.getUnitHolders().values()) {
            if (uH instanceof Planet) {
                buttons.add(Button.success("decoyOperationStep2_" + uH.getName(),
                        Helper.getPlanetRepresentation(uH.getName(), activeGame)));
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true)
                        + " tell the bot which planet you want to resolve decoy operations on",
                buttons);
    }

    public static void resolveDecoyOperationStep2(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        String planet = buttonID.split("_")[1];
        List<Button> buttons = ButtonHelper.getButtonsForMovingGroundForcesToAPlanet(activeGame, planet, player);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " use buttons to move up to 2 troops", buttons);
    }

    public static void resolveEmergencyRepairs(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Tile tile = activeGame.getTileByPosition(buttonID.split("_")[1]);
        tile.removeAllUnitDamage(player.getColor());
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                ButtonHelper.getIdent(player) + " repaired all damaged units in " + tile.getRepresentation());
    }

    public static void resolveTacticalBombardmentStep1(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        List<Button> buttons = getTacticalBombardmentButtons(activeGame, player);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " tell the bot which tile you wish to tactically bombard in",
                buttons);
    }

    public static void resolveTacticalBombardmentStep2(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Tile tile = activeGame.getTileByPosition(buttonID.split("_")[1]);
        for (UnitHolder uH : tile.getUnitHolders().values()) {
            if (uH instanceof Planet) {
                for (Player p2 : activeGame.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    if (p2.getPlanets().contains(uH.getName())) {
                        p2.exhaustPlanet(uH.getName());
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame),
                                p2.getRepresentation() + " Your planets in " + tile.getRepresentation()
                                        + " were exhausted");
                    }
                }
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                ButtonHelper.getIdent(player) + " exhausted all enemy planets in " + tile.getRepresentation());
    }

    public static void resolveUnstableStep2(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getPlanets()) {
            if (planet.toLowerCase().contains("custodia")) {
                continue;
            }
            Planet p = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
            if (p != null && ("hazardous".equalsIgnoreCase(p.getOriginalPlanetType())
                    || p.getTokenList().contains("attachment_titanspn.png"))) {
                buttons.add(Button.secondary("unstableStep3_" + p2.getFaction() + "_" + planet,
                        Helper.getPlanetRepresentation(planet, activeGame)));
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " select the planet you want to exhaust", buttons);
    }

    public static void resolveUnstableStep3(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, activeGame);
        event.getMessage().delete().queue();
        if (p2.getReadiedPlanets().contains(planet)) {
            p2.exhaustPlanet(planet);
        }
        int amountToKill = 3;
        if (p2.hasInf2Tech()) {
            UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
            amountToKill = uH.getUnitCount(UnitType.Infantry, p2.getColor());
            if (amountToKill > 3) {
                amountToKill = 3;
            }
            ButtonHelper.resolveInfantryDeath(activeGame, p2, amountToKill);
            boolean cabalMech = false;
            Tile tile = activeGame.getTileFromPlanet(planet);
            if (p2.hasAbility("amalgamation")
                    && activeGame.getTileFromPlanet(planet).getUnitHolders().get(planet).getUnitCount(UnitType.Mech,
                            p2.getColor()) > 0
                    && p2.hasUnit("cabal_mech")
                    && !activeGame.getLaws().containsKey("articles_war")) {
                cabalMech = true;
            }
            if (p2.hasAbility("amalgamation")
                    && (ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", p2, tile) || cabalMech)
                    && FoWHelper.playerHasUnitsOnPlanet(p2, tile, planet)) {
                ButtonHelperFactionSpecific.cabalEatsUnit(p2, activeGame, p2, amountToKill, "infantry", event);
            }
        }
        if ((p2.getUnitsOwned().contains("mahact_infantry") || p2.hasTech("cl2"))) {
            ButtonHelperFactionSpecific.offerMahactInfButtons(p2, activeGame);
        }
        new RemoveUnits().unitParsing(event, p2.getColor(), activeGame.getTileFromPlanet(planet),
                amountToKill + " inf " + planet, activeGame);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " you exhausted " + planetRep
                        + " and killed up to 3 infantry there");
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame),
                p2.getRepresentation(true, true) + " your planet " + planetRep
                        + " was exhausted and up to 3 infantry were destroyed.");
    }

    public static void resolveSeizeArtifactStep3(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String fragID = buttonID.split("_")[2];
        event.getMessage().delete().queue();
        p2.removeFragment(fragID);
        player.addFragment(fragID);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " you gained the fragment " + fragID);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame),
                p2.getRepresentation(true, true) + " your fragment " + fragID + " was seized.");
    }

    public static void resolveUprisingStep3(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, activeGame);
        event.getMessage().delete().queue();
        p2.exhaustPlanet(planet);
        int resValue = Helper.getPlanetResources(planet, activeGame);
        int oldTg = player.getTg();
        player.setTg(oldTg + resValue);
        MessageHelper.sendMessageToChannel(event.getChannel(),
                ButtonHelper.getIdent(player) + " gained " + resValue + " tgs (" + oldTg + "->" + player.getTg() + ")");
        ButtonHelperAbilities.pillageCheck(player, activeGame);
        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, resValue);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " you exhausted " + planetRep);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame),
                p2.getRepresentation(true, true) + " your planet " + planetRep + " was exhausted.");
    }

    public static void resolvePlagueStep3(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, activeGame);
        event.getMessage().delete().queue();
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
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
            new RemoveUnits().removeStuff(event, activeGame.getTileFromPlanet(planet), hits, planet, key, p2.getColor(),
                    false, activeGame);
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), msg.toString());
            ButtonHelper.resolveInfantryDeath(activeGame, p2, hits);
            if ((p2.getUnitsOwned().contains("mahact_infantry") || p2.hasTech("cl2"))) {
                ButtonHelperFactionSpecific.offerMahactInfButtons(p2, activeGame);
            }
            boolean cabalMech = false;
            Tile tile = activeGame.getTileFromPlanet(planet);
            if (p2.hasAbility("amalgamation")
                    && activeGame.getTileFromPlanet(planet).getUnitHolders().get(planet).getUnitCount(UnitType.Mech,
                            p2.getColor()) > 0
                    && p2.hasUnit("cabal_mech")
                    && !activeGame.getLaws().containsKey("articles_war")) {
                cabalMech = true;
            }
            if (p2.hasAbility("amalgamation")
                    && (ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", p2, tile) || cabalMech)
                    && FoWHelper.playerHasUnitsOnPlanet(p2, tile, planet)) {
                ButtonHelperFactionSpecific.cabalEatsUnit(p2, activeGame, p2, hits, "infantry", event);
            }
        }
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " you plagued " + planetRep + " and got " + hits + " hits");
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame),
                p2.getRepresentation(true, true) + " your planet " + planetRep + " was plagued and you lost " + hits
                        + " infantry.");
    }

    public static void resolveCrippleStep3(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, activeGame);
        event.getMessage().delete().queue();
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
        int amount = uH.getUnitCount(UnitType.Pds, p2.getColor());
        if (amount > 0) {
            UnitKey key = Mapper.getUnitKey(AliasHandler.resolveUnit("pds"), p2.getColor());
            new RemoveUnits().removeStuff(event, activeGame.getTileFromPlanet(planet), amount, planet, key,
                    p2.getColor(), false, activeGame);
        }
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " you crippled " + planetRep + " and killed " + amount + " pds");
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame),
                p2.getRepresentation(true, true) + " your planet " + planetRep + " was crippled and you lost " + amount
                        + " pds.");
    }

    public static void resolveInfiltrateStep3(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, activeGame);
        event.getMessage().delete().queue();
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
        ButtonHelperModifyUnits.infiltratePlanet(player, activeGame, uH, event);
        event.getMessage().delete().queue();
    }

    public static void resolveReparationsStep3(Player player, Game activeGame, ButtonInteractionEvent event,
            String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, activeGame);
        event.getMessage().delete().queue();
        p2.exhaustPlanet(planet);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " you exhausted " + planetRep);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame),
                p2.getRepresentation(true, true) + " your planet " + planetRep + " was exhausted.");
    }

    public static void resolveFocusedResearch(Game activeGame, Player player, String buttonID,
            ButtonInteractionEvent event) {
        if (!player.hasAbility("propagation")) {
            activeGame.setComponentAction(true);
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                    player.getRepresentation(true, true) + " you can use the button to get your tech",
                    List.of(Buttons.GET_A_TECH));
        } else {
            List<Button> buttons = ButtonHelper.getGainCCButtons(player);
            String message2 = player.getRepresentation() + "! Your current CCs are " + player.getCCRepresentation()
                    + ". Use buttons to gain CCs";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
            activeGame.setCurrentReacts("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        }
        event.getMessage().delete().queue();
    }

    public static void repealLaw(Game activeGame, Player player, String buttonID, ButtonInteractionEvent event) {
        String numID = buttonID.split("_")[1];
        String name = "";
        for (String law : activeGame.getLaws().keySet()) {
            if (numID.equalsIgnoreCase("" + activeGame.getLaws().get(law))) {
                name = law;
            }
        }
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                ButtonHelper.getIdent(player) + " repealed " + Mapper.getAgendaTitle(name));
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                    Mapper.getAgendaTitle(name) + " was repealed");
        }
        activeGame.removeLaw(name);
        event.getMessage().delete().queue();
    }

    public static List<Button> getPlagiarizeButtons(Game activeGame, Player player) {
        List<String> techToGain = new ArrayList<>();
        for (Player p2 : player.getNeighbouringPlayers()) {
            techToGain = ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(player, p2, techToGain,
                    activeGame);
        }
        List<Button> techs = new ArrayList<>();
        for (String tech : techToGain) {
            if ("".equals(Mapper.getTech(AliasHandler.resolveTech(tech)).getFaction().orElse(""))) {
                techs.add(Button.success("getTech_" + Mapper.getTech(tech).getAlias() + "__noPay",
                        Mapper.getTech(tech).getName()));
            }
        }
        return techs;
    }

    public static List<Button> getGhostShipButtons(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : activeGame.getTileMap().values()) {
            if (FoWHelper.doesTileHaveWHs(activeGame, tile.getPosition())) {
                boolean hasOtherShip = false;
                for (Player p2 : activeGame.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    if (FoWHelper.playerHasShipsInSystem(p2, tile)) {
                        hasOtherShip = true;
                    }
                }
                if (!hasOtherShip) {
                    buttons.add(Button.success("ghostShipStep2_" + tile.getPosition(),
                            tile.getRepresentationForButtons(activeGame, player)));
                }
            }
        }
        return buttons;
    }

    public static List<Button> getTacticalBombardmentButtons(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesOfUnitsWithBombard(player, activeGame)) {
            buttons.add(Button.success("tacticalBombardmentStep2_" + tile.getPosition(),
                    tile.getRepresentationForButtons(activeGame, player)));
        }
        return buttons;
    }

    public static List<Button> getProbeButtons(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : activeGame.getTileMap().values()) {
            if (tile.getUnitHolders().get("space").getTokenList().contains(Mapper.getTokenID(Constants.FRONTIER))) {
                boolean hasShips = false;
                for (String tile2pos : FoWHelper.getAdjacentTilesAndNotThisTile(activeGame, tile.getPosition(), player,
                        false)) {
                    if (FoWHelper.playerHasShipsInSystem(player, activeGame.getTileByPosition(tile2pos))) {
                        hasShips = true;
                    }
                }
                if (FoWHelper.playerHasShipsInSystem(player, tile)) {
                    hasShips = true;
                }
                if (hasShips) {
                    buttons.add(Button.success("probeStep2_" + tile.getPosition(),
                            tile.getRepresentationForButtons(activeGame, player)));
                }
            }
        }
        return buttons;
    }

    public static void resolveReverse(Game activeGame, Player player, String buttonID, ButtonInteractionEvent event) {
        String acName = buttonID.split("_")[1];
        List<String> acStrings = new ArrayList<>(activeGame.getDiscardActionCards().keySet());
        for (String acStringID : acStrings) {
            ActionCardModel actionCard = Mapper.getActionCard(acStringID);
            String actionCardTitle = actionCard.getName();
            if (acName.equalsIgnoreCase(actionCardTitle)) {
                boolean picked = activeGame.pickActionCard(player.getUserID(),
                        activeGame.getDiscardActionCards().get(acStringID));
                if (!picked) {
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                            "No such Action Card ID found, please retry");
                    return;
                }
                String sb = "Game: " + activeGame.getName() + " " +
                        "Player: " + player.getUserName() + "\n" +
                        "Picked card from Discards: " +
                        Mapper.getActionCard(acStringID).getRepresentation() + "\n";
                MessageHelper.sendMessageToChannel(event.getChannel(), sb);

                ACInfo.sendActionCardInfo(activeGame, player);
            }
        }
        event.getMessage().delete().queue();
    }

    public static void economicInitiative(Player player, Game activeGame, ButtonInteractionEvent event) {
        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.toLowerCase().contains("custodia")) {
                continue;
            }
            Planet p = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
            if (p != null && ("cultural".equalsIgnoreCase(p.getOriginalPlanetType())
                    || p.getTokenList().contains("attachment_titanspn.png"))) {
                player.refreshPlanet(planet);
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(),
                ButtonHelper.getIdent(player) + " readied each cultural planet");
        event.getMessage().delete().queue();
    }

    public static void industrialInitiative(Player player, Game activeGame, ButtonInteractionEvent event) {
        int oldTg = player.getTg();
        int count = 0;
        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.toLowerCase().contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            Planet p = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
            if (p != null && ("industrial".equalsIgnoreCase(p.getOriginalPlanetType())
                    || p.getTokenList().contains("attachment_titanspn.png"))) {
                count = count + 1;
            }
        }
        player.setTg(oldTg + count);
        MessageHelper.sendMessageToChannel(event.getChannel(),
                ButtonHelper.getIdent(player) + " gained " + count + " tgs(" + oldTg + "->" + player.getTg() + ")");
        ButtonHelperAbilities.pillageCheck(player, activeGame);
        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, count);
        event.getMessage().delete().queue();
    }

    public static void miningInitiative(Player player, Game activeGame, ButtonInteractionEvent event) {
        int oldTg = player.getTg();
        int count = 0;
        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.toLowerCase().contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            Planet p = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
            if (p != null && p.getResources() > count) {
                count = p.getResources();
            }
        }
        player.setTg(oldTg + count);
        MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdent(player) + " gained " + count
                + " tgs (" + oldTg + "->" + player.getTg() + ") from their highest resource planet");
        ButtonHelperAbilities.pillageCheck(player, activeGame);
        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, count);
        event.getMessage().delete().queue();
    }
}
