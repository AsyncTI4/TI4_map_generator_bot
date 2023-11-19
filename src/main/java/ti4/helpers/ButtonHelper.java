package ti4.helpers;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

import ti4.buttons.ButtonListener;
import ti4.commands.agenda.RevealAgenda;
import ti4.commands.agenda.ShowDiscardedAgendas;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsac.PlayAC;
import ti4.commands.cardsac.ShowDiscardActionCards;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.explore.ExpFrontier;
import ti4.commands.explore.ExpInfo;
import ti4.commands.explore.SendFragments;
import ti4.commands.leaders.ExhaustLeader;
import ti4.commands.leaders.HeroPlay;
import ti4.commands.leaders.RefreshLeader;
import ti4.commands.leaders.UnlockLeader;
import ti4.commands.planet.PlanetAdd;
import ti4.commands.player.SendDebt;
import ti4.commands.player.Setup;
import ti4.commands.special.CombatRoll;
import ti4.commands.special.StellarConverter;
import ti4.commands.status.Cleanup;
import ti4.commands.status.ListTurnOrder;
import ti4.commands.tokens.AddCC;
import ti4.commands.tokens.AddToken;
import ti4.commands.tokens.RemoveCC;
import ti4.commands.units.AddUnits;
import ti4.commands.units.MoveUnits;
import ti4.commands.units.RemoveUnits;
import ti4.draft.FrankenDraft;
import ti4.generator.GenerateMap;
import ti4.generator.GenerateTile;
import ti4.generator.Mapper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;
import ti4.model.PlanetModel;
import ti4.model.PromissoryNoteModel;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.selections.selectmenus.SelectFaction;

public class ButtonHelper {

