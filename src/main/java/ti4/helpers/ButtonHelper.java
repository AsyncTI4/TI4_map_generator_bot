package ti4.helpers;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import ti4.buttons.ButtonListener;
import ti4.commands.agenda.RevealAgenda;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.explore.ExpFrontier;
import ti4.commands.explore.ExploreAndDiscard;
import ti4.commands.explore.SendFragments;
import ti4.commands.leaders.UnlockLeader;
import ti4.commands.planet.PlanetRefresh;
import ti4.commands.special.CombatRoll;
import ti4.commands.special.KeleresHeroMentak;
import ti4.commands.special.StellarConverter;
import ti4.commands.status.Cleanup;
import ti4.commands.status.ListTurnOrder;
import ti4.commands.tokens.AddCC;
import ti4.commands.tokens.AddFrontierTokens;
import ti4.commands.tokens.AddToken;
import ti4.commands.tokens.RemoveCC;
import ti4.commands.units.AddUnits;
import ti4.commands.units.MoveUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.GenerateMap;
import ti4.generator.GenerateTile;
import ti4.generator.Mapper;
import ti4.helpers.DiceHelper.Die;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;
import ti4.model.PromissoryNoteModel;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.model.TechnologyModel.TechnologyType;

public class ButtonHelper {

    public static void resolveInfantryDeath(Game activeGame, Player player, int amount) {
        for (int x = 0; x < amount; x++) {
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), rollInfantryRevival(activeGame, player));
        }
    }

    public static List<Button> getDacxiveButtons(Game activeGame, Player player, String planet) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("dacxive_" + planet, "Resolve Dacxive"));
        buttons.add(Button.danger("deleteButtons", "No Dacxive"));
        return buttons;
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

        Map<String, String> unitRepresentation = Mapper.getUnitImageSuffixes();
        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        String cID = Mapper.getColorID(player.getColor());
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null) {
            }
            UnitHolder unitHolder = entry.getValue();
            HashMap<String, Integer> units = unitHolder.getUnits();
            if (unitHolder instanceof Planet planet) {
            } else {
                Map<String, Integer> tileUnits = new HashMap<>(units);
                for (Map.Entry<String, Integer> unitEntry : tileUnits.entrySet()) {
                    String key = unitEntry.getKey();
                    if (key.endsWith("gf.png") || key.endsWith("mf.png")
                        || ((!player.hasFF2Tech() && key.endsWith("ff.png")) || (cabal != null && (key.endsWith("ff.png") || key.endsWith("sd.png"))))) {
                        continue;
                    }
                    for (String unitRepresentationKey : unitRepresentation.keySet()) {
                        if (key.endsWith(unitRepresentationKey) && key.contains(cID)) {
                            String unitKey = key.replace(cID + "_", "");
                            int totalUnits = unitEntry.getValue();
                            unitKey = unitKey.replace(".png", "");
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
        String msg = Helper.getEmojiFromDiscord(unit.toLowerCase()) + " rolled a " + d1.getResult();
        if (damaged) {
            msg = "A damaged " + msg;
        }
        if (d1.isSuccess()) {
            msg = msg + " and survived. May you always be so lucky.";
        } else {
            String key = Mapper.getUnitID(AliasHandler.resolveUnit(unit), player.getColor());
            new RemoveUnits().removeStuff(event, tile, 1, "space", key, player.getColor(), damaged, activeGame);
            msg = msg + " and failed. Condolences for your loss.";
            if (cabal != null && cabal != player && !ButtonHelperFactionSpecific.isCabalBlockadedByPlayer(player, activeGame, cabal)) {
                ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, 1, unit, event);
            }
        }

        return msg;
    }

    public static String rollInfantryRevival(Game activeGame, Player player) {

        Die d1 = new Die(6);
        if (player.hasTech("so2")) {
            d1 = new Die(5);
        }
        String msg = Helper.getEmojiFromDiscord("infantry") + " rolled a " + d1.getResult();
        if (player.hasTech("cl2")) {
            msg = Helper.getEmojiFromDiscord("infantry") + " died";

        }
        if (d1.isSuccess() || player.hasTech("cl2")) {
            msg = msg + " and revived. You will be prompted to place them on a planet in your HS at the start of your next turn.";
            player.setStasisInfantry(player.getStasisInfantry() + 1);
        } else {
            msg = msg + " and failed. No revival";
        }
        return getIdent(player) + " " + msg;
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
            UnitHolder unitHolder = activeGame.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            boolean oneOfThree = planetReal != null && planetReal.getOriginalPlanetType() != null && ("industrial".equalsIgnoreCase(planetReal.getOriginalPlanetType())
                || "cultural".equalsIgnoreCase(planetReal.getOriginalPlanetType()) || "hazardous".equalsIgnoreCase(planetReal.getOriginalPlanetType()));
            if (planetReal != null && oneOfThree && !types.contains(planetReal.getOriginalPlanetType())) {
                types.add(planetReal.getOriginalPlanetType());
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
                buttons.add(Button.success("statusInfRevival_" + unitHolder.getName() + "_1", "Place 1 infantry on " + Helper.getPlanetRepresentation(unitHolder.getName(), activeGame)));
                if (player.getStasisInfantry() > 1) {
                    buttons.add(Button.success("statusInfRevival_" + unitHolder.getName() + "_" + player.getStasisInfantry(),
                        "Place " + player.getStasisInfantry() + " infantry on " + Helper.getPlanetRepresentation(unitHolder.getName(), activeGame)));

                }
            }
        }
        return buttons;

    }

    public static List<Button> getArcExpButtons(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        List<String> types = getTypesOfPlanetPlayerHas(activeGame, player);
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

    public static void resolveArcExpButtons(Game activeGame, Player player, String buttonID, ButtonInteractionEvent event, String trueIdentity) {
        String type = buttonID.replace("arcExp_", "");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            String cardID = activeGame.drawExplore(type);
            sb.append(new ExploreAndDiscard().displayExplore(cardID)).append(System.lineSeparator());
            String card = Mapper.getExplore(cardID);
            String[] cardInfo = card.split(";");
            String cardType = cardInfo[3];
            if (cardType.equalsIgnoreCase(Constants.FRAGMENT)) {
                sb.append(trueIdentity).append(" Gained relic fragment\n");
                player.addFragment(cardID);
                activeGame.purgeExplore(cardID);
            }
        }
        MessageChannel channel = getCorrectChannel(player, activeGame);
        MessageHelper.sendMessageToChannel(channel, sb.toString());
        event.getMessage().delete().queue();
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
            Button release = Button.secondary("getReleaseButtons", "Release captured units").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("cabal")));
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
        Message mainMessage = mainGameChannel.retrieveMessageById(messageId).completeAfter(500,
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
                reactionEmoji = Emoji.fromFormatted(Helper.getRandomizedEmoji(index, messageId));
            }
            MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
            if (reaction == null) {
                playersWhoAreMissed.add(player);
            }
        }
        return playersWhoAreMissed;
    }

    public static boolean canIBuildGFInSpace(Game activeGame, Player player, Tile tile, String kindOfBuild) {
        HashMap<String, UnitHolder> unitHolders = tile.getUnitHolders();

        if ("freelancers".equalsIgnoreCase(kindOfBuild) || "genericBuild".equalsIgnoreCase(kindOfBuild)) {
            return true;
        }
        String colorID = Mapper.getColorID(player.getColor());
        String mechKey;
        for (UnitHolder unitHolder : unitHolders.values()) {
            if (unitHolder instanceof Planet) {
                continue;
            }
            if (unitHolder.getUnits() == null || unitHolder.getUnits().isEmpty()) continue;
            mechKey = colorID + "_sd.png";
            if (unitHolder.getUnits().get(mechKey) != null) {
                return true;
            }
            mechKey = colorID + "_gf.png";
            if (unitHolder.getUnits().get(mechKey) != null && "arborec".equalsIgnoreCase(player.getFaction())) {
                return true;
            }
            mechKey = colorID + "_mf.png";
            if (unitHolder.getUnits().get(mechKey) != null && "arborec".equalsIgnoreCase(player.getFaction())) {
                return true;
            }
        }

        return player.getTechs().contains("mr") && ("Supernova".equalsIgnoreCase(tile.getRepresentation()) || "Nova Seed".equalsIgnoreCase(tile.getRepresentation()));
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
        if (player.getRelics().contains("absol_codex")) {
            amount = amount + 1;
            activeGame.drawActionCard(player.getUserID());
            message = message + " Absol Codex has been accounted for.";
        }

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
        addReaction(event, true, false, message, "");
        checkACLimit(activeGame, event, player);
        activeGame.setACDrawStatusInfo(activeGame.getACDrawStatusInfo() + "_" + player.getFaction());
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
                    ButtonHelperFactionSpecific.resolveArtunoCheck(player, activeGame, numOfNeighbors);
                    MessageHelper.sendMessageToChannel(channel, message.toString());
                    ButtonHelperFactionSpecific.pillageCheck(player, activeGame);
                }
            }
        }
    }

    public static int getNumberOfInfantryOnPlanet(String planetName, Game activeGame, Player player) {
        String colorID = Mapper.getColorID(player.getColor());
        UnitHolder unitHolder = getUnitHolderFromPlanetName(planetName, activeGame);
        String infKey = colorID + "_gf.png";
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
        String mechKey = colorID + "_mf.png";
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

        String activePlayerident = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true);
        MessageChannel channel = activeGame.getActionsChannel();
        if (justChecking) {
            Player ghostPlayer = Helper.getPlayerFromColorOrFaction(activeGame, "ghost");
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
                    ButtonHelperFactionSpecific.resolveArtunoCheck(nonActivePlayer, activeGame, 4);
                    ButtonHelperFactionSpecific.pillageCheck(nonActivePlayer, activeGame);
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
            if (nonActivePlayer.hasUnit("mahact_mech") && nonActivePlayer.hasMechInSystem(activeSystem) && nonActivePlayer.getMahactCC().contains(player.getColor())) {
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
                    Button lookAtACs = Button.success(fincheckerForNonActive + "yssarilcommander_ac_" + player.getFaction(), "Look at ACs");
                    Button lookAtPNs = Button.success(fincheckerForNonActive + "yssarilcommander_pn_" + player.getFaction(), "Look at PNs");
                    Button lookAtSOs = Button.success(fincheckerForNonActive + "yssarilcommander_so_" + player.getFaction(), "Look at SOs");
                    Button Decline2 = Button.danger(fincheckerForNonActive + "deleteButtons", "Decline Commander");
                    List<Button> buttons = List.of(lookAtACs, lookAtPNs, lookAtSOs, Decline2);
                    MessageHelper.sendMessageToChannelWithButtons(channel, ident + " use buttons to resolve Yssaril commander ", buttons);
                }
            }
            List<String> pns = new ArrayList<>(player.getPromissoryNotesInPlayArea());
            for (String pn : pns) {
                Player pnOwner = activeGame.getPNOwner(pn);
                if (!pnOwner.isRealPlayer() || !pnOwner.getFaction().equalsIgnoreCase(nonActivePlayer.getFaction())) {
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

    public static List<Button> getPsychoTechPlanets(Game activeGame, Player player){
         List<Button> buttons = new ArrayList<>();
         for (String planet : player.getReadiedPlanets()) {
            if ((Mapper.getPlanet(planet).getTechSpecialties() != null && Mapper.getPlanet(planet).getTechSpecialties().size() > 0) || checkForTechSkipAttachments(activeGame, planet)) {
                buttons.add(Button.success("psychoExhaust_" + planet, "Exhaust " + Helper.getPlanetRepresentation(planet, activeGame)));
            }
        }
        buttons.add(Button.danger("deleteButtons", "Delete Buttons"));
        return buttons;
    }
    public static void resolvePsychoExhaust(Game activeGame, ButtonInteractionEvent event, Player player, String buttonID){
        int oldTg = player.getTg();
        player.setTg(oldTg+1);
        String planet = buttonID.split("_")[1];
        player.exhaustPlanet(planet);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), getIdent(player) + " exhausted "+Helper.getPlanetRepresentation(planet, activeGame)+ " and gained 1tg ("+oldTg+"->"+player.getTg()+")");
        ButtonHelperFactionSpecific.pillageCheck(player, activeGame);
        ButtonHelperFactionSpecific.resolveArtunoCheck(player, activeGame, 1);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void bioStimsReady(Game activeGame, GenericInteractionCreateEvent event, Player player, String buttonID) {
        buttonID = buttonID.replace("biostimsReady_", "");
        String last = buttonID.substring(buttonID.lastIndexOf("_") + 1);
        if (buttonID.contains("tech_")) {
            player.refreshTech(last);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), Helper.getPlayerRepresentation(player, activeGame) + " readied tech: " + Helper.getTechRepresentation(last));
        } else {
            player.refreshPlanet(last);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), Helper.getPlayerRepresentation(player, activeGame) + " readied planet: " + Helper.getPlanetRepresentation(last, activeGame));
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
        File file = GenerateMap.getInstance().saveImage(activeGame, DisplayType.all, event);
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
                    buttonsWeb.add(Button.secondary("showGameAgain", "Show Game"));
                    MessageHelper.sendFileToChannelWithButtonsAfter(threadChannel_, file, "", buttonsWeb);

                }
            }
        } else {
            MessageHelper.sendFileToChannel(event.getMessageChannel(), file);
            foundsomething = true;
        }
        if (!foundsomething) {

            List<Button> buttonsWeb = new ArrayList<>();
            if (!activeGame.isFoWMode()) {
                Button linkToWebsite = Button.link("https://ti4.westaddisonheavyindustries.com/game/" + activeGame.getName(), "Website View");
                buttonsWeb.add(linkToWebsite);
            }
            buttonsWeb.add(Button.success("cardsInfo", "Cards Info"));
            buttonsWeb.add(Button.secondary("showGameAgain", "Show Game"));
            MessageHelper.sendFileToChannelWithButtonsAfter(event.getMessageChannel(), file, "", buttonsWeb);

        }

    }

    public static boolean nomadHeroAndDomOrbCheck(Player player, Game activeGame, Tile tile) {
        if (activeGame.getDominusOrbStatus()) {
            return true;
        }
        return player.getLeader("nomadhero").map(Leader::isActive).orElse(false);
    }

    public static int getAllTilesWithAlphaNBetaNUnits(Player player, Game activeGame) {
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
            case "dihmohn" -> shouldBeUnlocked = true;
            case "letnev" -> shouldBeUnlocked = true;
            case "hacan" -> {
                if (player.getTg() > 9) {
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
                int num = getNumberOfUnitsOnTheBoard(activeGame, player, "csd");
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
                if (getTilesOfPlayersSpecificUnit(activeGame, player, "mech").size() > 2) {
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
            new UnlockLeader().unlockLeader(event, faction + "commander", activeGame, player);
        }
    }

    public static int getAmountOfSpecificUnitsOnPlanets(Player player, Game activeGame, String unit) {
        int num = 0;
        for (Tile tile : activeGame.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder instanceof Planet planet) {
                    String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unit), player.getColor());
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
                if (planet.getUnits().containsKey(Mapper.getUnitID(AliasHandler.resolveUnit(unit), player.getColor()))) {
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
        String ident = player.getFactionEmoji();
        String msg = ident + " removed CC from " + tileRep;
        if (whatIsItFor.contains("mahactAgent")) {
            String faction = whatIsItFor.replace("mahactAgent", "");
            msg = getTrueIdentity(player, activeGame) + " " + msg + " using Mahact agent";
            player = Helper.getPlayerFromColorOrFaction(activeGame, faction);
        }
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
        for (String plan : player.getPlanets()) {
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
        File systemWithContext = GenerateTile.getInstance().saveImage(activeGame, context, tile.getPosition(), event, p1);
        MessageHelper.sendMessageWithFile(tc, systemWithContext, "Picture of system", false);
        List<Button> buttons = getButtonsForPictureCombats(activeGame, tile.getPosition(), p1, p2, spaceOrGround);
        MessageHelper.sendMessageToChannelWithButtons(tc, "", buttons);
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
            buttons.add(Button.secondary(finChecker + "exhaustAgent_titansagent", "Use Titans Agent").withEmoji(Emoji.fromFormatted(Emojis.Titans)));
        }

        Player sol = Helper.getPlayerFromUnlockedLeader(activeGame, "solagent");
        if (!activeGame.isFoWMode() && sol != null && sol.hasUnexhaustedLeader("solagent") && "ground".equalsIgnoreCase(groundOrSpace)) {
            String finChecker = "FFCC_" + sol.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "exhaustAgent_solagent", "Use Sol Agent").withEmoji(Emoji.fromFormatted(Emojis.Sol)));
        }

        Player letnev = Helper.getPlayerFromUnlockedLeader(activeGame, "letnevagent");
        if ((!activeGame.isFoWMode() || letnev == p1) && letnev != null && letnev.hasUnexhaustedLeader("letnevagent") && "space".equalsIgnoreCase(groundOrSpace)) {
            String finChecker = "FFCC_" + letnev.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "exhaustAgent_letnevagent", "Use Letnev Agent").withEmoji(Emoji.fromFormatted(Emojis.Letnev)));
        }

        Player nomad = Helper.getPlayerFromUnlockedLeader(activeGame, "nomadagentthundarian");
        if ((!activeGame.isFoWMode() || nomad == p1) && nomad != null && nomad.hasUnexhaustedLeader("nomadagentthundarian")) {
            String finChecker = "FFCC_" + nomad.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "exhaustAgent_nomadagentthundarian", "Use Thundarian").withEmoji(Emoji.fromFormatted(Emojis.Nomad)));
        }

        Player yin = Helper.getPlayerFromUnlockedLeader(activeGame, "yinagent");
        if ((!activeGame.isFoWMode() || yin == p1) && yin != null && yin.hasUnexhaustedLeader("yinagent")) {
            String finChecker = "FFCC_" + yin.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "yinagent_" + pos, "Use Yin Agent").withEmoji(Emoji.fromFormatted(Emojis.Yin)));
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
        if ("space".equalsIgnoreCase(groundOrSpace)) {
            buttons.add(Button.secondary("announceARetreat", "Announce A Retreat"));
        }
        if ("space".equalsIgnoreCase(groundOrSpace)) {
            buttons.add(Button.danger("retreat_" + pos, "Retreat"));
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
                    buttons.add(Button.secondary(finChecker + "utilizeSolCommander_" + unitH.getName(), "Use Sol Commander on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.Sol)));
                }
                if (activeGame.playerHasLeaderUnlockedOrAlliance(p2, "solcommander") && !activeGame.isFoWMode() && "ground".equalsIgnoreCase(groundOrSpace)) {
                    String finChecker = "FFCC_" + p2.getFaction() + "_";
                    buttons.add(Button.secondary(finChecker + "utilizeSolCommander_" + unitH.getName(), "Use Sol Commander on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.Sol)));
                }
                if (p1.hasAbility("indoctrination") && "ground".equalsIgnoreCase(groundOrSpace)) {
                    String finChecker = "FFCC_" + p1.getFaction() + "_";
                    buttons.add(Button.secondary(finChecker + "initialIndoctrination_" + unitH.getName(), "Indoctrinate on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.Yin)));
                }
                if (p2.hasAbility("indoctrination") && !activeGame.isFoWMode() && "ground".equalsIgnoreCase(groundOrSpace)) {
                    String finChecker = "FFCC_" + p2.getFaction() + "_";
                    buttons.add(Button.secondary(finChecker + "initialIndoctrination_" + unitH.getName(), "Indoctrinate on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.Yin)));
                }
            }
            if (nameOfHolder.equalsIgnoreCase("space")) {
                buttons.add(Button.secondary("combatRoll_" + pos + "_" + unitH.getName(), "Roll Space Combat"));
            } else {
                buttons.add(Button.secondary("combatRoll_" + pos + "_" + unitH.getName(), "Roll Ground Combat for " + nameOfHolder + ""));
            }

        }
        return buttons;
    }

    public static boolean isPlanetLegendaryOrHome(String planetName, Game activeGame, boolean onlyIncludeYourHome, Player p1) {
        UnitHolder unitHolder = getUnitHolderFromPlanetName(planetName, activeGame);
        Planet planetHolder = (Planet) unitHolder;
        boolean hasAbility = planetHolder.isHasAbility()
            || planetHolder.getTokenList().stream().anyMatch(token -> token.contains("nanoforge") || token.contains("legendary") || token.contains("consulate"));
        boolean oneOfThree = planetHolder.getOriginalPlanetType() != null && ("industrial".equalsIgnoreCase(planetHolder.getOriginalPlanetType())
            || "cultural".equalsIgnoreCase(planetHolder.getOriginalPlanetType()) || "hazardous".equalsIgnoreCase(planetHolder.getOriginalPlanetType()));
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
        int armadaValue = 0;
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
                if ((unit.getIsShip() != null && unit.getIsShip())) {
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
                }
                if (numFighter2sFleet + numOfCapitalShips > fleetCap) {
                    fleetSupplyViolated = true;
                }
            }
        }
        if (numOfCapitalShips > 4 && !fleetSupplyViolated) {
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
                    String mechKey = colorID + "_mf.png";
                    String infKey = colorID + "_gf.png";
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
        Player p2 = Helper.getPlayerFromColorOrFaction(activeGame, faction);
        String mechOrInf = buttonID.split("_")[3];
        String msg = ident + " used the special Mecatol Rex power to remove 1 " + mechOrInf + " on " + Helper.getPlanetRepresentation(planet, activeGame);
        new RemoveUnits().unitParsing(event, p2.getColor(), activeGame.getTileFromPlanet(planet), "1 " + mechOrInf + " " + planet, activeGame);
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), msg);
        event.getMessage().delete().queue();
    }

    public static List<Button> getEchoAvailableSystems(Game activeGame, Player player){
        List<Button> buttons = new ArrayList<>();
        for(Tile tile : activeGame.getTileMap().values()){
            if(tile.getUnitHolders().size() < 2){
                buttons.add(Button.success("echoPlaceFrontier_"+tile.getPosition(), tile.getRepresentationForButtons(activeGame, player)));
            }
        }
        return buttons;
    }
    public static void resolveEchoPlaceFrontier(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID){
        String pos = buttonID.split("_")[1];
        Tile tile = activeGame.getTileByPosition(pos);
        AddToken.addToken(event, tile, Constants.FRONTIER, activeGame);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdent(player) + " placed a frontier token in "+tile.getRepresentationForButtons(activeGame, player));
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
        if (player.getTechs().contains("bs") && !player.getExhaustedTechs().contains("bs")) {
            endButtons.add(Button.success(finChecker + "exhaustTech_bs", "Exhaust Bio-Stims"));
        }
        if (player.hasUnexhaustedLeader("naazagent")) {
            endButtons.add(Button.success(finChecker + "exhaustAgent_naazagent", "Use NRA Agent").withEmoji(Emoji.fromFormatted(Emojis.Naaz)));
        }

        endButtons.add(Button.danger("deleteButtons", "Delete these buttons"));
        return endButtons;
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
                    Button strategicAction = Button.success(finChecker + "strategicAction_" + SC, "Play SC #" + SC).withEmoji(Emoji.fromFormatted(Helper.getSCEmojiFromInteger(SC)));
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

        }
        if (doneActionThisTurn) {
            if (getEndOfTurnAbilities(player, activeGame).size() > 1) {
                startButtons.add(Button.primary("endOfTurnAbilities", "Do End Of Turn Ability (" + (getEndOfTurnAbilities(player, activeGame).size() - 1) + ")"));
            }
            Button pass = Button.danger(finChecker + "turnEnd", "End Turn");
            startButtons.add(pass);
        } else {
            if (player.getTechs().contains("cm")) {
                Button chaos = Button.secondary("startChaosMapping", "Use Chaos Mapping").withEmoji(Emoji.fromFormatted(Emojis.Saar));
                startButtons.add(chaos);
            }
            if (player.hasTech("td") && !player.getExhaustedTechs().contains("td")) {
                Button transit = Button.secondary(finChecker + "exhaustTech_td", "Exhaust Transit Diodes");
                transit = transit.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("Cybernetictech")));
                startButtons.add(transit);
            }
        }
        if (player.hasTech("pa") && ButtonHelper.getPsychoTechPlanets(activeGame, player).size() > 1) {
                Button psycho = Button.success(finChecker + "getPsychoButtons", "Use Psychoarcheology");
                psycho = psycho.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("Biotictech")));
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

    public static List<Button> getPossibleRings(Player player, Game activeGame) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> ringButtons = new ArrayList<>();
        Tile centerTile = activeGame.getTileByPosition("000");
        Button rex = Button.success(finChecker + "ringTile_000", centerTile.getRepresentationForButtons(activeGame, player));
        ringButtons.add(rex);
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
                String card = Mapper.getExplore(cardID);
                String[] cardInfo1 = card.split(";");
                String name1 = cardInfo1[0];
                String card2 = Mapper.getExplore(cardID2);
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
            } else {
                new ExpFrontier().expFront(event, tile, activeGame, player);
            }

        }
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
            Emoji emoji = Emoji.fromFormatted(Helper.getEmojiFromDiscord(trait));
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

        if (!activeGame.getNaaluAgent() && !AddCC.hasCC(event, player.getColor(), tile)) {
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
                String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unit), player.getColor());
                tile.addUnitDamage("space", unitID, amount);
            }
        }

        activeGame.resetCurrentMovedUnitsFrom1TacticalAction();
        String colorID = Mapper.getColorID(player.getColor());
        String mechKey = colorID + "_mf.png";
        String infKey = colorID + "_gf.png";

        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null) {
                representation = name;
            }
            UnitHolder unitHolder = entry.getValue();
            if (unitHolder instanceof Planet planet) {
                int limit;

                if (tile.getUnitHolders().get("space").getUnits() != null && tile.getUnitHolders().get("space").getUnits().get(infKey) != null) {
                    limit = tile.getUnitHolders().get("space").getUnits().get(infKey);
                    for (int x = 1; x < limit + 1; x++) {
                        if (x > 2) {
                            break;
                        }
                        Button validTile2 = Button
                            .danger(finChecker + "landUnits_" + tile.getPosition() + "_" + x + "infantry_" + representation,
                                "Land " + x + " Infantry on " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame))
                            .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("infantry")));
                        buttons.add(validTile2);
                    }
                }
                if (planet.getUnits().get(infKey) != null) {
                    limit = planet.getUnits().get(infKey);
                    for (int x = 1; x < limit + 1; x++) {
                        if (x > 2) {
                            break;
                        }
                        Button validTile2 = Button
                            .success(finChecker + "spaceUnits_" + tile.getPosition() + "_" + x + "infantry_" + representation,
                                "Undo Landing of " + x + " Infantry on " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame))
                            .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("infantry")));
                        buttons.add(validTile2);
                    }
                }

                if (tile.getUnitHolders().get("space").getUnits() != null && tile.getUnitHolders().get("space").getUnits().get(mechKey) != null) {
                    limit = tile.getUnitHolders().get("space").getUnits().get(mechKey);
                    for (int x = 1; x < limit + 1; x++) {
                        if (x > 2) {
                            break;
                        }
                        Button validTile2 = Button
                            .primary(finChecker + "landUnits_" + tile.getPosition() + "_" + x + "mech_" + representation,
                                "Land " + x + " Mech(s) on " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame))
                            .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("mech")));
                        buttons.add(validTile2);
                    }
                }

                if (planet.getUnits().get(mechKey) != null) {
                    for (int x = 1; x < planet.getUnits().get(mechKey) + 1; x++) {
                        if (x > 2) {
                            break;
                        }
                        Button validTile2 = Button
                            .primary(finChecker + "spaceUnits_" + tile.getPosition() + "_" + x + "mech_" + representation,
                                "Undo Landing of " + x + " Mech(s) on " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame))
                            .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("mech")));
                        buttons.add(validTile2);
                    }
                }
            }
        }
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "sardakkcommander")) {
            buttons.addAll(ButtonHelperFactionSpecific.getSardakkCommanderButtons(activeGame, player, event));
        }
        Button rift = Button.success(finChecker + "getRiftButtons_" + tile.getPosition(), "Rift some units").withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("grift")));
        buttons.add(rift);
        if (player.hasAbility("combat_drones") && FoWHelper.playerHasFightersInSystem(player, tile)) {
            Button combatDrones = Button.primary(finChecker + "combatDrones", "Use Combat Drones Ability");
            buttons.add(combatDrones);
        }
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "ghostcommander")) {
            Button ghostC = Button.primary(finChecker + "placeGhostCommanderFF_" + tile.getPosition(), "Place fighter with Ghost Commander")
                .withEmoji(Emoji.fromFormatted(Emojis.Ghost));
            buttons.add(ghostC);
        }
        if (player.hasLeaderUnlocked("muaathero")&&!tile.getTileID().equalsIgnoreCase("18")&&ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, player, "warsun").contains(tile)) {
            Button muaatH = Button.primary(finChecker + "novaSeed_" + tile.getPosition(), "Nova Seed This Tile")
                .withEmoji(Emoji.fromFormatted(Emojis.Muaat));
            buttons.add(muaatH);
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
                String mechKey = colorID + "_mf.png";
                int numMechs = 0;
                if (unitHolder.getUnits() != null) {

                    if (unitHolder.getUnits().get(mechKey) != null) {
                        numMechs = unitHolder.getUnits().get(mechKey);
                    }
                    if (numMechs > 0) {
                        String planetName = "";
                        if (!"space".equalsIgnoreCase(unitHolder.getName())) {
                            planetName = " " + unitHolder.getName();
                        }
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, numMechs + " infantry" + planetName, activeGame);

                        successMessageBuilder.append("\n Put ").append(numMechs).append(" ").append(Helper.getEmojiFromDiscord("infantry")).append(" with the mechs in ")
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
        Map<String, Integer> displacedUnits = activeGame.getCurrentMovedUnitsFrom1System();
        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
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
        Button rift = Button.success(finChecker + "getRiftButtons_" + tile.getPosition(), "Rift some units").withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("grift")));
        buttons.add(rift);
        if (player.hasUnexhaustedLeader("sardakkagent")) {
            buttons.addAll(ButtonHelperFactionSpecific.getSardakkAgentButtons(activeGame, player));
        }
        if (player.hasUnexhaustedLeader("nomadagentmercer")) {
            buttons.addAll(ButtonHelperFactionSpecific.getMercerAgentInitialButtons(activeGame, player));
        }
        Button concludeMove = Button.danger(finChecker + "doneWithTacticalAction", "Conclude tactical action (will DET if applicable)");
        buttons.add(concludeMove);
        return buttons;
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
                messageBuilder.append(prefix).append(" Landed ").append(amount).append(" ").append(damagedMsg).append(Helper.getEmojiFromDiscord(unit.toLowerCase()));
                if (planet == null) {
                    messageBuilder.append("\n");
                } else {
                    messageBuilder.append(" on the planet ").append(Helper.getPlanetRepresentation(planet.toLowerCase(), activeGame)).append("\n");
                }
            } else {
                messageBuilder.append(prefix).append(" ").append(moveOrRemove).append("d ").append(amount).append(" ").append(damagedMsg).append(Helper.getEmojiFromDiscord(unit.toLowerCase()));
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
            buttonRow.add(button);
        }
        if (buttonRow.size() > 0) {
            list.add(ActionRow.of(buttonRow));
        }
        return list;
    }

    public static String getUnitName(String id) {
        String name = "";
        switch (id) {
            case "fs" -> name = "flagship";
            case "ws" -> name = "warsun";
            case "gf" -> name = "infantry";
            case "mf" -> name = "mech";
            case "sd" -> name = "spacedock";
            case "csd" -> name = "cabalspacedock";
            case "pd" -> name = "pds";
            case "ff" -> name = "fighter";
            case "ca" -> name = "cruiser";
            case "dd" -> name = "destroyer";
            case "cv" -> name = "carrier";
            case "dn" -> name = "dreadnought";
        }
        return name;
    }

    public static List<Button> getButtonsForRiftingUnitsInSystem(Player player, Game activeGame, Tile tile) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();

        Map<String, String> unitRepresentation = Mapper.getUnitImageSuffixes();
        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        String cID = Mapper.getColorID(player.getColor());
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null) {
            }
            UnitHolder unitHolder = entry.getValue();
            HashMap<String, Integer> units = unitHolder.getUnits();

            if (unitHolder instanceof Planet planet) {
            } else {
                for (Map.Entry<String, Integer> unitEntry : units.entrySet()) {

                    String key = unitEntry.getKey();

                    if ((!activeGame.playerHasLeaderUnlockedOrAlliance(player, "sardakkcommander") && (key.endsWith("gf.png") || key.endsWith("mf.png")))
                        || (!player.hasFF2Tech() && key.endsWith("ff.png"))) {
                        continue;
                    }

                    for (String unitRepresentationKey : unitRepresentation.keySet()) {
                        if (key.endsWith(unitRepresentationKey) && key.contains(cID)) {

                            String unitKey = key.replace(cID + "_", "");

                            int totalUnits = unitEntry.getValue();
                            unitKey = unitKey.replace(".png", "");
                            unitKey = getUnitName(unitKey);
                            int damagedUnits = 0;
                            if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null) {
                                damagedUnits = unitHolder.getUnitDamage().get(key);
                            }
                            for (int x = 1; x < damagedUnits + 1; x++) {
                                if (x > 2) {
                                    break;
                                }
                                Button validTile2 = Button
                                    .danger(finChecker + "riftUnit_" + tile.getPosition() + "_" + x + unitKey + "damaged", "Rift " + x + " damaged " + unitRepresentation.get(unitRepresentationKey))
                                    .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                            }
                            totalUnits = totalUnits - damagedUnits;
                            for (int x = 1; x < totalUnits + 1; x++) {
                                if (x > 2) {
                                    break;
                                }
                                Button validTile2 = Button.danger(finChecker + "riftUnit_" + tile.getPosition() + "_" + x + unitKey, "Rift " + x + " " + unitRepresentation.get(unitRepresentationKey))
                                    .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                            }
                        }
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

        Map<String, String> unitRepresentation = Mapper.getUnitImageSuffixes();
        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        String cID = Mapper.getColorID(player.getColor());
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null) {
                representation = name;
            }
            UnitHolder unitHolder = entry.getValue();
            HashMap<String, Integer> units = unitHolder.getUnits();

            if (unitHolder instanceof Planet planet) {
                for (Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                    String key = unitEntry.getKey();
                    representation = representation.replace(" ", "").toLowerCase().replace("'", "").replace("-", "");
                    if ((key.endsWith("gf.png") || key.endsWith("mf.png")) && key.contains(cID)) {
                        String unitKey = key.replace(cID + "_", "");
                        unitKey = unitKey.replace(".png", "");
                        unitKey = getUnitName(unitKey);
                        for (int x = 1; x < unitEntry.getValue() + 1; x++) {
                            if (x > 2) {
                                break;
                            }
                            Button validTile2;
                            if (key.contains("gf")) {
                                validTile2 = Button
                                    .danger(finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_" + x + unitKey + "_" + representation,
                                        moveOrRemove + " " + x + " Infantry from " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame))
                                    .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("infantry")));
                            } else {
                                validTile2 = Button
                                    .danger(finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_" + x + unitKey + "_" + representation,
                                        moveOrRemove + " " + x + " Mech from " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame))
                                    .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("mech")));
                            }
                            buttons.add(validTile2);
                        }
                    }
                }

            } else {
                for (Map.Entry<String, Integer> unitEntry : units.entrySet()) {

                    String key = unitEntry.getKey();

                    for (String unitRepresentationKey : unitRepresentation.keySet()) {
                        if (key.endsWith(unitRepresentationKey) && key.contains(cID)) {

                            String unitKey = key.replace(cID + "_", "");

                            int totalUnits = unitEntry.getValue();
                            unitKey = unitKey.replace(".png", "");
                            unitKey = getUnitName(unitKey);
                            int damagedUnits = 0;
                            if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null) {
                                damagedUnits = unitHolder.getUnitDamage().get(key);
                            }
                            for (int x = 1; x < damagedUnits + 1; x++) {
                                if (x > 2) {
                                    break;
                                }
                                Button validTile2 = Button
                                    .danger(finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_" + x + unitKey + "damaged",
                                        moveOrRemove + " " + x + " damaged " + unitRepresentation.get(unitRepresentationKey))
                                    .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                            }
                            totalUnits = totalUnits - damagedUnits;
                            for (int x = 1; x < totalUnits + 1; x++) {
                                if (x > 2) {
                                    break;
                                }
                                Button validTile2 = Button
                                    .danger(finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_" + x + unitKey,
                                        moveOrRemove + " " + x + " " + unitRepresentation.get(unitRepresentationKey))
                                    .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                            }
                        }
                    }
                }
            }

        }
        Button concludeMove;
        Button doAll;
        if ("Remove".equalsIgnoreCase(moveOrRemove)) {
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
                    blabel).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitkey.toLowerCase().replace(" ", ""))));
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
        Map<String, String> unitRepresentation = Mapper.getUnitImageSuffixes();
        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        String cID = Mapper.getColorID(player.getColor());
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null) {
                representation = name;
            }
            UnitHolder unitHolder = entry.getValue();
            HashMap<String, Integer> units = unitHolder.getUnits();
            if (unitHolder instanceof Planet planet) {
                for (Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                    String key = unitEntry.getKey();
                    for (String unitRepresentationKey : unitRepresentation.keySet()) {
                        if (key.endsWith(unitRepresentationKey) && key.contains(cID)) {
                            String unitKey = key.replace(cID + "_", "");
                            unitKey = unitKey.replace(".png", "");
                            unitKey = getUnitName(unitKey);
                            int damagedUnits = 0;
                            if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null) {
                                damagedUnits = unitHolder.getUnitDamage().get(key);
                            }
                            int totalUnits = unitEntry.getValue() - damagedUnits;
                            for (int x = 1; x < totalUnits + 1; x++) {
                                if (x > 2) {
                                    break;
                                }
                                Button validTile2 = Button
                                    .danger(finChecker + "assignHits_" + tile.getPosition() + "_" + x + unitKey + "_" + representation,
                                        "Remove " + x + " " + unitRepresentation.get(unitRepresentationKey) + " from " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame))
                                    .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                                if (key.contains("mf") || (key.contains("pd") && (player.getUnitsOwned().contains("Hel-Titan") || player.getTechs().contains("ht2")))) {
                                    Button validTile3 = Button
                                        .secondary(finChecker + "assignDamage_" + tile.getPosition() + "_" + x + unitKey + "_" + representation,
                                            "Sustain " + x + " " + unitRepresentation.get(unitRepresentationKey) +
                                                " from " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame))
                                        .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                    buttons.add(validTile3);
                                }
                            }
                            for (int x = 1; x < damagedUnits + 1; x++) {
                                if (x > 2) {
                                    break;
                                }
                                Button validTile2 = Button.danger(finChecker + "assignHits_" + tile.getPosition() + "_" + x + unitKey + "_" + representation + "damaged", "Remove " + x + " damaged " +
                                    unitRepresentation.get(unitRepresentationKey) + " from " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame))
                                    .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                            }
                        }
                    }
                }
            } else {
                for (Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                    String key = unitEntry.getKey();
                    for (String unitRepresentationKey : unitRepresentation.keySet()) {
                        if (key.endsWith(unitRepresentationKey) && key.contains(cID)) {
                            String unitKey = key.replace(cID + "_", "");
                            int totalUnits = unitEntry.getValue();
                            unitKey = unitKey.replace(".png", "");
                            unitKey = getUnitName(unitKey);
                            int damagedUnits = 0;
                            if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null) {
                                damagedUnits = unitHolder.getUnitDamage().get(key);
                            }
                            totalUnits = totalUnits - damagedUnits;
                            for (int x = 1; x < damagedUnits + 1; x++) {
                                if (x > 2) {
                                    break;
                                }
                                Button validTile2 = Button.danger(finChecker + "assignHits_" + tile.getPosition() + "_" + x + unitKey + "damaged", "Remove " + x + " damaged " +
                                    unitRepresentation.get(unitRepresentationKey))
                                    .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                            }
                            for (int x = 1; x < totalUnits + 1; x++) {
                                if (x > 2) {
                                    break;
                                }
                                Button validTile2 = Button
                                    .danger(finChecker + "assignHits_" + tile.getPosition() + "_" + x + unitKey, "Remove " + x + " " + unitRepresentation.get(unitRepresentationKey))
                                    .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                            }
                            if ((("mech".equalsIgnoreCase(unitKey) && !activeGame.getLaws().containsKey("articles_war") && player.getUnitsOwned().contains("nomad_mech"))
                                || "dreadnought".equalsIgnoreCase(unitKey) || "warsun".equalsIgnoreCase(unitKey) || "flagship".equalsIgnoreCase(unitKey)
                                || ("cruiser".equalsIgnoreCase(unitKey) && player.hasTech("se2")) || ("carrier".equalsIgnoreCase(unitKey) && player.hasTech("ac2"))) && totalUnits > 0) {
                                Button validTile2 = Button
                                    .secondary(finChecker + "assignDamage_" + tile.getPosition() + "_" + 1 + unitKey, "Sustain " + 1 + " " + unitRepresentation.get(unitRepresentationKey))
                                    .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                            }

                        }
                    }
                }
            }
        }
        Button doAll = Button.secondary(finChecker + "assignHits_" + tile.getPosition() + "_All", "Remove all units");
        Button concludeMove = Button.primary("deleteButtons", "Done removing/sustaining units");
        buttons.add(doAll);
        buttons.add(concludeMove);
        return buttons;
    }

    public static void resolveStellar(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        new StellarConverter().secondHalfOfStellar(activeGame, buttonID.split("_")[1], event);
        event.getMessage().delete().queue();
    }

    public static List<Tile> getTilesOfUnitsWithBombard(Player player, Game activeGame) {
        List<Tile> tilesWithBombard = new ArrayList<>();
        tilesWithBombard.addAll(ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, player, "dreadnought"));
        tilesWithBombard.addAll(ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, player, "warsun"));
        if (player.hasUnit("bentor_flagship") || player.hasUnit("cabal_flagship") || player.hasUnit("letnev_flagship") || player.hasUnit("ghemina_flagship_lord") || player.hasUnit("gledge_flagship")
            || player.hasUnit("kortali_flagship") || player.hasUnit("vaden_flagship") || player.hasUnit("zelian_flagship")) {
            tilesWithBombard.addAll(ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, player, "flagship"));
        }
        if (player.hasUnit("l1z1x_mech")) {
            tilesWithBombard.addAll(ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, player, "mech"));
        }
        if (player.hasUnit("khrask_cruiser")) {
            tilesWithBombard.addAll(ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, player, "cruiser"));
        }
        if (player.hasUnit("lizho_fighter")) {
            tilesWithBombard.addAll(ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, player, "fighter"));
        }
        if (player.hasUnit("mirveda_pds")) {
            tilesWithBombard.addAll(ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, player, "pds"));
        }
        if (player.hasUnit("zelian_infantry")) {
            tilesWithBombard.addAll(ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, player, "infantry"));
        }
        return tilesWithBombard;
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

    public static List<Button> getButtonsForRepairingUnitsInASystem(Player player, Game activeGame, Tile tile) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        Map<String, String> unitRepresentation = Mapper.getUnitImageSuffixes();
        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        String cID = Mapper.getColorID(player.getColor());
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null) {
                representation = name;
            }
            UnitHolder unitHolder = entry.getValue();
            HashMap<String, Integer> units = unitHolder.getUnits();
            if (unitHolder instanceof Planet planet) {
                for (Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                    String key = unitEntry.getKey();
                    for (String unitRepresentationKey : unitRepresentation.keySet()) {
                        if (key.endsWith(unitRepresentationKey) && key.contains(cID)) {
                            String unitKey = key.replace(cID + "_", "");
                            unitKey = unitKey.replace(".png", "");
                            unitKey = getUnitName(unitKey);
                            int damagedUnits = 0;
                            if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null) {
                                damagedUnits = unitHolder.getUnitDamage().get(key);
                            }
                            for (int x = 1; x < damagedUnits + 1; x++) {
                                if (x > 2) {
                                    break;
                                }
                                Button validTile3 = Button
                                    .success(finChecker + "repairDamage_" + tile.getPosition() + "_" + x + unitKey + "_" + representation,
                                        "Repair " + x + " " + unitRepresentation.get(unitRepresentationKey) +
                                            " from " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame))
                                    .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile3);
                            }
                        }
                    }
                }
            } else {
                for (Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                    String key = unitEntry.getKey();
                    for (String unitRepresentationKey : unitRepresentation.keySet()) {
                        if (key.endsWith(unitRepresentationKey) && key.contains(cID)) {
                            String unitKey = key.replace(cID + "_", "");
                            unitKey = unitKey.replace(".png", "");
                            unitKey = getUnitName(unitKey);
                            int damagedUnits = 0;
                            if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null) {
                                damagedUnits = unitHolder.getUnitDamage().get(key);
                            }
                            for (int x = 1; x < damagedUnits + 1; x++) {
                                if (x > 2) {
                                    break;
                                }
                                Button validTile2 = Button.danger(finChecker + "repairDamage_" + tile.getPosition() + "_" + x + unitKey, "Repair " + x + " damaged " +
                                    unitRepresentation.get(unitRepresentationKey))
                                    .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                            }

                        }
                    }
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
        for (String tilePo : adjTiles) {
            Tile tile = activeGame.getTileByPosition(tilePo);
            for (UnitHolder area : tile.getUnitHolders().values()) {
                for (Player p : activeGame.getPlayers().values()) {
                    if (p.isRealPlayer() && !p.getFaction().equalsIgnoreCase(player.getFaction())) {
                        String unitKey1 = Mapper.getUnitID(AliasHandler.resolveUnit("pds"), p.getColor());
                        if (area.getUnits().containsKey(unitKey1) && p.hasPDS2Tech()) {
                            if (!playersWithPds2.contains(p)) {
                                playersWithPds2.add(p);
                            }
                        }
                        if (p.getUnitsOwned().contains("xxcha_mech")) {

                            String unitKey3 = Mapper.getUnitID(AliasHandler.resolveUnit("mech"), p.getColor());
                            if (area.getUnits().containsKey(unitKey3)) {
                                if (!playersWithPds2.contains(p)) {
                                    playersWithPds2.add(p);
                                }
                            }
                        }
                        if (p.getUnitsOwned().contains("xxcha_flagship")) {
                            String unitKey2 = Mapper.getUnitID(AliasHandler.resolveUnit("flagship"), p.getColor());
                            if (area.getUnits().containsKey(unitKey2)) {
                                if (!playersWithPds2.contains(p)) {
                                    playersWithPds2.add(p);
                                }
                            }
                        }
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
        boolean allPicked = true;
        Player privatePlayer =player;
        
        Player nextPlayer = player;
        

        //INFORM FIRST PLAYER IS UP FOR ACTION
        if (nextPlayer != null) {
            msgExtra += "# " + Helper.getPlayerRepresentation(nextPlayer, activeGame) + " is up for an action";
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

    public static void resolveImperialArbiter(ButtonInteractionEvent event, Game activeGame, Player player){
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdent(player) + " decided to use the Imperial Arbiter Law to swap SCs with someone");
        activeGame.removeLaw("arbiter");
        List<Button> buttons = ButtonHelperFactionSpecific.getSwapSCButtons(activeGame, "imperialarbiter", player);
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " choose who you want to swap CCs with", buttons);
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
            msgExtra += " " + Helper.getPlayerRepresentation(nextPlayer, activeGame) + " is up for an action";
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
                MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), "\n Use Buttons to do turn.", getStartOfTurnButtons(privatePlayer, activeGame, false, event));
                if (privatePlayer.getStasisInfantry() > 0) {
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(privatePlayer, activeGame),
                        "Use buttons to revive infantry. You have " + privatePlayer.getStasisInfantry() + " infantry left to revive.", getPlaceStatusInfButtons(activeGame, privatePlayer));
                }
            }
        }
        if(allPicked){
            for(Player p2: activeGame.getRealPlayers()){
                List<Button> buttons = new ArrayList<Button>();
                if(p2.hasTechReady("qdn")&& p2.getTg() >2 && p2.getStrategicCC() > 0){
                    buttons.add(Button.success("startQDN", "Use Quantum Datahub Node"));
                    buttons.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame), ButtonHelper.getTrueIdentity(p2, activeGame) + " you have the opportunity to use QDN", buttons);
                }
                if(activeGame.getLaws().containsKey("arbiter") && activeGame.getLawsInfo().get("arbiter").equalsIgnoreCase(p2.getFaction())){
                    buttons.add(Button.success("startArbiter", "Use Imperial Arbiter"));
                    buttons.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame), ButtonHelper.getTrueIdentity(p2, activeGame) + " you have the opportunity to use QDN", buttons);
                }
            }
        }
    }

    public static void startStatusHomework(GenericInteractionCreateEvent event, Game activeGame) {
        int playersWithSCs = 0;
        activeGame.setCurrentPhase("statusHomework");
        for (Player player2 : activeGame.getPlayers().values()) {
            if (playersWithSCs > 1) {
                new Cleanup().runStatusCleanup(activeGame);
                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), Helper.getGamePing(activeGame.getGuild(), activeGame) + "Status Cleanup Run!");
                playersWithSCs = -30;
                if (!activeGame.isFoWMode()) {
                    DisplayType displayType = DisplayType.map;
                    File stats_file = GenerateMap.getInstance().saveImage(activeGame, displayType, event);
                    MessageHelper.sendFileToChannel(activeGame.getActionsChannel(), stats_file);
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
                    Helper.getPlayerRepresentation(player2, activeGame, activeGame.getGuild(), true)
                        + " Reminder this is the window to do Naalu Hero. You can use the buttons to start the process",
                    buttons);
            }
            if (player2.getRelics() != null && player2.hasRelic("mawofworlds") && activeGame.isCustodiansScored()) {
                MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(),
                    Helper.getPlayerRepresentation(player2, activeGame, activeGame.getGuild(), true)
                        + " Reminder this is the window to do Maw of Worlds");
                MessageHelper.sendMessageToChannelWithButtons(player2.getCardsInfoThread(),
                    Helper.getPlayerRepresentation(player2, activeGame, activeGame.getGuild(), true)
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
                    if (unitHolder.getTokenList() != null
                        && unitHolder.getTokenList().contains("attachment_tombofemphidia.png")) {
                        MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(),
                            Helper.getPlayerRepresentation(player2, activeGame, activeGame.getGuild(), true)
                                + "Reminder this is the window to purge Crown of Emphidia if you want to.");
                        MessageHelper.sendMessageToChannelWithButtons(player2.getCardsInfoThread(),
                            Helper.getPlayerRepresentation(player2, activeGame, activeGame.getGuild(), true)
                                + " You can use these buttons to resolve Crown of Emphidia",
                            getCrownButtons());
                    }
                }
            }
            if (player2.getActionCards() != null && player2.getActionCards().containsKey("summit")
                && !activeGame.isCustodiansScored()) {
                MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(),
                    Helper.getPlayerRepresentation(player2, activeGame, activeGame.getGuild(), true)
                        + "Reminder this is the window to do summit");
            }
            if (player2.getActionCards() != null && (player2.getActionCards().containsKey("investments")
                && !activeGame.isCustodiansScored())) {
                MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(),
                    Helper.getPlayerRepresentation(player2, activeGame, activeGame.getGuild(), true)
                        + "Reminder this is the window to do manipulate investments.");
            }

            if (player2.getActionCards() != null && player2.getActionCards().containsKey("stability")) {
                MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(),
                    Helper.getPlayerRepresentation(player2, activeGame, activeGame.getGuild(), true)
                        + "Reminder this is the window to play political stability.");
            }

            for (String pn : player2.getPromissoryNotes().keySet()) {
                if (!player2.ownsPromissoryNote("ce") && "ce".equalsIgnoreCase(pn)) {
                    String cyberMessage = Helper.getPlayerRepresentation(player2, activeGame, event.getGuild(), true)
                        + " reminder to use cybernetic enhancements!";
                    MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(),
                        cyberMessage);
                }
            }
        }
        String message2 = "Resolve status homework using the buttons. Only the Ready for [X] button is essential to hit, all others are optional. ";
        activeGame.setACDrawStatusInfo("");
        Button draw1AC = Button.success("drawStatusACs", "Draw Status Phase ACs").withEmoji(Emoji.fromFormatted(Emojis.ActionCard));
        Button getCCs = Button.success("redistributeCCButtons", "Redistribute, Gain, & Confirm CCs").withEmoji(Emoji.fromFormatted("🔺"));
        boolean custodiansTaken = activeGame.isCustodiansScored();
        Button passOnAbilities;
        if (custodiansTaken) {
            passOnAbilities = Button.danger("pass_on_abilities", "Ready For Agenda");
            message2 = message2
                + " Ready for Agenda means you are done playing/passing on playing political stability, ancient burial sites, maw of worlds, Naalu hero, and crown of emphidia.";
        } else {
            passOnAbilities = Button.danger("pass_on_abilities", "Ready For Strategy Phase");
            message2 = message2
                + " Ready for Strategy Phase means you are done playing/passing on playing political stability, summit, and manipulate investments. ";
        }
        List<Button> buttons = new ArrayList<>();
        if (activeGame.isFoWMode()) {
            buttons.add(draw1AC);
            buttons.add(getCCs);
            message2 = "Resolve status homework using the buttons";
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
        if (activeGame.getActionCards().size() > 130 && Helper.getPlayerFromColorOrFaction(activeGame, "hacan") != null
            && getButtonsToSwitchWithAllianceMembers(Helper.getPlayerFromColorOrFaction(activeGame, "hacan"), activeGame, false).size() > 0) {
            buttons.add(Button.secondary("getSwapButtons_", "Swap"));
        }
        MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), message2, buttons);
    }

    public static void startStrategyPhase(GenericInteractionCreateEvent event, Game activeGame) {
        if (activeGame.getNaaluAgent()) {
            activeGame.setNaaluAgent(false);
            for(Player p2 : activeGame.getRealPlayers()){
                for(String planet : p2.getPlanets()){
                    if(ButtonHelper.isPlanetLegendaryOrHome(planet, activeGame, true, p2)){
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
        if(activeGame.getLaws().containsKey("checks")){
             pickSCMsg = "Use Buttons to Pick the SC you want to give someone";
        }
        ButtonHelperFactionSpecific.giveKeleresCommsNTg(activeGame, event);
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
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), Helper.getPlayerRepresentation(player, activeGame) + " purged Maw Of Worlds.");
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), Helper.getPlayerRepresentation(player, activeGame) + " Use the button to get a tech", buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveCrownOfE(Game activeGame, Player player, ButtonInteractionEvent event) {
        player.removeRelic("emphidia");
        player.removeExhaustedRelic("emphidia");
        Integer poIndex = activeGame.addCustomPO("Crown of Emphidia", 1);
        activeGame.scorePublicObjective(player.getUserID(), poIndex);
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), Helper.getPlayerRepresentation(player, activeGame) + " scored Crown of Emphidia");
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
        if (p1.getCommodities() > 0) {
            Button transact = Button.success(finChecker + "transact_Comms_" + p2.getFaction(), "Commodities");
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
            Button transact = Button.success(finChecker + "transact_Planets_" + p2.getFaction(), "Planets").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("hacan")));
            stuffToTransButtons.add(transact);
        }
        return stuffToTransButtons;
    }

    public static void resolveSpecificTransButtons(Game activeGame, Player p1, String buttonID, ButtonInteractionEvent event) {
        String finChecker = "FFCC_" + p1.getFaction() + "_";

        List<Button> stuffToTransButtons = new ArrayList<>();
        buttonID = buttonID.replace("transact_", "");
        String thingToTrans = buttonID.substring(0, buttonID.indexOf("_"));
        String factionToTrans = buttonID.substring(buttonID.indexOf("_") + 1);
        Player p2 = Helper.getPlayerFromColorOrFaction(activeGame, factionToTrans);
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
            case "Planets" -> {
                String message = "Click the planet you would like to send";
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, ButtonHelperFactionSpecific.getTradePlanetsWithHacanMechButtons(p1, p2, activeGame));
            }
            case "ACs" -> {
                String message = Helper.getPlayerRepresentation(p1, activeGame, activeGame.getGuild(), true) + " Click the GREEN button that indicates the AC you would like to send";
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
                        transact = Button.success(finChecker + "send_PNs_" + p2.getFaction() + "_" + p1.getPromissoryNotes().get(pnShortHand), promissoryNote.getName()).withEmoji(Emoji.fromFormatted(owner.getFactionEmoji()));
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

                if (p1.getVrf() > 0) {
                    for (int x = 1; x < p1.getVrf() + 1; x++) {
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
        Player p2 = Helper.getPlayerFromColorOrFaction(activeGame, factionToTrans);
        String message2 = "";
        String ident = Helper.getPlayerRepresentation(p1, activeGame, activeGame.getGuild(), false);
        String ident2 = Helper.getPlayerRepresentation(p2, activeGame, activeGame.getGuild(), false);
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
                ButtonHelperFactionSpecific.pillageCheck(p1, activeGame);
                ButtonHelperFactionSpecific.pillageCheck(p2, activeGame);
                ButtonHelperFactionSpecific.resolveDarkPactCheck(activeGame, p1, p2, tgAmount, event);
                message2 = ident + " sent " + tgAmount + " Commodities to " + ident2;
            }
            case "ACs" -> {

                message2 = ident + " sent AC #" + amountToTrans + " to " + ident2;
                int acNum = Integer.parseInt(amountToTrans);
                String acID = null;
                if (!p1.getActionCards().containsValue(acNum)) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that AC, no AC sent");
                    return;
                }
                for (Map.Entry<String, Integer> so : p1.getActionCards().entrySet()) {
                    if (so.getValue().equals(acNum)) {
                        acID = so.getKey();
                    }
                }
                p1.removeActionCard(acNum);
                p2.setActionCard(acID);
                ACInfo.sendActionCardInfo(activeGame, p2);
                ACInfo.sendActionCardInfo(activeGame, p1);
            }
            case "PNs" -> {
                String id = null;
                int pnIndex;
                pnIndex = Integer.parseInt(amountToTrans);
                for (Map.Entry<String, Integer> so : p1.getPromissoryNotes().entrySet()) {
                    if (so.getValue().equals(pnIndex)) {
                        id = so.getKey();
                    }
                }
                p1.removePromissoryNote(id);
                p2.setPromissoryNote(id);
                boolean sendSftT = false;
                boolean sendAlliance = false;
                String promissoryNoteOwner = Mapper.getPromissoryNoteOwner(id);
                if ((id.endsWith("_sftt") || id.endsWith("_an")) && !promissoryNoteOwner.equals(p2.getFaction())
                    && !promissoryNoteOwner.equals(p2.getColor()) && !p2.isPlayerMemberOfAlliance(Helper.getPlayerFromColorOrFaction(activeGame, promissoryNoteOwner))) {
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
                message2 = Helper.getPlayerRepresentation(p1, activeGame) + " sent " + Emojis.PN + text + "PN to " + ident2;
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
        GameSaveLoadManager.saveMap(activeGame, event);

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
            String techEmoji = Helper.getEmojiFromDiscord(techType.toString().toLowerCase() + "tech");
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

                String leaderRep = Mapper.getLeaderRepresentations().get(leaderID);
                if (leaderRep == null) {
                    continue;
                }
                //leaderID = 0:LeaderName ; 1:LeaderTitle ; 2:BacksideTitle/HeroAbility ; 3:AbilityWindow ; 4:AbilityText
                String[] leaderRepSplit = leaderRep.split(";");
                String leaderName = leaderRepSplit[0];
                String leaderAbilityWindow = leaderRepSplit[3];

                String factionEmoji = Helper.getFactionLeaderEmoji(leader);
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
                        led = "xxchaagent";
                        if (p1.hasExternalAccessToLeader(led)) {
                            Button lButton = Button.secondary(finChecker + prefix + "leader_" + led, "Use " + leaderName + " as Xxcha agent").withEmoji(Emoji.fromFormatted(factionEmoji));
                            compButtons.add(lButton);
                        }
                        led = "yssarilagent";
                        Button lButton = Button.secondary(finChecker + prefix + "leader_" + led, "Use " + leaderName + " as Unimplemented Component Agent").withEmoji(Emoji.fromFormatted(factionEmoji));
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
            if (Mapper.getPromissoryNoteOwner(pn) != null && !Mapper.getPromissoryNoteOwner(pn).equalsIgnoreCase(p1.getFaction()) && !p1.getPromissoryNotesInPlayArea().contains(pn)) {
                String pnText = Mapper.getPromissoryNote(pn, true);
                if (pnText.contains("Action:") && !"bmf".equalsIgnoreCase(pn)) {
                    PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(pn);
                    String pnName = pnModel.getName();
                    Button pnButton = Button.danger(finChecker + prefix + "pn_" + pn, "Use " + pnName);
                    compButtons.add(pnButton);
                }
            }
        }
        //Abilities
        if (p1.hasAbility("star_forge") && p1.getStrategicCC() > 0 && getTilesOfPlayersSpecificUnit(activeGame, p1, "warsun").size() > 0) {
            Button abilityButton = Button.success(finChecker + prefix + "ability_starForge", "Starforge");
            compButtons.add(abilityButton);
        }
        if (p1.hasAbility("orbital_drop") && p1.getStrategicCC() > 0) {
            Button abilityButton = Button.success(finChecker + prefix + "ability_orbitalDrop", "Orbital Drop");
            compButtons.add(abilityButton);
        }
        if (p1.hasAbility("stall_tactics") && p1.getActionCards().size() > 0) {
            Button abilityButton = Button.success(finChecker + prefix + "ability_stallTactics", "Stall Tactics");
            compButtons.add(abilityButton);
        }
        if (p1.hasAbility("fabrication") && p1.getFragments().size() > 0) {
            Button abilityButton = Button.success(finChecker + prefix + "ability_fabrication", "Purge 1 Frag for a CC");
            compButtons.add(abilityButton);
        }
        if (p1.getUnitsOwned().contains("muaat_flagship") && p1.getStrategicCC() > 0 && getTilesOfPlayersSpecificUnit(activeGame, p1, "flagship").size() > 0) {
            Button abilityButton = Button.success(finChecker + prefix + "ability_muaatFS", "Spend a Strat CC for a Cruiser with your FS");
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
        //Generic
        Button genButton = Button.secondary(finChecker + prefix + "generic_", "Generic Component Action");
        compButtons.add(genButton);

        return compButtons;
    }

    public static String mechOrInfCheck(String planetName, Game activeGame, Player player) {
        String message;
        Tile tile = activeGame.getTile(AliasHandler.resolveTile(planetName));
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        int numMechs = 0;
        int numInf = 0;
        String colorID = Mapper.getColorID(player.getColor());
        String mechKey = colorID + "_mf.png";
        String infKey = colorID + "_gf.png";
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
            new ButtonListener().checkForAllReactions(event, activeGame);
            if (message == null || message.isEmpty()) {
                return;
            }
        }

        String text = Helper.getPlayerRepresentation(player, activeGame) + " " + message;
        if (activeGame.isFoWMode() && sendPublic) {
            text = message;
        } else if (activeGame.isFoWMode() && !sendPublic) {
            text = "(You) " + emojiToUse.getFormatted() + " " + message;
        }

        if (!additionalMessage.isEmpty()) {
            text += Helper.getGamePing(event.getGuild(), activeGame) + " " + additionalMessage;
        }

        if (activeGame.isFoWMode() && !sendPublic) {
            MessageHelper.sendPrivateMessageToPlayer(player, activeGame, text);
            return;
        }

        MessageHelper.sendMessageToChannel(Helper.getThreadChannelIfExists(event), text);
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
        if(!activeGame.getCurrentPhase().contains("agenda")){
            youCanSpend = youCanSpend + "and " + player.getTg() + " tgs";
        }
        

        return youCanSpend;
    }

    public static List<Tile> getTilesOfPlayersSpecificUnit(Game activeGame, Player p1, String unit) {
        List<Tile> tiles = new ArrayList<>();
        for (Tile tile : activeGame.getTileMap().values()) {
            boolean tileHasIt = false;
            String unitKey = Mapper.getUnitID(AliasHandler.resolveUnit(unit), p1.getColor());
            for (UnitHolder unitH : tile.getUnitHolders().values()) {
                if (unitH.getUnits().containsKey(unitKey)) {
                    tileHasIt = true;
                    break;
                }
            }
            if (tileHasIt && !tiles.contains(tile)) {
                tiles.add(tile);
            }
        }
        return tiles;
    }

    public static int getNumberOfUnitsOnTheBoard(Game activeGame, Player p1, String unit) {
        int count = 0;
        for (Tile tile : activeGame.getTileMap().values()) {
            String unitKey = Mapper.getUnitID(AliasHandler.resolveUnit(unit), p1.getColor());
            for (UnitHolder unitH : tile.getUnitHolders().values()) {
                if (unitH.getUnits().containsKey(unitKey)) {
                    count = count + unitH.getUnits().get(unitKey);
                }
            }
            for (Player player_ : activeGame.getPlayers().values()) {
                UnitHolder unitH = player_.getNomboxTile().getUnitHolders().get(Constants.SPACE);
                if (unitH == null) {
                } else {
                    if (unitH.getUnits().containsKey(unitKey)) {
                        count = count + unitH.getUnits().get(unitKey);
                    }
                }
            }
        }
        return count;
    }

    public static void resolveDiploPrimary(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String type = buttonID.split("_")[2];
        if (type.toLowerCase().contains("mahact")) {
            String color2 = type.replace("mahact", "");
            Player mahactP = Helper.getPlayerFromColorOrFaction(activeGame, color2);
            Tile tile = activeGame.getTileByPosition(planet);
            AddCC.addCC(event, color2, tile);
            Helper.isCCCountCorrect(event, activeGame, color2);
            for (String color : mahactP.getMahactCC()) {
                if (Mapper.isColorValid(color) && !color.equalsIgnoreCase(player.getColor())) {
                    AddCC.addCC(event, color, tile);
                    Helper.isCCCountCorrect(event, activeGame, color);
                }
            }
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                ButtonHelper.getIdent(player) + " chose to use the mahact PN in the tile " + tile.getRepresentation());
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
            MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdent(player) + " chose to diplo the system containing "
                + Helper.getPlanetRepresentation(planet, activeGame));
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

                MessageHelper.sendMessageToChannel(event.getMessageChannel(), (Helper.getPlayerRepresentation(p1, activeGame) + " exhausted tech: " + Helper.getTechRepresentation(buttonID)));
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
                    List<Tile> tiles = getTilesOfPlayersSpecificUnit(activeGame, p1, "spacedock");
                    if (tiles.isEmpty()) {
                        tiles = getTilesOfPlayersSpecificUnit(activeGame, p1, "cabalspacedock");
                    }
                    if (p1.hasUnit("ghoti_flagship")) {
                        tiles.addAll(getTilesOfPlayersSpecificUnit(activeGame, p1, "flagship"));
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

                if (playerLeader != null && buttonID.contains("agent")) {
                    if (!"naaluagent".equalsIgnoreCase(buttonID) && !"muaatagent".equalsIgnoreCase(buttonID) && !"arborecagent".equalsIgnoreCase(buttonID)
                        && !"xxchaagent".equalsIgnoreCase(buttonID)) {
                        playerLeader.setExhausted(true);
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), Helper.getFactionLeaderEmoji(playerLeader));
                        String messageText = Helper.getPlayerRepresentation(p1, activeGame) +
                            " exhausted " + Helper.getLeaderFullRepresentation(playerLeader);
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), messageText);
                    } else {
                        List<Button> buttons = getButtonsForAgentSelection(activeGame, buttonID);
                        String message = Helper.getPlayerRepresentation(p1, activeGame, activeGame.getGuild(), true) + " Use buttons to select the user of the agent";
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                    }
                } else {
                    StringBuilder message = new StringBuilder(Helper.getPlayerRepresentation(p1, activeGame)).append(" played ").append(Helper.getLeaderFullRepresentation(playerLeader));
                    if ("letnevhero".equals(playerLeader.getId()) || "nomadhero".equals(playerLeader.getId())) {
                        playerLeader.setLocked(false);
                        playerLeader.setActive(true);
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message + " - Leader will be PURGED after status cleanup");
                    } else {
                        boolean purged = p1.removeLeader(playerLeader);
                        if (purged) {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message + " - Leader " + buttonID + " has been purged");
                        } else {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Leader was not purged - something went wrong");
                        }
                        if ("titanshero".equals(playerLeader.getId())) {
                            String titanshero = Mapper.getTokenID("titanshero");
                            System.out.println(titanshero);
                            Tile t = activeGame.getTile(AliasHandler.resolveTile(p1.getFaction()));
                            if (activeGame.getTileFromPlanet("elysium") != null && activeGame.getTileFromPlanet("elysium").getPosition().equalsIgnoreCase(t.getPosition())) {
                                t.addToken("attachment_titanshero.png", "elysium");
                                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Attachment added to Elysium and it has been readied");
                                new PlanetRefresh().doAction(p1, "elysium", activeGame);
                            } else {
                                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "`Use the following command to add the attachment: /add_token token:titanshero`");
                            }
                        }
                        if ("solhero".equals(playerLeader.getId())) {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                                Helper.getPlayerRepresentation(p1, activeGame, activeGame.getGuild(), true) + " removed all of your ccs from the board");
                            for (Tile t : activeGame.getTileMap().values()) {
                                if (AddCC.hasCC(event, p1.getColor(), t)) {
                                    RemoveCC.removeCC(event, p1.getColor(), t, activeGame);
                                }
                            }
                        }
                        if ("yinhero".equals(playerLeader.getId())) {
                            List<Button> buttons = new ArrayList<>();
                            buttons.add(Button.primary(finChecker + "yinHeroStart", "Invade a planet with Yin Hero"));
                            buttons.add(Button.danger("deleteButtons", "Delete Buttons"));
                            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), Helper.getPlayerRepresentation(p1, activeGame, activeGame.getGuild(), true)
                                + " use the button to do individual invasions, then delete the buttons when you have placed 3 total infantry.", buttons);
                        }
                        if ("ghosthero".equals(playerLeader.getId())) {
                            List<Button> buttons = ButtonHelperFactionSpecific.getGhostHeroTilesStep1(activeGame, p1);
                            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), Helper.getPlayerRepresentation(p1, activeGame, activeGame.getGuild(), true)
                                + " use the button to select the first tile you would like to swap with your hero.", buttons);
                        }
                        if ("augershero".equals(playerLeader.getId())) {
                            List<Button> buttons = new ArrayList<>();
                            buttons.add(Button.primary(finChecker + "augersHeroStart_" + 1, "Resolve Augers Hero on Stage 1 Deck"));
                            buttons.add(Button.primary(finChecker + "augersHeroStart_" + 2, "Resolve Augers Hero on Stage 2 Deck"));
                            buttons.add(Button.danger("deleteButtons", "Delete Buttons"));
                            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                                Helper.getPlayerRepresentation(p1, activeGame, activeGame.getGuild(), true) + " use the button to choose which objective type you wanna hero on", buttons);
                        }
                        if ("empyreanhero".equals(playerLeader.getId())) {
                            new AddFrontierTokens().parsingForTile(event, activeGame);
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Added frontier tokens");
                            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use Buttons to explore empties", ButtonHelperFactionSpecific.getEmpyHeroButtons(p1, activeGame));
                        }
                        if ("cabalhero".equals(playerLeader.getId())) {
                            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use Buttons to capture people", ButtonHelperFactionSpecific.getCabalHeroButtons(p1, activeGame));
                        }
                        if ("yssarilhero".equals(playerLeader.getId())) {
                            for (Player p2 : activeGame.getRealPlayers()) {
                                if (p2 == p1 || p2.getAc() == 0) {
                                    continue;
                                }
                                List<Button> buttons = new ArrayList<>(ACInfo.getYssarilHeroActionCardButtons(activeGame, p1, p2));
                                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(),
                                    Helper.getPlayerRepresentation(p2, activeGame, activeGame.getGuild(), true) + " Yssaril hero played.  Use buttons to select which AC you will offer to them.",
                                    buttons);
                            }
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                                Helper.getPlayerRepresentation(p1, activeGame, activeGame.getGuild(), true) + " sent everyone a ping in their private threads with buttons to send you an AC");
                        }
                        if ("keleresheroharka".equals(playerLeader.getId())) {
                            new KeleresHeroMentak().secondHalf(activeGame, p1, event);
                        }
                    }

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
                        sdButton = sdButton.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("spacedock")));
                        Button pdsButton = Button.success("jrStructure_pds", "Place a PDS");
                        pdsButton = pdsButton.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("pds")));
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
                        ButtonHelperFactionSpecific.pillageCheck(p1, activeGame);
                        ButtonHelperFactionSpecific.resolveArtunoCheck(p1, activeGame, p1.getTg() - oldTg);
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

                    List<Tile> tiles = getTilesOfPlayersSpecificUnit(activeGame, p1, "warsun");
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
                    ButtonHelperFactionSpecific.resolveMuaatCommanderCheck(p1, activeGame, event);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
                    String message = "Select the planet you would like to place 2 infantry on.";
                    List<Button> buttons = Helper.getPlanetPlaceUnitButtons(p1, activeGame, "2gf", "place");
                    buttons.add(Button.danger("orbitolDropFollowUp", "Done Dropping Infantry"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                } else if ("muaatFS".equalsIgnoreCase(buttonID)) {
                    String successMessage = "Used Muaat FS ability. Reduced strategy pool CCs by 1 (" + (p1.getStrategicCC()) + "->" + (p1.getStrategicCC() - 1) + ") \n";
                    p1.setStrategicCC(p1.getStrategicCC() - 1);
                    ButtonHelperFactionSpecific.resolveMuaatCommanderCheck(p1, activeGame, event);
                    List<Tile> tiles = getTilesOfPlayersSpecificUnit(activeGame, p1, "flagship");
                    Tile tile = tiles.get(0);
                    List<Button> buttons = getStartOfTurnButtons(p1, activeGame, true, event);
                    new AddUnits().unitParsing(event, p1.getColor(), tile, "1 cruiser", activeGame);
                    successMessage = successMessage + "Produced 1 " + Helper.getEmojiFromDiscord("cruiser") + " in tile "
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
                    if (p1.getVrf() > 0) {
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
                }
            }
            case "getRelic" -> {
                String message = "Click the fragments you'd like to purge. ";
                List<Button> purgeFragButtons = new ArrayList<>();
                int numToBeat = 2 - p1.getVrf();
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

                if (p1.getVrf() > 0) {
                    for (int x = 1; x < p1.getVrf() + 1; x++) {
                        Button transact = Button.secondary(finChecker + "purge_Frags_URF_" + x, "Frontier Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }
                Button transact2 = Button.danger(finChecker + "drawRelicFromFrag", "Finish Purging and Draw Relic");
                purgeFragButtons.add(transact2);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, purgeFragButtons);
            }
            case "generic" -> MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Doing unspecified component action. You could ping Fin to add this. ");
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
        File file = GenerateMap.getInstance().saveImage(activeGame, DisplayType.all, event);
    }

    public static void offerNanoforgeButtons(Player player, Game activeGame, GenericInteractionCreateEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            UnitHolder unitHolder = activeGame.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            boolean oneOfThree = planetReal != null && planetReal.getOriginalPlanetType() != null && ("industrial".equalsIgnoreCase(planetReal.getOriginalPlanetType())
                || "cultural".equalsIgnoreCase(planetReal.getOriginalPlanetType()) || "hazardous".equalsIgnoreCase(planetReal.getOriginalPlanetType()));
            if (oneOfThree && !planetReal.isHasAbility()) {
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
        String pnOwner = Mapper.getPromissoryNoteOwner(id);
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
        StringBuilder sb = new StringBuilder(Helper.getPlayerRepresentation(player, activeGame) + " played promissory note: " + pnName + "\n");
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
        PNInfo.sendPromissoryNoteInfo(activeGame, player, false);
        PNInfo.sendPromissoryNoteInfo(activeGame, owner, false);
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