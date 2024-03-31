package ti4.commands.milty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.helpers.StringHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;

@Data
public class MiltyDraftManager {

    private final List<MiltyDraftTile> all = new ArrayList<>();
    private final List<MiltyDraftTile> blue = new ArrayList<>();
    private final List<MiltyDraftTile> red = new ArrayList<>();
    private final List<MiltyDraftSlice> slices = new ArrayList<>();
    private final Map<String, PlayerDraft> draft = new HashMap<>(); //userID

    private int draftIndex = 0;
    private List<String> draftOrder = new ArrayList<>(); // userID
    private List<String> players = new ArrayList<>(); // userID
    private List<String> factionDraft = new ArrayList<>();

    private String prevSummaryMessage = null;
    private String prevSliceMessage = null;
    private String prevFactionMessage = null;
    private String prevOrderMessage = null;

    @Data
    public static class PlayerDraft {
        private String faction = null;
        private MiltyDraftSlice slice = null;
        private Integer order = null;

        @JsonIgnore
        public String summary() {
            StringBuilder sb = new StringBuilder();
            if (faction == null) {
                sb.append("<faction>");
            } else {
                sb.append(Emojis.getFactionIconFromDiscord(faction));
            }
            sb.append(" -- ");
            if (slice == null) {
                sb.append("<slice>");
            } else {
                sb.append(Emojis.getMiltyDraftEmoji(slice.getName()));
            }
            sb.append(" -- ");
            if (order == null) {
                sb.append("<pick>");
            } else {
                sb.append(Emojis.getSpeakerPickEmoji(order));
            }
            return sb.toString();
        }

        @JsonIgnore
        public String save() {
            String factionStr = faction == null ? "null" : faction;
            String sliceStr = slice == null ? "null" : slice.getName();
            String orderStr = order == null ? "null" : Integer.toString(order);
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
        return draftOrder.get(draftIndex);
    }

    public String getNextDraftPlayer() {
        if (draftOrder.size() <= draftIndex + 1) return null;
        return draftOrder.get(draftIndex + 1);
    }

    public Player getCurrentDraftPlayer(Game game) {
        return game.getPlayer(draftOrder.get(draftIndex));
    }

    public Player getNextDraftPlayer(Game game) {
        if (draftOrder.size() <= draftIndex + 1) return null;
        return game.getPlayer(draftOrder.get(draftIndex + 1));
    }

