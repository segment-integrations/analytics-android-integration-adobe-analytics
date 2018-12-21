package com.segment.analytics.android.integrations.adobeanalytics;

import com.segment.analytics.Properties;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.TrackPayload;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class ContextDataConfigurationTest {

    @Test
    public void initialize() {
        Map<String, String> contextDataVariables = new HashMap<>();
        contextDataVariables.put("field", "prop");

        ValueMap settings = new ValueMap();
        settings.putValue("contextValues", contextDataVariables);
        settings.putValue("customDataPrefix", "myapp.");

        ContextDataConfiguration config = new ContextDataConfiguration(settings);

        Assert.assertEquals("myapp.", config.getPrefix());
        Assert.assertEquals(contextDataVariables, config.getContextDataVariables());
    }

    @Test
    public void searchFields() {
        ContextDataConfiguration config = new ContextDataConfiguration(new ValueMap());

        TrackPayload payload = new TrackPayload.Builder()
                .event("event")
                .anonymousId("test-user")
                .properties(new Properties()
                        .putValue("field1", "a")
                        .putValue("field2", "b")).build();

        Assert.assertEquals("a", config.searchValue("field1", payload));
        Assert.assertEquals("b", config.searchValue("field2", payload));

    }

    @Test
    public void searchNestedFields() {
        ContextDataConfiguration config = new ContextDataConfiguration(new ValueMap());

        TrackPayload payload = new TrackPayload.Builder()
                .event("event")
                .anonymousId("test-user")
                .properties(new Properties()
                        .putValue("field1", "a")
                        .putValue("field2", new ValueMap().putValue("id", "1"))).build();

        Assert.assertEquals("a", config.searchValue("field1", payload));
        Assert.assertEquals("1", config.searchValue("field2.id", payload));

    }

    @Test
    public void searchParentFields() {
        ContextDataConfiguration config = new ContextDataConfiguration(new ValueMap());

        TrackPayload payload = new TrackPayload.Builder()
                .event("event")
                .anonymousId("test-user")
                .context(new ValueMap().putValue("library", "android"))
                .properties(new Properties()
                        .putValue("field1", "a")
                        .putValue("field2", new ValueMap().putValue("id", "1"))).build();

        Assert.assertEquals("test-user", config.searchValue(".anonymousId", payload));
        Assert.assertEquals("android", config.searchValue(".context.library", payload));

    }

    @Test
    public void searchInvalidFields() {
        ContextDataConfiguration config = new ContextDataConfiguration(new ValueMap());

        TrackPayload payload = new TrackPayload.Builder()
                .event("event")
                .anonymousId("test-user")
                .context(new ValueMap().putValue("library", "android"))
                .properties(new Properties()
                        .putValue("field1", "a")
                        .putValue("field2", new ValueMap().putValue("id", "1"))).build();


        for (String invalidField : new String[]{"..an..onymousId", ".context.library.    ."}) {
            IllegalArgumentException e = null;
            try {
                config.searchValue(invalidField, payload);
            } catch (IllegalArgumentException ex) {
                e = ex;
            }

            Assert.assertNotNull(e);
        }

    }

    @Test
    public void searchMissingFields() {
        ContextDataConfiguration config = new ContextDataConfiguration(new ValueMap());

        TrackPayload payload = new TrackPayload.Builder()
                .event("event")
                .anonymousId("test-user")
                .context(new ValueMap().putValue("library", "android"))
                .properties(new Properties()
                        .putValue("field1", "a")
                        .putValue("field2", new ValueMap().putValue("id", "1"))
                        .putValue("field4", 3)).build();


        for (String missingField : new String[]{".context.device", "field3", "field4.id"}) {
            Assert.assertNull(config.searchValue(missingField, payload));
        }

    }
}
