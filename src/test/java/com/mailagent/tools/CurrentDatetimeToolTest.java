package com.mailagent.tools;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;

public class CurrentDatetimeToolTest {

    @Test
    public void returnsIsoInstantFromInjectedClock() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-08-13T10:15:30Z"), ZoneOffset.UTC);
        CurrentDatetimeTool tool = new CurrentDatetimeTool(fixedClock);

        String result = tool.execute("{}");

        assertEquals("2026-08-13T10:15:30Z", result);
    }

    @Test
    public void nameAndDescriptionArePresent() {
        CurrentDatetimeTool tool = new CurrentDatetimeTool(Clock.systemUTC());

        assertEquals("current_datetime", tool.name());
        assertEquals(false, tool.description().isEmpty());
    }
}
