package ti4.service.milty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.function.Consumers;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.AliasHandler;
import ti4.helpers.Helper;
import ti4.helpers.StringHelper;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import ti4.model.TileModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiltyDraftEmojis;
import ti4.service.emoji.TI4Emoji;

@Data
public class MiltyDraftManager {
    private static final String SUMMARY_START = "# **__Draft Picks So Far__**:";

    private final List<MiltyDraftTile> all = new ArrayList<>();
    private final List<MiltyDraftTile> blue = new ArrayList<>();
    private final List<MiltyDraftTile> red = new ArrayList<>();
    private final List<MiltyDraftSlice> slices = new ArrayList<>();
    private final Map<String, PlayerDraft> draft = new HashMap<>(); //userID

    private int draftIndex;
    private List<String> draftOrder = new ArrayList<>(); // userID
    private List<String> players = new ArrayList<>(); // userID
    private List<String> factionDraft = new ArrayList<>();

    private String mapTemplate;

    private boolean finished;

    @Data
    public static class PlayerDraft {
        private String faction;
        private MiltyDraftSlice slice;
        private Integer position;

        public String summary(String doggy) {
            return String.join(" ", factionEmoji(doggy), sliceEmoji(), positionEmoji());
        }

        private String factionEmoji(String doggy) {
            return faction == null ? doggy : FactionEmojis.getFactionIcon(faction).toString();
        }

        private String sliceEmoji() {
            String ord = slice == null ? null : slice.getName();
            return MiltyDraftEmojis.getMiltyDraftEmoji(ord).toString();
        }

        private String positionEmoji() {
            int ord = position == null ? -1 : position;
            return MiltyDraftEmojis.getSpeakerPickEmoji(ord).toString();
        }

        @JsonIgnore
        public String save() {
            String factionStr = faction == null ? "null" : faction;
            String sliceStr = slice == null ? "null" : slice.getName();
            String orderStr = position == null ? "null" : Integer.toString(position);
            return String.join(",", factionStr, sliceStr, orderStr);
        }
    }

    public void addDraftTile(MiltyDraftTile draftTile) {
        TierList draftTileTier = draftTile.getTierList();
        switch (draftTileTier) {
            case high, mid, low -> blue.add(draftTile);
            case red, anomaly -> red.add(draftTile);
        }
        all.add(draftTile);
    }

    public String getCurrentDraftPlayer() {
        if (draftOrder.size() <= draftIndex) return null;
        return draftOrder.get(draftIndex);
    }

    public String getNextDraftPlayer() {
        if (draftOrder.size() <= draftIndex + 1) return null;
        return draftOrder.get(draftIndex + 1);
    }

    public Player getCurrentDraftPlayer(Game game) {
        String user = getCurrentDraftPlayer();
        if (user == null) return null;
        return game.getPlayer(user);
    }

    public Player getNextDraftPlayer(Game game) {
        String user = getNextDraftPlayer();
        if (user == null) return null;
        return game.getPlayer(user);
    }

    public void setNextPlayerInDraft() {
        draftIndex++;
    }

    public void replacePlayer(String oldUID, String newUID) {
        // Update player list
        List<String> newPlayers = new ArrayList<>();
        players.forEach(p -> newPlayers.add(p.equals(oldUID) ? newUID : p));
        players.clear();
        players.addAll(newPlayers);

        // Update draft order
        List<String> newDraftOrder = new ArrayList<>();
        draftOrder.forEach(p -> newDraftOrder.add(p.equals(oldUID) ? newUID : p));
        draftOrder.clear();
        draftOrder.addAll(newDraftOrder);

        // Update player draft keys
        Map<String, PlayerDraft> newDraft = new HashMap<>();
        draft.forEach((k, v) -> newDraft.put(k.equals(oldUID) ? newUID : k, v));
        draft.clear();
        draft.putAll(newDraft);
    }

    public List<MiltyDraftTile> getBlue() {
        return new ArrayList<>(blue);
    }

    public List<MiltyDraftTile> getRed() {
        return new ArrayList<>(red);
    }

    public void addSlice(MiltyDraftSlice slice) {
        slices.add(slice);
    }

    public void setPlayers(List<String> draftOrder) {
        players = draftOrder;

        for (String player : draftOrder) {
            if (!draft.containsKey(player)) {
                draft.put(player, new PlayerDraft());
            }
        }
    }

