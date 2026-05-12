package com.monitor.call.infrastructure.config;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

public class LocalTimeDeserializer extends StdDeserializer<LocalTime> {

    public static final LocalTimeDeserializer INSTANCE = new LocalTimeDeserializer();

    private static final DateTimeFormatter FLEXIBLE = new DateTimeFormatterBuilder()
            .appendPattern("HH:mm")
            .optionalStart()
            .appendPattern(":ss")
            .optionalEnd()
            .toFormatter();

    public LocalTimeDeserializer() {
        super(LocalTime.class);
    }

    @Override
    public LocalTime deserialize(JsonParser p, DeserializationContext ctxt) {
        String value = p.getText().trim();
        if (value.isBlank()) return null;
        return LocalTime.parse(value, FLEXIBLE);
    }
}
