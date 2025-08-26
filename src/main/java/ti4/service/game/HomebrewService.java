package ti4.service.game;

import java.util.*;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.CryypterHelper;
import ti4.helpers.omega_phase.OmegaPhaseModStatusHelper;
import ti4.helpers.omega_phase.PriorityTrackHelper.PriorityTrackMode;
import ti4.helpers.omega_phase.VoiceOfTheCouncilHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ComponentType;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.SourceEmojis;
import ti4.service.emoji.TI4Emoji;

@UtilityClass
public class HomebrewService {

    public enum Homebrew {
        HB444("4/4/4", "4 secrets, 4 stage 1s, 4 stage 2s, 12 VP", null),
        HB456("4/5/6", "4 Secrets, 5 stage 1s, 6 stage2 (revealed 2 at a time), 14 VP", null),
        HBABSOLRELICSAGENDAS("Absol Relics/Agendas", "Use Absol Relics and Agendas", SourceEmojis.Absol),
        HBABSOLTECHSMECHS("Absol Techs/Mechs", "Use Absol Techs and Mechs", SourceEmojis.Absol),
        HBDSFACTIONS("DS Factions", "Discordant Stars Factions", SourceEmojis.DiscordantStars),
        HBDSEXPLORES(
                "US Explores/Relics/ACs",
                "Uncharted Space Explores, Relics and Action Cards",
                SourceEmojis.UnchartedSpace),
        HBACDECK2("AC2 Deck", "Action Cards Deck 2", SourceEmojis.ActionDeck2),
        HBREDTAPE("Red Tape", "Red Tape mode", null),
        HBIGNISAURORA("Ignis Aurora", "Ignis Aurora decks for SC/agendas/techs/events/relics", null),
        HBREMOVESFTT("No Supports", "Remove Support for the Thrones", null),
        HBHBSC("Homebrew SCs", "Indicate game uses homebrew Strategy Cards", CardEmojis.SCBackBlank),
        HBOMEGAPHASE("Omega Phase", "Enable Omega Phase homebrew mode", null),
        HBVOTC("Voices of the Council", "Voices of the Council mode", null);

        final String name;
        final String description;
        final TI4Emoji emoji;

        Homebrew(String name, String description, TI4Emoji emoji) {
            this.name = name;
            this.description = description;
            this.emoji = emoji;
        }
    }

    @ButtonHandler("offerGameHomebrewButtons")
    public static void offerGameHomebrewButtons(MessageChannel channel) {
        List<Button> homebrewButtons = new ArrayList<>();
        homebrewButtons.add(Buttons.green("getHomebrewButtons", "Yes Homebrew"));
        homebrewButtons.add(Buttons.red("deleteButtons", "No Homebrew"));
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(
                channel,
                "If you plan to have a supported homebrew mode in this game, please indicate "
                        + "so with these buttons. 4/4/4 is a type of homebrew btw",
                homebrewButtons);
    }

