package com.danjvan.mgui.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MGuiLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("MGUI");
    
    public static void info(String message) {
        LOGGER.info(message);
    }
    
    public static void warn(String message) {
        LOGGER.warn(message);
    }
    
    public static void error(String message) {
        LOGGER.error(message);
    }
    
    public static void debug(String message) {
        LOGGER.debug(message);
    }
}
