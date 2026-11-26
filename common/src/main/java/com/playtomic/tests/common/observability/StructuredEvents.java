package com.playtomic.tests.common.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

/** One diagnostic logger per service; event names must come from its fixed vocabulary. */
public final class StructuredEvents {
    private final Logger logger;

    public StructuredEvents(String service) {
        logger = LoggerFactory.getLogger(service + ".events");
    }

    public LoggingEventBuilder fields(Level level, String event) {
        return logger.atLevel(level).setMessage(event).addKeyValue("event", event);
    }
}

