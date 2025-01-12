package io.tapdata.observable.logging;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Dexter
 */
public enum LogLevel {
    TRACE("TRACE", 1),
    DEBUG("DEBUG", 2),
    INFO("INFO", 3),
    WARN("WARN", 4),
    ERROR("ERROR", 5),
    FATAL("FATAL", 6),
    ;

    private static final Map<String, LogLevel> LOG_LEVEL_MAP = new HashMap<>();
    static {
        LOG_LEVEL_MAP.put(TRACE.level, TRACE);
        LOG_LEVEL_MAP.put(DEBUG.level, DEBUG);
        LOG_LEVEL_MAP.put(INFO.level, INFO);
        LOG_LEVEL_MAP.put(WARN.level, WARN);
        LOG_LEVEL_MAP.put(ERROR.level, ERROR);
        LOG_LEVEL_MAP.put(FATAL.level, FATAL);
    };


    private final String level;
    private final Integer value;

    LogLevel(String level, Integer value) {
        this.level = level;
        this.value = value;
    }

    public static LogLevel getLogLevel(String level) {
        if (null == level || !LOG_LEVEL_MAP.containsKey(level.toUpperCase())) {
            throw new RuntimeException("Invalid log level, should be in TRACE, DEBUG, INFO, WARN, ERROR and FATAL");
        }

        return LOG_LEVEL_MAP.get(level.toUpperCase());
    }

    public static boolean lt(String level1, String level2) {
        return getLogLevel(level1).value < getLogLevel(level2).value;
    }


    public String getLevel() {
        return level;
    }

    public Integer getValue() {
        return value;
    }

}