    @ButtonHandler("getHomebrewButtons")
    public static void offerHomeBrewButtons(Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();

        StringBuilder sb = new StringBuilder("### Choose the homebrew you'd like in the game\n");
        for (Homebrew hb : Homebrew.values()) {
            sb.append("**")
                    .append(hb.name)
                    .append("**: ")
                    .append(hb.description)
                    .append("\n");
            buttons.add(Buttons.green("setupHomebrew_" + hb, hb.name));
        }
        buttons.add(Buttons.red("setupHomebrewNone", "Remove All Homebrews"));
        buttons.add(Buttons.blue("showComponentSources", "Component Sources"));
        buttons.add(Buttons.DONE_DELETE_BUTTONS);

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), sb.toString(), buttons);
    }

    @ButtonHandler("setupHomebrewNone")
    public static void removeHomebrew(Game game, ButtonInteractionEvent event) {
        game.setHomebrewSCMode(false);
        game.setRedTapeMode(false);
        game.setDiscordantStarsMode(false);
        game.setAbsolMode(false);
        game.setOmegaPhaseMode(false);
        game.setVotcMode(false);
        game.setStoredValue("homebrewMode", "");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Set all homebrew options off. You need manually check and fix decks, VPs, objectives etc. that might've been set.");
    }

    @ButtonHandler("setupHomebrew_")
    public static void setUpHomebrew(Game game, ButtonInteractionEvent event, String buttonID) {
        ButtonHelper.deleteTheOneButton(event);
        game.setHomebrew(true);

        Homebrew type = Homebrew.valueOf(buttonID.split("_")[1]);
        switch (type) {
            case HB444 -> {
                game.setMaxSOCountPerPlayer(4);
                game.setUpPeakableObjectives(4, 1);
                game.setUpPeakableObjectives(4, 2);
                game.setVp(12);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set up 4/4/4.");
            }
            case HB456 -> {
                game.setMaxSOCountPerPlayer(4);
                game.setUpPeakableObjectives(5, 1);
                game.setUpPeakableObjectives(6, 2);
                game.setVp(14);
                game.setStoredValue("homebrewMode", "456");
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set up 4/5/6/14VP.");
            }
            case HBREMOVESFTT -> {
                for (Player p2 : game.getRealPlayers()) {
                    p2.removeOwnedPromissoryNoteByID(p2.getColor() + "_sftt");
                    p2.removePromissoryNote(p2.getColor() + "_sftt");
                }
                game.setStoredValue("removeSupports", "true");
            }
            case HBABSOLRELICSAGENDAS -> {
                game.setAbsolMode(true);
                // Toggle Absol agendas and relics via component sources
                toggleComponentSources(game, new String[] {"absol:agenda", "absol:relic"}, true);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Enabled Absol agendas and relics.");
                ti4.service.decks.DynamicDeckService.applyToGame(game);
            }
            case HBIGNISAURORA -> {
                game.setStrategyCardSet("ignis_aurora");
                game.setEventDeckID("events_baldrick");
                toggleComponentSources(
                        game,
                        new String[] {"ignis_aurora:agenda", "ignis_aurora:relic", "ignis_aurora:technology"},
                        true);
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(), "Enabled Ignis Aurora techs, relics, agendas and SC set.");
                ti4.service.decks.DynamicDeckService.applyToGame(game);
            }
            case HBABSOLTECHSMECHS -> {
                game.setAbsolMode(true);
                toggleComponentSources(game, new String[] {"absol:technology"}, true);
                game.swapInVariantUnits("absol");
                game.swapInVariantTechs();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Enabled Absol techs & mechs.");
                ti4.service.decks.DynamicDeckService.applyToGame(game);
            }
            case HBDSEXPLORES -> {
                game.setDiscordantStarsMode(true);
                game.setUnchartedSpaceStuff(true);
                toggleComponentSources(
                        game,
                        new String[] {"uncharted_space:explore", "ds:action_card", "ds:relic", "ds:technology"},
                        true);
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(), "Enabled Discordant Stars explores, ACs, relics, techs.");
                // Rebuild dynamic decks after toggles
                ti4.service.decks.DynamicDeckService.applyToGame(game);
            }
            case HBACDECK2 -> {
                // Use toggle system to switch to AC Deck 2
                toggleComponentSources(game, new String[] {"action_deck_2:action_card"}, true);
                ti4.service.decks.DynamicDeckService.applyToGame(game);
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(), "Enabled Action Card Deck 2 via dynamic sources.");
            }
            case HBDSFACTIONS -> {
                game.setDiscordantStarsMode(true);
                // DS factions is factions/tiles only; ensure techs respect DS if toggled elsewhere
                toggleComponentSources(game, new String[] {"ds:technology"}, true);
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "Set DS factions mode. Add US Explores to include DS ACs/Relics/Techs via toggles.");
            }
            case HBHBSC -> {
                game.setHomebrewSCMode(true);
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(), "Set game to homebrew strategy card mode.");
            }
            case HBREDTAPE -> {
                game.setRedTapeMode(true);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set game to Red Tape mode.");
            }
            case HBOMEGAPHASE -> {
                if (game.getRevealedPublicObjectives().size() > 1) {
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(),
                            "You can't enable Omega Phase after revealing public objectives.");
                    return;
                }
                game.setOmegaPhaseMode(true);
                game.validateAndSetPublicObjectivesStage1Deck(
                        event, Mapper.getDeck("public_stage_1_objectives_omegaphase"));
                game.setUpPeakableObjectives(9, 1);
                game.shuffleInBottomObjective(Constants.IMPERIUM_REX_ID, 5, 1);
                game.setUpPeakableObjectives(0, 2);
                game.validateAndSetPublicObjectivesStage2Deck(
                        event, Mapper.getDeck("public_stage_2_objectives_omegaphase"));
                game.setPriorityTrackMode(PriorityTrackMode.FULL);
                // Temporary measure: Remove incompatible components
                game.removeACFromGame("hack");
                game.removeAgendaFromGame("incentive");
                // end

                VoiceOfTheCouncilHelper.ResetVoiceOfTheCouncil(game);
                OmegaPhaseModStatusHelper.PrintGreeting(game);
            }
            case HBVOTC -> CryypterHelper.votcSetup(game, event);
        }
    }

    @ButtonHandler("showComponentSources")
    public static void showComponentSources(Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        String table = getAggregatedComponentSourcesList(game);
        StringBuilder sb = new StringBuilder();
        sb.append("### Component Source Toggles\n");
        sb.append(table.isEmpty() ? "(none)" : table);

        List<Button> buttons = new ArrayList<>();
        // Official Content
        for (ComponentSource cs : getOfficialSources()) {
            buttons.add(Buttons.blue("selectCompSource_" + cs.toString(), getSourceLabel(cs)));
        }
        // Major Mods
        for (ComponentSource cs : getMajorModSources()) {
            buttons.add(Buttons.blue("selectCompSource_" + cs.toString(), getSourceLabel(cs)));
        }
        // Personal Projects submenu
        buttons.add(Buttons.gray("showPersonalProjects", "Show Personal Projects"));
        // control row
        buttons.add(Buttons.green("enableAllComponentSources", "Enable All"));
        buttons.add(Buttons.red("disableAllComponentSources", "Disable All"));
        buttons.add(Buttons.gray("officialAllComponentSources", "Official Defaults"));
        buttons.add(Buttons.red("clearAllComponentSources", "Clear All"));
        buttons.add(Buttons.DONE_DELETE_BUTTONS);

        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), sb.toString(), buttons);
    }

    @ButtonHandler("showPersonalProjects")
    public static void showPersonalProjects(Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        StringBuilder sb = new StringBuilder();
        sb.append("### Personal Projects\n");
        sb.append("Select a source to toggle its component types.\n");
        List<Button> buttons = new ArrayList<>();
        for (ComponentSource cs : getPersonalProjectSourcesDynamic()) {
            buttons.add(Buttons.blue("selectCompSource_" + cs.toString(), getSourceLabel(cs)));
        }
        buttons.add(Buttons.blue("showComponentSources", "Back"));
        buttons.add(Buttons.DONE_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), sb.toString(), buttons);
    }

    @ButtonHandler("selectCompSource_")
    public static void selectComponentSource(Game game, ButtonInteractionEvent event, String buttonId) {
        ButtonHelper.deleteMessage(event);
        String sourceId = buttonId.substring("selectCompSource_".length());
        ComponentSource cs = ComponentSource.fromString(sourceId);
        if (cs == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Unknown source: " + sourceId);
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("### Toggle types for ").append(getSourceLabel(cs)).append("\n");
        sb.append(getAggregatedComponentSourcesList(game));

        EnumSet<ComponentType> supported = getSupportedTypesFor(cs);
        // Implicit apply-all if none of this source's types are enabled
        List<EnabledComponent> current = EnabledComponent.fromGame(game);
        boolean anyOn = false;
        for (ComponentType ct : supported) {
            if (EnabledComponent.has(current, cs, ct)) { anyOn = true; break; }
        }
        if (!anyOn && !supported.isEmpty()) {
            for (ComponentType ct : supported) {
                EnabledComponent.enableType(current, cs, ct);
            }
            EnabledComponent.toGame(game, current);
            ti4.service.decks.DynamicDeckService.applyToGame(game);
        }
        List<Button> buttons = new ArrayList<>();
        for (ComponentType ct : supported) {
            String pair = EnabledComponent.Entry.key(cs, ct);
            boolean on = EnabledComponent.has(current, cs, ct);
            String label = (on ? "ON " : "OFF ") + ct.toString();
            buttons.add(on ? Buttons.green("toggleCompSource_" + pair, label)
                           : Buttons.red("toggleCompSource_" + pair, label));
        }
        // per-source bulk: only Disable now
        buttons.add(Buttons.red("disableAllForSource_" + cs.toString(), "Disable"));
        buttons.add(Buttons.blue("showComponentSources", "Back"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), sb.toString(), buttons);
    }

    @ButtonHandler("toggleCompSource_")
    public static void toggleComponentSource(Game game, ButtonInteractionEvent event, String buttonId) {
        String pair = buttonId.substring("toggleCompSource_".length());
        EnabledComponent.Entry entry = EnabledComponent.Entry.parse(pair);
        if (entry == null) return;
        List<EnabledComponent> list = EnabledComponent.fromGame(game);
        boolean turnOn = !EnabledComponent.has(list, entry.source, entry.type);
        if (turnOn) {
            EnabledComponent.enableType(list, entry.source, entry.type);
        } else {
            EnabledComponent.disableType(list, entry.source, entry.type);
        }
        EnabledComponent.toGame(game, list);
        ti4.service.decks.DynamicDeckService.applyToGame(game);
        // Redisplay same source submenu
        selectComponentSource(game, event, "selectCompSource_" + entry.source.toString());
    }

    @ButtonHandler("applyComponentSourceToggles")
    public static void applyComponentSourceToggles(Game game, ButtonInteractionEvent event) {
        ti4.service.decks.DynamicDeckService.applyToGame(game);
        // After applying, return to the main component sources view (same as Back)
        showComponentSources(game, event);
    }

    @ButtonHandler("enableAllForSource_")
    public static void enableAllForSource(Game game, ButtonInteractionEvent event, String buttonId) {
        String srcId = buttonId.substring("enableAllForSource_".length());
        ComponentSource cs = ComponentSource.fromString(srcId);
        if (cs == null) return;
        EnumSet<ComponentType> types = getSupportedTypesFor(cs);
        List<EnabledComponent> list = EnabledComponent.fromGame(game);
        EnabledComponent.enableAllForSource(list, cs, types);
        EnabledComponent.toGame(game, list);
        ti4.service.decks.DynamicDeckService.applyToGame(game);
        selectComponentSource(game, event, "selectCompSource_" + srcId);
    }

    @ButtonHandler("disableAllForSource_")
    public static void disableAllForSource(Game game, ButtonInteractionEvent event, String buttonId) {
        String srcId = buttonId.substring("disableAllForSource_".length());
        ComponentSource cs = ComponentSource.fromString(srcId);
        if (cs == null) return;
        List<EnabledComponent> list = EnabledComponent.fromGame(game);
        EnabledComponent.disableAllForSource(list, cs);
        EnabledComponent.toGame(game, list);
        ti4.service.decks.DynamicDeckService.applyToGame(game);
        selectComponentSource(game, event, "selectCompSource_" + srcId);
    }

    @ButtonHandler("officialAllComponentSources")
    public static void officialAllComponentSources(Game game, ButtonInteractionEvent event) {
        game.setComponentSourcesCsv(String.join(",", getDefaultOfficialPairs()));
        showComponentSources(game, event);
    }

    @ButtonHandler("clearAllComponentSources")
    public static void clearAllComponentSources(Game game, ButtonInteractionEvent event) {
        game.setComponentSourcesCsv("");
        showComponentSources(game, event);
    }

    @ButtonHandler("enableAllComponentSources")
    public static void enableAllComponentSources(Game game, ButtonInteractionEvent event) {
        Map<ComponentSource, EnumSet<ComponentType>> support = getSupportedTypesBySource();
        List<EnabledComponent> list = EnabledComponent.fromGame(game);
        for (Map.Entry<ComponentSource, EnumSet<ComponentType>> e : support.entrySet()) {
            EnabledComponent.enableAllForSource(list, e.getKey(), e.getValue());
        }
        EnabledComponent.toGame(game, list);
        ti4.service.decks.DynamicDeckService.applyToGame(game);
        showComponentSources(game, event);
    }

    @ButtonHandler("disableAllComponentSources")
    public static void disableAllComponentSources(Game game, ButtonInteractionEvent event) {
        Map<ComponentSource, EnumSet<ComponentType>> support = getSupportedTypesBySource();
        List<EnabledComponent> list = EnabledComponent.fromGame(game);
        for (ComponentSource cs : support.keySet()) {
            EnabledComponent.disableAllForSource(list, cs);
        }
        EnabledComponent.toGame(game, list);
        ti4.service.decks.DynamicDeckService.applyToGame(game);
        showComponentSources(game, event);
    }

    private static void toggleComponentSources(Game game, String[] entries, boolean enable) {
        List<EnabledComponent> list = EnabledComponent.fromGame(game);
        for (String entry : entries) {
            EnabledComponent.Entry e = EnabledComponent.Entry.parse(entry);
            if (e == null) continue;
            if (enable) {
                EnabledComponent.enableType(list, e.source, e.type);
            } else {
                EnabledComponent.disableType(list, e.source, e.type);
            }
        }
        EnabledComponent.toGame(game, list);
    }

    private static boolean hasPair(Game game, String entry) {
        int idx = entry.indexOf(":");
        if (idx <= 0) return false;
        ComponentSource cs = ComponentSource.fromString(entry.substring(0, idx));
        ComponentType ct = ComponentType.fromString(entry.substring(idx + 1));
        if (cs == null || ct == null) return false;
        List<EnabledComponent> list = EnabledComponent.fromGame(game);
        return EnabledComponent.has(list, cs, ct);
    }

    private static String getAggregatedComponentSourcesList(Game game) {
        Map<ComponentSource, EnumSet<ComponentType>> support = getSupportedTypesBySource();
        if (support.isEmpty()) return "";
        List<EnabledComponent> current = EnabledComponent.fromGame(game);

        List<ComponentSource> sources = new ArrayList<>(support.keySet());
        sources.sort(HomebrewService::compareSourcesForDisplay);
        StringBuilder sb = new StringBuilder();
        for (ComponentSource cs : sources) {
            EnumSet<ComponentType> types = support.get(cs);
            if (types == null || types.isEmpty()) continue;
            boolean anyOn = false;
            for (ComponentType ct : types) {
                if (EnabledComponent.has(current, cs, ct)) { anyOn = true; break; }
            }
            if (!anyOn) continue; // hide sources that are entirely OFF

            sb.append("- ").append(getSourceLabel(cs)).append("\n");
            List<String> off = new ArrayList<>();
            for (ComponentType ct : types) {
                if (!EnabledComponent.has(current, cs, ct)) off.add(ct.toString());
            }
            if (!off.isEmpty()) {
                off.sort(String::compareTo);
                for (String t : off) {
                    sb.append("  - ❌ ").append(t).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private static List<ComponentSource> getOfficialSources() {
        return List.of(
                ComponentSource.base,
                ComponentSource.pok,
                ComponentSource.codex1,
                ComponentSource.codex2,
                ComponentSource.codex3,
                ComponentSource.codex4);
    }

    private static List<ComponentSource> getMajorModSources() {
        return List.of(
                ComponentSource.absol,
                ComponentSource.ds,
                ComponentSource.uncharted_space);
    }

    private static List<ComponentSource> getPersonalProjectSourcesDynamic() {
        Map<ComponentSource, EnumSet<ComponentType>> support = getSupportedTypesBySource();
        Set<ComponentSource> excluded = new HashSet<>();
        excluded.addAll(getOfficialSources());
        excluded.addAll(getMajorModSources());
        List<ComponentSource> sources = new ArrayList<>(support.keySet());
        sources.removeIf(excluded::contains);
        sources.sort(java.util.Comparator.comparing(HomebrewService::getSourceLabel));
        return sources;
    }

    private static String getSourceLabel(ComponentSource cs) {
        return switch (cs) {
            case base -> "Base Game";
            case pok -> "Prophecy of Kings";
            case codex1 -> "Codex 1";
            case codex2 -> "Codex 2";
            case codex3 -> "Codex 3";
            case codex4 -> "Codex 4";
            case ds -> "Discordant Stars";
            case uncharted_space -> "Uncharted Space";
            case absol -> "Absol";
            default -> cs.prettyName();
        };
    }

    private static int compareSourcesForDisplay(ComponentSource a, ComponentSource b) {
        List<ComponentSource> priority = List.of(
                ComponentSource.base,
                ComponentSource.pok,
                ComponentSource.codex1,
                ComponentSource.codex2,
                ComponentSource.codex3,
                ComponentSource.codex4,
                ComponentSource.ds,
                ComponentSource.uncharted_space,
                ComponentSource.absol);
        int ia = priority.indexOf(a);
        int ib = priority.indexOf(b);
        if (ia != -1 || ib != -1) {
            if (ia == -1) return 1;
            if (ib == -1) return -1;
            return Integer.compare(ia, ib);
        }
        return getSourceLabel(a).compareToIgnoreCase(getSourceLabel(b));
    }

    private static EnumSet<ComponentType> getSupportedTypesFor(ComponentSource source) {
        return getSupportedTypesBySource().getOrDefault(source, EnumSet.noneOf(ComponentType.class));
    }

    private static Map<ComponentSource, EnumSet<ComponentType>> getSupportedTypesBySource() {
        Map<ComponentSource, EnumSet<ComponentType>> out = new HashMap<>();
        // scan action cards
        for (var m : ti4.image.Mapper.getActionCards().values()) {
            out.computeIfAbsent(m.getSource(), k -> EnumSet.noneOf(ComponentType.class)).add(ComponentType.ACTION_CARD);
        }
        // scan agendas
        for (var m : ti4.image.Mapper.getAgendas().values()) {
            out.computeIfAbsent(m.getSource(), k -> EnumSet.noneOf(ComponentType.class)).add(ComponentType.AGENDA);
        }
        // scan explores
        for (var m : ti4.image.Mapper.getExplores().values()) {
            out.computeIfAbsent(m.getSource(), k -> EnumSet.noneOf(ComponentType.class)).add(ComponentType.EXPLORE);
        }
        // scan relics
        for (var m : ti4.image.Mapper.getRelics().values()) {
            out.computeIfAbsent(m.getSource(), k -> EnumSet.noneOf(ComponentType.class)).add(ComponentType.RELIC);
        }
        // scan techs
        for (var m : ti4.image.Mapper.getTechs().values()) {
            out.computeIfAbsent(m.getSource(), k -> EnumSet.noneOf(ComponentType.class)).add(ComponentType.TECHNOLOGY);
        }
        return out;
    }

    private static final class ComponentSourceCsvHelper {
        private static Set<String> parse(Game game) {
            String csv = game.getComponentSourcesCsv();
            Set<String> set = new LinkedHashSet<>();
            if (csv == null || csv.isBlank()) return set;
            for (String p : csv.split(",")) {
                String v = p == null ? null : p.trim();
                if (v != null && !v.isEmpty()) set.add(v);
            }
            return set;
        }

        private static void write(Game game, Set<String> set) {
            game.setComponentSourcesCsv(String.join(",", set));
        }

        static boolean has(Game game, String entry) {
            return parse(game).contains(entry);
        }

        static void add(Game game, Collection<String> entries) {
            Set<String> set = parse(game);
            set.addAll(entries);
            write(game, set);
        }

        static void remove(Game game, Collection<String> entries) {
            Set<String> set = parse(game);
            set.removeAll(entries);
            write(game, set);
        }
    }

    public static final class EnabledComponent {
        final ComponentSource source;
        final EnumSet<ComponentType> enabledTypes;

        EnabledComponent(ComponentSource source) {
            this.source = source;
            this.enabledTypes = EnumSet.noneOf(ComponentType.class);
        }

        String key() {
            return source.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            EnabledComponent that = (EnabledComponent) obj;
            return this.source == that.source;
        }

        @Override
        public int hashCode() {
            return Objects.hash(source);
        }

        static final Comparator<EnabledComponent> BY_DISPLAY = (a, b) ->
                compareSourcesForDisplay(a.source, b.source);

        public static final class Entry {
            final ComponentSource source;
            final ComponentType type;

            Entry(ComponentSource source, ComponentType type) {
                this.source = source;
                this.type = type;
            }

            public static Entry parse(String s) {
                if (s == null) return null;
                int idx = s.indexOf(":");
                if (idx <= 0) return null;
                ComponentSource cs = ComponentSource.fromString(s.substring(0, idx));
                ComponentType ct = ComponentType.fromString(s.substring(idx + 1));
                if (cs == null || ct == null) return null;
                return new Entry(cs, ct);
            }

            public static String key(ComponentSource source, ComponentType type) {
                return source.toString() + ":" + type.toString();
            }

            String key() {
                return key(source, type);
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (obj == null || getClass() != obj.getClass()) return false;
                Entry that = (Entry) obj;
                return this.source == that.source && this.type == that.type;
            }

            @Override
            public int hashCode() {
                return Objects.hash(source, type);
            }
        }

        public static List<EnabledComponent> fromGame(Game game) {
            Map<ComponentSource, EnabledComponent> bySource = new HashMap<>();
            String csv = game.getComponentSourcesCsv();
            if (csv != null && !csv.isBlank()) {
                for (String p : csv.split(",")) {
                    String v = p == null ? null : p.trim();
                    if (v == null || v.isEmpty()) continue;
                    Entry e = Entry.parse(v);
                    if (e == null) continue;
                    bySource.computeIfAbsent(e.source, EnabledComponent::new).enabledTypes.add(e.type);
                }
            }
            return new ArrayList<>(bySource.values());
        }

        public static void toGame(Game game, List<EnabledComponent> list) {
            List<String> out = new ArrayList<>();
            for (EnabledComponent ec : list) {
                for (ComponentType ct : ec.enabledTypes) {
                    out.add(Entry.key(ec.source, ct));
                }
            }
            game.setComponentSourcesCsv(String.join(",", out));
        }

        public static boolean has(List<EnabledComponent> list, ComponentSource cs, ComponentType ct) {
            for (EnabledComponent ec : list) {
                if (ec.source == cs && ec.enabledTypes.contains(ct)) return true;
            }
            return false;
        }

        public static void enableType(List<EnabledComponent> list, ComponentSource cs, ComponentType ct) {
            for (EnabledComponent ec : list) {
                if (ec.source == cs) { ec.enabledTypes.add(ct); return; }
            }
            EnabledComponent ec = new EnabledComponent(cs);
            ec.enabledTypes.add(ct);
            list.add(ec);
        }

        public static void disableType(List<EnabledComponent> list, ComponentSource cs, ComponentType ct) {
            for (EnabledComponent ec : list) {
                if (ec.source == cs) { ec.enabledTypes.remove(ct); return; }
            }
        }

        public static void enableAllForSource(List<EnabledComponent> list, ComponentSource cs, EnumSet<ComponentType> types) {
            EnabledComponent target = null;
            for (EnabledComponent ec : list) if (ec.source == cs) { target = ec; break; }
            if (target == null) { target = new EnabledComponent(cs); list.add(target); }
            target.enabledTypes.addAll(types);
        }

        public static void disableAllForSource(List<EnabledComponent> list, ComponentSource cs) {
            for (EnabledComponent ec : list) {
                if (ec.source == cs) { ec.enabledTypes.clear(); return; }
            }
        }

        public static List<String> allKeys(ComponentSource source, Collection<ComponentType> types) {
            List<String> out = new ArrayList<>();
            if (types == null) return out;
            for (ComponentType ct : types) out.add(Entry.key(source, ct));
            return out;
        }

        public static List<String> allKeys(Map<ComponentSource, ? extends Collection<ComponentType>> map) {
            List<String> out = new ArrayList<>();
            if (map == null || map.isEmpty()) return out;
            for (Map.Entry<ComponentSource, ? extends Collection<ComponentType>> e : map.entrySet()) {
                out.addAll(allKeys(e.getKey(), e.getValue()));
            }
            return out;
        }
    }

    private static final class EnabledComponentListHelper {}

    private static List<String> getDefaultOfficialPairs() {
        Map<ComponentSource, EnumSet<ComponentType>> map = new LinkedHashMap<>();
        map.put(ComponentSource.base, EnumSet.of(ComponentType.ACTION_CARD, ComponentType.AGENDA));
        map.put(ComponentSource.pok, EnumSet.of(ComponentType.ACTION_CARD, ComponentType.AGENDA, ComponentType.TECHNOLOGY, ComponentType.RELIC, ComponentType.EXPLORE));
        map.put(ComponentSource.codex1, EnumSet.of(ComponentType.ACTION_CARD, ComponentType.AGENDA, ComponentType.TECHNOLOGY));
        map.put(ComponentSource.codex2, EnumSet.of(ComponentType.ACTION_CARD, ComponentType.AGENDA, ComponentType.TECHNOLOGY, ComponentType.RELIC));
        map.put(ComponentSource.codex3, EnumSet.of(ComponentType.ACTION_CARD, ComponentType.AGENDA, ComponentType.TECHNOLOGY));
        map.put(ComponentSource.codex4, EnumSet.of(ComponentType.ACTION_CARD, ComponentType.AGENDA, ComponentType.TECHNOLOGY, ComponentType.RELIC));
        return EnabledComponent.allKeys(map);
    }
}
