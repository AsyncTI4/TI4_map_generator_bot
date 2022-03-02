package ti4.helpers;

import ti4.ResourceHelper;
import ti4.map.Storage;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.*;

public class LoggerHandler {
    private static Logger logger = null;
    private static LoggerHandler loggerHandler = null;

    private LoggerHandler() {
        //noinspection ConstantConditions
        try (InputStream stream = new FileInputStream(ResourceHelper.getInstance().getInfoFile("logging.properties"))) {
            LogManager.getLogManager().readConfiguration(stream);
            logger = Logger.getLogger(LoggerHandler.class.getName());
            @SuppressWarnings("ConstantConditions")
            FileHandler fileHandler = new FileHandler(Storage.getLoggerFile().toPath().toString(), true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not init log file");
        }
    }

    private static LoggerHandler getInstance() {
        if (loggerHandler == null) {
            loggerHandler = new LoggerHandler();
        }
        return loggerHandler;
    }

    private Logger getLogger() {
        return logger;
    }

    public static void logError(String text) {
        getInstance().getLogger().log(Level.SEVERE, text);
    }

    public static void log(String text) {
        getInstance().getLogger().log(Level.WARNING, text);
    }

    public static void logInfo(String text) {
        getInstance().getLogger().log(Level.INFO, text);
    }

    public static void log(String text, Throwable throwable) {
        getInstance().getLogger().log(Level.WARNING, text, throwable);
    }

    public static void logWarning(String text) {
        getInstance().getLogger().log(Level.WARNING, text);
    }

    public static void logError(String text, Throwable throwable) {
        getInstance().getLogger().log(Level.SEVERE, text, throwable);
    }

    public static void logWarning(String text, Throwable throwable) {
        getInstance().getLogger().log(Level.WARNING, text, throwable);
    }
}