    public PlayerDraft getPlayerDraft(Player player) {
        return draft.get(player.getUserID());
    }

    public PlayerDraft getPlayerDraft(String player) {
        return draft.get(player);
    }

    public void setFactionDraft(List<String> factions) {
        factionDraft.clear();
        factionDraft.addAll(factions);
    }

    public List<String> allRemainingOptionsForActive() {
        List<String> remaining = new ArrayList<>();
        if (getCurrentDraftPlayer() == null) return remaining;
        PlayerDraft active = getPlayerDraft(getCurrentDraftPlayer());
        if (active.getSlice() == null) {
            for (MiltyDraftSlice slice : slices)
                if (!isSliceTaken(slice.getName()))
                    remaining.add("Slice " + slice.getName());
        }
        if (active.getFaction() == null) {
            for (String faction : factionDraft)
                if (!isFactionTaken(faction))
                    remaining.add(faction);
        }
        if (active.getPosition() == null) {
            for (int i = 1; i <= players.size(); i++)
                if (!isOrderTaken(i))
                    remaining.add(StringHelper.ordinal(i) + " pick");
        }
        return remaining;
    }

    public List<FactionModel> remainingFactions() {
        List<FactionModel> ls = new ArrayList<>();
        for (String faction : factionDraft) {
            FactionModel model = Mapper.getFaction(faction);
            if (model == null || isFactionTaken(faction)) continue;
            ls.add(model);
        }
        return ls;
    }

    public List<FactionModel> pickedFactions() {
        List<FactionModel> ls = new ArrayList<>();
        for (String faction : factionDraft) {
            FactionModel model = Mapper.getFaction(faction);
            if (model == null || !isFactionTaken(faction)) continue;
            ls.add(model);
        }
        return ls;
    }

    public List<FactionModel> allFactions() {
        List<FactionModel> ls = new ArrayList<>();
        for (String faction : factionDraft) {
            FactionModel model = Mapper.getFaction(faction);
            if (model == null) continue;
            ls.add(model);
        }
        return ls;
    }

    public void init(Game game) {
        clear();
        MiltyDraftHelper.initDraftTiles(this, game);
    }

    //TODO (Jazz): Integrate this directly in the manager. For now, it's just dumb and hacky
    public void init(List<ComponentSource> sources) {
        clear();
        MiltyDraftHelper.initDraftTiles(this, sources);
    }

    @JsonIgnore
    public void clearSlices() {
        slices.clear();
    }

    @JsonIgnore
    public void clear() {
        clearSlices();
        all.clear();
        blue.clear();
        red.clear();
        draft.clear();
        draftOrder.clear();
        players.clear();
        factionDraft.clear();
        draftIndex = 0;
    }

    @JsonIgnore
    public MiltyDraftSlice getSlice(String sliceName) {
        for (MiltyDraftSlice s : slices) {
            if (s.getName().equals(sliceName)) {
                return s;
            }
        }
        return null;
    }

