package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Getter;
import ti4.helpers.settingsFramework.settings.IntegerSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.service.franken.FrankenDraftMode;
import tools.jackson.databind.JsonNode;

@Getter
@JsonIgnoreProperties("messageId")
class AuctionDraftSettings extends SettingsMenu {
    private static final String MENU_ID = "auctionDraft";

    private final IntegerSetting startingVotes = new IntegerSetting("StartingVotes", "Starting Votes", 30, 0, 100, 1);
    private final IntegerSetting startingBans = new IntegerSetting("StartingBans", "Starting Bans", 1, 0, 10, 1);
    private final IntegerSetting startingMulligans =
            new IntegerSetting("StartingMulligans", "Starting Mulligans", 1, 0, 10, 1);
    private final IntegerSetting extraComponents =
            new IntegerSetting("ExtraComponents", "Extra Pool Components", 2, 0, 20, 1);

    AuctionDraftSettings(JsonNode json, SettingsMenu parent) {
        super(
                MENU_ID,
                "Auction Draft Settings",
                "Configure private bids and nomination pools for Auction Draft.",
                parent);
        if (json != null && json.has("auctionDraftSettings")) json = json.get("auctionDraftSettings");
        if (json != null && json.has(MENU_ID)) json = json.get(MENU_ID);
        if (!FrankenSettings.isMenuJson(json, MENU_ID)) return;
        startingVotes.initialize(json.get("startingVotes"));
        startingBans.initialize(json.get("startingBans"));
        startingMulligans.initialize(json.get("startingMulligans"));
        extraComponents.initialize(json.get("extraComponents"));
    }

    @Override
    protected List<SettingInterface> settings() {
        if (!(parent instanceof FrankenSettings settings)
                || !FrankenDraftMode.AUCTION
                        .toString()
                        .equals(settings.getDraftMode().getValue())) {
            return List.of();
        }
        return List.of(startingVotes, startingBans, startingMulligans, extraComponents);
    }

    @Override
    public String menuSummaryString(String lastSettingTouched) {
        String summary = super.menuSummaryString(lastSettingTouched);
        if (parent instanceof FrankenSettings settings) settings.persistSettings();
        return summary;
    }
}
