package ti4.helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import ti4.ResourceHelper;

public class LoggerHandler {
    private static Logger logger;
    private static LoggerHandler loggerHandler;

    private LoggerHandler() {
      String logFilePropertiesPath = ResourceHelper.getInstance().getDataFile("logging.properties");

        try (InputStream stream = new FileInputStream(logFilePropertiesPath)) {
            LogManager.getLogManager().readConfiguration(stream);
            logger = Logger.getLogger(LoggerHandler.class.getName());
            File logFilePath = Storage.getLoggerFile();
            System.out.println("Initializing log file to  " + logFilePath.getAbsolutePath());
            FileHandler fileHandler = new FileHandler(logFilePath.getAbsolutePath(), true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
        } catch (IOException e) {
            // deliberately not using the logger here, b/c if we get here we likely don't have a functioning logger
            System.err.println("Could not init log file");
            e.printStackTrace();
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