    @JsonIgnore
    public boolean isSliceTaken(String sliceName) {
        for (PlayerDraft pd : draft.values()) {
            if (pd.getSlice() != null && pd.getSlice().getName().equals(sliceName)) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean isOrderTaken(int value) {
        for (PlayerDraft pd : draft.values()) {
            if (pd.getPosition() != null && pd.getPosition() == value) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean isFactionTaken(String faction) {
        for (PlayerDraft pd : draft.values()) {
            if (pd.getFaction() != null && pd.getFaction().equals(faction)) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public void doMiltyPick(GenericInteractionCreateEvent event, Game game, String buttonID, Player player) {
        String userId = player.getUserID();
        MessageChannel mainGameChannel = game.getMainGameChannel();
        if (draftIndex >= draftOrder.size()) {
            FinishDraftService.finishDraft(event, this, game);
            return;
        }
        if (getCurrentDraftPlayer() == null || !userId.equals(getCurrentDraftPlayer())) {
            if (event instanceof ButtonInteractionEvent bevent) {
                bevent.getHook().sendMessage("You are not up to draft").setEphemeral(true).queue(Consumers.nop(), BotLogger::catchRestError);
            } else {
                event.getMessageChannel().sendMessage("Something went wrong").queue();
            }
            return;
        }

        boolean auto = buttonID.startsWith("miltyAuto_");
        boolean force = buttonID.startsWith("miltyForce_");
        String draftPick = buttonID.replace("milty_", "").replace("miltyAuto_", "").replace("miltyForce_", "");
        String category = draftPick.substring(0, draftPick.indexOf("_"));
        String item = draftPick.substring(draftPick.indexOf("_") + 1);

        String errorMessage = switch (category) {
            case "slice" -> draftSlice(userId, item);
            case "faction" -> draftFaction(userId, item);
            case "order" -> draftSpeakerOrder(userId, item);
            default -> "Error parsing milty button press: " + buttonID;
        };

        //Oopsiedoops
        if (errorMessage != null) {
            if (event instanceof ButtonInteractionEvent bevent) {
                bevent.getHook().sendMessage(errorMessage).setEphemeral(true).queue(Consumers.nop(), BotLogger::catchRestError);
            } else {
                event.getMessageChannel().sendMessage(errorMessage).queue();
            }
            return;
        }

        // Send success message
        String middle = " drafted ";
        if (auto) middle = " only had one option available to draft, so they were given ";
        if (force) middle = " was forced to take ";
        try {
            String drafted = player.getPing() + middle + switch (category) {
                case "slice" -> "Slice " + item;
                case "faction" -> Mapper.getFaction(item).getFactionTitle().replace("Keleres - Mentak", "Keleres");
                case "order" -> StringHelper.ordinal(Integer.parseInt(item)) + " pick";
                default -> "Error parsing milty button press: " + buttonID;
            } + "!";
            MessageHelper.sendMessageToChannel(mainGameChannel, drafted);
        } catch (Exception e) {
            // Shouldn't get errors here, but fallback to a boring message
            String drafted = player.getPing() + middle + item;
            MessageHelper.sendMessageToChannel(mainGameChannel, drafted);
        }

        if (category.equals("faction") && item.contains("keleres")) {
            MiltyService.offerKeleresSetupButtons(this, player);
        }

        try {
            MiltyDraftHelper.buildPartialMap(game, event);
        } catch (Exception e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(event, game), "err", e);
        }

        setNextPlayerInDraft();
        if (draftIndex < draftOrder.size()) {
            game.updateActivePlayer(getCurrentDraftPlayer(game));

            // Check if the next player only has one option to pick
            String fauxPlayerPick = null;
            Player nextDrafter = getCurrentDraftPlayer(game);
            PlayerDraft pd = getPlayerDraft(nextDrafter);
            if (pd.getPosition() == null && pd.getFaction() != null && pd.getSlice() != null) {
                fauxPlayerPick = getAutoButtonID(getPositionButtons());
            } else if (pd.getSlice() == null && pd.getFaction() != null && pd.getPosition() != null) {
                fauxPlayerPick = getAutoButtonID(getSliceButtons());
            } else if (pd.getFaction() == null && pd.getPosition() != null && pd.getSlice() != null) {
                fauxPlayerPick = getAutoButtonID(getFactionButtons());
            }

            if (fauxPlayerPick != null) {
                doMiltyPick(event, game, fauxPlayerPick, nextDrafter);
            } else {
                DraftDisplayService.updateDraftInformation(event, this, game, category);
                DraftDisplayService.pingCurrentDraftPlayer(event, this, game, false);
            }
        } else {
            MessageHelper.sendMessageToChannel(mainGameChannel, game.getPing() + " the draft is finished! Ping jazz if there are any issues with the map.");
            FinishDraftService.finishDraft(event, this, game);
            game.updateActivePlayer(null);
        }
    }

    private String getAutoButtonID(List<Button> buttons) {
        if (buttons.size() == 1) return buttons.getFirst().getId().replaceFirst("milty_", "miltyAuto_");
        return null;
    }

    private String draftSlice(String player, String sliceName) {
        PlayerDraft current = getPlayerDraft(player);
        MiltyDraftSlice slice = getSlice(sliceName);
        if (current.getSlice() != null) return "You have already picked a slice. Try again.";
        if (isSliceTaken(sliceName)) return "Slice #" + sliceName + " has already been drafted. Try again.";
        if (slice == null) return "This slice (Slice #" + sliceName + ") doesn't seem to exist. Try again.";

        // Success
        current.setSlice(slice);
        return null;
    }

    private String draftSpeakerOrder(String player, String order) {
        PlayerDraft current = getPlayerDraft(player);
        if (current.getPosition() != null) return "You have already picked speaker order. Try again.";
        try {
            int draftedSpeakerOrder = Integer.parseInt(order);
            if (isOrderTaken(draftedSpeakerOrder)) return "The item you chose is already taken. Try again.";

            // Success
            current.setPosition(draftedSpeakerOrder);
            return null;
        } catch (Exception e) {
            return "Something went wrong picking your speaker order. Try again, and if the problem persists ping Jazz.";
        }
    }

    private String draftFaction(String player, String faction) {
        PlayerDraft current = getPlayerDraft(player);
        FactionModel factionModel = Mapper.getFaction(faction);
        if (current.getFaction() != null) return "You have already picked a faction. Try again.";
        if (isFactionTaken(faction)) return faction + " has already been drafted. Try again.";
        if (factionModel == null || !factionDraft.contains(faction)) return "This faction (" + faction + ") doesn't seem to exist or is not available in this draft. Try again.";

        // Success
        current.setFaction(faction);
        return null;
    }

    public List<Button> getSliceButtons() {
        List<Button> sliceButtons = new ArrayList<>();
        for (MiltyDraftSlice slice : getSlices()) {
            if (isSliceTaken(slice.getName())) continue;
            sliceButtons.add(Buttons.green("milty_slice_" + slice.getName(), " ", MiltyDraftEmojis.getMiltyDraftEmoji(slice.getName())));
        }
        return sliceButtons;
    }

    public List<Button> getFactionButtons() {
        List<Button> factionButtons = new ArrayList<>();
        for (String faction : getFactionDraft()) {
            FactionModel model = Mapper.getFaction(faction);
            if (model == null || isFactionTaken(faction)) continue;

            String name = model.getFactionName();
            if (faction.startsWith("keleres"))
                name = "The Council Keleres";
            factionButtons.add(Buttons.gray("milty_faction_" + faction, name, model.getFactionEmoji()));
        }
        return factionButtons;
    }

    public List<Button> getPositionButtons() {
        List<Button> orderButtons = new ArrayList<>();
        for (int speakerOrder = 1; speakerOrder <= players.size(); speakerOrder++) {
            if (isOrderTaken(speakerOrder)) continue;
            orderButtons.add(Buttons.green("milty_order_" + speakerOrder, " ", MiltyDraftEmojis.getSpeakerPickEmoji(speakerOrder)));
        }
        return orderButtons;
    }

    public String getOverallSummaryString(Game game) {
        int padding = String.format("%s", getPlayers().size()).length() + 1;
        String goodDogOfTheDay = TI4Emoji.getRandomGoodDog().toString();
        StringBuilder sb = new StringBuilder(SUMMARY_START);
        int pickNum = 1;
        for (String p : getPlayers()) {
            Player player = game.getPlayer(p);
            PlayerDraft picks = getPlayerDraft(p);
            sb.append("\n> `").append(Helper.leftpad(pickNum + ".", padding)).append("` ");
            sb.append(picks.summary(goodDogOfTheDay)).append(" ");

            String next = getNextDraftPlayer();
            String current = getCurrentDraftPlayer();
            if (next != null && p.equals(getNextDraftPlayer())) sb.append("*");
            if (current != null && p.equals(getCurrentDraftPlayer())) sb.append("**__");
            sb.append(player.getUserName());
            if (current != null && p.equals(getCurrentDraftPlayer())) sb.append("   <- CURRENTLY DRAFTING");
            if (next != null && p.equals(getNextDraftPlayer())) sb.append("   <- on deck");
            if (current != null && p.equals(getCurrentDraftPlayer())) sb.append("__**");
            if (next != null && p.equals(getNextDraftPlayer())) sb.append("*");

            pickNum++;
        }
        return sb.toString();
    }

    //SAVE AND LOAD
    public String superSaveMessage() {
        try {
            String sliceStr = String.join(";", slices.stream().map(MiltyDraftSlice::ttsString).toList());
            String factionStr = String.join(",", factionDraft);
            String playerStr = String.join(",", players);
            String picksStr = String.join(";", players.stream().map(p -> getPlayerDraft(p).save()).toList());
            String templateStr = getMapTemplate();

            List<String> lesserSaves = Arrays.asList(sliceStr, factionStr, playerStr, picksStr, /* messagesStr, */ templateStr);
            return String.join("|", lesserSaves);
        } catch (Exception e) {
            return "error";
        }
    }

    public void loadSuperSaveString(String saveString) throws Exception {
        StringTokenizer bigTokenizer = new StringTokenizer(saveString, "|");
        boolean ignoreMessageIDs = false;
        if (bigTokenizer.countTokens() == 5) {
            ignoreMessageIDs = true;
        } else if (bigTokenizer.countTokens() != 6) {
            throw new Exception("Bad milty draft save string: " + saveString);
        }

        // Slices
        String slices = bigTokenizer.nextToken();
        loadSlicesFromString(slices);

        // Factions
        String factionStr = bigTokenizer.nextToken();
        List<String> factions = new ArrayList<>(Arrays.asList(factionStr.split(",")));
        setFactionDraft(factions);

        // Players
        String playersStr = bigTokenizer.nextToken();
        List<String> players = new ArrayList<>(Arrays.asList(playersStr.split(",")));
        List<String> playersReversed = new ArrayList<>(players);
        Collections.reverse(playersReversed);
        List<String> draftOrder = new ArrayList<>(players);
        draftOrder.addAll(playersReversed);
        draftOrder.addAll(players);
        setDraftOrder(draftOrder);
        setPlayers(players);

        // Picks
        String picksStr = bigTokenizer.nextToken();
        StringTokenizer pickTokenizer = new StringTokenizer(picksStr, ";");
        int index = 0;
        while (pickTokenizer.hasMoreTokens()) {
            String player = players.get(index);
            StringTokenizer currentPicks = new StringTokenizer(pickTokenizer.nextToken(), ",");
            if (draftFaction(player, currentPicks.nextToken()) == null)
                setNextPlayerInDraft();
            if (draftSlice(player, currentPicks.nextToken()) == null)
                setNextPlayerInDraft();
            if (draftSpeakerOrder(player, currentPicks.nextToken()) == null)
                setNextPlayerInDraft();
            index++;
        }

        if (!ignoreMessageIDs) {
            // DEPRECATED
            //StringTokenizer messageIds = 
            new StringTokenizer(bigTokenizer.nextToken(), ",");
        }

        // Map Template
        String savedTemplate = bigTokenizer.nextToken();
        setMapTemplate(savedTemplate);
    }

    public void loadSlicesFromString(String str) {
        int sliceIndex = 1;
        StringTokenizer sliceTokenizer = new StringTokenizer(str, ";");
        while (sliceTokenizer.hasMoreTokens()) {
            loadSliceFromString(sliceTokenizer.nextToken(), sliceIndex);
            sliceIndex++;
        }
    }

    private void loadSliceFromString(String str, int index) {
        List<String> tiles = Arrays.asList(str.split(","));
        List<MiltyDraftTile> draftTiles = tiles.stream()
            .map(AliasHandler::resolveTile)
            .map(this::findTile).toList();
        MiltyDraftSlice slice = new MiltyDraftSlice();
        slice.setTiles(draftTiles);
        slice.setName(Character.toString(index - 1 + 'A'));
        slices.add(slice);
    }

    private MiltyDraftTile findTile(String tileId) {
        MiltyDraftTile result = all.stream().filter(t -> t.getTile().getTileID().equals(tileId)).findFirst().orElse(null);
        if (result == null) {
            TileModel tileRequested = TileHelper.getTileById(tileId);
            Set<ComponentSource> currentsources = all.stream()
                .map(t -> t.getTile().getTileModel().getSource())
                .filter(Objects::nonNull).collect(Collectors.toSet());
            if (tileRequested.getSource() != null) currentsources.add(tileRequested.getSource());
            if (tileId.matches("d\\d{1,3}")) currentsources.add(ComponentSource.uncharted_space);
            if (tileId.matches("e\\d{1,3}")) currentsources.add(ComponentSource.eronous);
            MiltyDraftHelper.initDraftTiles(this, new ArrayList<>(currentsources));
            result = all.stream().filter(t -> t.getTile().getTileID().equals(tileId)).findFirst().orElseThrow();
        }
        return result;
    }
}
