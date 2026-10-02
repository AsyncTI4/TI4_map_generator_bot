package ti4.discord.interactions.commands.frankendraz;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.draft.DraftBag;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.draft.FrankenDrazDraft;
import ti4.draft.items.FactionDraftItem;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AliasHandler;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;

final class FrankenDrazFactionHelper {

    private FrankenDrazFactionHelper() {}

    static String resolveFaction(String rawFaction, SlashCommandInteractionEvent event) {
        if (StringUtils.isBlank(rawFaction)) {
            MessageHelper.sendMessageToEventChannel(event, "Please provide a faction.");
            return null;
        }

        String faction = AliasHandler.resolveFaction(rawFaction.toLowerCase());
        if (!Mapper.isValidFaction(faction)) {
            MessageHelper.sendMessageToEventChannel(event, "Invalid faction: " + rawFaction);
            return null;
        }
        return faction;
    }

    static boolean canManagePostDraftComponents(Game game, Player player, SlashCommandInteractionEvent event) {
        if (!(game.getActiveBagDraft() instanceof FrankenDrazDraft)) {
            MessageHelper.sendMessageToEventChannel(event, "This command can only be used in a FrankenDraz draft.");
            return false;
        }
        if (player.getDraftHand() == null) {
            MessageHelper.sendMessageToEventChannel(event, player.getRepresentation() + " does not have a draft hand.");
            return false;
        }
        if (player.getDraftHand().getCategoryCount(DraftCategory.FACTION) > 0) {
            MessageHelper.sendMessageToEventChannel(
                    event, "This command can only be used after the FrankenDraz draft bags have been applied.");
            return false;
        }
        return true;
    }

    static ComponentChange addFactionComponents(Game game, Player player, String faction) {
        DraftBag hand = player.getDraftHand();
        Set<String> existingAliases =
                hand.Contents.stream().map(DraftItem::getAlias).collect(Collectors.toCollection(LinkedHashSet::new));

        List<DraftItem> added = new ArrayList<>();
        for (DraftItem component : getFactionComponents(game, faction)) {
            if (existingAliases.add(component.getAlias())) {
                hand.Contents.add(component);
                added.add(component);
            }
        }
        return new ComponentChange(added, List.of());
    }

    static ComponentChange removeFactionComponents(Game game, Player player, String faction) {
        Set<String> componentAliases = getFactionComponents(game, faction).stream()
                .map(DraftItem::getAlias)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<DraftItem> removed = new ArrayList<>();
        player.getDraftHand().Contents.removeIf(item -> {
            if (!componentAliases.contains(item.getAlias())) {
                return false;
            }
            removed.add(item);
            return true;
        });
        return new ComponentChange(List.of(), removed);
    }

    static String buildAddMessage(Player player, String faction, List<DraftItem> added) {
        return player.getRepresentation() + " added " + added.size() + " post-draft components from "
                + factionName(faction) + "." + summarizeItems(added, "Added");
    }

    static String buildRemoveMessage(Player player, String faction, List<DraftItem> removed) {
        return player.getRepresentation() + " removed " + removed.size() + " post-draft components from "
                + factionName(faction) + "." + summarizeItems(removed, "Removed");
    }

    static String buildSwapMessage(
            Player player,
            String removeFaction,
            String addFaction,
            ComponentChange removeChange,
            ComponentChange addChange) {
        return player.getRepresentation() + " swapped post-draft components from " + factionName(removeFaction)
                + " to " + factionName(addFaction) + ".\n"
                + "Removed " + removeChange.removed().size() + " components."
                + summarizeItems(removeChange.removed(), "Removed")
                + "\nAdded " + addChange.added().size() + " components."
                + summarizeItems(addChange.added(), "Added");
    }

    private static List<DraftItem> getFactionComponents(Game game, String faction) {
        return new FactionDraftItem(faction).getComponents(game);
    }

    private static String summarizeItems(List<DraftItem> items, String label) {
        if (items.isEmpty()) {
            return "\n> " + label + ": none";
        }

        Map<DraftCategory, List<DraftItem>> byCategory =
                items.stream().collect(Collectors.groupingBy(DraftItem::getItemCategory));
        StringBuilder summary = new StringBuilder();
        byCategory.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> summary.append("\n> ")
                .append(entry.getKey())
                .append(": ")
                .append(entry.getValue().stream()
                        .map(DraftItem::getShortDescription)
                        .collect(Collectors.joining(", "))));
        return summary.toString();
    }

    private static String factionName(String faction) {
        FactionModel model = Mapper.getFaction(faction);
        return model == null ? faction : model.getFactionName();
    }

    record ComponentChange(List<DraftItem> added, List<DraftItem> removed) {}
}
