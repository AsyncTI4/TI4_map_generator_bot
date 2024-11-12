package ti4.commands.user;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import ti4.helpers.Storage;
import ti4.message.BotLogger;

public class UserSettingsManager {

    private final Map<String, UserSettings> userSettingList = new HashMap<>();
    private final String userSettingsPath = Storage.getStoragePath() + File.separator + "user_settings";
    private static UserSettingsManager instance;

    private UserSettingsManager() {
    }

    public static UserSettingsManager getInstance() {
        if (instance == null) {
            instance = new UserSettingsManager();
        }
        return instance;
    }

    public void addUserSetting(UserSettings userSetting) {
        userSettingList.put(userSetting.getUserId(), userSetting);
    }

    public UserSettings getUserSettings(String userID) {
        UserSettings userSettings = userSettingList.get(userID);
        if (userSettings == null) {
            userSettings = new UserSettings(userID);
            userSettingList.put(userID, userSettings);
        }
        return userSettings;
    }

    public Map<String, UserSettings> getAllUserSettings() {
        return userSettingList;
    }

    public static void init() {
        getInstance().loadUserSettingsFromFolder();
    }

    public void loadUserSettingsFromFolder() {
        File folder = new File(userSettingsPath);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File[] listOfFiles = folder.listFiles();
        for (File file : listOfFiles) {
            if (file.isFile() && file.getName().endsWith(".json")) {
                try {
                    loadUserSetting(userSettingsPath + File.separator + file.getName());
                } catch (Exception e) {
                    BotLogger.log("Could not import JSON Objects from File: " + userSettingsPath + "/" + file.getName(), e);
                    e.printStackTrace();
                }
            }
        }
    }

    public void loadUserSetting(String settingFilePath) {
        ObjectMapper objectMapper = new ObjectMapper();
        String filePath = settingFilePath;
        if (filePath != null) {
            try {
                InputStream input = new FileInputStream(filePath);
                UserSettings userSetting = objectMapper.readValue(input, UserSettings.class);
                addUserSetting(userSetting);
            } catch (Exception e) {
                BotLogger.log("Could not import JSON Objects from File: " + settingFilePath, e);
            }
        }

    }

    public void saveUserSetting(UserSettings userSetting) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
        try {
            writer.writeValue(getSettingsFile(userSetting), userSetting);
        } catch (IOException e) {
            BotLogger.log("Error saving User Settings", e);
        }
    }

    private File getSettingsFile(UserSettings userSetting) {
        File folder = new File(userSettingsPath);
        File[] listOfFiles = folder.listFiles();
        for (File file : listOfFiles) {
            if (file.isFile() && file.getName().endsWith(".json") && userSetting.getUserId().equals(file.getName())) {
                return file;
            }
        }
        return new File(userSettingsPath + File.separator + userSetting.getUserId() + ".json");
    }
}
