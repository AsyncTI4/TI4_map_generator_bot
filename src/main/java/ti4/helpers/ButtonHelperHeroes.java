package ti4.helpers;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

import java.util.*;

import ti4.commands.cardspn.PNInfo;
import ti4.commands.custom.PeakAtStage1;
import ti4.commands.custom.PeakAtStage2;
import ti4.commands.player.SCPlay;
import ti4.commands.units.AddUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.GenerateTile;
import ti4.generator.Mapper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.model.PublicObjectiveModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;

public class ButtonHelperHeroes {

    public static List<Button> getArboHeroButtons(Game activeGame, Player player, GenericInteractionCreateEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getAllTilesWithProduction(activeGame, player, event)) {
            buttons.add(Button.success("arboHeroBuild_" + tile.getPosition(), tile.getRepresentationForButtons(activeGame, player)));
        }
        buttons.add(Button.danger("deleteButtons", "Done"));
        return buttons;
    }

    public static List<Button> getSaarHeroButtons(Game activeGame, Player player, GenericInteractionCreateEvent event) {
        List<Button> buttons = new ArrayList<>();
        List<Tile> tilesUsed = new ArrayList<>();
        for (Tile tile1 : ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, player, UnitType.Spacedock)) {
            for (String tile2Pos : FoWHelper.getAdjacentTilesAndNotThisTile(activeGame, tile1.getPosition(), player, false)) {
                Tile tile2 = activeGame.getTileByPosition(tile2Pos);
                if (!tilesUsed.contains(tile2)) {
                    tilesUsed.add(tile2);
                    buttons.add(Button.success("saarHeroResolution_" + tile2.getPosition(), tile2.getRepresentationForButtons(activeGame, player)));
                }
            }

        }
        buttons.add(Button.danger("deleteButtons", "Done"));
        return buttons;
    }

    public static void resolveSaarHero(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = activeGame.getTileByPosition(pos);
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            for (Player p2 : activeGame.getRealPlayers()) {
                if (p2 == player) {
                    continue;
                }
                String name = unitHolder.getName().replace("space", "");
                if(tile.containsPlayersUnits(p2)){
                    new RemoveUnits().unitParsing(event, p2.getColor(), tile, "200 ff, 200 inf " + name, activeGame);
                    MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(), ButtonHelper.getCorrectChannel(p2, activeGame) + " heads up, a tile with your units in it got hit with a saar hero, removing all fighters and infantry.");
                }
                
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            ButtonHelper.getIdent(player) + " removed all opposing infantry and fighters in " + tile.getRepresentationForButtons(activeGame, player) + " using Saar hero");
        event.getMessage().delete().queue();
    }

    public static void resolveArboHeroBuild(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        List<Button> buttons;
        buttons = Helper.getPlaceUnitButtons(event, player, activeGame, activeGame.getTileByPosition(pos), "arboHeroBuild", "place");
        String message = player.getRepresentation() + " Use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static List<Button> getNekroHeroButtons(Player player, Game activeGame) {
        List<Button> techPlanets = new ArrayList<>();
        for (Tile tile : activeGame.getTileMap().values()) {
            if (tile.containsPlayersUnits(player)) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    if (unitHolder instanceof Planet planetHolder) {
                        String planet = planetHolder.getName();
                        if ((Mapper.getPlanet(planet).getTechSpecialties() != null && Mapper.getPlanet(planet).getTechSpecialties().size() > 0)
                            || ButtonHelper.checkForTechSkipAttachments(activeGame, planet)) {
                            techPlanets.add(Button.secondary("nekroHeroStep2_" + planet, Mapper.getPlanet(planet).getName()));
                        }
                    }
                }
            }
        }
        return techPlanets;
    }

    public static void resolveNekroHeroStep2(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
        String techType = "none";
        if (Mapper.getPlanet(planet).getTechSpecialties() != null && Mapper.getPlanet(planet).getTechSpecialties().size() > 0) {
            techType = Mapper.getPlanet(planet).getTechSpecialties().get(0).toString().toLowerCase();
        } else {
            techType = ButtonHelper.getTechSkipAttachments(activeGame, planet);
        }
        if (techType.equalsIgnoreCase("none")) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "No tech skips found");
            return;
        }
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            String color = p2.getColor();
            unitHolder.removeAllUnits(color);
            unitHolder.removeAllUnitDamage(color);
        }
        Planet planetHolder = (Planet) unitHolder;
        int oldTg = player.getTg();
        int count = planetHolder.getResources() + planetHolder.getInfluence();
        player.setTg(oldTg + count);
        MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdent(player) + " gained " + count + " tgs (" + oldTg + "->" + player.getTg() + ")");
        ButtonHelperAbilities.pillageCheck(player, activeGame);
        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, count);

        List<TechnologyModel> techs = Helper.getAllTechOfAType(activeGame, techType, player.getFaction(), player);
        List<Button> buttons = Helper.getTechButtons(techs, techType, player, "nekro");
        String message = player.getRepresentation() + " Use the buttons to get the tech you want";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getCabalHeroButtons(Player player, Game activeGame) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> empties = new ArrayList<>();

        List<Tile> tiles = new ArrayList<>();
        activeGame.getRealPlayers().stream()
            .filter(p -> p.hasTech("dt2") || player.getUnitsOwned().contains("cabal_spacedock") || player.getUnitsOwned().contains("cabal_spacedock2"))
            .forEach(p -> tiles.addAll(ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, p, UnitType.CabalSpacedock, UnitType.Spacedock)));

        List<Tile> adjTiles = new ArrayList<>();
        for (Tile tile : tiles) {
            for (String pos : FoWHelper.getAdjacentTiles(activeGame, tile.getPosition(), player, false)) {
                Tile tileToAdd = activeGame.getTileByPosition(pos);
                if (!adjTiles.contains(tileToAdd) && !tile.getPosition().equalsIgnoreCase(pos)) {
                    adjTiles.add(tileToAdd);
                }
            }
        }

        for (Tile tile : adjTiles) {
            empties.add(Button.primary(finChecker + "cabalHeroTile_" + tile.getPosition(), "Roll for units in " + tile.getRepresentationForButtons(activeGame, player)));
        }
        return empties;
    }

    public static void executeCabalHero(String buttonID, Player player, Game activeGame, ButtonInteractionEvent event) {
        String pos = buttonID.replace("cabalHeroTile_", "");
        Tile tile = activeGame.getTileByPosition(pos);
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (FoWHelper.playerHasShipsInSystem(p2, tile) && !ButtonHelperFactionSpecific.isCabalBlockadedByPlayer(p2, activeGame, player)) {
                ButtonHelper.riftAllUnitsInASystem(pos, event, activeGame, p2, p2.getFactionEmoji(), player);
            }
            if (FoWHelper.playerHasShipsInSystem(p2, tile) && ButtonHelperFactionSpecific.isCabalBlockadedByPlayer(p2, activeGame, player)) {
                String msg = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " has failed to eat units owned by " + player.getRepresentation()
                    + " because they were blockaded. Wah-wah.";
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
            }
        }
    }

    public static List<Button> getEmpyHeroButtons(Player player, Game activeGame) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> empties = new ArrayList<>();
        for (Tile tile : activeGame.getTileMap().values()) {
            if (tile.getUnitHolders().values().size() > 1 || !FoWHelper.playerHasShipsInSystem(player, tile)) {
                continue;
            }
            empties.add(Button.primary(finChecker + "exploreFront_" + tile.getPosition(), "Explore " + tile.getRepresentationForButtons(activeGame, player)));
        }
        return empties;
    }

    public static void resolveNaaluHeroSend(Player p1, Game activeGame, String buttonID, ButtonInteractionEvent event) {
        buttonID = buttonID.replace("naaluHeroSend_", "");
        String factionToTrans = buttonID.substring(0, buttonID.indexOf("_"));
        String amountToTrans = buttonID.substring(buttonID.indexOf("_") + 1);
        Player p2 = activeGame.getPlayerFromColorOrFaction(factionToTrans);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p1, activeGame), "Could not resolve second player, please resolve manually.");
            return;
        }
        String message2 = "";
        // String ident = Helper.getPlayerRepresentation(p1, activeGame, activeGame.getGuild(), false);
        String ident2 = Helper.getPlayerRepresentation(p2, activeGame, activeGame.getGuild(), false);
        String id = null;
        int pnIndex;
        pnIndex = Integer.parseInt(amountToTrans);
        for (Map.Entry<String, Integer> pn : p1.getPromissoryNotes().entrySet()) {
            if (pn.getValue().equals(pnIndex)) {
                id = pn.getKey();
            }
        }
        if (id == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p1, activeGame), "Could not resolve PN, PN not sent.");
            return;
        }
        p1.removePromissoryNote(id);
        p2.setPromissoryNote(id);
        boolean sendSftT = false;
        boolean sendAlliance = false;
        String promissoryNoteOwner = Mapper.getPromissoryNoteOwner(id);
        if ((id.endsWith("_sftt") || id.endsWith("_an")) && !promissoryNoteOwner.equals(p2.getFaction())
            && !promissoryNoteOwner.equals(p2.getColor()) && !p2.isPlayerMemberOfAlliance(activeGame.getPlayerFromColorOrFaction(promissoryNoteOwner))) {
            p2.setPromissoryNotesInPlayArea(id);
            if (id.endsWith("_sftt")) {
                sendSftT = true;
            } else {
                sendAlliance = true;
            }
        }
        PNInfo.sendPromissoryNoteInfo(activeGame, p1, false);
        PNInfo.sendPromissoryNoteInfo(activeGame, p2, false);
        String text = sendSftT ? "**Support for the Throne** " : (sendAlliance ? "**Alliance** " : "");
        message2 = p1.getRepresentation() + " sent " + Emojis.PN + text + "PN to " + ident2;
        Helper.checkEndGame(activeGame, p2);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), message2);
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p1, activeGame), message2);
        }
        event.getMessage().delete().queue();

    }

    public static void resolveNivynHeroSustainEverything(Game activeGame, Player nivyn) {
        for (Tile tile : activeGame.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                HashMap<UnitKey, Integer> units = unitHolder.getUnits();
                for (Player player : activeGame.getRealPlayers()) {
                    for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                        if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                        UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                        if (unitModel == null) continue;
                        UnitKey unitKey = unitEntry.getKey();
                        int damagedUnits = 0;
                        if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                            damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                        }
                        int totalUnits = unitEntry.getValue() - damagedUnits;
                        if (totalUnits > 0 && unitModel.getSustainDamage() && (player != nivyn || !unitModel.getBaseType().equalsIgnoreCase("mech"))) {
                            tile.addUnitDamage(unitHolder.getName(), unitKey, totalUnits);
                        }
                    }
                }
            }
        }
    }

    public static void augersHeroSwap(Player player, Game activeGame, String buttonID, ButtonInteractionEvent event) {
        int num = Integer.parseInt(buttonID.split("_")[2]);
        if (buttonID.split("_")[1].equalsIgnoreCase("1")) {
            activeGame.swapStage1(1, num);
        } else {
            activeGame.swapStage2(1, num);
        }
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
            ButtonHelper.getTrueIdentity(player, activeGame) + " put the objective at location " + num + " as next up. Feel free to peek at it to confirm it worked");
        // GameSaveLoadManager.saveMap(activeGame, event);
        event.getMessage().delete().queue();
    }

    public static void augersHeroResolution(Player player, Game activeGame, String buttonID, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<Button>();
        if (buttonID.split("_")[1].equalsIgnoreCase("1")) {
            int size = activeGame.getPublicObjectives1Peakable().size() - 2;
            for (int x = size; x < size + 3; x++) {
                new PeakAtStage1().secondHalfOfPeak(event, activeGame, player, x);
                String obj = activeGame.peakAtStage1(x);
                PublicObjectiveModel po = Mapper.getPublicObjective(obj);
                buttons.add(Button.success("augerHeroSwap_1_" + x, "Put " + po.getName() + " As The Next Objective"));
            }
        } else {
            int size = activeGame.getPublicObjectives2Peakable().size() - 2;
            for (int x = size; x < size + 3; x++) {
                new PeakAtStage2().secondHalfOfPeak(event, activeGame, player, x);
                String obj = activeGame.peakAtStage2(x);
                PublicObjectiveModel po = Mapper.getPublicObjective(obj);
                buttons.add(Button.success("augerHeroSwap_2_" + x, "Put " + po.getName() + " As The Next Objective"));
            }
        }
        buttons.add(Button.danger("deleteButtons", "Decline to change the next objective"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), ButtonHelper.getTrueIdentity(player, activeGame) + " use buttons to resolve", buttons);
    }

    public static void resolveNaaluHeroInitiation(Player player, Game activeGame, ButtonInteractionEvent event) {
        Leader playerLeader = player.unsafeGetLeader("naaluhero");
        StringBuilder message2 = new StringBuilder(player.getRepresentation()).append(" played ").append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                message2 + " - Leader " + "naaluhero" + " has been purged. \n\n Sent buttons to resolve to everyone's channels");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Leader was not purged - something went wrong");
        }
        for (Player p1 : activeGame.getRealPlayers()) {
            if (p1 == player) {
                continue;
            }
            List<Button> stuffToTransButtons = new ArrayList<Button>();
            String message = Helper.getPlayerRepresentation(p1, activeGame, activeGame.getGuild(), true)
                + " The Naalu Hero has been played and you must send a PN. Please select the PN you would like to send";
            for (String pnShortHand : p1.getPromissoryNotes().keySet()) {
                if (p1.getPromissoryNotesInPlayArea().contains(pnShortHand)) {
                    continue;
                }
                PromissoryNoteModel promissoryNote = Mapper.getPromissoryNoteByID(pnShortHand);
                Player owner = activeGame.getPNOwner(pnShortHand);
                Button transact;
                if (activeGame.isFoWMode()) {
                    transact = Button.success("naaluHeroSend_" + player.getFaction() + "_" + p1.getPromissoryNotes().get(pnShortHand), owner.getColor() + " " + promissoryNote.getName());
                } else {
                    transact = Button.success("naaluHeroSend_" + player.getFaction() + "_" + p1.getPromissoryNotes().get(pnShortHand), promissoryNote.getName())
                        .withEmoji(Emoji.fromFormatted(owner.getFactionEmoji()));
                }
                stuffToTransButtons.add(transact);
            }
            MessageHelper.sendMessageToChannelWithButtons(p1.getCardsInfoThread(), message, stuffToTransButtons);
        }
        event.getMessage().delete().queue();
    }

    public static void lastStepOfYinHero(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String trueIdentity) {
        String planetNInf = buttonID.replace("yinHeroInfantry_", "");
        String planet = planetNInf.split("_")[0];
        String amount = planetNInf.split("_")[1];
        TextChannel mainGameChannel = activeGame.getMainGameChannel();
        Tile tile = activeGame.getTile(AliasHandler.resolveTile(planet));

        new AddUnits().unitParsing(event, player.getColor(),
            activeGame.getTile(AliasHandler.resolveTile(planet)), amount + " inf " + planet,
            activeGame);
        MessageHelper.sendMessageToChannel(event.getChannel(), trueIdentity + " Chose to land " + amount + " infantry on " + Helper.getPlanetRepresentation(planet, activeGame));
        UnitHolder unitHolder = tile.getUnitHolders().get(planet);
        for (Player player2 : activeGame.getRealPlayers()) {
            if (player2 == player) {
                continue;
            }
            String colorID = Mapper.getColorID(player2.getColor());
            int numMechs = 0;
            int numInf = 0;
            if (unitHolder.getUnits() != null) {
                numMechs = unitHolder.getUnitCount(UnitType.Mech, colorID);
                numInf = unitHolder.getUnitCount(UnitType.Infantry, colorID);
            }

            if (numInf > 0 || numMechs > 0) {
                String messageCombat = "Resolve ground combat.";

                if (!activeGame.isFoWMode()) {
                    MessageCreateBuilder baseMessageObject = new MessageCreateBuilder().addContent(messageCombat);
                    String threadName = activeGame.getName() + "-yinHero-" + activeGame.getRound() + "-planet-" + planet + "-" + player.getFaction() + "-vs-" + player2.getFaction();
                    mainGameChannel.sendMessage(baseMessageObject.build()).queue(message_ -> {
                        ThreadChannelAction threadChannel = mainGameChannel.createThreadChannel(threadName, message_.getId());
                        threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR);
                        threadChannel.queue(m5 -> {
                            List<ThreadChannel> threadChannels = activeGame.getActionsChannel().getThreadChannels();
                            if (threadChannels != null) {
                                for (ThreadChannel threadChannel_ : threadChannels) {
                                    if (threadChannel_.getName().equals(threadName)) {
                                        MessageHelper.sendMessageToChannel(threadChannel_,
                                            Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true)
                                                + Helper.getPlayerRepresentation(player2, activeGame, activeGame.getGuild(), true)
                                                + " Please resolve the interaction here. Reminder that Yin Hero skips pds fire.");
                                        int context = 0;
                                        FileUpload systemWithContext = GenerateTile.getInstance().saveImage(activeGame, context, tile.getPosition(), event);
                                        MessageHelper.sendMessageWithFile(threadChannel_, systemWithContext, "Picture of system", false);
                                        List<Button> buttons = ButtonHelper.getButtonsForPictureCombats(activeGame, tile.getPosition(), player, player2, "ground");
                                        MessageHelper.sendMessageToChannelWithButtons(threadChannel_, "", buttons);

                                    }
                                }
                            }
                        });
                    });
                }
                break;
            }

        }

        event.getMessage().delete().queue();
    }

    public static List<Button> getGhostHeroTilesStep1(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<Button>();
        for (Tile tile : activeGame.getTileMap().values()) {
            if (tile.getPosition().contains("t") || tile.getPosition().contains("b")) {
                continue;
            }
            if (FoWHelper.doesTileHaveWHs(activeGame, tile.getPosition(), player) || FoWHelper.playerHasUnitsInSystem(player, tile)) {
                buttons.add(Button.secondary("creussHeroStep1_" + tile.getPosition(), tile.getRepresentationForButtons(activeGame, player)));
            }

        }
        return buttons;
    }

    public static List<Button> getBenediction2ndTileOptions(Player player, Game activeGame, String pos1) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        Player origPlayer = player;
        Tile tile1 = activeGame.getTileByPosition(pos1);
        List<Player> players2 = ButtonHelper.getOtherPlayersWithShipsInTheSystem(player, activeGame, tile1);
        if (players2.size() != 0) {
            player = players2.get(0);
        }
        for (String pos2 : FoWHelper.getAdjacentTiles(activeGame, pos1, player, false)) {
            if (pos1.equalsIgnoreCase(pos2)) {
                continue;
            }
            Tile tile2 = activeGame.getTileByPosition(pos2);
            if (FoWHelper.otherPlayersHaveShipsInSystem(player, tile2, activeGame)) {
                buttons.add(Button.secondary(finChecker + "mahactBenedictionFrom_" + pos1 + "_" + pos2, tile2.getRepresentationForButtons(activeGame, origPlayer)));
            }
        }
        return buttons;
    }

    public static List<Button> getBenediction1stTileOptions(Player player, Game activeGame) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (Tile tile1 : activeGame.getTileMap().values()) {
            String pos1 = tile1.getPosition();
            for (Player p2 : activeGame.getRealPlayers()) {
                if (FoWHelper.playerHasShipsInSystem(p2, tile1)) {
                    boolean adjacentPeeps = false;
                    for (String pos2 : FoWHelper.getAdjacentTiles(activeGame, pos1, p2, false)) {
                        if (pos1.equalsIgnoreCase(pos2)) {
                            continue;
                        }
                        Tile tile2 = activeGame.getTileByPosition(pos2);
                        if (FoWHelper.otherPlayersHaveShipsInSystem(player, tile2, activeGame)) {
                            adjacentPeeps = true;
                        }
                    }
                    if (adjacentPeeps) {
                        buttons.add(Button.secondary(finChecker + "benedictionStep1_" + pos1, tile1.getRepresentationForButtons(activeGame, player)));
                    }
                    break;
                }

            }

        }

        return buttons;
    }

    public static List<Button> getJolNarHeroSwapOutOptions(Player player, Game activeGame) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (String tech : player.getTechs()) {
            TechnologyModel techM = Mapper.getTech(tech);
            if (!techM.getType().toString().equalsIgnoreCase("unitupgrade")) {
                buttons.add(Button.secondary(finChecker + "jnHeroSwapOut_" + tech, techM.getName()));
            }
        }
        buttons.add(Button.danger("deleteButtons", "Done resolving"));
        return buttons;
    }

    public static List<Button> getJolNarHeroSwapInOptions(Player player, Game activeGame, String buttonID) {
        String tech = buttonID.split("_")[1];
        TechnologyModel techM = Mapper.getTech(tech);
        List<TechnologyModel> techs = Helper.getAllTechOfAType(activeGame, techM.getType().toString(), player.getFaction(), player);
        List<Button> buttons = Helper.getTechButtons(techs, techM.getType().toString(), player, tech);
        return buttons;
    }

    public static void resolveAJolNarSwapStep1(Player player, Game activeGame, String buttonID, ButtonInteractionEvent event) {
        List<Button> buttons = getJolNarHeroSwapInOptions(player, activeGame, buttonID);
        String message = ButtonHelper.getTrueIdentity(player, activeGame) + " select the tech you would like to acquire";
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void resolveAJolNarSwapStep2(Player player, Game activeGame, String buttonID, ButtonInteractionEvent event) {
        String techOut = buttonID.split("_")[1];
        String techIn = buttonID.split("_")[2];
        TechnologyModel techM1 = Mapper.getTech(techOut);
        TechnologyModel techM2 = Mapper.getTech(techIn);
        player.addTech(techIn);
        player.removeTech(techOut);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
            ButtonHelper.getIdent(player) + " swapped the tech \'" + techM1.getName() + "\' for the tech \'" + techM2.getName() + "\'");
        event.getMessage().delete().queue();
    }

    public static void mahactBenediction(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player) {
        String pos1 = buttonID.split("_")[1];
        String pos2 = buttonID.split("_")[2];
        Tile tile1 = activeGame.getTileByPosition(pos1);
        Tile tile2 = activeGame.getTileByPosition(pos2);
        List<Player> players2 = ButtonHelper.getOtherPlayersWithShipsInTheSystem(player, activeGame, tile1);
        if (players2.size() != 0) {
            player = players2.get(0);
        }
        for (Map.Entry<String, UnitHolder> entry : tile1.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
            if (unitHolder instanceof Planet) continue;
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;

                UnitKey unitKey = unitEntry.getKey();
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int totalUnits = unitEntry.getValue();
                int damagedUnits = 0;

                if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                }

                new RemoveUnits().removeStuff(event, tile1, totalUnits, "space", unitKey, player.getColor(), false, activeGame);
                new AddUnits().unitParsing(event, player.getColor(), tile2, totalUnits + " " + unitName, activeGame);
                if (damagedUnits > 0) {
                    activeGame.getTileByPosition(pos2).addUnitDamage("space", unitKey, damagedUnits);
                }
            }
            List<Player> players = ButtonHelper.getOtherPlayersWithShipsInTheSystem(player, activeGame, tile2);
            if (players.size() > 0 && !player.getAllianceMembers().contains(players.get(0).getFaction())) {
                Player player2 = players.get(0);
                if (player2 == player) {
                    player2 = players.get(1);
                }

                String threadName = ButtonHelper.combatThreadName(activeGame, player, player2, tile2);
                if (threadName.contains("private")) {
                    threadName = threadName.replace("private", "benediction-private");
                } else {
                    threadName = threadName + "-benediction";
                }
                if (!activeGame.isFoWMode()) {
                    ButtonHelper.makeACombatThread(activeGame, activeGame.getActionsChannel(), player, player2, threadName, tile2, event, "space");
                } else {
                    ButtonHelper.makeACombatThread(activeGame, player.getPrivateChannel(), player, player2, threadName, tile2, event, "space");
                    ButtonHelper.makeACombatThread(activeGame, player2.getPrivateChannel(), player2, player, threadName, tile2, event, "space");
                    for (Player player3 : activeGame.getRealPlayers()) {
                        if (player3 == player2 || player3 == player) {
                            continue;
                        }
                        if (!tile2.getRepresentationForButtons(activeGame, player3).contains("(")) {
                            continue;
                        }
                        ButtonHelper.makeACombatThread(activeGame, player3.getPrivateChannel(), player3, player3, threadName, tile2, event, "space");
                    }
                }
            }

        }
    }

    public static void getGhostHeroTilesStep2(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos1 = buttonID.split("_")[1];
        List<Button> buttons = new ArrayList<Button>();
        Tile tile1 = activeGame.getTileByPosition(pos1);
        for (Tile tile : activeGame.getTileMap().values()) {
            if (tile.getPosition().contains("t") || tile.getPosition().contains("b") || tile == tile1) {
                continue;
            }
            if (FoWHelper.doesTileHaveWHs(activeGame, tile.getPosition(), player) || FoWHelper.playerHasUnitsInSystem(player, tile)) {
                buttons.add(Button.secondary("creussHeroStep2_" + pos1 + "_" + tile.getPosition(), tile.getRepresentationForButtons(activeGame, player)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
            ButtonHelper.getTrueIdentity(player, activeGame) + " Chose the tile you want to swap places with " + tile1.getRepresentationForButtons(activeGame, player), buttons);
        event.getMessage().delete().queue();
    }

    public static void killShipsSardakkHero(Player player, Game activeGame, ButtonInteractionEvent event) {
        String pos1 = activeGame.getActiveSystem();
        Tile tile1 = activeGame.getTileByPosition(pos1);
        for (Map.Entry<String, UnitHolder> entry : tile1.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
            if (unitHolder instanceof Planet) continue;
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;

                UnitKey unitKey = unitEntry.getKey();
                int totalUnits = unitEntry.getValue();
                if (!unitKey.getUnitType().equals(UnitType.Infantry) && !unitKey.getUnitType().equals(UnitType.Mech)) {
                    new RemoveUnits().removeStuff(event, tile1, totalUnits, "space", unitKey, player.getColor(), false, activeGame);
                }
            }
        }
    }

    public static void resolveWinnuHeroSC(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Integer sc = Integer.parseInt(buttonID.split("_")[1]);
        new SCPlay().playSC(event, sc, activeGame, activeGame.getMainGameChannel(), player, true);
        event.getMessage().delete().queue();
    }

    public static List<Button> getWinnuHeroSCButtons(Game activeGame, Player winnu) {
        List<Button> scButtons = new ArrayList<>();
        for (Integer sc : activeGame.getSCList()) {
            if (sc <= 0) continue; // some older games have a 0 in the list of SCs
            Emoji scEmoji = Emoji.fromFormatted(Emojis.getSCBackEmojiFromInteger(sc));
            Button button;
            String label = " ";
            if (scEmoji.getName().contains("SC") && scEmoji.getName().contains("Back") && !activeGame.isHomeBrewSCMode()) {
                button = Button.secondary("winnuHero_" + sc, label).withEmoji(scEmoji);
            } else {
                button = Button.secondary("winnuHero_" + sc, "" + sc + label);
            }
            scButtons.add(button);
        }
        return scButtons;
    }

    public static List<Button> getNRAHeroButtons(Game activeGame, Player winnu) {
        List<Button> scButtons = new ArrayList<>();
        if (activeGame.getScPlayed().get(1) == null || !activeGame.getScPlayed().get(1)) {
            scButtons.add(Button.success("leadershipGenerateCCButtons", "Gain CCs"));
            scButtons.add(Button.danger("leadershipExhaust", "Exhaust Planets"));
        }
        if (activeGame.getScPlayed().get(2) == null || !activeGame.getScPlayed().get(2)) {
            scButtons.add(Button.success("diploRefresh2", "Ready 2 Planets"));
        }
        if (activeGame.getScPlayed().get(3) == null || !activeGame.getScPlayed().get(3)) {
            scButtons.add(Button.secondary("sc_ac_draw", "Draw 2 Action Cards").withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
        }
        if (activeGame.getScPlayed().get(4) == null || !activeGame.getScPlayed().get(4)) {
            scButtons.add(Button.success("construction_sd", "Place A SD").withEmoji(Emoji.fromFormatted(Emojis.spacedock)));
            scButtons.add(Button.success("construction_pds", "Place a PDS").withEmoji(Emoji.fromFormatted(Emojis.pds)));
        }
        if (activeGame.getScPlayed().get(5) == null || !activeGame.getScPlayed().get(5)) {
            scButtons.add(Button.secondary("sc_refresh", "Replenish Commodities").withEmoji(Emoji.fromFormatted(Emojis.comm)));
        }
        if (activeGame.getScPlayed().get(6) == null || !activeGame.getScPlayed().get(6)) {
            scButtons.add(Button.success("warfareBuild", "Build At Home"));
        }
        if (activeGame.getScPlayed().get(7) == null || !activeGame.getScPlayed().get(7)) {
            activeGame.setComponentAction(true);
            scButtons.add(Button.success("acquireATech", "Get a Tech"));
        }
        if (activeGame.getScPlayed().get(8) == null || !activeGame.getScPlayed().get(8)) {
            scButtons.add(Button.secondary("sc_draw_so", "Draw Secret Objective").withEmoji(Emoji.fromFormatted(Emojis.SecretObjective)));
        }
        scButtons.add(Button.danger("deleteButtons", "Done resolving"));

        return scButtons;
    }

    public static void resolveGhostHeroStep2(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String position = buttonID.split("_")[1];
        String position2 = buttonID.split("_")[2];
        Tile tile = activeGame.getTileByPosition(position);
        Tile tile2 = activeGame.getTileByPosition(position2);
        tile.setPosition(position2);
        tile2.setPosition(position);
        activeGame.setTile(tile);
        activeGame.setTile(tile2);
        activeGame.rebuildTilePositionAutoCompleteList();
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " Chose to swap "
            + tile2.getRepresentationForButtons(activeGame, player) + " with " + tile.getRepresentationForButtons(activeGame, player));
        event.getMessage().delete().queue();
    }
}