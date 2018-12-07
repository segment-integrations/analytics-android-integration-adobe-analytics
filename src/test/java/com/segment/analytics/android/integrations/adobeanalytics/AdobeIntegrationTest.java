package com.segment.analytics.android.integrations.adobeanalytics;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import com.segment.analytics.Properties;
import com.segment.analytics.Traits;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.IdentifyPayload;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.ScreenPayload;
import com.segment.analytics.integrations.TrackPayload;
import com.segment.analytics.Analytics.LogLevel;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;


public class AdobeIntegrationTest {

  @Mock private com.segment.analytics.Analytics analytics;
  @Mock private Application application;
  @Mock private AdobeAnalyticsClient client;
  @Mock private VideoAnalytics videoAnalytics;
  @Mock private EcommerceAnalytics ecommerceAnalytics;
  private AdobeIntegration integration;
  
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    Mockito.when(analytics.getApplication()).thenReturn(application);
    Mockito.when(analytics.logger("Adobe Analytics")).thenReturn(Logger.with(LogLevel.VERBOSE));

    ValueMap settings = new ValueMap();
    settings.putValue("heartbeatTrackingServerUrl", "https://www.heartbeatTrackingServerURL.com/");

    integration = new AdobeIntegration(settings, videoAnalytics, ecommerceAnalytics, client, Logger.with(LogLevel.NONE));
  }

  @Test
  public void factory() {
    Assert.assertEquals(AdobeIntegration.FACTORY.key(), "Adobe Analytics");
  }

  @Test
  public void initialize() {
    ValueMap settings = new ValueMap();
    settings.putValue("eventsV2", new HashMap<String, Object>());
    settings.putValue("contextValues", new HashMap<String, Object>());
    settings.putValue("productIdentifier", "id");
    settings.putValue("adobeVerboseLogging", true);

    integration = new AdobeIntegration(settings, videoAnalytics, ecommerceAnalytics, client, Logger.with(LogLevel.VERBOSE));

    Assert.assertEquals(integration.getEventsMapping(), new HashMap<String, Object>());
    Assert.assertEquals(integration.getContextDataVariables(), new HashMap<String, Object>());
  }

  @Test
  public void activityCreate() {
    Activity activity = Mockito.mock(Activity.class);
    Bundle savedInstanceState = Mockito.mock(Bundle.class);
    integration.onActivityCreated(activity, savedInstanceState);

    Mockito.verify(client).setContext(activity.getApplicationContext());
  }

  @Test
  public void activityPause() {
    Activity activity = Mockito.mock(Activity.class);
    integration.onActivityPaused(activity);

    Mockito.verify(client).pauseCollectingLifecycleData();
  }

  @Test
  public void activityResume() {
    Activity activity = Mockito.mock(Activity.class);
    integration.onActivityResumed(activity);

    Mockito.verify(client).collectLifecycleData(activity);
  }

  @Test
  public void track() {
    Map<String, String> eventsMapping = new HashMap<>();
    eventsMapping.put("Testing Event", "Adobe Testing Event");
    integration.setEventsMapping(eventsMapping);

    TrackPayload payload = new TrackPayload.Builder()
            .userId("test-user")
            .event("Testing Event")
            .build();

    integration.track(payload);

    Mockito.verify(client).trackAction("Adobe Testing Event", null);
  }

  @Test
  public void trackVideoEvent() {
    TrackPayload payload = new TrackPayload.Builder()
            .userId("test-user")
            .event("VideoEvent Playback Started")
            .build();

    integration.track(payload);

    Mockito.verify(videoAnalytics).track(payload);
  }

  @Test
  public void trackEcommerceEvent() {
    TrackPayload payload = new TrackPayload.Builder()
            .userId("test-user")
            .event("Product Added")
            .build();

    integration.track(payload);

    Mockito.verify(ecommerceAnalytics).track(payload);
  }

  @Test
  public void trackWithContextValues() {
    Map<String, String> eventsMapping = new HashMap<>();
    eventsMapping.put("Testing Event", "Adobe Testing Event");
    integration.setEventsMapping(eventsMapping);

    Map<String, String> contextDataVariables = new HashMap<>();
    contextDataVariables.put("testing", "myapp.testing.Testing");
    integration.setContextDataVariables(contextDataVariables);

    TrackPayload payload = new TrackPayload.Builder()
            .userId("test-user")
            .event("Testing Event")
            .properties(new Properties().putValue("testing", "testing value"))
            .build();

    integration.track(payload);

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("myapp.testing.Testing", "testing value");
    Mockito.verify(client).trackAction("Adobe Testing Event", contextData);
  }

  @Test
  public void identify() {
    integration.identify(new IdentifyPayload.Builder()
        .userId("123")
        .traits(new Traits())
        .build());

    Mockito.verify(client).setUserIdentifier("123");
  }

  @Test
  public void screen() {

    ScreenPayload payload = new ScreenPayload.Builder()
            .userId("123")
            .name("Viewed a Screen")
            .build();

    integration.screen(payload);

    Mockito.verify(client).trackState("Viewed a Screen", null);
  }

  @Test
  public void screenWithContextValues() {
    Map<String, String> contextDataVariables = new HashMap<>();
    contextDataVariables.put("testing", "myapp.testing.Testing");
    integration.setContextDataVariables(contextDataVariables);

    ScreenPayload payload = new ScreenPayload.Builder()
            .userId("test-user")
            .name("Viewed a Screen")
            .properties(new Properties().putValue("testing", "testing value"))
            .build();

    integration.screen(payload);

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("myapp.testing.Testing", "testing value");
    Mockito.verify(client).trackState("Viewed a Screen", contextData);
  }

  @Test
  public void group() {
  }

  @Test
  public void flush() {
    integration.flush();
    Mockito.verify(client).flushQueue();
  }

  @Test
  public void reset() {
    integration.reset();
    Mockito.verify(client).setUserIdentifier(null);
  }

}
