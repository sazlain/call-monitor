package com.monitor.call.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalTimeDeserializerTest {

    private final LocalTimeDeserializer deserializer = LocalTimeDeserializer.INSTANCE;

    @Mock
    private JsonParser parser;

    @Mock
    private DeserializationContext ctxt;

    @ParameterizedTest
    @ValueSource(strings = {"16:34", "09:05", "23:59", "00:00"})
    void deserialize_HHmm_parsesHourAndMinuteCorrectly(String input) {
        when(parser.getText()).thenReturn(input);

        LocalTime result = deserializer.deserialize(parser, ctxt);

        String[] parts = input.split(":");
        assertThat(result).isNotNull();
        assertThat(result.getHour()).isEqualTo(Integer.parseInt(parts[0]));
        assertThat(result.getMinute()).isEqualTo(Integer.parseInt(parts[1]));
    }

    @Test
    void deserialize_HHmmss_parsesAllParts() {
        when(parser.getText()).thenReturn("14:30:45");

        LocalTime result = deserializer.deserialize(parser, ctxt);

        assertThat(result).isEqualTo(LocalTime.of(14, 30, 45));
    }

    @Test
    void deserialize_HHmmWithZeroSeconds_parsesCorrectly() {
        when(parser.getText()).thenReturn("14:30:00");

        LocalTime result = deserializer.deserialize(parser, ctxt);

        assertThat(result).isEqualTo(LocalTime.of(14, 30, 0));
    }

    @Test
    void deserialize_blankString_returnsNull() {
        when(parser.getText()).thenReturn("   ");

        LocalTime result = deserializer.deserialize(parser, ctxt);

        assertThat(result).isNull();
    }

    @Test
    void deserialize_emptyString_returnsNull() {
        when(parser.getText()).thenReturn("");

        LocalTime result = deserializer.deserialize(parser, ctxt);

        assertThat(result).isNull();
    }

    @Test
    void deserialize_withLeadingSpaces_trimsAndParses() {
        when(parser.getText()).thenReturn("  16:34  ");

        LocalTime result = deserializer.deserialize(parser, ctxt);

        assertThat(result).isEqualTo(LocalTime.of(16, 34));
    }

    @Test
    void instance_isSingleton() {
        assertThat(LocalTimeDeserializer.INSTANCE).isNotNull();
        assertThat(LocalTimeDeserializer.INSTANCE).isSameAs(deserializer);
    }

    @Test
    void deserialize_invalidFormat_throwsException() {
        when(parser.getText()).thenReturn("not-a-time");

        assertThatThrownBy(() -> deserializer.deserialize(parser, ctxt))
                .isInstanceOf(Exception.class);
    }
}
