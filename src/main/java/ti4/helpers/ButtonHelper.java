package ti4.helpers;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

import lombok.Data;
import ti4.AsyncTI4DiscordBot;
import ti4.buttons.ButtonListener;
import ti4.commands.agenda.RevealAgenda;
import ti4.commands.agenda.ShowDiscardedAgendas;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsac.PlayAC;
import ti4.commands.cardsac.ShowDiscardActionCards;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.cardsso.ShowUnScoredSOs;
import ti4.commands.combat.CombatRoll;
import ti4.commands.ds.DrawBlueBackTile;
import ti4.commands.explore.ExpFrontier;
import ti4.commands.explore.ExpInfo;
import ti4.commands.explore.SendFragments;
import ti4.commands.explore.ShowRemainingRelics;
import ti4.commands.game.GameCreate;
import ti4.commands.game.GameEnd;
import ti4.commands.leaders.ExhaustLeader;
import ti4.commands.leaders.HeroPlay;
import ti4.commands.leaders.RefreshLeader;
import ti4.commands.leaders.UnlockLeader;
import ti4.commands.planet.PlanetAdd;
import ti4.commands.player.ClearDebt;
import ti4.commands.player.SendDebt;
import ti4.commands.player.Setup;
import ti4.commands.special.StellarConverter;
import ti4.commands.status.Cleanup;
import ti4.commands.status.ListTurnOrder;
import ti4.commands.tokens.AddCC;
import ti4.commands.tokens.AddToken;
import ti4.commands.tokens.RemoveCC;
import ti4.commands.units.AddUnits;
import ti4.commands.units.MoveUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.GenerateTile;
import ti4.generator.MapGenerator;
import ti4.generator.Mapper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ColorModel;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;
import ti4.model.PlanetModel;
import ti4.model.PromissoryNoteModel;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.model.TemporaryCombatModifierModel;
import ti4.model.UnitModel;
import ti4.selections.selectmenus.SelectFaction;

public class ButtonHelper {