    public static boolean doesPlayerHaveFSHere(String flagshipID, Player player, Tile tile){
        if(!player.hasUnit(flagshipID)){
            return false;
        }
        UnitHolder space = tile.getUnitHolders().get("space");
        if(space.getUnitCount(UnitType.Flagship, player.getColor()) > 0){
            return true;
        }

        return false;
    }
    public static void resolveInfantryDeath(Game activeGame, Player player, int amount) {
        if (player.hasInf2Tech()) {
            for (int x = 0; x < amount; x++) {
                MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), rollInfantryRevival(activeGame, player));
            }
        }
    }

    public static List<Button> getDacxiveButtons(Game activeGame, Player player, String planet) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("dacxive_" + planet, "Resolve Dacxive"));
        buttons.add(Button.danger("deleteButtons", "No Dacxive"));
        return buttons;
    }

    public static List<Button> getForcedPNSendButtons(Game activeGame, Player player, Player p1) {
        List<Button> stuffToTransButtons = new ArrayList<Button>();
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
        return stuffToTransButtons;
    }

    public static void checkTransactionLegality(Game activeGame, Player player, Player player2) {
        if (player == player2 || !"action".equalsIgnoreCase(activeGame.getCurrentPhase()) || player.hasAbility("guild_ships") || player.getPromissoryNotes().containsKey("convoys")
            || player2.getPromissoryNotes().containsKey("convoys") || player2.hasAbility("guild_ships") || player2.getNeighbouringPlayers().contains(player)
            || player.getNeighbouringPlayers().contains(player2)) {
            return;
        }
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame),
            Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " this is a friendly reminder that you are not neighbors with " + player2.getColor());
    }

    public static void riftUnitsButton(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        Tile tile = activeGame.getTileByPosition(buttonID.replace("getRiftButtons_", ""));
        MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, activeGame), ident + " Use buttons to rift units", getButtonsForRiftingUnitsInSystem(player, activeGame, tile));
    }

    public static void riftUnitButton(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        String rest = buttonID.replace("riftUnit_", "").toLowerCase();
        String pos = rest.substring(0, rest.indexOf("_"));
        Tile tile = activeGame.getTileByPosition(pos);
        rest = rest.replace(pos + "_", "");
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unit = rest;
        for (int x = 0; x < amount; x++) {
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), ident + " " + riftUnit(unit, tile, activeGame, event, player, null));
        }
        String message = event.getMessage().getContentRaw();
        List<Button> systemButtons = getButtonsForRiftingUnitsInSystem(player, activeGame, tile);
        event.getMessage().editMessage(message)
            .setComponents(turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    public static void arboAgentOnButton(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        String rest = buttonID.replace("arboAgentOn_", "").toLowerCase();
        String pos = rest.substring(0, rest.indexOf("_"));
        Tile tile = activeGame.getTileByPosition(pos);
        rest = rest.replace(pos + "_", "");
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unit = rest;
        for (int x = 0; x < amount; x++) {
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), ident + " " + riftUnit(unit, tile, activeGame, event, player, null));
        }
        event.getMessage().delete().queue();
    }

    public static void riftAllUnitsButton(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        String pos = buttonID.replace("riftAllUnits_", "").toLowerCase();
        riftAllUnitsInASystem(pos, event, activeGame, player, ident, null);
    }

    public static void riftAllUnitsInASystem(String pos, ButtonInteractionEvent event, Game activeGame, Player player, String ident, Player cabal) {
        Tile tile = activeGame.getTileByPosition(pos);

        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null) {
            }
            UnitHolder unitHolder = entry.getValue();
            HashMap<UnitKey, Integer> units = unitHolder.getUnits();
            if (unitHolder instanceof Planet planet) {
            } else {
                Map<UnitKey, Integer> tileUnits = new HashMap<>(units);
                for (Map.Entry<UnitKey, Integer> unitEntry : tileUnits.entrySet()) {
                    if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                    UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                    if (unitModel == null) continue;

                    UnitKey key = unitEntry.getKey();
                    if (key.getUnitType().equals(UnitType.Infantry)
                        || key.getUnitType().equals(UnitType.Mech)
                        || (!player.hasFF2Tech() && key.getUnitType().equals(UnitType.Fighter))
                        || (cabal != null && (key.getUnitType().equals(UnitType.Fighter) || key.getUnitType().equals(UnitType.Spacedock)))) {
                        continue;
                    }

                    int totalUnits = unitEntry.getValue();
                    String unitKey = unitModel.getAsyncId();
                    unitKey = getUnitName(unitKey);
                    int damagedUnits = 0;
                    if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null) {
                        damagedUnits = unitHolder.getUnitDamage().get(key);
                    }
                    for (int x = 1; x < damagedUnits + 1; x++) {
                        MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), ident + " " + riftUnit(unitKey + "damaged", tile, activeGame, event, player, cabal));
                    }
                    totalUnits = totalUnits - damagedUnits;
                    for (int x = 1; x < totalUnits + 1; x++) {
                        MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), ident + " " + riftUnit(unitKey, tile, activeGame, event, player, cabal));
                    }
                }
            }
        }
        if (cabal == null) {
            String message = event.getMessage().getContentRaw();
            List<Button> systemButtons = getButtonsForRiftingUnitsInSystem(player, activeGame, tile);
            event.getMessage().editMessage(message)
                .setComponents(turnButtonListIntoActionRowList(systemButtons)).queue();
        } else {
            List<ActionRow> actionRow2 = new ArrayList<>();
            String exhaustedMessage = event.getMessage().getContentRaw();
            for (ActionRow row : event.getMessage().getActionRows()) {
                List<ItemComponent> buttonRow = row.getComponents();
                int buttonIndex = buttonRow.indexOf(event.getButton());
                if (buttonIndex > -1) {
                    buttonRow.remove(buttonIndex);
                }
                if (buttonRow.size() > 0) {
                    actionRow2.add(ActionRow.of(buttonRow));
                }
            }
            if ("".equalsIgnoreCase(exhaustedMessage)) {
                exhaustedMessage = "Rift";
            }
            if (actionRow2.size() > 0) {
                event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
            } else {
                event.getMessage().delete().queue();
            }
        }

    }

    public static String riftUnit(String unit, Tile tile, Game activeGame, GenericInteractionCreateEvent event, Player player, Player cabal) {
        boolean damaged = false;
        if (unit.contains("damaged")) {
            unit = unit.replace("damaged", "");
            damaged = true;
        }
        Die d1 = new Die(4);
        String msg = Emojis.getEmojiFromDiscord(unit.toLowerCase()) + " rolled a " + d1.getResult();
        if (damaged) {
            msg = "A damaged " + msg;
        }
        if (d1.isSuccess()) {
            msg = msg + " and survived. May you always be so lucky.";
        } else {
            UnitKey key = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor());
            new RemoveUnits().removeStuff(event, tile, 1, "space", key, player.getColor(), damaged, activeGame);
            msg = msg + " and failed. Condolences for your loss.";
            if (cabal != null && cabal != player && !ButtonHelperFactionSpecific.isCabalBlockadedByPlayer(player, activeGame, cabal)) {
                ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, 1, unit, event);
            }
        }

        return msg;
    }

    public static boolean shouldKeleresRiderExist(Game activeGame){
        if(activeGame.getPNOwner("ridera")!= null || activeGame.getPNOwner("riderm")!= null || activeGame.getPNOwner("riderx")!= null || activeGame.getPNOwner("ridera")!= null) {
            return true;
        }else{
            return false;
        }
    }
    public static String rollInfantryRevival(Game activeGame, Player player) {

        Die d1 = new Die(6);
        if (player.hasTech("so2")) {
            d1 = new Die(5);
        }
        String msg = Emojis.infantry + " rolled a " + d1.getResult();
        if (player.hasTech("cl2")) {
            msg = Emojis.infantry + " died";

        }
        if (d1.isSuccess() || player.hasTech("cl2")) {
            msg = msg + " and revived. You will be prompted to place them on a planet in your HS at the start of your next turn.";
            player.setStasisInfantry(player.getStasisInfantry() + 1);
        } else {
            msg = msg + " and failed. No revival";
        }
        return getIdent(player) + " " + msg;
    }
    public static void rollMykoMechRevival(Game activeGame, Player player) {
        Die d1 = new Die(6);
        String msg = Emojis.mech + " rolled a " + d1.getResult();
        if (d1.isSuccess()) {
            msg = msg + " and revived. You will be prompted to replace an infantry with a mech at the start of your turn.";
            ButtonHelperFactionSpecific.increaseMykoMech(activeGame);
        } else {
            msg = msg + " and failed. No revival";
        }
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), getIdent(player) + " " + msg);
    }

    public static void placeInfantryFromRevival(Game activeGame, ButtonInteractionEvent event, Player player, String buttonID) {
        String planet = buttonID.split("_")[1];
        String amount = "1";
        if (StringUtils.countMatches(buttonID, "_") > 1) {
            amount = buttonID.split("_")[2];
        } else {
            amount = "1";
        }

        Tile tile = activeGame.getTileFromPlanet(planet);
        new AddUnits().unitParsing(event, player.getColor(), tile, amount + " inf " + planet, activeGame);
        player.setStasisInfantry(player.getStasisInfantry() - Integer.parseInt(amount));
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame),
            getIdent(player) + " Placed " + amount + " infantry on " + Helper.getPlanetRepresentation(planet, activeGame) + ". You have " + player.getStasisInfantry() + " infantry left to revive.");
        if (player.getStasisInfantry() == 0) {
            event.getMessage().delete().queue();
        }
    }

    public static MessageChannel getSCFollowChannel(Game activeGame, Player player, int scNum) {
        String threadName = activeGame.getName() + "-round-" + activeGame.getRound() + "-";
        switch (scNum) {
            case 1 -> threadName = threadName + "leadership";
            case 2 -> threadName = threadName + "diplomacy";
            case 3 -> threadName = threadName + "politics";
            case 4 -> threadName = threadName + "construction";
            case 5 -> threadName = threadName + "trade";
            case 6 -> threadName = threadName + "warfare";
            case 7 -> threadName = threadName + "technology";
            case 8 -> threadName = threadName + "imperial";
            default -> {
                return getCorrectChannel(player, activeGame);
            }
        }
        List<ThreadChannel> threadChannels = activeGame.getMainGameChannel().getThreadChannels();
        for (ThreadChannel threadChannel_ : threadChannels) {
            if (threadChannel_.getName().equals(threadName)) {
                return threadChannel_;
            }
        }
        return getCorrectChannel(player, activeGame);
    }

    public static List<String> getTypesOfPlanetPlayerHas(Game activeGame, Player player) {
        List<String> types = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            if(planet.contains("custodia") || planet.contains("ghoti")){
                continue;
            }
            UnitHolder unitHolder = activeGame.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            boolean oneOfThree = planetReal != null && planetReal.getOriginalPlanetType() != null && ("industrial".equalsIgnoreCase(planetReal.getOriginalPlanetType())
                || "cultural".equalsIgnoreCase(planetReal.getOriginalPlanetType()) || "hazardous".equalsIgnoreCase(planetReal.getOriginalPlanetType()));
            if (planetReal != null && oneOfThree && !types.contains(planetReal.getOriginalPlanetType())) {
                types.add(planetReal.getOriginalPlanetType());
            }
            if(unitHolder.getTokenList().contains("attachment_titanspn.png")){
                if(!types.contains("hazardous")){
                    types.add("hazardous");
                }
                if(!types.contains("industrial")){
                    types.add("industrial");
                }
                if(!types.contains("cultural")){
                    types.add("cultural");
                }
            }
        }
        return types;
    }

    public static List<Button> getPlaceStatusInfButtons(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();

        Tile tile = activeGame.getTile(AliasHandler.resolveTile(player.getFaction()));
        if (tile == null) {
            tile = getTileOfPlanetWithNoTrait(player, activeGame);
        }
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder instanceof Planet) {
                if(player.getPlanets().contains(unitHolder.getName())){
                    buttons.add(Button.success("statusInfRevival_" + unitHolder.getName() + "_1", "Place 1 infantry on " + Helper.getPlanetRepresentation(unitHolder.getName(), activeGame)));
                    if (player.getStasisInfantry() > 1) {
                        buttons.add(Button.success("statusInfRevival_" + unitHolder.getName() + "_" + player.getStasisInfantry(),
                            "Place " + player.getStasisInfantry() + " infantry on " + Helper.getPlanetRepresentation(unitHolder.getName(), activeGame)));

                    }
                }
                
            }
        }
        return buttons;

    }

    public static List<Button> getExhaustButtonsWithTG(Game activeGame, Player player, GenericInteractionCreateEvent event) {
        List<Button> buttons = Helper.getPlanetExhaustButtons(event, player, activeGame);
        if (player.getTg() > 0) {
            Button lost1TG = Button.danger("reduceTG_1", "Spend 1 TG");
            buttons.add(lost1TG);
        }
        if (player.getTg() > 1) {
            Button lost2TG = Button.danger("reduceTG_2", "Spend 2 TGs");
            buttons.add(lost2TG);
        }
        if (player.getTg() > 2) {
            Button lost3TG = Button.danger("reduceTG_3", "Spend 3 TGs");
            buttons.add(lost3TG);
        }
        if (player.hasUnexhaustedLeader("keleresagent") && player.getCommodities() > 0) {
            Button lost1C = Button.danger("reduceComm_1", "Spend 1 comm");
            buttons.add(lost1C);
        }
        if (player.hasUnexhaustedLeader("keleresagent") && player.getCommodities() > 1) {
            Button lost2C = Button.danger("reduceComm_2", "Spend 2 comms");
            buttons.add(lost2C);
        }
        if (player.getNomboxTile().getUnitHolders().get("space").getUnits().size() > 0 && !event.getId().contains("leadership")) {
            Button release = Button.secondary("getReleaseButtons", "Release captured units").withEmoji(Emoji.fromFormatted(Emojis.getFactionIconFromDiscord("cabal")));
            buttons.add(release);
        }
        if (player.hasUnexhaustedLeader("khraskagent") && event.getId().contains("leadership")) {
            Button release = Button.secondary("exhaustAgent_khraskagent", "Exhaust Khrask Agent").withEmoji(Emoji.fromFormatted(Emojis.getFactionIconFromDiscord("khrask")));
            buttons.add(release);
        }
        if (player.hasAbility("diplomats") && ButtonHelperAbilities.getDiplomatButtons(activeGame, player).size() > 0) {
            Button release = Button.secondary("getDiplomatsButtons", "Use Diplomats Ability").withEmoji(Emoji.fromFormatted(Emojis.freesystems));
            buttons.add(release);
        }

        return buttons;
    }

    public static List<Player> getPlayersWhoHaveNoSC(Player player, Game activeGame) {
        List<Player> playersWhoDontHaveSC = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2.getSCs().size() > 0 || p2 == player) {
                continue;
            }
            playersWhoDontHaveSC.add(p2);
        }
        if (playersWhoDontHaveSC.isEmpty()) {
            playersWhoDontHaveSC.add(player);
        }
        return playersWhoDontHaveSC;
    }

    public static List<Player> getPlayersWhoHaventReacted(String messageId, Game activeGame) {
        List<Player> playersWhoAreMissed = new ArrayList<>();
        if (messageId == null || "".equalsIgnoreCase(messageId)) {
            return playersWhoAreMissed;
        }
        TextChannel mainGameChannel = activeGame.getMainGameChannel();
        if (mainGameChannel == null) {
            return playersWhoAreMissed;
        }
        try{
            Message mainMessage = mainGameChannel.retrieveMessageById(messageId).completeAfter(100,
                TimeUnit.MILLISECONDS);
            for (Player player : activeGame.getPlayers().values()) {
                if (!player.isRealPlayer()) {
                    continue;
                }

                String faction = player.getFaction();
                if (faction == null || faction.isEmpty() || "null".equals(faction)) {
                    continue;
                }

                Emoji reactionEmoji = Emoji.fromFormatted(player.getFactionEmoji());
                if (activeGame.isFoWMode()) {
                    int index = 0;
                    for (Player player_ : activeGame.getPlayers().values()) {
                        if (player_ == player)
                            break;
                        index++;
                    }
                    reactionEmoji = Emoji.fromFormatted(Emojis.getRandomizedEmoji(index, messageId));
                }
                MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
                if (reaction == null) {
                    playersWhoAreMissed.add(player);
                }
            }
            return playersWhoAreMissed;
        }catch(Exception e){
            return playersWhoAreMissed;
        }
    }

    public static String playerHasDMZPlanet(Player player, Game activeGame){
        String dmzPlanet = "no";
        for(String planet : player.getPlanets()){
            if(planet.contains("custodia") || planet.contains("ghoti")){
                continue;
            }
            UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
            Set<String> tokenList = unitHolder.getTokenList();
            if (tokenList.stream().anyMatch(token -> token.contains("dmz_large") || token.contains("dmz") )) {
                dmzPlanet = planet;
                break;
            }
        }
        return dmzPlanet;
    }

    public static List<Button> getTradePlanetsWithAlliancePartnerButtons(Player p1, Player receiver, Game activeGame) {
        List<Button> buttons = new ArrayList<Button>();
        if (!p1.getAllianceMembers().contains(receiver.getFaction())) {
            return buttons;
        }
        for (String planet : p1.getPlanets()) {
            if (planet.contains("custodia")|| planet.contains("ghoti")) {
                continue;
            }
            if (ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame).getUnitColorsOnHolder().contains(receiver.getColorID())) {
                String refreshed = "refreshed";
                if(p1.getExhaustedPlanets().contains(planet)){
                    refreshed="exhausted";
                }
                buttons.add(Button.secondary("resolveAlliancePlanetTrade_" + planet + "_" + receiver.getFaction()+"_"+refreshed, Helper.getPlanetRepresentation(planet, activeGame)));
            }
        }
        return buttons;
    }

    public static void resolveAllianceMemberPlanetTrade(Player p1, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String dmzPlanet = buttonID.split("_")[1];
        String receiverFaction = buttonID.split("_")[2];
        String exhausted = buttonID.split("_")[3];
        Player p2 = activeGame.getPlayerFromColorOrFaction(receiverFaction);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p1, activeGame), "Could not resolve second player, please resolve manually.");
            return;
        }
        UnitHolder oriPlanet = ButtonHelper.getUnitHolderFromPlanetName(dmzPlanet, activeGame);
        new PlanetAdd().doAction(p2, dmzPlanet, activeGame, event);
        if(!exhausted.equalsIgnoreCase("exhausted")){
            p2.refreshPlanet(dmzPlanet);
        }
        List<Button> goAgainButtons = new ArrayList<Button>();
        Button button = Button.secondary("transactWith_" + p2.getColor(), "Send something else to player?");
        Button done = Button.secondary("finishTransaction_" + p2.getColor(), "Done With This Transaction");
        String ident = ButtonHelper.getIdentOrColor(p1, activeGame);
        String message2 = ident + " traded the planet " + Helper.getPlanetRepresentation(dmzPlanet, activeGame) + " to " + ButtonHelper.getIdentOrColor(p2, activeGame);
        goAgainButtons.add(button);
        goAgainButtons.add(done);
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(p1.getPrivateChannel(), message2);
            MessageHelper.sendMessageToChannelWithButtons(p1.getPrivateChannel(), ident + " Use Buttons To Complete Transaction", goAgainButtons);
            MessageHelper.sendMessageToChannel(p2.getPrivateChannel(), message2);
        } else {
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), message2);
            MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), ident + " Use Buttons To Complete Transaction", goAgainButtons);
        }
        event.getMessage().delete().queue();
    }

    public static void resolveDMZTrade(Player p1, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String dmzPlanet = buttonID.split("_")[1];
        String receiverFaction = buttonID.split("_")[2];
        Player p2 = activeGame.getPlayerFromColorOrFaction(receiverFaction);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p1, activeGame), "Could not resolve second player, please resolve manually.");
            return;
        }
        UnitHolder oriPlanet = ButtonHelper.getUnitHolderFromPlanetName(dmzPlanet, activeGame);
        new PlanetAdd().doAction(p2, dmzPlanet, activeGame, event);
        List<Button> goAgainButtons = new ArrayList<Button>();
        Button button = Button.secondary("transactWith_" + p2.getColor(), "Send something else to player?");
        Button done = Button.secondary("finishTransaction_" + p2.getColor(), "Done With This Transaction");
        String ident = ButtonHelper.getIdentOrColor(p1, activeGame);
        String message2 = ident + " traded the planet " + Helper.getPlanetRepresentation(dmzPlanet, activeGame) + " to " + ButtonHelper.getIdentOrColor(p2, activeGame);
        goAgainButtons.add(button);
        goAgainButtons.add(done);
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(p1.getPrivateChannel(), message2);
            MessageHelper.sendMessageToChannelWithButtons(p1.getPrivateChannel(), ident + " Use Buttons To Complete Transaction", goAgainButtons);
            MessageHelper.sendMessageToChannel(p2.getPrivateChannel(), message2);
        } else {
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), message2);
            MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), ident + " Use Buttons To Complete Transaction", goAgainButtons);
        }
        event.getMessage().delete().queue();
    }

    public static boolean canIBuildGFInSpace(Game activeGame, Player player, Tile tile, String kindOfBuild) {
        HashMap<String, UnitHolder> unitHolders = tile.getUnitHolders();

        if ("freelancers".equalsIgnoreCase(kindOfBuild) || "genericBuild".equalsIgnoreCase(kindOfBuild) || "muaatagent".equalsIgnoreCase(kindOfBuild)) {
            return true;
        }

        for (UnitHolder unitHolder : unitHolders.values()) {
            if (unitHolder instanceof Planet) {
                continue;
            }

            for (Map.Entry<UnitKey, Integer> unitEntry : unitHolder.getUnits().entrySet()) {
                if (unitEntry.getValue() > 0 && player.unitBelongsToPlayer(unitEntry.getKey())) {
                    UnitModel model = player.getUnitFromUnitKey(unitEntry.getKey());
                    if (model != null && model.getProductionValue() > 0) return true;
                }
            }
        }

        return player.getTechs().contains("mr") && tile.getTileModel().isSupernova();
    }

    public static void getTech(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String ident = ButtonHelper.getIdent(player);
        String tech = buttonID.split("_")[1];
        TechnologyModel techM = Mapper.getTechs().get(AliasHandler.resolveTech(tech));
        String message = ident + " Acquired The Tech " + techM.getRepresentation(false);
        
        if (techM != null && techM.getRequirements().isPresent() && techM.getRequirements().get().length() > 1) {
            if (player.getLeaderIDs().contains("zealotscommander") && !player.hasLeaderUnlocked("zealotscommander")) {
                ButtonHelper.commanderUnlockCheck(player, activeGame, "zealots", event);
            }
        }
        player.addTech(AliasHandler.resolveTech(tech));
        ButtonHelperFactionSpecific.resolveResearchAgreementCheck(player, tech, activeGame);
        ButtonHelperCommanders.resolveNekroCommanderCheck(player, tech, activeGame);
        if ("iihq".equalsIgnoreCase(AliasHandler.resolveTech(tech))) {
            message = message + "\n Automatically added the Custodia Vigilia planet";
        }
        if (player.getLeaderIDs().contains("jolnarcommander") && !player.hasLeaderUnlocked("jolnarcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "jolnar", event);
        }
        if (player.getLeaderIDs().contains("nekrocommander") && !player.hasLeaderUnlocked("nekrocommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "nekro", event);
        }
        if (StringUtils.countMatches(buttonID, "_") < 2) {
            if (activeGame.getComponentAction()) {
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), message);
            } else {
                ButtonHelper.sendMessageToRightStratThread(player, activeGame, message, "technology");
            }
            payForTech(activeGame, player, event, buttonID);
        } else {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), message);
        }
        event.getMessage().delete().queue();
    }

    public static void payForTech(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String trueIdentity = ButtonHelper.getTrueIdentity(player, activeGame);
        String message2 = trueIdentity + " Click the names of the planets you wish to exhaust. ";
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, event);
        if (player.hasTechReady("aida")) {
            Button aiDEVButton = Button.danger("exhaustTech_aida", "Exhaust AIDEV");
            buttons.add(aiDEVButton);
        }
        if (player.hasTechReady("is")) {
            Button aiDEVButton = Button.secondary("exhaustTech_is", "Exhaust Inheritance Systems");
            buttons.add(aiDEVButton);
        }
        if (player.hasRelicReady("prophetstears")) {
            Button pT1 = Button.danger("prophetsTears_AC", "Exhaust Prophets Tears for AC");
            buttons.add(pT1);
            Button pT2 = Button.danger("prophetsTears_TechSkip", "Exhaust Prophets Tears for Tech Skip");
            buttons.add(pT2);
        }
        if (player.hasExternalAccessToLeader("jolnaragent") || player.hasUnexhaustedLeader("jolnaragent")) {
            Button pT2 = Button.secondary("exhaustAgent_jolnaragent", "Exhaust Jol Nar Agent").withEmoji(Emoji.fromFormatted(Emojis.Jolnar));
            buttons.add(pT2);
        }
        Button DoneExhausting = Button.danger("deleteButtons_technology", "Done Exhausting Planets");
        buttons.add(DoneExhausting);
        if (!player.hasAbility("technological_singularity")) {
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        }
    }

    public static void resolveTACheck(Game activeGame, Player player, GenericInteractionCreateEvent event) {
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2.getFaction().equalsIgnoreCase(player.getFaction())) {
                continue;
            }
            if (p2.getPromissoryNotes().containsKey(player.getColor() + "_ta")) {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Button.success("useTA_" + player.getColor(), "Use TA"));
                buttons.add(Button.danger("deleteButtons", "Decline to use TA"));
                String message = Helper.getPlayerRepresentation(p2, activeGame, activeGame.getGuild(), true) + " a player who's TA you hold has refreshed their comms, would you like to play the TA?";
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), message, buttons);
            }
        }
    }

    public static void offerDeckButtons(Game activeGame, ButtonInteractionEvent event){
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.secondary("showDeck_frontier", "Frontier").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("frontier"))));
        buttons.add(Button.primary("showDeck_cultural", "Cultural").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("cultural"))));
        buttons.add(Button.danger("showDeck_hazardous", "Hazardous").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("hazardous"))));
        buttons.add(Button.success("showDeck_industrial", "Industrial").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("industrial"))));
        buttons.add(Button.secondary("showDeck_all", "All Explores"));
        buttons.add(Button.danger("showDeck_ac", "AC Discards").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("actioncard"))));
        buttons.add(Button.danger("showDeck_agenda", "Agenda Discards").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("agenda"))));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Pick deck that you want", buttons);
    }
    public static void resolveDeckChoice(Game activeGame, ButtonInteractionEvent event, String buttonID, Player player){
        String type = buttonID.split("_")[1];
        List<String> types = new ArrayList<>();
        String msg = "You can click this button to get the full text";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("showTextOfDeck_"+type, "Show full text"));
        buttons.add(Button.danger("deleteButtons", "No Thanks"));
        if(type.equalsIgnoreCase("ac")){
            new ShowDiscardActionCards().showDiscard(activeGame, event);
        }else if(type.equalsIgnoreCase("all")){
            types.add(Constants.CULTURAL);
            types.add(Constants.INDUSTRIAL);
            types.add(Constants.HAZARDOUS);
            types.add(Constants.FRONTIER);
            new ExpInfo().secondHalfOfExpInfo(types, event, player, activeGame, false);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
        }else if(type.equalsIgnoreCase("agenda")){
            new ShowDiscardedAgendas().showDiscards(activeGame, event);
        }else{
            types.add(type);
            new ExpInfo().secondHalfOfExpInfo(types, event, player, activeGame, false);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
        }
        event.getMessage().delete().queue();
    }

    public static void resolveShowFullTextDeckChoice(Game activeGame, ButtonInteractionEvent event, String buttonID, Player player){
        String type = buttonID.split("_")[1];
        List<String> types = new ArrayList<>();
        if(type.equalsIgnoreCase("all")){
            types.add(Constants.CULTURAL);
            types.add(Constants.INDUSTRIAL);
            types.add(Constants.HAZARDOUS);
            types.add(Constants.FRONTIER);
            new ExpInfo().secondHalfOfExpInfo(types, event, player, activeGame, false, true);
        }else{
            types.add(type);
            new ExpInfo().secondHalfOfExpInfo(types, event, player, activeGame, false, true);
        }
        event.getMessage().delete().queue();
    }

    public static boolean isPlayerElected(Game activeGame, Player player, String lawID){
        for (String law : activeGame.getLaws().keySet()) {
            if (lawID.equalsIgnoreCase(law)) {
                if (activeGame.getLawsInfo().get(law).equalsIgnoreCase(player.getFaction()) ) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void drawStatusACs(Game activeGame, Player player, ButtonInteractionEvent event) {
        if (activeGame.getACDrawStatusInfo().contains(player.getFaction())) {
            addReaction(event, true, false, "It seems you already drew ACs this status phase. As such, I will not deal you more. Please draw manually if this is a mistake.", "");
            return;
        }
        String message = "";
        int amount = 1;
        activeGame.drawActionCard(player.getUserID());
        if (player.hasTech("nm")) {
            message = " Neural motivator has been accounted for.";
            activeGame.drawActionCard(player.getUserID());
            amount = 2;
        }
        if (player.hasAbility("scheming")) {
            message = message + " Scheming has been accounted for, please use blue button inside your card info thread to discard 1 AC.";
            activeGame.drawActionCard(player.getUserID());
            amount = amount + 1;
        }
        // if (player.getRelics().contains("absol_codex")) {
        //     amount = amount + 1;
        //     activeGame.drawActionCard(player.getUserID());
        //     message = message + " Absol Codex has been accounted for.";
        // }

        StringBuilder messageBuilder = new StringBuilder(message);
        for (String law : activeGame.getLaws().keySet()) {
            if ("minister_policy".equalsIgnoreCase(law)) {
                if (activeGame.getLawsInfo().get(law).equalsIgnoreCase(player.getFaction()) && !player.hasAbility("scheming")) {
                    messageBuilder.append(" Minister of Policy has been accounted for. If this AC is political stability, you cannot play it at this time. ");
                    activeGame.drawActionCard(player.getUserID());
                    amount = amount + 1;
                }
            }
        }
        message = messageBuilder.toString();

        message = "Drew " + amount + " AC." + message;
        ACInfo.sendActionCardInfo(activeGame, player, event);
        if (player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")) {
            commanderUnlockCheck(player, activeGame, "yssaril", event);
        }
        if (player.hasAbility("scheming")) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), ButtonHelper.getTrueIdentity(player, activeGame) + " use buttons to discard",
                ACInfo.getDiscardActionCardButtons(activeGame, player, false));
        }

        addReaction(event, true, false, message, "");
        checkACLimit(activeGame, event, player);
        activeGame.setACDrawStatusInfo(activeGame.getACDrawStatusInfo() + "_" + player.getFaction());
        ButtonHelperActionCards.checkForAssigningPublicDisgrace(activeGame, player);
        ButtonHelperActionCards.checkForPlayingManipulateInvestments(activeGame,player);
        ButtonHelperActionCards.checkForPlayingSummit(activeGame,player);
    }

    public static void resolveMinisterOfCommerceCheck(Game activeGame, Player player, GenericInteractionCreateEvent event) {
        resolveTACheck(activeGame, player, event);
        for (String law : activeGame.getLaws().keySet()) {
            if ("minister_commrece".equalsIgnoreCase(law) || "absol_minscomm".equalsIgnoreCase(law)) {
                if (activeGame.getLawsInfo().get(law).equalsIgnoreCase(player.getFaction())) {
                    MessageChannel channel = event.getMessageChannel();
                    if (activeGame.isFoWMode()) {
                        channel = player.getPrivateChannel();
                    }
                    int numOfNeighbors = player.getNeighbourCount();
                    StringBuilder message = new StringBuilder(Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true));
                    message.append(" Minister of Commerce triggered, your tgs have increased due to your ");
                    message.append(numOfNeighbors).append(" neighbors (").append(player.getTg()).append("->").append(player.getTg() + numOfNeighbors).append(")");
                    player.setTg(numOfNeighbors + player.getTg());
                    ButtonHelperAgents.resolveArtunoCheck(player, activeGame, numOfNeighbors);
                    MessageHelper.sendMessageToChannel(channel, message.toString());
                    ButtonHelperAbilities.pillageCheck(player, activeGame);
                }
            }
        }
    }

    public static int getNumberOfInfantryOnPlanet(String planetName, Game activeGame, Player player) {
        String colorID = Mapper.getColorID(player.getColor());
        UnitHolder unitHolder = getUnitHolderFromPlanetName(planetName, activeGame);
        UnitKey infKey = Mapper.getUnitKey("gf", colorID);
        int numInf = 0;
        if (unitHolder != null && unitHolder.getUnits() != null) {
            if (unitHolder.getUnits().get(infKey) != null) {
                numInf = unitHolder.getUnits().get(infKey);
            }
        }
        return numInf;
    }

    public static int getNumberOfMechsOnPlanet(String planetName, Game activeGame, Player player) {
        String colorID = Mapper.getColorID(player.getColor());
        UnitHolder unitHolder = getUnitHolderFromPlanetName(planetName, activeGame);
        UnitKey mechKey = Mapper.getUnitKey("mf", colorID);
        int numMechs = 0;
        if (unitHolder.getUnits() != null) {
            if (unitHolder.getUnits().get(mechKey) != null) {
                numMechs = unitHolder.getUnits().get(mechKey);
            }
        }
        return numMechs;
    }

    public static int resolveOnActivationEnemyAbilities(Game activeGame, Tile activeSystem, Player player, boolean justChecking) {
        int numberOfAbilities = 0;
        if(activeGame.getL1Hero()){
            return 0;
        }
        String activePlayerident = player.getRepresentation();
        MessageChannel channel = activeGame.getActionsChannel();
        if (justChecking) {
            Player ghostPlayer = activeGame.getPlayerFromColorOrFaction("ghost");
            if (ghostPlayer != null && ghostPlayer != player && getNumberOfUnitsOnTheBoard(activeGame, ghostPlayer, "mech") > 0) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    "This is a reminder that if you are moving via creuss wormhole, you should first pause and check if the creuss player wants to use their mech to move that wormhole. ");
            }
        }
        for (Player nonActivePlayer : activeGame.getPlayers().values()) {

            if (!nonActivePlayer.isRealPlayer() || nonActivePlayer.isPlayerMemberOfAlliance(player) || nonActivePlayer.getFaction().equalsIgnoreCase(player.getFaction())) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                channel = nonActivePlayer.getPrivateChannel();
            }
            String fincheckerForNonActive = "FFCC_" + nonActivePlayer.getFaction() + "_";
            String ident = Helper.getPlayerRepresentation(nonActivePlayer, activeGame, activeGame.getGuild(), true);
            //eres
            if (nonActivePlayer.getTechs().contains("ers") && FoWHelper.playerHasShipsInSystem(nonActivePlayer, activeSystem)) {
                if (justChecking) {
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger eres.");
                    }
                    numberOfAbilities++;
                } else {
                    int cTG = nonActivePlayer.getTg();
                    nonActivePlayer.setTg(cTG + 4);
                    MessageHelper.sendMessageToChannel(channel, ident + " gained 4 tg (" + cTG + "->" + nonActivePlayer.getTg() + ")");
                    ButtonHelperAgents.resolveArtunoCheck(nonActivePlayer, activeGame, 4);
                    ButtonHelperAbilities.pillageCheck(nonActivePlayer, activeGame);
                }
            }
            //neuroglaive
            if (nonActivePlayer.getTechs().contains("ng") && FoWHelper.playerHasShipsInSystem(nonActivePlayer, activeSystem)) {
                if (justChecking) {
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger neuroglaive");
                    }
                    numberOfAbilities++;
                } else {
                    int cTG = player.getFleetCC();
                    player.setFleetCC(cTG - 1);
                    if (activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(channel, ident + " you triggered neuroglaive");
                        channel = player.getPrivateChannel();
                    }
                    MessageHelper.sendMessageToChannel(channel, activePlayerident + " lost 1 fleet cc due to neuroglaive (" + cTG + "->" + player.getFleetCC() + ")");
                }
            }
            if (nonActivePlayer.getTechs().contains("vw") && FoWHelper.playerHasUnitsInSystem(nonActivePlayer, activeSystem)) {
                if (justChecking) {
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger voidwatch");
                    }
                    numberOfAbilities++;
                } else {
                    if (activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(channel, ident + " you triggered voidwatch");
                        channel = player.getPrivateChannel();
                    }
                    List<Button> stuffToTransButtons = ButtonHelper.getForcedPNSendButtons(activeGame, nonActivePlayer, player);
                    String message = ButtonHelper.getTrueIdentity(player, activeGame)
                        + " You have triggered void watch. Please select the PN you would like to send";
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, stuffToTransButtons);
                    MessageHelper.sendMessageToChannel(channel, activePlayerident + " you owe the defender one PN");
                }
            }
            if (activeGame.playerHasLeaderUnlockedOrAlliance(nonActivePlayer, "arboreccommander") && nonActivePlayer.hasProductionUnitInSystem(activeSystem)) {
                if (justChecking) {
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger the arborec commander");
                    }
                    numberOfAbilities++;
                } else {
                    Button gainTG = Button.success(fincheckerForNonActive + "freelancersBuild_" + activeSystem.getPosition(), "Build 1 Unit");
                    Button Decline2 = Button.danger(fincheckerForNonActive + "deleteButtons", "Decline Commander");
                    List<Button> buttons = List.of(gainTG, Decline2);
                    MessageHelper.sendMessageToChannelWithButtons(channel, ident + " use buttons to resolve Arborec commander ", buttons);
                }
            }
            if (nonActivePlayer.hasUnit("mahact_mech") && nonActivePlayer.hasMechInSystem(activeSystem) && nonActivePlayer.getMahactCC().contains(player.getColor()) && !activeGame.getLaws().containsKey("articles_war")) {
                if (justChecking) {
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger an opportunity for a mahact mech trigger");
                    }
                    numberOfAbilities++;
                } else {
                    Button gainTG = Button.success(fincheckerForNonActive + "mahactMechHit_" + activeSystem.getPosition() + "_" + player.getColor(),
                        "Return " + player.getColor() + " CC and end their turn");
                    Button Decline2 = Button.danger(fincheckerForNonActive + "deleteButtons", "Decline To Use Mech");
                    List<Button> buttons = List.of(gainTG, Decline2);
                    MessageHelper.sendMessageToChannelWithButtons(channel, ident + " use buttons to resolve Mahact mech ability ", buttons);
                }
            }
            if (activeGame.playerHasLeaderUnlockedOrAlliance(nonActivePlayer, "yssarilcommander") && FoWHelper.playerHasUnitsInSystem(nonActivePlayer, activeSystem)) {
                if (justChecking) {
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger yssaril commander");
                    }
                    numberOfAbilities++;
                } else {
                    Button lookAtACs = Button.success(fincheckerForNonActive + "yssarilcommander_ac_" + player.getFaction(), "Look at ACs ("+player.getAc()+")");
                    Button lookAtPNs = Button.success(fincheckerForNonActive + "yssarilcommander_pn_" + player.getFaction(), "Look at PNs ("+player.getPnCount()+")");
                    Button lookAtSOs = Button.success(fincheckerForNonActive + "yssarilcommander_so_" + player.getFaction(), "Look at SOs ("+(player.getSo())+")");
                    Button Decline2 = Button.danger(fincheckerForNonActive + "deleteButtons", "Decline Commander");
                    List<Button> buttons = List.of(lookAtACs, lookAtPNs, lookAtSOs, Decline2);
                    MessageHelper.sendMessageToChannelWithButtons(channel, ident + " use buttons to resolve Yssaril commander ", buttons);
                }
            }
            List<String> pns = new ArrayList<>(player.getPromissoryNotesInPlayArea());
            for (String pn : pns) {
                Player pnOwner = activeGame.getPNOwner(pn);
                if (pnOwner == null || !pnOwner.isRealPlayer() || !pnOwner.getFaction().equalsIgnoreCase(nonActivePlayer.getFaction())) {
                    continue;
                }
                PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(pn);
                if (pnModel.getText().contains("return this card") && pnModel.getText().contains("you activate a system that contains") && FoWHelper.playerHasUnitsInSystem(pnOwner, activeSystem)) {
                    if (justChecking) {
                        if (!activeGame.isFoWMode()) {
                            MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger the return of a PN (" + pnModel.getName() + ")");
                        }
                        numberOfAbilities++;
                    } else {
                        player.removePromissoryNote(pn);
                        nonActivePlayer.setPromissoryNote(pn);
                        PNInfo.sendPromissoryNoteInfo(activeGame, nonActivePlayer, false);
                        PNInfo.sendPromissoryNoteInfo(activeGame, player, false);
                        MessageHelper.sendMessageToChannel(channel, pnModel.getName() + " was returned");
                    }

                }
            }
        }
        return numberOfAbilities;
    }

    public static boolean checkForTechSkipAttachments(Game activeGame, String planetName) {
        boolean techPresent = false;
        if ("custodiavigilia".equalsIgnoreCase(planetName)) {
            return false;
        }
        Tile tile = activeGame.getTile(AliasHandler.resolveTile(planetName));
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        Set<String> tokenList = unitHolder.getTokenList();
        if (CollectionUtils.containsAny(tokenList, "attachment_warfare.png", "attachment_cybernetic.png", "attachment_biotic.png", "attachment_propulsion.png")) {
            techPresent = true;
        }
        return techPresent;
    }

    public static String getTechSkipAttachments(Game activeGame, String planetName) {
        Tile tile = activeGame.getTile(AliasHandler.resolveTile(planetName));
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        Set<String> tokenList = unitHolder.getTokenList();
        if (CollectionUtils.containsAny(tokenList, "attachment_warfare.png", "attachment_cybernetic.png", "attachment_biotic.png", "attachment_propulsion.png")) {
            String type = "warfare";
            if(tokenList.contains("attachment_"+type+".png")){
                return type;
            }
            type = "cybernetic";
            if(tokenList.contains("attachment_"+type+".png")){
                return type;
            }
            type = "propulsion";
            if(tokenList.contains("attachment_"+type+".png")){
                return type;
            }
            type = "biotic";
            if(tokenList.contains("attachment_"+type+".png")){
                return type;
            }
        }
        return "none";
    }

    public static List<Button> getXxchaAgentReadyButtons(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getExhaustedPlanets()) {
            buttons.add(Button.success("refresh_" + planet + "_" + player.getFaction(), "Ready " + Helper.getPlanetRepresentation(planet, activeGame)));
        }
        buttons.add(Button.danger("deleteButtons_spitItOut", "Delete These Buttons"));
        return buttons;
    }

    public static void sendAllTechsNTechSkipPlanetsToReady(Game activeGame, GenericInteractionCreateEvent event, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String tech : player.getExhaustedTechs()) {
            buttons.add(Button.success("biostimsReady_tech_" + tech, "Ready " + Mapper.getTechs().get(tech).getName()));
        }
        for (String planet : player.getExhaustedPlanets()) {
            if ((Mapper.getPlanet(planet).getTechSpecialties() != null && Mapper.getPlanet(planet).getTechSpecialties().size() > 0) || checkForTechSkipAttachments(activeGame, planet)) {
                buttons.add(Button.success("biostimsReady_planet_" + planet, "Ready " + Helper.getPlanetRepresentation(planet, activeGame)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to select a planet or tech to ready", buttons);
    }

    public static List<Button> getPsychoTechPlanets(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getReadiedPlanets()) {
            if ((Mapper.getPlanet(planet).getTechSpecialties() != null && Mapper.getPlanet(planet).getTechSpecialties().size() > 0) || checkForTechSkipAttachments(activeGame, planet)) {
                buttons.add(Button.success("psychoExhaust_" + planet, "Exhaust " + Helper.getPlanetRepresentation(planet, activeGame)));
            }
        }
        buttons.add(Button.danger("deleteButtons", "Delete Buttons"));
        return buttons;
    }

    public static void resolvePsychoExhaust(Game activeGame, ButtonInteractionEvent event, Player player, String buttonID) {
        int oldTg = player.getTg();
        player.setTg(oldTg + 1);
        String planet = buttonID.split("_")[1];
        player.exhaustPlanet(planet);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            getIdent(player) + " exhausted " + Helper.getPlanetRepresentation(planet, activeGame) + " and gained 1tg (" + oldTg + "->" + player.getTg() + ")");
        ButtonHelperAbilities.pillageCheck(player, activeGame);
        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void bioStimsReady(Game activeGame, GenericInteractionCreateEvent event, Player player, String buttonID) {
        buttonID = buttonID.replace("biostimsReady_", "");
        String last = buttonID.substring(buttonID.lastIndexOf("_") + 1);
        if (buttonID.contains("tech_")) {
            player.refreshTech(last);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " readied tech: " + Helper.getTechRepresentation(last));
        } else {
            player.refreshPlanet(last);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " readied planet: " + Helper.getPlanetRepresentation(last, activeGame));
        }
    }

    public static void checkACLimit(Game activeGame, GenericInteractionCreateEvent event, Player player) {
        if (player.hasAbility("crafty")) {
            return;
        }
        int limit = 7;
        if (activeGame.getLaws().containsKey("sanctions") && !activeGame.isAbsolMode()) {
            limit = 3;
        }
        if (activeGame.getLaws().containsKey("absol_sanctions")) {
            limit = 3;
            if (activeGame.getLawsInfo().get("absol_sanctions").equalsIgnoreCase(player.getFaction())) {
                limit = 5;
            }
        }
        if (player.getRelics().contains("absol_codex")) {
            limit = limit + 5;
        }
        if (player.getRelics().contains("e6-g0_network")) {
            limit = limit + 2;
        }
        if (player.getAc() > limit) {
            MessageChannel channel = activeGame.getMainGameChannel();
            if (activeGame.isFoWMode()) {
                channel = player.getPrivateChannel();
            }
            String ident = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true);
            MessageHelper.sendMessageToChannel(channel,
                ident + " you are exceeding the AC hand limit of " + limit + ". Please discard down to the limit. Check your cards info thread for the blue discard buttons. ");
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), ident + " use buttons to discard", ACInfo.getDiscardActionCardButtons(activeGame, player, false));
        }
    }

    public static void updateMap(Game activeGame, GenericInteractionCreateEvent event) {
        String threadName = activeGame.getName() + "-bot-map-updates";
        List<ThreadChannel> threadChannels = activeGame.getActionsChannel().getThreadChannels();
        boolean foundsomething = false;
        FileUpload file = GenerateMap.getInstance().saveImage(activeGame, DisplayType.all, event);
        if (!activeGame.isFoWMode()) {
            for (ThreadChannel threadChannel_ : threadChannels) {
                if (threadChannel_.getName().equals(threadName)) {
                    foundsomething = true;

                    List<Button> buttonsWeb = new ArrayList<>();
                    if (!activeGame.isFoWMode()) {
                        Button linkToWebsite = Button.link("https://ti4.westaddisonheavyindustries.com/game/" + activeGame.getName(), "Website View");
                        buttonsWeb.add(linkToWebsite);
                    }
                    buttonsWeb.add(Button.success("cardsInfo", "Cards Info"));
                    buttonsWeb.add(Button.primary("offerDeckButtons", "Show Decks"));
                    buttonsWeb.add(Button.secondary("showGameAgain", "Show Game"));
                    
                    MessageHelper.sendFileToChannelWithButtonsAfter(threadChannel_, file, "", buttonsWeb);

                }
            }
        } else {
            MessageHelper.sendFileUploadToChannel(event.getMessageChannel(), file);
            foundsomething = true;
        }
        if (!foundsomething) {

            List<Button> buttonsWeb = new ArrayList<>();
            if (!activeGame.isFoWMode()) {
                Button linkToWebsite = Button.link("https://ti4.westaddisonheavyindustries.com/game/" + activeGame.getName(), "Website View");
                buttonsWeb.add(linkToWebsite);
            }
            buttonsWeb.add(Button.success("cardsInfo", "Cards Info"));
            buttonsWeb.add(Button.primary("offerDeckButtons", "Show Decks"));
            buttonsWeb.add(Button.secondary("showGameAgain", "Show Game"));
            
            MessageHelper.sendFileToChannelWithButtonsAfter(event.getMessageChannel(), file, "", buttonsWeb);

        }

    }

    public static boolean nomadHeroAndDomOrbCheck(Player player, Game activeGame, Tile tile) {
        if (activeGame.getDominusOrbStatus() || activeGame.getL1Hero()) {
            return true;
        }
        return player.getLeader("nomadhero").map(Leader::isActive).orElse(false);
    }

    public static int getAllTilesWithAlphaNBetaNUnits(Player player, Game activeGame) {
        activeGame.getTileMap().values().stream()
            .filter(t -> t.containsPlayersUnits(player));
        int count = 0;
        for (Tile tile : activeGame.getTileMap().values()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tile) && FoWHelper.doesTileHaveAlphaOrBeta(activeGame, tile.getPosition(), player)) {
                count = count + 1;
            }
        }
        return count;
    }

    public static void commanderUnlockCheck(Player player, Game activeGame, String faction, GenericInteractionCreateEvent event) {
        boolean shouldBeUnlocked = false;
        switch (faction) {
            case "yssaril" -> {
                if (player.getActionCards().size() > 7 || (player.getExhaustedTechs().contains("mi") && player.getActionCards().size() > 6)) {
                    shouldBeUnlocked = true;
                }
            }
            case "edyn" -> {
                if (activeGame.getLaws().size() > 0) {
                    shouldBeUnlocked = true;
                }
            }
            
            case "zealots" -> shouldBeUnlocked = true;
            case "yin" -> shouldBeUnlocked = true;
            case "florzen" -> shouldBeUnlocked = true;
            case "letnev" -> shouldBeUnlocked = true;
            case "hacan" -> {
                if (player.getTg() > 9) {
                    shouldBeUnlocked = true;
                }
            }
            case "mykomentori" -> {
                if (player.getCommodities() > 3) {
                    shouldBeUnlocked = true;
                }
            }
            case "sardakk" -> {
                if (player.getPlanets().size() > 6) {
                    shouldBeUnlocked = true;
                }
            }
            case "ghost" -> {
                if (getAllTilesWithAlphaNBetaNUnits(player, activeGame) > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "sol" -> {
                int resources = 0;
                for (String planet : player.getPlanets()) {
                    resources = resources + Helper.getPlanetResources(planet, activeGame);
                }
                if (resources > 11) {
                    shouldBeUnlocked = true;
                }
            }
            case "xxcha" -> {
                int resources = 0;
                for (String planet : player.getPlanets()) {
                    resources = resources + Helper.getPlanetInfluence(planet, activeGame);
                }
                if (resources > 11) {
                    shouldBeUnlocked = true;
                }
            }
            case "mentak" -> {
                if (getNumberOfUnitsOnTheBoard(activeGame, player, "cruiser") > 3) {
                    shouldBeUnlocked = true;
                }
            }
            case "l1z1x" -> {
                if (getNumberOfUnitsOnTheBoard(activeGame, player, "dreadnought") > 3) {
                    shouldBeUnlocked = true;
                }
            }
            case "argent" -> {
                int num = getNumberOfUnitsOnTheBoard(activeGame, player, "pds") + getNumberOfUnitsOnTheBoard(activeGame, player, "dreadnought")
                    + getNumberOfUnitsOnTheBoard(activeGame, player, "destroyer");
                if (num > 5) {
                    shouldBeUnlocked = true;
                }
            }
            case "titans" -> {
                int num = getNumberOfUnitsOnTheBoard(activeGame, player, "pds") + getNumberOfUnitsOnTheBoard(activeGame, player, "spacedock");
                if (num > 4) {
                    shouldBeUnlocked = true;
                }
            }
            case "cabal" -> {
                int num = getNumberOfGravRiftsPlayerIsIn(player, activeGame);
                if (num > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "nekro" -> {
                if (player.getTechs().size() > 4) {
                    shouldBeUnlocked = true;
                }
            }
            case "jolnar" -> {
                if (player.getTechs().size() > 7) {
                    shouldBeUnlocked = true;
                }
            }
            case "saar" -> {
                if (getNumberOfUnitsOnTheBoard(activeGame, player, "spacedock") > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "naaz" -> {
                if (getTilesOfPlayersSpecificUnits(activeGame, player, UnitType.Mech).size() > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "nomad" -> {
                if (player.getSoScored() > 0) {
                    shouldBeUnlocked = true;
                }
            }
            case "mahact" -> {
                if (player.getMahactCC().size() > 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "empyrean" -> {
                if (player.getNeighbourCount() > (activeGame.getRealPlayers().size() - 2)) {
                    shouldBeUnlocked = true;
                }
            }
            case "muaat" -> shouldBeUnlocked = true;
            case "winnu" -> shouldBeUnlocked = true;
            case "naalu" -> {
                Tile rex = activeGame.getTileFromPlanet("mr");
                for (String tilePos : FoWHelper.getAdjacentTiles(activeGame, rex.getPosition(), player, false)) {
                    Tile tile = activeGame.getTileByPosition(tilePos);
                    for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                        if (unitHolder.getUnitCount(UnitType.Mech, player.getColor()) > 0 || unitHolder.getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
                            shouldBeUnlocked = true;
                        }
                    }
                }
            }
            case "keleres" -> shouldBeUnlocked = true;
            case "arborec" -> {
                int num = getAmountOfSpecificUnitsOnPlanets(player, activeGame, "infantry") + getAmountOfSpecificUnitsOnPlanets(player, activeGame, "mech");
                if (num > 11) {
                    shouldBeUnlocked = true;
                }
            }
            // missing: yin, ghost, cabal, naalu,letnev
        }
        if (shouldBeUnlocked) {
            UnlockLeader.unlockLeader(event, faction + "commander", activeGame, player);
        }
    }

    public static int getAmountOfSpecificUnitsOnPlanets(Player player, Game activeGame, String unit) {
        int num = 0;
        for (Tile tile : activeGame.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder instanceof Planet planet) {
                    UnitKey unitID = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor());
                    if (planet.getUnits().containsKey(unitID)) {
                        num = num + planet.getUnits().get(unitID);
                    }
                }
            }
        }
        return num;
    }

    public static List<String> getPlanetsWithSpecificUnit(Player player, Game activeGame, Tile tile, String unit) {
        List<String> planetsWithUnit = new ArrayList<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder instanceof Planet planet) {
                if (planet.getUnits().containsKey(Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor()))) {
                    planetsWithUnit.add(planet.getName());
                }
            }
        }
        return planetsWithUnit;
    }

    public static void doButtonsForSleepers(Player player, Game activeGame, Tile tile, ButtonInteractionEvent event) {
        String finChecker = "FFCC_" + player.getFaction() + "_";

        for (String planet : tile.getPlanetsWithSleeperTokens()) {
            List<Button> planetsWithSleepers = new ArrayList<>();
            planetsWithSleepers.add(Button.success(finChecker + "replaceSleeperWith_pds_" + planet, "Replace sleeper on " + planet + " with a pds."));
            if (getNumberOfUnitsOnTheBoard(activeGame, player, "mech") < 4) {
                planetsWithSleepers.add(Button.success(finChecker + "replaceSleeperWith_mech_" + planet, "Replace sleeper on " + planet + " with a mech and an infantry."));
            }
            planetsWithSleepers.add(Button.danger("deleteButtons", "Delete these buttons"));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to resolve sleeper", planetsWithSleepers);
        }

    }

    public static List<Button> getButtonsForTurningPDSIntoFS(Player player, Game activeGame, Tile tile) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> planetsWithPDS = new ArrayList<>();
        for (String planet : getPlanetsWithSpecificUnit(player, activeGame, tile, "pds")) {
            planetsWithPDS.add(Button.success(finChecker + "replacePDSWithFS_" + planet, "Replace pds on " + planet + " with your flagship."));
        }
        planetsWithPDS.add(Button.danger("deleteButtons", "Delete these buttons"));
        return planetsWithPDS;
    }

    public static List<Button> getButtonsForRemovingASleeper(Player player, Game activeGame) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> planetsWithSleepers = new ArrayList<>();
        for (String planet : activeGame.getAllPlanetsWithSleeperTokens()) {
            planetsWithSleepers.add(Button.success(finChecker + "removeSleeperFromPlanet_" + planet, "Remove the sleeper on " + planet + "."));
        }
        planetsWithSleepers.add(Button.danger("deleteButtons", "Delete these buttons"));
        return planetsWithSleepers;
    }

    public static void resolveTitanShenanigansOnActivation(Player player, Game activeGame, Tile tile, ButtonInteractionEvent event) {
        List<Button> buttons = getButtonsForTurningPDSIntoFS(player, activeGame, tile);
        if (buttons.size() > 1) {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to decide which pds to replace with your flagship", buttons);
        }
        doButtonsForSleepers(player, activeGame, tile, event);
    }

    public static List<Player> getOtherPlayersWithShipsInTheSystem(Player player, Game activeGame, Tile tile) {
        List<Player> playersWithShips = new ArrayList<>();
        for (Player p2 : activeGame.getPlayers().values()) {
            if (p2 == player || !p2.isRealPlayer()) {
                continue;
            }
            if (FoWHelper.playerHasShipsInSystem(p2, tile)) {
                playersWithShips.add(p2);
            }
        }
        return playersWithShips;
    }

    public static List<Player> getPlayersWithUnitsOnAPlanet(Game activeGame, Tile tile, String planet) {
        List<Player> playersWithShips = new ArrayList<>();
        for (Player p2 : activeGame.getPlayers().values()) {
            if (FoWHelper.playerHasUnitsOnPlanet(p2, tile, planet)) {
                playersWithShips.add(p2);
            }
        }
        return playersWithShips;
    }

    public static List<Tile> getTilesWithYourCC(Player player, Game activeGame, GenericInteractionCreateEvent event) {
        List<Tile> tilesWithCC = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(activeGame.getTileMap()).entrySet()) {
            if (AddCC.hasCC(event, player.getColor(), tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                tilesWithCC.add(tile);
            }
        }
        return tilesWithCC;
    }

    public static void resolveRemovingYourCC(Player player, Game activeGame, GenericInteractionCreateEvent event, String buttonID) {
        buttonID = buttonID.replace("removeCCFromBoard_", "");
        String whatIsItFor = buttonID.split("_")[0];
        String pos = buttonID.split("_")[1];
        Tile tile = activeGame.getTileByPosition(pos);
        String tileRep = tile.getRepresentationForButtons(activeGame, player);
        String ident = ButtonHelper.getIdentOrColor(player, activeGame);
        String msg = ident + " removed CC from " + tileRep;
        if (whatIsItFor.contains("mahactAgent")) {
            String faction = whatIsItFor.replace("mahactAgent", "");
            if (player != null) {
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
            }
            player = activeGame.getPlayerFromColorOrFaction(faction);
            msg = getTrueIdentity(player, activeGame) + " " + msg + " using Mahact agent";
        }

        if (player == null) return;
        RemoveCC.removeCC(event, player.getColor(), tile, activeGame);

        String finChecker = "FFCC_" + player.getFaction() + "_";
        if ("mahactCommander".equalsIgnoreCase(whatIsItFor)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), ident + "reduced their tactic CCs from " + player.getTacticalCC() + " to " + (player.getTacticalCC() - 1));
            player.setTacticalCC(player.getTacticalCC() - 1);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            List<Button> conclusionButtons = new ArrayList<>();
            Button endTurn = Button.danger(finChecker + "turnEnd", "End Turn");
            conclusionButtons.add(endTurn);
            if (getEndOfTurnAbilities(player, activeGame).size() > 1) {
                conclusionButtons.add(Button.primary("endOfTurnAbilities", "Do End Of Turn Ability (" + (getEndOfTurnAbilities(player, activeGame).size() - 1) + ")"));
            }
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use the buttons to end turn.", conclusionButtons);
        } else {
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), msg);
        }
        if ("warfare".equalsIgnoreCase(whatIsItFor)) {
            List<Button> redistributeButton = new ArrayList<>();
            Button redistribute = Button.success("FFCC_" + player.getFaction() + "_" + "redistributeCCButtons", "Redistribute & Gain CCs");
            Button deleButton = Button.danger("FFCC_" + player.getFaction() + "_" + "deleteButtons", "Delete These Buttons");
            redistributeButton.add(redistribute);
            redistributeButton.add(deleButton);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), false) + " click this after picking up a CC.", redistributeButton);
        }

    }

    public static void resolveMahactMechAbilityUse(Player mahact, Player target, Game activeGame, Tile tile, ButtonInteractionEvent event) {
        mahact.removeMahactCC(target.getColor());
        target.setTacticalCC(target.getTacticalCC() - 1);
        AddCC.addCC(event, target.getColor(), tile);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(mahact, activeGame),
            ButtonHelper.getTrueIdentity(mahact, activeGame) + " the " + target.getColor() + " cc has been removed from your fleet pool");
        List<Button> conclusionButtons = new ArrayList<>();
        Button endTurn = Button.danger("turnEnd", "End Turn");
        conclusionButtons.add(endTurn);
        if (getEndOfTurnAbilities(target, activeGame).size() > 1) {
            conclusionButtons.add(Button.primary("endOfTurnAbilities", "Do End Of Turn Ability (" + (getEndOfTurnAbilities(target, activeGame).size() - 1) + ")"));
        }
        Button getTactic = Button.success("increase_tactic_cc", "Gain 1 Tactic CC");
        Button getFleet = Button.success("increase_fleet_cc", "Gain 1 Fleet CC");
        Button getStrat = Button.success("increase_strategy_cc", "Gain 1 Strategy CC");
        Button DoneGainingCC = Button.danger("deleteButtons", "Done Gaining CCs");
        List<Button> buttons = List.of(getTactic, getFleet, getStrat, DoneGainingCC);
        String trueIdentity = Helper.getPlayerRepresentation(target, activeGame, activeGame.getGuild(), true);
        String message2 = trueIdentity + "! Your current CCs are " + target.getCCRepresentation() + ". Use buttons to gain CCs";
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(target, activeGame), message2, buttons);
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(target, activeGame), ButtonHelper.getTrueIdentity(target, activeGame)
            + " You've been hit with the Mahact mech ability. A cc has been placed from your tactics in the system and your turn has been ended. Use the buttons to resolve end of turn abilities and then end turn.",
            conclusionButtons);
        event.getMessage().delete().queue();

    }

    public static int checkNetGain(Player player, String ccs) {
        int netgain;
        int oldTactic = Integer.parseInt(ccs.substring(0, ccs.indexOf("/")));
        ccs = ccs.substring(ccs.indexOf("/") + 1);
        int oldFleet = Integer.parseInt(ccs.substring(0, ccs.indexOf("/")));
        ccs = ccs.substring(ccs.indexOf("/") + 1);
        int oldStrat = Integer.parseInt(ccs);

        netgain = (player.getTacticalCC() - oldTactic) + (player.getFleetCC() - oldFleet) + (player.getStrategicCC() - oldStrat);
        return netgain;
    }

    public static List<Button> getButtonsToRemoveYourCC(Player player, Game activeGame, GenericInteractionCreateEvent event, String whatIsItFor) {
        List<Button> buttonsToRemoveCC = new ArrayList<>();
        String finChecker = "FFCC_" + player.getFaction() + "_";
        for (Tile tile : getTilesWithYourCC(player, activeGame, event)) {
            buttonsToRemoveCC.add(Button.success(finChecker + "removeCCFromBoard_" + whatIsItFor + "_" + tile.getPosition(), "Remove CC from " + tile.getRepresentationForButtons(activeGame, player)));
        }
        return buttonsToRemoveCC;
    }

    public static List<Button> getButtonsToSwitchWithAllianceMembers(Player player, Game activeGame, boolean fromButton) {
        List<Button> buttonsToRemoveCC = new ArrayList<>();
        for (Player player2 : activeGame.getRealPlayers()) {
            if (player.getAllianceMembers().contains(player2.getFaction())) {
                buttonsToRemoveCC.add(
                    Button.success("swapToFaction_" + player2.getFaction(), "Swap to " + player2.getFaction()).withEmoji(Emoji.fromFormatted(player2.getFactionEmoji())));
            }
        }
        if (fromButton) {
            buttonsToRemoveCC.add(Button.danger("deleteButtons", "Delete These Buttons"));
        }

        return buttonsToRemoveCC;
    }

    public static List<Button> getButtonsToExploreAllPlanets(Player player, Game activeGame) {
        List<Button> buttons = new ArrayList<>();
        for (String plan : player.getPlanetsAllianceMode()) {
            UnitHolder planetUnit = activeGame.getPlanetsInfo().get(plan);
            Planet planetReal = (Planet) planetUnit;
            if (planetReal != null && planetReal.getOriginalPlanetType() != null) {
                List<Button> planetButtons = getPlanetExplorationButtons(activeGame, planetReal, player);
                buttons.addAll(planetButtons);
            }
        }
        return buttons;
    }

    public static List<Button> getButtonsForAgentSelection(Game activeGame, String agent) {
        return AgendaHelper.getPlayerOutcomeButtons(activeGame, null, "exhaustAgent_" + agent, null);
    }

    public static String combatThreadName(Game activeGame, Player p1, Player p2, Tile tile) {
        String thread = activeGame.getName() + "-round-" + activeGame.getRound() + "-system-" + tile.getPosition() + "-";
        if (activeGame.isFoWMode()) {
            thread += p1.getColor() + "-vs-" + p2.getColor() + "-private";
        } else {
            thread += p1.getFaction() + "-vs-" + p2.getFaction();
        }
        return thread;
    }

    public static void makeACombatThread(Game activeGame, MessageChannel channel, Player p1, Player p2, String threadName, Tile tile, GenericInteractionCreateEvent event, String spaceOrGround) {
        TextChannel textChannel = (TextChannel) channel;
        Helper.checkThreadLimitAndArchive(event.getGuild());
        MessageCreateBuilder baseMessageObject = new MessageCreateBuilder().addContent("Resolve combat");
        channel.sendMessage(baseMessageObject.build()).queue(message_ -> {
            boolean foundThread = false;
            for (ThreadChannel threadChannel_ : textChannel.getThreadChannels()) {
                if (threadChannel_.getName().equals(threadName)) {
                    foundThread = true;
                    initializeCombatThread(threadChannel_, activeGame, p1, p2, tile, event, spaceOrGround);
                    break;
                }
            }

            if (!foundThread) {
                ThreadChannelAction threadChannel = textChannel.createThreadChannel(threadName, message_.getId());
                if (activeGame.isFoWMode()) {
                    threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_3_DAYS);
                } else {
                    threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR);
                }

                threadChannel.queue(tc -> {
                    initializeCombatThread(tc, activeGame, p1, p2, tile, event, spaceOrGround);
                });
            }
        });
    }

    private static void initializeCombatThread(ThreadChannel tc, Game activeGame, Player p1, Player p2, Tile tile, GenericInteractionCreateEvent event, String spaceOrGround) {
        StringBuilder message = new StringBuilder();
        if (activeGame.isFoWMode()) {
            message.append(Helper.getPlayerRepresentation(p1, activeGame, activeGame.getGuild(), true));
        } else {
            message.append(Helper.getPlayerRepresentation(p1, activeGame, activeGame.getGuild(), true));
            message.append(Helper.getPlayerRepresentation(p2, activeGame, activeGame.getGuild(), false));
        }

        message.append(" Please resolve the interaction here. ");
        if ("ground".equalsIgnoreCase(spaceOrGround)) {
            message.append("Steps for Invasion:").append("\n");
            message.append("> 1. Start of invasion abilities (Tekklar, Blitz, Bunker, etc.)").append("\n");
            message.append("> 2. Bombardment").append("\n");
            message.append("> 3. Commit Ground Forces").append("\n");
            message.append("> 4. After commit window (Parley, Ghost Squad, etc.)").append("\n");
            message.append("> 5. Start of Combat (morale boost, etc.)").append("\n");
            message.append("> 6. Roll Dice!").append("\n");
        } else {
            message.append("Steps for Space Combat:").append("\n");
            message.append("> 1. End of movement abilities (Foresight, Stymie, etc.)").append("\n");
            message.append("> 2. Firing of PDS").append("\n");
            message.append("> 3. Start of Combat (Skilled retreat, Morale boost, etc.)").append("\n");
            message.append("> 4. Anti-Fighter Barrage").append("\n");
            message.append("> 5. Declare Retreats (including rout)").append("\n");
            message.append("> 6. Roll Dice!").append("\n");
        }

        MessageHelper.sendMessageToChannel(tc, message.toString());
        List<Player> playersWithPds2;
        if (activeGame.isFoWMode() || "ground".equalsIgnoreCase(spaceOrGround)) {
            playersWithPds2 = new ArrayList<>();
        } else {
            playersWithPds2 = tileHasPDS2Cover(p1, activeGame, tile.getPosition());
        }
        int context = 0;
        if (playersWithPds2.size() > 0) {
            context = 1;
        }
        FileUpload systemWithContext = GenerateTile.getInstance().saveImage(activeGame, context, tile.getPosition(), event, p1);
        MessageHelper.sendMessageWithFile(tc, systemWithContext, "Picture of system", false);
        List<Button> buttons = getButtonsForPictureCombats(activeGame, tile.getPosition(), p1, p2, spaceOrGround);
        MessageHelper.sendMessageToChannelWithButtons(tc, "Combat", buttons);
        if (playersWithPds2.size() > 0 && !activeGame.isFoWMode() && "space".equalsIgnoreCase(spaceOrGround)) {
            StringBuilder pdsMessage = new StringBuilder("The following players have pds2 cover in the region, and can use the button to fire it:");
            for (Player playerWithPds : playersWithPds2) {
                pdsMessage.append(" ").append(Helper.getPlayerRepresentation(playerWithPds, activeGame, activeGame.getGuild(), false));
            }
            MessageHelper.sendMessageToChannel(tc, pdsMessage.toString());
        } else {
            if (activeGame.isFoWMode()) {
                MessageHelper.sendMessageToChannel(tc, "In fog, it is the players responsibility to check for pds2");
            }
        }
        if ("space".equalsIgnoreCase(spaceOrGround)) {
            List<Button> buttons2 = new ArrayList<Button>();
            buttons2.add(Button.secondary("combatRoll_" + tile.getPosition() + "_space_afb", "Roll " + CombatRollType.AFB.getValue()));
            buttons2.add(Button.secondary("combatRoll_" + tile.getPosition() + "_space_spacecannonoffence", "Roll Space Cannon Offence"));
            if(!activeGame.isFoWMode()){
                buttons2.add(Button.danger("declinePDS", "Decline PDS"));
            }
            MessageHelper.sendMessageToChannelWithButtons(tc, "You can use these buttons to roll AFB or Space Cannon Offence", buttons2);
        }
    }

    public static void deleteTheOneButton(ButtonInteractionEvent event) {
        String exhaustedMessage = event.getMessage().getContentRaw();
        if ("".equalsIgnoreCase(exhaustedMessage)) {
            exhaustedMessage = "Updated";
        }
        List<ActionRow> actionRow2 = new ArrayList<>();
        for (ActionRow row : event.getMessage().getActionRows()) {
            List<ItemComponent> buttonRow = row.getComponents();
            int buttonIndex = buttonRow.indexOf(event.getButton());
            if (buttonIndex > -1) {
                buttonRow.remove(buttonIndex);
            }
            if (buttonRow.size() > 0) {
                actionRow2.add(ActionRow.of(buttonRow));
            }
        }
        if (actionRow2.size() > 0) {
            event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
        } else {
            event.getMessage().delete().queue();
        }
    }

    public static void saveButtons(ButtonInteractionEvent event, Game activeGame, Player player) {
        activeGame.setSavedButtons(new ArrayList<String>());
        String exhaustedMessage = event.getMessage().getContentRaw();
        if ("".equalsIgnoreCase(exhaustedMessage)) {
            exhaustedMessage = "Updated";
        }
        activeGame.setSavedChannelID(event.getMessageChannel().getId());
        activeGame.setSavedMessage(exhaustedMessage);
        List<Button> buttons = new ArrayList<>();
        for (ActionRow row : event.getMessage().getActionRows()) {
            List<ItemComponent> buttonRow = row.getComponents();
            for (ItemComponent but : buttonRow) {
                Button button = (Button) but;
                if (button != null) {
                    buttons.add(button);
                }
            }
        }

        for (Button button : buttons) {
            if (button.getId() == null || button.getId().equalsIgnoreCase("ultimateUndo")) {
                continue;
            }
            String builder = player.getFaction() + ";" + button.getId() + ";" + button.getLabel() + ";" + button.getStyle().toString();
            if (button.getEmoji() != null && !button.getEmoji().toString().equalsIgnoreCase("")) {
                builder = builder + ";" + button.getEmoji().toString();
            }
            activeGame.saveButton(builder);
        }
    }

    public static List<Button> getSavedButtons(Game activeGame) {
        List<Button> buttons = new ArrayList<>();
        for (String buttonString : activeGame.getSavedButtons()) {
            int x = 0;
            if (activeGame.getPlayerFromColorOrFaction(buttonString.split(";")[x]) != null) {
                x = 1;
            }
            String id = buttonString.split(";")[x];
            String label = buttonString.split(";")[x + 1];
            if (label.length() < 1) {
                label = "Edited";
            }
            String style = buttonString.split(";")[x + 2].toLowerCase();
            String emoji = "";
            if (StringUtils.countMatches(buttonString, ";") > x + 2) {
                emoji = buttonString.split(";")[x + 3];
                String name = StringUtils.substringBetween(emoji, ":", "(");
                String emojiID = StringUtils.substringBetween(emoji, "=", ")");
                emoji = "<:" + name + ":" + emojiID + ">";
            }
            if (style.equalsIgnoreCase("success")) {
                if (emoji.length() > 0) {
                    buttons.add(Button.success(id, label).withEmoji(Emoji.fromFormatted(emoji)));
                } else {
                    buttons.add(Button.success(id, label));
                }
            } else if (style.equalsIgnoreCase("danger")) {
                if (emoji.length() > 0) {
                    buttons.add(Button.danger(id, label).withEmoji(Emoji.fromFormatted(emoji)));
                } else {
                    buttons.add(Button.danger(id, label));
                }
            } else if (style.equalsIgnoreCase("secondary")) {
                if (emoji.length() > 0) {
                    buttons.add(Button.secondary(id, label).withEmoji(Emoji.fromFormatted(emoji)));
                } else {
                    buttons.add(Button.secondary(id, label));
                }
            } else {
                if (emoji.length() > 0) {
                    buttons.add(Button.primary(id, label).withEmoji(Emoji.fromFormatted(emoji)));
                } else {
                    buttons.add(Button.primary(id, label));
                }
            }
        }
        return buttons;
    }
    public static void resolveWarForgeRuins(Game activeGame, String buttonID, Player player, ButtonInteractionEvent event){
        String planet = buttonID.split("_")[1];
        String mech = buttonID.split("_")[2];
        String message = "";
        boolean failed = false;
        message = message + ButtonHelper.mechOrInfCheck(planet, activeGame, player);
        failed = message.contains("Please try again.");
        if (!failed) {
            if(mech.equalsIgnoreCase("mech")){
                new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileFromPlanet(planet), "mech "+planet, activeGame);
                message = message + "Placed mech on" + Mapper.getPlanet(planet).getName();
            }else{
                new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileFromPlanet(planet), "2 infantry "+planet, activeGame);
                message = message + "Placed 2 infantry on" + Mapper.getPlanet(planet).getName();
            }
            ButtonHelper.addReaction(event, false, false, message, "");
            event.getMessage().delete().queue();
        } else {
            ButtonHelper.addReaction(event, false, false, message, "");
        }
    }
    public static void resolveSeedySpace(Game activeGame, String buttonID, Player player, ButtonInteractionEvent event){
        String planet = buttonID.split("_")[2];
        String acOrAgent = buttonID.split("_")[1];
        String message = "";
        boolean failed = false;
        message = message + ButtonHelper.mechOrInfCheck(planet, activeGame, player);
        failed = message.contains("Please try again.");
        if (!failed) {
            if(acOrAgent.equalsIgnoreCase("ac")){
                if (player.hasAbility("scheming")) {
                    activeGame.drawActionCard(player.getUserID());
                    activeGame.drawActionCard(player.getUserID());
                    message = ButtonHelper.getIdent(player) + " Drew 2 AC With Scheming. Please Discard An AC with the blue buttons";
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), ButtonHelper.getTrueIdentity(player, activeGame) + " use buttons to discard",
                        ACInfo.getDiscardActionCardButtons(activeGame, player, false));
                } else {
                    activeGame.drawActionCard(player.getUserID());
                    message = ButtonHelper.getIdent(player) + " Drew 1 AC";
                    ACInfo.sendActionCardInfo(activeGame, player, event);
                }
                if (player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")) {
                    ButtonHelper.commanderUnlockCheck(player, activeGame, "yssaril", event);
                }
            }else{
                Leader playerLeader = player.getLeader(acOrAgent).orElse(null);
                if (playerLeader == null) {
                    return;
                }
                RefreshLeader.refreshLeader(player, playerLeader, activeGame);
                message = message + " Refreshed " + Mapper.getLeader(acOrAgent).getName();
            }
            ButtonHelper.addReaction(event, false, false, message, "");
            event.getMessage().delete().queue();
        } else {
            ButtonHelper.addReaction(event, false, false, message, "");
        }
    }

    public static List<Button> getButtonsForPictureCombats(Game activeGame, String pos, Player p1, Player p2, String groundOrSpace) {
        Tile tile = activeGame.getTileByPosition(pos);
        List<Button> buttons = new ArrayList<>();

        if ("justPicture".equalsIgnoreCase(groundOrSpace)) {
            buttons.add(Button.primary("refreshViewOfSystem_" + pos + "_" + p1.getFaction() + "_" + p2.getFaction() + "_" + groundOrSpace, "Refresh Picture"));
            return buttons;
        }
        buttons.add(Button.danger("getDamageButtons_" + pos, "Assign Hits"));
        if (getButtonsForRepairingUnitsInASystem(p1, activeGame, tile).size() > 1 || getButtonsForRepairingUnitsInASystem(p2, activeGame, tile).size() > 1) {
            buttons.add(Button.success("getRepairButtons_" + pos, "Repair Damage"));
        }
        buttons.add(Button.primary("refreshViewOfSystem_" + pos + "_" + p1.getFaction() + "_" + p2.getFaction() + "_" + groundOrSpace, "Refresh Picture"));

        Player titans = Helper.getPlayerFromUnlockedLeader(activeGame, "titansagent");
        if (!activeGame.isFoWMode() && titans != null && titans.hasUnexhaustedLeader("titansagent")) {
            String finChecker = "FFCC_" + titans.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "exhaustAgent_titansagent", "Titans Agent").withEmoji(Emoji.fromFormatted(Emojis.Titans)));
        }

        Player sol = Helper.getPlayerFromUnlockedLeader(activeGame, "solagent");
        if (!activeGame.isFoWMode() && sol != null && sol.hasUnexhaustedLeader("solagent") && "ground".equalsIgnoreCase(groundOrSpace)) {
            String finChecker = "FFCC_" + sol.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "exhaustAgent_solagent", "Sol Agent").withEmoji(Emoji.fromFormatted(Emojis.Sol)));
        }

        Player letnev = Helper.getPlayerFromUnlockedLeader(activeGame, "letnevagent");
        if ((!activeGame.isFoWMode() || letnev == p1) && letnev != null && letnev.hasUnexhaustedLeader("letnevagent") && "space".equalsIgnoreCase(groundOrSpace)) {
            String finChecker = "FFCC_" + letnev.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "exhaustAgent_letnevagent", "Letnev Agent").withEmoji(Emoji.fromFormatted(Emojis.Letnev)));
        }

        Player nomad = Helper.getPlayerFromUnlockedLeader(activeGame, "nomadagentthundarian");
        if ((!activeGame.isFoWMode() || nomad == p1) && nomad != null && nomad.hasUnexhaustedLeader("nomadagentthundarian")) {
            String finChecker = "FFCC_" + nomad.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "exhaustAgent_nomadagentthundarian", "Thundarian").withEmoji(Emoji.fromFormatted(Emojis.Nomad)));
        }

        Player yin = Helper.getPlayerFromUnlockedLeader(activeGame, "yinagent");
        if ((!activeGame.isFoWMode() || yin == p1) && yin != null && yin.hasUnexhaustedLeader("yinagent")) {
            String finChecker = "FFCC_" + yin.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "yinagent_" + pos, "Yin Agent").withEmoji(Emoji.fromFormatted(Emojis.Yin)));
        }

        if (p1.hasAbility("technological_singularity")) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "nekroStealTech_" + p2.getFaction(), "Steal Tech").withEmoji(Emoji.fromFormatted(Emojis.Nekro)));
        }
        if (p2.hasAbility("technological_singularity") && !activeGame.isFoWMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "nekroStealTech_" + p1.getFaction(), "Steal Tech").withEmoji(Emoji.fromFormatted(Emojis.Nekro)));
        }

        if ((p2.hasAbility("edict") || p2.hasAbility("imperia")) && !activeGame.isFoWMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "mahactStealCC_" + p1.getColor(), "Add Opponent CC to Fleet").withEmoji(Emoji.fromFormatted(Emojis.Mahact)));
        }
        if (p1.hasAbility("edict") || p1.hasAbility("imperia")) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "mahactStealCC_" + p2.getColor(), "Add Opponent CC to Fleet").withEmoji(Emoji.fromFormatted(Emojis.Mahact)));
        }



        if (p2.hasAbility("necrophage")  && !activeGame.isFoWMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "offerNecrophage", "Necrophage").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }
        if (p1.hasAbility("necrophage")) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "offerNecrophage" + p2.getColor(), "Necrophage").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }




        if ("space".equalsIgnoreCase(groundOrSpace) &&activeGame.playerHasLeaderUnlockedOrAlliance(p2, "mentakcommander") && !activeGame.isFoWMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "mentakCommander_" + p1.getColor(), "Mentak Commander on "+p1.getColor()).withEmoji(Emoji.fromFormatted(Emojis.Mentak)));
        }
        if ("space".equalsIgnoreCase(groundOrSpace) && activeGame.playerHasLeaderUnlockedOrAlliance(p1, "mentakcommander")) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "mentakCommander_" + p2.getColor(), "Mentak Commander on "+p2.getColor()).withEmoji(Emoji.fromFormatted(Emojis.Mentak)));
        }

        if ("space".equalsIgnoreCase(groundOrSpace) &&activeGame.playerHasLeaderUnlockedOrAlliance(p2, "mykomentoricommander") && !activeGame.isFoWMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "resolveMykoCommander", "Myko Commander").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }
        if ("space".equalsIgnoreCase(groundOrSpace) && activeGame.playerHasLeaderUnlockedOrAlliance(p1, "mykomentoricommander")) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "resolveMykoCommander", "Myko Commander").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }


        if ("space".equalsIgnoreCase(groundOrSpace) &&ButtonHelper.doesPlayerHaveFSHere("mykomentori_flagship", p2, tile) && !activeGame.isFoWMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "gain_1_comm_from_MahactInf", "Myko Flagship").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }
        if ("space".equalsIgnoreCase(groundOrSpace) && ButtonHelper.doesPlayerHaveFSHere("mykomentori_flagship", p1, tile)) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "gain_1_comm_from_MahactInf", "Myko Flagship").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }







        if ("space".equalsIgnoreCase(groundOrSpace) && !activeGame.isFoWMode()) {
            buttons.add(Button.secondary("announceARetreat", "Announce A Retreat"));
        }
        if ("space".equalsIgnoreCase(groundOrSpace)) {
            buttons.add(Button.danger("retreat_" + pos, "Retreat"));
        }
        if ("space".equalsIgnoreCase(groundOrSpace) &&p2.hasAbility("foresight") && p2.getStrategicCC() > 0 && !activeGame.isFoWMode()) {
            buttons.add(Button.danger("retreat_" + pos +"_foresight", "Foresight").withEmoji(Emoji.fromFormatted(Emojis.Naalu)));       
        }
        if ("space".equalsIgnoreCase(groundOrSpace) &&p1.hasAbility("foresight")&& p1.getStrategicCC() > 0) {
            buttons.add(Button.danger("retreat_" + pos +"_foresight", "Foresight").withEmoji(Emoji.fromFormatted(Emojis.Naalu)));       
        }
        if (p1.hasLeaderUnlocked("keleresherokuuasi") && "space".equalsIgnoreCase(groundOrSpace) && doesPlayerOwnAPlanetInThisSystem(tile, p1, activeGame)) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "purgeKeleresAHero", "Keleres Argent Hero")
                .withEmoji(Emoji.fromFormatted(Emojis.Keleres)));
        }
        if (p2.hasLeaderUnlocked("keleresherokuuasi") && !activeGame.isFoWMode() && "space".equalsIgnoreCase(groundOrSpace) && doesPlayerOwnAPlanetInThisSystem(tile, p1, activeGame)) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "purgeKeleresAHero", "Keleres Argent Hero")
                .withEmoji(Emoji.fromFormatted(Emojis.Keleres)));
        }

        if (ButtonHelper.getTilesOfUnitsWithBombard(p1, activeGame).contains(tile) || ButtonHelper.getTilesOfUnitsWithBombard(p2, activeGame).contains(tile)) {
            if (tile.getUnitHolders().size() > 2) {
                buttons.add(Button.secondary("bombardConfirm_combatRoll_" + tile.getPosition() + "_space_" + CombatRollType.bombardment, "Roll Bombardment"));
            } else {
                buttons.add(Button.secondary("combatRoll_" + tile.getPosition() + "_space_" + CombatRollType.bombardment, "Roll Bombardment"));
            }
        }
        for (UnitHolder unitH : tile.getUnitHolders().values()) {
            String nameOfHolder = "Space";
            if (unitH instanceof Planet) {
                nameOfHolder = Helper.getPlanetRepresentation(unitH.getName(), activeGame);
                if (activeGame.playerHasLeaderUnlockedOrAlliance(p1, "solcommander") && "ground".equalsIgnoreCase(groundOrSpace)) {
                    String finChecker = "FFCC_" + p1.getFaction() + "_";
                    buttons.add(Button.secondary(finChecker + "utilizeSolCommander_" + unitH.getName(), "Sol Commander on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.Sol)));
                }
                if (activeGame.playerHasLeaderUnlockedOrAlliance(p2, "solcommander") && !activeGame.isFoWMode() && "ground".equalsIgnoreCase(groundOrSpace)) {
                    String finChecker = "FFCC_" + p2.getFaction() + "_";
                    buttons.add(Button.secondary(finChecker + "utilizeSolCommander_" + unitH.getName(), "Sol Commander on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.Sol)));
                }
                if (p1.hasAbility("indoctrination") && "ground".equalsIgnoreCase(groundOrSpace)) {
                    String finChecker = "FFCC_" + p1.getFaction() + "_";
                    buttons.add(Button.secondary(finChecker + "initialIndoctrination_" + unitH.getName(), "Indoctrinate on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.Yin)));
                }
                if (p1.hasAbility("assimilate") && "ground".equalsIgnoreCase(groundOrSpace) && (unitH.getUnitCount(UnitType.Spacedock, p2.getColor()) > 0
                || unitH.getUnitCount(UnitType.CabalSpacedock, p2.getColor()) > 0 || unitH.getUnitCount(UnitType.Pds, p2.getColor()) > 0)) {
                    String finChecker = "FFCC_" + p1.getFaction() + "_";
                    buttons.add(Button.secondary(finChecker + "assimilate_" + unitH.getName(), "Assimilate Structures on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.L1Z1X)));
                }
                if (p1.hasUnit("letnev_mech") && "ground".equalsIgnoreCase(groundOrSpace) && unitH.getUnitCount(UnitType.Infantry, p1.getColor()) > 0
                    && ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, p1, "mech") < 4) {
                    String finChecker = "FFCC_" + p1.getFaction() + "_";
                    buttons.add(Button.secondary(finChecker + "letnevMechRes_" + unitH.getName() + "_mech", "Deploy Dunlain Reaper on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.Letnev)));
                }
                if (p2.hasUnit("letnev_mech") && !activeGame.isFoWMode() && "ground".equalsIgnoreCase(groundOrSpace) && unitH.getUnitCount(UnitType.Infantry, p2.getColor()) > 0
                    && ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, p2, "mech") < 4) {
                    String finChecker = "FFCC_" + p2.getFaction() + "_";
                    buttons.add(Button.secondary(finChecker + "letnevMechRes_" + unitH.getName() + "_mech", "Deploy Dunlain Reaper on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.Letnev)));
                }
                if (p2.hasAbility("indoctrination") && !activeGame.isFoWMode() && "ground".equalsIgnoreCase(groundOrSpace)) {
                    String finChecker = "FFCC_" + p2.getFaction() + "_";
                    buttons.add(Button.secondary(finChecker + "initialIndoctrination_" + unitH.getName(), "Indoctrinate on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.Yin)));
                }
                if (p2.hasAbility("assimilate")&& !activeGame.isFoWMode()  && "ground".equalsIgnoreCase(groundOrSpace) && (unitH.getUnitCount(UnitType.Spacedock, p1.getColor()) > 0
                || unitH.getUnitCount(UnitType.CabalSpacedock, p1.getColor()) > 0 || unitH.getUnitCount(UnitType.Pds, p1.getColor()) > 0)) {
                    String finChecker = "FFCC_" + p2.getFaction() + "_";
                    buttons.add(Button.secondary(finChecker + "assimilate_" + unitH.getName(), "Assimilate Structures on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.L1Z1X)));
                }
            }
            if (nameOfHolder.equalsIgnoreCase("space") && "space".equalsIgnoreCase(groundOrSpace)) {
                buttons.add(Button.secondary("combatRoll_" + pos + "_" + unitH.getName(), "Roll Space Combat"));
            } else {
                if(!"space".equalsIgnoreCase(groundOrSpace) && !nameOfHolder.equalsIgnoreCase("space")){
                    buttons.add(Button.secondary("combatRoll_" + pos + "_" + unitH.getName(),
                    "Roll Ground Combat for " + nameOfHolder + ""));
                    buttons.add(Button.secondary("combatRoll_" + tile.getPosition() + "_" + unitH.getName() + "_spacecannondefence", "Roll Space Cannon Defence for " + nameOfHolder));
                }
            }

        }
        return buttons;
    }

    public static boolean doesPlayerOwnAPlanetInThisSystem(Tile tile, Player player, Game activeGame) {
        for (String planet : player.getPlanets()) {
            Tile t2 = null;
            try {
                t2 = activeGame.getTileFromPlanet(planet);
            } catch (Error e) {

            }
            if (t2 != null && t2.getPosition().equalsIgnoreCase(tile.getPosition())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPlanetLegendaryOrHome(String planetName, Game activeGame, boolean onlyIncludeYourHome, Player p1) {
        UnitHolder unitHolder = getUnitHolderFromPlanetName(planetName, activeGame);
        Planet planetHolder = (Planet) unitHolder;
        if (planetHolder == null) return false;

        boolean hasAbility = planetHolder.isHasAbility()
            || planetHolder.getTokenList().stream().anyMatch(token -> token.contains("nanoforge") || token.contains("legendary") || token.contains("consulate"));

        String originalType = planetHolder.getOriginalPlanetType();
        boolean oneOfThree = originalType != null && List.of("industrial", "cultural", "hazardous").contains(originalType.toLowerCase());
        if (!planetHolder.getName().toLowerCase().contains("rex") && !planetHolder.getName().toLowerCase().contains("mr") && !oneOfThree) {
            if (onlyIncludeYourHome && p1 != null && p1.getPlayerStatsAnchorPosition() != null) {
                if (activeGame.getTileFromPlanet(planetName).getPosition().equalsIgnoreCase(p1.getPlayerStatsAnchorPosition())) {
                    hasAbility = true;
                }
                if (p1.getFaction().equalsIgnoreCase("ghost") && planetName.equalsIgnoreCase("creuss")) {
                    hasAbility = true;
                }
            } else {
                hasAbility = true;
            }
        }
        return hasAbility;
    }

    public static boolean isTileHomeSystem(Tile tile) {
        boolean isHome = false;
        if(tile.getTileID().equalsIgnoreCase("0g")){
            return true;
        }
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder instanceof Planet planetHolder) {
                boolean oneOfThree = planetHolder.getOriginalPlanetType() != null && ("industrial".equalsIgnoreCase(planetHolder.getOriginalPlanetType())
                    || "cultural".equalsIgnoreCase(planetHolder.getOriginalPlanetType()) || "hazardous".equalsIgnoreCase(planetHolder.getOriginalPlanetType()));
                if (!planetHolder.getName().toLowerCase().contains("rex") && !planetHolder.getName().toLowerCase().contains("mr") && !oneOfThree) {
                    isHome = true;
                }
            }
        }
        return isHome;
    }

    public static void checkFleetAndCapacity(Player player, Game activeGame, Tile tile, GenericInteractionCreateEvent event) {
        if (tile.getRepresentation() == null || tile.getRepresentation().equalsIgnoreCase("null")) {
            return;
        }
        if(tile.getRepresentation().toLowerCase().contains("nombox")){
            return;
        }
        int armadaValue = 0;
        if (player == null) {
            return;
        }
        if (player.hasAbility("armada")) {
            armadaValue = 2;
        }
        int fleetCap = (player.getFleetCC() + armadaValue + player.getMahactCC().size()) * 2;
        if (player.getLeader("letnevhero").map(Leader::isActive).orElse(false)) {
            fleetCap += 1000;
        }
        int capacity = 0;
        int numInfNFightersNMechs = 0;
        int numOfCapitalShips = 0;
        int fightersIgnored = 0;
        int numFighter2s = 0;
        int numFighter2sFleet = 0;
        boolean capacityViolated = false;
        boolean fleetSupplyViolated = false;

        for (UnitHolder capChecker : tile.getUnitHolders().values()) {
            HashMap<UnitModel, Integer> unitsByQuantity = CombatHelper.GetAllUnits(capChecker, player, event);
            for (UnitModel unit : unitsByQuantity.keySet()) {
                if ("space".equalsIgnoreCase(capChecker.getName())) {
                    capacity += unit.getCapacityValue() * unitsByQuantity.get(unit);
                }
                if ("spacedock".equalsIgnoreCase(unit.getBaseType()) && !"space".equalsIgnoreCase(capChecker.getName())) {
                    if ("cabal_spacedock".equalsIgnoreCase(unit.getId())) {
                        fightersIgnored += 6;
                    } else if ("cabal_spacedock2".equalsIgnoreCase(unit.getId())) {
                        fightersIgnored += 12;
                    } else {
                        fightersIgnored += 3;

                    }
                }
            }
        }

        UnitHolder combatOnHolder = tile.getUnitHolders().get("space");
        HashMap<UnitModel, Integer> unitsByQuantity = CombatHelper.GetAllUnits(combatOnHolder, player, event);
        for (UnitModel unit : unitsByQuantity.keySet()) {
            if ("fighter".equalsIgnoreCase(unit.getBaseType()) || "infantry".equalsIgnoreCase(unit.getBaseType()) || "mech".equalsIgnoreCase(unit.getBaseType())) {

                if ("fighter".equalsIgnoreCase(unit.getBaseType()) && player.hasFF2Tech()) {
                    numFighter2s = unitsByQuantity.get(unit) - fightersIgnored;
                    if (numFighter2s < 0) {
                        numFighter2s = 0;
                    }
                }
                if ("fighter".equalsIgnoreCase(unit.getBaseType())) {
                    int numCountedFighters = unit.getCapacityUsed() * unitsByQuantity.get(unit) - fightersIgnored;
                    if (numCountedFighters < 0) {
                        numCountedFighters = 0;
                    }
                    numInfNFightersNMechs += numCountedFighters;
                } else {
                    numInfNFightersNMechs += unit.getCapacityUsed() * unitsByQuantity.get(unit);
                }

            } else {
                if (unit.getIsShip()) {
                    if (player.hasAbility("capital_fleet") && unit.getBaseType().contains("destroyer")) {
                        numOfCapitalShips += unitsByQuantity.get(unit);
                    } else {
                        numOfCapitalShips += unitsByQuantity.get(unit) * 2;
                    }
                }
            }

        }
        if (numOfCapitalShips > fleetCap) {
            fleetSupplyViolated = true;
        }
        if (numInfNFightersNMechs > capacity) {
            if (numInfNFightersNMechs - numFighter2s > capacity) {
                capacityViolated = true;
            } else {
                numFighter2s = numInfNFightersNMechs - capacity;
                if (player.hasTech("hcf2")) {
                    numFighter2sFleet = numFighter2s;
                }else{
                    numFighter2sFleet = numFighter2s*2;
                }
                if (numFighter2sFleet + numOfCapitalShips > fleetCap) {
                    fleetSupplyViolated = true;
                }
            }
        }
        if (numOfCapitalShips > 8 && !fleetSupplyViolated) {
            if (player.getLeaderIDs().contains("letnevcommander") && !player.hasLeaderUnlocked("letnevcommander")) {
                commanderUnlockCheck(player, activeGame, "letnev", event);
            }
        }
        String message = getTrueIdentity(player, activeGame);
        if (fleetSupplyViolated) {
            message += " You are violating fleet supply in tile " + tile.getRepresentation() + ". ";
        }
        if (capacityViolated) {
            message += " You are violating carrying capacity in tile " + tile.getRepresentation() + ". ";
        }
        System.out.printf("%d %d %d %d%n", fleetCap, numOfCapitalShips, capacity, numInfNFightersNMechs);
        if (capacityViolated || fleetSupplyViolated) {
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), message);
        }
    }

    public static List<Tile> getAllTilesWithProduction(Game activeGame, Player player, GenericInteractionCreateEvent event){
        List<Tile> tiles = new ArrayList<>();
        for(Tile tile : activeGame.getTileMap().values()){
            for (UnitHolder capChecker : tile.getUnitHolders().values()) {
                HashMap<UnitModel, Integer> unitsByQuantity = CombatHelper.GetAllUnits(capChecker, player, event);
                for (UnitModel unit : unitsByQuantity.keySet()) {
                    if(unit.getProductionValue() > 0){
                        if(!tiles.contains(tile)){
                            tiles.add(tile);
                        }
                    }
                }
            }
        }
        return tiles;
    }

   

    public static List<String> getAllPlanetsAdjacentToTileNotOwnedByPlayer(Tile tile, Game activeGame, Player player) {
        List<String> planets = new ArrayList<>();
        for (String pos2 : FoWHelper.getAdjacentTiles(activeGame, tile.getPosition(), player, false)) {
            Tile tile2 = activeGame.getTileByPosition(pos2);
            for (UnitHolder planetUnit2 : tile2.getUnitHolders().values()) {
                if ("space".equalsIgnoreCase(planetUnit2.getName())) {
                    continue;
                }
                Planet planetReal2 = (Planet) planetUnit2;
                String planet2 = planetReal2.getName();
                if (!player.getPlanetsAllianceMode().contains(planet2)) {
                    planets.add(planet2);
                }
            }
        }
        return planets;
    }

    public static List<Button> customRexLegendary(Player player, Game activeGame) {
        List<Button> buttons = new ArrayList<>();
        Tile rex = activeGame.getTileFromPlanet("mr");
        List<String> planetsToCheck = getAllPlanetsAdjacentToTileNotOwnedByPlayer(rex, activeGame, player);
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            for (String planet2 : p2.getPlanetsAllianceMode()) {
                PlanetModel mod = Mapper.getPlanet(planet2);
                if (mod.getLegendaryAbilityName() != null && !"".equals(mod.getLegendaryAbilityName()) && !planetsToCheck.contains(planet2)) {
                    planetsToCheck.add(planet2);
                }
            }
        }
        for (String planet : planetsToCheck) {
            UnitHolder planetUnit2 = activeGame.getPlanetsInfo().get(planet);
            if (planetUnit2 != null) {
                for (Player p2 : activeGame.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    int numMechs = 0;
                    int numInf = 0;
                    String colorID = Mapper.getColorID(p2.getColor());
                    UnitKey mechKey = Mapper.getUnitKey("mf", colorID);
                    UnitKey infKey = Mapper.getUnitKey("gf", colorID);
                    if (planetUnit2.getUnits() != null) {
                        if (planetUnit2.getUnits().get(mechKey) != null) {
                            numMechs = planetUnit2.getUnits().get(mechKey);
                        }
                        if (planetUnit2.getUnits().get(infKey) != null) {
                            numInf = planetUnit2.getUnits().get(infKey);
                        }
                    }
                    String planetId2 = planetUnit2.getName();
                    String planetRepresentation2 = Helper.getPlanetRepresentation(planetId2, activeGame);
                    if (numInf > 0) {
                        buttons.add(Button.success("specialRex_" + planet + "_" + p2.getFaction() + "_infantry", "Remove 1 infantry from " + planetRepresentation2));
                    }
                    if (numMechs > 0) {
                        buttons.add(Button.primary("specialRex_" + planet + "_" + p2.getFaction() + "_mech", "Remove 1 mech from " + planetRepresentation2));
                    }
                }
            }
        }
        return buttons;
    }

    public static void resolveSpecialRex(Player player, Game activeGame, String buttonID, String ident, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
        if (p2 == null) return;

        String mechOrInf = buttonID.split("_")[3];
        String msg = ident + " used the special Mecatol Rex power to remove 1 " + mechOrInf + " on " + Helper.getPlanetRepresentation(planet, activeGame);
        new RemoveUnits().unitParsing(event, p2.getColor(), activeGame.getTileFromPlanet(planet), "1 " + mechOrInf + " " + planet, activeGame);
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), msg);
        event.getMessage().delete().queue();
    }

    public static List<Button> getEchoAvailableSystems(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : activeGame.getTileMap().values()) {
            if (tile.getUnitHolders().size() < 2) {
                buttons.add(Button.success("echoPlaceFrontier_" + tile.getPosition(), tile.getRepresentationForButtons(activeGame, player)));
            }
        }
        return buttons;
    }

    public static void resolveEchoPlaceFrontier(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = activeGame.getTileByPosition(pos);
        AddToken.addToken(event, tile, Constants.FRONTIER, activeGame);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdent(player) + " placed a frontier token in " + tile.getRepresentationForButtons(activeGame, player));
        event.getMessage().delete().queue();
    }

    public static List<Button> getEndOfTurnAbilities(Player player, Game activeGame) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> endButtons = new ArrayList<>();
        String planet = "mallice";
        if (player.getPlanets().contains(planet) && !player.getExhaustedPlanetsAbilities().contains(planet)) {
            endButtons.add(Button.success(finChecker + "planetAbilityExhaust_" + planet, "Use Mallice Ability"));
        }
        planet = "mirage";
        if (player.getPlanets().contains(planet) && !player.getExhaustedPlanetsAbilities().contains(planet)) {
            endButtons.add(Button.success(finChecker + "planetAbilityExhaust_" + planet, "Use Mirage Ability"));
        }
        planet = "hopesend";
        if (player.getPlanets().contains(planet) && !player.getExhaustedPlanetsAbilities().contains(planet)) {
            endButtons.add(Button.success(finChecker + "planetAbilityExhaust_" + planet, "Use Hope's End Ability"));
        }
        planet = "silence";
        if (player.getPlanets().contains(planet) && !player.getExhaustedPlanetsAbilities().contains(planet)) {
            endButtons.add(Button.success(finChecker + "planetAbilityExhaust_" + planet, "Use Silence Ability"));
        }
        planet = "tarrock";
        if (player.getPlanets().contains(planet) && !player.getExhaustedPlanetsAbilities().contains(planet)) {
            endButtons.add(Button.success(finChecker + "planetAbilityExhaust_" + planet, "Use Tarrock Ability"));
        }
        planet = "prism";
        if (player.getPlanets().contains(planet) && !player.getExhaustedPlanetsAbilities().contains(planet)) {
            endButtons.add(Button.success(finChecker + "planetAbilityExhaust_" + planet, "Use Prism Ability"));
        }
        planet = "echo";
        if (player.getPlanets().contains(planet) && !player.getExhaustedPlanetsAbilities().contains(planet)) {
            endButtons.add(Button.success(finChecker + "planetAbilityExhaust_" + planet, "Use Echo's Ability"));
        }
        planet = "domna";
        if (player.getPlanets().contains(planet) && !player.getExhaustedPlanetsAbilities().contains(planet)) {
            endButtons.add(Button.success(finChecker + "planetAbilityExhaust_" + planet, "Use Domna's Ability"));
        }
        planet = "primor";
        if (player.getPlanets().contains(planet) && !player.getExhaustedPlanetsAbilities().contains(planet)) {
            endButtons.add(Button.success(finChecker + "planetAbilityExhaust_" + planet, "Use Primor Ability"));
        }
        planet = "mr";
        if (player.getPlanets().contains(planet) && !player.getExhaustedPlanetsAbilities().contains(planet)
            && activeGame.getPlanetsInfo().get("mr").getTokenList().contains("attachment_legendary.png")) {
            endButtons.add(Button.success(finChecker + "planetAbilityExhaust_" + planet, "Use Mecatol Rex Ability"));
        }
        if (player.getTechs().contains("pi") && !player.getExhaustedTechs().contains("pi")) {
            endButtons.add(Button.danger(finChecker + "exhaustTech_pi", "Exhaust Predictive Intelligence"));
        }
        if (!player.hasAbility("arms_dealer")) {
            for (String shipOrder : ButtonHelper.getPlayersShipOrders(player)) {
                if (Helper.getTileWithShipsNTokenPlaceUnitButtons(player, activeGame, "dreadnought", "placeOneNDone_skipbuild", null).size() > 0) {
                    endButtons.add(Button.success(finChecker + "resolveShipOrder_" + shipOrder, "Use " + Mapper.getRelic(shipOrder).getName()));
                }
            }
        }

        if (player.getTechs().contains("bs") && !player.getExhaustedTechs().contains("bs")) {
            endButtons.add(Button.success(finChecker + "exhaustTech_bs", "Exhaust Bio-Stims"));
        }
        if (player.hasUnexhaustedLeader("naazagent")) {
            endButtons.add(Button.success(finChecker + "exhaustAgent_naazagent", "Use NRA Agent").withEmoji(Emoji.fromFormatted(Emojis.Naaz)));
        }

        endButtons.add(Button.danger("deleteButtons", "Delete these buttons"));
        return endButtons;
    }

    public static List<String> getPlayersShipOrders(Player player) {
        List<String> shipOrders = new ArrayList<String>();
        for (String relic : player.getRelics()) {
            if (relic.toLowerCase().contains("axisorder") && !player.getExhaustedRelics().contains(relic)) {
                shipOrders.add(relic);
            }
        }
        return shipOrders;
    }


    public static List<Button> getStartOfTurnButtons(Player player, Game activeGame, boolean doneActionThisTurn, GenericInteractionCreateEvent event) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        activeGame.setDominusOrb(false);
       
        List<Button> startButtons = new ArrayList<>();
        Button tacticalAction = Button.success(finChecker + "tacticalAction", "Tactical Action (" + player.getTacticalCC() + ")");
        int numOfComponentActions = getAllPossibleCompButtons(activeGame, player, event).size() - 2;
        Button componentAction = Button.success(finChecker + "componentAction", "Component Action (" + numOfComponentActions + ")");

        startButtons.add(tacticalAction);
        startButtons.add(componentAction);
        boolean hadAnyUnplayedSCs = false;
        for (Integer SC : player.getSCs()) {
            if (!activeGame.getPlayedSCs().contains(SC)) {
                hadAnyUnplayedSCs = true;
                if (activeGame.isHomeBrewSCMode()) {
                    Button strategicAction = Button.success(finChecker + "strategicAction_" + SC, "Play SC #" + SC);
                    startButtons.add(strategicAction);
                } else {
                    Button strategicAction = Button.success(finChecker + "strategicAction_" + SC, "Play SC #" + SC).withEmoji(Emoji.fromFormatted(Emojis.getSCEmojiFromInteger(SC)));
                    startButtons.add(strategicAction);
                }
            }
        }

        if (!hadAnyUnplayedSCs && !doneActionThisTurn) {
            Button pass = Button.danger(finChecker + "passForRound", "Pass");
            if (getEndOfTurnAbilities(player, activeGame).size() > 1) {
                startButtons.add(Button.primary("endOfTurnAbilities", "Do End Of Turn Ability (" + (getEndOfTurnAbilities(player, activeGame).size() - 1) + ")"));
            }

            startButtons.add(pass);
            if(!activeGame.isHomeBrewSCMode() && !activeGame.isFoWMode()){
                for(Player p2 : activeGame.getRealPlayers()){
                    for (int sc : player.getSCs()) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(ButtonHelper.getTrueIdentity(p2, activeGame));
                        sb.append(" You are getting this ping because SC #"+sc+" has been played and now it is their turn again and you still havent reacted. Please do so, or ping Fin if this is an error. ");
                        if(!activeGame.getFactionsThatReactedToThis("scPlay"+sc).isEmpty()){
                            sb.append("Message link is: "+activeGame.getFactionsThatReactedToThis("scPlay"+sc).replace("666fin", ":")+"\n");
                        }
                        sb.append("You currently have ").append(p2.getStrategicCC()).append(" CC in your strategy pool.");
                        if (!p2.hasFollowedSC(sc)) {
                            MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(), sb.toString());
                        }
                    }
                }
            }
            

        }
        if (doneActionThisTurn) {
            ButtonHelperFactionSpecific.checkBlockadeStatusOfEverything(player, activeGame, event);
            if (getEndOfTurnAbilities(player, activeGame).size() > 1) {
                startButtons.add(Button.primary("endOfTurnAbilities", "Do End Of Turn Ability (" + (getEndOfTurnAbilities(player, activeGame).size() - 1) + ")"));
            }
            startButtons.add(Button.danger(finChecker + "turnEnd", "End Turn"));
           for (String law : activeGame.getLaws().keySet()) {
                if ("minister_war".equalsIgnoreCase(law) ) {
                    if (activeGame.getLawsInfo().get(law).equalsIgnoreCase(player.getFaction())) {
                        startButtons.add(Button.secondary(finChecker+"ministerOfWar", "Use Minister of War"));
                    }
                }
            }
            if(!activeGame.getJustPlayedComponentAC()){
                player.setWhetherPlayerShouldBeTenMinReminded(true);
            }
        } else {
            activeGame.setJustPlayedComponentAC(false);
            if (player.getTechs().contains("cm")) {
                Button chaos = Button.secondary("startChaosMapping", "Use Chaos Mapping").withEmoji(Emoji.fromFormatted(Emojis.Saar));
                startButtons.add(chaos);
            }
            if (player.hasAbility("laws_order") && !activeGame.getLaws().isEmpty()) {
                Button chaos = Button.secondary("useLawsOrder", "Pay To Ignore Laws").withEmoji(Emoji.fromFormatted(Emojis.Keleres));
                startButtons.add(chaos);
            }
            if (player.hasTech("td") && !player.getExhaustedTechs().contains("td")) {
                Button transit = Button.secondary(finChecker + "exhaustTech_td", "Exhaust Transit Diodes");
                transit = transit.withEmoji(Emoji.fromFormatted(Emojis.CyberneticTech));
                startButtons.add(transit);
            }
        }
        if (player.hasTech("pa") && ButtonHelper.getPsychoTechPlanets(activeGame, player).size() > 1) {
            Button psycho = Button.success(finChecker + "getPsychoButtons", "Use Psychoarcheology");
            psycho = psycho.withEmoji(Emoji.fromFormatted(Emojis.BioticTech));
            startButtons.add(psycho);
        }

        Button transaction = Button.primary("transaction", "Transaction");
        startButtons.add(transaction);
        Button modify = Button.secondary("getModifyTiles", "Modify Units");
        startButtons.add(modify);
        if (player.hasUnexhaustedLeader("hacanagent")) {
            Button hacanButton = Button.secondary("exhaustAgent_hacanagent", "Use Hacan Agent").withEmoji(Emoji.fromFormatted(Emojis.Hacan));
            startButtons.add(hacanButton);
        }
        if (player.hasRelicReady("e6-g0_network")) {
            startButtons.add(Button.success("exhauste6g0network", "Exhaust E6-G0 Network Relic to Draw AC"));
        }
        if (player.hasUnexhaustedLeader("nekroagent") && player.getAc() > 0) {
            Button nekroButton = Button.secondary("exhaustAgent_nekroagent", "Use Nekro Agent on yourself").withEmoji(Emoji.fromFormatted(Emojis.Nekro));
            startButtons.add(nekroButton);
        }
        if (activeGame.getLatestTransactionMsg() != null && !"".equalsIgnoreCase(activeGame.getLatestTransactionMsg())) {
            activeGame.getMainGameChannel().deleteMessageById(activeGame.getLatestTransactionMsg()).queue();
            activeGame.setLatestTransactionMsg("");
        }
        if (activeGame.getActionCards().size() > 130 && getButtonsToSwitchWithAllianceMembers(player, activeGame, false).size() > 0) {
            startButtons.addAll(getButtonsToSwitchWithAllianceMembers(player, activeGame, false));
        }

        return startButtons;
    }

    public static void checkForPrePassing(Game activeGame, Player player){
        activeGame.setCurrentReacts("Pre Pass "+player.getFaction(), "");
        boolean hadAnyUnplayedSCs = false;
        for (Integer SC : player.getSCs()) {
            if (!activeGame.getPlayedSCs().contains(SC)) {
                hadAnyUnplayedSCs = true;
            }
        }
        if(player.getTacticalCC() == 0 && !hadAnyUnplayedSCs && !player.isPassed()){
            String msg = player.getRepresentation() + " you have the option to pre-pass, which means on your next turn, the bot automatically passes for you. This is entirely optional";
            List<Button> scButtons = new ArrayList<>();
            scButtons.add(Button.success("resolvePreassignment_Pre Pass "+player.getFaction(), "Pass on Next Turn"));
            scButtons.add(Button.danger("deleteButtons","Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),msg, scButtons);
        }
    }
    public static List<Button> getPossibleRings(Player player, Game activeGame) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> ringButtons = new ArrayList<>();
        Tile centerTile = activeGame.getTileByPosition("000");
        if (centerTile != null) {
            Button rex = Button.success(finChecker + "ringTile_000", centerTile.getRepresentationForButtons(activeGame, player));
            ringButtons.add(rex);
        }
        int rings = activeGame.getRingCount();
        for (int x = 1; x < rings + 1; x++) {
            Button ringX = Button.success(finChecker + "ring_" + x, "Ring #" + x);
            ringButtons.add(ringX);
        }
        Button corners = Button.success(finChecker + "ring_corners", "Corners");
        ringButtons.add(corners);
        return ringButtons;
    }

    public static List<Button> getTileInARing(Player player, Game activeGame, String buttonID, GenericInteractionCreateEvent event) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> ringButtons = new ArrayList<>();
        String ringNum = buttonID.replace("ring_", "");

        if ("corners".equalsIgnoreCase(ringNum)) {
            Tile tr = activeGame.getTileByPosition("tl");
            if (tr != null && !AddCC.hasCC(event, player.getColor(), tr)) {
                Button corners = Button.success(finChecker + "ringTile_tl", tr.getRepresentationForButtons(activeGame, player));
                ringButtons.add(corners);
            }
            tr = activeGame.getTileByPosition("tr");
            if (tr != null && !AddCC.hasCC(event, player.getColor(), tr)) {
                Button corners = Button.success(finChecker + "ringTile_tr", tr.getRepresentationForButtons(activeGame, player));
                ringButtons.add(corners);
            }
            tr = activeGame.getTileByPosition("bl");
            if (tr != null && !AddCC.hasCC(event, player.getColor(), tr)) {
                Button corners = Button.success(finChecker + "ringTile_bl", tr.getRepresentationForButtons(activeGame, player));
                ringButtons.add(corners);
            }
            tr = activeGame.getTileByPosition("br");
            if (tr != null && !AddCC.hasCC(event, player.getColor(), tr)) {
                Button corners = Button.success(finChecker + "ringTile_br", tr.getRepresentationForButtons(activeGame, player));
                ringButtons.add(corners);
            }
        } else {
            int ringN;
            if (ringNum.contains("_")) {
                ringN = Integer.parseInt(ringNum.substring(0, ringNum.indexOf("_")));
            } else {
                ringN = Integer.parseInt(ringNum);
            }
            int totalTiles = ringN * 6;
            if (ringNum.contains("_")) {
                String side = ringNum.substring(ringNum.lastIndexOf("_") + 1);
                if ("left".equalsIgnoreCase(side)) {
                    for (int x = totalTiles / 2; x < totalTiles + 1; x++) {
                        String pos = ringN + "" + x;
                        Tile tile = activeGame.getTileByPosition(pos);
                        if (tile != null && !tile.getRepresentationForButtons(activeGame, player).contains("Hyperlane") && !AddCC.hasCC(event, player.getColor(), tile)) {
                            Button corners = Button.success(finChecker + "ringTile_" + pos, tile.getRepresentationForButtons(activeGame, player));
                            ringButtons.add(corners);
                        }
                    }
                    String pos = ringN + "01";
                    Tile tile = activeGame.getTileByPosition(pos);
                    if (tile != null && !tile.getRepresentationForButtons(activeGame, player).contains("Hyperlane") && !AddCC.hasCC(event, player.getColor(), tile)) {
                        Button corners = Button.success(finChecker + "ringTile_" + pos, tile.getRepresentationForButtons(activeGame, player));
                        ringButtons.add(corners);
                    }
                } else {
                    for (int x = 1; x < (totalTiles / 2) + 1; x++) {
                        String pos = ringN + "" + x;
                        if (x < 10) {
                            pos = ringN + "0" + x;
                        }
                        Tile tile = activeGame.getTileByPosition(pos);
                        if (tile != null && !tile.getRepresentationForButtons(activeGame, player).contains("Hyperlane") && !AddCC.hasCC(event, player.getColor(), tile)) {
                            Button corners = Button.success(finChecker + "ringTile_" + pos, tile.getRepresentationForButtons(activeGame, player));
                            ringButtons.add(corners);
                        }
                    }
                }
            } else {

                if (ringN < 5) {
                    for (int x = 1; x < totalTiles + 1; x++) {
                        String pos = ringN + "" + x;
                        if (x < 10) {
                            pos = ringN + "0" + x;
                        }
                        Tile tile = activeGame.getTileByPosition(pos);
                        if (tile != null && !tile.getRepresentationForButtons(activeGame, player).contains("Hyperlane") && !AddCC.hasCC(event, player.getColor(), tile)) {
                            Button corners = Button.success(finChecker + "ringTile_" + pos, tile.getRepresentationForButtons(activeGame, player));
                            ringButtons.add(corners);
                        }
                    }
                } else {
                    Button ringLeft = Button.success(finChecker + "ring_" + ringN + "_left", "Left Half");
                    ringButtons.add(ringLeft);
                    Button ringRight = Button.success(finChecker + "ring_" + ringN + "_right", "Right Half");
                    ringButtons.add(ringRight);
                }
            }
        }
        ringButtons.add(Button.danger("ChooseDifferentDestination", "Get a different ring"));

        return ringButtons;
    }

    public static String getTrueIdentity(Player player, Game activeGame) {
        return Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true);
    }

    public static void exploreDET(Player player, Game activeGame, ButtonInteractionEvent event) {
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        if (!FoWHelper.playerHasShipsInSystem(player, tile)) {
            return;
        }
        if (player.hasTech("det") && tile.getUnitHolders().get("space").getTokenList().contains(Mapper.getTokenID(Constants.FRONTIER))) {
            if (player.hasAbility("voidsailors")) {
                String cardID = activeGame.drawExplore(Constants.FRONTIER);
                String cardID2 = activeGame.drawExplore(Constants.FRONTIER);
                String card = Mapper.getExploreRepresentation(cardID);
                String[] cardInfo1 = card.split(";");
                String name1 = cardInfo1[0];
                String card2 = Mapper.getExploreRepresentation(cardID2);
                String[] cardInfo2 = card2.split(";");
                String name2 = cardInfo2[0];

                Button resolveExplore1 = Button.success("resFrontier_" + cardID + "_" + tile.getPosition() + "_" + cardID2, "Choose " + name1);
                Button resolveExplore2 = Button.success("resFrontier_" + cardID2 + "_" + tile.getPosition() + "_" + cardID, "Choose " + name2);
                List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
                //code to draw 2 explores and get their names
                //Send Buttons to decide which one to explore
                String message = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " Please decide which card to resolve.";

                if (!activeGame.isFoWMode() && event.getChannel() != activeGame.getActionsChannel()) {

                    String pF = player.getFactionEmoji();
                    MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), "Using Voidsailors,  " + pF + " found a " + name1 + " and a " + name2 + " in " + tile.getRepresentation());

                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Found a " + name1 + " and a " + name2 + " in " + tile.getRepresentation());
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);

                String msg2 = "As a reminder of their text, the card abilities read as: \n";
                msg2 = msg2 + name1 +": "+cardInfo1[4]+"\n";
                msg2 = msg2 + name2 +": "+cardInfo2[4]+"\n";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2);
            } else {
                new ExpFrontier().expFront(event, tile, activeGame, player);
            }

        }
    }

    public static void sendTradeHolderSomething(Player player, Game activeGame, String buttonID, ButtonInteractionEvent event){
        String tgOrDebt = buttonID.split("_")[1];
        Player tradeHolder = null;
        for(Player p2 : activeGame.getRealPlayers()){
            if(p2.getSCs().contains(5)){
                tradeHolder = p2;
                break;
            }
        }
        String msg  = player.getRepresentation()+ " sent 1 "+tgOrDebt+" to "+ tradeHolder.getRepresentation();;
        if(tradeHolder != null){
            if(tgOrDebt.equalsIgnoreCase("tg")){
                checkTransactionLegality(activeGame, player, tradeHolder);
                if(player.getTg() > 0){
                    tradeHolder.setTg(tradeHolder.getTg() +1);
                    player.setTg(player.getTg() -1);
                }else{
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getTrueIdentity(player, activeGame) + " you had no tg to send, no tg sent.");
                    return;
                }
                
            }else{
                SendDebt.sendDebt(player, tradeHolder, 1);
            }
        }else{
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getTrueIdentity(player, activeGame) + " game could not find trade holder. Ping Fin to fix this.");
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);

    }

    public static boolean doesPlanetHaveAttachmentTechSkip(Tile tile, String planet) {
        UnitHolder unitHolder = tile.getUnitHolders().get(planet);
        return unitHolder.getTokenList().contains(Mapper.getAttachmentID(Constants.WARFARE)) ||
            unitHolder.getTokenList().contains(Mapper.getAttachmentID(Constants.CYBERNETIC)) ||
            unitHolder.getTokenList().contains(Mapper.getAttachmentID(Constants.BIOTIC)) ||
            unitHolder.getTokenList().contains(Mapper.getAttachmentID(Constants.PROPULSION));
    }

    public static List<Button> scanlinkResolution(Player player, Game activeGame, ButtonInteractionEvent event) {
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder planetUnit : tile.getUnitHolders().values()) {
            if ("space".equalsIgnoreCase(planetUnit.getName())) {
                continue;
            }
            Planet planetReal = (Planet) planetUnit;
            String planet = planetReal.getName();
            if (planetReal.getOriginalPlanetType() != null && player.getPlanetsAllianceMode().contains(planet) && FoWHelper.playerHasUnitsOnPlanet(player, tile, planet)) {
                List<Button> planetButtons = getPlanetExplorationButtons(activeGame, planetReal, player);
                buttons.addAll(planetButtons);
            }
        }
        return buttons;
    }

    public static List<Button> getPlanetExplorationButtons(Game activeGame, Planet planet, Player player) {
        if (planet == null || activeGame == null) return null;

        String planetType = planet.getOriginalPlanetType();
        String planetId = planet.getName();
        String planetRepresentation = Helper.getPlanetRepresentation(planetId, activeGame);
        List<Button> buttons = new ArrayList<>();
        Set<String> explorationTraits = new HashSet<>();
        if (("industrial".equalsIgnoreCase(planetType) || "cultural".equalsIgnoreCase(planetType) || "hazardous".equalsIgnoreCase(planetType))) {
            explorationTraits.add(planetType);
        }
        if (planet.getTokenList().contains("attachment_titanspn.png")) {
            explorationTraits.add("cultural");
            explorationTraits.add("industrial");
            explorationTraits.add("hazardous");
        }
        if (planet.getTokenList().contains("attachment_industrialboom.png")) {
            explorationTraits.add("industrial");
        }
        if (player.hasAbility("black_markets") && explorationTraits.size() > 0) {
            String traits = ButtonHelperFactionSpecific.getAllOwnedPlanetTypes(player, activeGame);
            if (traits.contains("industrial")) {
                explorationTraits.add("industrial");
            }
            if (traits.contains("cultural")) {
                explorationTraits.add("cultural");
            }
            if (traits.contains("hazardous")) {
                explorationTraits.add("hazardous");
            }
        }

        for (String trait : explorationTraits) {
            String buttonId = "movedNExplored_filler_" + planetId + "_" + trait;
            String buttonMessage = "Explore " + planetRepresentation + (explorationTraits.size() > 1 ? " as " + trait : "");
            Emoji emoji = Emoji.fromFormatted(Emojis.getEmojiFromDiscord(trait));
            Button button = Button.secondary(buttonId, buttonMessage).withEmoji(emoji);
            buttons.add(button);
        }
        return buttons;
    }

    public static void resolveEmpyCommanderCheck(Player player, Game activeGame, Tile tile, GenericInteractionCreateEvent event) {

        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 != player && AddCC.hasCC(event, p2.getColor(), tile) && activeGame.playerHasLeaderUnlockedOrAlliance(p2, "empyreancommander")) {
                MessageChannel channel = activeGame.getMainGameChannel();
                if (activeGame.isFoWMode()) {
                    channel = p2.getPrivateChannel();
                }
                RemoveCC.removeCC(event, p2.getColor(), tile, activeGame);
                String message = Helper.getPlayerRepresentation(p2, activeGame, activeGame.getGuild(), true)
                    + " due to having the empyrean commander, the cc you had in the active system has been removed. Reminder that this is optional but was done automatically";
                MessageHelper.sendMessageToChannel(channel, message);
            }
        }
    }

    public static List<Tile> getTilesWithShipsInTheSystem(Player player, Game activeGame) {
        List<Tile> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(activeGame.getTileMap()).entrySet()) {
            if (FoWHelper.playerHasShipsInSystem(player, tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                buttons.add(tile);
            }
        }
        return buttons;
    }

    public static List<Button> getTilesToModify(Player player, Game activeGame, GenericInteractionCreateEvent event) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(activeGame.getTileMap()).entrySet()) {
            if (FoWHelper.playerIsInSystem(activeGame, tileEntry.getValue(), player)) {
                Tile tile = tileEntry.getValue();
                Button validTile = Button.success(finChecker + "genericModify_" + tileEntry.getKey(), tile.getRepresentationForButtons(activeGame, player));
                buttons.add(validTile);
            }
        }
        Button validTile2 = Button.danger(finChecker + "deleteButtons", "Delete these buttons");
        buttons.add(validTile2);
        return buttons;
    }

    public static List<Button> getDomnaStepOneTiles(Player player, Game activeGame) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(activeGame.getTileMap()).entrySet()) {
            if (FoWHelper.playerHasShipsInSystem(player, tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                Button validTile = Button.success(finChecker + "domnaStepOne_" + tileEntry.getKey(), tile.getRepresentationForButtons(activeGame, player));
                buttons.add(validTile);
            }
        }
        return buttons;
    }

    public static void offerBuildOrRemove(Player player, Game activeGame, GenericInteractionCreateEvent event, Tile tile) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        Button buildButton = Button.success(finChecker + "genericBuild_" + tile.getPosition(), "Build in " + tile.getRepresentationForButtons(activeGame, player));
        buttons.add(buildButton);
        Button remove = Button.danger(finChecker + "getDamageButtons_" + tile.getPosition(), "Remove or damage units in " + tile.getRepresentationForButtons(activeGame, player));
        buttons.add(remove);
        Button validTile2 = Button.secondary(finChecker + "deleteButtons", "Delete these buttons");
        buttons.add(validTile2);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Choose to either add units (build) or remove them", buttons);
    }

    public static void resolveCombatRoll(Player player, Game activeGame, GenericInteractionCreateEvent event, String buttonID) {
        String[] idInfo = buttonID.split("_");
        String pos = idInfo[1];
        String unitHolderName = idInfo[2];
        CombatRollType rollType = CombatRollType.combatround;
        if (idInfo.length > 3) {
            String rollTypeString = idInfo[3];
            switch (rollTypeString) {
                case "afb":
                    rollType = CombatRollType.AFB;
                    break;
                case "bombardment":
                    rollType = CombatRollType.bombardment;
                    break;
                case "spacecannonoffence":
                    rollType = CombatRollType.SpaceCannonOffence;
                    break;
                case "spacecannondefence":
                    rollType = CombatRollType.SpaceCannonDefence;
                    break;
                default:
                    break;
            }
        }
        new CombatRoll().secondHalfOfCombatRoll(player, activeGame, event, activeGame.getTileByPosition(pos), unitHolderName, new HashMap<>(), new ArrayList<>(), rollType);
    }

    public static MessageChannel getCorrectChannel(Player player, Game activeGame) {
        if (activeGame.isFoWMode()) {
            return player.getPrivateChannel();
        } else {
            return activeGame.getMainGameChannel();
        }
    }

    public static List<Button> getTilesToMoveFrom(Player player, Game activeGame, GenericInteractionCreateEvent event) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(activeGame.getTileMap()).entrySet()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tileEntry.getValue())
                && (!AddCC.hasCC(event, player.getColor(), tileEntry.getValue()) || nomadHeroAndDomOrbCheck(player, activeGame, tileEntry.getValue()))) {
                Tile tile = tileEntry.getValue();
                Button validTile = Button.success(finChecker + "tacticalMoveFrom_" + tileEntry.getKey(), tile.getRepresentationForButtons(activeGame, player));
                buttons.add(validTile);
            }
        }

        if (player.hasUnexhaustedLeader("saaragent")) {
            Button saarButton = Button.secondary("exhaustAgent_saaragent", "Use Saar Agent").withEmoji(Emoji.fromFormatted(Emojis.Saar));
            buttons.add(saarButton);
        }

        if (player.hasRelic("dominusorb")) {
            Button domButton = Button.secondary("dominusOrb", "Purge Dominus Orb");
            buttons.add(domButton);
        }

        if (player.hasUnexhaustedLeader("ghostagent") && FoWHelper.doesTileHaveWHs(activeGame, activeGame.getActiveSystem(), player)) {
            Button ghostButton = Button.secondary("exhaustAgent_ghostagent", "Use Ghost Agent").withEmoji(Emoji.fromFormatted(Emojis.Ghost));
            buttons.add(ghostButton);
        }
        String planet = "eko";
        if (player.getPlanets().contains(planet) && !player.getExhaustedPlanetsAbilities().contains(planet)) {
            buttons.add(Button.secondary(finChecker + "planetAbilityExhaust_" + planet, "Use Eko's Ability To Ignore Anomalies"));
        }

        Button validTile = Button.danger(finChecker + "concludeMove", "Done moving");
        buttons.add(validTile);
        Button validTile2 = Button.primary(finChecker + "ChooseDifferentDestination", "Activate a different system");
        buttons.add(validTile2);
        return buttons;
    }

    public static List<Button> moveAndGetLandingTroopsButtons(Player player, Game activeGame, ButtonInteractionEvent event) {
        String finChecker = "FFCC_" + player.getFaction() + "_";

        List<Button> buttons = new ArrayList<>();
        Map<String, Integer> displacedUnits = activeGame.getMovedUnitsFromCurrentActivation();
        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        if (!activeGame.getMovedUnitsFromCurrentActivation().isEmpty()) {
            tile = MoveUnits.flipMallice(event, tile, activeGame);
        }

        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not flip Mallice");
            return buttons;
        }
        int cc = player.getTacticalCC();

        if (!activeGame.getNaaluAgent() && !activeGame.getL1Hero() && !AddCC.hasCC(event, player.getColor(), tile)) {
            cc -= 1;
            player.setTacticalCC(cc);
            AddCC.addCC(event, player.getColor(), tile, true);
        }
        String thingToAdd = "box";
        for (String unit : displacedUnits.keySet()) {
            int amount = displacedUnits.get(unit);
            if (unit.contains("damaged")) {
                unit = unit.replace("damaged", "");
            }
            if ("box".equalsIgnoreCase(thingToAdd)) {
                thingToAdd = amount + " " + unit;
            } else {
                thingToAdd = thingToAdd + ", " + amount + " " + unit;
            }
        }
        if (!"box".equalsIgnoreCase(thingToAdd)) {
            new AddUnits().unitParsing(event, player.getColor(),
                tile, thingToAdd, activeGame);
        }
        for (String unit : displacedUnits.keySet()) {
            int amount = displacedUnits.get(unit);
            if (unit.contains("damaged")) {
                unit = unit.replace("damaged", "");
                UnitKey unitID = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor());
                tile.addUnitDamage("space", unitID, amount);
            }
        }

        activeGame.resetCurrentMovedUnitsFrom1TacticalAction();
        String colorID = Mapper.getColorID(player.getColor());
        UnitType inf = UnitType.Infantry;
        UnitType mech = UnitType.Mech;
        UnitType ff = UnitType.Fighter;
        UnitType fs = UnitType.Flagship;

        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null) {
                representation = name;
            }
            UnitHolder unitHolder = entry.getValue();
            if (unitHolder instanceof Planet planet) {
                int limit;

                if (tile.getUnitHolders().get("space").getUnits() != null && tile.getUnitHolders().get("space").getUnitCount(inf, colorID) > 0) {
                    limit = tile.getUnitHolders().get("space").getUnitCount(inf, colorID);
                    for (int x = 1; x < limit + 1; x++) {
                        if (x > 2) {
                            break;
                        }
                        Button validTile2 = Button
                            .danger(finChecker + "landUnits_" + tile.getPosition() + "_" + x + "infantry_" + representation,
                                "Land " + x + " Infantry on " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame))
                            .withEmoji(Emoji.fromFormatted(Emojis.infantry));
                        buttons.add(validTile2);
                    }
                }
                if (planet.getUnitCount(inf, colorID) > 0) {
                    limit = planet.getUnitCount(inf, colorID);
                    for (int x = 1; x < limit + 1; x++) {
                        if (x > 2) {
                            break;
                        }
                        Button validTile2 = Button
                            .success(finChecker + "spaceUnits_" + tile.getPosition() + "_" + x + "infantry_" + representation,
                                "Undo Landing of " + x + " Infantry on " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame))
                            .withEmoji(Emoji.fromFormatted(Emojis.infantry));
                        buttons.add(validTile2);
                    }
                }

                if (tile.getUnitHolders().get("space").getUnits() != null && tile.getUnitHolders().get("space").getUnitCount(mech, colorID) > 0) {
                    limit = tile.getUnitHolders().get("space").getUnitCount(mech, colorID);
                    for (int x = 1; x < limit + 1; x++) {
                        if (x > 2) {
                            break;
                        }
                        String buttonID = finChecker + "landUnits_" + tile.getPosition() + "_" + x + "mech_" + representation;
                        String buttonText = "Land " + x + " Mech(s) on " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame);
                        Button validTile2 = Button.primary(buttonID, buttonText).withEmoji(Emoji.fromFormatted(Emojis.mech));
                        buttons.add(validTile2);
                    }
                }
                if (player.hasUnit("naalu_flagship") && tile.getUnitHolders().get("space").getUnits() != null && tile.getUnitHolders().get("space").getUnitCount(fs, colorID) > 0
                    && tile.getUnitHolders().get("space").getUnitCount(ff, colorID) > 0) {
                    limit = tile.getUnitHolders().get("space").getUnitCount(ff, colorID);
                    for (int x = 1; x < limit + 1; x++) {
                        if (x > 2) {
                            break;
                        }
                        String buttonID = finChecker + "landUnits_" + tile.getPosition() + "_" + x + "ff_" + representation;
                        String buttonText = "Land " + x + " Fighter(s) on " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame);
                        Button validTile2 = Button.primary(buttonID, buttonText).withEmoji(Emoji.fromFormatted(Emojis.fighter));
                        buttons.add(validTile2);
                    }
                }

                if (planet.getUnitCount(mech, colorID) > 0) {
                    for (int x = 1; x <= planet.getUnitCount(mech, colorID); x++) {
                        if (x > 2) {
                            break;
                        }
                        String buttonID = finChecker + "spaceUnits_" + tile.getPosition() + "_" + x + "mech_" + representation;
                        String buttonText = "Undo Landing of " + x + " Mech(s) on " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame);
                        Button validTile2 = Button.primary(buttonID, buttonText).withEmoji(Emoji.fromFormatted(Emojis.mech));
                        buttons.add(validTile2);
                    }
                }
            }
        }
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "sardakkcommander")) {
            buttons.addAll(ButtonHelperCommanders.getSardakkCommanderButtons(activeGame, player, event));
        }
        if (player.getPromissoryNotes().keySet().contains("ragh")) {
            buttons.addAll(ButtonHelperFactionSpecific.getRaghsCallButtons(player, activeGame, tile));
        }
        Button rift = Button.success(finChecker + "getRiftButtons_" + tile.getPosition(), "Rift some units").withEmoji(Emoji.fromFormatted(Emojis.GravityRift));
        buttons.add(rift);
        if (player.hasAbility("combat_drones") && FoWHelper.playerHasFightersInSystem(player, tile)) {
            Button combatDrones = Button.primary(finChecker + "combatDrones", "Use Combat Drones Ability");
            buttons.add(combatDrones);
        }
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "ghostcommander")) {
            Button ghostC = Button.primary(finChecker + "placeGhostCommanderFF_" + tile.getPosition(), "Place fighter with Ghost Commander")
                .withEmoji(Emoji.fromFormatted(Emojis.Ghost));
            buttons.add(ghostC);
        } //"purgeSardakkHero"
        if (player.hasLeaderUnlocked("muaathero") && !tile.getTileID().equalsIgnoreCase("18") && ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, player, UnitType.Warsun).contains(tile)) {
            Button muaatH = Button.primary(finChecker + "novaSeed_" + tile.getPosition(), "Nova Seed This Tile")
                .withEmoji(Emoji.fromFormatted(Emojis.Muaat));
            buttons.add(muaatH);
        }
        if (player.hasLeaderUnlocked("sardakkhero")) {
            Button sardakkH = Button.primary(finChecker + "purgeSardakkHero", "Use Sardakk Hero")
                .withEmoji(Emoji.fromFormatted(Emojis.Sardakk));
            buttons.add(sardakkH);
        }
        if (tile.getUnitHolders().size() > 1 && ButtonHelper.getTilesOfUnitsWithBombard(player, activeGame).contains(tile)) {
            if (tile.getUnitHolders().size() > 2) {
                buttons.add(Button.secondary("bombardConfirm_combatRoll_" + tile.getPosition() + "_space_" + CombatRollType.bombardment, "Roll Bombardment"));
            } else {
                buttons.add(Button.secondary("combatRoll_" + tile.getPosition() + "_space_" + CombatRollType.bombardment, "Roll Bombardment"));
            }

        }
        Button concludeMove = Button.secondary(finChecker + "doneLanding", "Done landing troops");
        buttons.add(concludeMove);
        if (player.getLeaderIDs().contains("naazcommander") && !player.hasLeaderUnlocked("naazcommander")) {
            commanderUnlockCheck(player, activeGame, "naaz", event);
        }
        if (player.getLeaderIDs().contains("empyreancommander") && !player.hasLeaderUnlocked("empyreancommander")) {
            commanderUnlockCheck(player, activeGame, "empyrean", event);
        }
        if (player.getLeaderIDs().contains("ghostcommander") && !player.hasLeaderUnlocked("ghostcommander")) {
            commanderUnlockCheck(player, activeGame, "ghost", event);
        }
        
        return buttons;
    }

    public static String putInfWithMechsForStarforge(String pos, String successMessage, Game activeGame, Player player, ButtonInteractionEvent event) {

        Set<String> tiles = FoWHelper.getAdjacentTiles(activeGame, pos, player, true);
        tiles.add(pos);
        StringBuilder successMessageBuilder = new StringBuilder(successMessage);
        for (String tilePos : tiles) {
            Tile tile = activeGame.getTileByPosition(tilePos);
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {

                String colorID = Mapper.getColorID(player.getColor());
                UnitType mech = UnitType.Mech;
                if (unitHolder.getUnits() != null) {
                    if (unitHolder.getUnitCount(mech, colorID) > 0) {
                        int numMechs = unitHolder.getUnitCount(mech, colorID);
                        String planetName = "";
                        if (!"space".equalsIgnoreCase(unitHolder.getName())) {
                            planetName = " " + unitHolder.getName();
                        }
                        new AddUnits().unitParsing(event, player.getColor(), tile, numMechs + " infantry" + planetName, activeGame);

                        successMessageBuilder.append("\n Put ").append(numMechs).append(" ").append(Emojis.infantry).append(" with the mechs in ")
                            .append(tile.getRepresentationForButtons(activeGame, player));
                    }
                }
            }
        }
        successMessage = successMessageBuilder.toString();

        return successMessage;

    }

    public static List<Button> landAndGetBuildButtons(Player player, Game activeGame, ButtonInteractionEvent event) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        // Map<String, Integer> displacedUnits = activeGame.getCurrentMovedUnitsFrom1System();
        // Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());

        // for(String unit :displacedUnits.keySet()){
        //     int amount = displacedUnits.get(unit);
        //     String[] combo = unit.split("_");
        //     if(combo.length < 2){
        //         continue;
        //     }
        //     combo[1] = combo[1].toLowerCase().replace(" ", "");
        //     combo[1] = combo[1].replace("'", "");
        //     if(combo[0].contains("damaged")){
        //         combo[0]=combo[0].replace("damaged","");
        //         new AddUnits().unitParsing(event, player.getColor(),
        //             tile, amount +" " +combo[0]+" "+combo[1], activeMap);
        //         tile.addUnitDamage(combo[1], combo[0],amount);
        //     }else{
        //          new AddUnits().unitParsing(event, player.getColor(),
        //         tile, amount +" " +combo[0]+" "+combo[1], activeMap);
        //     }

        //     String key = Mapper.getUnitID(AliasHandler.resolveUnit(combo[0]), player.getColor());
        //     tile.removeUnit("space",key, amount);
        // }
        activeGame.resetCurrentMovedUnitsFrom1System();
        Button buildButton = Button.success(finChecker + "tacticalActionBuild_" + activeGame.getActiveSystem(), "Build in this system");
        buttons.add(buildButton);
        Button rift = Button.success(finChecker + "getRiftButtons_" + tile.getPosition(), "Rift some units").withEmoji(Emoji.fromFormatted(Emojis.GravityRift));
        buttons.add(rift);
        if (player.hasUnexhaustedLeader("sardakkagent")) {
            buttons.addAll(ButtonHelperAgents.getSardakkAgentButtons(activeGame, player));
        }
        if (player.hasUnexhaustedLeader("nomadagentmercer")) {
            buttons.addAll(ButtonHelperAgents.getMercerAgentInitialButtons(activeGame, player));
        }
        Button concludeMove = Button.danger(finChecker + "doneWithTacticalAction", "Conclude tactical action (will DET if applicable)");
        buttons.add(concludeMove);
        return buttons;
    }

    public static void resolveTransitDiodesStep1(Game activeGame, Player player, ButtonInteractionEvent event){
        List<Button> buttons = new ArrayList<>();
        for(String planet : player.getPlanetsAllianceMode()){
            if(planet.toLowerCase().contains("custodia")|| planet.contains("ghoti")){
                continue;
            }
            buttons.add(Button.success("transitDiodes_"+planet, Helper.getPlanetRepresentation(planet, activeGame)));
        }
        buttons.add(Button.danger("deleteButtons", "Done resolving transit diodes"));
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation()+" use buttons to choose the planet you want to move troops too", buttons);
    }
    public static void resolveTransitDiodesStep2(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID){
        List<Button> buttons = getButtonsForMovingGroundForcesToAPlanet(activeGame, buttonID.split("_")[1], player);
        deleteTheOneButton(event);
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation()+" use buttons to choose the troops you want to move to "+Helper.getPlanetRepresentation(buttonID.split("_")[1], activeGame), buttons);
    }

    public static List<Button> getButtonsForMovingGroundForcesToAPlanet(Game activeGame, String planetName, Player player){
        List<Button> buttons = new ArrayList<>();
        for(Tile tile : activeGame.getTileMap().values()){
            for(UnitHolder uH : tile.getUnitHolders().values()){
                if(uH.getUnitCount(UnitType.Infantry, player.getColor()) > 0){
                    if(uH instanceof Planet){
                        buttons.add(Button.success("mercerMove_" + planetName + "_" +tile.getPosition()+"_"+ uH.getName() + "_infantry",
                        "Move Infantry from " + Helper.getPlanetRepresentation(uH.getName(), activeGame) + " to " + Helper.getPlanetRepresentation(planetName, activeGame)));
                    }else{
                        buttons.add(Button.success("mercerMove_" + planetName + "_" +tile.getPosition()+"_"+ uH.getName()+ "_infantry",
                        "Move Infantry from space of " + tile.getRepresentation() + " to " + Helper.getPlanetRepresentation(planetName, activeGame)));
                    }
                }
                if(uH.getUnitCount(UnitType.Mech, player.getColor()) > 0){
                    if(uH instanceof Planet){
                        buttons.add(Button.success("mercerMove_" + planetName + "_" +tile.getPosition()+"_"+ uH.getName() + "_mech",
                        "Move Mech from " + Helper.getPlanetRepresentation(uH.getName(), activeGame) + " to " + Helper.getPlanetRepresentation(planetName, activeGame)));
                    }else{
                        buttons.add(Button.success("mercerMove_" + planetName + "_" +tile.getPosition()+"_"+ uH.getName()+ "_mech",
                        "Move Mech from space of " + tile.getRepresentation() + " to " + Helper.getPlanetRepresentation(planetName, activeGame)));
                    }
                }
                if(player.hasUnit("titans_pds") || player.hasTech("ht2")){
                    if(uH.getUnitCount(UnitType.Pds, player.getColor()) > 0){
                        if(uH instanceof Planet){
                            buttons.add(Button.success("mercerMove_" + planetName + "_" +tile.getPosition()+"_"+ uH.getName() + "_pds",
                            "Move PDS from " + Helper.getPlanetRepresentation(uH.getName(), activeGame) + " to " + Helper.getPlanetRepresentation(planetName, activeGame)));
                        }else{
                            buttons.add(Button.success("mercerMove_" + planetName + "_" +tile.getPosition()+"_"+ uH.getName()+ "_pds",
                            "Move PDS from space of " + tile.getRepresentation() + " to " + Helper.getPlanetRepresentation(planetName, activeGame)));
                        }
                    }
                }
            }
        }
        buttons.add(Button.danger("deleteButtons", "Done moving to this planet"));
        return buttons;
    }

    public static void offerSetAutoPassOnSaboButtons(Game activeGame) {
        List<Button> buttons = new ArrayList<>();
        int x = 1;
        buttons.add(Button.secondary("setAutoPassMedian_" + x, "" + x));
        x = 2;
        buttons.add(Button.secondary("setAutoPassMedian_" + x, "" + x));
        x = 4;
        buttons.add(Button.secondary("setAutoPassMedian_" + x, "" + x));
        x = 6;
        buttons.add(Button.secondary("setAutoPassMedian_" + x, "" + x));
        x = 8;
        buttons.add(Button.secondary("setAutoPassMedian_" + x, "" + x));
        x = 16;
        buttons.add(Button.secondary("setAutoPassMedian_" + x, "" + x));
        x = 24;
        buttons.add(Button.secondary("setAutoPassMedian_" + x, "" + x));
        x = 36;
        buttons.add(Button.secondary("setAutoPassMedian_" + x, "" + x));
        buttons.add(Button.danger("deleteButtons", "Decline"));
        x = 0;
        buttons.add(Button.danger("setAutoPassMedian_" + x, "Turn off"));
        for (Player player : activeGame.getRealPlayers()) {
            String message = getTrueIdentity(player, activeGame)
                + " you can choose to automatically pass on sabo's after a random amount of time if you don't have a sabo/instinct training/watcher mechs. How it works is you secretly set a median time (in hours) here, and then from now on when an AC is played, the bot will randomly react for you, 50% of the time being above that amount of time and 50% below. It's random so people cant derive much information from it. You are free to decline, noone will ever know either way, but if necessary you can change your time later with /player stats";
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
        }
    }

    public static UnitHolder getUnitHolderFromPlanetName(String planetName, Game activeGame) {
        Tile tile = activeGame.getTileFromPlanet(AliasHandler.resolvePlanet(planetName));
        if (tile == null) {
            return null;
        }
        return tile.getUnitHolders().get(planetName);
    }

    public static String getIdent(Player player) {
        return player.getFactionEmoji();
    }

    public static String getIdentOrColor(Player player, Game activeGame) {
        if (activeGame.isFoWMode()) {
            return StringUtils.capitalize(player.getColor());
        }
        return player.getFactionEmoji();
    }

    public static String buildMessageFromDisplacedUnits(Game activeGame, boolean landing, Player player, String moveOrRemove) {
        String message;
        HashMap<String, Integer> displacedUnits = activeGame.getCurrentMovedUnitsFrom1System();
        String prefix = " > " + player.getFactionEmoji();

        StringBuilder messageBuilder = new StringBuilder();
        for (String unit : displacedUnits.keySet()) {
            int amount = displacedUnits.get(unit);
            String damagedMsg = "";
            if (unit.contains("damaged")) {
                unit = unit.replace("damaged", "");
                damagedMsg = " damaged ";
            }
            String planet = null;
            if (unit.contains("_")) {
                planet = unit.substring(unit.lastIndexOf("_") + 1);
                unit = unit.replace("_" + planet, "");
            }
            if (landing) {
                messageBuilder.append(prefix).append(" Landed ").append(amount).append(" ").append(damagedMsg).append(Emojis.getEmojiFromDiscord(unit.toLowerCase()));
                if (planet == null) {
                    messageBuilder.append("\n");
                } else {
                    messageBuilder.append(" on the planet ").append(Helper.getPlanetRepresentation(planet.toLowerCase(), activeGame)).append("\n");
                }
            } else {
                messageBuilder.append(prefix).append(" ").append(moveOrRemove).append("d ").append(amount).append(" ").append(damagedMsg).append(Emojis.getEmojiFromDiscord(unit.toLowerCase()));
                if (planet == null) {
                    messageBuilder.append("\n");
                } else {
                    messageBuilder.append(" from the planet ").append(Helper.getPlanetRepresentation(planet.toLowerCase(), activeGame)).append("\n");
                }
            }

        }
        message = messageBuilder.toString();
        if ("".equalsIgnoreCase(message)) {
            message = "Nothing moved.";
        }
        return message;
    }

    public static List<LayoutComponent> turnButtonListIntoActionRowList(List<Button> buttons) {
        List<LayoutComponent> list = new ArrayList<>();
        List<ItemComponent> buttonRow = new ArrayList<>();
        for (Button button : buttons) {
            if (buttonRow.size() == 5) {
                list.add(ActionRow.of(buttonRow));
                buttonRow = new ArrayList<>();
            }
            if(buttons.size() < 26 || !button.getId().contains("_2")){
                buttonRow.add(button);
            }
        }
        if (buttonRow.size() > 0) {
            list.add(ActionRow.of(buttonRow));
        }
        
        return list;
    }

    public static String getUnitName(String id) {
        return switch (id) {
            case "fs" -> "flagship";
            case "ws" -> "warsun";
            case "gf" -> "infantry";
            case "mf" -> "mech";
            case "sd" -> "spacedock";
            case "csd" -> "cabalspacedock";
            case "pd" -> "pds";
            case "ff" -> "fighter";
            case "ca", "cr" -> "cruiser";
            case "dd" -> "destroyer";
            case "cv" -> "carrier";
            case "dn" -> "dreadnought";
            default -> "";
        };
    }

    public static List<Button> getButtonsForRiftingUnitsInSystem(Player player, Game activeGame, Tile tile) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();

        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            HashMap<UnitKey, Integer> units = unitHolder.getUnits();

            if (unitHolder instanceof Planet) {
            } else {
                for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                    UnitKey key = unitEntry.getKey();
                    if (!player.unitBelongsToPlayer(key)) continue;

                    UnitModel unitModel = player.getUnitFromUnitKey(key);
                    if (unitModel == null) continue;

                    UnitType unitType = key.getUnitType();
                    if ((!activeGame.playerHasLeaderUnlockedOrAlliance(player, "sardakkcommander") && (unitType.equals(UnitType.Infantry) || unitType.equals(UnitType.Mech)))
                        || (!player.hasFF2Tech() && unitType.equals(UnitType.Fighter))) {
                        continue;
                    }

                    String asyncID = key.asyncID();
                    asyncID = getUnitName(asyncID);

                    int totalUnits = unitEntry.getValue();

                    int damagedUnits = 0;
                    if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null) {
                        damagedUnits = unitHolder.getUnitDamage().get(key);
                    }
                    EmojiUnion emoji = Emoji.fromFormatted(unitModel.getUnitEmoji());
                    for (int x = 1; x < damagedUnits + 1 && x <= 2; x++) {
                        Button validTile2 = Button.danger(finChecker + "riftUnit_" + tile.getPosition() + "_" + x + asyncID + "damaged", "Rift " + x + " damaged " + unitModel.getBaseType());
                        if (emoji != null) validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);
                    }
                    totalUnits = totalUnits - damagedUnits;
                    for (int x = 1; x < totalUnits + 1 && x <= 2; x++) {
                        Button validTile2 = Button.danger(finChecker + "riftUnit_" + tile.getPosition() + "_" + x + asyncID, "Rift " + x + " " + unitModel.getBaseType());
                        if (emoji != null) validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);
                    }
                }
            }
        }
        Button concludeMove;
        Button doAll;
        Button concludeMove1;

        doAll = Button.secondary(finChecker + "riftAllUnits_" + tile.getPosition(), "Rift all units");
        concludeMove1 = Button.primary("getDamageButtons_" + tile.getPosition(), "Remove excess inf/ff");
        concludeMove = Button.danger("deleteButtons", "Done rifting units and removing excess capacity");

        buttons.add(doAll);
        buttons.add(concludeMove1);
        buttons.add(concludeMove);

        return buttons;
    }

    public static List<Button> getButtonsForAllUnitsInSystem(Player player, Game activeGame, Tile tile, String moveOrRemove) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();

        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null) {
                representation = name;
            }
            UnitHolder unitHolder = entry.getValue();
            HashMap<UnitKey, Integer> units = unitHolder.getUnits();

            if (unitHolder instanceof Planet planet) {
                for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                    if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                    UnitKey unitKey = unitEntry.getKey();
                    representation = representation.replace(" ", "").toLowerCase().replace("'", "").replace("-", "");
                    if ((unitKey.getUnitType().equals(UnitType.Infantry) || unitKey.getUnitType().equals(UnitType.Mech))) {
                        String unitName = getUnitName(unitKey.asyncID());
                        for (int x = 1; x < unitEntry.getValue() + 1; x++) {
                            if (x > 2) {
                                break;
                            }

                            String buttonID = finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_" + x + unitName + "_" + representation;
                            String buttonText = moveOrRemove + " " + x + " " + unitKey.unitName() + " from " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame);
                            String buttonEmoji = unitKey.unitEmoji();
                            Button validTile2 = Button.danger(buttonID, buttonText).withEmoji(Emoji.fromFormatted(buttonEmoji));
                            buttons.add(validTile2);
                        }
                    }
                }

            } else {
                for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                    if (!(player.unitBelongsToPlayer(unitEntry.getKey()))) continue;

                    UnitKey unitKey = unitEntry.getKey();
                    String unitName = getUnitName(unitKey.asyncID());
                    int totalUnits = unitEntry.getValue();
                    int damagedUnits = 0;

                    if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                        damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                    }
                    EmojiUnion emoji = Emoji.fromFormatted(unitKey.unitEmoji());
                    for (int x = 1; x < damagedUnits + 1 && x <= 2; x++) {
                        String buttonID = finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_" + x + unitName + "damaged";
                        String buttonText = moveOrRemove + " " + x + " damaged " + unitKey.unitName();
                        Button validTile2 = Button.danger(buttonID, buttonText);
                        if (emoji != null) validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);
                    }
                    totalUnits = totalUnits - damagedUnits;
                    for (int x = 1; x < totalUnits + 1 && x <= 2; x++) {
                        String buttonID = finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_" + x + unitName;
                        String buttonText = moveOrRemove + " " + x + " " + unitKey.unitName();
                        Button validTile2 = Button.danger(buttonID, buttonText);
                        if (emoji != null) validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);
                    }
                }
            }

        }
        Button concludeMove;
        Button doAll;
        Button doAllShips;
        if ("Remove".equalsIgnoreCase(moveOrRemove)) {
            doAllShips = Button.secondary(finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_removeAllShips", "Remove all Ships");
             buttons.add(doAllShips);
            doAll = Button.secondary(finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_removeAll", "Remove all units");
            concludeMove = Button.primary(finChecker + "doneRemoving", "Done removing units");
        } else {
            doAll = Button.secondary(finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_moveAll", "Move all units");
            concludeMove = Button.primary(finChecker + "doneWithOneSystem_" + tile.getPosition(), "Done moving units from this system");
        }
        buttons.add(doAll);
        buttons.add(concludeMove);
        HashMap<String, Integer> displacedUnits = activeGame.getCurrentMovedUnitsFrom1System();
        for (String unit : displacedUnits.keySet()) {
            String unitkey;
            String planet = "";
            String origUnit = unit;
            String damagedMsg = "";
            if (unit.contains("damaged")) {
                unit = unit.replace("damaged", "");
                damagedMsg = " damaged ";
            }
            if (unit.contains("_")) {
                unitkey = unit.split("_")[0];
                planet = unit.split("_")[1];
            } else {
                unitkey = unit;
            }
            for (int x = 1; x < displacedUnits.get(origUnit) + 1; x++) {
                if (x > 2) {
                    break;
                }
                String blabel = "Undo move of " + x + " " + damagedMsg + unitkey;
                if (!"".equalsIgnoreCase(planet)) {
                    blabel = blabel + " from " + Helper.getPlanetRepresentation(planet.toLowerCase(), activeGame);
                }
                Button validTile2 = Button.success(
                    finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_" + x + unit.toLowerCase().replace(" ", "").replace("'", "") + damagedMsg.replace(" ", "") + "_reverse",
                    blabel).withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord(unitkey.toLowerCase().replace(" ", ""))));
                buttons.add(validTile2);
            }
        }
        if (displacedUnits.keySet().size() > 0) {
            Button validTile2 = Button.success(finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_reverseAll", "Undo all");
            buttons.add(validTile2);
        }
        return buttons;
    }

    public static List<Button> getButtonsForRemovingAllUnitsInSystem(Player player, Game activeGame, Tile tile) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null) {
                representation = name;
            }
            UnitHolder unitHolder = entry.getValue();
            HashMap<UnitKey, Integer> units = unitHolder.getUnits();
            if (unitHolder instanceof Planet planet) {
                for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                    if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                    UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                    if (unitModel == null) continue;

                    UnitKey unitKey = unitEntry.getKey();
                    String unitName = getUnitName(unitKey.asyncID());

                    int damagedUnits = 0;
                    if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                        damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                    }
                    int totalUnits = unitEntry.getValue() - damagedUnits;

                    EmojiUnion emoji = Emoji.fromFormatted(unitModel.getUnitEmoji());
                    for (int x = 1; x < totalUnits + 1 && x < 3; x++) {
                        String buttonID = finChecker + "assignHits_" + tile.getPosition() + "_" + x + unitName + "_" + representation;
                        String buttonText = "Remove " + x + " " + unitModel.getBaseType() + " from " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame);
                        Button validTile2 = Button.danger(buttonID, buttonText);
                        if (emoji != null) validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);

                        if (unitModel.getSustainDamage()) {
                            buttonID = finChecker + "assignDamage_" + tile.getPosition() + "_" + x + unitName + "_" + representation;
                            buttonText = "Sustain " + x + " " + unitModel.getBaseType() + " from " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame);
                            Button validTile3 = Button.secondary(buttonID, buttonText);
                            if (emoji != null) validTile2 = validTile2.withEmoji(emoji);
                            buttons.add(validTile3);
                        }
                    }
                    for (int x = 1; x < damagedUnits + 1 && x < 2; x++) {
                        String buttonID = finChecker + "assignHits_" + tile.getPosition() + "_" + x + unitName + "_" + representation + "damaged";
                        String buttonText = "Remove " + x + " damaged " + unitModel.getBaseType() + " from " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame);
                        Button validTile2 = Button.danger(buttonID, buttonText);
                        if (emoji != null) validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);
                    }
                }
            } else {
                for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                    if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                    UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                    if (unitModel == null) continue;

                    UnitKey key = unitEntry.getKey();
                    String unitName = getUnitName(key.asyncID());
                    int totalUnits = unitEntry.getValue();
                    int damagedUnits = 0;

                    if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null) {
                        damagedUnits = unitHolder.getUnitDamage().get(key);
                    }
                    totalUnits = totalUnits - damagedUnits;

                    EmojiUnion emoji = Emoji.fromFormatted(unitModel.getUnitEmoji());
                    for (int x = 1; x < damagedUnits + 1 && x < 2; x++) {
                        Button validTile2 = Button.danger(finChecker + "assignHits_" + tile.getPosition() + "_" + x + unitName + "damaged",
                            "Remove " + x + " damaged " + unitModel.getBaseType());
                        if (emoji != null) validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);
                    }
                    for (int x = 1; x < totalUnits + 1 && x < 3; x++) {
                        Button validTile2 = Button.danger(finChecker + "assignHits_" + tile.getPosition() + "_" + x + unitName, "Remove " + x + " " + unitModel.getBaseType());
                        if (emoji != null) validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);
                    }
                    
                    if ((("mech".equalsIgnoreCase(unitName) && !activeGame.getLaws().containsKey("articles_war") && player.getUnitsOwned().contains("nomad_mech"))
                        || "dreadnought".equalsIgnoreCase(unitName) || "warsun".equalsIgnoreCase(unitName) || "flagship".equalsIgnoreCase(unitName) || ("mech".equalsIgnoreCase(unitName) && ButtonHelper.doesPlayerHaveFSHere("nekro_flagship", player, tile))
                        || ("cruiser".equalsIgnoreCase(unitName) && player.hasTech("se2")) || ("carrier".equalsIgnoreCase(unitName) && player.hasTech("ac2"))) && totalUnits > 0) {
                        Button validTile2 = Button
                            .secondary(finChecker + "assignDamage_" + tile.getPosition() + "_" + 1 + unitName, "Sustain " + 1 + " " + unitModel.getBaseType());
                        if (emoji != null) validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);
                    }
                }
            }
        }
        Button doAllShips;
        doAllShips = Button.secondary(finChecker + "assignHits_" + tile.getPosition() + "_AllShips", "Remove all Ships");
        buttons.add(doAllShips);
        Button doAll = Button.secondary(finChecker + "assignHits_" + tile.getPosition() + "_All", "Remove all units");
        Button concludeMove = Button.primary("deleteButtons", "Done removing/sustaining units");
        buttons.add(doAll);
        buttons.add(concludeMove);
        return buttons;
    }
    public static List<Button> getUserSetupButtons(Game activeGame){
        List<Button> buttons = new ArrayList<>();
        for (Player player : activeGame.getPlayers().values()) {
            String userId = player.getUserID();
            buttons.add(Button.success("setupStep1_"+userId, player.getUserName()));
        }
        return buttons;
    }

    public static void setUpFrankenFactions(Game activeGame, GenericInteractionCreateEvent event){
        List<Player> players = new ArrayList<Player>();
        players.addAll(activeGame.getPlayers().values());
        int x = 1;
        for (Player player : players) {
            if(x < 9){
                switch(x){
                    case 1 ->{new Setup().secondHalfOfPlayerSetup(player, activeGame, "black", "franken1", "201", event, false);}
                    case 2 ->{new Setup().secondHalfOfPlayerSetup(player, activeGame, "green", "franken2", "202", event, false);}
                    case 3 ->{new Setup().secondHalfOfPlayerSetup(player, activeGame, "purple", "franken3", "203", event, false);}
                    case 4 ->{new Setup().secondHalfOfPlayerSetup(player, activeGame, "orange", "franken4", "204", event, false);}
                    case 5 ->{new Setup().secondHalfOfPlayerSetup(player, activeGame, "pink", "franken5", "205", event, false);}
                    case 6 ->{new Setup().secondHalfOfPlayerSetup(player, activeGame, "yellow", "franken6", "206", event, false);}
                    case 7 ->{new Setup().secondHalfOfPlayerSetup(player, activeGame, "red", "franken7", "207", event, false);}
                    case 8 ->{new Setup().secondHalfOfPlayerSetup(player, activeGame, "blue", "franken8", "208", event, false);}
                    default ->{

                    }
                }
            }
            x++;
        }
    }

    public static List<Button> getFactionSetupButtons(Game activeGame, String buttonID){
        String userId = buttonID.split("_")[1];
        List<Button> buttons = new ArrayList<>();
        List<String> allFactions = FrankenDraft.getAllFactionIds(activeGame);
        for (var factionId : allFactions) {
            FactionModel faction  = Mapper.getFaction(factionId);
            if(faction != null && activeGame.getPlayerFromColorOrFaction(factionId) == null){
                String name = faction.getFactionName();

                if(factionId.contains("keleres")){
                    factionId = "keleres";
                    name = "The Council Keleres";
                }
                buttons.add(Button.success("setupStep2_"+userId+"_"+factionId, name).withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord(factionId))));
            }
        }
        return buttons;
    }
    public static List<Button> getColorSetupButtons(Game activeGame, String buttonID){
        List<Button> buttons = new ArrayList<>();
        String userId = buttonID.split("_")[1];
        String factionId = buttonID.split("_")[2];
        List<String> allColors = Mapper.getColors();
        for (String color : allColors) {
            if(activeGame.getPlayerFromColorOrFaction(color) == null){
                buttons.add(Button.success("setupStep3_"+userId+"_"+factionId+"_"+color, color));
            }
        }
        return buttons;
    }

    public static void offerPlayerSetupButtons(MessageChannel channel) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("startPlayerSetup", "Setup a Player"));
        MessageHelper.sendMessageToChannelWithButtons(channel, "After setting up the map, you can use this button instead of /player setup if you wish", buttons);
    }

    public static void resolveSetupStep0(Player player, Game activeGame, ButtonInteractionEvent event) {
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation() + "Please tell the bot which user you are setting up", getUserSetupButtons(activeGame));
    }

    public static void resolveSetupStep1(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        if (activeGame.isTestBetaFeaturesMode()) {
            SelectFaction.offerFactionSelectionMenu(event);
            return;
        } 
        
        String userId = buttonID.split("_")[1];
        event.getMessage().delete().queue();
        List<Button> buttons = getFactionSetupButtons(activeGame, buttonID);
        List<Button> newButtons = new ArrayList<>();
        int maxBefore = 0;
        for (int x = 0; x < buttons.size(); x++) {
            if (x > maxBefore && x < (maxBefore + 23)) {
                newButtons.add(buttons.get(x));
            }
        }
        newButtons.add(Button.secondary("setupStep2_" + userId + "_" + (maxBefore + 22) + "!", "Get more factions"));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please tell the bot the desired faction", newButtons);
    }

    public static void resolveSetupStep2(Player player, Game activeGame, GenericInteractionCreateEvent event, String buttonID){
        String userId = buttonID.split("_")[1];
        String factionId = buttonID.split("_")[2];
        if (event instanceof ButtonInteractionEvent) {
            ((ButtonInteractionEvent) event).getMessage().delete().queue();
        }
        if(factionId.contains("!")){
            List<Button> buttons = getFactionSetupButtons(activeGame, buttonID);
            List<Button> newButtons = new ArrayList<>();
            int maxBefore = Integer.parseInt(factionId.replace("!", ""));
            for(int x = 0; x < buttons.size(); x++){
                if(x > maxBefore && x < (maxBefore+23)){
                    newButtons.add(buttons.get(x));
                }
            }
            newButtons.add(Button.secondary("setupStep2_"+userId+"_"+(maxBefore+22)+"!", "Get more factions"));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Please tell the bot the desired faction", newButtons);
            return;
        }
        if(factionId.equalsIgnoreCase("keleres")){
            List<Button> newButtons = new ArrayList<>();
            newButtons.add(Button.success("setupStep2_"+userId+"_keleresa", "Keleres Argent").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("argent"))));
            newButtons.add(Button.success("setupStep2_"+userId+"_keleresm", "Keleres Mentak").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mentak"))));
            newButtons.add(Button.success("setupStep2_"+userId+"_keleresx", "Keleres Xxcha").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("xxcha"))));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Please tell the bot which flavor of keleres you are", newButtons);
            return;
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Setting up as faction: " + Mapper.getFaction(factionId).getFactionName());
        offerColourSetupButtons(activeGame, event, buttonID, userId, factionId);
        
    }

    private static void offerColourSetupButtons(Game activeGame, GenericInteractionCreateEvent event, String buttonID, String userId, String factionId) {
        List<Button> buttons = getColorSetupButtons(activeGame, buttonID);
        List<Button> newButtons = new ArrayList<>();
        int maxBefore = 0;
        for(int x = 0; x < buttons.size(); x++){
            if(x > maxBefore && x < (maxBefore+23)){
                newButtons.add(buttons.get(x));
            }
        }
        newButtons.add(Button.secondary("setupStep3_"+userId+"_"+factionId+"_"+(maxBefore+22)+"!", "Get more colors"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Please tell the bot the desired player color", newButtons);
    }

    public static List<Button> getTileSetupButtons(Game activeGame, String buttonID){
        List<Button> buttons = new ArrayList<>();
        String userId = buttonID.split("_")[1];
        String factionId = buttonID.split("_")[2];
        String color = buttonID.split("_")[3];
        
        for (Tile tile : activeGame.getTileMap().values()) {
            if(ButtonHelper.isTileHomeSystem(tile)){
                String rep = tile.getRepresentation();
                if(rep == null || rep.isEmpty()){
                    rep = tile.getTileID() + "("+tile.getPosition()+ ")";
                }
                buttons.add(Button.success("setupStep4_"+userId+"_"+factionId+"_"+color+"_"+tile.getPosition(), rep));
            }
        }
        return buttons;
    }

    public static List<Button> getSpeakerSetupButtons(Game activeGame, String buttonID){
        List<Button> buttons = new ArrayList<>();
        String userId = buttonID.split("_")[1];
        String factionId = buttonID.split("_")[2];
        String color = buttonID.split("_")[3];
        String pos = buttonID.split("_")[4];
        buttons.add(Button.success("setupStep5_"+userId+"_"+factionId+"_"+color+"_"+pos+"_yes", "Yes, setting up speaker"));
        buttons.add(Button.success("setupStep5_"+userId+"_"+factionId+"_"+color+"_"+pos+"_no", "No"));
        return buttons;
    }
    public static void resolveSetupStep3(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID){
         String userId = buttonID.split("_")[1];
        String factionId = buttonID.split("_")[2];
        String color = buttonID.split("_")[3];
        event.getMessage().delete().queue();
        if(color.contains("!")){
            List<Button> buttons = getColorSetupButtons(activeGame, buttonID);
            List<Button> newButtons = new ArrayList<>();
            int maxBefore = Integer.parseInt(color.replace("!", ""));
            for(int x = 0; x < buttons.size(); x++){
                if(x > maxBefore && x < (maxBefore+23)){
                    newButtons.add(buttons.get(x));
                }
            }
            newButtons.add(Button.secondary("setupStep3_"+userId+"_"+factionId+"_"+(maxBefore+22)+"!", "Get more color"));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please tell the bot the desired color", newButtons);
            return;
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Setting up as color: "+color);

        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please tell the bot the home system location", getTileSetupButtons(activeGame, buttonID));
        
    }

    public static void resolveSetupStep4And5(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID){
        String userId = buttonID.split("_")[1];
        String factionId = buttonID.split("_")[2];
        String color = buttonID.split("_")[3];
        String pos = buttonID.split("_")[4];
        Player speaker = null;
        player = activeGame.getPlayer(userId);
        if (activeGame.getPlayer(activeGame.getSpeaker()) != null) {
            speaker = activeGame.getPlayers().get(activeGame.getSpeaker());
        }
        if(buttonID.split("_").length == 6 || speaker != null){
            if(speaker != null){
                new Setup().secondHalfOfPlayerSetup(player, activeGame, color, factionId, pos, event, false);
            }else{
                new Setup().secondHalfOfPlayerSetup(player, activeGame, color, factionId, pos, event, buttonID.split("_")[5].equalsIgnoreCase("yes"));
            }
        }else{
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please tell the bot if the player is the speaker", getSpeakerSetupButtons(activeGame, buttonID));
        }
        event.getMessage().delete().queue();
    }

    public static void resolveStellar(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        StellarConverter.secondHalfOfStellar(activeGame, buttonID.split("_")[1], event);
        event.getMessage().delete().queue();
    }

    public static String getUnitHolderRep(UnitHolder unitHolder, Tile tile, Game activeGame) {
        String name = unitHolder.getName();
        if (name.equalsIgnoreCase("space")) {
            name = "Space Area of " + tile.getRepresentation();
        } else {
            if (unitHolder instanceof Planet planet) {
                name = Helper.getPlanetRepresentation(name, activeGame);
            }
        }
        return name;
    }

    public static Set<Tile> getTilesOfUnitsWithProduction(Player player, Game activeGame) {
        Set<Tile> tilesWithProduction = activeGame.getTileMap().values().stream()
            .filter(tile -> tile.containsPlayersUnitsWithModelCondition(player, unit -> unit.getProductionValue() > 0))
            .collect(Collectors.toSet());
        if (player.hasUnit("ghoti_flagship")) {
            tilesWithProduction.addAll(getTilesOfPlayersSpecificUnits(activeGame, player, UnitType.Flagship));
        }
        if (player.hasTech("mr") || player.hasTech("absol_mr")) {
            List<Tile> tilesWithNovaAndUnits = activeGame.getTileMap().values().stream()
                .filter(Tile::isSupernova)
                .filter(tile -> tile.containsPlayersUnits(player))
                .toList();
            tilesWithProduction.addAll(tilesWithNovaAndUnits);
        }
        return tilesWithProduction;
    }

    public static List<Tile> getTilesOfUnitsWithBombard(Player player, Game activeGame) {
        return activeGame.getTileMap().values().stream()
            .filter(tile -> tile.containsPlayersUnitsWithModelCondition(player, unit -> unit.getBombardDieCount() > 0))
            .toList();
    }

    public static List<Button> getButtonsForStellar(Player player, Game activeGame) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        List<Tile> tilesWithBombard = ButtonHelper.getTilesOfUnitsWithBombard(player, activeGame);
        Set<String> adjacentTiles = FoWHelper.getAdjacentTilesAndNotThisTile(activeGame, tilesWithBombard.get(0).getPosition(), player, false);
        for (Tile tile : tilesWithBombard) {
            adjacentTiles.addAll(FoWHelper.getAdjacentTilesAndNotThisTile(activeGame, tile.getPosition(), player, false));
        }
        for (String pos : adjacentTiles) {
            Tile tile = activeGame.getTileByPosition(pos);
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder instanceof Planet planet) {
                    if (!player.getPlanetsAllianceMode().contains(planet.getName()) && !ButtonHelper.isPlanetLegendaryOrHome(unitHolder.getName(), activeGame, false, player)
                        && !planet.getName().toLowerCase().contains("rex")) {
                        buttons.add(Button.success(finChecker + "stellarConvert_" + planet.getName(), "Stellar Convert " + Helper.getPlanetRepresentation(planet.getName(), activeGame)));
                    }
                }
            }
        }
        return buttons;
    }

    public static int getNumberOfGravRiftsPlayerIsIn(Player player, Game activeGame) {
        return (int) activeGame.getTileMap().values().stream().filter(tile -> tile.isGravityRift() && tile.containsPlayersUnits(player)).count();
    }

    public static List<Button> getButtonsForRepairingUnitsInASystem(Player player, Game activeGame, Tile tile) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null) {
                representation = name;
            }
            UnitHolder unitHolder = entry.getValue();
            HashMap<UnitKey, Integer> units = unitHolder.getUnits();

            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;

                UnitKey key = unitEntry.getKey();
                String unitName = getUnitName(key.asyncID());
                int damagedUnits = 0;

                if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(key);
                }

                EmojiUnion emoji = Emoji.fromFormatted(unitModel.getUnitEmoji());
                for (int x = 1; x < damagedUnits + 1 && x < 3; x++) {
                    String buttonID = finChecker + "repairDamage_" + tile.getPosition() + "_" + x + unitName;
                    String buttonText = "Repair " + x + " damaged " + unitModel.getBaseType();
                    if (unitHolder instanceof Planet) {
                        buttonID += "_" + representation;
                        buttonText += " from " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame);
                    }
                    Button validTile3 = Button.success(buttonID, buttonText);
                    if (emoji != null) validTile3 = validTile3.withEmoji(emoji);
                    buttons.add(validTile3);
                }
            }
        }
        Button concludeMove = Button.primary("deleteButtons", "Done repairing units");
        buttons.add(concludeMove);
        return buttons;
    }

    public static List<Player> tileHasPDS2Cover(Player player, Game activeGame, String tilePos) {
        Set<String> adjTiles = FoWHelper.getAdjacentTiles(activeGame, tilePos, player, false);
        List<Player> playersWithPds2 = new ArrayList<>();
        for (String adjTilePos : adjTiles) {
            Tile adjTile = activeGame.getTileByPosition(adjTilePos);
            if (adjTile == null) {
                BotLogger.log("`ButtonHelper.tileHasPDS2Cover` Game: " + activeGame.getName() + " Tile: " + tilePos + " has a null adjacent tile: `" + adjTilePos + "` within: `" + adjTiles.toString() + "`");
                continue;
            }
            for (UnitHolder unitHolder : adjTile.getUnitHolders().values()) {
                for (Map.Entry<UnitKey, Integer> unitEntry : unitHolder.getUnits().entrySet()) {
                    if (unitEntry.getValue() == 0) {
                        continue;
                    }

                    UnitKey unitKey = unitEntry.getKey();
                    Player owningPlayer = activeGame.getPlayerByColorID(unitKey.getColorID()).orElse(null);
                    if (owningPlayer == null || owningPlayer.getFaction().equals(player.getFaction()) || playersWithPds2.contains(owningPlayer)) {
                        continue;
                    }

                    UnitModel model = owningPlayer.getUnitFromUnitKey(unitKey);
                    if (model != null && model.getDeepSpaceCannon()) {
                        playersWithPds2.add(owningPlayer);
                    }
                }
            }
        }
        return playersWithPds2;
    }

    public static void fixRelics(Game activeGame) {
        for (Player player : activeGame.getPlayers().values()) {
            if (player != null && player.getRelics() != null) {
                List<String> rels = new ArrayList<>(player.getRelics());
                for (String relic : rels) {
                    if (relic.contains("extra")) {
                        player.removeRelic(relic);
                        relic = relic.replace("extra1", "");
                        relic = relic.replace("extra2", "");
                        player.addRelic(relic);
                    }
                }
            }
        }
    }

    public static void fixAllianceMembers(Game activeGame) {
        for (Player player : activeGame.getRealPlayers()) {
            player.setAllianceMembers("");
        }
    }

    public static void startMyTurn(GenericInteractionCreateEvent event, Game activeGame, Player player) {
        boolean isFowPrivateGame = FoWHelper.isPrivateGame(activeGame, event);
        String msg;
        String msgExtra = "";
        Player privatePlayer = player;

        Player nextPlayer = player;

        //INFORM FIRST PLAYER IS UP FOR ACTION
        if (nextPlayer != null) {
            msgExtra += "# " + nextPlayer.getRepresentation() + " is up for an action";
            privatePlayer = nextPlayer;
            activeGame.updateActivePlayer(nextPlayer);
            if (activeGame.isFoWMode()) {
                FoWHelper.pingAllPlayersWithFullStats(activeGame, event, nextPlayer, "started turn");
            }
            ButtonHelperFactionSpecific.resolveMilitarySupportCheck(nextPlayer, activeGame);
            ButtonHelperFactionSpecific.resolveKolleccAbilities(nextPlayer, activeGame);

            activeGame.setCurrentPhase("action");
        }

        msg = "";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        if (isFowPrivateGame) {
            msgExtra = "# " + Helper.getPlayerRepresentation(privatePlayer, activeGame, event.getGuild(), true) + " UP NEXT";
            String fail = "User for next faction not found. Report to ADMIN";
            String success = "The next player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(privatePlayer, activeGame, event, msgExtra, fail, success);
            activeGame.updateActivePlayer(privatePlayer);

            MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getPrivateChannel(), msgExtra + "\n Use Buttons to do turn.",
                getStartOfTurnButtons(privatePlayer, activeGame, false, event));
            if (privatePlayer.getStasisInfantry() > 0) {
                MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(privatePlayer, activeGame),
                    "Use buttons to revive infantry. You have " + privatePlayer.getStasisInfantry() + " infantry left to revive.", getPlaceStatusInfButtons(activeGame, privatePlayer));
            }

        } else {
            if (!msgExtra.isEmpty()) {
                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), msgExtra);
                MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), "\n Use Buttons to do turn.", getStartOfTurnButtons(privatePlayer, activeGame, false, event));
                if (privatePlayer.getStasisInfantry() > 0) {
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(privatePlayer, activeGame),
                        "Use buttons to revive infantry. You have " + privatePlayer.getStasisInfantry() + " infantry left to revive.", getPlaceStatusInfButtons(activeGame, privatePlayer));
                }
            }
        }
    }

    public static void resolveImperialArbiter(ButtonInteractionEvent event, Game activeGame, Player player) {
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdent(player) + " decided to use the Imperial Arbiter Law to swap SCs with someone");
        activeGame.removeLaw("arbiter");
        List<Button> buttons = ButtonHelperFactionSpecific.getSwapSCButtons(activeGame, "imperialarbiter", player);
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " choose who you want to swap SCs with",
            buttons);
        event.getMessage().delete().queue();
    }

    

    //playerHasUnitsInSystem(player, tile);
    public static void startActionPhase(GenericInteractionCreateEvent event, Game activeGame) {
        boolean isFowPrivateGame = FoWHelper.isPrivateGame(activeGame, event);
        String msg;
        String msgExtra = "";
        boolean allPicked = true;
        Player privatePlayer = null;
        Collection<Player> activePlayers = activeGame.getPlayers().values().stream()
            .filter(Player::isRealPlayer)
            .toList();
        Player nextPlayer = null;
        int lowestSC = 100;
        for(Player p2 : activeGame.getRealPlayers()){
            ButtonHelperActionCards.checkForAssigningCoup(activeGame,p2);
            if(activeGame.getFactionsThatReactedToThis("Play Naalu PN") != null && activeGame.getFactionsThatReactedToThis("Play Naalu PN").contains(p2.getFaction())){
                if(!p2.getPromissoryNotesInPlayArea().contains("gift") && p2.getPromissoryNotes().keySet().contains("gift")){
                    ButtonHelper.resolvePNPlay("gift", p2, activeGame, event);
                }
            }
        }
        msgExtra += Helper.getGamePing(event, activeGame) + "\nAll players picked SC";
        for (Player player_ : activePlayers) {
            int playersLowestSC = player_.getLowestSC();
            String scNumberIfNaaluInPlay = GenerateMap.getSCNumberIfNaaluInPlay(player_, activeGame, Integer.toString(playersLowestSC));
            if (scNumberIfNaaluInPlay.startsWith("0/")) {
                nextPlayer = player_; //no further processing, this player has the 0 token
                break;
            }
            if (playersLowestSC < lowestSC) {
                lowestSC = playersLowestSC;
                nextPlayer = player_;
            }
        }

        //INFORM FIRST PLAYER IS UP FOR ACTION
        if (nextPlayer != null) {
            msgExtra += " " + nextPlayer.getRepresentation() + " is up for an action";
            privatePlayer = nextPlayer;
            activeGame.updateActivePlayer(nextPlayer);
            if (activeGame.isFoWMode()) {
                FoWHelper.pingAllPlayersWithFullStats(activeGame, event, nextPlayer, "started turn");
            }
            ButtonHelperFactionSpecific.resolveMilitarySupportCheck(nextPlayer, activeGame);

            activeGame.setCurrentPhase("action");
        }

        msg = "";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        if (isFowPrivateGame) {
            msgExtra = "Start phase command run";
            String fail = "User for next faction not found. Report to ADMIN";
            String success = "The next player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(privatePlayer, activeGame, event, msgExtra, fail, success);
            if (privatePlayer == null) return;
            msgExtra = "# " + Helper.getPlayerRepresentation(privatePlayer, activeGame, event.getGuild(), true) + " UP NEXT";
            activeGame.updateActivePlayer(privatePlayer);

            if (!allPicked) {
                activeGame.setCurrentPhase("strategy");
                MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getPrivateChannel(), "Use Buttons to Pick SC", Helper.getRemainingSCButtons(event, activeGame, privatePlayer));
            } else {

                MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getPrivateChannel(), msgExtra + "\n Use Buttons to do turn.",
                    getStartOfTurnButtons(privatePlayer, activeGame, false, event));
                if (privatePlayer.getStasisInfantry() > 0) {
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(privatePlayer, activeGame),
                        "Use buttons to revive infantry. You have " + privatePlayer.getStasisInfantry() + " infantry left to revive.", getPlaceStatusInfButtons(activeGame, privatePlayer));
                }
            }

        } else {
            ListTurnOrder.turnOrder(event, activeGame);
            if (!msgExtra.isEmpty()) {
                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), msgExtra);
                if (privatePlayer == null) {
                    MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), "Could not find player.");
                    return;
                }
                MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), "\n Use Buttons to do turn.", getStartOfTurnButtons(privatePlayer, activeGame, false, event));
                if (privatePlayer.getStasisInfantry() > 0) {
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(privatePlayer, activeGame),
                        "Use buttons to revive infantry. You have " + privatePlayer.getStasisInfantry() + " infantry left to revive.", getPlaceStatusInfButtons(activeGame, privatePlayer));
                }
            }
        }
        if (allPicked) {
            for (Player p2 : activeGame.getRealPlayers()) {
                List<Button> buttons = new ArrayList<Button>();
                if (p2.hasTechReady("qdn") && p2.getTg() > 2 && p2.getStrategicCC() > 0) {
                    buttons.add(Button.success("startQDN", "Use Quantum Datahub Node"));
                    buttons.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame), ButtonHelper.getTrueIdentity(p2, activeGame) + " you have the opportunity to use QDN",
                        buttons);
                }
                buttons = new ArrayList<Button>();
                if (activeGame.getLaws().containsKey("arbiter") && activeGame.getLawsInfo().get("arbiter").equalsIgnoreCase(p2.getFaction())) {
                    buttons.add(Button.success("startArbiter", "Use Imperial Arbiter"));
                    buttons.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame),
                        ButtonHelper.getTrueIdentity(p2, activeGame) + " you have the opportunity to use Imperial Arbiter", buttons);
                }
            }
        }
    }

    public static void startStatusHomework(GenericInteractionCreateEvent event, Game activeGame) {
        int playersWithSCs = 0;
        activeGame.setCurrentPhase("statusHomework");
        for (Player player2 : activeGame.getPlayers().values()) {
            if (playersWithSCs > 0) {
                new Cleanup().runStatusCleanup(activeGame);
                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), Helper.getGamePing(activeGame.getGuild(), activeGame) + "Status Cleanup Run!");
                playersWithSCs = -30;
                if (!activeGame.isFoWMode()) {
                    DisplayType displayType = DisplayType.map;
                    FileUpload stats_file = GenerateMap.getInstance().saveImage(activeGame, displayType, event);
                    MessageHelper.sendFileUploadToChannel(activeGame.getActionsChannel(), stats_file);
                }
            }
            if (player2.isRealPlayer()) {
                if (player2.getSCs() != null && player2.getSCs().size() > 0
                    && !player2.getSCs().contains(0)) {
                    playersWithSCs = playersWithSCs + 1;
                }
            } else {
                continue;
            }

            Leader playerLeader = player2.getLeader("naaluhero").orElse(null);

            if (player2.hasLeader("naaluhero") && player2.getLeaderByID("naaluhero").isPresent()
                && playerLeader != null && !playerLeader.isLocked()) {
                List<Button> buttons = new ArrayList<Button>();
                buttons.add(Button.success("naaluHeroInitiation", "Play Naalu Hero"));
                buttons.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(player2.getCardsInfoThread(),
                    player2.getRepresentation()
                        + " Reminder this is the window to do Naalu Hero. You can use the buttons to start the process",
                    buttons);
            }
            if (player2.getRelics() != null && player2.hasRelic("mawofworlds") && activeGame.isCustodiansScored()) {
                MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(),
                    player2.getRepresentation()
                        + " Reminder this is the window to do Maw of Worlds");
                MessageHelper.sendMessageToChannelWithButtons(player2.getCardsInfoThread(),
                   player2.getRepresentation()
                        + " You can use these buttons to resolve Maw Of Worlds",
                    getMawButtons());
            }
            if (player2.getRelics() != null && player2.hasRelic("emphidia")) {
                for (String pl : player2.getPlanets()) {
                    Tile tile = activeGame.getTile(AliasHandler.resolveTile(pl));
                    if (tile == null) {
                        continue;
                    }
                    UnitHolder unitHolder = tile.getUnitHolders().get(pl);
                    if (unitHolder != null && unitHolder.getTokenList() != null
                        && unitHolder.getTokenList().contains("attachment_tombofemphidia.png")) {
                        MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(),
                            player2.getRepresentation()
                                + "Reminder this is the window to purge Crown of Emphidia if you want to.");
                        MessageHelper.sendMessageToChannelWithButtons(player2.getCardsInfoThread(),
                            player2.getRepresentation()
                                + " You can use these buttons to resolve Crown of Emphidia",
                            getCrownButtons());
                    }
                }
            }
            if (player2.getActionCards() != null && player2.getActionCards().containsKey("summit")
                && !activeGame.isCustodiansScored()) {
                MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(),
                    player2.getRepresentation()
                        + "Reminder this is the window to do summit");
            }
            if (player2.getActionCards() != null && (player2.getActionCards().containsKey("investments")
                && !activeGame.isCustodiansScored())) {
                MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(),
                    player2.getRepresentation()
                        + "Reminder this is the window to do manipulate investments.");
            }

            if (player2.getActionCards() != null && player2.getActionCards().containsKey("stability")) {
                MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(),
                    player2.getRepresentation()
                        + "Reminder this is the window to play political stability.");
            }

            if (player2.getActionCards() != null && player2.getActionCards().containsKey("abs")&& activeGame.isCustodiansScored()) {
                MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(),
                    player2.getRepresentation()
                        + "Reminder this is the window to play ancient burial sites.");
            }

            for (String pn : player2.getPromissoryNotes().keySet()) {
                if (!player2.ownsPromissoryNote("ce") && "ce".equalsIgnoreCase(pn)) {
                    String cyberMessage = "# " + Helper.getPlayerRepresentation(player2, activeGame, event.getGuild(), true)
                        + " reminder to use cybernetic enhancements!";
                    MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(),
                        cyberMessage);
                }
            }
        }
        String message2 = "Resolve status homework using the buttons. \n ";
        activeGame.setACDrawStatusInfo("");
        Button draw1AC = Button.success("drawStatusACs", "Draw Status Phase ACs").withEmoji(Emoji.fromFormatted(Emojis.ActionCard));
        Button getCCs = Button.success("redistributeCCButtons", "Redistribute, Gain, & Confirm CCs").withEmoji(Emoji.fromFormatted("🔺"));
        boolean custodiansTaken = activeGame.isCustodiansScored();
        Button passOnAbilities;
        if (custodiansTaken) {
            passOnAbilities = Button.danger("pass_on_abilities", "Ready For Agenda");
            message2 = message2
                + "This is the moment when you should resolve: \n- Political Stability \n- Ancient Burial Sites\n- Maw of Worlds \n- Naalu hero\n- Crown of Emphidia";
        } else {
            passOnAbilities = Button.danger("pass_on_abilities", "Ready For Strategy Phase");
            message2 = message2
                + "Ready For Strategy Phase means you are done playing/passing on: \n- Political Stability \n- Summit \n- Manipulate Investments ";
        }
        List<Button> buttons = new ArrayList<>();
        if (activeGame.isFoWMode()) {
            buttons.add(draw1AC);
            buttons.add(getCCs);
            message2 = "Please resolve status homework";
            for (Player p1 : activeGame.getPlayers().values()) {
                if (p1 == null || p1.isDummy() || p1.getFaction() == null || p1.getPrivateChannel() == null) {
                } else {
                    MessageHelper.sendMessageToChannelWithButtons(p1.getPrivateChannel(), message2, buttons);

                }
            }
            buttons = new ArrayList<>();
            buttons.add(passOnAbilities);
        } else {

            buttons.add(draw1AC);
            buttons.add(getCCs);
            buttons.add(passOnAbilities);
        }
        // if (activeGame.getActionCards().size() > 130 && activeGame.getPlayerFromColorOrFaction("hacan") != null
        //     && getButtonsToSwitchWithAllianceMembers(activeGame.getPlayerFromColorOrFaction("hacan"), activeGame, false).size() > 0) {
        //     buttons.add(Button.secondary("getSwapButtons_", "Swap"));
        // }
        MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), message2, buttons);
    }

    public static void startStrategyPhase(GenericInteractionCreateEvent event, Game activeGame) {
        if(activeGame.getHasHadAStatusPhase()){
            int round = activeGame.getRound();
            round++;
            activeGame.setRound(round);
        }
        ButtonHelperFactionSpecific.checkForNaaluPN(activeGame);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Started Round "+activeGame.getRound());
        for(Player p2: activeGame.getRealPlayers()){
            if(activeGame.getFactionsThatReactedToThis("Summit") != null && activeGame.getFactionsThatReactedToThis("Summit").contains(p2.getFaction()) && p2.getActionCards().keySet().contains("summit")){
                PlayAC.playAC(event, activeGame, p2, "summit", activeGame.getMainGameChannel(), event.getGuild());
            }
            if(activeGame.getFactionsThatReactedToThis("Investments") != null && activeGame.getFactionsThatReactedToThis("Investments").contains(p2.getFaction()) && p2.getActionCards().keySet().contains("investments")){
                PlayAC.playAC(event, activeGame, p2, "investments", activeGame.getMainGameChannel(), event.getGuild());
            }
        }
        if (activeGame.getNaaluAgent()) {
            activeGame.setNaaluAgent(false);
            for (Player p2 : activeGame.getRealPlayers()) {
                for (String planet : p2.getPlanets()) {
                    if (planet.contains("custodia")|| planet.contains("ghoti")) {
                        continue;
                    }
                    if (ButtonHelper.isTileHomeSystem(activeGame.getTileFromPlanet(planet))) {
                        p2.exhaustPlanet(planet);
                    }
                }
            }
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), "# Exhausted all home systems due to that one agenda");
        }
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Pinged speaker to pick SC.");
        }
        Player speaker;
        if (activeGame.getPlayer(activeGame.getSpeaker()) != null) {
            speaker = activeGame.getPlayers().get(activeGame.getSpeaker());
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Speaker not found. Can't proceed");
            return;
        }
        String message = Helper.getPlayerRepresentation(speaker, activeGame, event.getGuild(), true)
            + " UP TO PICK SC\n";
        activeGame.updateActivePlayer(speaker);
        activeGame.setCurrentPhase("strategy");
        String pickSCMsg = "Use Buttons to Pick SC";
        if (activeGame.getLaws().containsKey("checks") || activeGame.getLaws().containsKey("absol_checks")) {
            pickSCMsg = "Use Buttons to Pick the SC you want to give someone";
        }
        ButtonHelperAbilities.giveKeleresCommsNTg(activeGame, event);
        if (activeGame.isFoWMode()) {
            if (!activeGame.isHomeBrewSCMode()) {
                MessageHelper.sendMessageToChannelWithButtons(speaker.getPrivateChannel(),
                    message + pickSCMsg, Helper.getRemainingSCButtons(event, activeGame, speaker));
            } else {
                MessageHelper.sendPrivateMessageToPlayer(speaker, activeGame, message);
            }
        } else {
            MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), message + pickSCMsg, Helper.getRemainingSCButtons(event, activeGame, speaker));
        }
        for (Player player2 : activeGame.getRealPlayers()) {
            if (player2.getActionCards() != null && player2.getActionCards().containsKey("summit")
                && activeGame.isCustodiansScored()) {
                MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(),
                    Helper.getPlayerRepresentation(player2, activeGame, activeGame.getGuild(), true)
                        + "Reminder this is the window to do summit");
            }
            for (String pn : player2.getPromissoryNotes().keySet()) {
                if (!player2.ownsPromissoryNote("scepter") && "scepter".equalsIgnoreCase(pn)) {
                    String pnShortHand = pn;
                    PromissoryNoteModel promissoryNote = Mapper.getPromissoryNoteByID(pnShortHand);
                    Player owner = activeGame.getPNOwner(pnShortHand);
                    Button transact = Button.success("resolvePNPlay_" + pnShortHand, "Play " + promissoryNote.getName()).withEmoji(Emoji.fromFormatted(owner.getFactionEmoji()));
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(transact);
                    buttons.add(Button.danger("deleteButtons", "Decline"));
                    String cyberMessage = Helper.getPlayerRepresentation(player2, activeGame, event.getGuild(), true)
                        + " reminder this is the window to play Mahact PN if you want (button should work)";
                    MessageHelper.sendMessageToChannelWithButtons(player2.getCardsInfoThread(),
                        cyberMessage, buttons);
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                            "You should all pause for a potential mahact PN play here if you think it relevant");
                    }
                }
            }
        }
    }

    public static List<Button> getMawButtons() {
        List<Button> playerButtons = new ArrayList<>();
        playerButtons.add(Button.success("resolveMaw", "Purge Maw of Worlds"));
        playerButtons.add(Button.danger("deleteButtons", "Decline"));
        return playerButtons;
    }

    public static List<Button> getCrownButtons() {
        List<Button> playerButtons = new ArrayList<>();
        playerButtons.add(Button.success("resolveCrownOfE", "Purge Crown"));
        playerButtons.add(Button.danger("deleteButtons", "Decline"));
        return playerButtons;
    }

    public static void resolveMaw(Game activeGame, Player player, ButtonInteractionEvent event) {

        player.removeRelic("mawofworlds");
        player.removeExhaustedRelic("mawofworlds");
        for (String planet : player.getPlanets()) {
            player.exhaustPlanet(planet);
        }
        activeGame.setComponentAction(true);
        Button getTech = Button.success("acquireATech", "Get a tech");
        List<Button> buttons = new ArrayList<>();
        buttons.add(getTech);
        buttons.add(Button.danger("deleteButtons", "Delete These"));
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), player.getRepresentation() + " purged Maw Of Worlds.");
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentation() + " Use the button to get a tech", buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveCrownOfE(Game activeGame, Player player, ButtonInteractionEvent event) {
        if(player.hasRelic("absol_emphidia")){
            player.removeRelic("absol_emphidia");
            player.removeExhaustedRelic("absol_emphidia");
        }
        if(player.hasRelic("emphidia")){
             player.removeRelic("emphidia");
            player.removeExhaustedRelic("emphidia");
        }
        Integer poIndex = activeGame.addCustomPO("Crown of Emphidia", 1);
        activeGame.scorePublicObjective(player.getUserID(), poIndex);
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), player.getRepresentation() + " scored Crown of Emphidia");
        Helper.checkEndGame(activeGame, player);
        event.getMessage().delete().queue();
    }

    public static List<Button> getPlayersToTransact(Game activeGame, Player p) {
        List<Button> playerButtons = new ArrayList<>();
        String finChecker = "FFCC_" + p.getFaction() + "_";
        for (Player player : activeGame.getPlayers().values()) {
            if (player.isRealPlayer()) {
                if (player.getFaction().equalsIgnoreCase(p.getFaction())) {
                    continue;
                }
                String faction = player.getFaction();
                if (faction != null && Mapper.isFaction(faction)) {
                    Button button;
                    if (!activeGame.isFoWMode()) {
                        button = Button.secondary(finChecker + "transactWith_" + faction, " ");

                        String factionEmojiString = player.getFactionEmoji();
                        button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                    } else {
                        button = Button.secondary(finChecker + "transactWith_" + player.getColor(), player.getColor());
                    }
                    playerButtons.add(button);
                }

            }
        }
        return playerButtons;
    }

    public static List<Button> getStuffToTransButtons(Game activeGame, Player p1, Player p2) {
        String finChecker = "FFCC_" + p1.getFaction() + "_";
        List<Button> stuffToTransButtons = new ArrayList<>();
        if (p1.getTg() > 0) {
            Button transact = Button.success(finChecker + "transact_TGs_" + p2.getFaction(), "TGs");
            stuffToTransButtons.add(transact);
        }
        if (p1.getCommodities() > 0 && !p1.hasAbility("military_industrial_complex")) {
            Button transact = Button.success(finChecker + "transact_Comms_" + p2.getFaction(), "Commodities");
            stuffToTransButtons.add(transact);
        }

        if ((p1.getCommodities() > 0 || p2.getCommodities() > 0)&& !p1.hasAbility("military_industrial_complex") && !p1.getAllianceMembers().contains(p2.getFaction())) {
            Button transact = Button.secondary(finChecker + "send_WashComms_" + p2.getFaction()+ "_0", "Wash Both Players Comms");
            stuffToTransButtons.add(transact);
        }
        if (ButtonHelper.getPlayersShipOrders(p1).size() > 0) {
            Button transact = Button.secondary(finChecker + "transact_shipOrders_" + p2.getFaction(), "Axis Orders");
            stuffToTransButtons.add(transact);
        }
        if ((p1.hasAbility("arbiters") || p2.hasAbility("arbiters")) && p1.getAc() > 0) {
            Button transact = Button.success(finChecker + "transact_ACs_" + p2.getFaction(), "Action Cards");
            stuffToTransButtons.add(transact);
        }
        if (p1.getPnCount() > 0) {
            Button transact = Button.success(finChecker + "transact_PNs_" + p2.getFaction(), "Promissory Notes");
            stuffToTransButtons.add(transact);
        }
        if (p1.getFragments().size() > 0) {
            Button transact = Button.success(finChecker + "transact_Frags_" + p2.getFaction(), "Fragments");
            stuffToTransButtons.add(transact);
        }
        if (ButtonHelperFactionSpecific.getTradePlanetsWithHacanMechButtons(p1, p2, activeGame).size() > 0) {
            Button transact = Button.success(finChecker + "transact_Planets_" + p2.getFaction(), "Planets").withEmoji(Emoji.fromFormatted(Emojis.getFactionIconFromDiscord("hacan")));
            stuffToTransButtons.add(transact);
        }
        if (ButtonHelper.getTradePlanetsWithAlliancePartnerButtons(p1, p2, activeGame).size() > 0) {
            Button transact = Button.success(finChecker + "transact_AlliancePlanets_" + p2.getFaction(), "Alliance Planets").withEmoji(Emoji.fromFormatted(Emojis.getFactionIconFromDiscord(p2.getFaction())));
            stuffToTransButtons.add(transact);
        }
        if (activeGame.getCurrentPhase().toLowerCase().contains("agenda")&& !ButtonHelper.playerHasDMZPlanet(p1, activeGame).equalsIgnoreCase("no")) {
            Button transact = Button.secondary(finChecker + "resolveDMZTrade_"+ButtonHelper.playerHasDMZPlanet(p1, activeGame)+"_" + p2.getFaction(), "Trade "+Mapper.getPlanet(ButtonHelper.playerHasDMZPlanet(p1, activeGame)).getName() + " (DMZ)");
            stuffToTransButtons.add(transact);
        }
        return stuffToTransButtons;
    }

    public static void resolveSetAFKTime(Game activeGameOG, Player player, String buttonID, ButtonInteractionEvent event) {
        String time = buttonID.split("_")[1];
        player.addHourThatIsAFK(time);
        deleteTheOneButton(event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdent(player) + " Set hour " + time + " as a time that you are afk");
        Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
        String afkTimes = "" + player.getHoursThatPlayerIsAFK();
        for (Game activeGame : mapList.values()) {
            if (!activeGame.isHasEnded()) {
                for (Player player2 : activeGame.getRealPlayers()) {
                    if (player2.getUserID().equalsIgnoreCase(player.getUserID())) {
                        player2.setHoursThatPlayerIsAFK(afkTimes);
                        GameSaveLoadManager.saveMap(activeGame);
                    }
                }
            }
        }
    }

    public static void offerAFKTimeOptions(Game activeGame, Player player) {
        List<Button> buttons = ButtonHelper.getSetAFKButtons(activeGame);
        player.setHoursThatPlayerIsAFK("");
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), ButtonHelper.getTrueIdentity(player, activeGame)
            + " your afk times (if any) have been reset. Use buttons to select the hours (note they are in UTC) in which you're afk. If you select 8 for example, you will be set as AFK from 8:00 UTC to 8:59 UTC in every game you are in.",
            buttons);
    }

    public static void resolveSpecificTransButtons(Game activeGame, Player p1, String buttonID, ButtonInteractionEvent event) {
        String finChecker = "FFCC_" + p1.getFaction() + "_";

        List<Button> stuffToTransButtons = new ArrayList<>();
        buttonID = buttonID.replace("transact_", "");
        String thingToTrans = buttonID.substring(0, buttonID.indexOf("_"));
        String factionToTrans = buttonID.substring(buttonID.indexOf("_") + 1);
        Player p2 = activeGame.getPlayerFromColorOrFaction(factionToTrans);
        if (p2 == null) {
            return;
        }

        switch (thingToTrans) {
            case "TGs" -> {
                String message = "Click the amount of tgs you would like to send";
                for (int x = 1; x < p1.getTg() + 1; x++) {
                    Button transact = Button.success(finChecker + "send_TGs_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "Comms" -> {
                String message = "Click the amount of commodities you would like to send";
                for (int x = 1; x < p1.getCommodities() + 1; x++) {
                    Button transact = Button.success(finChecker + "send_Comms_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "shipOrders" -> {
                String message = "Click the axis order you would like to send";
                for (String shipOrder : ButtonHelper.getPlayersShipOrders(p1)) {
                    Button transact = Button.success(finChecker + "send_shipOrders_" + p2.getFaction() + "_" + shipOrder, "" + Mapper.getRelic(shipOrder).getName());
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "Planets" -> {
                String message = "Click the planet you would like to send";
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, ButtonHelperFactionSpecific.getTradePlanetsWithHacanMechButtons(p1, p2, activeGame));
            }
            case "AlliancePlanets" -> {
                String message = "Click the planet you would like to send";
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, ButtonHelper.getTradePlanetsWithAlliancePartnerButtons(p1, p2, activeGame));
            }
            case "ACs" -> {
                String message = p1.getRepresentation() + " Click the GREEN button that indicates the AC you would like to send";
                for (String acShortHand : p1.getActionCards().keySet()) {
                    Button transact = Button.success(finChecker + "send_ACs_" + p2.getFaction() + "_" + p1.getActionCards().get(acShortHand), Mapper.getActionCardName(acShortHand));
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(p1.getCardsInfoThread(), message, stuffToTransButtons);
            }
            case "PNs" -> {
                PNInfo.sendPromissoryNoteInfo(activeGame, p1, false);
                String message = Helper.getPlayerRepresentation(p1, activeGame, activeGame.getGuild(), true) + " Click the PN you would like to send";

                for (String pnShortHand : p1.getPromissoryNotes().keySet()) {
                    if (p1.getPromissoryNotesInPlayArea().contains(pnShortHand)) {
                        continue;
                    }
                    PromissoryNoteModel promissoryNote = Mapper.getPromissoryNoteByID(pnShortHand);
                    Player owner = activeGame.getPNOwner(pnShortHand);
                    Button transact;
                    if (activeGame.isFoWMode()) {
                        transact = Button.success(finChecker + "send_PNs_" + p2.getFaction() + "_" + p1.getPromissoryNotes().get(pnShortHand), owner.getColor() + " " + promissoryNote.getName());
                    } else {
                        transact = Button.success(finChecker + "send_PNs_" + p2.getFaction() + "_" + p1.getPromissoryNotes().get(pnShortHand), promissoryNote.getName())
                            .withEmoji(Emoji.fromFormatted(owner.getFactionEmoji()));
                    }
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(p1.getCardsInfoThread(), message, stuffToTransButtons);
            }
            case "Frags" -> {
                String message = "Click the amount of fragments you would like to send";

                if (p1.getCrf() > 0) {
                    for (int x = 1; x < p1.getCrf() + 1; x++) {
                        Button transact = Button.primary(finChecker + "send_Frags_" + p2.getFaction() + "_CRF" + x, "Cultural Fragments (" + x + ")");
                        stuffToTransButtons.add(transact);
                    }
                }
                if (p1.getIrf() > 0) {
                    for (int x = 1; x < p1.getIrf() + 1; x++) {
                        Button transact = Button.success(finChecker + "send_Frags_" + p2.getFaction() + "_IRF" + x, "Industrial Fragments (" + x + ")");
                        stuffToTransButtons.add(transact);
                    }
                }
                if (p1.getHrf() > 0) {
                    for (int x = 1; x < p1.getHrf() + 1; x++) {
                        Button transact = Button.danger(finChecker + "send_Frags_" + p2.getFaction() + "_HRF" + x, "Hazardous Fragments (" + x + ")");
                        stuffToTransButtons.add(transact);
                    }
                }

                if (p1.getUrf() > 0) {
                    for (int x = 1; x < p1.getUrf() + 1; x++) {
                        Button transact = Button.secondary(finChecker + "send_Frags_" + p2.getFaction() + "_URF" + x, "Frontier Fragments (" + x + ")");
                        stuffToTransButtons.add(transact);
                    }
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);

            }
        }

    }

    public static void resolveSpecificTransButtonPress(Game activeGame, Player p1, String buttonID, ButtonInteractionEvent event) {
        String finChecker = "FFCC_" + p1.getFaction() + "_";
        buttonID = buttonID.replace("send_", "");
        List<Button> goAgainButtons = new ArrayList<>();

        String thingToTrans = buttonID.substring(0, buttonID.indexOf("_"));
        buttonID = buttonID.replace(thingToTrans + "_", "");
        String factionToTrans = buttonID.substring(0, buttonID.indexOf("_"));
        String amountToTrans = buttonID.substring(buttonID.indexOf("_") + 1);
        Player p2 = activeGame.getPlayerFromColorOrFaction(factionToTrans);
        String message2 = "";
        String ident = Helper.getPlayerRepresentation(p1, activeGame, activeGame.getGuild(), false);
        String ident2 = Helper.getPlayerRepresentation(p2, activeGame, activeGame.getGuild(), false);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find second player. Transaction aborted.");
            return;
        }
        switch (thingToTrans) {
            case "TGs" -> {
                int tgAmount = Integer.parseInt(amountToTrans);
                p1.setTg(p1.getTg() - tgAmount);
                p2.setTg(p2.getTg() + tgAmount);
                if (p2.getLeaderIDs().contains("hacancommander") && !p2.hasLeaderUnlocked("hacancommander")) {
                    commanderUnlockCheck(p2, activeGame, "hacan", event);
                }
                message2 = ident + " sent " + tgAmount + " TGs to " + ident2;
            }
            case "Comms" -> {
                int tgAmount = Integer.parseInt(amountToTrans);
                p1.setCommodities(p1.getCommodities() - tgAmount);
                if (!p1.isPlayerMemberOfAlliance(p2)) {
                    int targetTG = p2.getTg();
                    targetTG += tgAmount;
                    p2.setTg(targetTG);
                } else {
                    int targetTG = p2.getCommodities();
                    targetTG += tgAmount;
                    if (targetTG > p2.getCommoditiesTotal()) {
                        targetTG = p2.getCommoditiesTotal();
                    }
                    p2.setCommodities(targetTG);
                }

                if (p2.getLeaderIDs().contains("hacancommander") && !p2.hasLeaderUnlocked("hacancommander")) {
                    commanderUnlockCheck(p2, activeGame, "hacan", event);
                }
                ButtonHelperAbilities.pillageCheck(p1, activeGame);
                ButtonHelperAbilities.pillageCheck(p2, activeGame);
                ButtonHelperFactionSpecific.resolveDarkPactCheck(activeGame, p1, p2, tgAmount, event);
                message2 = ident + " sent " + tgAmount + " Commodities to " + ident2;
            }
            case "WashComms" -> {
                int tgAmount = Integer.parseInt(amountToTrans);
                int oldP1Comms = p1.getCommodities();
                int oldP2Comms = p2.getCommodities();
                p1.setCommodities(0);
                p2.setCommodities(0);
                p1.setTg(p1.getTg()+oldP1Comms);
                p2.setTg(p2.getTg()+oldP2Comms);
                if (p2.getLeaderIDs().contains("hacancommander") && !p2.hasLeaderUnlocked("hacancommander")) {
                    commanderUnlockCheck(p2, activeGame, "hacan", event);
                }
                //ButtonHelperAbilities.pillageCheck(p1, activeGame);
                //ButtonHelperAbilities.pillageCheck(p2, activeGame);
                ButtonHelperFactionSpecific.resolveDarkPactCheck(activeGame, p1, p2, oldP1Comms, event);
                 ButtonHelperFactionSpecific.resolveDarkPactCheck(activeGame, p2, p1, oldP2Comms, event);
                message2 = ident + " washed their " + oldP1Comms + " Commodities with " + ident2 +"\n"+ ident2 + " washed their " + oldP2Comms + " Commodities with " + ident;
            }
            case "shipOrders" -> {
                message2 = ident + " sent " + Mapper.getRelic(amountToTrans).getName() + " to " + ident2;
                p1.removeRelic(amountToTrans);
                p2.addRelic(amountToTrans);
            }
            case "ACs" -> {

                message2 = ident + " sent AC #" + amountToTrans + " to " + ident2;
                int acNum = Integer.parseInt(amountToTrans);
                String acID = null;
                if (!p1.getActionCards().containsValue(acNum)) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that AC, no AC sent");
                    return;
                }
                for (Map.Entry<String, Integer> ac : p1.getActionCards().entrySet()) {
                    if (ac.getValue().equals(acNum)) {
                        acID = ac.getKey();
                    }
                }
                p1.removeActionCard(acNum);
                p2.setActionCard(acID);
                ACInfo.sendActionCardInfo(activeGame, p2);
                ACInfo.sendActionCardInfo(activeGame, p1);
                if(!p1.hasAbility("arbiters") &&!p2.hasAbility("arbiters")){
                    if (activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(p1.getPrivateChannel(), message2);
                        MessageHelper.sendMessageToChannel(p2.getPrivateChannel(), message2);
                    } else {
                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), message2);
                    }
                    return;
                }
            }
            case "PNs" -> {
                String id = null;
                int pnIndex;
                pnIndex = Integer.parseInt(amountToTrans);
                for (Map.Entry<String, Integer> pn : p1.getPromissoryNotes().entrySet()) {
                    if (pn.getValue().equals(pnIndex)) {
                        id = pn.getKey();
                    }
                }
                if (id == null) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that PN, no PN sent");
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
            }
            case "Frags" -> {

                String fragType = amountToTrans.substring(0, 3);
                int fragNum = Integer.parseInt(amountToTrans.charAt(3) + "");
                String trait = switch (fragType) {
                    case "CRF" -> "cultural";
                    case "HRF" -> "hazardous";
                    case "IRF" -> "industrial";
                    case "URF" -> "frontier";
                    default -> "";
                };
                new SendFragments().sendFrags(event, p1, p2, trait, fragNum, activeGame);
                message2 = "";
            }
        }
        Button button = Button.secondary(finChecker + "transactWith_" + p2.getColor(), "Send something else to player?");
        Button done = Button.secondary("finishTransaction_" + p2.getColor(), "Done With This Transaction");

        goAgainButtons.add(button);
        goAgainButtons.add(done);
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(p1.getPrivateChannel(), message2);
            MessageHelper.sendMessageToChannelWithButtons(p1.getPrivateChannel(), ident + " Use Buttons To Complete Transaction", goAgainButtons);
            MessageHelper.sendMessageToChannel(p2.getPrivateChannel(), message2);
        } else {
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), message2);
            MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), ident + " Use Buttons To Complete Transaction", goAgainButtons);
        }
        //GameSaveLoadManager.saveMap(activeGame, event);

    }

    public static List<Button> getSetAFKButtons(Game activeGame) {
        List<Button> buttons = new ArrayList<>();
        for (int x = 0; x < 24; x++) {
            buttons.add(Button.secondary("setHourAsAFK_" + x, "" + x));
        }
        buttons.add(Button.danger("deleteButtons", "Done"));
        return buttons;
    }

    public static List<Button> getAllPossibleCompButtons(Game activeGame, Player p1, GenericInteractionCreateEvent event) {
        String finChecker = "FFCC_" + p1.getFaction() + "_";
        String prefix = "componentActionRes_";
        List<Button> compButtons = new ArrayList<>();
        //techs
        for (String tech : p1.getTechs()) {
            if (!p1.getExhaustedTechs().isEmpty() && p1.getExhaustedTechs().contains(tech)) {
                continue;
            }
            TechnologyModel techRep = Mapper.getTechs().get(tech);
            String techName = techRep.getName();
            TechnologyType techType = techRep.getType();
            String techEmoji = Emojis.getEmojiFromDiscord(techType.toString().toLowerCase() + "tech");
            String techText = techRep.getText();

            if (techText.contains("ACTION")) {
                Button tButton = Button.danger(finChecker + prefix + "tech_" + tech, "Exhaust " + techName).withEmoji(Emoji.fromFormatted(techEmoji));
                compButtons.add(tButton);
            }
        }
        //leaders
        for (Leader leader : p1.getLeaders()) {
            if (!leader.isExhausted() && !leader.isLocked()) {
                String leaderID = leader.getId();

                LeaderModel leaderModel = Mapper.getLeader(leaderID);
                if (leaderModel == null) {
                    continue;
                }

                String leaderName = leaderModel.getName();
                String leaderAbilityWindow = leaderModel.getAbilityWindow();

                String factionEmoji = Emojis.getFactionLeaderEmoji(leader);
                if ("ACTION:".equalsIgnoreCase(leaderAbilityWindow) || leaderName.contains("Ssruu")) {
                    if (leaderName.contains("Ssruu")) {
                        String led = "muaatagent";
                        if (p1.hasExternalAccessToLeader(led)) {
                            Button lButton = Button.secondary(finChecker + prefix + "leader_" + led, "Use " + leaderName + " as Muaat agent").withEmoji(Emoji.fromFormatted(factionEmoji));
                            compButtons.add(lButton);
                        }
                        led = "naaluagent";
                        if (p1.hasExternalAccessToLeader(led)) {
                            Button lButton = Button.secondary(finChecker + prefix + "leader_" + led, "Use " + leaderName + " as Naalu agent").withEmoji(Emoji.fromFormatted(factionEmoji));
                            compButtons.add(lButton);
                        }
                        led = "arborecagent";
                        if (p1.hasExternalAccessToLeader(led)) {
                            Button lButton = Button.secondary(finChecker + prefix + "leader_" + led, "Use " + leaderName + " as Arborec agent").withEmoji(Emoji.fromFormatted(factionEmoji));
                            compButtons.add(lButton);
                        }
                        led = "axisagent";
                        if (p1.hasExternalAccessToLeader(led)) {
                            Button lButton = Button.secondary(finChecker + prefix + "leader_" + led, "Use " + leaderName + " as Axis agent").withEmoji(Emoji.fromFormatted(factionEmoji));
                            compButtons.add(lButton);
                        }
                        led = "xxchaagent";
                        if (p1.hasExternalAccessToLeader(led)) {
                            Button lButton = Button.secondary(finChecker + prefix + "leader_" + led, "Use " + leaderName + " as Xxcha agent").withEmoji(Emoji.fromFormatted(factionEmoji));
                            compButtons.add(lButton);
                        }
                        led = "yssarilagent";
                        Button lButton = Button.secondary(finChecker + prefix + "leader_" + led, "Use " + leaderName + " as Unimplemented Component Agent")
                            .withEmoji(Emoji.fromFormatted(factionEmoji));
                        compButtons.add(lButton);

                    } else {
                        Button lButton = Button.secondary(finChecker + prefix + "leader_" + leaderID, "Use " + leaderName).withEmoji(Emoji.fromFormatted(factionEmoji));
                        compButtons.add(lButton);
                    }

                } else if ("mahactcommander".equalsIgnoreCase(leaderID) && p1.getTacticalCC() > 0 && getTilesWithYourCC(p1, activeGame, event).size() > 0) {
                    Button lButton = Button.secondary(finChecker + "mahactCommander", "Use " + leaderName).withEmoji(Emoji.fromFormatted(factionEmoji));
                    compButtons.add(lButton);
                }
            }
        }
        // Relics
        boolean dontEnigTwice = true;
        for (String relic : p1.getRelics()) {
            RelicModel relicData = Mapper.getRelic(relic);
            if (relicData == null) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that PN, no PN sent");
                continue;
            }

            if (relic.equalsIgnoreCase(Constants.ENIGMATIC_DEVICE) || (relicData != null && relicData.getText().contains("Action:"))) {
                Button rButton;
                if (relic.equalsIgnoreCase(Constants.ENIGMATIC_DEVICE)) {
                    if (!dontEnigTwice) {
                        continue;
                    }
                    rButton = Button.danger(finChecker + prefix + "relic_" + relic, "Purge Enigmatic Device");
                    dontEnigTwice = false;
                } else {
                    if ("titanprototype".equalsIgnoreCase(relic) || "absol_jr".equalsIgnoreCase(relic)) {
                        if (!p1.getExhaustedRelics().contains(relic)) {
                            rButton = Button.primary(finChecker + prefix + "relic_" + relic, "Exhaust " + relicData.getName());
                        } else {
                            continue;
                        }

                    } else {
                        rButton = Button.danger(finChecker + prefix + "relic_" + relic, "Purge " + relicData.getName());
                    }

                }
                compButtons.add(rButton);
            }
        }
        //PNs
        for (String pn : p1.getPromissoryNotes().keySet()) {
            if (pn != null && Mapper.getPromissoryNoteOwner(pn) != null && !Mapper.getPromissoryNoteOwner(pn).equalsIgnoreCase(p1.getFaction()) && !p1.getPromissoryNotesInPlayArea().contains(pn)
                && Mapper.getPromissoryNote(pn, true) != null) {
                String pnText = Mapper.getPromissoryNote(pn, true);
                if (pnText.contains("Action:") && !"bmf".equalsIgnoreCase(pn)) {
                    PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(pn);
                    String pnName = pnModel.getName();
                    Button pnButton = Button.danger(finChecker + prefix + "pn_" + pn, "Use " + pnName);
                    compButtons.add(pnButton);
                }
            }
            if (Mapper.getPromissoryNote(pn, true) == null) {
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p1, activeGame),
                    ButtonHelper.getTrueIdentity(p1, activeGame) + " you have a null PN. Please use /pn purge after reporting it " + pn);
                PNInfo.sendPromissoryNoteInfo(activeGame, p1, false);
            }
        }
        //Abilities
        if (p1.hasAbility("star_forge") && p1.getStrategicCC() > 0 && getTilesOfPlayersSpecificUnits(activeGame, p1, UnitType.Warsun).size() > 0) {
            Button abilityButton = Button.success(finChecker + prefix + "ability_starForge", "Starforge").withEmoji(Emoji.fromFormatted(Emojis.Muaat));
            compButtons.add(abilityButton);
        }
        if (p1.hasAbility("orbital_drop") && p1.getStrategicCC() > 0) {
            Button abilityButton = Button.success(finChecker + prefix + "ability_orbitalDrop", "Orbital Drop").withEmoji(Emoji.fromFormatted(Emojis.Sol));
            compButtons.add(abilityButton);
        }
        if (p1.hasAbility("mantle_cracking") && ButtonHelperAbilities.getMantleCrackingButtons(p1, activeGame).size() > 0) {
            Button abilityButton = Button.success(finChecker + prefix + "ability_mantlecracking", "Mantle Crack").withEmoji(Emoji.fromFormatted(Emojis.gledge));
            compButtons.add(abilityButton);
        }
        if (p1.hasAbility("stall_tactics") && p1.getActionCards().size() > 0) {
            Button abilityButton = Button.success(finChecker + prefix + "ability_stallTactics", "Stall Tactics").withEmoji(Emoji.fromFormatted(Emojis.Yssaril));
            compButtons.add(abilityButton);
        }
        if (p1.hasAbility("fabrication") && p1.getFragments().size() > 0) {
            Button abilityButton = Button.success(finChecker + prefix + "ability_fabrication", "Purge 1 Frag for a CC").withEmoji(Emoji.fromFormatted(Emojis.Naaz));
            compButtons.add(abilityButton);
        }
        if (p1.getUnitsOwned().contains("muaat_flagship") && p1.getStrategicCC() > 0 && getTilesOfPlayersSpecificUnits(activeGame, p1, UnitType.Flagship).size() > 0) {
            Button abilityButton = Button.success(finChecker + prefix + "ability_muaatFS", "Spend a Strat CC for a Cruiser with your FS").withEmoji(Emoji.fromFormatted(Emojis.Muaat));
            compButtons.add(abilityButton);
        }
        //Get Relic
        if (p1.enoughFragsForRelic()) {
            Button getRelicButton = Button.success(finChecker + prefix + "getRelic_", "Get Relic");
            compButtons.add(getRelicButton);
        }
        //ACs
        Button acButton = Button.secondary(finChecker + prefix + "actionCards_", "Play \"ACTION:\" AC");
        compButtons.add(acButton);

        //absol
        if(ButtonHelper.isPlayerElected(activeGame, p1, "absol_minswar") && !activeGame.getFactionsThatReactedToThis("absolMOW").contains(p1.getFaction())){
            Button absolButton = Button.secondary(finChecker + prefix + "absolMOW_", "Absol Minister of War Action");
            compButtons.add(absolButton);
        }
        
        //Generic
        Button genButton = Button.secondary(finChecker + prefix + "generic_", "Generic Component Action");
        compButtons.add(genButton);

        return compButtons;
    }
    public static void resolvePreAssignment(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String messageID = buttonID.split("_")[1];
        String msg = getIdent(player)+" successfully preset "+messageID;
        String part2 = player.getFaction();
        if(activeGame.getFactionsThatReactedToThis(messageID) != null && !activeGame.getFactionsThatReactedToThis(messageID).isEmpty()){
            part2 = activeGame.getFactionsThatReactedToThis(messageID) + "_"+player.getFaction();
        }
        if(StringUtils.countMatches(buttonID, "_") > 1){
            part2  = part2+ "_"+buttonID.split("_")[2];
            msg = msg + " on "+buttonID.split("_")[2];
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),msg );
        activeGame.setCurrentReacts(messageID, part2);
        event.getMessage().delete().queue();
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.danger("removePreset_"+messageID, "Remove The Preset"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),player.getRepresentation()+ " you can use this button to undo the preset. Ignore it otherwise", buttons);

    }
    public static void resolveRemovalOfPreAssignment(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String messageID = buttonID.split("_")[1];
        String msg = getIdent(player)+" successfully removed the preset for "+messageID;
        String part2 = player.getFaction();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),msg);
        if(activeGame.getFactionsThatReactedToThis(messageID) != null){
            activeGame.setCurrentReacts(messageID, activeGame.getFactionsThatReactedToThis(messageID).replace(part2, ""));
        }
        event.getMessage().delete().queue();

    }
    public static String mechOrInfCheck(String planetName, Game activeGame, Player player) {
        String message;
        Tile tile = activeGame.getTile(AliasHandler.resolveTile(planetName));
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        int numMechs = 0;
        int numInf = 0;
        String colorID = Mapper.getColorID(player.getColor());
        UnitKey mechKey = Mapper.getUnitKey("mf", colorID);
        UnitKey infKey = Mapper.getUnitKey("gf", colorID);
        if (unitHolder.getUnits() != null) {

            if (unitHolder.getUnits().get(mechKey) != null) {
                numMechs = unitHolder.getUnits().get(mechKey);
            }
            if (unitHolder.getUnits().get(infKey) != null) {
                numInf = unitHolder.getUnits().get(infKey);
            }
        }
        if (numMechs > 0 || numInf > 0) {
            if (numMechs > 0) {
                message = "Planet had a mech. ";
            } else {
                message = "Planet did not have a mech. Removed 1 infantry (" + numInf + "->" + (numInf - 1) + "). ";
                tile.removeUnit(planetName, infKey, 1);
            }
        } else {
            message = "Planet did not have a mech or infantry. Please try again.";
        }
        return message;
    }

    public static void addReaction(ButtonInteractionEvent event, boolean skipReaction, boolean sendPublic, String message, String additionalMessage) {
        if (event == null) return;

        String userID = event.getUser().getId();
        Game activeGame = GameManager.getInstance().getUserActiveGame(userID);
        Player player = Helper.getGamePlayer(activeGame, null, event.getMember(), userID);
        if (player == null || !player.isRealPlayer()) {
            event.getChannel().sendMessage("You're not an active player of the game").queue();
            return;
        }
        //String playerFaction = player.getFaction();
        Guild guild = event.getGuild();
        if (guild == null) {
            event.getChannel().sendMessage("Could not find server Emojis").queue();
            return;
        }
        HashMap<String, Emoji> emojiMap = ButtonListener.emoteMap.get(guild);
        List<RichCustomEmoji> emojis = guild.getEmojis();
        if (emojiMap != null && emojiMap.size() != emojis.size()) {
            emojiMap.clear();
        }
        if (emojiMap == null || emojiMap.isEmpty()) {
            emojiMap = new HashMap<>();
            for (Emoji emoji : emojis) {
                emojiMap.put(emoji.getName().toLowerCase(), emoji);
            }
        }

        Message mainMessage = event.getInteraction().getMessage();
        Emoji emojiToUse = Helper.getPlayerEmoji(activeGame, player, mainMessage);
        String messageId = mainMessage.getId();

        if (!skipReaction) {
            if (event.getMessageChannel() instanceof ThreadChannel) {

                activeGame.getActionsChannel().addReactionById(event.getChannel().getId(), emojiToUse).queue();
            }

            event.getChannel().addReactionById(messageId, emojiToUse).queue();
            if(activeGame.getFactionsThatReactedToThis(messageId) != null){
                if(!activeGame.getFactionsThatReactedToThis(messageId).contains(player.getFaction())){
                    activeGame.setCurrentReacts(messageId, activeGame.getFactionsThatReactedToThis(messageId)+"_"+player.getFaction());
                }
            }else{
                activeGame.setCurrentReacts(messageId, player.getFaction());
            }
            
            new ButtonListener().checkForAllReactions(event, activeGame);
            if (message == null || message.isEmpty()) {
                return;
            }
        }

        String text = player.getRepresentation();
        if ("Not Following".equalsIgnoreCase(message)) text = player.getRepresentation(false, false);
        text = text + " " + message;
        if (activeGame.isFoWMode() && sendPublic) {
            text = message;
        } else if (activeGame.isFoWMode() && !sendPublic) {
            text = "(You) " + emojiToUse.getFormatted() + " " + message;
        }

        if (additionalMessage != null && !additionalMessage.isEmpty()) {
            text += Helper.getGamePing(event.getGuild(), activeGame) + " " + additionalMessage;
        }

        if (activeGame.isFoWMode() && !sendPublic) {
            MessageHelper.sendPrivateMessageToPlayer(player, activeGame, text);
            return;
        }

        MessageHelper.sendMessageToChannel(Helper.getThreadChannelIfExists(event), text);
    }

    public static void addReaction(Player player, boolean skipReaction, boolean sendPublic, String message, String additionalMessage, String messageID, Game activeGame) {
        Guild guild = activeGame.getGuild();
        if (guild == null) return;

        HashMap<String, Emoji> emojiMap = ButtonListener.emoteMap.get(guild);
        List<RichCustomEmoji> emojis = guild.getEmojis();
        if (emojiMap != null && emojiMap.size() != emojis.size()) {
            emojiMap.clear();
        }
        if (emojiMap == null || emojiMap.isEmpty()) {
            emojiMap = new HashMap<>();
            for (Emoji emoji : emojis) {
                emojiMap.put(emoji.getName().toLowerCase(), emoji);
            }
        }
        
        try {
            activeGame.getMainGameChannel().retrieveMessageById(messageID).queue(mainMessage -> {
                Emoji emojiToUse = Helper.getPlayerEmoji(activeGame, player, mainMessage);
                String messageId = mainMessage.getId();

        if (!skipReaction) {
            activeGame.getMainGameChannel().addReactionById(messageId, emojiToUse).queue();
            if(activeGame.getFactionsThatReactedToThis(messageId) != null){
                activeGame.setCurrentReacts(messageId, activeGame.getFactionsThatReactedToThis(messageId)+"_"+player.getFaction());
            }else{
                activeGame.setCurrentReacts(messageId, player.getFaction());
            }
            new ButtonListener().checkForAllReactions(messageId, activeGame);
            if (message == null || message.isEmpty()) {
                return;
            }
        }

        String text = player.getRepresentation() + " " + message;
        if (activeGame.isFoWMode() && sendPublic) {
            text = message;
        } else if (activeGame.isFoWMode() && !sendPublic) {
            text = "(You) " + emojiToUse.getFormatted() + " " + message;
        }

                if (additionalMessage != null && !additionalMessage.isEmpty()) {
                    text += Helper.getGamePing(guild, activeGame) + " " + additionalMessage;
                }

                if (activeGame.isFoWMode() && !sendPublic) {
                    MessageHelper.sendPrivateMessageToPlayer(player, activeGame, text);
                    return;
                }
            });
        } catch (Error e) {
            activeGame.removeMessageIDForSabo(messageID);
            return;
        }

    }

    public static Tile getTileOfPlanetWithNoTrait(Player player, Game activeGame) {

        for (String planet : player.getPlanets()) {
            UnitHolder unitHolder = activeGame.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            boolean oneOfThree = planetReal != null && planetReal.getOriginalPlanetType() != null && ("industrial".equalsIgnoreCase(planetReal.getOriginalPlanetType())
                || "cultural".equalsIgnoreCase(planetReal.getOriginalPlanetType()) || "hazardous".equalsIgnoreCase(planetReal.getOriginalPlanetType()));
            if (!"mr".equalsIgnoreCase(planet) && !"custodiavigilia".equalsIgnoreCase(planet) && !oneOfThree) {
                return activeGame.getTileFromPlanet(planet);
            }
        }

        return null;

    }

    public static String getListOfStuffAvailableToSpend(Player player, Game activeGame) {
        String youCanSpend;
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        StringBuilder youCanSpendBuilder = new StringBuilder("You have available to you to spend: ");
        for (String planet : planets) {
            youCanSpendBuilder.append(Helper.getPlanetRepresentation(planet, activeGame)).append(", ");
        }
        youCanSpend = youCanSpendBuilder.toString();
        if (planets.isEmpty()) {
            youCanSpend = "You have available to you 0 unexhausted planets ";
        }
        if (!activeGame.getCurrentPhase().contains("agenda")) {
            youCanSpend = youCanSpend + "and " + player.getTg() + " tgs";
        }

        return youCanSpend;
    }

    public static List<Tile> getTilesOfPlayersSpecificUnits(Game activeGame, Player p1, UnitType... type) {
        List<UnitType> unitTypes = new ArrayList<>();
        for (UnitType t : type)
            unitTypes.add(t);

        List<Tile> realTiles = activeGame.getTileMap().values().stream()
            .filter(t -> t.containsPlayersUnitsWithKeyCondition(p1, unit -> unitTypes.contains(unit.getUnitType())))
            .toList();
        return realTiles;
    }

    public static int getNumberOfUnitsOnTheBoard(Game activeGame, Player p1, String unit) {
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), p1.getColor());
        return getNumberOfUnitsOnTheBoard(activeGame, unitKey);
    }

    public static int getNumberOfUnitsOnTheBoard(Game activeGame, UnitKey unitKey) {
        List<UnitHolder> unitHolders = new ArrayList<>(activeGame.getTileMap().values().stream()
            .flatMap(t -> t.getUnitHolders().values().stream()).toList());
        unitHolders.addAll(activeGame.getRealPlayers().stream()
            .flatMap(p -> p.getNomboxTile().getUnitHolders().values().stream()).toList());

        return unitHolders.stream()
            .flatMap(uh -> uh.getUnits().entrySet().stream())
            .filter(e -> e.getKey().equals(unitKey))
            .collect(Collectors.summingInt(e -> Optional.ofNullable(e.getValue()).orElse(0).intValue()));
    }

    public static void resolveDiploPrimary(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String type = buttonID.split("_")[2];
        if (type.toLowerCase().contains("mahact")) {
            String color2 = type.replace("mahact", "");
            Player mahactP = activeGame.getPlayerFromColorOrFaction(color2);
            if (mahactP == null) {
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Could not find mahact player");
                return;
            }
            Tile tile = activeGame.getTileByPosition(planet);
            AddCC.addCC(event, color2, tile);
            Helper.isCCCountCorrect(event, activeGame, color2);
            for (String color : mahactP.getMahactCC()) {
                if (Mapper.isColorValid(color) && !color.equalsIgnoreCase(player.getColor())) {
                    AddCC.addCC(event, color, tile);
                    Helper.isCCCountCorrect(event, activeGame, color);
                }
            }
            String message = ButtonHelper.getIdent(player) + " chose to use the mahact PN in the tile " + tile.getRepresentation();
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), message);
        } else {
            String tileID = AliasHandler.resolveTile(planet.toLowerCase());
            Tile tile = activeGame.getTile(tileID);
            if (tile == null) {
                tile = activeGame.getTileByPosition(tileID);
            }
            if (tile == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Could not resolve tileID:  `" + tileID + "`. Tile not found");
                return;
            }
            for (Player player_ : activeGame.getPlayers().values()) {
                if (player_ != player) {
                    String color = player_.getColor();
                    if (Mapper.isColorValid(color)) {
                        AddCC.addCC(event, color, tile);
                        Helper.isCCCountCorrect(event, activeGame, color);
                    }
                }
            }
            String message = ButtonHelper.getIdent(player) + " chose to diplo the system containing " + Helper.getPlanetRepresentation(planet, activeGame);
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        }
        event.getMessage().delete().queue();
    }

    public static void resolvePressedCompButton(Game activeGame, Player p1, ButtonInteractionEvent event, String buttonID) {
        String prefix = "componentActionRes_";
        String finChecker = "FFCC_" + p1.getFaction() + "_";
        buttonID = buttonID.replace(prefix, "");

        String firstPart = buttonID.substring(0, buttonID.indexOf("_"));
        buttonID = buttonID.replace(firstPart + "_", "");

        switch (firstPart) {
            case "tech" -> {
                p1.exhaustTech(buttonID);

                MessageHelper.sendMessageToChannel(event.getMessageChannel(), (p1.getRepresentation() + " exhausted tech: " + Helper.getTechRepresentation(buttonID)));
                if ("mi".equalsIgnoreCase(buttonID)) {
                    List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(activeGame, null, "getACFrom", null);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), ButtonHelper.getTrueIdentity(p1, activeGame) + " Select who you would like to mageon.", buttons);
                }
                if ("vtx".equalsIgnoreCase(buttonID)) {
                    List<Button> buttons = ButtonHelperFactionSpecific.getUnitButtonsForVortex(p1, activeGame, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), ButtonHelper.getTrueIdentity(p1, activeGame) + " Select what unit you would like to capture", buttons);
                }
                if ("wg".equalsIgnoreCase(buttonID)) {
                    List<Button> buttons = new ArrayList<>(ButtonHelperFactionSpecific.getCreusIFFTypeOptions(activeGame, p1));
                    String message = getTrueIdentity(p1, activeGame) + " select type of wormhole you wish to drop";
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(p1, activeGame), message, buttons);
                }
                if ("pm".equalsIgnoreCase(buttonID)) {
                    ButtonHelperFactionSpecific.resolveProductionBiomesStep1(p1, activeGame, event, buttonID);
                }
                if ("sr".equalsIgnoreCase(buttonID)) {
                    List<Button> buttons = new ArrayList<>();
                    List<Tile> tiles = new ArrayList<>();
                    tiles.addAll(getTilesOfPlayersSpecificUnits(activeGame, p1, UnitType.Spacedock, UnitType.CabalSpacedock, UnitType.PlenaryOrbital));
                    if (p1.hasUnit("ghoti_flagship")) {
                        tiles.addAll(getTilesOfPlayersSpecificUnits(activeGame, p1, UnitType.Flagship));
                    }
                    List<String> pos2 = new ArrayList<String>();
                    for (Tile tile : tiles) {
                        if (!pos2.contains(tile.getPosition())) {
                            Button tileButton = Button.success("produceOneUnitInTile_" + tile.getPosition() + "_sling", tile.getRepresentationForButtons(activeGame, p1));
                            buttons.add(tileButton);
                            pos2.add(tile.getPosition());
                        }
                    }
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Select which tile you would like to sling in.", buttons);
                }
            }
            case "leader" -> {
                Leader playerLeader = p1.getLeader(buttonID).orElse(null);
                if (playerLeader == null || !Mapper.isValidLeader(playerLeader.getId())) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not resolve leader.");
                    return;
                }
                if (buttonID.contains("agent")) {
                    List<String> leadersThatNeedSpecialSelection = List.of("naaluagent", "muaatagent", "arborecagent", "xxchaagent", "axisagent");
                    if (leadersThatNeedSpecialSelection.contains(playerLeader.getId().toLowerCase())) {
                        List<Button> buttons = ButtonHelper.getButtonsForAgentSelection(activeGame, playerLeader.getId());
                        String message = p1.getRepresentation(true, true) + " Use buttons to select the user of the agent";
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                    } else {
                        ExhaustLeader.exhaustLeader(event, activeGame, p1, playerLeader, null);
                    }
                } else if (buttonID.contains("hero")) {
                    HeroPlay.playHero(event, activeGame, p1, playerLeader);
                }
            }
            case "relic" -> {
                String purgeOrExhaust = "Purged ";

                if (p1.hasRelic(buttonID)) {
                    if ("titanprototype".equalsIgnoreCase(buttonID) || "absol_jr".equalsIgnoreCase(buttonID)) {
                        List<Button> buttons2 = AgendaHelper.getPlayerOutcomeButtons(activeGame, null, "jrResolution", null);
                        p1.addExhaustedRelic(buttonID);
                        purgeOrExhaust = "Exhausted ";
                        Button sdButton = Button.success("jrStructure_sd", "Place A SD");
                        sdButton = sdButton.withEmoji(Emoji.fromFormatted(Emojis.spacedock));
                        Button pdsButton = Button.success("jrStructure_pds", "Place a PDS");
                        pdsButton = pdsButton.withEmoji(Emoji.fromFormatted(Emojis.pds));
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to decide who to use JR on", buttons2);
                    } else {
                        p1.removeRelic(buttonID);
                        p1.removeExhaustedRelic(buttonID);
                    }

                    RelicModel relicModel = Mapper.getRelic(buttonID);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), purgeOrExhaust + Emojis.Relic + " relic: " + relicModel.getName() + "\n> " + relicModel.getText());
                    if (relicModel.getName().contains("Enigmatic")) {
                        activeGame.setComponentAction(true);
                        Button getTech = Button.success("acquireATech", "Get a tech");
                        List<Button> buttons = new ArrayList<>();
                        buttons.add(getTech);
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), ButtonHelper.getTrueIdentity(p1, activeGame) + " Use Button to get a tech", buttons);
                    }
                    if (relicModel.getName().contains("Nanoforge")) {
                        offerNanoforgeButtons(p1, activeGame, event);
                    }
                    if ("dynamiscore".equals(buttonID) || "absol_dynamiscore".equals(buttonID)) {
                        int oldTg = p1.getTg();
                        p1.setTg(oldTg + p1.getCommoditiesTotal() + 2);
                        if ("absol_dynamiscore".equals(buttonID)) {
                            p1.setTg(p1.getTg() + 2);
                        }
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getTrueIdentity(p1, activeGame) + " Your tgs increased from " + oldTg + " -> " + p1.getTg());
                        ButtonHelperAbilities.pillageCheck(p1, activeGame);
                        ButtonHelperAgents.resolveArtunoCheck(p1, activeGame, p1.getTg() - oldTg);
                    }
                    if ("stellarconverter".equals(buttonID)) {
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), ButtonHelper.getTrueIdentity(p1, activeGame) + " Select the planet you want to destroy",
                            ButtonHelper.getButtonsForStellar(p1, activeGame));
                    }

                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Invalid relic or player does not have specified relic");
                }

            }
            case "pn" -> resolvePNPlay(buttonID, p1, activeGame, event);
            case "ability" -> {
                if ("starForge".equalsIgnoreCase(buttonID)) {

                    List<Tile> tiles = getTilesOfPlayersSpecificUnits(activeGame, p1, UnitType.Warsun);
                    List<Button> buttons = new ArrayList<>();
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Chose to use the starforge ability");
                    String message = "Select the tile you would like to starforge in";
                    for (Tile tile : tiles) {
                        Button starTile = Button.success("starforgeTile_" + tile.getPosition(), tile.getRepresentationForButtons(activeGame, p1));
                        buttons.add(starTile);
                    }
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                } else if ("orbitalDrop".equalsIgnoreCase(buttonID)) {
                    String successMessage = "Reduced strategy pool CCs by 1 (" + (p1.getStrategicCC()) + "->" + (p1.getStrategicCC() - 1) + ")";
                    p1.setStrategicCC(p1.getStrategicCC() - 1);
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(p1, activeGame, event);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
                    String message = "Select the planet you would like to place 2 infantry on.";
                    List<Button> buttons = Helper.getPlanetPlaceUnitButtons(p1, activeGame, "2gf", "place");
                    buttons.add(Button.danger("orbitolDropFollowUp", "Done Dropping Infantry"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                } else if ("muaatFS".equalsIgnoreCase(buttonID)) {
                    String successMessage = "Used Muaat FS ability. Reduced strategy pool CCs by 1 (" + (p1.getStrategicCC()) + "->" + (p1.getStrategicCC() - 1) + ") \n";
                    p1.setStrategicCC(p1.getStrategicCC() - 1);
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(p1, activeGame, event);
                    List<Tile> tiles = getTilesOfPlayersSpecificUnits(activeGame, p1, UnitType.Flagship);
                    Tile tile = tiles.get(0);
                    List<Button> buttons = getStartOfTurnButtons(p1, activeGame, true, event);
                    new AddUnits().unitParsing(event, p1.getColor(), tile, "1 cruiser", activeGame);
                    successMessage = successMessage + "Produced 1 " + Emojis.cruiser + " in tile "
                        + tile.getRepresentationForButtons(activeGame, p1) + ".";
                    MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
                    String message = "Use buttons to end turn or do another action";
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
                    event.getMessage().delete().queue();

                } else if ("fabrication".equalsIgnoreCase(buttonID)) {
                    String message = "Click the fragment you'd like to purge. ";
                    List<Button> purgeFragButtons = new ArrayList<>();
                    if (p1.getCrf() > 0) {
                        Button transact = Button.primary(finChecker + "purge_Frags_CRF_1", "Purge 1 Cultural Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getIrf() > 0) {
                        Button transact = Button.success(finChecker + "purge_Frags_IRF_1", "Purge 1 Industrial Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getHrf() > 0) {
                        Button transact = Button.danger(finChecker + "purge_Frags_HRF_1", "Purge 1 Hazardous Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getUrf() > 0) {
                        Button transact = Button.secondary(finChecker + "purge_Frags_URF_1", "Purge 1 Frontier Fragment");
                        purgeFragButtons.add(transact);
                    }
                    Button transact2 = Button.success(finChecker + "gain_CC", "Gain CC");
                    purgeFragButtons.add(transact2);
                    Button transact3 = Button.danger(finChecker + "finishComponentAction", "Done Resolving Fabrication");
                    purgeFragButtons.add(transact3);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, purgeFragButtons);

                } else if ("stallTactics".equalsIgnoreCase(buttonID)) {
                    String secretScoreMsg = "_ _\n" + Helper.getPlayerRepresentation(p1, activeGame, activeGame.getGuild(), true) + " Click a button below to discard an Action Card";
                    List<Button> acButtons = ACInfo.getDiscardActionCardButtons(activeGame, p1, true);
                    if (!acButtons.isEmpty()) {
                        List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(secretScoreMsg, acButtons);
                        ThreadChannel cardsInfoThreadChannel = p1.getCardsInfoThread();
                        for (MessageCreateData message : messageList) {
                            cardsInfoThreadChannel.sendMessage(message).queue();
                        }
                    }
                } else if ("mantlecracking".equalsIgnoreCase(buttonID)) {
                    List<Button> buttons = ButtonHelperAbilities.getMantleCrackingButtons(p1, activeGame);
                    //MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdent(p1)+" Chose to use the mantle cracking ability");
                    String message = "Select the planet you would like to mantle crack";
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                }
            }
            case "getRelic" -> {
                String message = "Click the fragments you'd like to purge. ";
                List<Button> purgeFragButtons = new ArrayList<>();
                int numToBeat = 2 - p1.getUrf();
                if ((p1.hasAbility("fabrication") || p1.getPromissoryNotes().containsKey("bmf"))) {
                    numToBeat = numToBeat - 1;
                    if (p1.getPromissoryNotes().containsKey("bmf") && !p1.hasAbility("fabrication")) {
                        Button transact = Button.primary(finChecker + "resolvePNPlay_bmf", "Play BMF");
                        purgeFragButtons.add(transact);
                    }

                }
                if (p1.getCrf() > numToBeat) {
                    for (int x = numToBeat + 1; (x < p1.getCrf() + 1 && x < 4); x++) {
                        Button transact = Button.primary(finChecker + "purge_Frags_CRF_" + x, "Cultural Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }
                if (p1.getIrf() > numToBeat) {
                    for (int x = numToBeat + 1; (x < p1.getIrf() + 1 && x < 4); x++) {
                        Button transact = Button.success(finChecker + "purge_Frags_IRF_" + x, "Industrial Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }
                if (p1.getHrf() > numToBeat) {
                    for (int x = numToBeat + 1; (x < p1.getHrf() + 1 && x < 4); x++) {
                        Button transact = Button.danger(finChecker + "purge_Frags_HRF_" + x, "Hazardous Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }

                if (p1.getUrf() > 0) {
                    for (int x = 1; x < p1.getUrf() + 1; x++) {
                        Button transact = Button.secondary(finChecker + "purge_Frags_URF_" + x, "Frontier Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }
                Button transact2 = Button.danger(finChecker + "drawRelicFromFrag", "Finish Purging and Draw Relic");
                purgeFragButtons.add(transact2);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, purgeFragButtons);
            }
            case "generic" -> MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Doing unspecified component action. You could ping Fin to add this. ");
            case "absolMOW" -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdent(p1)+ " is exhausting the agenda called Minister of War and spending a strategy cc to remove 1 cc from the board");
                if(p1.getStrategicCC() > 0){
                    p1.setStrategicCC(p1.getStrategicCC()-1);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdent(p1)+ " strategy cc went from "+(p1.getStrategicCC()+1)+" to "+p1.getStrategicCC());
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(p1, activeGame, event);
                }
                List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(p1, activeGame, event, "absol");
                MessageChannel channel = ButtonHelper.getCorrectChannel(p1, activeGame);
                MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to remove token.", buttons);
                activeGame.setCurrentReacts("absolMOW", p1.getFaction());
            }
            case "actionCards" -> {
                String secretScoreMsg = "_ _\nClick a button below to play an Action Card";
                List<Button> acButtons = ACInfo.getActionPlayActionCardButtons(activeGame, p1);
                if (!acButtons.isEmpty()) {
                    List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(secretScoreMsg, acButtons);
                    ThreadChannel cardsInfoThreadChannel = p1.getCardsInfoThread();
                    for (MessageCreateData message : messageList) {
                        cardsInfoThreadChannel.sendMessage(message).queue();
                    }
                }

            }
        }

        if (!firstPart.contains("ability") && !firstPart.contains("getRelic")) {
            String message = "Use buttons to end turn or do another action.";
            List<Button> systemButtons = getStartOfTurnButtons(p1, activeGame, true, event);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
        }
        // FileUpload file = GenerateMap.getInstance().saveImage(activeGame, DisplayType.all, event);
    }

    public static void sendMessageToRightStratThread(Player player, Game activeGame, String message, String stratName) {
        sendMessageToRightStratThread(player, activeGame, message, stratName, null);
    }

    public static void sendMessageToRightStratThread(Player player, Game activeGame, String message, String stratName, @Nullable List<Button> buttons) {
        List<ThreadChannel> threadChannels = activeGame.getActionsChannel().getThreadChannels();
        String threadName = activeGame.getName() + "-round-" + activeGame.getRound() + "-" + stratName;
        boolean messageSent = false;
        for (ThreadChannel threadChannel_ : threadChannels) {
            if ((threadChannel_.getName().startsWith(threadName) || threadChannel_.getName().equals(threadName + "WinnuHero"))
                && (!stratName.equalsIgnoreCase("technology") || !activeGame.getComponentAction())) {
                if (buttons == null) {
                    MessageHelper.sendMessageToChannel(threadChannel_, message);
                } else {
                    MessageHelper.sendMessageToChannelWithButtons(threadChannel_, message, buttons);
                }
                messageSent = true;
                break;
            }
        }
        if (messageSent) {
            return;
        }
        if (buttons == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), message);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
        }
    }

    public static void offerNanoforgeButtons(Player player, Game activeGame, GenericInteractionCreateEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            UnitHolder unitHolder = activeGame.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            if (planetReal == null) continue;

            boolean legendaryOrHome = isPlanetLegendaryOrHome(planet, activeGame, false, null);
            if (!legendaryOrHome) {
                buttons.add(Button.success("nanoforgePlanet_" + planet, Helper.getPlanetRepresentation(planet, activeGame)));
            }
        }
        String message = "Use buttons to select which planet to nanoforge";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
    }

    public static void resolvePNPlay(String id, Player player, Game activeGame, GenericInteractionCreateEvent event) {
        boolean longPNDisplay = false;
        PromissoryNoteModel pn = Mapper.getPromissoryNoteByID(id);
        String pnName = pn.getName();
        // String pnOwner = Mapper.getPromissoryNoteOwner(id);
        Player owner = activeGame.getPNOwner(id);
        if (pn.getPlayArea() && !player.isPlayerMemberOfAlliance(owner)) {
            player.setPromissoryNotesInPlayArea(id);
        } else {
            player.removePromissoryNote(id);
            owner.setPromissoryNote(id);
            PNInfo.sendPromissoryNoteInfo(activeGame, owner, false);
            PNInfo.sendPromissoryNoteInfo(activeGame, player, false);
        }
        String emojiToUse = activeGame.isFoWMode() ? "" : owner.getFactionEmoji();
        StringBuilder sb = new StringBuilder(player.getRepresentation() + " played promissory note: " + pnName + "\n");
        sb.append(emojiToUse).append(Emojis.PN);
        String pnText;

        //Handle AbsolMode Political Secret
        if (activeGame.isAbsolMode() && id.endsWith("_ps")) {
            pnText = "Political Secret" + Emojis.Absol
                + ":  *When you cast votes:* You may exhaust up to 3 of the {colour} player's planets and cast additional votes equal to the combined influence value of the exhausted planets. Then return this card to the {colour} player.";
        } else {
            pnText = Mapper.getPromissoryNote(id, longPNDisplay);
        }
        sb.append(pnText).append("\n");

        //TERRAFORM TIP
        if ("terraform".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.offerTerraformButtons(player, activeGame, event);
        }
        if ("iff".equalsIgnoreCase(id)) {
            List<Button> buttons = new ArrayList<>(ButtonHelperFactionSpecific.getCreusIFFTypeOptions(activeGame, player));
            String message = getTrueIdentity(player, activeGame) + " select type of wormhole you wish to drop";
            MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, activeGame), message, buttons);
        }
        if ("ms".equalsIgnoreCase(id)) {
            List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, activeGame, "2gf", "placeOneNDone_skipbuild"));
            if (owner.getStrategicCC() > 0) {
                owner.setStrategicCC(owner.getStrategicCC() - 1);
                MessageHelper.sendMessageToChannel(getCorrectChannel(owner, activeGame),
                    getTrueIdentity(owner, activeGame) + " lost a command counter from strategy pool due to a Military Support play");
            }
            String message = getTrueIdentity(player, activeGame) + " Use buttons to drop 2 infantry on a planet";
            MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, activeGame), message, buttons);
        }
        if (!activeGame.isAbsolMode() && id.endsWith("_ps")) {
            MessageHelper.sendMessageToChannel(getCorrectChannel(owner, activeGame), getTrueIdentity(owner, activeGame) +" due to a play of your PS, you will be unable to vote in agenda (unless you have xxcha alliance). The bot doesnt enforce the other restrictions regarding no abilities, but you should abide by them.");
            activeGame.setCurrentReacts("AssassinatedReps", activeGame.getFactionsThatReactedToThis("AssassinatedReps")+owner.getFaction());
        }
        

        //Fog of war ping
        if (activeGame.isFoWMode()) {
            // Add extra message for visibility
            FoWHelper.pingAllPlayersWithFullStats(activeGame, event, player, sb.toString());
        }
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), sb.toString());
        if ("fires".equalsIgnoreCase(id)) {
            player.addTech("ws");
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " acquired Warsun tech");
            owner.setFleetCC(owner.getFleetCC() - 1);
            String reducedMsg = Helper.getPlayerRepresentation(owner, activeGame, activeGame.getGuild(), true) + " reduced your fleet cc by 1 due to fires being played";
            if (activeGame.isFoWMode()) {
                MessageHelper.sendMessageToChannel(owner.getPrivateChannel(), reducedMsg);
            } else {
                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), reducedMsg);
            }
        }
        if (id.endsWith("_ta")) {
            int comms = owner.getCommodities();
            owner.setCommodities(0);
            String reducedMsg = Helper.getPlayerRepresentation(owner, activeGame, activeGame.getGuild(), true) + " your TA was played.";
            String reducedMsg2 = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " you gained tgs equal to the number of comms the player had (your tgs went from "
                + player.getTg() + "tgs to -> " + (player.getTg() + comms) + "tgs). Please follow up with the player if this number seems off";
            player.setTg(player.getTg() + comms);
            ButtonHelperFactionSpecific.resolveDarkPactCheck(activeGame, owner, player, owner.getCommoditiesTotal(), event);
            MessageHelper.sendMessageToChannel(getCorrectChannel(owner, activeGame), reducedMsg);
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), reducedMsg2);
        }
        if (("favor".equalsIgnoreCase(id))) {
            if (owner.getStrategicCC() > 0) {
                owner.setStrategicCC(owner.getStrategicCC() - 1);
                String reducedMsg = Helper.getPlayerRepresentation(owner, activeGame, activeGame.getGuild(), true) + " reduced your strategy cc by 1 due to your PN getting played";
                if (activeGame.isFoWMode()) {
                    MessageHelper.sendMessageToChannel(owner.getPrivateChannel(), reducedMsg);
                } else {
                    MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), reducedMsg);
                }
                new RevealAgenda().revealAgenda(event, false, activeGame, activeGame.getMainGameChannel());
                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), "Political Facor (xxcha PN) was played");
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "PN owner did not have a strategy cc, agenda not vetod");
            }
        }
        if (("scepter".equalsIgnoreCase(id))) {
            String message = getTrueIdentity(player, activeGame) + " Use buttons choose which system to mahact diplo";
            MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, activeGame), message, Helper.getPlanetSystemDiploButtons(event, player, activeGame, false, owner));
        }
        if (("rider".equalsIgnoreCase(id))) {
            String riderName = "Keleres Rider";
            String finsFactionCheckerPrefix = "FFCC_"+player.getFaction()+"_";

            List<Button> riderButtons = AgendaHelper.getAgendaButtons(riderName, activeGame, finsFactionCheckerPrefix);
            List<Button> afterButtons = AgendaHelper.getAfterButtons(activeGame);
            MessageHelper.sendMessageToChannelWithFactionReact(activeGame.getMainGameChannel(), "Please select your rider target", activeGame, player, riderButtons);
            MessageHelper.sendMessageToChannelWithPersistentReacts(activeGame.getMainGameChannel(), "Please indicate no afters again.", activeGame, afterButtons, "after");

        }
        if("dspnedyn".equalsIgnoreCase(id)){
            String riderName = "Edyn Rider";
            String finsFactionCheckerPrefix = "FFCC_"+player.getFaction()+"_";

            List<Button> riderButtons = AgendaHelper.getAgendaButtons(riderName, activeGame, finsFactionCheckerPrefix);
            List<Button> afterButtons = AgendaHelper.getAfterButtons(activeGame);
            MessageHelper.sendMessageToChannelWithFactionReact(activeGame.getMainGameChannel(), "Please select your rider target", activeGame, player, riderButtons);
            MessageHelper.sendMessageToChannelWithPersistentReacts(activeGame.getMainGameChannel(), "Please indicate no afters again.", activeGame, afterButtons, "after");
        }
        PNInfo.sendPromissoryNoteInfo(activeGame, player, false);
        PNInfo.sendPromissoryNoteInfo(activeGame, owner, false);
        if("spynet".equalsIgnoreCase(id)){
            ButtonHelperFactionSpecific.offerSpyNetOptions(player);
        }
        if("gift".equalsIgnoreCase(id)){
            ButtonHelper.startActionPhase(event, activeGame);
        }
    }

    public static void offerSpeakerButtons(Game activeGame, Player player) {
        String assignSpeakerMessage = "Please, before you draw your action cards or look at agendas, click a faction below to assign Speaker " + Emojis.SpeakerToken;
        List<Button> assignSpeakerActionRow = getAssignSpeakerButtons(activeGame);
        MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), assignSpeakerMessage, assignSpeakerActionRow);
    }

    private static List<Button> getAssignSpeakerButtons(Game activeGame) {
        List<Button> assignSpeakerButtons = new ArrayList<>();
        for (Player player : activeGame.getPlayers().values()) {
            if (player.isRealPlayer() && !player.getUserID().equals(activeGame.getSpeaker())) {
                String faction = player.getFaction();
                if (faction != null && Mapper.isFaction(faction)) {
                    Button button = Button.secondary("assignSpeaker_" + faction, " ");
                    String factionEmojiString = player.getFactionEmoji();
                    button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                    assignSpeakerButtons.add(button);
                }
            }
        }
        return assignSpeakerButtons;
    }

}