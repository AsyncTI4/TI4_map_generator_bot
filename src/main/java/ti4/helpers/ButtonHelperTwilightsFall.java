package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.draft.DraftBag;
import ti4.draft.DraftItem;
import ti4.draft.InauguralSpliceFrankenDraft;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;
import ti4.model.MapTemplateModel;
import ti4.model.Source.ComponentSource;
import ti4.model.StrategyCardModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.model.TileModel;
import ti4.model.UnitModel;
import ti4.service.VeiledHeartService;
import ti4.service.button.ReactionService;
import ti4.service.draft.DraftTileManager;
import ti4.service.draft.MantisMapBuildContext;
import ti4.service.draft.MantisMapBuildService;
import ti4.service.emoji.TechEmojis;
import ti4.service.franken.FrankenDraftBagService;
import ti4.service.franken.FrankenMapBuildContextHelper;
import ti4.service.milty.MiltyDraftHelper;
import ti4.service.milty.MiltyDraftManager;
import ti4.service.milty.MiltyDraftManager.PlayerDraft;
import ti4.service.milty.MiltyDraftSlice;
import ti4.service.milty.MiltyDraftTile;
import ti4.service.milty.MiltyService;
import ti4.service.turn.EndTurnService;
import ti4.service.unit.AddUnitService;

public class ButtonHelperTwilightsFall {

