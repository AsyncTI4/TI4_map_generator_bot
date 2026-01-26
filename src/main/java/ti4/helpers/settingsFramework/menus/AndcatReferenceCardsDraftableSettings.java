package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.helpers.settingsFramework.settings.IntegerSetting;
import ti4.helpers.settingsFramework.settings.ListSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import ti4.service.draft.draftables.AndcatReferenceCardsDraftable.ReferenceCardPackage;

@Getter
@JsonIgnoreProperties("messageId")
public class AndcatReferenceCardsDraftableSettings extends SettingsMenu {

    // Setting
    private final IntegerSetting numPackages;
    private final ListSetting<FactionModel> banFactions;
    // This is handled fully manually as there's a lot of validation to do
    private String presetPackages;

    @JsonIgnore
    private List<ReferenceCardPackage> parsedPackages;

    private static final String MENU_ID = "dsAndcatRefPackage";

    public AndcatReferenceCardsDraftableSettings(Game game, JsonNode json, DraftSystemSettings parent) {
        super(MENU_ID, "Reference Card Package Settings", "Control reference card package options.", parent);

        int players = parent.getPlayerUserIds().size();
        numPackages = new IntegerSetting("#Packages", "Number of Packages", players, 2, 9, 1);
        // Initialize values & keys for ban/priority factions
        Set<String> empty = new HashSet<>();
        Set<Entry<String, FactionModel>> allFactions = new HashSet<>();
        banFactions = new ListSetting<>(
                "BanFactionCards", "Banned faction cards", "Ban faction", "Unban faction", allFactions, empty, empty);

        // Emojis
        banFactions.setGetEmoji(FactionModel::getFactionEmoji);

        // Other Initialization
        banFactions.setShow(FactionModel::getAlias);

        // Finish initializing transient settings here
        updateTransientSettings();

        // Load JSON if applicable
        if (!(json == null
                || !json.has("menuId")
                || !MENU_ID.equals(json.get("menuId").asText("")))) {
            numPackages.initialize(json.get("numPackages"));
            banFactions.initialize(json.get("banFactions"));

            if (json.has("presetPackages")) {
                setPresetPackages(json.get("presetPackages").asText(null));
            }
        }
    }

    @Override
    public List<SettingInterface> settings() {
        List<SettingInterface> ls = new ArrayList<>();
        if (presetPackages != null) {
            return ls;
        }
        ls.add(numPackages);
        ls.add(banFactions);
        return ls;
    }

    @Override
    public List<Button> specialButtons() {
        String idPrefix = menuAction + "_" + navId() + "_";
        ArrayList<Button> buttons = new ArrayList<>(super.specialButtons());
        buttons.add(Buttons.blue(idPrefix + "presetPackages~MDL", "Use preset packages"));
        return buttons;
    }

    @Override
    protected void updateTransientSettings() {
        if (parent instanceof DraftSystemSettings dss) {
            List<ComponentSource> sources = dss.getSourceSettings().getFactionSources();
            Map<String, FactionModel> allFactions = Mapper.getFactionsValues().stream()
                    .filter(model -> sources.contains(model.getSource()))
                    .filter(model -> model.getPriorityNumber() != null)
                    .filter(model -> !model.getAlias().contains("keleres")
                            || "keleresm".equals(model.getAlias())) // Limit the pool to only 1 keleres flavor
                    .collect(Collectors.toMap(FactionModel::getAlias, f -> f));
            banFactions.setAllValues(allFactions);
        }
    }

    @Override
    public String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        String error =
                switch (action) {
                    case "presetPackages~MDL" -> getPresetPackagesFromUser(event);
                    case "presetPackages" -> setPresetPackages(event);
                    default -> null;
                };

