package ti4.commands.draft;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.commands.Subcommand;
import ti4.commands.SubcommandGroup;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftManager;
import ti4.service.draft.draftables.FactionDraftable;

public class FactionDraftableSubcommands extends SubcommandGroup {

    private static final Map<String, Subcommand> subcommands = Stream.of(
                    new FactionDraftableAddFaction(),
                    new FactionDraftableRemoveFaction(),
                    new FactionDraftableSetKeleresFlavor())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    protected FactionDraftableSubcommands() {
        super(Constants.DRAFT_FACTION, "Commands for managing faction drafting");
    }

    @Override
    public Map<String, Subcommand> getGroupSubcommands() {
        return subcommands;
    }

    public static FactionDraftable getDraftable(Game game) {
        DraftManager draftManager = game.getDraftManager();
        if (draftManager == null) {
            return null;
        }
        return (FactionDraftable) draftManager.getDraftableByType(FactionDraftable.TYPE);
    }

    public static class FactionDraftableAddFaction extends GameStateSubcommand {

        protected FactionDraftableAddFaction() {
            super(Constants.DRAFT_FACTION_ADD, "Add a faction to the draft", true, true);
            addOption(OptionType.STRING, Constants.FACTION, "The faction to add", true, true);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            FactionDraftable draftable = getDraftable(getGame());
            if (draftable == null) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "Factions aren't draftable; you may need `/draft manage add_draftable Faction`.");
                return;
            }
            String factionAlias = event.getOption(Constants.FACTION).getAsString();
            try {
                draftable.addFaction(factionAlias);
                MessageHelper.sendMessageToChannel(
                        event.getChannel(), "Added faction " + factionAlias + " to the draft. ");
                draftable.validateState(getGame().getDraftManager());
            } catch (IllegalArgumentException e) {
                MessageHelper.sendMessageToChannel(event.getChannel(), e.getMessage());
            }
        }
    }

    public static class FactionDraftableRemoveFaction extends GameStateSubcommand {

        protected FactionDraftableRemoveFaction() {
            super(Constants.DRAFT_FACTION_REMOVE, "Remove a faction from the draft", true, true);
            addOption(OptionType.STRING, Constants.DRAFT_FACTION_OPTION, "The faction to remove", true, true);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            FactionDraftable draftable = getDraftable(getGame());
            if (draftable == null) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "Factions aren't draftable; you may need `/draft manage add_draftable Faction`.");
                return;
            }
            String factionAlias =
                    event.getOption(Constants.DRAFT_FACTION_OPTION).getAsString();
            try {
                draftable.removeFaction(factionAlias);
                MessageHelper.sendMessageToChannel(
                        event.getChannel(), "Removed faction " + factionAlias + " from the draft.");
                draftable.validateState(getGame().getDraftManager());
            } catch (IllegalArgumentException e) {
                MessageHelper.sendMessageToChannel(event.getChannel(), e.getMessage());
            }
        }
    }

    public static class FactionDraftableSetKeleresFlavor extends GameStateSubcommand {

        protected FactionDraftableSetKeleresFlavor() {
            super(Constants.DRAFT_FACTION_SET_KELERES_FLAVOR, "Set the Keleres flavor for the draft", true, false);
            addOption(
                    OptionType.STRING,
                    Constants.KELERES_FLAVOR_OPTION,
                    "Either 'mentak', 'xxcha', or 'argent'",
                    true,
                    true);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            FactionDraftable draftable = getDraftable(getGame());
            if (draftable == null) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "Factions aren't draftable; you may need `/draft manage add_draftable Faction`.");
                return;
            }
            String flavor = event.getOption(Constants.KELERES_FLAVOR_OPTION).getAsString();
            try {
                draftable.setKeleresFlavor(flavor);
                MessageHelper.sendMessageToChannel(event.getChannel(), "Keleres flavor set to '" + flavor + "''.");
                draftable.validateState(getGame().getDraftManager());
            } catch (IllegalArgumentException e) {
                MessageHelper.sendMessageToChannel(event.getChannel(), e.getMessage());
            }
        }
    }
}