    public static boolean checkForQueuedSplicePick(Player privatePlayer, Game game) {
        Player player = privatePlayer;
        String alreadyQueued = game.getStoredValue(player.getFaction() + "splicequeue");
        if (!alreadyQueued.isEmpty()) {
            String unpickedSpliceCard = "";
            for (String spliceCard : alreadyQueued.split("_")) {
                if (!player.isNpc()) {
                    game.setStoredValue(
                            player.getFaction() + "splicequeue",
                            game.getStoredValue(player.getFaction() + "splicequeue")
                                    .replace(spliceCard + "_", ""));
                }
                List<String> cards = getSpliceCards(game);
                boolean held = !cards.contains(spliceCard)
                        && !"antimatter".equalsIgnoreCase(spliceCard)
                        && !"wavelength".equalsIgnoreCase(spliceCard);
                if (held) continue;
                unpickedSpliceCard = spliceCard;
                break;
            }
            if (unpickedSpliceCard.isEmpty()) {
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(),
                        "Tried to pick your queued splice card, but they were all already taken.");
                return false;
            } else {
                MessageHelper.sendMessageToChannel(
                        privatePlayer.getCorrectChannel(),
                        privatePlayer.getRepresentation(false, false) + " had queued a splice pick.");
                selectASpliceCard(game, player, "selectASpliceCard_" + unpickedSpliceCard, null);
                return true;
            }
        }
        return false;
    }

    public static List<Button> getQueueSplicePickButtons(Game game, Player player) {
        String type = game.getStoredValue("spliceType");
        List<String> cards = getSpliceCards(game);
        List<String> nCards = new ArrayList<>(cards);
        String alreadyQueued = game.getStoredValue(player.getFaction() + "splicequeue");
        for (String cardID : alreadyQueued.split("_")) {
            nCards.remove(cardID);
        }
        List<Button> buttons = new ArrayList<>(getSpliceButtons(game, type, nCards, player, "queueSplicePick_"));
        if (alreadyQueued.isEmpty()) buttons.add(Buttons.red("deleteButtons", "Decline to Queue"));
        buttons.add(Buttons.gray("restartSpliceQueue", "Restart Queue"));
        return buttons;
    }

    public static String getQueueSpliceMessage(Game game, Player player) {
        int number = getParticipantsList(game).indexOf(player) + 1;
        String alreadyQueued = game.getStoredValue(player.getFaction() + "splicequeue");
        int numQueued = alreadyQueued.split("_").length;
        if (alreadyQueued.isEmpty()) {
            numQueued = 0;
        }
        StringBuilder msg = new StringBuilder(player.getRepresentationNoPing() + " you are #" + number
                + " pick in this splice and so can queue " + number + " cards."
                + " So far you have queued " + numQueued + " cards. ");
        if (numQueued > 0) {
            msg.append(
                    "The queued splice cards are as follows (in the order the bot will attempt to select them for you):\n");
            int count = 1;
            for (String cardID : alreadyQueued.split("_")) {
                if (cardID.isEmpty()) {
                    continue;
                }
                String type = game.getStoredValue("spliceType");
                String spliceEmoji = null;
                String name = "";
                if ("wavelength".equalsIgnoreCase(cardID) || "antimatter".equalsIgnoreCase(cardID)) {
                    name = Mapper.getTech(cardID).getName();
                    String faction = player.getFaction();
                    spliceEmoji = Mapper.getFaction(faction).getFactionEmoji();
                } else {
                    if ("ability".equalsIgnoreCase(type)) {
                        name = Mapper.getTech(cardID).getName();
                        String faction = Mapper.getTech(cardID).getFaction().orElse("neutral");
                        if (faction.contains("keleres")) {
                            faction = "keleresm";
                        }
                        spliceEmoji = Mapper.getFaction(faction).getFactionEmoji();
                    }
                    if ("genome".equalsIgnoreCase(type)) {
                        name = Mapper.getLeader(cardID).getTFNameIfAble();
                        String faction = Mapper.getLeader(cardID).getFaction();
                        if (faction.contains("keleres")) {
                            faction = "keleresm";
                        }
                        spliceEmoji = Mapper.getFaction(faction).getFactionEmoji();
                    }
                    if ("units".equalsIgnoreCase(type)) {
                        name = Mapper.getUnit(cardID).getName();
                        String faction = Mapper.getUnit(cardID).getFaction().orElse("neutral");
                        if (faction.contains("keleres")) {
                            faction = "keleresm";
                        }
                        spliceEmoji = Mapper.getFaction(faction).getFactionEmoji();
                    }
                }
                msg.append(count)
                        .append(". ")
                        .append(name)
                        .append(" ")
                        .append(spliceEmoji)
                        .append("\n");
            }
        }
        return msg.toString();
    }

    @ButtonHandler("queueSplicePick_")
    public static void queueSplicePick(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        if (getParticipantsList(game).getFirst() == player) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    "You are currently up to pick a splice card, and should just do that instead of queueing.");
            return;
        }
        String spliceCard = buttonID.split("_")[1];
        game.setStoredValue(
                player.getFaction() + "splicequeue",
                game.getStoredValue(player.getFaction() + "splicequeue") + spliceCard + "_");
        String alreadyQueued = game.getStoredValue(player.getFaction() + "splicequeue");
        int number = getParticipantsList(game).indexOf(player) + 1;
        int numQueued = alreadyQueued.split("_").length;
        if (alreadyQueued.isEmpty()) {
            numQueued = 0;
        }
        List<Button> buttons = getQueueSplicePickButtons(game, player);
        String msg = getQueueSpliceMessage(game, player);
        if (number <= numQueued || (alreadyQueued.contains("antimatter") || alreadyQueued.contains("wavelength"))) {
            msg +=
                    "You can use this button to restart if some mistake was made. Otherwise one of these cards should be selected for you when it is your turn to pick a splice card.";
            buttons = new ArrayList<>();
            buttons.add(Buttons.gray("restartSpliceQueue", "Restart Queue"));
        } else {
            msg +=
                    "You can use these buttons to queue another card in case all the ones you currently have queued are taken.";
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("restartSpliceQueue")
    public static void restartSpliceQueue(ButtonInteractionEvent event, Game game, Player player) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        game.setStoredValue(player.getFaction() + "splicequeue", "");
        List<Button> buttons = getQueueSplicePickButtons(game, player);
        String msg = getQueueSpliceMessage(game, player);
        msg += "You can use these buttons to queue your splice pick.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("startFrankenSliceBuild")
    public static void startSliceBuild(Game game, GenericInteractionCreateEvent event) {
        try {
            MiltyDraftManager manager = game.getMiltyDraftManager();
            List<String> playerIDs = new ArrayList<>();
            for (Player p : game.getRealPlayers()) {
                playerIDs.add(p.getUserID());
            }
            manager.setPlayers(playerIDs);
            List<DraftItem.Category> componentCategories = game.isTwilightsFallMode()
                    ? FrankenDraftBagService.TFcomponentCategories
                    : List.of(
                            DraftItem.Category.DRAFTORDER,
                            DraftItem.Category.HOMESYSTEM,
                            DraftItem.Category.STARTINGFLEET,
                            DraftItem.Category.BLUETILE,
                            DraftItem.Category.REDTILE);
            for (Player p : game.getPlayers().values()) {
                DraftBag bag = p.getDraftHand();
                PlayerDraft draft = manager.getPlayerDraft(p);
                List<MiltyDraftTile> slice = new ArrayList<>();
                for (DraftItem.Category category : componentCategories) {
                    List<DraftItem> items = bag.Contents.stream()
                            .filter(item -> item.ItemCategory == category)
                            .toList();
                    if (items.isEmpty()) {
                        continue;
                    }
                    List<Button> buttons = new ArrayList<>();
                    if (category == DraftItem.Category.DRAFTORDER) {
                        draft.setPosition(Integer.parseInt(items.getFirst().ItemId));
                        if (Integer.parseInt(items.getFirst().ItemId) == 1) {
                            game.setSpeaker(p);
                        }
                    }
                    if (category == DraftItem.Category.HOMESYSTEM) {
                        draft.setFaction(items.getFirst().ItemId);
                        for (DraftItem item : items) {
                            buttons.add(Buttons.green("chooseHomeSystem_" + item.ItemId, item.getShortDescription()));
                            game.setStoredValue(
                                    "draftedHSFor" + p.getUserID(),
                                    game.getStoredValue("draftedHSFor" + p.getUserID()) + "_" + item.ItemId);
                        }
                        MessageHelper.sendMessageToChannel(
                                p.getCardsInfoThread(),
                                p.getRepresentation() + ", please choose your home system tile.",
                                buttons);
                    }
                    if (category == DraftItem.Category.STARTINGFLEET) {
                        for (DraftItem item : items) {
                            buttons.add(
                                    Buttons.green("chooseStartingFleet_" + item.ItemId, item.getShortDescription()));
                        }
                        MessageHelper.sendMessageToChannel(
                                p.getCardsInfoThread(),
                                p.getRepresentation()
                                        + ", after choosing your home system, please choose your starting units.",
                                buttons);
                    }
                    if (category == DraftItem.Category.BLUETILE || category == DraftItem.Category.REDTILE) {
                        for (DraftItem item : items) {
                            TileModel tile = TileHelper.getTileById(item.ItemId);
                            slice.add(DraftTileManager.getDraftTileFromModel(tile));
                        }
                    }
                }
                Collections.shuffle(slice);
                if (slice.get(1).getTile().getPlanetUnitHolders().isEmpty()
                        || slice.get(1).getTile().isAnomaly()) {
                    Collections.rotate(slice, 1);
                    if (slice.get(1).getTile().getPlanetUnitHolders().isEmpty()
                            || slice.get(1).getTile().isAnomaly()) {
                        Collections.rotate(slice, 1);
                    }
                }
                MiltyDraftSlice mslice = new MiltyDraftSlice();
                mslice.setTiles(slice);
                draft.setSlice(mslice);
            }
            MiltyDraftHelper.buildPartialMap(game, event);
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(event, game), "err", e);
        }
    }

    @ButtonHandler("startFrankenMantisBuild")
    public static void startMantisBuild(Game game, GenericInteractionCreateEvent event) {
        try {
            MiltyDraftManager manager = game.getMiltyDraftManager();
            List<String> playerIDs = new ArrayList<>();
            for (Player p : game.getRealPlayers()) {
                playerIDs.add(p.getUserID());
            }
            manager.setPlayers(playerIDs);
            List<DraftItem.Category> componentCategories = game.isTwilightsFallMode()
                    ? FrankenDraftBagService.TFcomponentCategories
                    : List.of(
                            DraftItem.Category.DRAFTORDER,
                            DraftItem.Category.HOMESYSTEM,
                            DraftItem.Category.STARTINGFLEET);
            for (Player p : game.getPlayers().values()) {
                DraftBag bag = p.getDraftHand();
                PlayerDraft draft = manager.getPlayerDraft(p);
                for (DraftItem.Category category : componentCategories) {
                    List<DraftItem> items = bag.Contents.stream()
                            .filter(item -> item.ItemCategory == category)
                            .toList();
                    if (items.isEmpty()) {
                        continue;
                    }
                    List<Button> buttons = new ArrayList<>();
                    if (category == DraftItem.Category.DRAFTORDER) {
                        draft.setPosition(Integer.parseInt(items.getFirst().ItemId));
                        if (Integer.parseInt(items.getFirst().ItemId) == 1) {
                            game.setSpeaker(p);
                        }
                    }
                    if (category == DraftItem.Category.HOMESYSTEM) {
                        draft.setFaction(items.getFirst().ItemId);
                        for (DraftItem item : items) {
                            buttons.add(Buttons.green("chooseHomeSystem_" + item.ItemId, item.getShortDescription()));
                            game.setStoredValue(
                                    "draftedHSFor" + p.getUserID(),
                                    game.getStoredValue("draftedHSFor" + p.getUserID()) + "_" + item.ItemId);
                        }
                        MessageHelper.sendMessageToChannel(
                                p.getCardsInfoThread(),
                                p.getRepresentation() + " choose your starting home system",
                                buttons);
                    }
                    if (category == DraftItem.Category.STARTINGFLEET) {
                        for (DraftItem item : items) {
                            buttons.add(
                                    Buttons.green("chooseStartingFleet_" + item.ItemId, item.getShortDescription()));
                        }
                        MessageHelper.sendMessageToChannel(
                                p.getCardsInfoThread(),
                                p.getRepresentation() + " after choosing your home system, choose your starting fleet",
                                buttons);
                    }
                }
            }

            // Ensure map template is set
            String mapTemplate = game.getMapTemplateID();
            if (mapTemplate == null || "null".equals(mapTemplate)) {
                MapTemplateModel defaultTemplate = Mapper.getDefaultMapTemplateForPlayerCount(
                        manager.getPlayers().size());
                if (defaultTemplate == null) {
                    throw new Exception("idk how to build this map yet: " + game.getName() + ", players: "
                            + manager.getPlayers().size());
                }
                game.setMapTemplateID(defaultTemplate.getAlias());
                manager.setMapTemplate(defaultTemplate.getAlias());
            }

            // Place draft tiles
            MiltyDraftHelper.buildPartialMap(game, event);

            // Send buttons for map build
            MantisMapBuildContext mapBuildContext = FrankenMapBuildContextHelper.createContext(game);
            MantisMapBuildService.initializeMapBuilding(mapBuildContext);
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(event, game), "err", e);
        }
    }

    @ButtonHandler("chooseStartingFleet_")
    public static void chooseStartingFleet(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String factionFleet = buttonID.split("_")[1];

        String pos = "";
        for (String faction :
                game.getStoredValue("draftedHSFor" + player.getUserID()).split("_")) {
            if (!faction.isEmpty() && Mapper.getFaction(faction).getHomeSystem() != null) {
                if (game.getTile(Mapper.getFaction(faction).getHomeSystem()) != null) {
                    pos = game.getTile(Mapper.getFaction(faction).getHomeSystem())
                            .getPosition();
                }
            }
        }

        Tile tile = game.getTileByPosition(pos);

        if (!pos.isEmpty()) {
            String unitList = Mapper.getFaction(factionFleet).getStartingFleet();
            AddUnitService.addUnitsToDefaultLocations(event, tile, game, player.getColor(), unitList);

            for (Planet plan : tile.getPlanetUnitHolders()) {
                player.refreshPlanet(plan.getName());
            }

            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentation() + ", you've set your starting units successfully.");
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentation() + ", I couldn't figure out the starting units you wanted, sorry.");
        }

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("chooseHomeSystem_")
    public static void chooseHomeSystem(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String factionHS = buttonID.split("_")[1];

        String pos = "";
        for (String faction :
                game.getStoredValue("draftedHSFor" + player.getUserID()).split("_")) {
            if (!faction.isEmpty() && Mapper.getFaction(faction).getHomeSystem() != null) {
                if (game.getTile(Mapper.getFaction(faction).getHomeSystem()) != null) {
                    pos = game.getTile(Mapper.getFaction(faction).getHomeSystem())
                            .getPosition();
                }
            }
        }
        String positionHS = pos;
        String faction = factionHS;
        String tileID = Mapper.getFaction(factionHS).getHomeSystem();
        tileID = AliasHandler.resolveTile(tileID);

        if (!pos.isEmpty()) {

            if (game.getTileByPosition(pos) != null) {
                for (UnitHolder planet :
                        game.getTileByPosition(pos).getUnitHolders().values()) {
                    if (player.getPlanets().contains(planet.getName())) {
                        player.removePlanet(planet.getName());
                    }
                }
            }
            Tile toAdd = new Tile(tileID, pos);
            game.setTile(toAdd);
            player.setHomeSystemPosition(pos);
            player.setPlayerStatsAnchorPosition(pos);
            MiltyService.setupExtraFactionTiles(game, player, faction, positionHS, toAdd);
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentation() + ", you've set your home system tile successfully.");
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentation() + ", I couldn't figure out that home system tile, sorry.");
        }

        ButtonHelper.deleteMessage(event);
    }

    // @ButtonHandler("initiateASplice_")
    public static void initiateASplice(Game game, Player startPlayer, String buttonID, List<Player> participants) {
        String spliceType = buttonID;
        if (buttonID.contains("_")) {
            spliceType = buttonID.split("_")[1];
        }
        game.setStoredValue("spliceType", spliceType);
        if (!game.getStoredValue("reverseSpliceOrder").isEmpty()) {
            game.removeStoredValue("reverseSpliceOrder");
        } else {
            Collections.reverse(participants);
            Collections.rotate(participants, 1);
        }
        {
            String engineerACSplice = game.getStoredValue("engineerACSplice");
            if ("take_remove_remove".equals(engineerACSplice)) {
                participants.addFirst(startPlayer);
                participants.addFirst(startPlayer);
            } else if (!engineerACSplice.isEmpty()) {
                // Cleans up any dirty values left over from e.g. playing Engineer without finishing the splice
                game.removeStoredValue("engineerACSplice");
            }
        }
        if (!game.getStoredValue("paid6ForSplice").isEmpty()) {
            participants.add(startPlayer);
            game.removeStoredValue("paid6ForSplice");
        }
        int size = 1 + participants.size();
        // left here for legacy, remove when Jan 2026 occurs
        if (!game.getStoredValue("researchagentSplice").isEmpty()) {
            size += 3;
            game.removeStoredValue("researchagentSplice");
        }
        for (Player p : game.getRealPlayers()) {
            if (!game.getStoredValue("researchagentSplice" + p.getFaction()).isEmpty()) {
                size += 3;
                game.removeStoredValue("researchagentSplice" + p.getFaction());
            }
        }
        game.removeStoredValue("savedParticipants");
        setNewSpliceCards(game, spliceType, size);

        for (Player p : participants) {
            if (game.getStoredValue("savedParticipants").isEmpty()) {
                game.setStoredValue("savedParticipants", p.getFaction());
            } else {
                game.setStoredValue(
                        "savedParticipants", game.getStoredValue("savedParticipants") + "_" + p.getFaction());
            }
        }
        List<String> cards = getSpliceCards(game);
        List<MessageEmbed> embeds = getSpliceEmbeds(game, spliceType, cards, null);

        MessageHelper.sendMessageToChannel(startPlayer.getCorrectChannel(), "A splice has started.");

        sendPlayerSpliceOptions(game, startPlayer);
        for (Player player2 : getParticipantsList(game)) {
            if (player2 == startPlayer || game.isFowMode() || game.isVeiledHeartMode()) {
                continue;
            }
            game.setStoredValue(player2.getFaction() + "splicequeue", "");
            String msg = player2.getRepresentationUnfogged()
                    + " in order to speed up the splice, you can now offer the bot a ranked list of your desired"
                    + " splice cards, which it will pick for you when it's your turn to pick. If you do not wish to, that is fine, just decline.";
            MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(), msg);
            MessageHelper.sendMessageToChannelWithButtons(
                    player2.getCardsInfoThread(),
                    getQueueSpliceMessage(game, player2),
                    getQueueSplicePickButtons(game, player2));
        }
    }

    public static List<String> getSpliceCards(Game game) {
        return List.of(game.getStoredValue("savedSpliceCards").split("_"));
    }

    public static List<Player> getParticipantsList(Game game) {
        List<Player> players = new ArrayList<>();
        for (String faction : game.getStoredValue("savedParticipants").split("_")) {
            Player p = game.getPlayerFromColorOrFaction(faction);
            if (p != null) {
                players.add(p);
            }
        }
        return players;
    }

    public static void sendPlayerSpliceOptions(Game game, Player player) {
        String type = game.getStoredValue("spliceType");

        List<String> cards = getSpliceCards(game);
        List<Button> buttons = getSpliceButtons(game, type, cards, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", unfortunately, there are no more splice cards remaining."
                            + " Please reimburse yourself any costs associated with the splice, using the `/player cc` command."
                            + " Same for anyone else after you in the splice.");
        } else {
            List<MessageEmbed> embeds = getSpliceEmbeds(game, type, cards, player);
            String msg = player.getRepresentationUnfogged() + ", please choose the card you wish to splice.";
            if (game.getStoredValue("engineerACSplice").startsWith("remove")) {
                msg = player.getRepresentationUnfogged() + ", please choose a card to remove from the splice.";
            }
            if (player.isNpc()) {
                selectASpliceCard(
                        game,
                        player,
                        buttons.getFirst().getCustomId().replace(player.getFinsFactionCheckerPrefix(), ""),
                        null);
            } else {
                if (game.isVeiledHeartMode()) {
                    MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                            player.getCardsInfoThread(), msg, embeds, buttons);
                } else {
                    MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                            player.getCorrectChannel(), msg, embeds, buttons);
                }
            }
        }
    }

    @ButtonHandler("participateInSplice_")
    public static void participateInSplice(Game game, Player player, String buttonID, ButtonInteractionEvent event) {

        int splice = Integer.parseInt(buttonID.split("_")[1]);
        game.setStoredValue(
                "willParticipateInSplice", game.getStoredValue("willParticipateInSplice") + "_" + player.getFaction());

        ButtonHelperSCs.scFollow(game, player, event, buttonID);

        if (splice == 7) {
            List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "both");
            Button DoneExhausting = Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets");
            buttons.add(DoneExhausting);
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", please pay the 3 resources or 3 influence.",
                    buttons);
        }
        if (splice == 6) {
            List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
            Button DoneExhausting = Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets");
            buttons.add(DoneExhausting);
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(), player.getRepresentation() + ", please pay 4 resources.", buttons);
        }
        ButtonHelper.sendMessageToRightStratThread(
                player,
                game,
                player.getRepresentationNoPing() + " will participate in the splice.",
                ButtonHelper.getStratName(splice));

        // Some message in SC thread to say they are participating?

    }

    @ButtonHandler("startSplice_")
    public static void startSplice(Game game, Player player, String buttonID, ButtonInteractionEvent event) {

        int splice = Integer.parseInt(buttonID.split("_")[1]);
        String spliceType = "ability";
        if (splice == 2) {
            spliceType = "genome";
        }
        if (splice == 6) {
            spliceType = "units";
        }
        List<Player> participants = new ArrayList<>();
        List<Player> fullOrder = Helper.getSpeakerOrFullPriorityOrderFromPlayer(player, game);
        if (buttonID.contains("all")) {
            participants.addAll(fullOrder);
            ButtonHelper.deleteMessage(event);
        } else {
            for (Player p : game.getRealPlayers()) {
                if (!p.getFollowedSCs().contains(splice) && !p.getFaction().equals(player.getFaction())) {
                    MessageHelper.sendEphemeralMessageToEventChannel(
                            event,
                            p.getRepresentation()
                                    + " has not yet chosen whether to participate in the splice, so the splice cannot proceed.");
                    return;
                }
            }
            for (Player p : fullOrder) {
                if (game.getStoredValue("willParticipateInSplice").contains(p.getFaction())
                        || p.getFaction().equals(player.getFaction())) {
                    participants.add(p);
                }
            }
            game.removeStoredValue("willParticipateInSplice");
        }
        initiateASplice(game, player, spliceType, participants);
    }

    public static void triggerYellowUnits(Game game, Player player) {
        if (player.hasUnit("yellowtf_flagship")
                && !ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Mech, UnitType.Flagship)
                        .isEmpty()) {
            String message = player.getRepresentationUnfogged()
                    + ", please resolve your mech and flagship abilities using these buttons. "
                    + "Each mech triggers once, and the flagship has to do convert 2 commodities or gain 2 commodities.";
            List<Button> buttons = ButtonHelperFactionSpecific.gainOrConvertCommButtons(player, false);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        }
        if (player.hasUnit("blacktf_mech")) {
            int numMechs = 0;
            for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Mech)) {
                boolean validPos = false;
                for (String pos : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, true)) {
                    if (FoWHelper.otherPlayersHaveUnitsInSystem(player, game.getTileByPosition(pos), game)) {
                        validPos = true;
                        break;
                    }
                }
                if (validPos) {
                    for (UnitHolder uH : tile.getUnitHolders().values()) {
                        numMechs += uH.getUnitCount(UnitType.Mech, player);
                    }
                }
            }
            if (numMechs > 0) {
                AddUnitService.addUnits(null, player.getNomboxTile(), game, player.getColor(), numMechs + " infantry");
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentation() + " captured " + numMechs + " infantry with their mechs.");
            }
        }
    }

    @ButtonHandler("discardTech_")
    public static void discardTech(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String cardID = buttonID.split("_")[1];
        player.removeTech(cardID);
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.getRepresentation() + " has discarded the ability: "
                        + Mapper.getTech(cardID).getName(),
                Mapper.getTech(cardID).getRepresentationEmbed());
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("discardAgent_")
    public static void discardAgent(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String cardID = buttonID.split("_")[1];
        player.removeLeader(cardID);
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.getRepresentation() + " has discarded the genome: "
                        + Mapper.getLeader(cardID).getName(),
                Mapper.getLeader(cardID).getRepresentationEmbed(true));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("fixMahactColors")
    public static void fixMahactColors(Game game, GenericInteractionCreateEvent event) {

        // ColorChangeHelper.changePlayerColor(game, player, oldColor, newColor);
        for (Player player : game.getRealPlayers()) {
            String factionColor = player.getFaction().replace("tf", "");
            if (Mapper.getColor(factionColor) != null && !player.getColor().equalsIgnoreCase(factionColor)) {
                Player p2 = game.getPlayerFromColorOrFaction(factionColor);
                if (p2 != null) {
                    ColorChangeHelper.changePlayerColor(
                            game,
                            p2,
                            p2.getColor(),
                            game.getUnusedColors().getFirst().getAlias());
                }
                ColorChangeHelper.changePlayerColor(game, player, player.getColor(), factionColor);
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("selectASpliceCard_")
    public static void selectASpliceCard(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String cardID = buttonID.split("_")[1];
        boolean remove;
        String[] engineerACSplice = game.getStoredValue("engineerACSplice").split("_", 2);
        if (engineerACSplice.length > 1) {
            game.setStoredValue("engineerACSplice", engineerACSplice[1]);
        } else {
            game.removeStoredValue("engineerACSplice");
        }
        remove = "remove".equals(engineerACSplice[0]);

        String type = game.getStoredValue("spliceType");
        if ("antimatter".equalsIgnoreCase(cardID) || "wavelength".equalsIgnoreCase(cardID)) {
            player.addTech(cardID);

            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has chosen to get the _"
                            + Mapper.getTech(cardID).getName() + "_ generic ability instead of splicing.",
                    Mapper.getTech(cardID).getRepresentationEmbed());
            triggerYellowUnits(game, player);
        } else {
            if (game.getStoredValue("savedSpliceCards").contains(cardID + "_")) {
                game.setStoredValue(
                        "savedSpliceCards",
                        game.getStoredValue("savedSpliceCards").replace(cardID + "_", ""));
            } else {
                if (game.getStoredValue("savedSpliceCards").contains("_" + cardID)) {
                    game.setStoredValue(
                            "savedSpliceCards",
                            game.getStoredValue("savedSpliceCards").replace("_" + cardID, ""));
                } else {
                    game.setStoredValue(
                            "savedSpliceCards",
                            game.getStoredValue("savedSpliceCards").replace(cardID, ""));
                }
            }
            if (remove) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentation() + " has removed a spliced card from the draft.");
            } else {
                if (!game.isVeiledHeartMode()) {
                    if ("ability".equalsIgnoreCase(type)) {
                        player.addTech(cardID);
                        MessageHelper.sendMessageToChannelWithEmbed(
                                player.getCorrectChannel(),
                                player.getRepresentation() + " has spliced in the _"
                                        + Mapper.getTech(cardID).getName() + "_ ability.",
                                Mapper.getTech(cardID).getRepresentationEmbed());
                    }
                    if ("genome".equalsIgnoreCase(type)) {
                        player.addLeader(cardID);
                        MessageHelper.sendMessageToChannelWithEmbed(
                                player.getCorrectChannel(),
                                player.getRepresentation() + " has spliced in the "
                                        + Mapper.getLeader(cardID).getTFNameIfAble() + " genome.",
                                Mapper.getLeader(cardID).getRepresentationEmbed(false, true, false, false, true));
                    }
                    if ("units".equalsIgnoreCase(type)) {
                        UnitModel unitModel = Mapper.getUnit(cardID);
                        String asyncId = unitModel.getAsyncId();
                        if (!"fs".equalsIgnoreCase(asyncId) && !"mf".equalsIgnoreCase(asyncId)) {
                            List<UnitModel> unitsToRemove = player.getUnitsByAsyncID(asyncId).stream()
                                    .filter(unit -> unit.getFaction().isEmpty()
                                            || unit.getUpgradesFromUnitId().isEmpty())
                                    .toList();
                            for (UnitModel u : unitsToRemove) {
                                player.removeOwnedUnitByID(u.getId());
                            }
                        }
                        player.addOwnedUnitByID(cardID);
                        MessageHelper.sendMessageToChannelWithEmbed(
                                player.getCorrectChannel(),
                                player.getRepresentation() + " has spliced in the "
                                        + Mapper.getUnit(cardID).getName() + " unit upgrade.",
                                Mapper.getUnit(cardID).getRepresentationEmbed());
                    }
                } else {
                    game.setStoredValue(
                            "veiledCards" + player.getFaction(),
                            game.getStoredValue("veiledCards" + player.getFaction()) + cardID + "_");
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            player.getRepresentationNoPing()
                                    + " has spliced in a secret card. They may put it into play with a button in their `#cards-info` thread.");
                }
                if (!buttonID.contains("spoof_")) {
                    triggerYellowUnits(game, player);
                }
            }
        }
        if (!buttonID.contains("spoof_")) {
            List<Player> participants = getParticipantsList(game);
            participants.remove(player);
            game.removeStoredValue("savedParticipants");
            if (!participants.isEmpty()) {
                for (Player p : participants) {
                    if (game.getStoredValue("savedParticipants").isEmpty()) {
                        game.setStoredValue("savedParticipants", p.getFaction());
                    } else {
                        game.setStoredValue(
                                "savedParticipants", game.getStoredValue("savedParticipants") + "_" + p.getFaction());
                    }
                }
                if (!checkForQueuedSplicePick(participants.getFirst(), game)) {
                    sendPlayerSpliceOptions(game, participants.getFirst());
                }
            } else {
                MessageHelper.sendMessageToChannel(
                        game.getMainGameChannel(), game.getPing() + ", the splice is complete.");
                if (!game.getStoredValue("endTurnWhenSpliceEnds").isEmpty()) {
                    Player p2 = game.getActivePlayer();
                    if (game.getStoredValue("endTurnWhenSpliceEnds").contains(p2.getFaction())) {
                        EndTurnService.endTurnAndUpdateMap(event, game, p2);
                    }
                    game.setStoredValue("endTurnWhenSpliceEnds", "");
                }
                game.removeStoredValue("willParticipateInSplice");
            }
            ButtonHelper.deleteMessage(event);
        }
    }

    @ButtonHandler("revealVeiledCards")
    public static void revealVeiledCards(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        List<MessageEmbed> embeds = new ArrayList<>();
        for (String card :
                game.getStoredValue("veiledCards" + player.getFaction()).split("_")) {
            if (card == null || card.isEmpty()) {
                continue;
            }
            if (Mapper.getTech(card) != null) {
                buttons.add(Buttons.green(
                        "revealSpecificVeiledCard_ability_" + card,
                        Mapper.getTech(card).getName()));
                embeds.add(Mapper.getTech(card).getRepresentationEmbed());
            }
            if (Mapper.getUnit(card) != null) {
                buttons.add(Buttons.gray(
                        "revealSpecificVeiledCard_units_" + card,
                        Mapper.getUnit(card).getName()));
                embeds.add(Mapper.getUnit(card).getRepresentationEmbed());
            }
            LeaderModel leaderModel = Mapper.getLeader(card);
            if (leaderModel != null) {
                if ("agent".equalsIgnoreCase(leaderModel.getType())) {
                    buttons.add(Buttons.blue(
                            "revealSpecificVeiledCard_genome_" + card, leaderModel.getLeaderPositionAndFaction()));
                    embeds.add(leaderModel.getRepresentationEmbed(true));
                }
                if ("hero".equalsIgnoreCase(leaderModel.getType())) {
                    buttons.add(Buttons.red(
                            "revealSpecificVeiledCard_paradigm_" + card, leaderModel.getLeaderPositionAndFaction()));
                    embeds.add(leaderModel.getRepresentationEmbed(true));
                }
            }
        }
        if (!buttons.isEmpty()) {
            buttons.add(Buttons.red("deleteButtons", "Done"));
            MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                    player.getCardsInfoThread(),
                    player.getRepresentation() + ", please choose a card to reveal.",
                    embeds,
                    buttons);
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), player.getRepresentation() + ", you have no veiled cards.");
        }
    }

    @ButtonHandler("revealSpecificVeiledCard_")
    public static void revealSpecificVeiledCard(
            Game game, String buttonID, Player player, GenericInteractionCreateEvent event) {
        String type = buttonID.split("_")[1];
        String cardID = buttonID.split("_")[2];
        if ("ability".equalsIgnoreCase(type)) {
            player.addTech(cardID);
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has unveiled the ability: "
                            + Mapper.getTech(cardID).getName(),
                    Mapper.getTech(cardID).getRepresentationEmbed());
        }
        if ("genome".equalsIgnoreCase(type)) {
            player.addLeader(cardID);
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has unveiled the genome: "
                            + Mapper.getLeader(cardID).getName(),
                    Mapper.getLeader(cardID).getRepresentationEmbed(true));
        }
        if ("paradigm".equalsIgnoreCase(type)) {
            player.addLeader(cardID);
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has unveiled the paradigm: "
                            + Mapper.getLeader(cardID).getName(),
                    Mapper.getLeader(cardID).getRepresentationEmbed(true));
            player.getLeaderByID(cardID).get().setLocked(false);
        }
        if ("units".equalsIgnoreCase(type)) {
            UnitModel unitModel = Mapper.getUnit(cardID);
            String asyncId = unitModel.getAsyncId();
            if (!"fs".equalsIgnoreCase(asyncId) && !"mf".equalsIgnoreCase(asyncId)) {
                List<UnitModel> unitsToRemove = player.getUnitsByAsyncID(asyncId).stream()
                        .filter(unit -> unit.getFaction().isEmpty()
                                || unit.getUpgradesFromUnitId().isEmpty())
                        .toList();
                for (UnitModel u : unitsToRemove) {
                    player.removeOwnedUnitByID(u.getId());
                }
            }
            player.addOwnedUnitByID(cardID);
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has unveiled the unit upgrade: "
                            + Mapper.getUnit(cardID).getName(),
                    Mapper.getUnit(cardID).getRepresentationEmbed());
        }

        game.setStoredValue(
                "veiledCards" + player.getFaction(),
                game.getStoredValue("veiledCards" + player.getFaction()).replace(cardID + "_", ""));
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("drawParadigm")
    public static void drawParadigm(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        if (buttonID.contains("AC")) {
            drawParadigm(game, player, event, false);
            ButtonHelper.deleteMessage(event);
        } else {
            drawParadigm(game, player, event, true);
        }
    }

    @ButtonHandler("addMagusSpliceCard")
    public static void addMagusSpliceCard(Game game, Player player, ButtonInteractionEvent event) {
        game.setStoredValue("paid6ForSplice", "yes");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                "The **Magus** holder has chosen to pay the 3 resources and 3 influence for an extra draw.");
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "both");
        Button DoneExhausting = Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets");
        buttons.add(DoneExhausting);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), "Please pay the 3 resources and 3 influence.", buttons);
    }

    public static void drawParadigm(Game game, Player player, ButtonInteractionEvent event, boolean scPara) {

        if (scPara) {
            String messageID = event.getMessageId();
            boolean used = ButtonHelperSCs.addUsedSCPlayer(messageID, game, player);
            StrategyCardModel scModel = game.getStrategyCardModelByInitiative(8).get();
            if (!player.getFollowedSCs().contains(scModel.getInitiative())) {
                ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scModel.getInitiative(), game, event);
            }

            if (!used
                    && (scModel.usesAutomationForSCID("pok8imperial") || scModel.usesAutomationForSCID("tf8"))
                    && !player.getFollowedSCs().contains(scModel.getInitiative())
                    && game.getPlayedSCs().contains(scModel.getInitiative())) {
                int scNum = scModel.getInitiative();
                player.addFollowedSC(scNum, event);
                ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
                if (player.getStrategicCC() > 0) {
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed **Aeterna**");
                }

                String message = ButtonHelperSCs.deductCC(game, player, scNum);
                ReactionService.addReaction(event, game, player, message);
            }
        }

        List<String> allCards = Mapper.getDeck("tf_paradigm").getNewShuffledDeck();
        List<String> alreadyDrawn =
                List.of(game.getStoredValue("savedParadigms").split("_"));
        for (String card : alreadyDrawn) {
            if (card.equalsIgnoreCase("hacanhero")) {
                allCards.remove("sanctionhero");
            }
            allCards.remove(card);
        }
        String paradigm = allCards.removeFirst();
        drawSpecificParadigm(game, player, paradigm);
        if (!scPara && "agenda".equalsIgnoreCase(game.getPhaseOfGame())) {
            if (game.getStoredValue("artificeParadigms").isEmpty()) {
                game.setStoredValue("artificeParadigms", paradigm);
            } else {
                game.setStoredValue("artificeParadigms", game.getStoredValue("artificeParadigms") + "_" + paradigm);
            }
        }
    }

    public static boolean drawSpecificParadigm(
            Game game, Player player, String paradigm, boolean checkDeck, boolean checkDrawn) {
        if (checkDeck && !Mapper.getDeck("tf_paradigm").getNewDeck().contains(paradigm)) {
            return false;
        }
        if (checkDrawn
                && List.of(game.getStoredValue("savedParadigms").split("_")).contains(paradigm)) {
            return false;
        }
        drawSpecificParadigm(game, player, paradigm);
        return true;
    }

    public static void drawSpecificParadigm(Game game, Player player, String paradigm) {
        String valueToStore = game.getStoredValue("savedParadigms");
        if (!valueToStore.isEmpty()) {
            valueToStore += "_";
        }
        valueToStore += paradigm;
        game.setStoredValue("savedParadigms", valueToStore);

        MessageHelper.sendMessageToChannelWithEmbed(
                game.isVeiledHeartMode() ? player.getCardsInfoThread() : player.getCorrectChannel(),
                player.getRepresentation() + " has drawn a new paradigm: "
                        + Mapper.getLeader(paradigm).getName(),
                Mapper.getLeader(paradigm).getRepresentationEmbed(false, true, false, false, true));
        if (game.isVeiledHeartMode()) {
            VeiledHeartService.doAction(
                    VeiledHeartService.VeiledCardAction.DRAW,
                    VeiledHeartService.VeiledCardType.PARADIGM,
                    player,
                    paradigm);
        } else {
            player.addLeader(paradigm);
            player.getLeaderByID(paradigm).get().setLocked(false);
        }
    }

    public static List<Button> getSpliceButtons(Game game, String type, List<String> cards, Player player) {
        return getSpliceButtons(game, type, cards, player, "selectASpliceCard_");
    }

    public static List<Button> getSpliceButtons(
            Game game, String type, List<String> cards, Player player, String prefix) {
        List<Button> buttons = new ArrayList<>();
        if ("ability".equalsIgnoreCase(type)) {
            for (String card : cards) {
                String name = Mapper.getTech(card).getName();
                buttons.add(Buttons.green(
                        player.getFinsFactionCheckerPrefix() + prefix + card,
                        name,
                        Mapper.getTech(card).getSingleTechEmoji()));
            }
        }
        if ("genome".equalsIgnoreCase(type)) {
            for (String card : cards) {
                String name = Mapper.getLeader(card).getTFNameIfAble();
                String faction = Mapper.getLeader(card).getFaction();
                if (faction.contains("keleres")) {
                    faction = "keleresm";
                }
                FactionModel factionModel = Mapper.getFaction(faction);
                buttons.add(Buttons.green(
                        player.getFinsFactionCheckerPrefix() + prefix + card, name, factionModel.getFactionEmoji()));
            }
        }
        if ("units".equalsIgnoreCase(type)) {
            for (String card : cards) {
                String name = Mapper.getUnit(card).getName();
                buttons.add(Buttons.green(
                        player.getFinsFactionCheckerPrefix() + prefix + card,
                        name,
                        Mapper.getUnit(card).getUnitEmoji()));
            }
        }
        if (!game.getStoredValue("engineerACSplice").startsWith("remove")) {
            if (!player.hasTech("wavelength")) {
                buttons.add(Buttons.green(
                        player.getFinsFactionCheckerPrefix() + prefix + "wavelength",
                        "Wavelength",
                        TechEmojis.GenericTF));
            }
            if (!player.hasTech("antimatter")) {
                buttons.add(Buttons.green(
                        player.getFinsFactionCheckerPrefix() + prefix + "antimatter",
                        "Antimatter",
                        TechEmojis.GenericTF));
            }
        }
        return buttons;
    }

    public static void sendSpliceDeck(Game game, String type, ButtonInteractionEvent event) {
        List<String> cards = getDeckForSplicing(game, type, 100, true);
        if (cards.isEmpty()) {
            String messageText = "There are no more cards in the " + type + " deck.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), messageText);
            return;
        }
        List<MessageEmbed> embeds = new ArrayList<>();
        if ("ability".equalsIgnoreCase(type)) {
            for (String card : cards) {
                embeds.add(Mapper.getTech(card).getRepresentationEmbed());
            }
        }
        if ("genome".equalsIgnoreCase(type) || "paradigm".equalsIgnoreCase(type)) {
            for (String card : cards) {
                embeds.add(Mapper.getLeader(card).getRepresentationEmbed(false, true, false, false, true));
            }
        }
        if ("units".equalsIgnoreCase(type)) {
            for (String card : cards) {
                embeds.add(Mapper.getUnit(card).getRepresentationEmbed());
            }
        }
        MessageHelper.sendMessageEmbedsToThread(event.getChannel(), "Remaining cards of type: " + type, embeds);
    }

    public static List<MessageEmbed> getSpliceEmbeds(Game game, String type, List<String> cards, Player player) {
        List<MessageEmbed> embeds = new ArrayList<>();
        if ("ability".equalsIgnoreCase(type)) {
            for (String card : cards) {
                embeds.add(Mapper.getTech(card).getRepresentationEmbed());
            }
        }
        if ("genome".equalsIgnoreCase(type)) {
            for (String card : cards) {
                embeds.add(Mapper.getLeader(card).getRepresentationEmbed(false, true, false, false, true));
            }
        }
        if ("units".equalsIgnoreCase(type)) {
            for (String card : cards) {
                embeds.add(Mapper.getUnit(card).getRepresentationEmbed());
            }
        }
        if (player != null && !player.hasTech("wavelength")) {
            embeds.add(Mapper.getTech("wavelength").getRepresentationEmbed());
        }
        if (player != null && !player.hasTech("antimatter")) {
            embeds.add(Mapper.getTech("antimatter").getRepresentationEmbed());
        }
        return embeds;
    }

    @ButtonHandler("radicalAdvancementStart")
    public static void startRadicalAdvancement(Game game, Player player, GenericInteractionCreateEvent event) {
        List<Button> buttons = new ArrayList<>();
        ButtonHelper.deleteMessage(event);
        for (String tech : player.getTechs()) {
            if ("antimatter".equalsIgnoreCase(tech) || "wavelength".equalsIgnoreCase(tech)) {
                continue;
            }
            buttons.add(Buttons.red(
                    "radAdvancementStep2_" + tech,
                    "Discard " + Mapper.getTech(tech).getName()));
        }
        String msg = player.getRepresentation() + ", use these buttons to discard an ability card.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("radAdvancementStep2")
    public static void radAdvancementStep2(ButtonInteractionEvent event, Game game, String buttonID, Player player) {

        String cardID = buttonID.split("_")[1];
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.getRepresentation() + " has lost the ability: "
                        + Mapper.getTech(cardID).getName(),
                Mapper.getTech(cardID).getRepresentationEmbed());
        TechnologyType type = Mapper.getTech(cardID).getFirstType();

        List<MessageEmbed> embeds = new ArrayList<>();
        List<String> allCards = Mapper.getDeck("techs_tf").getNewShuffledDeck();
        for (Player p : game.getRealPlayers()) {
            for (String tech : p.getTechs()) {
                allCards.remove(tech);
            }
        }
        List<String> someCardList = new ArrayList<>(allCards);
        for (String card : someCardList) {
            if (game.getStoredValue("purgedAbilities").contains("_" + card)) {
                allCards.remove(card);
            }
        }
        String found = "nothing applicable";
        Collections.shuffle(allCards);
        for (String card : allCards) {
            embeds.add(Mapper.getTech(card).getRepresentationEmbed());
            if (Mapper.getTech(card).getFirstType() == type) {
                player.addTech(card);
                found = Mapper.getTech(card).getRepresentation(false) + "\nIt has been automatically gained.";
                break;
            }
        }
        String msg = player.getRepresentation() + " searched through the following cards and found: " + found;
        MessageHelper.sendMessageToChannelWithEmbeds(player.getCorrectChannel(), msg, embeds);
        player.removeTech(cardID);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("discardSpliceCard")
    public static void discardSpliceCard(Game game, String buttonID, Player player) {
        String type = buttonID;
        if (buttonID.contains("_")) {
            type = buttonID.split("_")[1];
        }
        List<Button> buttons = new ArrayList<>();

        if ("ability".equalsIgnoreCase(type)) {
            for (String tech : player.getTechs()) {
                if ("antimatter".equalsIgnoreCase(tech) || "wavelength".equalsIgnoreCase(tech)) {
                    continue;
                }
                buttons.add(Buttons.red(
                        "discardSpecificSpliceCard_" + type + "_" + tech,
                        "Discard " + Mapper.getTech(tech).getName()));
            }
        }
        if ("genome".equalsIgnoreCase(type)) {
            for (String leader : player.getLeaderIDs()) {
                if (!leader.contains("agent")) {
                    continue;
                }
                buttons.add(Buttons.red(
                        "discardSpecificSpliceCard_" + type + "_" + leader,
                        "Discard " + Mapper.getLeader(leader).getTFNameIfAble()));
            }
        }
        if ("units".equalsIgnoreCase(type)) {
            for (String unit : player.getUnitsOwned()) {
                if (unit.contains("tf_")) {
                    continue;
                }
                buttons.add(Buttons.red(
                        "discardSpecificSpliceCard_" + type + "_" + unit,
                        "Discard " + Mapper.getUnit(unit).getName()));
            }
        }
        String msg = player.getRepresentation() + " use buttons to discard a card.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("discardSpecificSpliceCard")
    public static void discardSpecificSpliceCard(
            ButtonInteractionEvent event, Game game, String buttonID, Player player) {
        String type = buttonID.split("_")[1];

        String cardID = buttonID.split("_")[2];
        if ("ability".equalsIgnoreCase(type)) {
            player.removeTech(cardID);
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has lost the ability: "
                            + Mapper.getTech(cardID).getName(),
                    Mapper.getTech(cardID).getRepresentationEmbed());
        }
        if ("genome".equalsIgnoreCase(type)) {
            player.removeLeader(cardID);
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has lost the genome: "
                            + Mapper.getLeader(cardID).getTFNameIfAble(),
                    Mapper.getLeader(cardID).getRepresentationEmbed(false, true, false, false, true));
        }
        if ("units".equalsIgnoreCase(type)) {
            player.removeOwnedUnitByID(cardID);
            UnitModel u = Mapper.getUnit(cardID);
            if (u.getUnitType() != UnitType.Flagship && u.getUnitType() != UnitType.Mech) {
                String replacementUnit = u.getBaseType();
                player.addOwnedUnitByID(replacementUnit);
            }
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has lost the unit upgrade: "
                            + Mapper.getUnit(cardID).getName(),
                    Mapper.getUnit(cardID).getRepresentationEmbed());
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("searchSpliceDeck")
    public static void searchSpliceDeck(Game game, String buttonID, Player player, ButtonInteractionEvent event) {
        String type = buttonID;
        if (buttonID.contains("_")) {
            type = buttonID.split("_")[1];
        }
        List<Button> buttons = new ArrayList<>();
        List<String> cards = getDeckForSplicing(game, type, 100);
        for (String card : cards) {
            buttons.add(Buttons.green(
                    "drawSingularNewSpliceCard_" + type + "_" + card,
                    "Draw "
                            + ("ability".equalsIgnoreCase(type)
                                    ? Mapper.getTech(card).getName()
                                    : "genome".equalsIgnoreCase(type)
                                            ? Mapper.getLeader(card).getName()
                                            : Mapper.getUnit(card).getName())));
        }
        String msg = player.getRepresentation() + ", use these buttons to draw a card from the splice deck.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("keepUnit_")
    public static void keepUnit(Game game, String buttonID, Player player, GenericInteractionCreateEvent event) {
        String cardID = buttonID.split("_")[1];
        UnitModel unitModel = Mapper.getUnit(cardID);
        String asyncId = unitModel.getAsyncId();
        if (!"fs".equalsIgnoreCase(asyncId) && !"mf".equalsIgnoreCase(asyncId)) {
            List<UnitModel> unitsToRemove = player.getUnitsByAsyncID(asyncId).stream()
                    .filter(unit -> unit.getFaction().isEmpty()
                            || unit.getUpgradesFromUnitId().isEmpty())
                    .toList();
            for (UnitModel u : unitsToRemove) {
                player.removeOwnedUnitByID(u.getId());
            }
        }
        player.addOwnedUnitByID(cardID);
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.getRepresentation() + " has reacquired the unit upgrade: "
                        + Mapper.getUnit(cardID).getName(),
                Mapper.getUnit(cardID).getRepresentationEmbed());
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("drawSingularNewSpliceCard")
    public static void drawSingularNewSpliceCard(
            Game game, String buttonID, Player player, GenericInteractionCreateEvent event) {
        String type = buttonID;
        if (buttonID.contains("_")) {
            type = buttonID.split("_")[1];
        }
        List<String> cardsToDraw = getDeckForSplicing(game, type, 1);
        if (cardsToDraw.isEmpty()) {
            String messageText = "There are no more cards in the " + type + " deck.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), messageText);
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        String cardID = cardsToDraw.getFirst();
        if (buttonID.split("_").length > 2 && !buttonID.contains("pinktfmech")) {
            cardID = buttonID.split("_")[2];
            ButtonHelper.deleteMessage(event);
        }
        if (!game.isVeiledHeartMode()) {
            if ("ability".equalsIgnoreCase(type)) {
                player.addTech(cardID);
                MessageHelper.sendMessageToChannelWithEmbed(
                        player.getCorrectChannel(),
                        player.getRepresentation() + " has acquired the ability: "
                                + Mapper.getTech(cardID).getName(),
                        Mapper.getTech(cardID).getRepresentationEmbed());
            }
            if ("genome".equalsIgnoreCase(type)) {
                player.addLeader(cardID);
                MessageHelper.sendMessageToChannelWithEmbed(
                        player.getCorrectChannel(),
                        player.getRepresentation() + " has acquired the genome: "
                                + Mapper.getLeader(cardID).getName(),
                        Mapper.getLeader(cardID).getRepresentationEmbed(true));
            }
            if ("units".equalsIgnoreCase(type)) {
                UnitModel unitModel = Mapper.getUnit(cardID);
                String asyncId = unitModel.getAsyncId();
                if (!"fs".equalsIgnoreCase(asyncId) && !"mf".equalsIgnoreCase(asyncId)) {
                    List<UnitModel> unitsToRemove = player.getUnitsByAsyncID(asyncId).stream()
                            .filter(unit -> unit.getFaction().isEmpty()
                                    || unit.getUpgradesFromUnitId().isEmpty())
                            .toList();
                    for (UnitModel u : unitsToRemove) {
                        if (u.getAlias().contains("tf-")) {
                            List<Button> buttons = new ArrayList<>();
                            buttons.add(Buttons.green("keepUnit_" + u.getAlias(), "Keep " + u.getName()));
                            buttons.add(Buttons.red("deleteButtons", "Keep the New Unit"));
                            MessageHelper.sendMessageToChannel(
                                    player.getCorrectChannel(),
                                    player.getRepresentation() + " you automatically lost the "
                                            + u.getNameRepresentation()
                                            + " unit upgrade. If you would like to keep it and lose the newly acquired unit upgrade, please click the green button.",
                                    buttons);
                        }
                        player.removeOwnedUnitByID(u.getId());
                    }
                }
                player.addOwnedUnitByID(cardID);
                MessageHelper.sendMessageToChannelWithEmbed(
                        player.getCorrectChannel(),
                        player.getRepresentation() + " has acquired the unit upgrade: "
                                + Mapper.getUnit(cardID).getName(),
                        Mapper.getUnit(cardID).getRepresentationEmbed());
            }
        } else {
            game.setStoredValue(
                    "veiledCards" + player.getFaction(),
                    game.getStoredValue("veiledCards" + player.getFaction()) + cardID + "_");
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " has taken a secret card. They may put it into play with a button in their `#cards-info` thread.");
        }
        if (buttonID.contains("pinktfmech")) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", please remove a mech.",
                    ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, "mech", true));
        }
    }

    public static List<String> getDeckForSplicing(Game game, String type, int size) {
        return getDeckForSplicing(game, type, size, false);
    }

    public static List<String> getDeckForSplicing(Game game, String type, int size, boolean includeVeiledCards) {
        List<String> cards = new ArrayList<>();
        List<String> allCards = new ArrayList<>();
        if ("ability".equalsIgnoreCase(type)) {
            allCards = Mapper.getDeck("techs_tf").getNewShuffledDeck();
            for (Player p : game.getRealPlayers()) {
                for (String tech : p.getTechs()) {
                    allCards.remove(tech);
                }
                for (String tech : p.getPurgedTechs()) {
                    allCards.remove(tech);
                }
            }
            List<String> someCardList = new ArrayList<>(allCards);
            for (String card : someCardList) {
                if (game.getStoredValue("purgedAbilities").contains("_" + card)) {
                    allCards.remove(card);
                }
            }
        }
        if ("genome".equalsIgnoreCase(type)) {
            allCards = Mapper.getDeck("tf_genome").getNewShuffledDeck();
            for (Player p : game.getRealPlayers()) {
                for (String tech : p.getLeaderIDs()) {
                    allCards.remove(tech);
                }
            }
        }
        if ("units".equalsIgnoreCase(type)) {
            Map<String, UnitModel> allUnits = Mapper.getUnits();
            for (Map.Entry<String, UnitModel> entry : allUnits.entrySet()) {
                UnitModel mod = entry.getValue();
                if (mod.getFaction().isPresent() && mod.getSource() == ComponentSource.twilights_fall) {
                    FactionModel faction = Mapper.getFaction(mod.getFaction().get());
                    if (faction != null && faction.getSource() != ComponentSource.twilights_fall) {
                        allCards.add(entry.getKey());
                    }
                }
            }
            for (Player p : game.getRealPlayers()) {
                for (String unit : p.getUnitsOwned()) {
                    allCards.remove(unit);
                }
            }
            Collections.shuffle(allCards);
        }
        if ("paradigm".equalsIgnoreCase(type)) {
            allCards = Mapper.getDeck("tf_paradigm").getNewShuffledDeck();
            List<String> alreadyDrawn =
                    List.of(game.getStoredValue("savedParadigms").split("_"));
            for (String card : alreadyDrawn) {
                // savedParadigms includes veiled paradigms, which should only be removed if includeVeiledCards is false
                boolean shouldRemove = true;
                if (game.isVeiledHeartMode() & includeVeiledCards) {
                    for (Player p2 : game.getRealPlayers()) {
                        if (game.getStoredValue("veiledCards" + p2.getFaction()).contains(card)) {
                            shouldRemove = false;
                            break;
                        }
                    }
                }
                if (shouldRemove) {
                    allCards.remove(card);
                }
            }
        } else if (game.isVeiledHeartMode() && !includeVeiledCards) {
            List<String> someCardList = new ArrayList<>(allCards);
            for (String card : someCardList) {
                for (Player p2 : game.getRealPlayers()) {
                    if (game.getStoredValue("veiledCards" + p2.getFaction()).contains(card)) {
                        allCards.remove(card);
                    }
                }
            }
        }
        for (int i = 0; i < size && !allCards.isEmpty(); i++) {
            cards.add(allCards.removeFirst());
        }

        return cards;
    }

    public static void setNewSpliceCards(Game game, String type, int size) {
        List<String> cards = getDeckForSplicing(game, type, size);
        game.removeStoredValue("savedSpliceCards");
        for (String card : cards) {
            if (game.getStoredValue("savedSpliceCards").isEmpty()) {
                game.setStoredValue("savedSpliceCards", card);
            } else {
                game.setStoredValue("savedSpliceCards", game.getStoredValue("savedSpliceCards") + "_" + card);
            }
        }
    }

    public static void startInauguralSplice(Game game) {
        // The inaugural splice uses the seating order, so it's set here already
        Helper.setOrder(game);
        game.setBagDraft(new InauguralSpliceFrankenDraft(game));
        FrankenDraftBagService.startDraft(game);
    }
}