        return (error == null ? "success" : error);
    }

    @Override
    public String menuSummaryString(String lastSettingTouched) {
        if (presetPackages == null) {
            return super.menuSummaryString(lastSettingTouched);
        }

        StringBuilder sb = new StringBuilder("# **__").append(menuName).append(":__**");
        for (String line : description) sb.append("\n- *").append(line).append("*");
        sb.append("\n");

        int pad = enabledSettings().stream()
                .map(x -> x.getName().length())
                .max(Comparator.comparingInt(x -> x))
                .orElse(15);
        for (SettingInterface setting : enabledSettings()) {
            sb.append("> ");
            sb.append(setting.longSummary(pad, lastSettingTouched));
            sb.append("\n");
        }
        sb.append("> Using preset packages: ").append(presetPackages).append("\n");
        if (!enabledSettings().isEmpty()) sb.append("\n"); // extra line for formatting

        if (!categories().isEmpty()) {
            List<String> catStrings = new ArrayList<>();
            for (SettingsMenu cat : categories()) {
                catStrings.add(cat.shortSummaryString(false));
            }
            String catStr = String.join("\n\n", catStrings);
            if (sb.length() + catStr.length() > 1999) {
                List<String> shorterCatStrings = new ArrayList<>();
                for (SettingsMenu cat : categories()) {
                    shorterCatStrings.add(cat.shortSummaryString(true));
                }
                catStr = String.join("\n\n", shorterCatStrings);
                if (sb.length() + catStr.length() > 1999) catStr = ""; // give up
            }
            sb.append(catStr);
        }
        return sb.toString();
    }

    @Override
    public String shortSummaryString(boolean descrOnly) {
        StringBuilder sb = new StringBuilder("**__" + menuName + ":__**");
        if (presetPackages != null) {
            sb.append("\n> Using preset packages: ").append(presetPackages);
            return sb.toString();
        }
        return super.shortSummaryString(descrOnly);
    }

    @Override
    protected String resetSettings() {
        presetPackages = null;
        parsedPackages = null;
        return super.resetSettings();
    }

    private String getPresetPackagesFromUser(GenericInteractionCreateEvent event) {
        String modalId = menuAction + "_" + navId() + "_presetPackages";
        TextInput packagesString = TextInput.create("packageStrings", TextInputStyle.PARAGRAPH)
                .setPlaceholder(
                        "Comma separated faction IDs, packages separated by semi-colons. e.g. arborec,letnev,sol;...")
                .setMinLength(1)
                .setRequired(true)
                .build();
        Modal modal = Modal.create(modalId, "Enter package info")
                .addComponents(Label.of("Faction packages", packagesString))
                .build();
        if (event instanceof ButtonInteractionEvent buttonEvent) {
            buttonEvent.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
            return null;
        }
        return "Unknown Event";
    }

    private String setPresetPackages(GenericInteractionCreateEvent event) {
        if (event instanceof ModalInteractionEvent modalEvent) {
            ModalMapping packagesMapping = modalEvent.getValue("packageStrings");
            if (packagesMapping == null) {
                return "No package string provided.";
            }
            String packages = packagesMapping.getAsString();
            String error = setPresetPackages(packages);
            if (error != null) {
                MessageHelper.sendEphemeralMessageToEventChannel(modalEvent, error);
            }
            return null;
        }
        return "Unknown Event";
    }

    public String setPresetPackages(String packagesString) {
        if (packagesString == null || packagesString.isEmpty()) {
            return null;
        }

        int players = 6;
        if (parent instanceof DraftSystemSettings dss) {
            players = dss.getPlayerUserIds().size();
        }
        presetPackages = packagesString;

        parsedPackages = new ArrayList<>();
        StringTokenizer packageTokens = new StringTokenizer(packagesString, ";");
        int packageKey = 1;
        while (packageTokens.hasMoreTokens()) {
            String packageToken = packageTokens.nextToken().trim();
            String[] factionIds = packageToken.split(",");
            if (factionIds.length != 3) {
                presetPackages = null;
                parsedPackages = null;
                return "Each package must contain exactly 3 faction IDs.";
            }
            List<String> factionsInPackage = new ArrayList<>();
            for (String factionIdStr : factionIds) {
                String trimmedId = factionIdStr.trim();
                String faction = parseFactionToAlias(trimmedId);
                if (faction == null) {
                    presetPackages = null;
                    parsedPackages = null;
                    return "Invalid faction alias: " + trimmedId;
                }
                factionsInPackage.add(faction);
            }
            ReferenceCardPackage refPackage =
                    new ReferenceCardPackage(packageKey, factionsInPackage, null, null, null, null);
            packageKey++;
            parsedPackages.add(refPackage);
        }

        if (parsedPackages.size() < players) {
            presetPackages = null;
            parsedPackages = null;
            return "Not enough packages for the number of players.";
        }
        return null;
    }

    private String parseFactionToAlias(String inputFaction) {
        FactionModel faction = Mapper.getFaction(inputFaction);
        if (faction != null) {
            return faction.getAlias();
        }

        if (inputFaction.contains("keleres")) {
            return "keleresm";
        }

        // Try other matching strategies

        return null;
    }
}
