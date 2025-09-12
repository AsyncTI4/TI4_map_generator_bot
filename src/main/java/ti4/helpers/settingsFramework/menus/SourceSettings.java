package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Getter;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.settingsFramework.settings.BooleanSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.map.Game;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.SourceEmojis;

// This is a sub-menu
@Getter
@JsonIgnoreProperties("messageId")
public class SourceSettings extends SettingsMenu {
    private static final Pattern TOG = Pattern.compile("tog");
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Settings & Submenus
    // ---------------------------------------------------------------------------------------------------------------------------------
    private final BooleanSetting base;
    private final BooleanSetting pok;
    private final BooleanSetting codexes;
    private final BooleanSetting discoStars;
    private final BooleanSetting unchartedSpace;
    private final BooleanSetting absol;
    private final BooleanSetting ignis;
    private final BooleanSetting eronous;
    private final BooleanSetting actionCardDeck2;
    private final BooleanSetting teDemo;

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Constructor & Initialization
    // ---------------------------------------------------------------------------------------------------------------------------------
    SourceSettings(Game game, JsonNode json, SettingsMenu parent) {
        super(
                "source",
                "Expansions and Homebrew",
                "Adjust various settings related to expansions and homebrew that you wish to use",
                parent);

        // Initialize Settings to default values
        base = new BooleanSetting("BaseGame", "Base Game", true);
        pok = new BooleanSetting("PoK", "Prophecy of Kings", true);
        codexes = new BooleanSetting("Codexes", "Codex 1-4", true);
        discoStars = new BooleanSetting("DiscoStars", "DS Factions", game.isDiscordantStarsMode());
        teDemo = new BooleanSetting("ThundersEdge", "Thunders Edge Demo", game.isThundersEdge());
        unchartedSpace = new BooleanSetting("UnchartSpace", "Uncharted Space", game.isUnchartedSpaceStuff());
        absol = new BooleanSetting("Absol", "Absol Mod", game.isAbsolMode());
        ignis = new BooleanSetting(
                "Ignis",
                "Ignis Aurora Mod",
                game.getTechnologyDeckID().toLowerCase().contains("baldrick"));
        eronous = new BooleanSetting("Eronous", "Eronous Tiles", false);
        actionCardDeck2 = new BooleanSetting(
                "ActionCardDeck2", "Action Card Deck 2", "action_deck_2".equalsIgnoreCase(game.getAcDeckID()));
        // Emojis
        base.setEmoji(SourceEmojis.TI4BaseGame);
        pok.setEmoji(SourceEmojis.TI4PoK);
        codexes.setEmoji(FactionEmojis.Keleres);
        discoStars.setEmoji(SourceEmojis.DiscordantStars);
        unchartedSpace.setEmoji(SourceEmojis.DiscordantStars);
        absol.setEmoji(SourceEmojis.Absol);
        eronous.setEmoji(SourceEmojis.Eronous);
        actionCardDeck2.setEmoji(SourceEmojis.ActionDeck2);

        // Other Initialization
        // miltymod.setExtraInfo("NOTE: this is NOT \"milty draft\", this is a homebrew mod that replaces components in
        // the game");

        // Get the correct JSON node for initialization if applicable.
        // Add additional names here to support new generated JSON as needed.
        if (json != null && json.has("sourceSettings")) json = json.get("sourceSettings");

        // Verify this is the correct JSON node and continue initialization
        List<String> historicIDs = new ArrayList<>(List.of("source"));
        if (json != null
                && json.has("menuId")
                && historicIDs.contains(json.get("menuId").asText(""))) {
            base.initialize(json.get("base"));
            pok.initialize(json.get("pok"));
            codexes.initialize(json.get("codexes"));
            discoStars.initialize(json.get("discoStars"));
            unchartedSpace.initialize(json.get("unchartedSpace"));
            absol.initialize(json.get("absol"));
            eronous.initialize(json.get("eronous"));
            actionCardDeck2.initialize(json.get("actionCardDeck2"));
        }
        base.setEditable(false);
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Overridden Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    @Override
    public List<SettingInterface> settings() {
        List<SettingInterface> ls = new ArrayList<>();
        // Add settings to the list. Any marked 'disabled' will not show
        ls.add(base);
        ls.add(pok);
        ls.add(codexes);
        ls.add(teDemo);
        ls.add(discoStars);
        ls.add(unchartedSpace);
        ls.add(absol);
        ls.add(ignis);
        ls.add(eronous);
        ls.add(actionCardDeck2);
        return ls;
    }

    @Override
    protected String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        if (action.startsWith("tog") && event instanceof ButtonInteractionEvent bEvent) {
            String setting = TOG.matcher(action).replaceFirst("");
            afterChangeSources(bEvent, setting);
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Specific Implementation / Helpers
    // ---------------------------------------------------------------------------------------------------------------------------------
    @JsonIgnore
    public List<ComponentSource> getTileSources() {
        List<ComponentSource> sources = new ArrayList<>();
        if (base.isVal()) sources.add(ComponentSource.base);
        if (pok.isVal()) sources.add(ComponentSource.pok);
        if (codexes.isVal())
            sources.addAll(List.of(
                    ComponentSource.codex1, ComponentSource.codex2, ComponentSource.codex3, ComponentSource.codex4));
        if (unchartedSpace.isVal() || discoStars.isVal()) sources.add(ComponentSource.uncharted_space);
        if (absol.isVal()) sources.add(ComponentSource.absol);
        if (eronous.isVal()) sources.add(ComponentSource.eronous);
        return sources;
    }

    @JsonIgnore
    public List<ComponentSource> getFactionSources() {
        List<ComponentSource> sources = new ArrayList<>();
        if (base.isVal()) sources.add(ComponentSource.base);
        if (pok.isVal()) sources.add(ComponentSource.pok);
        if (codexes.isVal())
            sources.addAll(List.of(
                    ComponentSource.codex1, ComponentSource.codex2, ComponentSource.codex3, ComponentSource.codex4));
        if (discoStars.isVal()) sources.add(ComponentSource.ds);
        if (absol.isVal()) sources.add(ComponentSource.absol);
        if (eronous.isVal()) sources.add(ComponentSource.eronous);
        if (ignis.isVal()) sources.add(ComponentSource.ignis_aurora);
        return sources;
    }

    private void afterChangeSources(ButtonInteractionEvent event, String setting) {
        Game game = null;
        DeckSettings decks = null;
        if (parent instanceof MiltySettings ms) {
            game = ms.getGame();
            decks = ms.getGameSettings().getDecks();
        }
        if (game == null || decks == null) return;

        switch (setting) {
            case "PoK" -> {
                game.setBaseGameMode(!pok.isVal());
                event.getHook()
                        .sendMessage(
                                "This setting doesn't fully change the decks, please resolve manually after starting the draft if you actually want to play base game mode. You can ping Bothelper for assistance.")
                        .setEphemeral(true)
                        .queue();
            }
            case "Codexes" ->
                event.getHook()
                        .sendMessage("This setting doesn't really do much. It only disables Keleres.")
                        .setEphemeral(true)
                        .queue();
            case "DiscoStars" ->
                event.getHook()
                        .sendMessage(
                                "This setting only controls factions. If you want technologies, relics, explores, etc, you need to also enable **__Uncharted Space__**.")
                        .setEphemeral(true)
                        .queue();
            case "ThundersEdge" -> {
                event.getHook()
                        .sendMessage(
                                "This is only a demo of TE. Only the 6 factions that have revealed breakthroughs will be draftable. No Fracture.")
                        .setEphemeral(true)
                        .queue();
                game.setThundersEdge(true);
            }
            case "Ignis" -> {
                boolean ignis = this.ignis.isVal();

                // Decks with both
                String relic = ignis ? "relics_baldrick" : "relics_pok";
                String techs = ignis ? "techs_baldrick" : "techs_pok_c4";

                // Decks for ABSOL
                String agenda = ignis ? "agendas_baldrick" : "agendas_pok";

                String sc = ignis ? "ignis_aurora" : "pok";

                game.setStrategyCardSet(sc);
                game.setEventDeckID("events_baldrick");
                // set 'em up
                decks.getRelics().setChosenKey(relic);
                decks.getTechs().setChosenKey(techs);
                decks.getAgendas().setChosenKey(agenda);

                String absolDS = "Reset your decks to include all of the Ignis cards.";
                String pokStr = "Reset your decks to include only PoK cards.";
                event.getHook()
                        .sendMessage((ignis) ? absolDS : pokStr)
                        .setEphemeral(true)
                        .queue();
            }
            case "UnchartSpace", "Absol", "ActionCardDeck2" -> {
                boolean abs = absol.isVal();
                boolean ds = unchartedSpace.isVal();
                boolean both = abs && ds;
                boolean acd2 = actionCardDeck2.isVal();

                // Decks with both
                String relic = both ? "relics_absol_ds" : (abs ? "relics_absol" : (ds ? "relics_ds" : "relics_pok"));
                String techs = both ? "techs_ds_absol" : (abs ? "techs_absol" : (ds ? "techs_ds" : "techs_pok_c4"));

                // Decks for ABSOL
                String agenda = abs ? "agendas_absol" : "agendas_pok";

                // Decks for Uncharted Space
                String explore = ds ? "explores_DS" : "explores_pok";
                String acs = acd2 ? "action_deck_2" : (ds ? "action_cards_ds" : "action_cards_pok");

                // set 'em up
                decks.getRelics().setChosenKey(relic);
                decks.getTechs().setChosenKey(techs);
                decks.getAgendas().setChosenKey(agenda);
                decks.getExplores().setChosenKey(explore);
                decks.getActionCards().setChosenKey(acs);

                var inclusions = new ArrayList<String>();
                if (abs) inclusions.add("Absol Mod");
                if (ds) inclusions.add("Uncharted Space");
                if (acd2) inclusions.add("Action Deck 2");
                String message = inclusions.isEmpty()
                        ? "Reset your decks to include only PoK cards."
                        : "Reset your decks to include all of the " + String.join(" and ", inclusions) + " cards.";
                event.getHook().sendMessage(message).setEphemeral(true).queue();
            }
            case "Eronous" -> {}
        }
    }
}
