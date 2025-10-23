package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.draft.DraftBag;
import ti4.draft.DraftItem;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import ti4.model.StrategyCardModel;
import ti4.model.TileModel;
import ti4.model.UnitModel;
import ti4.service.button.ReactionService;
import ti4.service.draft.DraftTileManager;
import ti4.service.franken.FrankenDraftBagService;
import ti4.service.milty.MiltyDraftHelper;
import ti4.service.milty.MiltyDraftManager;
import ti4.service.milty.MiltyDraftManager.PlayerDraft;
import ti4.service.milty.MiltyDraftSlice;
import ti4.service.milty.MiltyDraftTile;
import ti4.service.unit.AddUnitService;

public class ButtonHelperTwilightsFall {

    public static void startSliceBuild(Game game, GenericInteractionCreateEvent event) {
        try {
            MiltyDraftManager manager = game.getMiltyDraftManager();
            List<String> playerIDs = new ArrayList<>();
            for (Player p : game.getRealPlayers()) {
                playerIDs.add(p.getUserID());
            }
            manager.setPlayers(playerIDs);
            for (Player p : game.getPlayers().values()) {
                DraftBag bag = p.getDraftHand();
                PlayerDraft draft = manager.getPlayerDraft(p);
                List<MiltyDraftTile> slice = new ArrayList<>();
                for (DraftItem.Category category : FrankenDraftBagService.TFcomponentCategories) {
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
                    if (category == DraftItem.Category.BLUETILE || category == DraftItem.Category.REDTILE) {
                        for (DraftItem item : items) {
                            TileModel tile = TileHelper.getTileById(item.ItemId);
                            slice.add(DraftTileManager.getDraftTileFromModel(tile));
                        }
                    }
                }
                Collections.shuffle(slice);
                if (slice.get(1).getTile().getPlanetUnitHolders().isEmpty()) {
                    Collections.rotate(slice, 1);
                    if (slice.get(1).getTile().getPlanetUnitHolders().isEmpty()) {
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
            Map<UnitType, Integer> unitCounts =
                    Helper.getUnitList(Mapper.getFaction(factionFleet).getStartingFleet());
            for (UnitType unit : unitCounts.keySet()) {
                int amount = unitCounts.get(unit);
                UnitModel mod =
                        player.getUnitsByAsyncID(unit.getValue().toLowerCase()).getFirst();
                String bestResPlanet = null;
                int best = 0;
                for (Planet plan : tile.getPlanetUnitHolders()) {
                    if (plan.getResources() > best) {
                        best = plan.getResources();
                        bestResPlanet = plan.getName();
                    }
                }
                if (mod != null) {
                    if (bestResPlanet == null
                            || mod.getIsShip()
                            || (UnitType.Spacedock == unit
                                    && (player.hasUnit("saar_spacedock") || player.hasUnit("tf-floatingfactory")))) {
                        AddUnitService.addUnits(event, tile, game, player.getColor(), amount + " " + mod.getAsyncId());
                    } else {
                        if (UnitType.Spacedock == unit) {
                            AddUnitService.addUnits(
                                    event,
                                    tile,
                                    game,
                                    player.getColor(),
                                    amount + " " + mod.getAsyncId() + " " + bestResPlanet);
                        } else {
                            while (amount > 0) {
                                for (Planet plan : tile.getPlanetUnitHolders()) {
                                    if (amount > 0) {
                                        AddUnitService.addUnits(
                                                event,
                                                tile,
                                                game,
                                                player.getColor(),
                                                "1 " + mod.getAsyncId() + " " + plan.getName());
                                        amount--;
                                    }
                                    player.refreshPlanet(plan.getName());
                                }
                            }
                        }
                    }
                }
            }

            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), player.getRepresentation() + " set starting fleet successfully.");
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), player.getRepresentation() + " couldnt figure out that fleet, sorry.");
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

        String tileID = Mapper.getFaction(factionHS).getHomeSystem();
        tileID = AliasHandler.resolveTile(tileID);

        if (!pos.isEmpty() && tileID != null) {

            Tile toAdd = new Tile(tileID, pos);
            game.setTile(toAdd);
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), player.getRepresentation() + " set home system successfully.");
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), player.getRepresentation() + " couldnt figure out that HS, sorry.");
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
        if (!game.getStoredValue("engineerACSplice").isEmpty()) {
            participants.add(0, startPlayer);
            participants.add(0, startPlayer);
            game.removeStoredValue("engineerACSplice");
        }
        int size = 1 + participants.size();
        if (!game.getStoredValue("researchagentSplice").isEmpty()) {
            size += 3;
            game.removeStoredValue("researchagentSplice");
        }
        game.removeStoredValue("savedParticipants");
        game.removeStoredValue("lastSplicer");
        setNewSpliceCards(game, spliceType, size);

        sendPlayerSpliceOptions(game, startPlayer);
        for (Player p : participants) {
            if (game.getStoredValue("savedParticipants").isEmpty()) {
                game.setStoredValue("savedParticipants", p.getFaction());
            } else {
                game.setStoredValue(
                        "savedParticipants", game.getStoredValue("savedParticipants") + "_" + p.getFaction());
            }
        }
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), "A splice has started.");
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
        List<MessageEmbed> embeds = getSpliceEmbeds(game, type, cards, player);
        String msg = player.getRepresentation() + " Select a card to splice into your faction:";
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(player.getCorrectChannel(), msg, embeds, buttons);
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
                    player.getCorrectChannel(), player.getRepresentation() + " Use Buttons to Pay 3i/3r", buttons);
        }
        if (splice == 6) {
            List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
            Button DoneExhausting = Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets");
            buttons.add(DoneExhausting);
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(), player.getRepresentation() + " Use Buttons to Pay 4r", buttons);
        }

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
            for (Player p : fullOrder) {
                participants.add(p);
            }
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
        if (splice == 7 && !game.getStoredValue("paid6ForSplice").isEmpty()) {
            participants.add(player);
            game.removeStoredValue("paid6ForSplice");
        }
        initiateASplice(game, player, spliceType, participants);
    }

    public static void triggerYellowUnits(Game game, Player player) {
        if (player.hasUnit("yellowtf_flagship")
                && !ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Mech, UnitType.Flagship)
                        .isEmpty()) {
            String message = player.getRepresentationUnfogged()
                    + ", please resolve your mech and flagship abilities using these buttons. Each mech triggers once, and the flagship has to do convert 2 comms or gain 2 comms.";
            List<Button> buttons = ButtonHelperFactionSpecific.gainOrConvertCommButtons(player, false);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        }
        if (player.hasUnit("blacktf_mech")) {
            int numMechs = ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", false);
            AddUnitService.addUnits(null, player.getNomboxTile(), game, player.getColor(), numMechs + " infantry");
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " captured " + numMechs + " infantry with their mechs.");
        }
    }

    @ButtonHandler("discardTech_")
    public static void discardTech(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String cardID = buttonID.split("_")[1];
        player.removeTech(cardID);
        MessageHelper.sendMessageToChannelWithEmbed(
                game.getActionsChannel(),
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
                game.getActionsChannel(),
                player.getRepresentation() + " has discarded the genome: "
                        + Mapper.getLeader(cardID).getName(),
                Mapper.getLeader(cardID).getRepresentationEmbed());
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("selectASpliceCard_")
    public static void selectASpliceCard(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String cardID = buttonID.split("_")[1];
        String lastSplicer = game.getStoredValue("lastSplicer");
        String type = game.getStoredValue("spliceType");
        if (cardID.equalsIgnoreCase("antimatter") || cardID.equalsIgnoreCase("wavelength")) {
            player.addTech(cardID);

            MessageHelper.sendMessageToChannelWithEmbed(
                    game.getActionsChannel(),
                    player.getRepresentation()
                            + " has chosen to get a commonly available tech instead of splicing: the tech is "
                            + Mapper.getTech(cardID).getName(),
                    Mapper.getTech(cardID).getRepresentationEmbed());
            triggerYellowUnits(game, player);
        } else {
            game.setStoredValue(
                    "savedSpliceCards",
                    game.getStoredValue("savedSpliceCards")
                            .replace(cardID + "_", "")
                            .replace(cardID, ""));
            if (lastSplicer.equalsIgnoreCase(player.getFaction())) {
                MessageHelper.sendMessageToChannel(
                        game.getActionsChannel(),
                        player.getRepresentation() + " has removed a spliced card from the draft");
            } else {
                if (type.equalsIgnoreCase("ability")) {
                    player.addTech(cardID);
                    MessageHelper.sendMessageToChannelWithEmbed(
                            game.getActionsChannel(),
                            player.getRepresentation() + " has spliced in the ability: "
                                    + Mapper.getTech(cardID).getName(),
                            Mapper.getTech(cardID).getRepresentationEmbed());
                }
                if (type.equalsIgnoreCase("genome")) {
                    player.addLeader(cardID);
                    MessageHelper.sendMessageToChannelWithEmbed(
                            game.getActionsChannel(),
                            player.getRepresentation() + " has spliced in the genome: "
                                    + Mapper.getLeader(cardID).getName(),
                            Mapper.getLeader(cardID).getRepresentationEmbed());
                }
                if (type.equalsIgnoreCase("units")) {
                    player.addOwnedUnitByID(cardID);
                    MessageHelper.sendMessageToChannelWithEmbed(
                            game.getActionsChannel(),
                            player.getRepresentation() + " has spliced in the unit: "
                                    + Mapper.getUnit(cardID).getName(),
                            Mapper.getUnit(cardID).getRepresentationEmbed());
                }
                triggerYellowUnits(game, player);
            }
        }
        List<Player> participants = getParticipantsList(game);
        participants.remove(player);
        game.removeStoredValue("savedParticipants");
        if (participants.size() > 0) {
            game.setStoredValue("lastSplicer", player.getFaction());
            sendPlayerSpliceOptions(game, participants.get(0));
            participants.remove(0);
            for (Player p : participants) {
                if (game.getStoredValue("savedParticipants").isEmpty()) {
                    game.setStoredValue("savedParticipants", p.getFaction());
                } else {
                    game.setStoredValue(
                            "savedParticipants", game.getStoredValue("savedParticipants") + "_" + p.getFaction());
                }
            }
        } else {
            game.removeStoredValue("lastSplicer");
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), game.getPing() + " The splice is complete.");
            game.removeStoredValue("willParticipateInSplice");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("drawParadigm")
    public static void drawParadigm(Game game, Player player, ButtonInteractionEvent event) {
        drawParadigm(game, player, event, true);
    }

    @ButtonHandler("addMagusSpliceCard")
    public static void addMagusSpliceCard(Game game, Player player, ButtonInteractionEvent event) {
        game.setStoredValue("paid6ForSplice", "yes");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), "Magus Holder chose to pay the 3i/3r for an extra draw.");
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "both");
        Button DoneExhausting = Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets");
        buttons.add(DoneExhausting);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "Use Buttons to Pay 3i/3r", buttons);
    }

    public static void drawParadigm(Game game, Player player, ButtonInteractionEvent event, boolean scPara) {

        String messageID = event.getMessageId();

        if (scPara) {
            boolean used = ButtonHelperSCs.addUsedSCPlayer(messageID, game, player);
            StrategyCardModel scModel = game.getStrategyCardModelByInitiative(8).get();
            if (scModel != null && !player.getFollowedSCs().contains(scModel.getInitiative())) {
                ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scModel.getInitiative(), game, event);
            }

            if (scModel != null
                    && !used
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
            allCards.remove(card);
        }
        String leader = allCards.remove(0);
        if (game.getStoredValue("savedParadigms").isEmpty()) {
            game.setStoredValue("savedParadigms", leader);
        } else {
            game.setStoredValue("savedParadigms", game.getStoredValue("savedParadigms") + "_" + leader);
        }
        if (!scPara && game.getPhaseOfGame().equalsIgnoreCase("agenda")) {
            if (game.getStoredValue("artificeParadigms").isEmpty()) {
                game.setStoredValue("artificeParadigms", leader);
            } else {
                game.setStoredValue("artificeParadigms", game.getStoredValue("artificeParadigms") + "_" + leader);
            }
        }
        MessageHelper.sendMessageToChannelWithEmbed(
                game.getActionsChannel(),
                player.getRepresentation() + " has drawn a new paradigm: "
                        + Mapper.getLeader(leader).getName(),
                Mapper.getLeader(leader).getRepresentationEmbed());
        player.addLeader(leader);
        player.getLeaderByID(leader).get().setLocked(false);
    }

    public static List<Button> getSpliceButtons(Game game, String type, List<String> cards, Player player) {
        List<Button> buttons = new ArrayList<>();
        if (type.equalsIgnoreCase("ability")) {
            for (String card : cards) {
                String name = Mapper.getTech(card).getName();
                buttons.add(Buttons.green(
                        "selectASpliceCard_" + card,
                        "Select " + name,
                        Mapper.getTech(card).getSingleTechEmoji()));
            }
        }
        if (type.equalsIgnoreCase("genome")) {
            for (String card : cards) {
                String name = Mapper.getLeader(card).getName();
                String faction = Mapper.getLeader(card).getFaction();
                FactionModel factionModel = Mapper.getFaction(faction);
                buttons.add(
                        Buttons.green("selectASpliceCard_" + card, "Select " + name, factionModel.getFactionEmoji()));
            }
        }
        if (type.equalsIgnoreCase("units")) {
            for (String card : cards) {
                String name = Mapper.getUnit(card).getName();
                buttons.add(Buttons.green(
                        "selectASpliceCard_" + card,
                        "Select " + name,
                        Mapper.getUnit(card).getUnitEmoji()));
            }
        }
        if (!player.hasTech("wavelength")) {
            buttons.add(Buttons.green("selectASpliceCard_wavelength", "Select Wavelength"));
        }
        if (!player.hasTech("antimatter")) {
            buttons.add(Buttons.green("selectASpliceCard_antimatter", "Select Antimatter"));
        }
        return buttons;
    }

    public static List<MessageEmbed> getSpliceEmbeds(Game game, String type, List<String> cards, Player player) {
        List<MessageEmbed> embeds = new ArrayList<>();
        if (type.equalsIgnoreCase("ability")) {
            for (String card : cards) {
                embeds.add(Mapper.getTech(card).getRepresentationEmbed());
            }
        }
        if (type.equalsIgnoreCase("genome")) {
            for (String card : cards) {
                embeds.add(Mapper.getLeader(card).getRepresentationEmbed());
            }
        }
        if (type.equalsIgnoreCase("units")) {
            for (String card : cards) {
                embeds.add(Mapper.getUnit(card).getRepresentationEmbed());
            }
        }
        if (!player.hasTech("wavelength")) {
            embeds.add(Mapper.getTech("wavelength").getRepresentationEmbed());
        }
        if (!player.hasTech("antimatter")) {
            embeds.add(Mapper.getTech("antimatter").getRepresentationEmbed());
        }
        return embeds;
    }

    @ButtonHandler("discardSpliceCard")
    public static void discardSpliceCard(Game game, String buttonID, Player player) {
        String type = buttonID;
        if (buttonID.contains("_")) {
            type = buttonID.split("_")[1];
        }
        List<Button> buttons = new ArrayList<>();

        if (type.equalsIgnoreCase("ability")) {
            for (String tech : player.getTechs()) {
                if (tech.equalsIgnoreCase("antimatter") || tech.equalsIgnoreCase("wavelength")) {
                    continue;
                }
                buttons.add(Buttons.red(
                        "discardSpecificSpliceCard_" + type + "_" + tech,
                        "Discard " + Mapper.getTech(tech).getName()));
            }
        }
        if (type.equalsIgnoreCase("genome")) {
            for (String leader : player.getLeaderIDs()) {
                if (!leader.contains("agent")) {
                    continue;
                }
                buttons.add(Buttons.red(
                        "discardSpecificSpliceCard_" + type + "_" + leader,
                        "Discard " + Mapper.getLeader(leader).getName()));
            }
        }
        if (type.equalsIgnoreCase("units")) {
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
        if (type.equalsIgnoreCase("ability")) {
            player.removeTech(cardID);
            MessageHelper.sendMessageToChannelWithEmbed(
                    game.getActionsChannel(),
                    player.getRepresentation() + " has lost the ability: "
                            + Mapper.getTech(cardID).getName(),
                    Mapper.getTech(cardID).getRepresentationEmbed());
        }
        if (type.equalsIgnoreCase("genome")) {
            player.removeLeader(cardID);
            MessageHelper.sendMessageToChannelWithEmbed(
                    game.getActionsChannel(),
                    player.getRepresentation() + " has lost the genome: "
                            + Mapper.getLeader(cardID).getName(),
                    Mapper.getLeader(cardID).getRepresentationEmbed());
        }
        if (type.equalsIgnoreCase("units")) {
            player.removeOwnedUnitByID(cardID);
            MessageHelper.sendMessageToChannelWithEmbed(
                    game.getActionsChannel(),
                    player.getRepresentation() + " has lost the unit: "
                            + Mapper.getUnit(cardID).getName(),
                    Mapper.getUnit(cardID).getRepresentationEmbed());
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("drawSingularNewSpliceCard")
    public static void drawSingularNewSpliceCard(Game game, String buttonID, Player player) {
        String type = buttonID;
        if (buttonID.contains("_")) {
            type = buttonID.split("_")[1];
        }
        String cardID = getDeckForSplicing(game, type, 1).get(0);
        if (type.equalsIgnoreCase("ability")) {
            player.addTech(cardID);
            MessageHelper.sendMessageToChannelWithEmbed(
                    game.getActionsChannel(),
                    player.getRepresentation() + " has acquired the ability: "
                            + Mapper.getTech(cardID).getName(),
                    Mapper.getTech(cardID).getRepresentationEmbed());
        }
        if (type.equalsIgnoreCase("genome")) {
            player.addLeader(cardID);
            MessageHelper.sendMessageToChannelWithEmbed(
                    game.getActionsChannel(),
                    player.getRepresentation() + " has acquired the genome: "
                            + Mapper.getLeader(cardID).getName(),
                    Mapper.getLeader(cardID).getRepresentationEmbed());
        }
        if (type.equalsIgnoreCase("units")) {
            player.addOwnedUnitByID(cardID);
            MessageHelper.sendMessageToChannelWithEmbed(
                    game.getActionsChannel(),
                    player.getRepresentation() + " has acquired the unit: "
                            + Mapper.getUnit(cardID).getName(),
                    Mapper.getUnit(cardID).getRepresentationEmbed());
        }
    }

    public static List<String> getDeckForSplicing(Game game, String type, int size) {
        List<String> cards = new ArrayList<>();
        if (type.equalsIgnoreCase("ability")) {
            List<String> allCards = Mapper.getDeck("techs_tf").getNewShuffledDeck();
            for (Player p : game.getRealPlayers()) {
                for (String tech : p.getTechs()) {
                    allCards.remove(tech);
                }
            }
            for (int i = 0; i < size && allCards.size() > 0; i++) {
                cards.add(allCards.remove(0));
            }
        }
        if (type.equalsIgnoreCase("genome")) {
            List<String> allCards = Mapper.getDeck("tf_genome").getNewShuffledDeck();
            for (Player p : game.getRealPlayers()) {
                for (String tech : p.getLeaderIDs()) {
                    allCards.remove(tech);
                }
            }
            for (int i = 0; i < size && allCards.size() > 0; i++) {
                cards.add(allCards.remove(0));
            }
        }
        if (type.equalsIgnoreCase("units")) {
            List<String> allCards = new ArrayList<>();
            Map<String, UnitModel> allUnits = Mapper.getUnits();
            for (String unitID : allUnits.keySet()) {
                UnitModel mod = allUnits.get(unitID);
                if (mod.getFaction().isPresent() && mod.getSource() == ComponentSource.twilights_fall) {
                    FactionModel faction = Mapper.getFaction(mod.getFaction().get());
                    if (faction != null && faction.getSource() != ComponentSource.twilights_fall) {
                        allCards.add(unitID);
                    }
                }
            }
            for (Player p : game.getRealPlayers()) {
                for (String unit : p.getUnitsOwned()) {
                    allCards.remove(unit);
                }
            }
            Collections.shuffle(allCards);
            for (int i = 0; i < size && allCards.size() > 0; i++) {
                cards.add(allCards.remove(0));
            }
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
}