    public void setNextPlayerInDraft() {
        draftIndex++;
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

    public void init() {
        clear();
        MiltyDraftHelper.initDraftTiles(this);
    }

    @JsonIgnore
    public void clearSlices() {
        slices.clear();
    }

    @JsonIgnore
    public void clear() {
        clearSlices();
        blue.clear();
        red.clear();
        draft.clear();
        draftOrder.clear();
        players.clear();
        factionDraft.clear();
        draftIndex = 0;
        prevSummaryMessage = null;
        prevSliceMessage = null;
        prevFactionMessage = null;
        prevOrderMessage = null;
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
            if (pd.getOrder() != null && pd.getOrder() == value) {
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
    public void doMiltyPick(Game game, String buttonID, Player player) {
        String userId = player.getUserID();
        MessageChannel mainGameChannel = game.getMainGameChannel();
        if (draftIndex >= draftOrder.size()) {
            finishDraft(game);
            return;
        }
        if (!userId.equals(getCurrentDraftPlayer())) {
            MessageHelper.sendMessageToChannel(mainGameChannel, "You are not up to draft.");
            return;
        }

        String draftPick = buttonID.replace("milty_", "");
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
            MessageHelper.sendMessageToChannel(mainGameChannel, errorMessage);
            return;
        }

        // Send success message
        try {
            String drafted = player.getPing() + " drafted " + switch (category) {
                case "slice" -> "Slice #" + item;
                case "faction" -> Mapper.getFaction(item).getFactionTitle();
                case "order" -> StringHelper.ordinal(Integer.parseInt(item)) + " pick";
                default -> "Error parsing milty button press: " + buttonID;
            } + "!";
            MessageHelper.sendMessageToChannel(mainGameChannel, drafted);
        } catch (Exception e) {
            // Shouldn't get errors here, but fallback to a boring message
            String drafted = player.getPing() + " drafted " + item;
            MessageHelper.sendMessageToChannel(mainGameChannel, drafted);
        }

        // Clear out old buttons and ping the next player
        clearOldButtons(game);
        setNextPlayerInDraft();
        if (draftIndex < draftOrder.size()) {
            serveCurrentPlayer(game);
        } else {
            MessageHelper.sendMessageToChannel(mainGameChannel, "Draft is finished! Setting up the map...");
            finishDraft(game);
        }
    }

    private void finishDraft(Game game) {
        MessageChannel mainGameChannel = game.getMainGameChannel();
        try {
            MiltyDraftHelper.buildMap(game);
        } catch (Exception e) {
            String error = "Something went wrong and the map could not be built automatically. Here are the slice strings if you want to try doing it manually: ";
            List<PlayerDraft> speakerOrdered = getDraft().values().stream()
                .sorted(Comparator.comparing(PlayerDraft::getOrder))
                .toList();
            int index = 1;
            for (PlayerDraft d : speakerOrdered) {
                error += "\n" + index + ". " + d.getSlice().ttsString();
            }
            MessageHelper.sendMessageToChannel(mainGameChannel, error);
            BotLogger.log(e.getMessage(), e);
        }
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
        if (current.getOrder() != null) return "You have already picked speaker order. Try again.";
        try {
            Integer draftedSpeakerOrder = Integer.parseInt(order);
            if (draftedSpeakerOrder != null && isOrderTaken(draftedSpeakerOrder)) return "The item you chose is already taken. Try again.";

            // Success
            current.setOrder(draftedSpeakerOrder);
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

    private void clearOldButtons(Game game) {
        String summaryMsg = new String(prevSummaryMessage);
        String sliceMsg = new String(prevSliceMessage);
        String factionMsg = new String(prevFactionMessage);
        String orderMsg = new String(prevOrderMessage);

        try {
            game.getMainGameChannel().getHistoryAround(summaryMsg, 20).queue((history) -> {
                Message m = history.getMessageById(summaryMsg);
                if (m != null) m.delete().queue();

                m = history.getMessageById(sliceMsg);
                if (m != null) m.delete().queue();

                m = history.getMessageById(factionMsg);
                if (m != null) m.delete().queue();

                m = history.getMessageById(orderMsg);
                if (m != null) m.delete().queue();
            });
        } catch (Exception e) {
            BotLogger.log("Unable to clear out old buttons and messages.", e);
        }

        // And then null them out so we don't mess with 'em again
        prevSliceMessage = prevFactionMessage = prevOrderMessage = prevSummaryMessage = null;
    }

    private List<Button> getSliceButtons() {
        List<Button> sliceButtons = new ArrayList<>();
        for (MiltyDraftSlice slice : getSlices()) {
            if (isSliceTaken(slice.getName())) continue;
            Button button = Button.success("milty_slice_" + slice.getName(), slice.getName());
            String emoji = Emojis.getMiltyDraftEmoji(slice.getName());
            button = button.withEmoji(Emoji.fromFormatted(emoji));
            sliceButtons.add(button);
        }
        return sliceButtons;
    }

    private List<Button> getFactionButtons() {
        List<Button> factionButtons = new ArrayList<>();
        for (String faction : getFactionDraft()) {
            FactionModel model = Mapper.getFaction(faction);
            if (model == null || isFactionTaken(faction)) continue;

            Button button = Button.secondary("milty_faction_" + faction, model.getFactionName());
            button = button.withEmoji(Emoji.fromFormatted(model.getFactionEmoji()));
            factionButtons.add(button);
        }
        return factionButtons;
    }

    private List<Button> getOrderButtons() {
        List<Button> orderButtons = new ArrayList<>();
        for (int speakerOrder = 1; speakerOrder <= players.size(); speakerOrder++) {
            if (isOrderTaken(speakerOrder)) continue;
            Button button = Button.success("milty_order_" + speakerOrder, StringHelper.ordinal(speakerOrder) + " pick");
            String emoji = Emojis.getSpeakerPickEmoji(speakerOrder);
            button = button.withEmoji(Emoji.fromFormatted(emoji));
            orderButtons.add(button);
        }
        return orderButtons;
    }

    private String getOverallSummaryString(Game game) {
        StringBuilder sb = new StringBuilder();
        sb.append("# **__Draft Picks So Far__**:");
        int pickNum = 1;
        for (String p : getPlayers()) {
            Player player = game.getPlayer(p);
            PlayerDraft picks = getPlayerDraft(p);
            sb.append("\n> `").append(pickNum).append(".` ");
            if (p.equals(getCurrentDraftPlayer())) {
                sb.append(player.getPing());
            } else {
                sb.append(player.getUserName());
            }
            sb.append(": ");
            sb.append(picks.summary());
            if (p.equals(getCurrentDraftPlayer())) {
                sb.append("   <- CURRENTLY DRAFTING");
            }
            if (p.equals(getNextDraftPlayer())) {
                sb.append("   <- ON DECK");
            }
            pickNum++;
        }
        return sb.toString();
    }

    @JsonIgnore
    public void serveCurrentPlayer(Game game) {
        MessageChannel chan = game.getMainGameChannel();
        String summary = getOverallSummaryString(game);
        MessageHelper.splitAndSentWithAction(summary, chan, (m) -> prevSummaryMessage = m.getId());

        String slice = "**__Slices:__**", faction = "**__Factions:__**", speaker = "**__Speaker Order:__**";
        MessageHelper.splitAndSentWithAction(slice, chan, getSliceButtons(), (m) -> prevSliceMessage = m.getId());
        MessageHelper.splitAndSentWithAction(faction, chan, getFactionButtons(), (m) -> prevFactionMessage = m.getId());
        MessageHelper.splitAndSentWithAction(speaker, chan, getOrderButtons(), (m) -> prevOrderMessage = m.getId());
    }

    //SAVE AND LOAD
    public String superSaveMessage() {
        try {
            String sliceStr = String.join(";", slices.stream().map(s -> s.ttsString()).toList());
            String factionStr = String.join(",", factionDraft);
            String playerStr = String.join(",", players);
            String picksStr = String.join(";", players.stream().map(p -> getPlayerDraft(p).save()).toList());
            String messagesStr = getMessageIdSaveString();

            List<String> lesserSaves = List.of(sliceStr, factionStr, playerStr, picksStr, messagesStr);
            return String.join("|", lesserSaves);
        } catch (Exception e) {
            BotLogger.log("reeeeeeee", e);
            return "error";
        }
    }

    public String getMessageIdSaveString() {
        List<String> msgs = new ArrayList<>();
        msgs.add(prevSummaryMessage);
        msgs.add(prevSliceMessage);
        msgs.add(prevFactionMessage);
        msgs.add(prevOrderMessage);
        return String.join(",", msgs.stream().map(m -> m == null ? "null" : m).toList());
    }

    public void loadSuperSaveString(Game game, String saveString) throws Exception {
        StringTokenizer bigTokenizer = new StringTokenizer(saveString, "|");
        if (bigTokenizer.countTokens() != 5) {
            throw new Exception("Bad milty draft save string: " + saveString);
        }

        // Slices
        int sliceIndex = 1;
        String slices = bigTokenizer.nextToken();
        StringTokenizer sliceTokenizer = new StringTokenizer(slices, ";");
        while (sliceTokenizer.hasMoreTokens()) {
            loadSliceFromString(sliceTokenizer.nextToken(), sliceIndex);
            sliceIndex++;
        }

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

        // Messages
        StringTokenizer messageIds = new StringTokenizer(bigTokenizer.nextToken(), ",");
        prevSummaryMessage = messageIds.nextToken();
        if (prevSummaryMessage.equals("null")) {
            prevSummaryMessage = null;
        }
        prevSliceMessage = messageIds.nextToken();
        if (prevSliceMessage.equals("null")) {
            prevSliceMessage = null;
        }
        prevFactionMessage = messageIds.nextToken();
        if (prevFactionMessage.equals("null")) {
            prevFactionMessage = null;
        }
        prevOrderMessage = messageIds.nextToken();
        if (prevOrderMessage.equals("null")) {
            prevOrderMessage = null;
        }
    }

    private void loadSliceFromString(String str, int index) throws Exception {
        List<String> tiles = Arrays.asList(str.split(","));
        if (tiles.size() != 5) throw new Exception("Slice does not have the right number of tiles.");
        List<MiltyDraftTile> draftTiles = tiles.stream().map(t -> findTile(t)).toList();
        MiltyDraftSlice slice = new MiltyDraftSlice();
        slice.setLeft(draftTiles.get(0));
        slice.setFront(draftTiles.get(1));
        slice.setRight(draftTiles.get(2));
        slice.setEquidistant(draftTiles.get(3));
        slice.setFarFront(draftTiles.get(4));

        slice.setName(Character.toString(index - 1 + 'A'));
        slices.add(slice);
    }

    private MiltyDraftTile findTile(String tileId) {
        return all.stream().filter(t -> t.getTile().getTileID().equals(tileId)).findFirst().orElseThrow();
    }
}