    public static void pickACardFromDiscardStep1(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String acStringID : activeGame.getDiscardActionCards().keySet()) {
            buttons.add(Button.success("pickFromDiscard_" + acStringID, Mapper.getActionCardName(acStringID)));
        }
        buttons.add(Button.danger("deleteButtons", "Delete These Buttons"));
        if (buttons.size() > 25) {
            buttons.add(25, Button.danger("deleteButtons_", "Delete These Buttons"));
        }
        if (buttons.size() > 50) {
            buttons.add(50, Button.danger("deleteButtons_2", "Delete These Buttons"));
        }
        if (buttons.size() > 75) {
            buttons.add(75, Button.danger("deleteButtons_3", "Delete These Buttons"));
        }
        String msg = player.getRepresentation(true, true) + " use buttons to grab an AC from the discard";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    public static void pickACardFromDiscardStep2(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        event.getMessage().delete().queue();
        String acID = buttonID.split("_")[1];
        boolean picked = activeGame.pickActionCard(player.getUserID(), activeGame.getDiscardActionCards().get(acID));
        if (!picked) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Action Card ID found, please retry");
            return;
        }
        String msg2 = player.getRepresentation(true, true) + " grabbed " + Mapper.getActionCardName(acID) + " from the discard";
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), msg2);

        ACInfo.sendActionCardInfo(activeGame, player, event);
        if (player.hasAbility("autonetic_memory")) {
            String message = player.getRepresentation(true, true) + " if you did not just use the codex to get that ac, please discard an AC due to your cybernetic madness ability";
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, ACInfo.getDiscardActionCardButtons(activeGame, player, false));
        }
    }

    public static boolean doesPlayerHaveFSHere(String flagshipID, Player player, Tile tile) {
        if (!player.hasUnit(flagshipID)) {
            return false;
        }
        UnitHolder space = tile.getUnitHolders().get("space");
        return space.getUnitCount(UnitType.Flagship, player.getColor()) > 0;
    }

    public static void resolveInfantryDeath(Game activeGame, Player player, int amount) {
        if (player.hasInf2Tech()) {
            for (int x = 0; x < amount; x++) {
                MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), rollInfantryRevival(player));
            }
        }
    }

    public static List<Button> getDacxiveButtons(String planet) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("dacxive_" + planet, "Resolve Dacxive"));
        buttons.add(Button.danger("deleteButtons", "No Dacxive"));
        return buttons;
    }

    public static List<Button> getForcedPNSendButtons(Game activeGame, Player player, Player p1) {
        List<Button> stuffToTransButtons = new ArrayList<>();
        for (String pnShortHand : p1.getPromissoryNotes().keySet()) {
            if (p1.getPromissoryNotesInPlayArea().contains(pnShortHand)) {
                continue;
            }
            PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pnShortHand);
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

    public static boolean canTheseTwoTransact(Game activeGame, Player player, Player player2) {
        return player == player2 || !"action".equalsIgnoreCase(activeGame.getCurrentPhase()) || player.hasAbility("guild_ships") || player.getPromissoryNotes().containsKey("convoys")
            || player2.getPromissoryNotes().containsKey("convoys") || player2.hasAbility("guild_ships") || player2.getNeighbouringPlayers().contains(player)
            || player.getNeighbouringPlayers().contains(player2);
    }

    public static void checkTransactionLegality(Game activeGame, Player player, Player player2) {
        if (!canTheseTwoTransact(activeGame, player, player2)) {
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " this is a friendly reminder that you are not neighbors with " + player2.getColor());
        }
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
            Map<UnitKey, Integer> units = unitHolder.getUnits();
            if (!(unitHolder instanceof Planet planet)) {
                Map<UnitKey, Integer> tileUnits = new HashMap<>(units);
                for (Map.Entry<UnitKey, Integer> unitEntry : tileUnits.entrySet()) {
                    if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                    UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                    if (unitModel == null) continue;

                    UnitKey key = unitEntry.getKey();
                    if (key.getUnitType() == UnitType.Infantry
                        || key.getUnitType() == UnitType.Mech
                        || (!player.hasFF2Tech() && key.getUnitType() == UnitType.Fighter)
                        || (cabal != null && (key.getUnitType() == UnitType.Fighter || key.getUnitType() == UnitType.Spacedock))) {
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

    public static boolean shouldKeleresRiderExist(Game activeGame) {
        return activeGame.getPNOwner("ridera") != null || activeGame.getPNOwner("riderm") != null || activeGame.getPNOwner("riderx") != null || activeGame.getPNOwner("rider") != null;
    }

    public static String rollInfantryRevival(Player player) {
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
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), getIdent(player) + " " + msg);
    }

    public static void placeInfantryFromRevival(Game activeGame, ButtonInteractionEvent event, Player player, String buttonID) {
        String planet = buttonID.split("_")[1];
        String amount;
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
            if (planet.contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            UnitHolder unitHolder = activeGame.getPlanetsInfo().get(planet);
            if (unitHolder == null) return types;
            Planet planetReal = (Planet) unitHolder;
            boolean oneOfThree = planetReal != null && planetReal.getOriginalPlanetType() != null && ("industrial".equalsIgnoreCase(planetReal.getOriginalPlanetType())
                || "cultural".equalsIgnoreCase(planetReal.getOriginalPlanetType()) || "hazardous".equalsIgnoreCase(planetReal.getOriginalPlanetType()));
            if (planetReal != null && oneOfThree && !types.contains(planetReal.getOriginalPlanetType())) {
                types.add(planetReal.getOriginalPlanetType());
            }
            if (unitHolder.getTokenList().contains("attachment_titanspn.png")) {
                if (!types.contains("hazardous")) {
                    types.add("hazardous");
                }
                if (!types.contains("industrial")) {
                    types.add("industrial");
                }
                if (!types.contains("cultural")) {
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
                if (player.getPlanets().contains(unitHolder.getName())) {
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

    public static List<Button> getExhaustButtonsWithTG(Game activeGame, Player player) {
        return getExhaustButtonsWithTG(activeGame, player, "both");
    }

    public static List<Button> getExhaustButtonsWithTG(Game activeGame, Player player, String whatIsItFor) {
        List<Button> buttons = Helper.getPlanetExhaustButtons(player, activeGame, whatIsItFor);
        if (player.getTg() > 0 || (activeGame.playerHasLeaderUnlockedOrAlliance(player, "titanscommander") && !whatIsItFor.contains("inf"))) {
            Button lost1TG = Button.danger("reduceTG_1_" + whatIsItFor, "Spend 1 TG");
            buttons.add(lost1TG);
        }
        if (player.getTg() > 1) {
            Button lost2TG = Button.danger("reduceTG_2_" + whatIsItFor, "Spend 2 TGs");
            buttons.add(lost2TG);
        }
        if (player.getTg() > 2) {
            Button lost3TG = Button.danger("reduceTG_3_" + whatIsItFor, "Spend 3 TGs");
            buttons.add(lost3TG);
        }
        if (player.hasUnexhaustedLeader("keleresagent") && player.getCommodities() > 0) {
            Button lost1C = Button.danger("reduceComm_1_" + whatIsItFor, "Spend 1 comm");
            buttons.add(lost1C);
        }
        if (player.hasUnexhaustedLeader("olradinagent")) {
            Button hacanButton = Button.secondary("exhaustAgent_olradinagent_" + player.getFaction(), "Use Olradin Agent").withEmoji(Emoji.fromFormatted(Emojis.olradin));
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("keleresagent") && player.getCommodities() > 1) {
            Button lost2C = Button.danger("reduceComm_2_" + whatIsItFor, "Spend 2 comms");
            buttons.add(lost2C);
        }
        if (player.getNomboxTile().getUnitHolders().get("space").getUnits().size() > 0 && !whatIsItFor.contains("inf") && !whatIsItFor.contains("both")) {
            Button release = Button.secondary("getReleaseButtons", "Release captured units").withEmoji(Emoji.fromFormatted(Emojis.getFactionIconFromDiscord("cabal")));
            buttons.add(release);
        }
        if (player.hasUnexhaustedLeader("khraskagent") && (whatIsItFor.contains("inf") || whatIsItFor.contains("both"))) {
            Button release = Button.secondary("exhaustAgent_khraskagent_" + player.getFaction(), "Exhaust Khrask Agent").withEmoji(Emoji.fromFormatted(Emojis.getFactionIconFromDiscord("khrask")));
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
        try {
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
        } catch (Exception e) {
            return playersWhoAreMissed;
        }
    }

    public static String playerHasDMZPlanet(Player player, Game activeGame) {
        String dmzPlanet = "no";
        for (String planet : player.getPlanets()) {
            if (planet.contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            UnitHolder unitHolder = getUnitHolderFromPlanetName(planet, activeGame);
            if (unitHolder == null) {
                BotLogger.log("Null unitholder for planet " + planet);
                continue;
            }
            Set<String> tokenList = unitHolder.getTokenList();
            if (tokenList.stream().anyMatch(token -> token.contains("dmz_large") || token.contains("dmz"))) {
                dmzPlanet = planet;
                break;
            }
        }
        return dmzPlanet;
    }

    public static List<Button> getTradePlanetsWithAlliancePartnerButtons(Player p1, Player receiver, Game activeGame) {
        List<Button> buttons = new ArrayList<>();
        if (!p1.getAllianceMembers().contains(receiver.getFaction())) {
            return buttons;
        }
        for (String planet : p1.getPlanets()) {
            if (planet.contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            UnitHolder unitHolder = getUnitHolderFromPlanetName(planet, activeGame);
            if (unitHolder != null && unitHolder.getUnitColorsOnHolder().contains(receiver.getColorID())) {
                String refreshed = "refreshed";
                if (p1.getExhaustedPlanets().contains(planet)) {
                    refreshed = "exhausted";
                }
                buttons.add(Button.secondary("resolveAlliancePlanetTrade_" + planet + "_" + receiver.getFaction() + "_" + refreshed, Helper.getPlanetRepresentation(planet, activeGame)));
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
            MessageHelper.sendMessageToChannel(getCorrectChannel(p1, activeGame), "Could not resolve second player, please resolve manually.");
            return;
        }
        UnitHolder oriPlanet = getUnitHolderFromPlanetName(dmzPlanet, activeGame);
        new PlanetAdd().doAction(p2, dmzPlanet, activeGame, event);
        if (!"exhausted".equalsIgnoreCase(exhausted)) {
            p2.refreshPlanet(dmzPlanet);
        }
        List<Button> goAgainButtons = new ArrayList<>();
        Button button = Button.secondary("transactWith_" + p2.getColor(), "Send something else to player?");
        Button done = Button.secondary("finishTransaction_" + p2.getColor(), "Done With This Transaction");
        String ident = getIdentOrColor(p1, activeGame);
        String message2 = ident + " traded the planet " + Helper.getPlanetRepresentation(dmzPlanet, activeGame) + " to " + getIdentOrColor(p2, activeGame);
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
            MessageHelper.sendMessageToChannel(getCorrectChannel(p1, activeGame), "Could not resolve second player, please resolve manually.");
            return;
        }
        UnitHolder oriPlanet = getUnitHolderFromPlanetName(dmzPlanet, activeGame);
        new PlanetAdd().doAction(p2, dmzPlanet, activeGame, event);
        List<Button> goAgainButtons = new ArrayList<>();
        Button button = Button.secondary("transactWith_" + p2.getColor(), "Send something else to player?");
        Button done = Button.secondary("finishTransaction_" + p2.getColor(), "Done With This Transaction");
        String ident = getIdentOrColor(p1, activeGame);
        String message2 = ident + " traded the planet " + Helper.getPlanetRepresentation(dmzPlanet, activeGame) + " to " + getIdentOrColor(p2, activeGame);
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
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), p2.getRepresentation(true, true) + " you got traded the DMZ");
        event.getMessage().delete().queue();
    }

    public static boolean canIBuildGFInSpace(Game activeGame, Player player, Tile tile, String kindOfBuild) {
        Map<String, UnitHolder> unitHolders = tile.getUnitHolders();

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
                    if (model == null) continue;
                    if (model.getProductionValue() > 0) return true;
                    if (player.hasUnit("ghoti_flagship") && "flagship".equalsIgnoreCase(model.getBaseType())) {
                        return true;
                    }
                }
            }
        }

        return player.getTechs().contains("mr") && tile.getTileModel().isSupernova();
    }

    public static void getTech(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String ident = getIdent(player);
        boolean paymentRequired = !buttonID.contains("__noPay");
        buttonID = buttonID.replace("__noPay", "");

        String techID = StringUtils.substringAfter(buttonID, "getTech_");
        techID = AliasHandler.resolveTech(techID);
        if (!Mapper.isValidTech(techID)) {
            BotLogger.log(event, "`ButtonHelper.getTech` Invalid TechID in 'getTech_' Button: " + techID);
            return;
        }
        TechnologyModel techM = Mapper.getTech(techID);
        StringBuilder message = new StringBuilder(ident).append(" Acquired The Tech ").append(techM.getRepresentation(false));

        if (techM.getRequirements().isPresent() && techM.getRequirements().get().length() > 1) {
            if (player.getLeaderIDs().contains("zealotscommander") && !player.hasLeaderUnlocked("zealotscommander")) {
                commanderUnlockCheck(player, activeGame, "zealots", event);
            }
        }
        if (techM.getType() == TechnologyType.UNITUPGRADE) {
            if (player.hasUnexhaustedLeader("mirvedaagent") && player.getStrategicCC() > 0) {
                List<Button> buttons = new ArrayList<>();
                Button hacanButton = Button.secondary("exhaustAgent_mirvedaagent_" + player.getFaction(), "Use Mirveda Agent").withEmoji(Emoji.fromFormatted(Emojis.mirveda));
                buttons.add(hacanButton);
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                    player.getRepresentation(true, true) + " you can use mirveda agent to spend a CC and research a tech of the same color as a prereq of the tech you just got", buttons);
            }
        }
        if (player.hasUnexhaustedLeader("zealotsagent")) {
            List<Button> buttons = new ArrayList<>();
            Button hacanButton = Button.secondary("exhaustAgent_zealotsagent_" + player.getFaction(), "Use Zealots Agent").withEmoji(Emoji.fromFormatted(Emojis.zealots));
            buttons.add(hacanButton);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                player.getRepresentation(true, true) + " you can use Zealots agent to produce 1 ship at home or in a system where you have a tech skip planet", buttons);
        }
        player.addTech(techID);
        ButtonHelperFactionSpecific.resolveResearchAgreementCheck(player, techID, activeGame);
        ButtonHelperCommanders.resolveNekroCommanderCheck(player, techID, activeGame);
        if ("iihq".equalsIgnoreCase(techID)) {
            message.append("\n Automatically added the Custodia Vigilia planet");
        }
        if (player.getLeaderIDs().contains("jolnarcommander") && !player.hasLeaderUnlocked("jolnarcommander")) {
            commanderUnlockCheck(player, activeGame, "jolnar", event);
        }
        if (player.getLeaderIDs().contains("nekrocommander") && !player.hasLeaderUnlocked("nekrocommander")) {
            commanderUnlockCheck(player, activeGame, "nekro", event);
        }
        if (player.getLeaderIDs().contains("mirvedacommander") && !player.hasLeaderUnlocked("mirvedacommander")) {
            commanderUnlockCheck(player, activeGame, "mirveda", event);
        }
        if (player.getLeaderIDs().contains("dihmohncommander") && !player.hasLeaderUnlocked("dihmohncommander")) {
            commanderUnlockCheck(player, activeGame, "dihmohn", event);
        }
        if (StringUtils.countMatches(buttonID, "_") < 2) { //TODO: Better explain what this is doing and why this way
            if (activeGame.getComponentAction()|| !activeGame.getCurrentPhase().equalsIgnoreCase("action")) {
                MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), message.toString());
            } else {
                sendMessageToRightStratThread(player, activeGame, message.toString(), "technology");
            }
            if (paymentRequired) {
                payForTech(activeGame, player, event, buttonID);
            }else{
                if(player.hasLeader("zealotshero") && player.getLeader("zealotshero").get().isActive()){
                    if(activeGame.getFactionsThatReactedToThis("zealotsHeroTechs").isEmpty()){
                        activeGame.setCurrentReacts("zealotsHeroTechs", techID);
                    }else{
                        activeGame.setCurrentReacts("zealotsHeroTechs", activeGame.getFactionsThatReactedToThis("zealotsHeroTechs")+"-"+techID);
                    }
                }
            }
            if (player.hasUnit("augers_mech") && getNumberOfUnitsOnTheBoard(activeGame, player, "mech") < 4) {
                MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame),
                    player.getFactionEmoji() + " has the opportunity to deploy an Augur mech on a legendary planet or planet with a tech skip");
                String message2 = player.getRepresentation(true, true) + " Use buttons to drop a mech on a legendary planet or planet with a tech skip";
                List<Button> buttons2 = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, activeGame, "mech", "placeOneNDone_skipbuild"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message2, buttons2);
            }
        } else {
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), message.toString());
        }
        event.getMessage().delete().queue();
    }

    public static void payForTech(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String trueIdentity = player.getRepresentation(true, true);
        String message2 = trueIdentity + " Click the names of the planets you wish to exhaust. ";
        List<Button> buttons = getExhaustButtonsWithTG(activeGame, player, "res");
        String tech = buttonID.split("_")[1];
        TechnologyModel techM = Mapper.getTechs().get(AliasHandler.resolveTech(tech));
        if ("unitupgrade".equalsIgnoreCase(techM.getType().toString()) && player.hasTechReady("aida")) {
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
        if (player.hasUnexhaustedLeader("veldyragent")) {
            Button winnuButton = Button.danger("exhaustAgent_veldyragent_" + player.getFaction(), "Exhaust Veldyr Agent").withEmoji(Emoji.fromFormatted(Emojis.veldyr));
            buttons.add(winnuButton);
        }
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "yincommander")) {
            Button pT2 = Button.secondary("yinCommanderStep1_", "Remove Inf Via Yin Commander").withEmoji(Emoji.fromFormatted(Emojis.Yin));
            buttons.add(pT2);
        }
        Button doneExhausting = Button.danger("deleteButtons_technology", "Done Exhausting Planets");
        buttons.add(doneExhausting);
        if (!player.hasAbility("propagation")) {
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        }
    }

    public static void forceARefresh(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String msg = getIdent(player) + " forced " + getIdentOrColor(p2, activeGame) + " to refresh";
        String msg2 = p2.getRepresentation(true, true) + " the trade holder has forced you to refresh";
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), msg);
        MessageHelper.sendMessageToChannel(getCorrectChannel(p2, activeGame), msg2);
        deleteTheOneButton(event);
        p2.addFollowedSC(5);
        p2.setCommodities(p2.getCommoditiesTotal());
        if (p2.getLeaderIDs().contains("mykomentoricommander") && !p2.hasLeaderUnlocked("mykomentoricommander")) {
            commanderUnlockCheck(p2, activeGame, "mykomentori", event);
        }
        if (p2.hasAbility("military_industrial_complex") && ButtonHelperAbilities.getBuyableAxisOrders(p2, activeGame).size() > 1) {
            MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(p2, activeGame),
                p2.getRepresentation(true, true) + " you have the opportunity to buy axis orders", ButtonHelperAbilities.getBuyableAxisOrders(p2, activeGame));
        }
        resolveMinisterOfCommerceCheck(activeGame, p2, event);
        ButtonHelperAgents.cabalAgentInitiation(activeGame, p2);
    }

    public static List<Button> getForcedRefreshButtons(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("forceARefresh_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("forceARefresh_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        buttons.add(Button.danger("deleteButtons", "Done Resolving"));
        return buttons;
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
                String message = p2.getRepresentation(true, true) + " a player who's TA you hold has refreshed their comms, would you like to play the TA?";
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), message, buttons);
            }
        }
    }

    public static void offerDeckButtons(Game activeGame, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.secondary("showDeck_frontier", "Frontier").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("frontier"))));
        buttons.add(Button.primary("showDeck_cultural", "Cultural").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("cultural"))));
        buttons.add(Button.danger("showDeck_hazardous", "Hazardous").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("hazardous"))));
        buttons.add(Button.success("showDeck_industrial", "Industrial").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("industrial"))));
        buttons.add(Button.secondary("showDeck_all", "All Explores"));
        buttons.add(Button.secondary("showDeck_ac", "AC Discards").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("actioncard"))));
        buttons.add(Button.secondary("showDeck_agenda", "Agenda Discards").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("agenda"))));
        buttons.add(Button.secondary("showDeck_relic", "Relics").withEmoji(Emoji.fromFormatted(Emojis.Relic)));
        buttons.add(Button.secondary("showDeck_unscoredSO", "Unscored SOs").withEmoji(Emoji.fromFormatted(Emojis.SecretObjective)));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Pick deck that you want", buttons);
    }

    public static void resolveDeckChoice(Game activeGame, ButtonInteractionEvent event, String buttonID, Player player) {
        String type = buttonID.split("_")[1];
        List<String> types = new ArrayList<>();
        String msg = "You can click this button to get the full text";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("showTextOfDeck_" + type, "Show full text"));
        buttons.add(Button.danger("deleteButtons", "No Thanks"));
        if ("ac".equalsIgnoreCase(type)) {
            new ShowDiscardActionCards().showDiscard(activeGame, event);
        } else if ("all".equalsIgnoreCase(type)) {
            types.add(Constants.CULTURAL);
            types.add(Constants.INDUSTRIAL);
            types.add(Constants.HAZARDOUS);
            types.add(Constants.FRONTIER);
            new ExpInfo().secondHalfOfExpInfo(types, event, player, activeGame, false);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
        } else if ("agenda".equalsIgnoreCase(type)) {
            new ShowDiscardedAgendas().showDiscards(activeGame, event);
        } else if ("relic".equalsIgnoreCase(type)) {
            new ShowRemainingRelics().showRemaining(event, false, activeGame, player);
        } else if ("unscoredSO".equalsIgnoreCase(type)) {
            new ShowUnScoredSOs().showUnscored(activeGame, event);
        } else {
            types.add(type);
            new ExpInfo().secondHalfOfExpInfo(types, event, player, activeGame, false);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
        }
        event.getMessage().delete().queue();
    }

    public static void resolveShowFullTextDeckChoice(Game activeGame, ButtonInteractionEvent event, String buttonID, Player player) {
        String type = buttonID.split("_")[1];
        List<String> types = new ArrayList<>();
        if ("all".equalsIgnoreCase(type)) {
            types.add(Constants.CULTURAL);
            types.add(Constants.INDUSTRIAL);
            types.add(Constants.HAZARDOUS);
            types.add(Constants.FRONTIER);
            new ExpInfo().secondHalfOfExpInfo(types, event, player, activeGame, false, true);
        } else {
            types.add(type);
            new ExpInfo().secondHalfOfExpInfo(types, event, player, activeGame, false, true);
        }
        event.getMessage().delete().queue();
    }

    public static boolean isPlayerElected(Game activeGame, Player player, String lawID) {
        for (String law : activeGame.getLaws().keySet()) {
            if (lawID.equalsIgnoreCase(law)) {
                if (activeGame.getLawsInfo().get(law).equalsIgnoreCase(player.getFaction())) {
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
        if (player.hasAbility("autonetic_memory")) {
            if (player.hasTech("nm")) {
                ButtonHelperAbilities.autoneticMemoryStep1(activeGame, player, 2);
            } else {
                ButtonHelperAbilities.autoneticMemoryStep1(activeGame, player, 1);
            }
            message = getIdent(player) + " Triggered Autonetic Memory Option";
        } else {
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

        if (!player.hasAbility("autonetic_memory")) {
            message = "Drew " + amount + " AC." + message;
        }

        ACInfo.sendActionCardInfo(activeGame, player, event);
        if (player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")) {
            commanderUnlockCheck(player, activeGame, "yssaril", event);
        }
        if (player.hasAbility("scheming")) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation(true, true) + " use buttons to discard",
                ACInfo.getDiscardActionCardButtons(activeGame, player, false));
        }

        addReaction(event, true, false, message, "");
        checkACLimit(activeGame, event, player);
        activeGame.setACDrawStatusInfo(activeGame.getACDrawStatusInfo() + "_" + player.getFaction());
        ButtonHelperActionCards.checkForAssigningPublicDisgrace(activeGame, player);
        ButtonHelperActionCards.checkForPlayingManipulateInvestments(activeGame, player);
        ButtonHelperActionCards.checkForPlayingSummit(activeGame, player);
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
                    String message = player.getRepresentation(true, true) + " Minister of Commerce triggered, your tgs have increased due to your " +
                        numOfNeighbors + " neighbors (" + player.getTg() + "->" + (player.getTg() + numOfNeighbors) + ")";
                    player.setTg(numOfNeighbors + player.getTg());
                    ButtonHelperAgents.resolveArtunoCheck(player, activeGame, numOfNeighbors);
                    MessageHelper.sendMessageToChannel(channel, message);
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
        if (unitHolder == null) return 0;
        UnitKey mechKey = Mapper.getUnitKey("mf", colorID);
        int numMechs = 0;
        if (unitHolder.getUnits() != null && unitHolder.getUnits().get(mechKey) != null) {
            numMechs = unitHolder.getUnits().get(mechKey);
        }
        return numMechs;
    }

    public static int resolveOnActivationEnemyAbilities(Game activeGame, Tile activeSystem, Player player, boolean justChecking) {
        int numberOfAbilities = 0;
        if (activeGame.getL1Hero()) {
            return 0;
        }
        String activePlayerident = player.getRepresentation();
        MessageChannel channel = activeGame.getActionsChannel();
        if (justChecking) {
            Player ghostPlayer = Helper.getPlayerFromUnit(activeGame, "ghost_mech");
            if (ghostPlayer != null && ghostPlayer != player && getNumberOfUnitsOnTheBoard(activeGame, ghostPlayer, "mech") > 0  && !activeGame.getLaws().containsKey("articles_war")) {
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
            String ident = nonActivePlayer.getRepresentation(true, true);
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
            //keleres_fs
            if (nonActivePlayer.hasUnit("keleres_flagship") && activeSystem.getUnitHolders().get("space").getUnitCount(UnitType.Flagship, nonActivePlayer.getColor()) > 0) {
                if (justChecking) {
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(channel, "Warning: you would have to pay 2 influence to activate this system due to Keleres FS");
                    }
                    numberOfAbilities++;
                } else {
                    List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, "inf");
                    Button doneExhausting = Button.danger("deleteButtons_spitItOut", "Done Exhausting Planets");
                    buttons.add(doneExhausting);
                    MessageHelper.sendMessageToChannel(channel, activePlayerident + " you must pay 2 influence due to Keleres FS");
                    MessageHelper.sendMessageToChannelWithButtons(channel, "Click the names of the planets you wish to exhaust", buttons);
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
                        MessageHelper.sendMessageToChannel(getCorrectChannel(nonActivePlayer, activeGame), ident + " you triggered voidwatch");
                        channel = player.getPrivateChannel();
                    }
                    List<Button> stuffToTransButtons = getForcedPNSendButtons(activeGame, nonActivePlayer, player);
                    String message = player.getRepresentation(true, true)
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
                    Button decline = Button.danger(fincheckerForNonActive + "deleteButtons", "Decline Commander");
                    List<Button> buttons = List.of(gainTG, decline);
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(nonActivePlayer, activeGame), ident + " use buttons to resolve Arborec commander ", buttons);
                }
            }
            if (nonActivePlayer.hasLeaderUnlocked("celdaurihero") && FoWHelper.playerHasPlanetsInSystem(nonActivePlayer, activeSystem) && !activeSystem.getTileID().equalsIgnoreCase("18")) {
                if (justChecking) {
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger a chance to play celdauri Hero");
                    }
                    numberOfAbilities++;
                } else {
                    Button gainTG = Button.success(fincheckerForNonActive + "purgeCeldauriHero_" + activeSystem.getPosition(), "Use Celdauri Hero");
                    Button decline = Button.danger(fincheckerForNonActive + "deleteButtons", "Decline Hero");
                    List<Button> buttons = List.of(gainTG, decline);
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(nonActivePlayer, activeGame), ident + " use buttons to decide if you want to use Celdauri Hero", buttons);
                }
            }
            if (nonActivePlayer.hasUnit("mahact_mech") && nonActivePlayer.hasMechInSystem(activeSystem) && nonActivePlayer.getMahactCC().contains(player.getColor())
                && !activeGame.getLaws().containsKey("articles_war")) {
                if (justChecking) {
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger an opportunity for a mahact mech trigger");
                    }
                    numberOfAbilities++;
                } else {
                    Button gainTG = Button.success(fincheckerForNonActive + "mahactMechHit_" + activeSystem.getPosition() + "_" + player.getColor(),
                        "Return " + player.getColor() + " CC and end their turn");
                    Button decline = Button.danger(fincheckerForNonActive + "deleteButtons", "Decline To Use Mech");
                    List<Button> buttons = List.of(gainTG, decline);
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(nonActivePlayer, activeGame), ident + " use buttons to resolve Mahact mech ability ", buttons);
                }
            }
            if (nonActivePlayer.hasTechReady("nf") && FoWHelper.playerHasShipsInSystem(nonActivePlayer, activeSystem) && nonActivePlayer.getStrategicCC() > 0) {
                if (justChecking) {
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger an opportunity for nullification field to trigger");
                    }
                    numberOfAbilities++;
                } else {
                    Button gainTG = Button.success(fincheckerForNonActive + "nullificationField_" + activeSystem.getPosition() + "_" + player.getColor(),
                        "Spend Strat CC and end their turn");
                    Button decline = Button.danger(fincheckerForNonActive + "deleteButtons", "Decline To Use Nullification Field");
                    List<Button> buttons = List.of(gainTG, decline);
                    MessageHelper.sendMessageToChannelWithButtons(channel, ident + " use buttons to resolve Nullfication field ", buttons);
                }
            }
            if (activeGame.playerHasLeaderUnlockedOrAlliance(nonActivePlayer, "yssarilcommander") && FoWHelper.playerHasUnitsInSystem(nonActivePlayer, activeSystem)) {
                if (justChecking) {
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(channel, "Warning: you would trigger yssaril commander");
                    }
                    numberOfAbilities++;
                } else {
                    Button lookAtACs = Button.success(fincheckerForNonActive + "yssarilcommander_ac_" + player.getFaction(), "Look at ACs (" + player.getAc() + ")");
                    Button lookAtPNs = Button.success(fincheckerForNonActive + "yssarilcommander_pn_" + player.getFaction(), "Look at PNs (" + player.getPnCount() + ")");
                    Button lookAtSOs = Button.success(fincheckerForNonActive + "yssarilcommander_so_" + player.getFaction(), "Look at SOs (" + (player.getSo()) + ")");
                    Button decline = Button.danger(fincheckerForNonActive + "deleteButtons", "Decline Commander");
                    List<Button> buttons = List.of(lookAtACs, lookAtPNs, lookAtSOs, decline);
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(nonActivePlayer, activeGame), ident + " use buttons to resolve Yssaril commander ", buttons);
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
                        if (pn.endsWith("_an") && nonActivePlayer.hasLeaderUnlocked("bentorcommander")) {
                            player.setCommoditiesTotal(player.getCommodities() - 1);
                        }
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
        if (tile == null) {
            BotLogger.log("Couldnt find tile for " + planetName + " in game " + activeGame.getName());
            return "none";
        }
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        if (unitHolder == null) {
            BotLogger.log("Couldnt find unitholder for " + planetName + " in game " + activeGame.getName());
            return "none";
        }
        Set<String> tokenList = unitHolder.getTokenList();
        if (CollectionUtils.containsAny(tokenList, "attachment_warfare.png", "attachment_cybernetic.png", "attachment_biotic.png", "attachment_propulsion.png")) {
            String type = "warfare";
            if (tokenList.contains("attachment_" + type + ".png")) {
                return type;
            }
            type = "cybernetic";
            if (tokenList.contains("attachment_" + type + ".png")) {
                return type;
            }
            type = "propulsion";
            if (tokenList.contains("attachment_" + type + ".png")) {
                return type;
            }
            type = "biotic";
            if (tokenList.contains("attachment_" + type + ".png")) {
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

    public static List<Button> getAllTechsToReady(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String tech : player.getExhaustedTechs()) {
            buttons.add(Button.success("biostimsReady_tech_" + tech, "Ready " + Mapper.getTechs().get(tech).getName()));
        }
        return buttons;
    }

    public static void sendAllTechsNTechSkipPlanetsToReady(Game activeGame, GenericInteractionCreateEvent event, Player player, boolean absol) {
        List<Button> buttons = new ArrayList<>();
        for (String tech : player.getExhaustedTechs()) {
            buttons.add(Button.success("biostimsReady_tech_" + tech, "Ready " + Mapper.getTechs().get(tech).getName()));
        }
        for (String planet : player.getExhaustedPlanets()) {
            if (absol || (Mapper.getPlanet(planet).getTechSpecialties() != null && Mapper.getPlanet(planet).getTechSpecialties().size() > 0) || checkForTechSkipAttachments(activeGame, planet)) {
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
        deleteTheOneButton(event);
    }

    public static void bioStimsReady(Game activeGame, GenericInteractionCreateEvent event, Player player, String buttonID) {
        buttonID = buttonID.replace("biostimsReady_", "");
        String last = buttonID.substring(buttonID.lastIndexOf("_") + 1);
        if (buttonID.contains("tech_")) {
            player.refreshTech(last);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " readied tech: " + Helper.getTechRepresentation(last));
            if (player.getLeaderIDs().contains("kolumecommander") && !player.hasLeaderUnlocked("kolumecommander")) {
                ButtonHelper.commanderUnlockCheck(player, activeGame, "kolume", event);
            }
        } else {
            player.refreshPlanet(last);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " readied planet: " + Helper.getPlanetRepresentation(last, activeGame));
        }
    }

    public static boolean isPlayerOverLimit(Game activeGame, Player player) {
        if (player.hasAbility("crafty")) {
            return false;
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
        if (player.getTechs().contains("absol_nm")) {
            limit = limit + 3;
        }
        if (player.getRelics().contains("e6-g0_network")) {
            limit = limit + 2;
        }
        return player.getAc() > limit;
    }

    public static void checkACLimit(Game activeGame, GenericInteractionCreateEvent event, Player player) {
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
        if (player.getTechs().contains("absol_nm")) {
            limit = limit + 3;
        }
        if (player.getRelics().contains("e6-g0_network")) {
            limit = limit + 2;
        }
        if (isPlayerOverLimit(activeGame, player)) {
            MessageChannel channel = activeGame.getMainGameChannel();
            if (activeGame.isFoWMode()) {
                channel = player.getPrivateChannel();
            }
            String ident = player.getRepresentation(true, true);
            MessageHelper.sendMessageToChannel(channel,
                ident + " you are exceeding the AC hand limit of " + limit + ". Please discard down to the limit. Check your cards info thread for the blue discard buttons. ");
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), ident + " use buttons to discard", ACInfo.getDiscardActionCardButtons(activeGame, player, false));
        }
    }

    public static void updateMap(Game activeGame, GenericInteractionCreateEvent event) {

        updateMap(activeGame, event, "");
    }

    public static void updateMap(Game activeGame, GenericInteractionCreateEvent event, String message) {
        String threadName = activeGame.getName() + "-bot-map-updates";
        List<ThreadChannel> threadChannels = activeGame.getActionsChannel().getThreadChannels();
        MapGenerator.saveImage(activeGame, DisplayType.all, event)
            .thenApply(fileUpload -> {
                boolean foundSomething = false;
                if (!activeGame.isFoWMode()) {
                    for (ThreadChannel threadChannel_ : threadChannels) {
                        if (threadChannel_.getName().equals(threadName)) {
                            foundSomething = true;

                            List<Button> buttonsWeb = new ArrayList<>();
                            if (!activeGame.isFoWMode()) {
                                Button linkToWebsite = Button.link("https://ti4.westaddisonheavyindustries.com/game/" + activeGame.getName(), "Website View");
                                buttonsWeb.add(linkToWebsite);
                            }
                            buttonsWeb.add(Button.success("cardsInfo", "Cards Info"));
                            buttonsWeb.add(Button.primary("offerDeckButtons", "Show Decks"));
                            buttonsWeb.add(Button.secondary("showGameAgain", "Show Game"));

                            MessageHelper.sendFileToChannelWithButtonsAfter(threadChannel_, fileUpload, message, buttonsWeb);
                        }
                    }
                } else {
                    MessageHelper.sendFileUploadToChannel(event.getMessageChannel(), fileUpload);
                    foundSomething = true;
                }
                if (!foundSomething) {
                    List<Button> buttonsWeb = new ArrayList<>();
                    if (!activeGame.isFoWMode()) {
                        Button linkToWebsite = Button.link("https://ti4.westaddisonheavyindustries.com/game/" + activeGame.getName(), "Website View");
                        buttonsWeb.add(linkToWebsite);
                    }
                    buttonsWeb.add(Button.success("cardsInfo", "Cards Info"));
                    buttonsWeb.add(Button.primary("offerDeckButtons", "Show Decks"));
                    buttonsWeb.add(Button.secondary("showGameAgain", "Show Game"));

                    MessageHelper.sendFileToChannelWithButtonsAfter(event.getMessageChannel(), fileUpload, message, buttonsWeb);
                }
                return fileUpload;
            });
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
            if (FoWHelper.playerHasUnitsInSystem(player, tile) && FoWHelper.doesTileHaveAlphaOrBeta(activeGame, tile.getPosition())) {
                count = count + 1;
            }
        }
        return count;
    }

    public static int getNumberOfTilesPlayerIsInWithNoPlanets(Game activeGame, Player player) {
        int count = 0;
        for (Tile tile : activeGame.getTileMap().values()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tile) && tile.getUnitHolders().values().size() == 1) {
                count++;
            }
        }
        return count;
    }

    public static int getNumberOfUncontrolledNonLegendaryPlanets(Game activeGame) {
        int count = 0;
        for (Tile tile : activeGame.getTileMap().values()) {
            for (UnitHolder plan : tile.getPlanetUnitHolders()) {
                if (plan.getName().contains("mallice")) {
                    continue;
                }
                Planet planet = (Planet) plan;
                if (planet.isHasAbility()) {
                    continue;
                }
                boolean unowned = true;
                for (Player player : activeGame.getRealPlayers()) {
                    if (player.getPlanets().contains(plan.getName())) {
                        unowned = false;
                    }
                }
                if (unowned) {
                    count++;
                }
            }
        }
        return count;
    }

    public static int getNumberOfNonHomeAnomaliesPlayerIsIn(Game activeGame, Player player) {
        int count = 0;
        int asteroids = 0;
        int grav = 0;
        int nebula = 0;
        for (Tile tile : activeGame.getTileMap().values()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tile) && tile.isAnomaly() && !ButtonHelper.isTileHomeSystem(tile)) {
                if (tile.isGravityRift(activeGame)) {
                    grav = 1;
                } else if (tile.isNebula()) {
                    nebula = 1;
                } else if (tile.isAsteroidField()) {
                    asteroids = 1;
                } else {
                    count = 1;
                }
            }
        }
        return count + asteroids + grav + nebula;
    }

    public static int getNumberOfAsteroidsPlayerIsIn(Game activeGame, Player player) {
        int count = 0;
        for (Tile tile : activeGame.getTileMap().values()) {
            if (tile.isAsteroidField() && FoWHelper.playerHasShipsInSystem(player, tile)) {
                count++;
            }
        }
        return count;
    }

    public static int getNumberOfXTypePlanets(Player player, Game activeGame, String type) {
        int count = 0;
        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.toLowerCase().contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            Planet p = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
            if (p != null && (type.equalsIgnoreCase(p.getOriginalPlanetType()) || p.getTokenList().contains("attachment_titanspn.png"))) {
                count++;
            }
        }
        return count;
    }

    public static int checkHighestProductionSystem(Player player, Game activeGame) {
        int count = 0;
        for (Tile tile : activeGame.getTileMap().values()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tile) && Helper.getProductionValue(player, activeGame, tile, false) > count) {
                count = Helper.getProductionValue(player, activeGame, tile, false);
            }
        }
        return count;
    }

    public static int checkHighestCostSystem(Player player, Game activeGame) {
        int count = 0;
        for(Tile tile : activeGame.getTileMap().values()){
            if(FoWHelper.playerHasShipsInSystem(player, tile) && checkValuesOfNonFighterShips(player, activeGame, tile) > count){
                count = checkValuesOfNonFighterShips(player, activeGame, tile);
            }
        }
        return count;
    }
    public static int checkNumberNonFighterShips(Player player, Game activeGame, Tile tile) {
        int count = 0;
        UnitHolder space = tile.getUnitHolders().get("space");
        for (UnitKey unit : space.getUnits().keySet()) {
            if (!unit.getColor().equals(player.getColor())) {
                continue;
            }
            UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).get(0);
            if (removedUnit.getIsShip() && !removedUnit.getAsyncId().contains("ff")) {
                count = count + (int) space.getUnits().get(unit);
            }
        }
        return count;
    }
     public static int checkTypesOfNonFighterShips(Player player, Game activeGame, Tile tile) {
        int count = 0;
        UnitHolder space = tile.getUnitHolders().get("space");
        for (UnitKey unit : space.getUnits().keySet()) {
            if (!unit.getColor().equals(player.getColor())) {
                continue;
            }
            UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).get(0);
            if (removedUnit.getIsShip() && !removedUnit.getAsyncId().contains("ff")) {
                count = count + 1;
            }
        }
        return count;
    }
    public static int checkValuesOfNonFighterShips(Player player, Game activeGame, Tile tile) {
        int count = 0;
        UnitHolder space = tile.getUnitHolders().get("space");
        for (UnitKey unit : space.getUnits().keySet()) {
            if (!unit.getColor().equals(player.getColor())) {
                continue;
            }
            UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).get(0);
            if (removedUnit.getIsShip() && !removedUnit.getAsyncId().contains("ff")) {
                count = count + (int) removedUnit.getCost() * space.getUnits().get(unit);
            }
        }
        return count;
    }
    public static float getTotalResourceValueOfUnits(Player player, Game activeGame) {
        float count = 0;
        for(Tile tile : activeGame.getTileMap().values()){
                count = count + checkValuesOfUnits(player, activeGame, tile);
        }
        return count;
    }
    public static float checkValuesOfUnits(Player player, Game activeGame, Tile tile) {
        float count = 0;
        for(UnitHolder uh : tile.getUnitHolders().values()){
            for(UnitKey unit : uh.getUnits().keySet()){
                if(!unit.getColor().equals(player.getColor())){
                    continue;
                }
                if(player.getUnitsByAsyncID(unit.asyncID()).size() == 0){
                    continue;
                }
                UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).get(0);
                if(removedUnit.getIsShip() || removedUnit.getIsGroundForce()){
                    count = count + removedUnit.getCost() * uh.getUnits().get(unit);
                }
            }
        }
        return Math.round(count*10)/(float)10.0;
    }
    public static float getTotalUnitAbilityValueOfUnits(Player player, Game activeGame) {
        float count = 0;
        for(Tile tile : activeGame.getTileMap().values()){
                count = count + checkUnitAbilityValuesOfUnits(player, activeGame, tile);
        }
        return Math.round(count*10)/(float)10.0;
    }
    public static float checkUnitAbilityValuesOfUnits(Player player, Game activeGame, Tile tile) {
        float count = 0;
        for(UnitHolder uh : tile.getUnitHolders().values()){
            for(UnitKey unit : uh.getUnits().keySet()){
                if(!unit.getColor().equals(player.getColor())){
                    continue;
                }
                if(player.getUnitsByAsyncID(unit.asyncID()).size() == 0){
                    continue;
                }
                UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).get(0);
                float hitChance = 0;
                if(removedUnit.getAfbDieCount() > 0){
                    hitChance = (((float)11.0-removedUnit.getAfbHitsOn())/10);
                    if(activeGame.playerHasLeaderUnlockedOrAlliance(player, "jolnarcommander")){
                        hitChance = 1- ((1-hitChance) * (1-hitChance));
                    }
                    count = count + removedUnit.getAfbDieCount() * hitChance * uh.getUnits().get(unit);
                }
                if(removedUnit.getSpaceCannonDieCount() > 0){
                    hitChance =  (((float)11.0-removedUnit.getSpaceCannonHitsOn())/10);
                    if(activeGame.playerHasLeaderUnlockedOrAlliance(player, "jolnarcommander")){
                        hitChance = 1- ((1-hitChance) * (1-hitChance));
                    }
                    count = count + removedUnit.getSpaceCannonDieCount() * hitChance * uh.getUnits().get(unit);
                }
                if(removedUnit.getBombardDieCount() > 0){
                    hitChance = (((float)11.0-removedUnit.getBombardHitsOn())/10);
                    if(activeGame.playerHasLeaderUnlockedOrAlliance(player, "jolnarcommander")){
                        hitChance = 1- ((1-hitChance) * (1-hitChance));
                    }
                    count = count + removedUnit.getBombardDieCount() * hitChance * uh.getUnits().get(unit);
                }
            }
        }
        return Math.round(count*10)/(float)10.0;
    }
    public static float getTotalCombatValueOfUnits(Player player, Game activeGame) {
        float count = 0;
        for(Tile tile : activeGame.getTileMap().values()){
                count = count + checkCombatValuesOfUnits(player, activeGame, tile);
        }
        return  Math.round(count*10)/(float)10.0;
    }
    public static float checkCombatValuesOfUnits(Player player, Game activeGame, Tile tile) {
        float count = 0;
        for(UnitHolder uh : tile.getUnitHolders().values()){
            for(UnitKey unit : uh.getUnits().keySet()){
                if(!unit.getColor().equals(player.getColor())){
                    continue;
                }
                if(player.getUnitsByAsyncID(unit.asyncID()).size() == 0){
                    continue;
                }
                UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).get(0);
                float unrelententing = 0;
                if(player.hasAbility("unrelenting")){
                    unrelententing = (float)0.1;
                }else if(player.hasAbility("fragile")){
                    unrelententing = (float)-0.1;
                }
                if(removedUnit.getIsShip() || removedUnit.getIsGroundForce()){
                    count = count + removedUnit.getCombatDieCount() * (((float)11.0-removedUnit.getCombatHitsOn())/10+unrelententing) * uh.getUnits().get(unit);
                }
            }
        }
        return  Math.round(count*10)/(float)10.0;
    }

    public static int getTotalHPValueOfUnits(Player player, Game activeGame) {
        int count = 0;
        for(Tile tile : activeGame.getTileMap().values()){
                count = count + checkHPOfUnits(player, activeGame, tile);
        }
        return count;
    }
    public static int checkHPOfUnits(Player player, Game activeGame, Tile tile) {
        int count = 0;
        for(UnitHolder uh : tile.getUnitHolders().values()){
            for(UnitKey unit : uh.getUnits().keySet()){
                if(!unit.getColor().equals(player.getColor())){
                    continue;
                }
                if(player.getUnitsByAsyncID(unit.asyncID()).size() == 0){
                    continue;
                }
                UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).get(0);
                if(removedUnit.getIsShip() || removedUnit.getIsGroundForce()){
                    int sustain = 0;
                    if(removedUnit.getSustainDamage()){
                        sustain = 1;
                        if(player.hasTech("nes")){
                            sustain =2;
                        }
                    }
                    int damagedUnits = 0;
                    if (uh.getUnitDamage() != null && uh.getUnitDamage().get(unit) != null) {
                        damagedUnits = uh.getUnitDamage().get(unit);
                    }
                    int totalUnits = uh.getUnits().get(unit);
                    totalUnits = totalUnits - damagedUnits;
                    count = count + uh.getUnits().get(unit);
                    count = count + sustain * totalUnits;
                }
            }
        }
        return count;
    }
    public static int howManyDifferentDebtPlayerHas(Player player){
        int count = 0;
        for (String color : player.getDebtTokens().keySet()) {
            if (player.getDebtTokens().get(color) > 0) {
                count++;
            }
        }
        return count;
    }

    public static int getNumberOfPlanetsWithStructuresNotInHS(Player player, Game activeGame) {
        int count = 0;
        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.toLowerCase().contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            if (ButtonHelper.isTileHomeSystem(activeGame.getTileFromPlanet(planet))) {
                continue;
            }
            Planet p = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
            if (p != null && (p.getUnitCount(UnitType.Spacedock, player.getColor()) > 0 || p.getUnitCount(UnitType.Pds, player.getColor()) > 0
                || (p.getUnitCount(UnitType.Mech, player.getColor()) > 0 && player.hasAbility("byssus")))) {
                count++;
            }
        }
        return count;
    }

    public static int getNumberOfSpacedocksNotInOrAdjacentHS(Player player, Game activeGame) {
        int count = 0;
        Tile hs = FoWHelper.getPlayerHS(activeGame, player);
        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.toLowerCase().contains("custodia") || planet.contains("ghoti")) {
                continue;
            }

            if (activeGame.getTileFromPlanet(planet) == hs || FoWHelper.getAdjacentTiles(activeGame, hs.getPosition(), player, false).contains(activeGame.getTileFromPlanet(planet).getPosition())) {
                continue;
            }
            Planet p = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
            if (p != null && p.getUnitCount(UnitType.Spacedock, player.getColor()) > 0) {
                count++;
            }
        }
        return count;
    }

    public static int getNumberOfSystemsWithShipsNotAdjacentToHS(Player player, Game activeGame) {
        int count = 0;
        Tile hs = FoWHelper.getPlayerHS(activeGame, player);
        if (hs == null) {
            BotLogger.log("not finding a HS for " + player.getFaction() + " in " + activeGame.getName());
            return 0;
        }
        String hsPos = hs.getPosition();
        for (Tile tile : activeGame.getTileMap().values()) {
            if (tile == hs) {
                continue;
            }
            if (FoWHelper.playerHasShipsInSystem(player, tile) && !FoWHelper.getAdjacentTiles(activeGame, tile.getPosition(), player, false).contains(hsPos)) {
                count++;
            }
        }
        return count;
    }

    public static void fullCommanderUnlockCheck(Player player, Game activeGame, String faction, GenericInteractionCreateEvent event) {
        if (player.getLeaderIDs().contains(faction + "commander") && !player.hasLeaderUnlocked(faction + "commander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, faction, event);
        }
    }

    public static void commanderUnlockCheck(Player player, Game activeGame, String faction, GenericInteractionCreateEvent event) {
        boolean shouldBeUnlocked = false;
        switch (faction) {
            case "axis" -> {
                if (ButtonHelperAbilities.getNumberOfDifferentAxisOrdersBought(player, activeGame) > 3) {
                    shouldBeUnlocked = true;
                }
            }
            case "rohdhna" -> {
                if (checkHighestProductionSystem(player, activeGame) > 6) {
                    shouldBeUnlocked = true;
                }
            }
            case "freesystems" -> {
                if (getNumberOfUncontrolledNonLegendaryPlanets(activeGame) < 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "mortheus" -> {
                if (getNumberOfSystemsWithShipsNotAdjacentToHS(player, activeGame) > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "celdauri" -> {
                if (getNumberOfSpacedocksNotInOrAdjacentHS(player, activeGame) > 0) {
                    shouldBeUnlocked = true;
                }
            }
            case "cheiran" -> {
                if (getNumberOfPlanetsWithStructuresNotInHS(player, activeGame) > 3) {
                    shouldBeUnlocked = true;
                }
            }
            case "vaden" -> {
                if (howManyDifferentDebtPlayerHas(player) > (activeGame.getRealPlayers().size() / 2) - 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "gledge" -> {
                if (checkHighestCostSystem(player, activeGame) > 9) {
                    shouldBeUnlocked = true;
                }
            }
            case "olradin" -> {
                if (getNumberOfXTypePlanets(player, activeGame, "industrial") > 0 && getNumberOfXTypePlanets(player, activeGame, "cultural") > 0
                    && getNumberOfXTypePlanets(player, activeGame, "hazardous") > 0) {
                    shouldBeUnlocked = true;
                }
            }
            case "vaylerian" -> {
                if (getNumberOfXTypePlanets(player, activeGame, "industrial") > 2 || getNumberOfXTypePlanets(player, activeGame, "cultural") > 2
                    || getNumberOfXTypePlanets(player, activeGame, "hazardous") > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "ghoti" -> {
                if (getNumberOfTilesPlayerIsInWithNoPlanets(activeGame, player) > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "nivyn" -> {
                if (getNumberOfNonHomeAnomaliesPlayerIsIn(activeGame, player) > 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "zelian" -> {
                if (getNumberOfAsteroidsPlayerIsIn(activeGame, player) > 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "yssaril" -> {
                if (player.getActionCards().size() > 7 || (player.getExhaustedTechs().contains("mi") && player.getActionCards().size() > 6)) {
                    shouldBeUnlocked = true;
                }
            }
            case "kjalengard" -> {
                if (ButtonHelperAgents.getGloryTokenTiles(activeGame).size() > 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "kolume" -> {
                shouldBeUnlocked = true;

            }
            case "veldyr" -> {
                if (ButtonHelperFactionSpecific.getPlayersWithBranchOffices(activeGame, player).size() > 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "mirveda" -> {
                if (getNumberOfUnitUpgrades(player) > 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "dihmohn" -> {
                if (getNumberOfUnitUpgrades(player) > 0) {
                    shouldBeUnlocked = true;
                }
            }
            case "kollecc" -> {
                if (player.getCrf() + player.getHrf() + player.getIrf() + player.getUrf() > 3) {
                    shouldBeUnlocked = true;
                }
            }
            case "bentor" -> {
                if (player.getNumberOfBluePrints() > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "edyn" -> {
                if (activeGame.getLaws().size() > 0) {
                    shouldBeUnlocked = true;
                }
            }
            case "lizho" -> {
                if (player.getTrapCardsPlanets().size() > 2) {
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
            case "ghemina" -> {
                if ((getNumberOfUnitsOnTheBoard(activeGame, player, "flagship") + getNumberOfUnitsOnTheBoard(activeGame, player, "lady")) > 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "tnelis" -> {
                if (getNumberOfUnitsOnTheBoard(activeGame, player, "destroyer") > 5) {
                    shouldBeUnlocked = true;
                }
            }
            case "cymiae" -> {
                if (getNumberOfUnitsOnTheBoard(activeGame, player, "infantry") > 9) {
                    shouldBeUnlocked = true;
                }
            }
            case "kyro" -> {
                if (getNumberOfUnitsOnTheBoard(activeGame, player, "infantry") > 5 && getNumberOfUnitsOnTheBoard(activeGame, player, "fighter") > 5) {
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
                int count = 2;
                if (player.hasTech("vax")) {
                    count++;
                }
                if (player.hasTech("vay")) {
                    count++;
                }
                if (player.getTechs().size() > count) {
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
            case "lanefir" -> {
                if (activeGame.getNumberOfPurgedFragments() > 6) {
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
        if (!player.hasAbility("awaken")) {
            return;
        }
        for (String planet : tile.getPlanetsWithSleeperTokens()) {
            List<Button> planetsWithSleepers = new ArrayList<>();
            planetsWithSleepers.add(Button.success(finChecker + "replaceSleeperWith_pds_" + planet, "Replace sleeper on " + planet + " with a pds."));
            if (getNumberOfUnitsOnTheBoard(activeGame, player, "mech") < 4 && player.hasUnit("titans_mech")) {
                planetsWithSleepers.add(Button.success(finChecker + "replaceSleeperWith_mech_" + planet, "Replace sleeper on " + planet + " with a mech and an infantry."));
            }
            planetsWithSleepers.add(Button.danger("deleteButtons", "Delete these buttons"));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to resolve sleeper", planetsWithSleepers);
        }

    }

    public static List<Button> getButtonsForTurningPDSIntoFS(Player player, Game activeGame, Tile tile) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> planetsWithPDS = new ArrayList<>();
        if (!player.hasUnit("titans_flagship")) {
            return planetsWithPDS;
        }
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

    public static List<Player> getOtherPlayersWithUnitsInTheSystem(Player player, Game activeGame, Tile tile) {
        List<Player> playersWithShips = new ArrayList<>();
        for (Player p2 : activeGame.getPlayers().values()) {
            if (p2 == player || !p2.isRealPlayer()) {
                continue;
            }
            if (FoWHelper.playerHasUnitsInSystem(p2, tile)) {
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
        String ident = getIdentOrColor(player, activeGame);
        String msg = ident + " removed CC from " + tileRep;
        if (whatIsItFor.contains("mahactAgent")) {
            String faction = whatIsItFor.replace("mahactAgent", "");
            if (player != null) {
                MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), msg);
            }
            player = activeGame.getPlayerFromColorOrFaction(faction);
            msg = player.getRepresentation(true, true) + " " + msg + " using Mahact agent";
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
                player.getRepresentation() + " click this after picking up a CC.", redistributeButton);
        }

    }

    public static void resolveMahactMechAbilityUse(Player mahact, Player target, Game activeGame, Tile tile, ButtonInteractionEvent event) {
        mahact.removeMahactCC(target.getColor());
        if (!activeGame.getNaaluAgent()) {
            target.setTacticalCC(target.getTacticalCC() - 1);
            AddCC.addCC(event, target.getColor(), tile);
        }
        MessageHelper.sendMessageToChannel(getCorrectChannel(mahact, activeGame),
            mahact.getRepresentation(true, true) + " the " + target.getColor() + " cc has been removed from your fleet pool");
        List<Button> conclusionButtons = new ArrayList<>();
        Button endTurn = Button.danger("turnEnd", "End Turn");
        conclusionButtons.add(endTurn);
        if (getEndOfTurnAbilities(target, activeGame).size() > 1) {
            conclusionButtons.add(Button.primary("endOfTurnAbilities", "Do End Of Turn Ability (" + (getEndOfTurnAbilities(target, activeGame).size() - 1) + ")"));
        }
        Button getTactic = Button.success("increase_tactic_cc", "Gain 1 Tactic CC");
        Button getFleet = Button.success("increase_fleet_cc", "Gain 1 Fleet CC");
        Button getStrat = Button.success("increase_strategy_cc", "Gain 1 Strategy CC");
        Button doneGainingCC = Button.danger("deleteButtons", "Done Gaining CCs");
        List<Button> buttons = List.of(getTactic, getFleet, getStrat, doneGainingCC);
        String trueIdentity = target.getRepresentation(true, true);
        String message2 = trueIdentity + "! Your current CCs are " + target.getCCRepresentation() + ". Use buttons to gain CCs";
        MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(target, activeGame), message2, buttons);
        MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(target, activeGame), target.getRepresentation(true, true)
            + " You've been hit with the Mahact mech ability. A cc has been placed from your tactics in the system and your turn has been ended. Use the buttons to resolve end of turn abilities and then end turn.",
            conclusionButtons);
        event.getMessage().delete().queue();

    }

    public static void resolveNullificationFieldUse(Player mahact, Player target, Game activeGame, Tile tile, ButtonInteractionEvent event) {
        mahact.setStrategicCC(mahact.getStrategicCC() - 1);
        mahact.exhaustTech("nf");
        ButtonHelperCommanders.resolveMuaatCommanderCheck(mahact, activeGame, event);
        if (!activeGame.getNaaluAgent()) {
            target.setTacticalCC(target.getTacticalCC() - 1);
            AddCC.addCC(event, target.getColor(), tile);
        }
        MessageHelper.sendMessageToChannel(getCorrectChannel(mahact, activeGame),
            mahact.getRepresentation(true, true) + " you have spent a strat cc");
        List<Button> conclusionButtons = new ArrayList<>();
        Button endTurn = Button.danger("turnEnd", "End Turn");
        conclusionButtons.add(endTurn);
        if (getEndOfTurnAbilities(target, activeGame).size() > 1) {
            conclusionButtons.add(Button.primary("endOfTurnAbilities", "Do End Of Turn Ability (" + (getEndOfTurnAbilities(target, activeGame).size() - 1) + ")"));
        }

        MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(target, activeGame), target.getRepresentation(true, true)
            + " You've been hit with nullification field. A cc has been placed from your tactics in the system and your turn has been ended. Use the buttons to resolve end of turn abilities and then end turn.",
            conclusionButtons);
        event.getMessage().delete().queue();

    }

    public static void resolveMinisterOfPeace(Player minister, Game activeGame, ButtonInteractionEvent event) {
        Player target = activeGame.getActivePlayerObject();

        if (target == null || target == minister) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Target player not found");
            return;
        }
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Active system not found");
            return;
        }
        boolean success = activeGame.removeLaw(activeGame.getLaws().get("minister_peace"));
        if (success) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Minister of War Law removed");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Law ID not found");
            return;
        }

        if (!activeGame.getNaaluAgent()) {
            if (!AddCC.hasCC(target, tile)) {
                target.setTacticalCC(target.getTacticalCC() - 1);
                AddCC.addCC(event, target.getColor(), tile);
            }
        }
        MessageHelper.sendMessageToChannel(getCorrectChannel(minister, activeGame),
            minister.getRepresentation(true, true) + " you have used the Minister of Peace agenda");
        List<Button> conclusionButtons = new ArrayList<>();
        Button endTurn = Button.danger("turnEnd", "End Turn");
        conclusionButtons.add(endTurn);
        if (getEndOfTurnAbilities(target, activeGame).size() > 1) {
            conclusionButtons.add(Button.primary("endOfTurnAbilities", "Do End Of Turn Ability (" + (getEndOfTurnAbilities(target, activeGame).size() - 1) + ")"));
        }

        MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(target, activeGame), target.getRepresentation(true, true)
            + " You've been hit with minister of peace. A cc has been placed from your tactics in the system and your turn has been ended. Use the buttons to resolve end of turn abilities and then end turn.",
            conclusionButtons);
        ButtonHelper.deleteTheOneButton(event);

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
            if (whatIsItFor.contains("kjal")) {
                String pos = whatIsItFor.split("_")[1];
                if (!pos.equalsIgnoreCase(tile.getPosition()) && !FoWHelper.getAdjacentTiles(activeGame, pos, player, false).contains(tile.getPosition())) {
                    continue;
                }
            }
            buttonsToRemoveCC.add(
                Button.success(finChecker + "removeCCFromBoard_" + whatIsItFor.replace("_", "") + "_" + tile.getPosition(), "Remove CC from " + tile.getRepresentationForButtons(activeGame, player)));
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
        for (ThreadChannel threadChannel_ : textChannel.getThreadChannels()) {
            if (threadChannel_.getName().equals(threadName)) {
                initializeCombatThread(threadChannel_, activeGame, p1, p2, tile, event, spaceOrGround);
                return;
            }
        }
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
        channel.sendMessage(baseMessageObject.build()).queue(message -> {
            ThreadChannelAction threadChannel = textChannel.createThreadChannel(threadName, message.getId());
            if (activeGame.isFoWMode()) {
                threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_3_DAYS);
            } else {
                threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR);
            }
            threadChannel.queue(tc -> initializeCombatThread(tc, activeGame, p1, p2, tile, event, spaceOrGround, systemWithContext));
        });

    }

    private static void initializeCombatThread(ThreadChannel tc, Game activeGame, Player p1, Player p2, Tile tile, GenericInteractionCreateEvent event, String spaceOrGround) {
        initializeCombatThread(tc, activeGame, p1, p2, tile, event, spaceOrGround, null);
    }

    private static void initializeCombatThread(ThreadChannel tc, Game activeGame, Player p1, Player p2, Tile tile, GenericInteractionCreateEvent event, String spaceOrGround, FileUpload file) {
        StringBuilder message = new StringBuilder();
        if (activeGame.isFoWMode()) {
            message.append(p1.getRepresentation(true, true));
        } else {
            message.append(p1.getRepresentation(true, true));
            message.append(p2.getRepresentation());
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
        FileUpload systemWithContext;
        if (file == null) {
            systemWithContext = GenerateTile.getInstance().saveImage(activeGame, context, tile.getPosition(), event, p1);
        } else {
            systemWithContext = file;
        }
        MessageHelper.sendMessageWithFile(tc, systemWithContext, "Picture of system", false);
        List<Button> buttons = getButtonsForPictureCombats(activeGame, tile.getPosition(), p1, p2, spaceOrGround);
        Button graviton = null;
        MessageHelper.sendMessageToChannelWithButtons(tc, "Combat", buttons);
        if (playersWithPds2.size() > 0 && !activeGame.isFoWMode() && "space".equalsIgnoreCase(spaceOrGround)) {
            StringBuilder pdsMessage = new StringBuilder("The following players have space cannon offense cover in the region, and can use the button to fire it:");
            for (Player playerWithPds : playersWithPds2) {
                pdsMessage.append(" ").append(playerWithPds.getRepresentation());
                if (playerWithPds.hasTechReady("gls") && graviton == null) {
                    graviton = Button.secondary("exhaustTech_gls", "Exhaust Graviton Laser Systems");
                }
            }
            MessageHelper.sendMessageToChannel(tc, pdsMessage.toString());
        } else {
            if (activeGame.isFoWMode()) {
                MessageHelper.sendMessageToChannel(tc, "In fog, it is the players responsibility to check for pds2");
            }
        }
        if ("space".equalsIgnoreCase(spaceOrGround)) {
            List<Button> buttons2 = new ArrayList<>();
            buttons2.add(Button.secondary("combatRoll_" + tile.getPosition() + "_space_afb", "Roll " + CombatRollType.AFB.getValue()));
            buttons2.add(Button.secondary("combatRoll_" + tile.getPosition() + "_space_spacecannonoffence", "Roll Space Cannon Offence"));
            if (!activeGame.isFoWMode()) {
                buttons2.add(Button.danger("declinePDS", "Decline PDS"));
                if((p1.hasTech("asc") && checkNumberNonFighterShips(p1, activeGame, tile) >2) || (p2.hasTech("asc") && checkNumberNonFighterShips(p2, activeGame, tile) > 2) ){
                    buttons2.add(Button.primary("assCannonNDihmohn_asc_" + tile.getPosition(), "Use Assault Cannon").withEmoji(Emoji.fromFormatted(Emojis.WarfareTech)));
                }
                if( FoWHelper.doesTileHaveWHs(activeGame,tile.getPosition()) && (p1.hasTech("ds") || p2.hasTech("ds"))){
                    buttons2.add(Button.primary("assCannonNDihmohn_ds_" + tile.getPosition(), "Use Dimensional Splicer").withEmoji(Emoji.fromFormatted(Emojis.Ghost)));
                }
                if((activeGame.playerHasLeaderUnlockedOrAlliance(p1, "dihmohncommander") && checkNumberNonFighterShips(p1, activeGame, tile) > 2) || (activeGame.playerHasLeaderUnlockedOrAlliance(p2, "dihmohncommander") && checkNumberNonFighterShips(p2, activeGame, tile) > 2) ){
                    buttons2.add(Button.primary("assCannonNDihmohn_dihmohn_" + tile.getPosition(), "Use Dihmohn Commander").withEmoji(Emoji.fromFormatted(Emojis.dihmohn)));
                }
                if((p1.hasAbility("ambush")) || (!activeGame.isFoWMode() && p2.hasAbility("ambush"))){

                    buttons2.add(Button.secondary("rollForAmbush_"+tile.getPosition(), "Ambush" ).withEmoji(Emoji.fromFormatted(Emojis.Mentak)));
                }
                
                buttons2.add(Button.secondary("announceARetreat", "Announce A Retreat"));
                
            }
            if (graviton != null) {
                buttons2.add(graviton);
            }
            MessageHelper.sendMessageToChannelWithButtons(tc, "You can use these buttons to roll AFB or Space Cannon Offence", buttons2);
        }

        if ((p1.hasTech("dslaner") && p1.getAtsCount() > 0) || (p2.hasTech("dslaner") && p2.getAtsCount() > 0)) {
            List<Button> buttons3 = new ArrayList<>(ButtonHelperFactionSpecific.getLanefirATSButtons(p1, p2));
            MessageHelper.sendMessageToChannelWithButtons(tc, "You can use these buttons to remove commodities from ATS Armaments", buttons3);
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
        activeGame.setSavedButtons(new ArrayList<>());
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
            if (button.getId() == null || "ultimateUndo".equalsIgnoreCase(button.getId())) {
                continue;
            }
            String builder = player.getFaction() + ";" + button.getId() + ";" + button.getLabel() + ";" + button.getStyle();
            if (button.getEmoji() != null && !"".equalsIgnoreCase(button.getEmoji().toString())) {
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
            if ("success".equalsIgnoreCase(style)) {
                if (emoji.length() > 0) {
                    buttons.add(Button.success(id, label).withEmoji(Emoji.fromFormatted(emoji)));
                } else {
                    buttons.add(Button.success(id, label));
                }
            } else if ("danger".equalsIgnoreCase(style)) {
                if (emoji.length() > 0) {
                    buttons.add(Button.danger(id, label).withEmoji(Emoji.fromFormatted(emoji)));
                } else {
                    buttons.add(Button.danger(id, label));
                }
            } else if ("secondary".equalsIgnoreCase(style)) {
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

    public static void resolveWarForgeRuins(Game activeGame, String buttonID, Player player, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String mech = buttonID.split("_")[2];
        String message = "";
        boolean failed;
        message = message + mechOrInfCheck(planet, activeGame, player);
        failed = message.contains("Please try again.");
        if (!failed) {
            if ("mech".equalsIgnoreCase(mech)) {
                new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileFromPlanet(planet), "mech " + planet, activeGame);
                message = message + "Placed mech on" + Mapper.getPlanet(planet).getName();
            } else {
                new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileFromPlanet(planet), "2 infantry " + planet, activeGame);
                message = message + "Placed 2 infantry on" + Mapper.getPlanet(planet).getName();
            }
            addReaction(event, false, false, message, "");
            event.getMessage().delete().queue();
        } else {
            addReaction(event, false, false, message, "");
        }
    }

    public static void resolveSeedySpace(Game activeGame, String buttonID, Player player, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[2];
        String acOrAgent = buttonID.split("_")[1];
        String message = "";
        boolean failed;
        message = message + mechOrInfCheck(planet, activeGame, player);
        failed = message.contains("Please try again.");
        if (!failed) {
            if ("ac".equalsIgnoreCase(acOrAgent)) {
                if (player.hasAbility("scheming")) {
                    activeGame.drawActionCard(player.getUserID());
                    activeGame.drawActionCard(player.getUserID());
                    message = getIdent(player) + " Drew 2 AC With Scheming. Please Discard An AC with the blue buttons";
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation(true, true) + " use buttons to discard",
                        ACInfo.getDiscardActionCardButtons(activeGame, player, false));
                } else {
                    activeGame.drawActionCard(player.getUserID());
                    message = getIdent(player) + " Drew 1 AC";
                    ACInfo.sendActionCardInfo(activeGame, player, event);
                }
                if (player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")) {
                    commanderUnlockCheck(player, activeGame, "yssaril", event);
                }
            } else {
                Leader playerLeader = player.getLeader(acOrAgent).orElse(null);
                if (playerLeader == null) {
                    return;
                }
                RefreshLeader.refreshLeader(player, playerLeader, activeGame);
                message = message + " Refreshed " + Mapper.getLeader(acOrAgent).getName();
            }
            addReaction(event, false, false, message, "");
            event.getMessage().delete().queue();
        } else {
            addReaction(event, false, false, message, "");
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
        //if (getButtonsForRepairingUnitsInASystem(p1, activeGame, tile).size() > 1 || getButtonsForRepairingUnitsInASystem(p2, activeGame, tile).size() > 1) {
        buttons.add(Button.success("getRepairButtons_" + pos, "Repair Damage"));
       // }
        buttons.add(Button.primary("refreshViewOfSystem_" + pos + "_" + p1.getFaction() + "_" + p2.getFaction() + "_" + groundOrSpace, "Refresh Picture"));

        Player titans = Helper.getPlayerFromUnlockedLeader(activeGame, "titansagent");
        if (!activeGame.isFoWMode() && titans != null && titans.hasUnexhaustedLeader("titansagent")) {
            String finChecker = "FFCC_" + titans.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "exhaustAgent_titansagent", "Titans Agent").withEmoji(Emoji.fromFormatted(Emojis.Titans)));
        }
        if(p1.hasTechReady("sc") || (!activeGame.isFoWMode() && p2.hasTechReady("sc"))){
           // TemporaryCombatModifierModel combatModAC = CombatTempModHelper.GetPossibleTempModifier("tech", "sc", p1.getNumberTurns());
            buttons.add(Button.success("applytempcombatmod__" + "tech" + "__" + "sc", "Use Super Charge" ).withEmoji(Emoji.fromFormatted(Emojis.Naaz)));
        }
        

        Player ghemina = Helper.getPlayerFromUnlockedLeader(activeGame, "gheminaagent");
        if (!activeGame.isFoWMode() && ghemina != null && ghemina.hasUnexhaustedLeader("gheminaagent")) {
            String finChecker = "FFCC_" + ghemina.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "exhaustAgent_gheminaagent", "Ghemina Agent").withEmoji(Emoji.fromFormatted(Emojis.ghemina)));
        }

        Player khal = Helper.getPlayerFromUnlockedLeader(activeGame, "kjalengardagent");
        if (!activeGame.isFoWMode() && khal != null && khal.hasUnexhaustedLeader("kjalengardagent")) {
            String finChecker = "FFCC_" + khal.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "exhaustAgent_kjalengardagent", "Kjalengard Agent").withEmoji(Emoji.fromFormatted(Emojis.kjalengard)));
        }

        Player sol = Helper.getPlayerFromUnlockedLeader(activeGame, "solagent");
        if (!activeGame.isFoWMode() && sol != null && sol.hasUnexhaustedLeader("solagent") && "ground".equalsIgnoreCase(groundOrSpace)) {
            String finChecker = "FFCC_" + sol.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "exhaustAgent_solagent", "Sol Agent").withEmoji(Emoji.fromFormatted(Emojis.Sol)));
        }

        Player kyro = Helper.getPlayerFromUnlockedLeader(activeGame, "kyroagent");
        if (!activeGame.isFoWMode() && kyro != null && kyro.hasUnexhaustedLeader("kyroagent") && "ground".equalsIgnoreCase(groundOrSpace)) {
            String finChecker = "FFCC_" + kyro.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "getAgentSelection_kyroagent", "Kyro Agent").withEmoji(Emoji.fromFormatted(Emojis.blex)));
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

        if ((p2.hasUnexhaustedLeader("kortaliagent")) && !activeGame.isFoWMode() && "ground".equalsIgnoreCase(groundOrSpace) && p1.getFragments().size() > 0) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "exhaustAgent_kortaliagent_" + p1.getColor(), "Use Kortali Agent To Steal Frag").withEmoji(Emoji.fromFormatted(Emojis.kortali)));
        }
        if (p1.hasUnexhaustedLeader("kortaliagent") && "ground".equalsIgnoreCase(groundOrSpace) && p2.getFragments().size() > 0) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "exhaustAgent_kortaliagent_" + p2.getColor(), "Use Kortali Agent To Steal Frag").withEmoji(Emoji.fromFormatted(Emojis.kortali)));
        }

        if ((p2.hasAbility("edict") || p2.hasAbility("imperia")) && !activeGame.isFoWMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "mahactStealCC_" + p1.getColor(), "Add Opponent CC to Fleet").withEmoji(Emoji.fromFormatted(Emojis.Mahact)));
        }
        if (p1.hasAbility("edict") || p1.hasAbility("imperia")) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "mahactStealCC_" + p2.getColor(), "Add Opponent CC to Fleet").withEmoji(Emoji.fromFormatted(Emojis.Mahact)));
        }
        if ((p2.hasAbility("for_glory")) && !activeGame.isFoWMode() && ButtonHelperAgents.getGloryTokenTiles(activeGame).size() < 3) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "placeGlory_" + pos, "Place Glory (Upon Win)").withEmoji(Emoji.fromFormatted(Emojis.kjalengard)));
        }
        if (p1.hasAbility("for_glory") && ButtonHelperAgents.getGloryTokenTiles(activeGame).size() < 3) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "placeGlory_" + pos, "Place Glory (Upon Win)").withEmoji(Emoji.fromFormatted(Emojis.kjalengard)));
        }

        if (p2.hasAbility("necrophage") && !activeGame.isFoWMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "offerNecrophage", "Necrophage").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }
        if (p1.hasAbility("necrophage")) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "offerNecrophage", "Necrophage").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }

        if ("space".equalsIgnoreCase(groundOrSpace) && activeGame.playerHasLeaderUnlockedOrAlliance(p2, "mentakcommander") && !activeGame.isFoWMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "mentakCommander_" + p1.getColor(), "Mentak Commander on " + p1.getColor()).withEmoji(Emoji.fromFormatted(Emojis.Mentak)));
        }
        if("space".equalsIgnoreCase(groundOrSpace) && ((p1.hasTech("so")) || (!activeGame.isFoWMode() && p2.hasTech("so")))){

            buttons.add(Button.secondary("salvageOps_"+tile.getPosition(), "Salvage Ops" ).withEmoji(Emoji.fromFormatted(Emojis.Mentak)));
        }
        if ("space".equalsIgnoreCase(groundOrSpace) && activeGame.playerHasLeaderUnlockedOrAlliance(p1, "mentakcommander")) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "mentakCommander_" + p2.getColor(), "Mentak Commander on " + p2.getColor()).withEmoji(Emoji.fromFormatted(Emojis.Mentak)));
        }

        if ("space".equalsIgnoreCase(groundOrSpace) && activeGame.playerHasLeaderUnlockedOrAlliance(p2, "mykomentoricommander") && !activeGame.isFoWMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "resolveMykoCommander", "Myko Commander").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }
        if ("space".equalsIgnoreCase(groundOrSpace) && activeGame.playerHasLeaderUnlockedOrAlliance(p1, "mykomentoricommander")) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "resolveMykoCommander", "Myko Commander").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }

        if ("space".equalsIgnoreCase(groundOrSpace) && doesPlayerHaveFSHere("mykomentori_flagship", p2, tile) && !activeGame.isFoWMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "gain_1_comm_from_MahactInf", "Myko Flagship").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }
        if ("space".equalsIgnoreCase(groundOrSpace) && doesPlayerHaveFSHere("mykomentori_flagship", p1, tile)) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "gain_1_comm_from_MahactInf", "Myko Flagship").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }

        
        if ("space".equalsIgnoreCase(groundOrSpace)) {
            buttons.add(Button.danger("retreat_" + pos, "Retreat"));
        }
        if ("space".equalsIgnoreCase(groundOrSpace) && p2.hasAbility("foresight") && p2.getStrategicCC() > 0 && !activeGame.isFoWMode()) {
            buttons.add(Button.danger("retreat_" + pos + "_foresight", "Foresight").withEmoji(Emoji.fromFormatted(Emojis.Naalu)));
        }
        if ("space".equalsIgnoreCase(groundOrSpace) && p1.hasAbility("foresight") && p1.getStrategicCC() > 0) {
            buttons.add(Button.danger("retreat_" + pos + "_foresight", "Foresight").withEmoji(Emoji.fromFormatted(Emojis.Naalu)));
        }
        boolean gheminaCommanderApplicable = false;
        if (tile.getPlanetUnitHolders().isEmpty()) {
            gheminaCommanderApplicable = true;
        } else {
            for (Player p3 : activeGame.getRealPlayers()) {
                if (ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, p3, UnitType.Pds, UnitType.Spacedock).contains(tile)) {
                    gheminaCommanderApplicable = true;
                    break;
                }
            }
        }
        if ("space".equalsIgnoreCase(groundOrSpace) && activeGame.playerHasLeaderUnlockedOrAlliance(p2, "gheminacommander") && gheminaCommanderApplicable && !activeGame.isFoWMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.danger(finChecker + "declareUse_Ghemina Commander", "Use Ghemina Commander").withEmoji(Emoji.fromFormatted(Emojis.ghemina)));
        }
        if ("space".equalsIgnoreCase(groundOrSpace) && activeGame.playerHasLeaderUnlockedOrAlliance(p1, "gheminacommander") && gheminaCommanderApplicable) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.danger(finChecker + "declareUse_Ghemina Commander", "Use Ghemina Commander").withEmoji(Emoji.fromFormatted(Emojis.ghemina)));
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

        if (p1.hasLeaderUnlocked("dihmohnhero") && "space".equalsIgnoreCase(groundOrSpace)) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "purgeDihmohnHero", "Dihmohn Hero")
                .withEmoji(Emoji.fromFormatted(Emojis.dihmohn)));
        }
        if (p2.hasLeaderUnlocked("dihmohnhero") && !activeGame.isFoWMode() && "space".equalsIgnoreCase(groundOrSpace)) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "purgeDihmohnHero", "Dihmohn Hero")
                .withEmoji(Emoji.fromFormatted(Emojis.dihmohn)));
        }

        if (p1.hasLeaderUnlocked("kortalihero")) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "purgeKortaliHero_"+p2.getFaction(), "Kortali Hero")
                .withEmoji(Emoji.fromFormatted(Emojis.dihmohn)));
        }
        if (p2.hasLeaderUnlocked("kortalihero") && !activeGame.isFoWMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "purgeKortaliHero_"+p1.getFaction(), "Kortali Hero")
                .withEmoji(Emoji.fromFormatted(Emojis.kortali)));
        }

        if (getTilesOfUnitsWithBombard(p1, activeGame).contains(tile) || getTilesOfUnitsWithBombard(p2, activeGame).contains(tile)) {
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
                if (p1 != activeGame.getActivePlayerObject() && activeGame.playerHasLeaderUnlockedOrAlliance(p1, "solcommander") && "ground".equalsIgnoreCase(groundOrSpace)) {
                    String finChecker = "FFCC_" + p1.getFaction() + "_";
                    buttons.add(Button.secondary(finChecker + "utilizeSolCommander_" + unitH.getName(), "Sol Commander on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.Sol)));
                }
                if (p2 != activeGame.getActivePlayerObject() && activeGame.playerHasLeaderUnlockedOrAlliance(p2, "solcommander") && !activeGame.isFoWMode()
                    && "ground".equalsIgnoreCase(groundOrSpace)) {
                    String finChecker = "FFCC_" + p2.getFaction() + "_";
                    buttons.add(Button.secondary(finChecker + "utilizeSolCommander_" + unitH.getName(), "Sol Commander on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.Sol)));
                }
                if (p1.hasAbility("indoctrination") && "ground".equalsIgnoreCase(groundOrSpace)) {
                    String finChecker = "FFCC_" + p1.getFaction() + "_";
                    buttons.add(Button.secondary(finChecker + "initialIndoctrination_" + unitH.getName(), "Indoctrinate on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.Yin)));
                }
                if (activeGame.playerHasLeaderUnlockedOrAlliance(p1, "cheirancommander") && "ground".equalsIgnoreCase(groundOrSpace) && p1 != activeGame.getActivePlayerObject()) {
                    String finChecker = "FFCC_" + p1.getFaction() + "_";
                    buttons.add(Button.secondary(finChecker + "cheiranCommanderBlock_" + unitH.getName(), "Cheiran Commander on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.cheiran)));
                }
                if (!activeGame.isFoWMode() && activeGame.playerHasLeaderUnlockedOrAlliance(p2, "cheirancommander") && "ground".equalsIgnoreCase(groundOrSpace)
                    && p2 != activeGame.getActivePlayerObject()) {
                    String finChecker = "FFCC_" + p2.getFaction() + "_";
                    buttons.add(Button.secondary(finChecker + "cheiranCommanderBlock_" + unitH.getName(), "Cheiran Commander on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.cheiran)));
                }
                if (p1.hasAbility("assimilate") && "ground".equalsIgnoreCase(groundOrSpace) && (unitH.getUnitCount(UnitType.Spacedock, p2.getColor()) > 0
                    || unitH.getUnitCount(UnitType.CabalSpacedock, p2.getColor()) > 0 || unitH.getUnitCount(UnitType.Pds, p2.getColor()) > 0)) {
                    String finChecker = "FFCC_" + p1.getFaction() + "_";
                    buttons.add(Button.secondary(finChecker + "assimilate_" + unitH.getName(), "Assimilate Structures on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.L1Z1X)));
                }
                if (p1.hasUnit("letnev_mech") && "ground".equalsIgnoreCase(groundOrSpace) && unitH.getUnitCount(UnitType.Infantry, p1.getColor()) > 0
                    && getNumberOfUnitsOnTheBoard(activeGame, p1, "mech") < 4) {
                    String finChecker = "FFCC_" + p1.getFaction() + "_";
                    buttons.add(Button.secondary(finChecker + "letnevMechRes_" + unitH.getName() + "_mech", "Deploy Dunlain Reaper on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.Letnev)));
                }
                if (p2.hasUnit("letnev_mech") && !activeGame.isFoWMode() && "ground".equalsIgnoreCase(groundOrSpace) && unitH.getUnitCount(UnitType.Infantry, p2.getColor()) > 0
                    && getNumberOfUnitsOnTheBoard(activeGame, p2, "mech") < 4) {
                    String finChecker = "FFCC_" + p2.getFaction() + "_";
                    buttons.add(Button.secondary(finChecker + "letnevMechRes_" + unitH.getName() + "_mech", "Deploy Dunlain Reaper on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.Letnev)));
                }
                if (p2.hasAbility("indoctrination") && !activeGame.isFoWMode() && "ground".equalsIgnoreCase(groundOrSpace)) {
                    String finChecker = "FFCC_" + p2.getFaction() + "_";
                    buttons.add(Button.secondary(finChecker + "initialIndoctrination_" + unitH.getName(), "Indoctrinate on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.Yin)));
                }
                if (p2.hasAbility("assimilate") && !activeGame.isFoWMode() && "ground".equalsIgnoreCase(groundOrSpace) && (unitH.getUnitCount(UnitType.Spacedock, p1.getColor()) > 0
                    || unitH.getUnitCount(UnitType.CabalSpacedock, p1.getColor()) > 0 || unitH.getUnitCount(UnitType.Pds, p1.getColor()) > 0)) {
                    String finChecker = "FFCC_" + p2.getFaction() + "_";
                    buttons.add(Button.secondary(finChecker + "assimilate_" + unitH.getName(), "Assimilate Structures on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.L1Z1X)));
                }
            }
            if ("space".equalsIgnoreCase(nameOfHolder) && "space".equalsIgnoreCase(groundOrSpace)) {
                buttons.add(Button.secondary("combatRoll_" + pos + "_" + unitH.getName(), "Roll Space Combat"));
            } else {
                if (!"space".equalsIgnoreCase(groundOrSpace) && !"space".equalsIgnoreCase(nameOfHolder)) {
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
            } catch (Error ignored) {

            }
            if (t2 != null && t2.getPosition().equalsIgnoreCase(tile.getPosition())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isTileLegendary(Tile tile, Game activeGame) {

        for (UnitHolder planet : tile.getUnitHolders().values()) {
            if (planet instanceof Planet planetHolder) {
                boolean hasAbility = planetHolder.isHasAbility()
                    || planetHolder.getTokenList().stream().anyMatch(token -> token.contains("nanoforge") || token.contains("legendary") || token.contains("consulate"));
                if (hasAbility) {
                    return true;
                }
            }

        }
        return false;
    }

    public static boolean isPlanetLegendaryOrTechSkip(String planetName, Game activeGame) {
        UnitHolder unitHolder = getUnitHolderFromPlanetName(planetName, activeGame);
        Planet planetHolder = (Planet) unitHolder;
        if (planetHolder == null) return false;
        boolean hasAbility = planetHolder.isHasAbility()
            || planetHolder.getTokenList().stream().anyMatch(token -> token.contains("nanoforge") || token.contains("legendary") || token.contains("consulate"));
        if ((Mapper.getPlanet(planetName).getTechSpecialties() != null && Mapper.getPlanet(planetName).getTechSpecialties().size() > 0) || checkForTechSkipAttachments(activeGame, planetName)) {
            return true;
        }
        return hasAbility;
    }

    public static boolean isPlanetTechSkip(String planetName, Game activeGame) {
        UnitHolder unitHolder = getUnitHolderFromPlanetName(planetName, activeGame);
        Planet planetHolder = (Planet) unitHolder;
        if (planetHolder == null) return false;
        if ((Mapper.getPlanet(planetName).getTechSpecialties() != null && Mapper.getPlanet(planetName).getTechSpecialties().size() > 0) || checkForTechSkipAttachments(activeGame, planetName)) {
            return true;
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
                if ("ghost".equalsIgnoreCase(p1.getFaction()) && "creuss".equalsIgnoreCase(planetName)) {
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
        if ("0g".equalsIgnoreCase(tile.getTileID()) || "17".equalsIgnoreCase(tile.getTileID())) {
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

    public static void checkFleetInEveryTile(Player player, Game activeGame, GenericInteractionCreateEvent event) {
        for (Tile tile : activeGame.getTileMap().values()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tile)) {
                checkFleetAndCapacity(player, activeGame, tile, event);
            }
        }
    }

    public static void checkFleetAndCapacity(Player player, Game activeGame, Tile tile, GenericInteractionCreateEvent event) {
        if (tile.getRepresentation() == null || "null".equalsIgnoreCase(tile.getRepresentation())) {
            return;
        }
        if (tile.getRepresentation().toLowerCase().contains("nombox")) {
            return;
        }
        int armadaValue = 0;
        if (player == null) {
            return;
        }
        if (player.hasAbility("armada")) {
            armadaValue = 2;
        }
        if(player.hasTech("dsghotg") && tile == FoWHelper.getPlayerHS(activeGame, player)){
            armadaValue = armadaValue+3;
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
            if (capChecker.getUnitCount(UnitType.CabalSpacedock, player.getColor()) > 0) {
                String colorID = Mapper.getColorID(player.getColor());
                UnitKey csdKey = Mapper.getUnitKey("csd", colorID);
                UnitKey sdKey = Mapper.getUnitKey("sd", colorID);
                capChecker.removeUnit(csdKey, 1);
                capChecker.addUnit(sdKey, 1);
                BotLogger.log("Removing csd in game " + activeGame.getName());
                // new RemoveUnits().unitParsing(event, player.getColor(), tile, "csd "+capChecker.getName(), activeGame);
                // new AddUnits().unitParsing(event, player.getColor(), tile, "sd "+capChecker.getName(), activeGame);
            }
            Map<UnitModel, Integer> unitsByQuantity = CombatHelper.GetAllUnits(capChecker, player);
            for (UnitModel unit : unitsByQuantity.keySet()) {
                if ("space".equalsIgnoreCase(capChecker.getName())) {
                    capacity += unit.getCapacityValue() * unitsByQuantity.get(unit);
                }
                // System.out.println(unit.getBaseType());
                if ("spacedock".equalsIgnoreCase(unit.getBaseType()) && !"space".equalsIgnoreCase(capChecker.getName())) {
                    if (player.ownsUnit("cabal_spacedock")) {
                        fightersIgnored += 6;
                    } else if (player.ownsUnit("cabal_spacedock2")) {
                        fightersIgnored += 12;
                    } else {
                        fightersIgnored += 3;

                    }
                }
            }
            if (capChecker.getUnitCount(UnitType.PlenaryOrbital, player.getColor()) > 0) {
                fightersIgnored += 8;
                fleetCap = fleetCap + 4;
            }
        }
        //System.out.println(fightersIgnored);
        UnitHolder combatOnHolder = tile.getUnitHolders().get("space");
        Map<UnitModel, Integer> unitsByQuantity = CombatHelper.GetAllUnits(combatOnHolder, player);
        for (UnitModel unit : unitsByQuantity.keySet()) {
            if ("fighter".equalsIgnoreCase(unit.getBaseType()) || "infantry".equalsIgnoreCase(unit.getBaseType()) || "mech".equalsIgnoreCase(unit.getBaseType())) {

                if ("fighter".equalsIgnoreCase(unit.getBaseType()) && player.hasFF2Tech()) {
                    numFighter2s += unitsByQuantity.get(unit) - fightersIgnored;
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
        if (capacity > 0 && activeGame.playerHasLeaderUnlockedOrAlliance(player, "vayleriancommander") && tile.getPosition().equals(activeGame.getActiveSystem())
            && player == activeGame.getActivePlayerObject()) {
            capacity = capacity + 2;
        }
        if (numInfNFightersNMechs > capacity) {
            if (numInfNFightersNMechs - numFighter2s > capacity) {
                capacityViolated = true;
            } else {
                numFighter2s = numInfNFightersNMechs - capacity;
                if (player.hasTech("hcf2")) {
                    numFighter2sFleet = numFighter2s;
                } else {
                    numFighter2sFleet = numFighter2s * 2;
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
        String message = player.getRepresentation(true, true);
        if (fleetSupplyViolated) {
            message += " You are violating fleet supply in tile " + tile.getRepresentation() + ". Specifically, the bot believes you have "+fleetCap/2 +" fleet supply, and that you currently are filling "+(numFighter2sFleet + numOfCapitalShips)/2+" of that. " ;
        }
        if (capacityViolated) {
            message += " You are violating carrying capacity in tile " + tile.getRepresentation() + ". Specifically, the bot believes you have "+capacity+" capacity, and you are trying to carry "+ (numInfNFightersNMechs - numFighter2s)+" things";
        }
        System.out.printf("%d %d %d %d%n", fleetCap, numOfCapitalShips, capacity, numInfNFightersNMechs);
        if (capacityViolated || fleetSupplyViolated) {
            Button remove = Button.danger("getDamageButtons_" + tile.getPosition(), "Remove units in " + tile.getRepresentationForButtons(activeGame, player));
            MessageHelper.sendMessageToChannelWithButton(getCorrectChannel(player, activeGame), message, remove);
        }
    }

    public static List<Tile> getAllTilesWithProduction(Game activeGame, Player player, GenericInteractionCreateEvent event) {
        List<Tile> tiles = new ArrayList<>();
        for (Tile tile : activeGame.getTileMap().values()) {
            for (UnitHolder capChecker : tile.getUnitHolders().values()) {
                Map<UnitModel, Integer> unitsByQuantity = CombatHelper.GetAllUnits(capChecker, player);
                for (UnitModel unit : unitsByQuantity.keySet()) {
                    if (unit.getProductionValue() > 0) {
                        if (!tiles.contains(tile)) {
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
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), getIdent(player) + " placed a frontier token in " + tile.getRepresentationForButtons(activeGame, player));
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
            for (String shipOrder : getPlayersShipOrders(player)) {
                if (Helper.getTileWithShipsNTokenPlaceUnitButtons(player, activeGame, "dreadnought", "placeOneNDone_skipbuild", null).size() > 0) {
                    endButtons.add(Button.success(finChecker + "resolveShipOrder_" + shipOrder, "Use " + Mapper.getRelic(shipOrder).getName()));
                }
            }
        }
        if (player.getTechs().contains("bs") && !player.getExhaustedTechs().contains("bs")) {
            endButtons.add(Button.success(finChecker + "exhaustTech_bs", "Exhaust Bio-Stims"));
        }
        if (player.getTechs().contains("absol_bs") && !player.getExhaustedTechs().contains("absol_bs")) {
            endButtons.add(Button.success(finChecker + "exhaustTech_absol_bs", "Exhaust Absol Bio-Stims"));
        }
        if (player.getTechs().contains("miltymod_hm") && !player.getExhaustedTechs().contains("miltymod_hm")) {
            endButtons.add(Button.success(finChecker + "exhaustTech_miltymod_hm", "Exhaust Hyper Metabolism"));
        }
        if (player.hasUnexhaustedLeader("naazagent")) {
            endButtons.add(Button.success(finChecker + "exhaustAgent_naazagent", "Use NRA Agent").withEmoji(Emoji.fromFormatted(Emojis.Naaz)));
        }
        if (player.hasUnexhaustedLeader("cheiranagent") && ButtonHelperAgents.getCheiranAgentTiles(player, activeGame).size() > 0) {
            endButtons.add(Button.success(finChecker + "exhaustAgent_cheiranagent_" + player.getFaction(), "Use Cheiran Agent").withEmoji(Emoji.fromFormatted(Emojis.cheiran)));
        }

        if (player.hasUnexhaustedLeader("freesystemsagent") && player.getReadiedPlanets().size() > 0 && ButtonHelperAgents.getAvailableLegendaryAbilities(activeGame).size() > 0) {
            endButtons.add(Button.success(finChecker + "exhaustAgent_freesystemsagent_" + player.getFaction(), "Use Free Systems Agent").withEmoji(Emoji.fromFormatted(Emojis.freesystems)));
        }
        if (player.hasRelic("absol_tyrantslament") && !player.hasUnit("tyrantslament")) {
            endButtons.add(Button.success("deployTyrant", "Deploy The Tyrant's Lament").withEmoji(Emoji.fromFormatted(Emojis.Absol)));
        }

        if (player.hasUnexhaustedLeader("lizhoagent")) {
            endButtons.add(Button.success(finChecker + "exhaustAgent_lizhoagent", "Use Lizho Agent on Yourself").withEmoji(Emoji.fromFormatted(Emojis.lizho)));
        }

        endButtons.add(Button.danger("deleteButtons", "Delete these buttons"));
        return endButtons;
    }

    public static List<String> getPlayersShipOrders(Player player) {
        List<String> shipOrders = new ArrayList<>();
        for (String relic : player.getRelics()) {
            if (relic.toLowerCase().contains("axisorder") && !player.getExhaustedRelics().contains(relic)) {
                shipOrders.add(relic);
            }
        }
        return shipOrders;
    }

    public static List<String> getPlayersStarCharts(Player player) {
        List<String> shipOrders = new ArrayList<>();
        for (String relic : player.getRelics()) {
            if (relic.toLowerCase().contains("starchart")) {
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
            if (!activeGame.isHomeBrewSCMode() && !activeGame.isFoWMode()) {
                for (Player p2 : activeGame.getRealPlayers()) {
                    for (int sc : player.getSCs()) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(p2.getRepresentation(true, true));
                        sb.append(" You are getting this ping because SC #").append(sc)
                            .append(
                                " has been played and now it is their turn again and you still havent reacted. Please do so, or ping Fin if this is an error. \nTIP: Double check that you paid the command counter to follow\n");
                        if (!activeGame.getFactionsThatReactedToThis("scPlay" + sc).isEmpty()) {
                            sb.append("Message link is: ").append(activeGame.getFactionsThatReactedToThis("scPlay" + sc).replace("666fin", ":")).append("\n");
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
                if ("minister_war".equalsIgnoreCase(law)) {
                    if (activeGame.getLawsInfo().get(law).equalsIgnoreCase(player.getFaction())) {
                        startButtons.add(Button.secondary(finChecker + "ministerOfWar", "Use Minister of War"));
                    }
                }
            }
            if (!activeGame.getJustPlayedComponentAC()) {
                player.setWhetherPlayerShouldBeTenMinReminded(true);
            }
        } else {
            activeGame.setJustPlayedComponentAC(false);
            if (player.getTechs().contains("cm")) {
                Button chaos = Button.secondary("startChaosMapping", "Use Chaos Mapping").withEmoji(Emoji.fromFormatted(Emojis.Saar));
                startButtons.add(chaos);
            }
            if (player.hasUnexhaustedLeader("florzenagent") && ButtonHelperAgents.getAttachments(activeGame, player).size() > 0) {
                startButtons.add(Button.success(finChecker + "exhaustAgent_florzenagent_" + player.getFaction(), "Use Florzen Agent").withEmoji(Emoji.fromFormatted(Emojis.florzen)));
            }
            if (player.hasUnexhaustedLeader("vadenagent")) {
                Button chaos = Button.secondary("exhaustAgent_vadenagent_" + player.getFaction(), "Use Vaden Agent").withEmoji(Emoji.fromFormatted(Emojis.vaden));
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
        if (player.hasTech("pa") && getPsychoTechPlanets(activeGame, player).size() > 1) {
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
            Button nekroButton = Button.secondary("exhaustAgent_nekroagent", "Use Nekro Agent").withEmoji(Emoji.fromFormatted(Emojis.Nekro));
            startButtons.add(nekroButton);
        }
        if (player.hasUnexhaustedLeader("kolleccagent")) {
            Button nekroButton = Button.secondary("exhaustAgent_kolleccagent", "Use Kollecc Agent").withEmoji(Emoji.fromFormatted(Emojis.kollecc));
            startButtons.add(nekroButton);
        }
        if (activeGame.getLatestTransactionMsg() != null && !"".equalsIgnoreCase(activeGame.getLatestTransactionMsg())) {
            activeGame.getMainGameChannel().deleteMessageById(activeGame.getLatestTransactionMsg()).queue();
            activeGame.setLatestTransactionMsg("");
        }
        // if (activeGame.getActionCards().size() > 130 && getButtonsToSwitchWithAllianceMembers(player, activeGame, false).size() > 0) {
        //     startButtons.addAll(getButtonsToSwitchWithAllianceMembers(player, activeGame, false));
        // }

        return startButtons;
    }

    public static void checkForPrePassing(Game activeGame, Player player) {
        activeGame.setCurrentReacts("Pre Pass " + player.getFaction(), "");
        boolean hadAnyUnplayedSCs = false;
        for (Integer SC : player.getSCs()) {
            if (!activeGame.getPlayedSCs().contains(SC)) {
                hadAnyUnplayedSCs = true;
            }
        }
        if (player.getTacticalCC() == 0 && !hadAnyUnplayedSCs && !player.isPassed()) {
            String msg = player.getRepresentation() + " you have the option to pre-pass, which means on your next turn, the bot automatically passes for you. This is entirely optional";
            List<Button> scButtons = new ArrayList<>();
            scButtons.add(Button.success("resolvePreassignment_Pre Pass " + player.getFaction(), "Pass on Next Turn"));
            scButtons.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, scButtons);
        }
    }

    public static int getKyroHeroSC(Game activeGame) {

        if (activeGame.getFactionsThatReactedToThis("kyroHeroSC").isEmpty()) {
            return 1000;
        } else {
            return Integer.parseInt(activeGame.getFactionsThatReactedToThis("kyroHeroSC"));
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
            if (tr != null && !AddCC.hasCC(event, player.getColor(), tr) && (!activeGame.getNaaluAgent() || !isTileHomeSystem(tr))) {
                Button corners = Button.success(finChecker + "ringTile_tl", tr.getRepresentationForButtons(activeGame, player));
                ringButtons.add(corners);
            }
            tr = activeGame.getTileByPosition("tr");
            if (tr != null && !AddCC.hasCC(event, player.getColor(), tr) && (!activeGame.getNaaluAgent() || !isTileHomeSystem(tr))) {
                Button corners = Button.success(finChecker + "ringTile_tr", tr.getRepresentationForButtons(activeGame, player));
                ringButtons.add(corners);
            }
            tr = activeGame.getTileByPosition("bl");
            if (tr != null && !AddCC.hasCC(event, player.getColor(), tr) && (!activeGame.getNaaluAgent() || !isTileHomeSystem(tr))) {
                Button corners = Button.success(finChecker + "ringTile_bl", tr.getRepresentationForButtons(activeGame, player));
                ringButtons.add(corners);
            }
            tr = activeGame.getTileByPosition("br");
            if (tr != null && !AddCC.hasCC(event, player.getColor(), tr) && (!activeGame.getNaaluAgent() || !isTileHomeSystem(tr))) {
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
                        if (tile != null && !tile.getRepresentationForButtons(activeGame, player).contains("Hyperlane") && !AddCC.hasCC(event, player.getColor(), tile)
                            && (!activeGame.getNaaluAgent() || !isTileHomeSystem(tile) || tile.getTileID().equalsIgnoreCase("17"))) {
                            Button corners = Button.success(finChecker + "ringTile_" + pos, tile.getRepresentationForButtons(activeGame, player));
                            ringButtons.add(corners);
                        }
                    }
                    String pos = ringN + "01";
                    Tile tile = activeGame.getTileByPosition(pos);
                    if (tile != null && !tile.getRepresentationForButtons(activeGame, player).contains("Hyperlane") && !AddCC.hasCC(event, player.getColor(), tile)
                        && (!activeGame.getNaaluAgent() || !isTileHomeSystem(tile) || tile.getTileID().equalsIgnoreCase("17"))) {
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
                        if (tile != null && !tile.getRepresentationForButtons(activeGame, player).contains("Hyperlane") && !AddCC.hasCC(event, player.getColor(), tile)
                            && (!activeGame.getNaaluAgent() || !isTileHomeSystem(tile))) {
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
                        if (tile != null && !tile.getRepresentationForButtons(activeGame, player).contains("Hyperlane") && !AddCC.hasCC(event, player.getColor(), tile)
                            && (!activeGame.getNaaluAgent() || !isTileHomeSystem(tile))) {
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

    public static int getNumberOfUnitUpgrades(Player player) {
        int count = 0;
        for (String tech : player.getTechs()) {
            TechnologyModel techM = Mapper.getTech(tech);
            if ("unitupgrade".equalsIgnoreCase(techM.getType().toString())) {
                count++;
            }
        }
        return count;
    }

    public static void exploreDET(Player player, Game activeGame, ButtonInteractionEvent event) {
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());

        if (player.hasAbility("reclamation")) {
            for (UnitHolder uH : tile.getPlanetUnitHolders()) {
                if (uH.getName().equals("mr") && activeGame.getFactionsThatReactedToThis("planetsTakenThisRound").contains(uH.getName())) {
                    new AddUnits().unitParsing(event, player.getColor(), tile, "sd mr, pds mr", activeGame);
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                        player.getRepresentation(true, true) + " Due to the reclamation ability, A pds and SD have been added to Mecatol Rex. This is optional though.");
                }
            }
        }
        if (player.hasUnit("winnu_mech")) {
            for (UnitHolder uH : tile.getPlanetUnitHolders()) {
                if (uH.getUnitCount(UnitType.Mech, player.getColor()) > 0 && activeGame.getFactionsThatReactedToThis("planetsTakenThisRound").contains(uH.getName())) {
                    String planet = uH.getName();
                    Button sdButton = Button.success("winnuStructure_sd_" + planet, "Place A SD on " + Helper.getPlanetRepresentation(planet, activeGame));
                    sdButton = sdButton.withEmoji(Emoji.fromFormatted(Emojis.spacedock));
                    Button pdsButton = Button.success("winnuStructure_pds_" + planet, "Place a PDS on " + Helper.getPlanetRepresentation(planet, activeGame));
                    pdsButton = pdsButton.withEmoji(Emoji.fromFormatted(Emojis.pds));
                    Button tgButton = Button.danger("deleteButtons", "Delete Buttons");
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(sdButton);
                    buttons.add(pdsButton);
                    buttons.add(tgButton);
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, activeGame),
                        player.getRepresentation(true, true) + " Use buttons to place structures equal to the amount of mechs you have", buttons);
                }
            }
        }
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
                String message = player.getRepresentation(true, true) + " Please decide which card to resolve.";

                if (!activeGame.isFoWMode() && event.getChannel() != activeGame.getActionsChannel()) {

                    String pF = player.getFactionEmoji();
                    MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), "Using Voidsailors,  " + pF + " found a " + name1 + " and a " + name2 + " in " + tile.getRepresentation());

                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Found a " + name1 + " and a " + name2 + " in " + tile.getRepresentation());
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);

                String msg2 = "As a reminder of their text, the card abilities read as: \n";
                msg2 = msg2 + name1 + ": " + cardInfo1[4] + "\n";
                msg2 = msg2 + name2 + ": " + cardInfo2[4] + "\n";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2);
            } else if (player.hasUnexhaustedLeader("lanefiragent")) {
                String cardID = activeGame.drawExplore(Constants.FRONTIER);
                String card = Mapper.getExploreRepresentation(cardID);
                String[] cardInfo1 = card.split(";");
                String name1 = cardInfo1[0];
                Button resolveExplore1 = Button.success("lanefirAgentRes_Decline_frontier_" + cardID + "_" + tile.getPosition(), "Choose " + name1);
                Button resolveExplore2 = Button.success("lanefirAgentRes_Accept_frontier_" + tile.getPosition(), "Use Lanefir Agent");
                List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
                String message = player.getRepresentation(true, true) + " You have Lanefir Agent, and thus can decline this explore to draw another one instead.";
                if (!activeGame.isFoWMode() && event.getChannel() != activeGame.getActionsChannel()) {
                    String pF = player.getFactionEmoji();
                    MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), pF + " found a " + name1 + " in " + tile.getRepresentation());
                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Found a " + name1 + " and in " + tile.getRepresentation());
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                String msg2 = "As a reminder of the text, the card reads as: \n";
                msg2 = msg2 + name1 + ": " + cardInfo1[4] + "\n";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2);
            } else {
                new ExpFrontier().expFront(event, tile, activeGame, player);
            }

        }
    }

    public static void sendTradeHolderSomething(Player player, Game activeGame, String buttonID, ButtonInteractionEvent event) {
        String tgOrDebt = buttonID.split("_")[1];
        Player tradeHolder = null;
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2.getSCs().contains(5)) {
                tradeHolder = p2;
                break;
            }
        }
        if (tradeHolder == null) {
            BotLogger.log(event, "`ButtonHelper.sendTradeHolderSomething` tradeHolder was **null**");
            return;
        }
        String msg = player.getRepresentation() + " sent 1 " + tgOrDebt + " to " + tradeHolder.getRepresentation();
        if ("tg".equalsIgnoreCase(tgOrDebt)) {
            checkTransactionLegality(activeGame, player, tradeHolder);
            if (player.getTg() > 0) {
                tradeHolder.setTg(tradeHolder.getTg() + 1);
                player.setTg(player.getTg() - 1);
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation(true, true) + " you had no tg to send, no tg sent.");
                return;
            }
        } else {
            SendDebt.sendDebt(player, tradeHolder, 1);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);

    }

    public static boolean doesPlanetHaveAttachmentTechSkip(Tile tile, String planet) {
        UnitHolder unitHolder = tile.getUnitHolders().get(planet);
        return unitHolder.getTokenList().contains(Mapper.getAttachmentImagePath(Constants.WARFARE)) ||
            unitHolder.getTokenList().contains(Mapper.getAttachmentImagePath(Constants.CYBERNETIC)) ||
            unitHolder.getTokenList().contains(Mapper.getAttachmentImagePath(Constants.BIOTIC)) ||
            unitHolder.getTokenList().contains(Mapper.getAttachmentImagePath(Constants.PROPULSION));
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
                String message = p2.getRepresentation(true, true)
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
                case "afb" -> rollType = CombatRollType.AFB;
                case "bombardment" -> rollType = CombatRollType.bombardment;
                case "spacecannonoffence" -> rollType = CombatRollType.SpaceCannonOffence;
                case "spacecannondefence" -> rollType = CombatRollType.SpaceCannonDefence;
                default -> {
                }
            }
        }
        new CombatRoll().secondHalfOfCombatRoll(player, activeGame, event, activeGame.getTileByPosition(pos), unitHolderName, rollType);
        if (buttonID.contains("bombardment") && activeGame.getLaws().containsKey("conventions")) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "This is a reminder that conventions of war is in play, so bombardment of cultural planets is illegal. Ignore this message if not relevant");
        }
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

        if (player.hasUnexhaustedLeader("ghostagent") && FoWHelper.doesTileHaveWHs(activeGame, activeGame.getActiveSystem())) {
            Button ghostButton = Button.secondary("exhaustAgent_ghostagent", "Use Ghost Agent").withEmoji(Emoji.fromFormatted(Emojis.Ghost));
            buttons.add(ghostButton);
        }
        if (player.hasTech("as") && FoWHelper.isTileAdjacentToAnAnomaly(activeGame, activeGame.getActiveSystem(), player)) {
            Button ghostButton = Button.secondary("declareUse_Aetherstream", "Declare Aetherstream").withEmoji(Emoji.fromFormatted(Emojis.Empyrean));
            buttons.add(ghostButton);
        }

        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "vayleriancommander")) {
            Button ghostButton = Button.secondary("declareUse_Vaylerian Commander", "Use Vaylerian Commander").withEmoji(Emoji.fromFormatted(Emojis.vaylerian));
            buttons.add(ghostButton);
        }
        if (player.hasLeaderUnlocked("vaylerianhero")) {
            Button sardakkH = Button.primary(finChecker + "purgeVaylerianHero", "Use Vaylerian Hero")
                .withEmoji(Emoji.fromFormatted(Emojis.vaylerian));
            buttons.add(sardakkH);
        }
        if (player.ownsUnit("ghost_mech") && getNumberOfUnitsOnTheBoard(activeGame, player, "mech") > 0) {
            Button ghostButton = Button.secondary("creussMechStep1_", "Use Ghost Mech").withEmoji(Emoji.fromFormatted(Emojis.Ghost));
            buttons.add(ghostButton);
        }
        if (player.hasTech("dslihb") && !isTileHomeSystem(activeGame.getTileByPosition(activeGame.getActiveSystem()))) {
            Button ghostButton = Button.secondary("exhaustTech_dslihb", "Exhaust Wraith Engine").withEmoji(Emoji.fromFormatted(Emojis.lizho));
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

        if (!activeGame.getNaaluAgent() && !activeGame.getL1Hero() && !AddCC.hasCC(event, player.getColor(), tile) && activeGame.getFactionsThatReactedToThis("vaylerianHeroActive").isEmpty()) {
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
                String colorID = Mapper.getColorID(player.getColor());
                UnitKey unitID = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), colorID);
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
                if (planet.getUnitCount(inf, colorID) > 0 || planet.getUnitCount(mech, colorID) > 0) {
                    if (player.hasUnexhaustedLeader("dihmohnagent")) {
                            Button dihmohn = Button
                                .success("exhaustAgent_dihmohnagent_" + unitHolder.getName(),
                                    "Use Dihmohn Agent to land an extra Infantry on " + Helper.getPlanetRepresentation(unitHolder.getName(), activeGame))
                                .withEmoji(Emoji.fromFormatted(Emojis.dihmohn));
                            buttons.add(dihmohn);
                        }
                }
                if (planet.getUnitCount(inf, colorID) > 0) {
                    limit = planet.getUnitCount(inf, colorID);
                    for (int x = 1; x < limit + 1; x++) {
                        if (x > 2) {
                            break;
                        }
                        Button validTile2 = Button
                            .secondary(finChecker + "spaceUnits_" + tile.getPosition() + "_" + x + "infantry_" + representation,
                                "Undo Landing of " + x + " Infantry on " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame))
                            .withEmoji(Emoji.fromFormatted(Emojis.infantry));
                        buttons.add(validTile2);
                    }
                }
                UnitHolder spaceUH = tile.getUnitHolders().get("space");
                if (tile.getUnitHolders().get("space").getUnits() != null && tile.getUnitHolders().get("space").getUnitCount(mech, colorID) > 0) {
                    UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit("mech"), colorID);
                    limit = tile.getUnitHolders().get("space").getUnitCount(mech, colorID);
                    int totalUnits = limit;
                    int damagedUnits = 0;
                    if (spaceUH.getUnitDamage() != null && spaceUH.getUnitDamage().get(unitKey) != null) {
                        damagedUnits = spaceUH.getUnitDamage().get(unitKey);
                    }
                    limit = damagedUnits;
                    for (int x = 1; x < limit + 1; x++) {
                        if (x > 2) {
                            break;
                        }
                        String buttonID = finChecker + "landUnits_" + tile.getPosition() + "_" + x + "mechdamaged_" + representation;
                        String buttonText = "Land " + x + " Damaged Mech(s) on " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame);
                        Button validTile2 = Button.primary(buttonID, buttonText).withEmoji(Emoji.fromFormatted(Emojis.mech));
                        buttons.add(validTile2);
                    }
                    limit = totalUnits - damagedUnits;
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
                    UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit("mech"), colorID);
                    limit = planet.getUnitCount(mech, colorID);
                    int totalUnits = limit;
                    int damagedUnits = 0;
                    if (planet.getUnitDamage() != null && planet.getUnitDamage().get(unitKey) != null) {
                        damagedUnits = planet.getUnitDamage().get(unitKey);
                    }
                    limit = damagedUnits;

                    for (int x = 1; x <= limit; x++) {
                        if (x > 2) {
                            break;
                        }
                        String buttonID = finChecker + "spaceUnits_" + tile.getPosition() + "_" + x + "mechdamaged_" + representation;
                        String buttonText = "Undo Landing of " + x + " Damaged Mech(s) on " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame);
                        Button validTile2 = Button.secondary(buttonID, buttonText).withEmoji(Emoji.fromFormatted(Emojis.mech));
                        buttons.add(validTile2);
                    }
                    limit = totalUnits - damagedUnits;
                     for (int x = 1; x <= limit; x++) {
                        if (x > 2) {
                            break;
                        }
                        String buttonID = finChecker + "spaceUnits_" + tile.getPosition() + "_" + x + "mech_" + representation;
                        String buttonText = "Undo Landing of " + x + " Mech(s) on " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame);
                        Button validTile2 = Button.secondary(buttonID, buttonText).withEmoji(Emoji.fromFormatted(Emojis.mech));
                        buttons.add(validTile2);
                    }
                }
            }
        }
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "sardakkcommander")) {
            buttons.addAll(ButtonHelperCommanders.getSardakkCommanderButtons(activeGame, player, event));
        }
        if (player.getPromissoryNotes().containsKey("ragh")) {
            buttons.addAll(ButtonHelperFactionSpecific.getRaghsCallButtons(player, activeGame, tile));
        }
        Button rift = Button.success(finChecker + "getRiftButtons_" + tile.getPosition(), "Rift some units").withEmoji(Emoji.fromFormatted(Emojis.GravityRift));
        buttons.add(rift);
        if (player.hasAbility("combat_drones") && FoWHelper.playerHasFightersInSystem(player, tile)) {
            Button combatDrones = Button.primary(finChecker + "combatDrones", "Use Combat Drones Ability").withEmoji(Emoji.fromFormatted(Emojis.mirveda));
            buttons.add(combatDrones);
        }
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "mirvedacommander")) {
            Button combatDrones = Button.primary(finChecker + "offerMirvedaCommander", "Use Mirveda Commander").withEmoji(Emoji.fromFormatted(Emojis.mirveda));
            buttons.add(combatDrones);
        }
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "ghostcommander")) {
            Button ghostC = Button.primary(finChecker + "placeGhostCommanderFF_" + tile.getPosition(), "Place fighter with Ghost Commander")
                .withEmoji(Emoji.fromFormatted(Emojis.Ghost));
            buttons.add(ghostC);
        }
        if (tile.getPlanetUnitHolders().size() > 0 && activeGame.playerHasLeaderUnlockedOrAlliance(player, "khraskcommander")) {
            Button ghostC = Button.primary(finChecker + "placeKhraskCommanderInf_" + tile.getPosition(), "Place infantry with Khrask Commander")
                .withEmoji(Emoji.fromFormatted(Emojis.khrask));
            buttons.add(ghostC);
        }
        if (player.hasUnexhaustedLeader("nokaragent") && FoWHelper.playerHasShipsInSystem(player, tile)) {
            Button chaos = Button.secondary("exhaustAgent_nokaragent_" + player.getFaction(), "Use Nokar Agent To Place A Destroyer").withEmoji(Emoji.fromFormatted(Emojis.nokar));
            buttons.add(chaos);
        }
        if (player.hasUnexhaustedLeader("tnelisagent") && FoWHelper.playerHasShipsInSystem(player, tile) && FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, activeGame)) {
            Button chaos = Button.secondary("exhaustAgent_tnelisagent_" + player.getFaction(), "Use Tnelis Agent").withEmoji(Emoji.fromFormatted(Emojis.tnelis));
            buttons.add(chaos);
        }
        if (player.hasUnexhaustedLeader("zelianagent") && tile.getUnitHolders().get("space").getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
            Button chaos = Button.secondary("exhaustAgent_zelianagent_" + player.getFaction(), "Use Zelian Agent Yourself").withEmoji(Emoji.fromFormatted(Emojis.zelian));
            buttons.add(chaos);
        }
        if (player.hasLeaderUnlocked("muaathero") && !"18".equalsIgnoreCase(tile.getTileID()) && getTilesOfPlayersSpecificUnits(activeGame, player, UnitType.Warsun).contains(tile)) {
            Button muaatH = Button.primary(finChecker + "novaSeed_" + tile.getPosition(), "Nova Seed This Tile")
                .withEmoji(Emoji.fromFormatted(Emojis.Muaat));
            buttons.add(muaatH);
        }
        if (player.hasLeaderUnlocked("zelianhero") && !"18".equalsIgnoreCase(tile.getTileID()) && getTilesOfUnitsWithBombard(player, activeGame).contains(tile)) {
            Button zelianH = Button.primary(finChecker + "celestialImpact_" + tile.getPosition(), "Celestial Impact This Tile")
                .withEmoji(Emoji.fromFormatted(Emojis.zelian));
            buttons.add(zelianH);
        }
        if (player.hasLeaderUnlocked("sardakkhero") && tile.getPlanetUnitHolders().size() > 0) {
            Button sardakkH = Button.primary(finChecker + "purgeSardakkHero", "Use Sardakk Hero")
                .withEmoji(Emoji.fromFormatted(Emojis.Sardakk));
            buttons.add(sardakkH);
        }
        if (player.hasLeaderUnlocked("rohdhnahero")) {
            Button sardakkH = Button.primary(finChecker + "purgeRohdhnaHero", "Use Rohdhna Hero")
                .withEmoji(Emoji.fromFormatted(Emojis.rohdhna));
            buttons.add(sardakkH);
        }
        if (tile.getUnitHolders().size() > 1 && getTilesOfUnitsWithBombard(player, activeGame).contains(tile)) {
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
        Button buildButton = Button.success(finChecker + "tacticalActionBuild_" + activeGame.getActiveSystem(),
            "Build in this system (" + Helper.getProductionValue(player, activeGame, tile, false) + " PRODUCTION Value)");
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

    public static void resolveTransitDiodesStep1(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.toLowerCase().contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            buttons.add(Button.success("transitDiodes_" + planet, Helper.getPlanetRepresentation(planet, activeGame)));
        }
        buttons.add(Button.danger("deleteButtons", "Done resolving transit diodes"));
        MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, activeGame), player.getRepresentation() + " use buttons to choose the planet you want to move troops to", buttons);
    }

    public static void resolveTransitDiodesStep2(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = getButtonsForMovingGroundForcesToAPlanet(activeGame, buttonID.split("_")[1], player);
        deleteTheOneButton(event);
        MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, activeGame),
            player.getRepresentation() + " use buttons to choose the troops you want to move to " + Helper.getPlanetRepresentation(buttonID.split("_")[1], activeGame), buttons);
    }

    public static List<Button> getButtonsForMovingGroundForcesToAPlanet(Game activeGame, String planetName, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : activeGame.getTileMap().values()) {
            for (UnitHolder uH : tile.getUnitHolders().values()) {
                if (uH.getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
                    if (uH instanceof Planet) {
                        buttons.add(Button.success("mercerMove_" + planetName + "_" + tile.getPosition() + "_" + uH.getName() + "_infantry",
                            "Move Infantry from " + Helper.getPlanetRepresentation(uH.getName(), activeGame) + " to " + Helper.getPlanetRepresentation(planetName, activeGame)));
                    } else {
                        buttons.add(Button.success("mercerMove_" + planetName + "_" + tile.getPosition() + "_" + uH.getName() + "_infantry",
                            "Move Infantry from space of " + tile.getRepresentation() + " to " + Helper.getPlanetRepresentation(planetName, activeGame)));
                    }
                }
                if (uH.getUnitCount(UnitType.Mech, player.getColor()) > 0) {
                    if (uH instanceof Planet) {
                        buttons.add(Button.success("mercerMove_" + planetName + "_" + tile.getPosition() + "_" + uH.getName() + "_mech",
                            "Move Mech from " + Helper.getPlanetRepresentation(uH.getName(), activeGame) + " to " + Helper.getPlanetRepresentation(planetName, activeGame)));
                    } else {
                        buttons.add(Button.success("mercerMove_" + planetName + "_" + tile.getPosition() + "_" + uH.getName() + "_mech",
                            "Move Mech from space of " + tile.getRepresentation() + " to " + Helper.getPlanetRepresentation(planetName, activeGame)));
                    }
                }
                if (player.hasUnit("titans_pds") || player.hasTech("ht2")) {
                    if (uH.getUnitCount(UnitType.Pds, player.getColor()) > 0) {
                        if (uH instanceof Planet) {
                            buttons.add(Button.success("mercerMove_" + planetName + "_" + tile.getPosition() + "_" + uH.getName() + "_pds",
                                "Move PDS from " + Helper.getPlanetRepresentation(uH.getName(), activeGame) + " to " + Helper.getPlanetRepresentation(planetName, activeGame)));
                        } else {
                            buttons.add(Button.success("mercerMove_" + planetName + "_" + tile.getPosition() + "_" + uH.getName() + "_pds",
                                "Move PDS from space of " + tile.getRepresentation() + " to " + Helper.getPlanetRepresentation(planetName, activeGame)));
                        }
                    }
                }
            }
        }
        buttons.add(Button.danger("deleteButtons", "Done moving to this planet"));
        return buttons;
    }

    public static void offerSetAutoPassOnSaboButtons(Game activeGame, Player player2) {
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
        buttons.add(Button.danger("setAutoPassMedian_" + x, "Turn off (if already on)"));
        if (player2 == null) {
            for (Player player : activeGame.getRealPlayers()) {
                String message = player.getRepresentation(true, true)
                    + " you can choose to automatically pass on sabo's after a random amount of time if you don't have a sabo/instinct training/watcher mechs. How it works is you secretly set a median time (in hours) here, and then from now on when an AC is played, the bot will randomly react for you, 50% of the time being above that amount of time and 50% below. It's random so people cant derive much information from it. You are free to decline, noone will ever know either way, but if necessary you can change your time later with /player stats";
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
            }
        } else {
            Player player = player2;
            String message = player.getRepresentation(true, true)
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
        Map<String, Integer> displacedUnits = activeGame.getCurrentMovedUnitsFrom1System();
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
            if (buttons.size() < 26 || !button.getId().contains("_2")) {
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
            case "tyrantslament" -> "tyrantslament";
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
            case "lady" -> "lady";
            case "plenaryorbital" -> "plenaryorbital";
            default -> "";
        };
    }

    public static List<Button> getButtonsForRiftingUnitsInSystem(Player player, Game activeGame, Tile tile) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();

        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = unitHolder.getUnits();

            if (unitHolder instanceof Planet) {
            } else {
                for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                    UnitKey key = unitEntry.getKey();
                    if (!player.unitBelongsToPlayer(key)) continue;

                    UnitModel unitModel = player.getUnitFromUnitKey(key);
                    if (unitModel == null) continue;

                    UnitType unitType = key.getUnitType();
                    if ((!activeGame.playerHasLeaderUnlockedOrAlliance(player, "sardakkcommander") && (unitType == UnitType.Infantry || unitType == UnitType.Mech))
                        || (!player.hasFF2Tech() && unitType == UnitType.Fighter)) {
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
                        validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);
                    }
                    totalUnits = totalUnits - damagedUnits;
                    for (int x = 1; x < totalUnits + 1 && x <= 2; x++) {
                        Button validTile2 = Button.danger(finChecker + "riftUnit_" + tile.getPosition() + "_" + x + asyncID, "Rift " + x + " " + unitModel.getBaseType());
                        validTile2 = validTile2.withEmoji(emoji);
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
            Map<UnitKey, Integer> units = unitHolder.getUnits();

            if (unitHolder instanceof Planet planet) {
                for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                    if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                    UnitKey unitKey = unitEntry.getKey();
                    representation = representation.replace(" ", "").toLowerCase().replace("'", "").replace("-", "");
                    if ((unitKey.getUnitType() == UnitType.Infantry || unitKey.getUnitType() == UnitType.Mech)) {
                        String unitName = getUnitName(unitKey.asyncID());
                        int totalUnits = unitEntry.getValue();
                        int damagedUnits = 0;
                        EmojiUnion emoji = Emoji.fromFormatted(unitKey.unitEmoji());
                        if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                            damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                        }
                        for (int x = 1; x < damagedUnits + 1 && x <= 2; x++) {
                            String buttonID = finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_" + x + unitName + "damaged_" + representation;
                            String buttonText = moveOrRemove + " " + x + " damaged " + unitKey.unitName()+" from " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame);
                            Button validTile2 = Button.danger(buttonID, buttonText).withEmoji(emoji);
                            buttons.add(validTile2);
                        }
                        totalUnits = totalUnits - damagedUnits;
                        for (int x = 1; x < totalUnits + 1 && x <= 2; x++) {
                            String buttonID = finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_" + x + unitName + "_" + representation;
                            String buttonText = moveOrRemove + " " + x + " " + unitKey.unitName() +" from " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame);
                            Button validTile2 = Button.danger(buttonID, buttonText).withEmoji(emoji);
                            buttons.add(validTile2);
                        }
                    }
                }
            } else {
                for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                    if (!(player.unitBelongsToPlayer(unitEntry.getKey()))) continue;

                    UnitKey unitKey = unitEntry.getKey();
                    String unitName = getUnitName(unitKey.asyncID());
                    // System.out.println(unitKey.asyncID());
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
                        validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);
                    }
                    totalUnits = totalUnits - damagedUnits;
                    for (int x = 1; x < totalUnits + 1 && x <= 2; x++) {
                        String buttonID = finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_" + x + unitName;
                        String buttonText = moveOrRemove + " " + x + " " + unitKey.unitName();
                        Button validTile2 = Button.danger(buttonID, buttonText);
                        validTile2 = validTile2.withEmoji(emoji);
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
            if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "tneliscommander") && activeGame.getFactionsThatReactedToThis("tnelisCommanderTracker").isEmpty()) {
                buttons.add(Button.primary("declareUse_Tnelis Commander_" + tile.getPosition(), "Use Tnelis Commander").withEmoji(Emoji.fromFormatted(Emojis.tnelis)));
            }
        }
        buttons.add(doAll);
        buttons.add(concludeMove);
        Map<String, Integer> displacedUnits = activeGame.getCurrentMovedUnitsFrom1System();
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

    public static List<Tile> getAllWormholeTiles(Game activeGame) {
        List<Tile> wormholes = new ArrayList<>();
        for (Tile tile : activeGame.getTileMap().values()) {
            if (FoWHelper.doesTileHaveWHs(activeGame, tile.getPosition())) {
                wormholes.add(tile);
            }
        }
        return wormholes;
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
            Map<UnitKey, Integer> units = unitHolder.getUnits();
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
                        validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);

                        if (unitModel.getSustainDamage()) {
                            buttonID = finChecker + "assignDamage_" + tile.getPosition() + "_" + x + unitName + "_" + representation;
                            buttonText = "Sustain " + x + " " + unitModel.getBaseType() + " from " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame);
                            Button validTile3 = Button.secondary(buttonID, buttonText);
                            validTile2 = validTile2.withEmoji(emoji);
                            buttons.add(validTile3);
                        }
                    }
                    for (int x = 1; x < damagedUnits + 1 && x < 2; x++) {
                        String buttonID = finChecker + "assignHits_" + tile.getPosition() + "_" + x + unitName + "_" + representation + "damaged";
                        String buttonText = "Remove " + x + " damaged " + unitModel.getBaseType() + " from " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame);
                        Button validTile2 = Button.danger(buttonID, buttonText);
                        validTile2 = validTile2.withEmoji(emoji);
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
                        validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);
                    }
                    for (int x = 1; x < totalUnits + 1 && x < 3; x++) {
                        Button validTile2 = Button.danger(finChecker + "assignHits_" + tile.getPosition() + "_" + x + unitName, "Remove " + x + " " + unitModel.getBaseType());
                        validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);
                    }

                    if ((("mech".equalsIgnoreCase(unitName) && !activeGame.getLaws().containsKey("articles_war") && player.getUnitsOwned().contains("nomad_mech"))
                        || "dreadnought".equalsIgnoreCase(unitName)
                        || (player != activeGame.getActivePlayerObject() && !"fighter".equalsIgnoreCase(unitName) && !"mech".equalsIgnoreCase(unitName) && !"infantry".equalsIgnoreCase(unitName)
                            && activeGame.playerHasLeaderUnlockedOrAlliance(player, "mortheuscommander"))
                        || ("warsun".equalsIgnoreCase(unitName) && !activeGame.getLaws().containsKey("schematics")) || "lady".equalsIgnoreCase(unitName) || "flagship".equalsIgnoreCase(unitName)
                        || ("mech".equalsIgnoreCase(unitName) && doesPlayerHaveFSHere("nekro_flagship", player, tile))
                        || ("cruiser".equalsIgnoreCase(unitName) && player.hasTech("se2")) || ("carrier".equalsIgnoreCase(unitName) && player.hasTech("ac2"))) && totalUnits > 0) {
                        Button validTile2 = Button
                            .secondary(finChecker + "assignDamage_" + tile.getPosition() + "_" + 1 + unitName, "Sustain " + 1 + " " + unitModel.getBaseType());
                        validTile2 = validTile2.withEmoji(emoji);
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

    public static List<Button> getUserSetupButtons(Game activeGame) {
        List<Button> buttons = new ArrayList<>();
        for (Player player : activeGame.getPlayers().values()) {
            String userId = player.getUserID();
            buttons.add(Button.success("setupStep1_" + userId, player.getUserName()));
        }
        return buttons;
    }

    public static void setUpFrankenFactions(Game activeGame, GenericInteractionCreateEvent event) {
        List<Player> players = new ArrayList<>(activeGame.getPlayers().values());
        int x = 1;
        for (Player player : players) {
            if (x < 9) {
                switch (x) {
                    case 1 -> new Setup().secondHalfOfPlayerSetup(player, activeGame, "black", "franken1", "201", event, false);
                    case 2 -> new Setup().secondHalfOfPlayerSetup(player, activeGame, "green", "franken2", "202", event, false);
                    case 3 -> new Setup().secondHalfOfPlayerSetup(player, activeGame, "purple", "franken3", "203", event, false);
                    case 4 -> new Setup().secondHalfOfPlayerSetup(player, activeGame, "orange", "franken4", "204", event, false);
                    case 5 -> new Setup().secondHalfOfPlayerSetup(player, activeGame, "pink", "franken5", "205", event, false);
                    case 6 -> new Setup().secondHalfOfPlayerSetup(player, activeGame, "yellow", "franken6", "206", event, false);
                    case 7 -> new Setup().secondHalfOfPlayerSetup(player, activeGame, "red", "franken7", "207", event, false);
                    case 8 -> new Setup().secondHalfOfPlayerSetup(player, activeGame, "blue", "franken8", "208", event, false);
                    default -> {

                    }
                }
            }
            x++;
        }
    }

    public static List<Button> getFactionSetupButtons(Game activeGame, String buttonID) {
        String userId = buttonID.split("_")[1];
        List<Button> buttons = new ArrayList<>();
        List<FactionModel> factionsOnMap = Mapper.getFactions().stream()
            .filter(f -> activeGame.getTile(f.getHomeSystem()) != null)
            .filter(f -> activeGame.getPlayerFromColorOrFaction(f.getAlias()) == null)
            .toList();
        List<FactionModel> allFactions = Mapper.getFactions().stream()
            .filter(f -> activeGame.isDiscordantStarsMode() ? f.getSource().isDs() : f.getSource().isPok())
            .filter(f -> activeGame.getPlayerFromColorOrFaction(f.getAlias()) == null)
            .sorted((f1, f2) -> factionsOnMap.contains(f1) ? (factionsOnMap.contains(f2) ? 0 : -1) : (factionsOnMap.contains(f2) ? 1 : 0))
            .toList();

        Set<String> factionsComplete = new HashSet<>();
        for (FactionModel faction : allFactions) {
            String factionId = faction.getAlias();
            if (activeGame.getPlayerFromColorOrFaction(factionId) == null) {
                String name = faction.getFactionName();
                if (factionId.contains("keleres")) {
                    factionId = "keleres";
                    name = "The Council Keleres";
                }
                if (factionsComplete.contains(factionId)) continue;
                Emoji factionEmoji = Emoji.fromFormatted(Emojis.getFactionIconFromDiscord(factionId));
                buttons.add(Button.success("setupStep2_" + userId + "_" + factionId, name).withEmoji(factionEmoji));
            }

            factionsComplete.add(factionId);
        }
        return buttons;
    }
    public static boolean isNumeric(String strNum) {
        if (strNum == null) {
            return false;
        }
        try {
            int d = Integer.parseInt(strNum);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    public static void rematch(Game activeGame, GenericInteractionCreateEvent event){

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "**Game: `" + activeGame.getName() + "` has ended!**");
        activeGame.setHasEnded(true);
        activeGame.setEndedDate(new Date().getTime());
        GameSaveLoadManager.saveMap(activeGame, event);
        String gameEndText = GameEnd.getGameEndText(activeGame, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), gameEndText);
        activeGame.setAutoPing(false);
        activeGame.setAutoPingSpacer(0);
         String name = activeGame.getName();
         MapGenerator.saveImage(activeGame, DisplayType.all, event)
         .thenAccept(fileUpload -> {
           StringBuilder message = new StringBuilder();
           for (String playerID : activeGame.getRealPlayerIDs()) { //GET ALL PLAYER PINGS
             Member member = event.getGuild().getMemberById(playerID);
             if (member != null) message.append(member.getAsMention());
           }
           message.append("\nPlease provide a summary of the game below:");
        if (!activeGame.isFoWMode() && !AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("the-pbd-chronicles", true).isEmpty()) {
                TextChannel pbdChroniclesChannel = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("the-pbd-chronicles", true).get(0);
                String channelMention = pbdChroniclesChannel == null ? "#the-pbd-chronicles" : pbdChroniclesChannel.getAsMention();
                if (pbdChroniclesChannel == null) {
                  BotLogger.log(event, "`#the-pbd-chronicles` channel not found - `/game end` cannot post summary");
                  return;
                }
                if (!activeGame.isFoWMode()) {
                  // INFORM PLAYERS
                  pbdChroniclesChannel.sendMessage(gameEndText).queue(m -> { //POST INITIAL MESSAGE
                    m.editMessageAttachments(fileUpload).queue(); //ADD MAP FILE TO MESSAGE
                    m.createThreadChannel(name).queueAfter(2, TimeUnit.SECONDS, t -> t.sendMessage(message.toString()).queue(null,
                        (error) -> BotLogger.log("Failure to create Game End thread for **" + activeGame.getName() + "** in PBD Chronicles:\n> " + error.getMessage()))); //CREATE THREAD AND POST FOLLOW UP
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Game summary has been posted in the " + channelMention + " channel: " + m.getJumpUrl());
                  });
                }
              }
         });
       
        int charValue = name.charAt(name.length()-1);
        String next = String.valueOf( (char) (charValue + 1));
        String newName= "";
        if(isNumeric(next)){
            newName = name + "b";
        }else{
            newName = name.substring(0, name.length()-1)+next;
        }

        Guild guild = activeGame.getGuild();
        Role gameRole = null;
        if (guild != null) {
            for (Role role : guild.getRoles()) {
                if (activeGame.getName().equals(role.getName().toLowerCase())) {
                    gameRole = role;
                }
            }
        }
        if(gameRole != null){
            gameRole.getManager().setName(newName).queue();
        }else{
            gameRole = guild.createRole()
            .setName(newName)
            .setMentionable(true)
            .complete();
            for (Player player : activeGame.getRealPlayers()) {
                Member member = guild.getMemberById(player.getUserID());
                if(member != null){
                    guild.addRoleToMember(member, gameRole).complete();
                }
            }
        }
        TextChannel tableTalkChannel = activeGame.getTableTalkChannel();
        TextChannel actionsChannel = activeGame.getMainGameChannel();
        
        // CLOSE THREADS IN CHANNELS
        if (tableTalkChannel != null) {
            for (ThreadChannel threadChannel : tableTalkChannel.getThreadChannels()) {
                threadChannel.getManager().setArchived(true).queue();
            }
            String newTableName = tableTalkChannel.getName().replace(name, newName);
            activeGame.getTableTalkChannel().getManager().setName(newTableName).queue();
        }
        if (actionsChannel != null) {
            for (ThreadChannel threadChannel : actionsChannel.getThreadChannels()) {
                threadChannel.getManager().setArchived(true).queue();
            }
            activeGame.getActionsChannel().getManager().setName(newName+"-actions").queue();
        }
        Member gameOwner = guild.getMemberById(activeGame.getOwnerID());
        Game newGame = GameCreate.createNewGame(event, newName, gameOwner);

        //ADD PLAYERS
        for (Player player : activeGame.getPlayers().values()) {
            newGame.addPlayer(player.getUserID(), player.getUserName());
        }
        newGame.setPlayerCountForMap(newGame.getPlayers().values().size());
        newGame.setStrategyCardsPerPlayer(newGame.getSCList().size() / newGame.getPlayers().values().size());

        //CREATE CHANNELS
       
        newGame.setCustomName(activeGame.getCustomName()+" Rematch");
        

       
        newGame.setTableTalkChannelID(tableTalkChannel.getId());

        // CREATE ACTIONS CHANNEL
        String newBotThreadName = newName + Constants.BOT_CHANNEL_SUFFIX;
        newGame.setMainGameChannelID(actionsChannel.getId());

        // CREATE BOT/MAP THREAD
        ThreadChannel botThread = actionsChannel.createThreadChannel(newBotThreadName)
            .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_WEEK)
            .complete();
        newGame.setBotMapUpdatesThreadID(botThread.getId());

        // INTRODUCTION TO TABLETALK CHANNEL
        String tabletalkGetStartedMessage = gameRole.getAsMention() + " - table talk channel\n" +
            "This channel is for typical over the table converstion, as you would over the table while playing the game in real life.\n" +
            "If this group has agreed to whispers (secret conversations), you can create private threads off this channel.\n" +
            "Typical things that go here are: general conversation, deal proposals, memes - everything that isn't either an actual action in the game or a bot command\n";
        MessageHelper.sendMessageToChannelAndPin(tableTalkChannel, tabletalkGetStartedMessage);

        // INTRODUCTION TO ACTIONS CHANNEL
        String actionsGetStartedMessage = gameRole.getAsMention() + " - actions channel\n" +
            "This channel is for taking actions in the game, primarily using buttons or the odd slash command.\n" +
            "Please keep this channel clear of any chat with other players. Ideally this channel is a nice clean ledger of what has physically happened in the game.\n";
        MessageHelper.sendMessageToChannelAndPin(actionsChannel, actionsGetStartedMessage);
        ButtonHelper.offerPlayerSetupButtons(actionsChannel);

        // INTRODUCTION TO BOT-MAP THREAD
        String botGetStartedMessage = gameRole.getAsMention() + " - bot/map channel\n" +
            "This channel is for bot slash commands and updating the map, to help keep the actions channel clean.\n" +
            "### __Use the following commands to get started:__\n" +
            "> `/map add_tile_list {mapString}`, replacing {mapString} with a TTPG map string\n" +
            "> `/player setup` to set player faction and color\n" +
            "> `/game setup` to set player count and additional options\n" +
            "> `/game set_order` to set the starting speaker order\n" +
            "\n" +
            "### __Other helpful commands:__\n" +
            "> `/game replace` to replace a player in the game with a new one\n";
        MessageHelper.sendMessageToChannelAndPin(botThread, botGetStartedMessage);
        MessageHelper.sendMessageToChannelAndPin(botThread, "Website Live Map: https://ti4.westaddisonheavyindustries.com/game/" + newName);


        GameSaveLoadManager.saveMap(newGame, event);
        if(event instanceof ButtonInteractionEvent event2){
            event2.getMessage().delete().queue();
        }
    }

    public static List<Button> getColorSetupButtons(Game activeGame, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        String userId = buttonID.split("_")[1];
        String factionId = buttonID.split("_")[2];
        List<String> allColors = Mapper.getColors();
        for (String color : allColors) {
            if (activeGame.getPlayerFromColorOrFaction(color) == null) {
                Emoji colorEmoji = Emoji.fromFormatted(Emojis.getColorEmoji(color));
                buttons.add(Button.success("setupStep3_" + userId + "_" + factionId + "_" + color, color).withEmoji(colorEmoji));
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
        int maxBefore = -1;
        long numberOfHomes = Mapper.getFactions().stream()
            .filter(f -> activeGame.getTile(f.getHomeSystem()) != null)
            .filter(f -> activeGame.getPlayerFromColorOrFaction(f.getAlias()) == null)
            .count();
        if (numberOfHomes <= 0) numberOfHomes = 22;

        for (int x = 0; x < buttons.size(); x++) {
            if (x <= maxBefore + numberOfHomes) {
                newButtons.add(buttons.get(x));
            }
        }
        newButtons.add(Button.secondary("setupStep2_" + userId + "_" + (maxBefore + numberOfHomes) + "!", "Get more factions"));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please tell the bot the desired faction", newButtons);
    }

    public static void resolveSetupStep2(Player player, Game activeGame, GenericInteractionCreateEvent event, String buttonID) {
        String userId = buttonID.split("_")[1];
        String factionId = buttonID.split("_")[2];
        if (event instanceof ButtonInteractionEvent) {
            ((ComponentInteraction) event).getMessage().delete().queue();
        }
        if (factionId.contains("!")) {
            List<Button> buttons = getFactionSetupButtons(activeGame, buttonID);
            List<Button> newButtons = new ArrayList<>();
            int maxBefore = Integer.parseInt(factionId.replace("!", ""));
            for (int x = 0; x < buttons.size(); x++) {
                if (x > maxBefore && x < (maxBefore + 23)) {
                    newButtons.add(buttons.get(x));
                }
            }
            newButtons.add(Button.secondary("setupStep2_" + userId + "_" + (maxBefore + 22) + "!", "Get more factions"));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Please tell the bot the desired faction", newButtons);
            return;
        }
        if ("keleres".equalsIgnoreCase(factionId)) {
            List<Button> newButtons = new ArrayList<>();
            newButtons.add(Button.success("setupStep2_" + userId + "_keleresa", "Keleres Argent").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("argent"))));
            newButtons.add(Button.success("setupStep2_" + userId + "_keleresm", "Keleres Mentak").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mentak"))));
            newButtons.add(Button.success("setupStep2_" + userId + "_keleresx", "Keleres Xxcha").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("xxcha"))));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Please tell the bot which flavor of keleres you are", newButtons);
            return;
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Setting up as faction: " + Mapper.getFaction(factionId).getFactionName());
        offerColorSetupButtons(activeGame, event, buttonID, userId, factionId);

    }

    private static void offerColorSetupButtons(Game activeGame, GenericInteractionCreateEvent event, String buttonID, String userId, String factionId) {
        List<Button> buttons = getColorSetupButtons(activeGame, buttonID);
        List<Button> newButtons = new ArrayList<>();
        int maxBefore = -1;
        for (int x = 0; x < buttons.size(); x++) {
            if (x < maxBefore + 23) {
                newButtons.add(buttons.get(x));
            }
        }
        newButtons.add(Button.secondary("setupStep3_" + userId + "_" + factionId + "_" + (maxBefore + 22) + "!", "Get more colors"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Please tell the bot the desired player color", newButtons);
    }

    public static List<Button> getSpeakerSetupButtons(Game activeGame, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        String userId = buttonID.split("_")[1];
        String factionId = buttonID.split("_")[2];
        String color = buttonID.split("_")[3];
        String pos = buttonID.split("_")[4];
        buttons.add(Button.success("setupStep5_" + userId + "_" + factionId + "_" + color + "_" + pos + "_yes", "Yes, setting up speaker"));
        buttons.add(Button.success("setupStep5_" + userId + "_" + factionId + "_" + color + "_" + pos + "_no", "No"));
        return buttons;
    }

    public static void resolveSetupStep3(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String userId = buttonID.split("_")[1];
        String factionId = buttonID.split("_")[2];
        String color = buttonID.split("_")[3];
        event.getMessage().delete().queue();
        if (color.contains("!")) {
            List<Button> buttons = getColorSetupButtons(activeGame, buttonID);
            List<Button> newButtons = new ArrayList<>();
            int maxBefore = Integer.parseInt(color.replace("!", ""));
            for (int x = 0; x < buttons.size(); x++) {
                if (x > maxBefore && x < (maxBefore + 23)) {
                    newButtons.add(buttons.get(x));
                }
            }
            newButtons.add(Button.secondary("setupStep3_" + userId + "_" + factionId + "_" + (maxBefore + 22) + "!", "Get more color"));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please tell the bot the desired color", newButtons);
            return;
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Setting up as color: " + color);

        List<Button> buttons = new ArrayList<>();

        for (Tile tile : activeGame.getTileMap().values()) {
            FactionModel fModel = Mapper.getFaction(factionId);
            if (fModel.getHomeSystem().equalsIgnoreCase(tile.getTileID())) {
                resolveSetupStep4And5(activeGame, event, "setupStep4_" + userId + "_" + factionId + "_" + color + "_" + tile.getPosition());
                return;
            }
            if (isTileHomeSystem(tile)) {

                String rep = tile.getRepresentation();
                if (rep == null || rep.isEmpty()) {
                    rep = tile.getTileID() + "(" + tile.getPosition() + ")";
                }
                buttons.add(Button.success("setupStep4_" + userId + "_" + factionId + "_" + color + "_" + tile.getPosition(), rep));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please tell the bot the home system location", buttons);

    }

    public static void resolveSetupStep4And5(Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String userId = buttonID.split("_")[1];
        String factionId = buttonID.split("_")[2];
        String color = buttonID.split("_")[3];
        String pos = buttonID.split("_")[4];
        Player speaker = null;
        Player player = activeGame.getPlayer(userId);
        if (activeGame.getPlayer(activeGame.getSpeaker()) != null) {
            speaker = activeGame.getPlayers().get(activeGame.getSpeaker());
        }
        if (buttonID.split("_").length == 6 || speaker != null) {
            if (speaker != null) {
                new Setup().secondHalfOfPlayerSetup(player, activeGame, color, factionId, pos, event, false);
            } else {
                new Setup().secondHalfOfPlayerSetup(player, activeGame, color, factionId, pos, event, "yes".equalsIgnoreCase(buttonID.split("_")[5]));
            }
        } else {
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please tell the bot if the player is the speaker", getSpeakerSetupButtons(activeGame, buttonID));
        }
        event.getMessage().delete().queue();
    }

    /**
     * Check all colors in the active game and print out errors and possible solutions if any have too low of a luminance variation
     */
    public static void resolveSetupColorChecker(Game activeGame) {
        @Data
        class Collision {
            Player p1, p2;
            double contrast;

            Collision(Player p1, Player p2, double contrast) {
                this.p1 = p1;
                this.p2 = p2;
                this.contrast = contrast;
            }
        }

        List<Player> players = activeGame.getRealPlayers();
        List<Collision> issues = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            Player p1 = players.get(i);
            for (int j = i + 1; j < players.size(); j++) {
                Player p2 = players.get(j);

                double contrast = colorContrast(p1.getColor(), p2.getColor());
                if (contrast < 4.5) {
                    Collision e1 = new Collision(p1, p2, contrast);
                    issues.add(e1);
                }
            }
        }

        if (issues.isEmpty()) return;

        StringBuilder sb = new StringBuilder("### The following pairs of players have colors with a low contrast value:\n");
        for (Collision issue : issues) {
            sb.append("> ").append(issue.p1.getRepresentation(false, false)).append(" & ").append(issue.p2.getRepresentation(false, false)).append("  -> ");
            sb.append("Ratio = 1:").append(issue.contrast);
            if (issue.contrast < 2) {
                sb.append("(very bad!)");
            }
            sb.append("\n");
        }

        MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), sb.toString());
    }

    public static double colorContrast(String color1, String color2) {
        return Math.max(colorPrimaryContrast(color1, color2), colorSecondaryContrast(color1, color2));
    }

    private static double colorPrimaryContrast(String color1, String color2) {
        Color c1 = ColorModel.primaryColor(color1);
        Color c2 = ColorModel.primaryColor(color2);
        if (c1 == null || c2 == null) return 1;

        double l1 = relativeLuminance(c1);
        double l2 = relativeLuminance(c2);
        double contrast = contrastRatio(l1, l2);
        return contrast;
    }

    private static double colorSecondaryContrast(String color1, String color2) {
        Color c1 = ColorModel.secondaryColor(color1);
        Color c2 = ColorModel.secondaryColor(color2);

        // if there is no secondary color (not a split color), compare on the primary color
        double l1 = c1 == null ? relativeLuminance(ColorModel.primaryColor(color1)) : relativeLuminance(c1);
        double l2 = c2 == null ? relativeLuminance(ColorModel.primaryColor(color2)) : relativeLuminance(c2);
        double contrast = contrastRatio(l1, l2);
        return contrast;
    }

    /**
     * For the sRGB colorspace, the relative luminance of a color is defined as
     * <p>
     * L = 0.2126 * R + 0.7152 * G + 0.0722 * B
     * <p>
     * where R, G and B are defined as:
     * <p>
     * if XsRGB <= 0.03928 then X = XsRGB/12.92 else X = ((XsRGB+0.055)/1.055) ^ 2.4
     * <p>
     * -
     * <p>
     * and RsRGB, GsRGB, and BsRGB are defined as:
     * <p>
     * XsRGB = X8bit/255
     */
    private static double relativeLuminance(Color color) {
        if (color == null) return 0;
        double RsRGB = ((double) color.getRed()) / 255.0;
        double GsRGB = ((double) color.getGreen()) / 255.0;
        double BsRGB = ((double) color.getBlue()) / 255.0;

        double r = color.getRed() <= 10 ? RsRGB / 12.92 : Math.pow((RsRGB + 0.055) / 1.055, 2.4);
        double g = color.getGreen() <= 10 ? GsRGB / 12.92 : Math.pow((GsRGB + 0.055) / 1.055, 2.4);
        double b = color.getBlue() <= 10 ? BsRGB / 12.92 : Math.pow((BsRGB + 0.055) / 1.055, 2.4);

        return (0.2126 * r) + (0.7152 * g) + (0.0722 * b);
    }

    /**
     * To calculate the contrast ratio, the relative luminance of the lighter colour (L1) is divided through the relative luminance of the darker colour (L2):
     * <p>
     * (L1 + 0.05) / (L2 + 0.05)
     * <p>
     * This results in a value ranging from 1:1 (no contrast at all) to 21:1 (the highest possible contrast).
     * 
     * @param L1 The lighter color (higher luminance)
     * @param L2 the darker color (lower luminance)
     * @return contrast ratio (1:x)
     */
    private static double contrastRatio(double L1, double L2) {
        if (L1 < L2) return contrastRatio(L2, L1);
        return (L1 + 0.05) / (L2 + 0.05);
    }

    public static void resolveStellar(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        StellarConverter.secondHalfOfStellar(activeGame, buttonID.split("_")[1], event);
        event.getMessage().delete().queue();
    }

    public static String getUnitHolderRep(UnitHolder unitHolder, Tile tile, Game activeGame) {
        String name = unitHolder.getName();
        if ("space".equalsIgnoreCase(name)) {
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
        if (player.hasTech("iihq") && player.hasPlanet("mr")) {
            Tile mr = activeGame.getTileFromPlanet("mr");
            tilesWithProduction.add(mr);
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
        List<Tile> tilesWithBombard = getTilesOfUnitsWithBombard(player, activeGame);
        Set<String> adjacentTiles = FoWHelper.getAdjacentTilesAndNotThisTile(activeGame, tilesWithBombard.get(0).getPosition(), player, false);
        for (Tile tile : tilesWithBombard) {
            adjacentTiles.addAll(FoWHelper.getAdjacentTilesAndNotThisTile(activeGame, tile.getPosition(), player, false));
        }
        for (String pos : adjacentTiles) {
            Tile tile = activeGame.getTileByPosition(pos);
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder instanceof Planet planet) {
                    if (!player.getPlanetsAllianceMode().contains(planet.getName()) && !isPlanetLegendaryOrHome(unitHolder.getName(), activeGame, false, player)
                        && !planet.getName().toLowerCase().contains("rex")) {
                        buttons.add(Button.success(finChecker + "stellarConvert_" + planet.getName(), "Stellar Convert " + Helper.getPlanetRepresentation(planet.getName(), activeGame)));
                    }
                }
            }
        }
        return buttons;
    }

    public static int getNumberOfGravRiftsPlayerIsIn(Player player, Game activeGame) {
        return (int) activeGame.getTileMap().values().stream().filter(tile -> tile.isGravityRift(activeGame) && tile.containsPlayersUnits(player)).count();
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
            Map<UnitKey, Integer> units = unitHolder.getUnits();

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
                    validTile3 = validTile3.withEmoji(emoji);
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
                BotLogger.log("`ButtonHelper.tileHasPDS2Cover` Game: " + activeGame.getName() + " Tile: " + tilePos + " has a null adjacent tile: `" + adjTilePos + "` within: `" + adjTiles + "`");
                continue;
            }
            for (UnitHolder unitHolder : adjTile.getUnitHolders().values()) {
                for (Map.Entry<UnitKey, Integer> unitEntry : unitHolder.getUnits().entrySet()) {
                    if (unitEntry.getValue() == 0) {
                        continue;
                    }

                    UnitKey unitKey = unitEntry.getKey();
                    Player owningPlayer = activeGame.getPlayerByColorID(unitKey.getColorID()).orElse(null);
                    if (owningPlayer == null || playersWithPds2.contains(owningPlayer)) {
                        continue;
                    }

                    UnitModel model = owningPlayer.getUnitFromUnitKey(unitKey);
                    if (model != null && (model.getDeepSpaceCannon() || (tilePos.equalsIgnoreCase(adjTilePos) && model.getSpaceCannonDieCount() > 0))) {
                        if (owningPlayer == player) {
                            if (FoWHelper.otherPlayersHaveShipsInSystem(player, activeGame.getTileByPosition(tilePos), activeGame)) {
                                playersWithPds2.add(owningPlayer);
                            }
                        } else {
                            playersWithPds2.add(owningPlayer);
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
        Player privatePlayer = player;

        //INFORM FIRST PLAYER IS UP FOR ACTION
        if (player != null) {
            msgExtra += "# " + player.getRepresentation() + " is up for an action";
            activeGame.updateActivePlayer(player);
            if (activeGame.isFoWMode()) {
                FoWHelper.pingAllPlayersWithFullStats(activeGame, event, player, "started turn");
            }
            ButtonHelperFactionSpecific.resolveMilitarySupportCheck(player, activeGame);
            ButtonHelperFactionSpecific.resolveKolleccAbilities(player, activeGame);

            activeGame.setCurrentPhase("action");
        }

        msg = "";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        if (isFowPrivateGame) {
            if (privatePlayer == null) {
                BotLogger.log(event, "`ButtonHelper.startMyTurn` privatePlayer is null");
                return;
            }
            msgExtra = "# " + privatePlayer.getRepresentation(true, true) + " UP NEXT";
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
                if (privatePlayer == null) {
                    BotLogger.log(event, "`ButtonHelper.startMyTurn` privatePlayer is null");
                    return;
                }
                if (privatePlayer.getStasisInfantry() > 0) {
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(privatePlayer, activeGame),
                        "Use buttons to revive infantry. You have " + privatePlayer.getStasisInfantry() + " infantry left to revive.", getPlaceStatusInfButtons(activeGame, privatePlayer));
                }
            }
        }
    }

    public static void resolveImperialArbiter(ButtonInteractionEvent event, Game activeGame, Player player) {
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), getIdent(player) + " decided to use the Imperial Arbiter Law to swap SCs with someone");
        activeGame.removeLaw("arbiter");
        List<Button> buttons = ButtonHelperFactionSpecific.getSwapSCButtons(activeGame, "imperialarbiter", player);
        MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, activeGame), player.getRepresentation(true, true) + " choose who you want to swap SCs with",
            buttons);
        event.getMessage().delete().queue();
    }

    //playerHasUnitsInSystem(player, tile);
    public static void startActionPhase(GenericInteractionCreateEvent event, Game activeGame) {
        boolean isFowPrivateGame = FoWHelper.isPrivateGame(activeGame, event);
        String msg;
        activeGame.setCurrentPhase("action");
        String msgExtra = "";
        boolean allPicked = true;
        Player privatePlayer = null;
        Collection<Player> activePlayers = activeGame.getPlayers().values().stream()
            .filter(Player::isRealPlayer)
            .toList();
        Player nextPlayer = null;
        int lowestSC = 100;
        for (Player p2 : activeGame.getRealPlayers()) {
            ButtonHelperActionCards.checkForAssigningCoup(activeGame, p2);
            if (activeGame.getFactionsThatReactedToThis("Play Naalu PN") != null && activeGame.getFactionsThatReactedToThis("Play Naalu PN").contains(p2.getFaction())) {
                if (!p2.getPromissoryNotesInPlayArea().contains("gift") && p2.getPromissoryNotes().containsKey("gift")) {
                    resolvePNPlay("gift", p2, activeGame, event);
                }
            }
        }
        msgExtra += "\nAll players picked SC";
        for (Player player_ : activePlayers) {
            int playersLowestSC = player_.getLowestSC();
            String scNumberIfNaaluInPlay = activeGame.getSCNumberIfNaaluInPlay(player_, Integer.toString(playersLowestSC));
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
        MessageHelper.sendMessageToChannel(getCorrectChannel(nextPlayer, activeGame), msg);
        if (isFowPrivateGame) {
            msgExtra = "Start phase command run";
            String fail = "User for next faction not found. Report to ADMIN";
            String success = "The next player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(privatePlayer, activeGame, event, msgExtra, fail, success);
            if (privatePlayer == null) return;
            msgExtra = "# " + privatePlayer.getRepresentation(true, true) + " UP NEXT";
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
        for (Player p2 : activeGame.getRealPlayers()) {
            List<Button> buttons = new ArrayList<>();
            if (p2.hasTechReady("qdn") && p2.getTg() > 2 && p2.getStrategicCC() > 0) {
                buttons.add(Button.success("startQDN", "Use Quantum Datahub Node"));
                buttons.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(p2, activeGame), p2.getRepresentation(true, true) + " you have the opportunity to use QDN",
                    buttons);
            }
            buttons = new ArrayList<>();
            if (activeGame.getLaws().containsKey("arbiter") && activeGame.getLawsInfo().get("arbiter").equalsIgnoreCase(p2.getFaction())) {
                buttons.add(Button.success("startArbiter", "Use Imperial Arbiter"));
                buttons.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(p2, activeGame),
                    p2.getRepresentation(true, true) + " you have the opportunity to use Imperial Arbiter", buttons);
            }
        }
    }

    public static void startStatusHomework(GenericInteractionCreateEvent event, Game activeGame) {
        activeGame.setCurrentPhase("statusHomework");

        // first do cleanup if necessary
        int playersWithSCs = 0;
        for (Player player : activeGame.getRealPlayers()) {
            if (player.getSCs() != null && player.getSCs().size() > 0 && !player.getSCs().contains(0)) {
                playersWithSCs++;
            }
        }

        if (playersWithSCs > 0) {
            new Cleanup().runStatusCleanup(activeGame);
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), activeGame.getPing() + "Status Cleanup Run!");
            if (!activeGame.isFoWMode()) {
                DisplayType displayType = DisplayType.map;
                MapGenerator.saveImage(activeGame, displayType, event)
                    .thenAccept(fileUpload -> MessageHelper.sendFileUploadToChannel(activeGame.getActionsChannel(), fileUpload));
            }
        }

        for (Player player : activeGame.getRealPlayers()) {
            Leader playerLeader = player.getLeader("naaluhero").orElse(null);

            if (player.hasLeader("naaluhero") && player.getLeaderByID("naaluhero").isPresent()
                && playerLeader != null && !playerLeader.isLocked()) {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Button.success("naaluHeroInitiation", "Play Naalu Hero"));
                buttons.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                    player.getRepresentation()
                        + " Reminder this is the window to do Naalu Hero. You can use the buttons to start the process",
                    buttons);
            }
            if (player.getRelics() != null && player.hasRelic("mawofworlds") && activeGame.isCustodiansScored()) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    player.getRepresentation()
                        + " Reminder this is the window to do Maw of Worlds");
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                    player.getRepresentation()
                        + " You can use these buttons to resolve Maw Of Worlds",
                    getMawButtons());
            }
            if (player.getRelics() != null && player.hasRelic("emphidia")) {
                for (String pl : player.getPlanets()) {
                    Tile tile = activeGame.getTile(AliasHandler.resolveTile(pl));
                    if (tile == null) {
                        continue;
                    }
                    UnitHolder unitHolder = tile.getUnitHolders().get(pl);
                    if (unitHolder != null && unitHolder.getTokenList() != null
                        && unitHolder.getTokenList().contains("attachment_tombofemphidia.png")) {
                        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                            player.getRepresentation()
                                + "Reminder this is the window to purge Crown of Emphidia if you want to.");
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                            player.getRepresentation()
                                + " You can use these buttons to resolve Crown of Emphidia",
                            getCrownButtons());
                    }
                }
            }
            if (player.getActionCards() != null && player.getActionCards().containsKey("summit")
                && !activeGame.isCustodiansScored()) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    player.getRepresentation()
                        + "Reminder this is the window to do summit");
            }
            if (player.getActionCards() != null && (player.getActionCards().containsKey("investments")
                && !activeGame.isCustodiansScored())) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    player.getRepresentation()
                        + "Reminder this is the window to do manipulate investments.");
            }

            if (player.getActionCards() != null && player.getActionCards().containsKey("stability")) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    player.getRepresentation()
                        + "Reminder this is the window to play political stability.");
            }

            if (player.getActionCards() != null && player.getActionCards().containsKey("abs") && activeGame.isCustodiansScored()) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    player.getRepresentation()
                        + "Reminder this is the window to play ancient burial sites.");
            }

            for (String pn : player.getPromissoryNotes().keySet()) {
                if (!player.ownsPromissoryNote("ce") && "ce".equalsIgnoreCase(pn)) {
                    String cyberMessage = "# " + player.getRepresentation(true, true)
                        + " reminder to use cybernetic enhancements!";
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                        cyberMessage);
                }
            }
        }
        String message2 = "Resolve status homework using the buttons. \n ";
        activeGame.setACDrawStatusInfo("");
        Button draw1AC = Button.success("drawStatusACs", "Draw Status Phase ACs").withEmoji(Emoji.fromFormatted(Emojis.ActionCard));
        Button getCCs = Button.success("redistributeCCButtons", "Redistribute, Gain, & Confirm CCs").withEmoji(Emoji.fromFormatted("🔺"));
        Button yssarilPolicy = null;
        for (Player player : activeGame.getRealPlayers()) {
            if (ButtonHelper.isPlayerElected(activeGame, player, "minister_policy") && player.hasAbility("scheming")) {
                yssarilPolicy = Button.secondary("FFCC_" + player.getFaction() + "_yssarilMinisterOfPolicy", "Draw Minister of Policy AC").withEmoji(Emoji.fromFormatted(Emojis.Yssaril));
            }
        }
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
            if (yssarilPolicy != null) {
                buttons.add(yssarilPolicy);
            }
        }
        // if (activeGame.getActionCards().size() > 130 && activeGame.getPlayerFromColorOrFaction("hacan") != null
        //     && getButtonsToSwitchWithAllianceMembers(activeGame.getPlayerFromColorOrFaction("hacan"), activeGame, false).size() > 0) {
        //     buttons.add(Button.secondary("getSwapButtons_", "Swap"));
        // }
        MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), message2, buttons);
    }

    public static void startStrategyPhase(GenericInteractionCreateEvent event, Game activeGame) {
        if (activeGame.getHasHadAStatusPhase()) {
            int round = activeGame.getRound();
            round++;
            activeGame.setRound(round);
        }
        if (activeGame.getRealPlayers().size() == 6) {
            activeGame.setStrategyCardsPerPlayer(1);
        }
        ButtonHelperFactionSpecific.checkForNaaluPN(activeGame);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Started Round " + activeGame.getRound());
        for (Player p2 : activeGame.getRealPlayers()) {
            if (activeGame.getFactionsThatReactedToThis("Summit") != null && activeGame.getFactionsThatReactedToThis("Summit").contains(p2.getFaction()) && p2.getActionCards().containsKey("summit")) {
                PlayAC.playAC(event, activeGame, p2, "summit", activeGame.getMainGameChannel(), event.getGuild());
            }
            if (activeGame.getFactionsThatReactedToThis("Investments") != null && activeGame.getFactionsThatReactedToThis("Investments").contains(p2.getFaction())
                && p2.getActionCards().containsKey("investments")) {
                PlayAC.playAC(event, activeGame, p2, "investments", activeGame.getMainGameChannel(), event.getGuild());
            }
            if(p2.hasLeader("zealotshero") && p2.getLeader("zealotshero").get().isActive()){
                if(!activeGame.getFactionsThatReactedToThis("zealotsHeroTechs").isEmpty()){
                    String list = activeGame.getFactionsThatReactedToThis("zealotsHeroTechs");
                    List<Button> buttons = new ArrayList<>();
                    for(String techID : list.split("-")){
                        buttons.add(Button.success("purgeTech_"+techID,"Purge "+Mapper.getTech(techID).getName()));
                    }
                    String msg = p2.getRepresentation(true, true) +" due to zealots hero, you have to purge 2 techs. Use buttons to purge ";
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), msg+ "the first tech", buttons);
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), msg+"the second tech", buttons);
                    p2.removeLeader("zealotshero");
                    activeGame.setCurrentReacts("zealotsHeroTechs", "");
                }
            }
        }
        if (!activeGame.getFactionsThatReactedToThis("agendaConstitution").isEmpty()) {
            activeGame.setCurrentReacts("agendaConstitution", "");
            for (Player p2 : activeGame.getRealPlayers()) {
                for (String planet : p2.getPlanets()) {
                    if (planet.contains("custodia") || planet.contains("ghoti")) {
                        continue;
                    }
                    if (isTileHomeSystem(activeGame.getTileFromPlanet(planet)) && isPlanetLegendaryOrHome(planet, activeGame, true, p2)) {
                        p2.exhaustPlanet(planet);
                    }
                }
            }
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), "# Exhausted all home systems due to that one agenda");
        }
        if (!activeGame.getFactionsThatReactedToThis("agendaArmsReduction").isEmpty()) {
            activeGame.setCurrentReacts("agendaArmsReduction", "");
            for (Player p2 : activeGame.getRealPlayers()) {
                for (String planet : p2.getPlanets()) {
                    if (planet.contains("custodia") || planet.contains("ghoti")) {
                        continue;
                    }
                    if (isPlanetTechSkip(planet, activeGame)) {
                        p2.exhaustPlanet(planet);
                    }
                }
            }
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), "# Exhausted all tech skip planets due to that one agenda");
        }
        if (!activeGame.getFactionsThatReactedToThis("agendaRepGov").isEmpty()) {
            for (Player p2 : activeGame.getRealPlayers()) {
                if (activeGame.getFactionsThatReactedToThis("agendaRepGov").contains(p2.getFaction())) {
                    for (String planet : p2.getPlanets()) {
                        if (planet.contains("custodia") || planet.contains("ghoti")) {
                            continue;
                        }
                        Planet p = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
                        if (p != null && ("cultural".equalsIgnoreCase(p.getOriginalPlanetType()) || p.getTokenList().contains("attachment_titanspn.png"))) {
                            p2.exhaustPlanet(planet);
                        }
                    }
                }
            }
            activeGame.setCurrentReacts("agendaRepGov", "");
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), "# Exhausted all cultural planets of those who voted against on that one agenda");
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
        String message = speaker.getRepresentation(true, true)
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
                    player2.getRepresentation(true, true)
                        + "Reminder this is the window to do summit");
            }
            for (String pn : player2.getPromissoryNotes().keySet()) {
                if (!player2.ownsPromissoryNote("scepter") && "scepter".equalsIgnoreCase(pn)) {
                    PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pn);
                    Player owner = activeGame.getPNOwner(pn);
                    Button transact = Button.success("resolvePNPlay_" + pn, "Play " + promissoryNote.getName()).withEmoji(Emoji.fromFormatted(owner.getFactionEmoji()));
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(transact);
                    buttons.add(Button.danger("deleteButtons", "Decline"));
                    String cyberMessage = player2.getRepresentation(true, true)
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
        
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), player.getRepresentation() + " purged Maw Of Worlds.");
        Button getTech = Button.success("acquireAFreeTech", "Get a tech");
        MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), player.getRepresentation() + " Use the button to get a tech", getTech);
        event.getMessage().delete().queue();
    }

    public static void resolveCrownOfE(Game activeGame, Player player, ButtonInteractionEvent event) {
        if (player.hasRelic("absol_emphidia")) {
            player.removeRelic("absol_emphidia");
            player.removeExhaustedRelic("absol_emphidia");
        }
        if (player.hasRelic("emphidia")) {
            player.removeRelic("emphidia");
            player.removeExhaustedRelic("emphidia");
        }
        Integer poIndex = activeGame.addCustomPO("Crown of Emphidia", 1);
        activeGame.scorePublicObjective(player.getUserID(), poIndex);
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), player.getRepresentation() + " scored Crown of Emphidia");
        event.getMessage().delete().queue();
        Helper.checkEndGame(activeGame, player);
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
                if (faction != null && Mapper.isValidFaction(faction)) {
                    Button button;
                    if (!activeGame.isFoWMode()) {
                        String label = " ";
                        if (!canTheseTwoTransact(activeGame, p, player)) {
                            label = "(Not Neighbors)";
                        }
                        button = Button.secondary(finChecker + "transactWith_" + faction, label);

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
        if (p1.getDebtTokenCount(p2.getColor()) > 0) {
            Button transact = Button.primary(finChecker + "transact_ClearDebt_" + p2.getFaction(), "Clear Debt");
            stuffToTransButtons.add(transact);
        }
        stuffToTransButtons.add(Button.danger(finChecker + "transact_SendDebt_" + p2.getFaction(), "Send Debt"));
        if (p1.getCommodities() > 0 && !p1.hasAbility("military_industrial_complex")) {
            Button transact = Button.success(finChecker + "transact_Comms_" + p2.getFaction(), "Commodities");
            stuffToTransButtons.add(transact);
        }

        if ((p1.getCommodities() > 0 || p2.getCommodities() > 0) && !p1.hasAbility("military_industrial_complex") && !p1.getAllianceMembers().contains(p2.getFaction())) {
            Button transact = Button.secondary(finChecker + "send_WashComms_" + p2.getFaction() + "_0", "Wash Both Players Comms");
            stuffToTransButtons.add(transact);
        }
        if (getPlayersShipOrders(p1).size() > 0) {
            Button transact = Button.secondary(finChecker + "transact_shipOrders_" + p2.getFaction(), "Axis Orders");
            stuffToTransButtons.add(transact);
        }
        if (getNumberOfStarCharts(p1) > 0) {
            Button transact = Button.secondary(finChecker + "transact_starCharts_" + p2.getFaction(), "Star Charts");
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
        if (getTradePlanetsWithAlliancePartnerButtons(p1, p2, activeGame).size() > 0) {
            Button transact = Button.success(finChecker + "transact_AlliancePlanets_" + p2.getFaction(), "Alliance Planets")
                .withEmoji(Emoji.fromFormatted(Emojis.getFactionIconFromDiscord(p2.getFaction())));
            stuffToTransButtons.add(transact);
        }
        if (activeGame.getCurrentPhase().toLowerCase().contains("agenda") && !"no".equalsIgnoreCase(playerHasDMZPlanet(p1, activeGame))) {
            Button transact = Button.secondary(finChecker + "resolveDMZTrade_" + playerHasDMZPlanet(p1, activeGame) + "_" + p2.getFaction(),
                "Trade " + Mapper.getPlanet(playerHasDMZPlanet(p1, activeGame)).getName() + " (DMZ)");
            stuffToTransButtons.add(transact);
        }
        return stuffToTransButtons;
    }

    public static void resolveSetAFKTime(Game activeGameOG, Player player, String buttonID, ButtonInteractionEvent event) {
        String time = buttonID.split("_")[1];
        player.addHourThatIsAFK(time);
        deleteTheOneButton(event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), getIdent(player) + " Set hour " + time + " as a time that you are afk");
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
        List<Button> buttons = getSetAFKButtons(activeGame);
        player.setHoursThatPlayerIsAFK("");
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation(true, true)
            + " your afk times (if any) have been reset. Use buttons to select the hours (note they are in UTC) in which you're afk. If you select 8 for example, you will be set as AFK from 8:00 UTC to 8:59 UTC in every game you are in.",
            buttons);
    }

    public static void offerPersonalPingOptions(Game activeGame, Player player) {
        List<Button> buttons = getPersonalAutoPingButtons(activeGame);
        player.setHoursThatPlayerIsAFK("");
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation(true, true)
            + " select the number of hours you would like the bot to wait before it pings you that it is your turn. This will apply to all your games. 0 is off. Your current interval is "
            + player.getPersonalPingInterval(),
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
            case "ClearDebt" -> {
                String message = "Click the amount of debt you would like to clear";
                for (int x = 1; x < p1.getDebtTokenCount(p2.getColor()) + 1; x++) {
                    Button transact = Button.success(finChecker + "send_ClearDebt_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "SendDebt" -> {
                String message = "Click the amount of debt you would like to send";
                for (int x = 1; x < 6; x++) {
                    Button transact = Button.success(finChecker + "send_SendDebt_" + p2.getFaction() + "_" + x, "" + x);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "shipOrders" -> {
                String message = "Click the axis order you would like to send";
                for (String shipOrder : getPlayersShipOrders(p1)) {
                    Button transact = Button.success(finChecker + "send_shipOrders_" + p2.getFaction() + "_" + shipOrder, "" + Mapper.getRelic(shipOrder).getName());
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, stuffToTransButtons);
            }
            case "starCharts" -> {
                String message = "Click the star chart you would like to send";
                for (String shipOrder : getPlayersStarCharts(p1)) {
                    Button transact = Button.success(finChecker + "send_starCharts_" + p2.getFaction() + "_" + shipOrder, "" + Mapper.getRelic(shipOrder).getName());
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
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, getTradePlanetsWithAlliancePartnerButtons(p1, p2, activeGame));
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
                String message = p1.getRepresentation(true, true) + " Click the PN you would like to send.";

                for (String pnShortHand : p1.getPromissoryNotes().keySet()) {
                    if (p1.getPromissoryNotesInPlayArea().contains(pnShortHand)) {
                        continue;
                    }
                    PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pnShortHand);
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
                MessageHelper.sendMessageToChannel(p1.getCardsInfoThread(), "Reminder that, unlike other things, you can only send a person 1 PN in a transaction.");
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
        String ident = p1.getRepresentation();
        String ident2 = p2.getRepresentation();
        switch (thingToTrans) {
            case "TGs" -> {
                int tgAmount = Integer.parseInt(amountToTrans);
                p1.setTg(p1.getTg() - tgAmount);
                p2.setTg(p2.getTg() + tgAmount);
                if (p2.getLeaderIDs().contains("hacancommander") && !p2.hasLeaderUnlocked("hacancommander")) {
                    commanderUnlockCheck(p2, activeGame, "hacan", event);
                }
                message2 = ident + " sent " + tgAmount + " TGs to " + ident2;
                if(!p2.hasAbility("binding_debts") && p2.getDebtTokenCount(p1.getColor()) > 0){
                    int amount = Math.min(tgAmount, p2.getDebtTokenCount(p1.getColor()));
                    ClearDebt.clearDebt(p2, p1, amount);
                    message2 = message2 +"\n"+ident2+ " cleared " + amount + " debt tokens owned by " + ident;
                }
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
                ButtonHelperFactionSpecific.resolveDarkPactCheck(activeGame, p1, p2, tgAmount);
                ButtonHelperAbilities.pillageCheck(p1, activeGame);
                ButtonHelperAbilities.pillageCheck(p2, activeGame);
                message2 = ident + " sent " + tgAmount + " Commodities to " + ident2;
            }
            case "WashComms" -> {
                int tgAmount = Integer.parseInt(amountToTrans);
                int oldP1Tg = p1.getTg();
                int oldP2tg = p2.getTg();
                int oldP1Comms = p1.getCommodities();
                int newP1Comms = 0;
                int totalWashPowerP1 = p1.getCommodities() + p1.getTg();
                int totalWashPowerP2 = p2.getCommodities() + p2.getTg();
                if (oldP1Comms > totalWashPowerP2) {
                    newP1Comms = oldP1Comms - totalWashPowerP2;

                }
                int oldP2Comms = p2.getCommodities();
                int newP2Comms = 0;
                if (oldP2Comms > totalWashPowerP1) {
                    newP2Comms = oldP2Comms - totalWashPowerP1;
                }
                p1.setCommodities(newP1Comms);
                p2.setCommodities(newP2Comms);
                p1.setTg(p1.getTg() + (oldP1Comms - newP1Comms));
                p2.setTg(p2.getTg() + (oldP2Comms - newP2Comms));
                if (p2.getLeaderIDs().contains("hacancommander") && !p2.hasLeaderUnlocked("hacancommander")) {
                    commanderUnlockCheck(p2, activeGame, "hacan", event);
                }
                ButtonHelperFactionSpecific.resolveDarkPactCheck(activeGame, p1, p2, oldP1Comms);
                ButtonHelperFactionSpecific.resolveDarkPactCheck(activeGame, p2, p1, oldP2Comms);
                ButtonHelperAbilities.pillageCheck(p1, activeGame);
                ButtonHelperAbilities.pillageCheck(p2, activeGame);
                String id1 = ButtonHelper.getIdentOrColor(p1, activeGame);
                String id2 = ButtonHelper.getIdentOrColor(p2, activeGame);
                message2 = ident + " washed their " + (oldP1Comms - newP1Comms) + " Commodities with " + ident2 + "  (" + id1 + " tg went from (" + oldP1Tg + "->" + p1.getTg() + "))\n" + id2
                    + " washed their " + (oldP2Comms - newP2Comms) + " Commodities with " + id1 + " (" + id2 + " tg went from (" + oldP2tg + "->" + p2.getTg() + "))";
            }
            case "shipOrders" -> {
                message2 = ident + " sent " + Mapper.getRelic(amountToTrans).getName() + " to " + ident2;
                p1.removeRelic(amountToTrans);
                p2.addRelic(amountToTrans);
            }
            case "SendDebt" -> {
                message2 = ident + " sent " + amountToTrans + " debt tokens to " + ident2;
                p2.addDebtTokens(p1.getColor(), Integer.parseInt(amountToTrans));
                ButtonHelper.fullCommanderUnlockCheck(p2, activeGame, "vaden", event);
            }
            case "ClearDebt" -> {
                message2 = ident + " cleared " + amountToTrans + " debt tokens of " + ident2;
                p1.removeDebtTokens(p2.getColor(), Integer.parseInt(amountToTrans));
            }
            case "starCharts" -> {
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
                if (!p1.hasAbility("arbiters") && !p2.hasAbility("arbiters")) {
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
                if (id.contains("dspnveld")) {
                    resolvePNPlay(id, p2, activeGame, event);
                }
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
                        if (activeGame.getPNOwner(id).hasLeaderUnlocked("bentorcommander")) {
                            p2.setCommoditiesTotal(p2.getCommodities() + 1);
                        }
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

    public static List<Button> getPersonalAutoPingButtons(Game activeGame) {
        List<Button> buttons = new ArrayList<>();
        for (int x = 0; x < 13; x++) {
            buttons.add(Button.secondary("setPersonalAutoPingInterval_" + x, "" + x));
        }
        buttons.add(Button.secondary("setPersonalAutoPingInterval_" + 24, "" + 24));
        buttons.add(Button.secondary("setPersonalAutoPingInterval_" + 48, "" + 48));
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
                if ("lgf".equals(tech) && !p1.getPlanets().contains("mr")) {
                    continue;
                }
                Button tButton = Button.danger(finChecker + prefix + "tech_" + tech, "Exhaust " + techName).withEmoji(Emoji.fromFormatted(techEmoji));
                compButtons.add(tButton);
            }
        }
        if (getNumberOfStarCharts(p1) > 1) {
            Button tButton = Button.danger(finChecker + prefix + "doStarCharts_", "Purge 2 Starcharts ");
            compButtons.add(tButton);
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
                        led = "bentoragent";
                        if (p1.hasExternalAccessToLeader(led)) {
                            Button lButton = Button.secondary(finChecker + prefix + "leader_" + led, "Use " + leaderName + " as Bentor agent").withEmoji(Emoji.fromFormatted(factionEmoji));
                            compButtons.add(lButton);
                        }
                        led = "kolumeagent";
                        if (p1.hasExternalAccessToLeader(led)) {
                            Button lButton = Button.secondary(finChecker + prefix + "leader_" + led, "Use " + leaderName + " as Kolume agent").withEmoji(Emoji.fromFormatted(factionEmoji));
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
                        if(ButtonHelperFactionSpecific.doesAnyoneElseHaveJr(activeGame, p1)){
                            Button jrButton = Button.secondary(finChecker + "yssarilAgentAsJr", "Use " + leaderName + " The Relic/Agent JR")
                                .withEmoji(Emoji.fromFormatted(factionEmoji));
                            compButtons.add(jrButton);
                        }

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

            if (relic.equalsIgnoreCase(Constants.ENIGMATIC_DEVICE) || !relic.contains("starchart") && (relicData.getText().contains("Action:") || relicData.getText().contains("ACTION:"))) {
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
                && Mapper.getPromissoryNoteText(pn, true) != null) {
                String pnText = Mapper.getPromissoryNoteText(pn, true);
                if (pnText.contains("Action:") && !"bmf".equalsIgnoreCase(pn)) {
                    PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(pn);
                    String pnName = pnModel.getName();
                    Button pnButton = Button.danger(finChecker + prefix + "pn_" + pn, "Use " + pnName);
                    compButtons.add(pnButton);
                }
            }
            if (Mapper.getPromissoryNoteText(pn, true) == null) {
                MessageHelper.sendMessageToChannel(getCorrectChannel(p1, activeGame),
                    p1.getRepresentation(true, true) + " you have a null PN. Please use /pn purge after reporting it " + pn);
                PNInfo.sendPromissoryNoteInfo(activeGame, p1, false);
            }
        }
        //Abilities
        if (p1.hasAbility("star_forge") && (p1.getStrategicCC() > 0 || p1.hasRelicReady("emelpar")) && getTilesOfPlayersSpecificUnits(activeGame, p1, UnitType.Warsun).size() > 0) {
            Button abilityButton = Button.success(finChecker + prefix + "ability_starForge", "Starforge").withEmoji(Emoji.fromFormatted(Emojis.Muaat));
            compButtons.add(abilityButton);
        }
        if (p1.hasAbility("meditation") && (p1.getStrategicCC() > 0 || p1.hasRelicReady("emelpar")) && p1.getExhaustedTechs().size() > 0) {
            Button abilityButton = Button.success(finChecker + prefix + "ability_meditation", "Meditation").withEmoji(Emoji.fromFormatted(Emojis.kolume));
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
        if (isPlayerElected(activeGame, p1, "absol_minswar") && !activeGame.getFactionsThatReactedToThis("absolMOW").contains(p1.getFaction())) {
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
        String msg = getIdent(player) + " successfully preset " + messageID;
        String part2 = player.getFaction();
        if (activeGame.getFactionsThatReactedToThis(messageID) != null && !activeGame.getFactionsThatReactedToThis(messageID).isEmpty()) {
            part2 = activeGame.getFactionsThatReactedToThis(messageID) + "_" + player.getFaction();
        }
        if (StringUtils.countMatches(buttonID, "_") > 1) {
            part2 = part2 + "_" + buttonID.split("_")[2];
            msg = msg + " on " + buttonID.split("_")[2];
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        activeGame.setCurrentReacts(messageID, part2);
        event.getMessage().delete().queue();
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.danger("removePreset_" + messageID, "Remove The Preset"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentation() + " you can use this button to undo the preset. Ignore it otherwise", buttons);

    }

    public static void resolveRemovalOfPreAssignment(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String messageID = buttonID.split("_")[1];
        String msg = getIdent(player) + " successfully removed the preset for " + messageID;
        String part2 = player.getFaction();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        if (activeGame.getFactionsThatReactedToThis(messageID) != null) {
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
        Map<String, Emoji> emojiMap = ButtonListener.emoteMap.get(guild);
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
            if (activeGame.getFactionsThatReactedToThis(messageId) != null) {
                if (!activeGame.getFactionsThatReactedToThis(messageId).contains(player.getFaction())) {
                    activeGame.setCurrentReacts(messageId, activeGame.getFactionsThatReactedToThis(messageId) + "_" + player.getFaction());
                }
            } else {
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
            text += activeGame.getPing() + " " + additionalMessage;
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

        Map<String, Emoji> emojiMap = ButtonListener.emoteMap.get(guild);
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
                    if (activeGame.getFactionsThatReactedToThis(messageId) != null) {
                        if (!activeGame.getFactionsThatReactedToThis(messageId).contains(player.getFaction())) {
                            activeGame.setCurrentReacts(messageId, activeGame.getFactionsThatReactedToThis(messageId) + "_" + player.getFaction());
                        }
                    } else {
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
                    text += activeGame.getPing() + " " + additionalMessage;
                }

                if (activeGame.isFoWMode() && !sendPublic) {
                    MessageHelper.sendPrivateMessageToPlayer(player, activeGame, text);
                }
            });
        } catch (Error e) {
            activeGame.removeMessageIDForSabo(messageID);
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
        Collections.addAll(unitTypes, type);

        return activeGame.getTileMap().values().stream()
            .filter(t -> t.containsPlayersUnitsWithKeyCondition(p1, unit -> unitTypes.contains(unit.getUnitType())))
            .toList();
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
            .filter(e -> e.getKey().equals(unitKey)).mapToInt(e -> Optional.ofNullable(e.getValue()).orElse(0)).sum();
    }

    public static void resolveDiploPrimary(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String type = buttonID.split("_")[2];
        if (type.toLowerCase().contains("mahact")) {
            String color2 = type.replace("mahact", "");
            Player mahactP = activeGame.getPlayerFromColorOrFaction(color2);
            if (mahactP == null) {
                MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), "Could not find mahact player");
                return;
            }
            Tile tile = activeGame.getTileByPosition(planet);
            AddCC.addCC(event, color2, tile);
            Helper.isCCCountCorrect(event, activeGame, color2);
            for (String color : mahactP.getMahactCC()) {
                if (Mapper.isValidColor(color) && !color.equalsIgnoreCase(player.getColor())) {
                    AddCC.addCC(event, color, tile);
                    Helper.isCCCountCorrect(event, activeGame, color);
                }
            }
            String message = getIdent(player) + " chose to use the mahact PN in the tile " + tile.getRepresentation();
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), message);
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
                    if (Mapper.isValidColor(color)) {
                        AddCC.addCC(event, color, tile);
                        Helper.isCCCountCorrect(event, activeGame, color);
                    }
                }
            }
            String message = getIdent(player) + " chose to diplo the system containing " + Helper.getPlanetRepresentation(planet, activeGame);
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
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), p1.getRepresentation(true, true) + " Select who you would like to mageon.", buttons);
                }
                if ("vtx".equalsIgnoreCase(buttonID)) {
                    List<Button> buttons = ButtonHelperFactionSpecific.getUnitButtonsForVortex(p1, activeGame, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), p1.getRepresentation(true, true) + " Select what unit you would like to capture", buttons);
                }
                if ("wg".equalsIgnoreCase(buttonID)) {
                    List<Button> buttons = new ArrayList<>(ButtonHelperFactionSpecific.getCreussIFFTypeOptions());
                    String message = p1.getRepresentation(true, true) + " select type of wormhole you wish to drop";
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(p1, activeGame), message, buttons);
                }
                if ("pm".equalsIgnoreCase(buttonID)) {
                    ButtonHelperFactionSpecific.resolveProductionBiomesStep1(p1, activeGame, event);
                }
                if ("lgf".equalsIgnoreCase(buttonID)) {
                    if (p1.getPlanets().contains("mr")) {
                        new AddUnits().unitParsing(event, p1.getColor(), activeGame.getTileFromPlanet("mr"), "inf mr", activeGame);
                        MessageHelper.sendMessageToChannel(getCorrectChannel(p1, activeGame), getIdent(p1) + " added 1 infantry to Mecatol Rex using Laxax Gate Folding");
                    }
                }
                if ("sr".equalsIgnoreCase(buttonID)) {
                    List<Button> buttons = new ArrayList<>();
                    List<Tile> tiles = new ArrayList<>(getTilesOfPlayersSpecificUnits(activeGame, p1, UnitType.Spacedock, UnitType.CabalSpacedock, UnitType.PlenaryOrbital));
                    if (p1.hasUnit("ghoti_flagship")) {
                        tiles.addAll(getTilesOfPlayersSpecificUnits(activeGame, p1, UnitType.Flagship));
                    }
                    List<String> pos2 = new ArrayList<>();
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

                if (!Mapper.isValidLeader(buttonID)) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not resolve leader.");
                    return;
                }
                if (buttonID.contains("agent")) {
                    List<String> leadersThatNeedSpecialSelection = List.of("naaluagent", "muaatagent", "kolumeagent", "arborecagent", "bentoragent", "xxchaagent", "axisagent");
                    if (leadersThatNeedSpecialSelection.contains(buttonID)) {
                        List<Button> buttons = getButtonsForAgentSelection(activeGame, buttonID);
                        String message = p1.getRepresentation(true, true) + " Use buttons to select the user of the agent";
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                    } else {
                        ExhaustLeader.exhaustLeader(event, activeGame, p1, p1.getLeader(buttonID).orElse(null), null);
                    }
                } else if (buttonID.contains("hero")) {
                    HeroPlay.playHero(event, activeGame, p1, p1.getLeader(buttonID).orElse(null));
                }
            }
            case "relic" -> {
                String purgeOrExhaust = "Purged ";

                if (p1.hasRelic(buttonID)) {
                    if ("titanprototype".equalsIgnoreCase(buttonID) || "absol_jr".equalsIgnoreCase(buttonID)) {
                        List<Button> buttons2 = AgendaHelper.getPlayerOutcomeButtons(activeGame, null, "jrResolution", null);
                        p1.addExhaustedRelic(buttonID);
                        purgeOrExhaust = "Exhausted ";
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to decide who to use JR on", buttons2);
                        for (Player p2 : activeGame.getRealPlayers()) {
                            if (p2.hasTech("tcs") && !p2.getExhaustedTechs().contains("tcs")) {
                                List<Button> buttons3 = new ArrayList<>();
                                buttons3.add(Button.success("exhaustTCS_" + buttonID + "_" + p1.getFaction(), "Exhaust TCS to Ready " + buttonID));
                                buttons3.add(Button.danger("deleteButtons", "Decline"));
                                String msg = p2.getRepresentation(true, true) + " you have the opportunity to exhaust your TCS tech to ready " + buttonID + " and potentially resolve a transaction.";
                                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame), msg, buttons3);
                            }
                        }
                    } else {
                        p1.removeRelic(buttonID);
                        p1.removeExhaustedRelic(buttonID);
                    }

                    RelicModel relicModel = Mapper.getRelic(buttonID);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), purgeOrExhaust + Emojis.Relic + " relic: " + relicModel.getName() + "\n> " + relicModel.getText());
                    if (relicModel.getName().contains("Enigmatic")) {
                        ButtonHelperActionCards.resolveFocusedResearch(activeGame, p1, buttonID, event);
                    }
                    if (relicModel.getName().contains("Nanoforge")) {
                        offerNanoforgeButtons(p1, activeGame, event);
                    }
                    if (buttonID.contains("decrypted_cartoglyph")) {
                        new DrawBlueBackTile().drawBlueBackTiles(event, activeGame, p1, 3, false);
                    }
                    if ("dynamiscore".equals(buttonID) || "absol_dynamiscore".equals(buttonID)) {
                        int oldTg = p1.getTg();
                        p1.setTg(oldTg + p1.getCommoditiesTotal() + 2);
                        if ("absol_dynamiscore".equals(buttonID)) {
                            p1.setTg(p1.getTg() + 2);
                        }
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), p1.getRepresentation(true, true) + " Your tgs increased from " + oldTg + " -> " + p1.getTg());
                        ButtonHelperAbilities.pillageCheck(p1, activeGame);
                        ButtonHelperAgents.resolveArtunoCheck(p1, activeGame, p1.getTg() - oldTg);
                    }
                    if ("stellarconverter".equals(buttonID)) {
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), p1.getRepresentation(true, true) + " Select the planet you want to destroy",
                            getButtonsForStellar(p1, activeGame));
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
                    String secretScoreMsg = "_ _\n" + p1.getRepresentation(true, true) + " Click a button below to discard an Action Card";
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
                } else if ("meditation".equalsIgnoreCase(buttonID)) {
                    if (p1.getStrategicCC() > 0) {
                        String successMessage = ButtonHelper.getIdent(p1) + " Reduced strategy pool CCs by 1 (" + (p1.getStrategicCC()) + "->" + (p1.getStrategicCC() - 1) + ")";
                        p1.setStrategicCC(p1.getStrategicCC() - 1);
                        ButtonHelperCommanders.resolveMuaatCommanderCheck(p1, activeGame, event);
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
                    } else {
                        String successMessage = ButtonHelper.getIdent(p1) + " Exhausted Scepter";
                        p1.addExhaustedRelic("emelpar");
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
                    }
                    String message = "Select the tech you would like to ready";
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), message, getAllTechsToReady(activeGame, p1));
                }
            }
            case "getRelic" -> {
                String message = "Click the fragments you'd like to purge. ";
                List<Button> purgeFragButtons = new ArrayList<>();
                int numToBeat = 2 - p1.getUrf();
                if ((p1.hasAbility("fabrication") || p1.getPromissoryNotes().containsKey("bmf"))) {
                    numToBeat = numToBeat - 1;
                    if (p1.getPromissoryNotes().containsKey("bmf") && activeGame.getPNOwner("bmf") != p1) {
                        Button transact = Button.primary(finChecker + "resolvePNPlay_bmfNotHand", "Play BMF");
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
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    getIdent(p1) + " is exhausting the agenda called Minister of War and spending a strategy cc to remove 1 cc from the board");
                if (p1.getStrategicCC() > 0) {
                    p1.setStrategicCC(p1.getStrategicCC() - 1);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), getIdent(p1) + " strategy cc went from " + (p1.getStrategicCC() + 1) + " to " + p1.getStrategicCC());
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(p1, activeGame, event);
                }
                List<Button> buttons = getButtonsToRemoveYourCC(p1, activeGame, event, "absol");
                MessageChannel channel = getCorrectChannel(p1, activeGame);
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
            case "doStarCharts" -> {
                purge2StarCharters(p1);
                new DrawBlueBackTile().drawBlueBackTiles(event, activeGame, p1, 1, false);
            }
        }

        if (!firstPart.contains("ability") && !firstPart.contains("getRelic")) {
            String message = "Use buttons to end turn or do another action.";
            List<Button> systemButtons = getStartOfTurnButtons(p1, activeGame, true, event);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
        }
        // FileUpload file = new GenerateMap().saveImage(activeGame, DisplayType.all, event);
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
                && (!"technology".equalsIgnoreCase(stratName) || !activeGame.getComponentAction())) {
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
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), message);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, activeGame), message, buttons);
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

    public static void resolveSARMechStep1(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String tilePos = buttonID.split("_")[1];
        String warfareOrNot = buttonID.split("_")[2];
        List<Button> buttons = new ArrayList<>();
        Tile tile = activeGame.getTileByPosition(tilePos);
        for (UnitHolder uH : tile.getPlanetUnitHolders()) {
            if (player.getPlanetsAllianceMode().contains(uH.getName())) {
                buttons.add(Button.success("sarMechStep2_" + uH.getName() + "_" + warfareOrNot, "Place mech on " + Helper.getPlanetRepresentation(uH.getName(), activeGame)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentation(true, true) + " choose the planet to drop a mech on", buttons);
        deleteTheOneButton(event);
    }

    public static void resolveSARMechStep2(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String warfareOrNot = buttonID.split("_")[2];
        String msg1 = getIdent(player) + " exhausted Self-Assembley Routines to place a mech on " + Helper.getPlanetRepresentation(planet, activeGame);
        player.exhaustTech("sar");
        Tile tile = activeGame.getTileFromPlanet(planet);
        new AddUnits().unitParsing(event, player.getColor(), tile, "mech " + planet, activeGame);
        event.getMessage().delete().queue();
        sendMessageToRightStratThread(player, activeGame, msg1, warfareOrNot);
    }

    public static String resolveACDraw(Player p2, Game activeGame, GenericInteractionCreateEvent event) {
        String message = "";
        if (p2.hasAbility("scheming")) {
            activeGame.drawActionCard(p2.getUserID());
            activeGame.drawActionCard(p2.getUserID());
            message = getIdent(p2) + " Drew 2 AC With Scheming. Please Discard An AC with the blue buttons";
            MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), p2.getRepresentation(true, true) + " use buttons to discard",
                ACInfo.getDiscardActionCardButtons(activeGame, p2, false));
        } else if (p2.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(activeGame, p2, 1);
            message = getIdent(p2) + " Triggered Autonetic Memory Option";
        } else {
            activeGame.drawActionCard(p2.getUserID());
            message = getIdent(p2) + " Drew 1 AC";
            ACInfo.sendActionCardInfo(activeGame, p2, event);
        }
        return message;
    }

    public static int getNumberOfStarCharts(Player player) {
        int count = 0;
        for (String relic : player.getRelics()) {
            if (relic.contains("starchart")) {
                count++;
            }
        }
        return count;
    }

    public static void purge2StarCharters(Player player) {
        List<String> relics = new ArrayList<>(player.getRelics());
        int count = 0;
        for (String relic : relics) {
            if (relic.contains("starchart") && count < 2) {
                count++;
                player.removeRelic(relic);
            }
        }
    }

    public static void offerPlayerPreferences(Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.secondary("playerPref_autoSaboReact", "Change Auto No-Sabo React Time").withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
        buttons.add(Button.secondary("playerPref_afkTimes", "Change AFK Times"));
        buttons.add(Button.secondary("playerPref_tacticalAction", "Change Distance-Based Tactical Action Preference"));
        buttons.add(Button.secondary("playerPref_autoNoWhensAfters", "Change Auto No Whens/Afters React").withEmoji(Emoji.fromFormatted(Emojis.Agenda)));
        buttons.add(Button.secondary("playerPref_personalPingInterval", "Change Personal Ping Interval"));
        //deleteTheOneButton(event);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation() + " Choose the thing you wish to change", buttons);
    }

    public static void resolvePlayerPref(Player player, ButtonInteractionEvent event, String buttonID, Game activeGame) {
        String thing = buttonID.split("_")[1];
        switch (thing) {
            case "autoSaboReact" -> {
                ButtonHelper.offerSetAutoPassOnSaboButtons(activeGame, player);
            }
            case "afkTimes" -> {
                ButtonHelper.offerAFKTimeOptions(activeGame, player);
            }
            case "tacticalAction" -> {
                List<Button> buttons = new ArrayList<>();
                String msg = player.getRepresentation()
                    + " Choose whether you want your tactical action buttons to be distance based (offer you 0 tiles away initially, then 1, 2, 3 tiles away upon more button presses) or ring based (choose what ring the active system is in). Default is ring based. This will apply to all your games";
                buttons.add(Button.success("playerPrefDecision_true_distance", "Make it distance based"));
                buttons.add(Button.success("playerPrefDecision_false_distance", "Make it ring based"));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg, buttons);
            }
            case "autoNoWhensAfters" -> {
                List<Button> buttons = new ArrayList<>();
                String msg = player.getRepresentation()
                    + " Choose whether you want the game to auto react no whens/afters after a random amount of time for you when you have no whens/afters. Default is off. This will only apply to this game. If you have any whens or afters or related when/after abilities, it will not do anything. ";
                buttons.add(Button.success("playerPrefDecision_true_agenda", "Turn on"));
                buttons.add(Button.success("playerPrefDecision_false_agenda", "Turn off"));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg, buttons);
            }
            case "personalPingInterval" -> {
                offerPersonalPingOptions(activeGame, player);
            }
        }
        event.getMessage().delete().queue();
    }

    public static void resolvePlayerPrefDecision(Player player, ButtonInteractionEvent event, String buttonID, Game activeGame) {
        String trueOrFalse = buttonID.split("_")[1];
        String distanceOrAgenda = buttonID.split("_")[2];
        if (trueOrFalse.equals("true")) {
            if (distanceOrAgenda.equals("distance")) {
                player.setPreferenceForDistanceBasedTacticalActions(true);
                Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
                for (Game activeGame2 : mapList.values()) {
                    for (Player player2 : activeGame2.getRealPlayers()) {
                        if (player2.getUserID().equalsIgnoreCase(player.getUserID())) {
                            player2.setPreferenceForDistanceBasedTacticalActions(true);
                            GameSaveLoadManager.saveMap(activeGame2);
                        }
                    }
                }
            } else {
                player.setAutoPassWhensAfters(true);
            }
        } else {
            if (distanceOrAgenda.equals("distance")) {
                player.setPreferenceForDistanceBasedTacticalActions(false);
                Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
                for (Game activeGame2 : mapList.values()) {
                    for (Player player2 : activeGame2.getRealPlayers()) {
                        if (player2.getUserID().equalsIgnoreCase(player.getUserID())) {
                            player2.setPreferenceForDistanceBasedTacticalActions(false);
                            GameSaveLoadManager.saveMap(activeGame2);
                        }
                    }
                }
            } else {
                player.setAutoPassWhensAfters(false);
            }
        }
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Set setting successfully");

        event.getMessage().delete().queue();
    }

    //TODO: Combine with PlayPN.playPN()
    public static void resolvePNPlay(String id, Player player, Game activeGame, GenericInteractionCreateEvent event) {
        boolean longPNDisplay = false;
        boolean fromHand = true;
        if ("bmfNotHand".equals(id)) {
            fromHand = false;
            id = "bmf";
        }

        if (id.contains("dspnflor")) {
            if (id.contains("Checked")) {
                id = "dspnflor";
            } else {
                MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, player.getRepresentation(true, true)
                    + " this PN will be applied automatically the next time you draw a relic. It will not work if you play it before then, so I am stopping you here");
                return;
            }
        }
        PromissoryNoteModel pn = Mapper.getPromissoryNote(id);
        String pnName = pn.getName();
        // String pnOwner = Mapper.getPromissoryNoteOwner(id);
        Player owner = activeGame.getPNOwner(id);
        if (pn.getPlayArea() && !player.isPlayerMemberOfAlliance(owner)) {
            player.setPromissoryNotesInPlayArea(id);
        } else {
            player.removePromissoryNote(id);
            if(!id.equalsIgnoreCase("dspncymi")){
                owner.setPromissoryNote(id);
                PNInfo.sendPromissoryNoteInfo(activeGame, owner, false);
            }
            PNInfo.sendPromissoryNoteInfo(activeGame, player, false);
        }
        String emojiToUse = activeGame.isFoWMode() ? "" : owner.getFactionEmoji();
        StringBuilder sb = new StringBuilder(player.getRepresentation() + " played promissory note: " + pnName + "\n");
        sb.append(emojiToUse).append(Emojis.PN);
        String pnText;

        //Handle AbsolMode Political Secret
        if (activeGame.isAbsolMode() && id.endsWith("_ps")) {
            pnText = "Political Secret" + Emojis.Absol
                + ":  *When you cast votes:* You may exhaust up to 3 of the {color} player's planets and cast additional votes equal to the combined influence value of the exhausted planets. Then return this card to the {color} player.";
        } else {
            pnText = Mapper.getPromissoryNoteText(id, longPNDisplay);
        }
        sb.append(pnText).append("\n");

        //TERRAFORM TIP

        if (id.contains("dspnveld")) {
            ButtonHelperFactionSpecific.offerVeldyrButtons(player, activeGame, id);
        }
        if ("terraform".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.offerTerraformButtons(player, activeGame, event);
        }
        if ("iff".equalsIgnoreCase(id)) {
            List<Button> buttons = new ArrayList<>(ButtonHelperFactionSpecific.getCreussIFFTypeOptions());
            String message = player.getRepresentation(true, true) + " select type of wormhole you wish to drop";
            MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, activeGame), message, buttons);
        }
        if ("greyfire".equalsIgnoreCase(id)) {
            List<Button> buttons = ButtonHelperFactionSpecific.getGreyfireButtons(activeGame);
            String message = player.getRepresentation(true, true) + " select planet you wish to use greyfire on";
            MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, activeGame), message, buttons);
        }
        if ("dspnlizh".equalsIgnoreCase(id)) {
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(activeGame.getActiveSystem()), "2 ff", activeGame);
            String message = player.getRepresentation(true, true) + " added 2 ff to the active system";
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), message);
        }
        if("dspncymi".equalsIgnoreCase(id)){
            ButtonHelper.pickACardFromDiscardStep1(activeGame,player);
        }
        if("dspnkort".equalsIgnoreCase(id)){
            List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, activeGame, event, "kortalipn");
            MessageChannel channel = ButtonHelper.getCorrectChannel(player, activeGame);
            MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to remove token.", buttons);
        }
        if ("ragh".equalsIgnoreCase(id)) {
            String message = player.getRepresentation(true, true) + " select planet to raghs call on";
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), message,
                ButtonHelperFactionSpecific.getRaghsCallButtons(player, activeGame, activeGame.getTileByPosition(activeGame.getActiveSystem())));
        }
        if ("ms".equalsIgnoreCase(id)) {
            List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, activeGame, "2gf", "placeOneNDone_skipbuild"));
            if (owner.getStrategicCC() > 0) {
                owner.setStrategicCC(owner.getStrategicCC() - 1);
                MessageHelper.sendMessageToChannel(getCorrectChannel(owner, activeGame),
                    owner.getRepresentation(true, true) + " lost a command counter from strategy pool due to a Military Support play");
            }
            String message = player.getRepresentation(true, true) + " Use buttons to drop 2 infantry on a planet";
            MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, activeGame), message, buttons);
        }
        if (!"agendas_absol".equals(activeGame.getAgendaDeckID()) && id.endsWith("_ps")) {
            MessageHelper.sendMessageToChannel(getCorrectChannel(owner, activeGame), owner.getRepresentation(true, true)
                + " due to a play of your PS, you will be unable to vote in agenda (unless you have xxcha alliance). The bot doesnt enforce the other restrictions regarding no abilities, but you should abide by them.");
            activeGame.setCurrentReacts("AssassinatedReps", activeGame.getFactionsThatReactedToThis("AssassinatedReps") + owner.getFaction());
        }

        //Fog of war ping
        if (activeGame.isFoWMode()) {
            // Add extra message for visibility
            FoWHelper.pingAllPlayersWithFullStats(activeGame, event, player, sb.toString());
        }
        MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), sb.toString());
        if ("fires".equalsIgnoreCase(id)) {
            player.addTech("ws");
            if (player.getLeaderIDs().contains("mirvedacommander") && !player.hasLeaderUnlocked("mirvedacommander")) {
                commanderUnlockCheck(player, activeGame, "mirveda", event);
            }
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation(true, true) + " acquired Warsun tech");
            owner.setFleetCC(owner.getFleetCC() - 1);
            String reducedMsg = owner.getRepresentation(true, true) + " reduced your fleet cc by 1 due to fires being played";
            if (activeGame.isFoWMode()) {
                MessageHelper.sendMessageToChannel(owner.getPrivateChannel(), reducedMsg);
            } else {
                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), reducedMsg);
            }
        }
        if (id.endsWith("_ta")) {
            int comms = owner.getCommodities();
            owner.setCommodities(0);
            String reducedMsg = owner.getRepresentation(true, true) + " your TA was played.";
            String reducedMsg2 = player.getRepresentation(true, true) + " you gained tgs equal to the number of comms the player had (your tgs went from "
                + player.getTg() + "tgs to -> " + (player.getTg() + comms) + "tgs). Please follow up with the player if this number seems off";
            player.setTg(player.getTg() + comms);
            ButtonHelperFactionSpecific.resolveDarkPactCheck(activeGame, owner, player, owner.getCommoditiesTotal());
            ButtonHelperAbilities.pillageCheck(player, activeGame);
            MessageHelper.sendMessageToChannel(getCorrectChannel(owner, activeGame), reducedMsg);
            MessageHelper.sendMessageToChannel(getCorrectChannel(player, activeGame), reducedMsg2);
        }
        if (("favor".equalsIgnoreCase(id))) {
            if (owner.getStrategicCC() > 0) {
                owner.setStrategicCC(owner.getStrategicCC() - 1);
                String reducedMsg = owner.getRepresentation(true, true) + " reduced your strategy cc by 1 due to your PN getting played";
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
            String message = player.getRepresentation(true, true) + " Use buttons choose which system to mahact diplo";
            MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, activeGame), message, Helper.getPlanetSystemDiploButtons(event, player, activeGame, false, owner));
        }
        if (id.contains("rider")) {
            String riderName = "Keleres Rider";
            String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";

            List<Button> riderButtons = AgendaHelper.getAgendaButtons(riderName, activeGame, finsFactionCheckerPrefix);
            List<Button> afterButtons = AgendaHelper.getAfterButtons(activeGame);
            MessageHelper.sendMessageToChannelWithFactionReact(activeGame.getMainGameChannel(), "Please select your rider target", activeGame, player, riderButtons);
            MessageHelper.sendMessageToChannelWithPersistentReacts(activeGame.getMainGameChannel(), "Please indicate no afters again.", activeGame, afterButtons, "after");

        }
        if ("dspnedyn".equalsIgnoreCase(id)) {
            String riderName = "Edyn Rider";
            String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";

            List<Button> riderButtons = AgendaHelper.getAgendaButtons(riderName, activeGame, finsFactionCheckerPrefix);
            List<Button> afterButtons = AgendaHelper.getAfterButtons(activeGame);
            MessageHelper.sendMessageToChannelWithFactionReact(activeGame.getMainGameChannel(), "Please select your rider target", activeGame, player, riderButtons);
            MessageHelper.sendMessageToChannelWithPersistentReacts(activeGame.getMainGameChannel(), "Please indicate no afters again.", activeGame, afterButtons, "after");
        }
        if ("dspnkyro".equalsIgnoreCase(id)) {
            String riderName = "Kyro Rider";
            String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";

            List<Button> riderButtons = AgendaHelper.getAgendaButtons(riderName, activeGame, finsFactionCheckerPrefix);
            List<Button> afterButtons = AgendaHelper.getAfterButtons(activeGame);
            MessageHelper.sendMessageToChannelWithFactionReact(activeGame.getMainGameChannel(), "Please select your rider target", activeGame, player, riderButtons);
            MessageHelper.sendMessageToChannelWithPersistentReacts(activeGame.getMainGameChannel(), "Please indicate no afters again.", activeGame, afterButtons, "after");
        }
        PNInfo.sendPromissoryNoteInfo(activeGame, player, false);
        PNInfo.sendPromissoryNoteInfo(activeGame, owner, false);
        if ("spynet".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.offerSpyNetOptions(player);
        }
        if ("gift".equalsIgnoreCase(id)) {
            startActionPhase(event, activeGame);
        }
        if ("bmf".equalsIgnoreCase(id)) {
            if (fromHand) {
                String finChecker = "";
                String message = "Click the fragments you'd like to purge. ";
                List<Button> purgeFragButtons = new ArrayList<>();
                int numToBeat = 2 - player.getUrf();

                numToBeat = numToBeat - 1;

                if (player.getCrf() > numToBeat) {
                    for (int x = numToBeat + 1; (x < player.getCrf() + 1 && x < 4); x++) {
                        Button transact = Button.primary(finChecker + "purge_Frags_CRF_" + x, "Cultural Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }
                if (player.getIrf() > numToBeat) {
                    for (int x = numToBeat + 1; (x < player.getIrf() + 1 && x < 4); x++) {
                        Button transact = Button.success(finChecker + "purge_Frags_IRF_" + x, "Industrial Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }
                if (player.getHrf() > numToBeat) {
                    for (int x = numToBeat + 1; (x < player.getHrf() + 1 && x < 4); x++) {
                        Button transact = Button.danger(finChecker + "purge_Frags_HRF_" + x, "Hazardous Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }

                if (player.getUrf() > 0) {
                    for (int x = 1; x < player.getUrf() + 1; x++) {
                        Button transact = Button.secondary(finChecker + "purge_Frags_URF_" + x, "Frontier Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }
                Button transact2 = Button.danger(finChecker + "drawRelicFromFrag", "Finish Purging and Draw Relic");
                purgeFragButtons.add(transact2);
                MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(player, activeGame), message, purgeFragButtons);
            }
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
                if (faction != null && Mapper.isValidFaction(faction)) {
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
