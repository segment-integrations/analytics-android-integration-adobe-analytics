package com.segment.analytics.android.integrations.adobeanalytics;

import android.app.Activity;
import android.os.Bundle;
import com.adobe.mobile.Analytics;
import com.adobe.mobile.Config;

import com.segment.analytics.Traits;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.IdentifyPayload;
import com.segment.analytics.Properties;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.ScreenPayload;
import com.segment.analytics.integrations.TrackPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.robolectric.RobolectricTestRunner;

import static com.segment.analytics.Analytics.LogLevel.NONE;
import static com.segment.analytics.Analytics.LogLevel.VERBOSE;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

@RunWith(RobolectricTestRunner.class)
@PrepareForTest({Analytics.class, Config.class})
@org.robolectric.annotation.Config(constants = BuildConfig.class)
public class AdobeTest {

  @Rule public PowerMockRule rule = new PowerMockRule();
  private AdobeIntegration integration;

  @Before
  public void setUp() {
    PowerMockito.mockStatic(Config.class);
    PowerMockito.mockStatic(Analytics.class);
    integration = new AdobeIntegration(new ValueMap(), Logger.with(NONE));
  }

  @Test
  public void factory() {
    assertTrue(AdobeIntegration.FACTORY.key().equals("Adobe Analytics"));
  }

  @Test
  public void initialize() {
    integration = new AdobeIntegration(new ValueMap()
        .putValue("eventsV2", new HashMap<String, Object>())
        .putValue("contextValues", new HashMap<String, Object>())
        .putValue("lVarsV2", new ArrayList()),
      Logger.with(VERBOSE));

    assertTrue(integration.eventsV2.equals(new HashMap<String, Object>()));
    assertTrue(integration.contextValues.equals(new HashMap<String, Object>()));
    assertTrue(integration.lVarsV2.equals(new ArrayList()));
  }

  @Test
  public void initializeWithDefaultArguments() {
    // all default arguments have not yet been defined
  }

  @Test public void activityCreate() {
    Activity activity = mock(Activity.class);
    Bundle savedInstanceState = mock (Bundle.class);
    integration.onActivityCreated(activity, savedInstanceState);

    verifyStatic();
    Config.setContext(activity.getApplicationContext());
  }

  @Test public void activityPause() {
    Activity activity = mock(Activity.class);
    integration.onActivityPaused(activity);

    verifyStatic();
    Config.pauseCollectingLifecycleData();
  }

  @Test public void activityResume() {
    Activity activity = mock(Activity.class);
    integration.onActivityResumed(activity);

    verifyStatic();
    Config.collectLifecycleData(activity);
  }

  @Test
  public void track() {
    integration.eventsV2 = new HashMap<>();
    integration.eventsV2.put("Testing Event", "Adobe Testing Event");

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Testing Event")
        .build()
    );

    verifyStatic();
    Analytics.trackAction("Adobe Testing Event", null);
  }

  @Test
  public void trackWithContextValues() {
    integration.eventsV2 = new HashMap<>();
    integration.eventsV2.put("Testing Event", "Adobe Testing Event");
    integration.contextValues = new HashMap<>();
    integration.contextValues.put("testing", "myapp.testing.Testing");

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Testing Event")
        .properties(new Properties()
            .putValue("testing", "testing value"))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("myapp.testing.Testing", "testing value");
    verifyStatic();
    Analytics.trackAction("Adobe Testing Event", contextData);
  }

  @Test
  public void trackWithlVarsV2() {
    integration.eventsV2 = new HashMap<>();
    integration.eventsV2.put("Testing Event", "Adobe Testing Event");
    integration.lVarsV2 = new ArrayList<>();

    ValueMap setting = new ValueMap();
    Map<String, String> values = new HashMap<>();
    values.put("property", "filters");
    values.put("lVar", "myapp.filters");
    values.put("delimiter", ",");
    setting.put("value", values);

    integration.lVarsV2.add(setting);

    List<Object> list = new ArrayList<>();
    list.add("item1");
    list.add("item2");

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Testing Event")
        .properties(new Properties()
            .putValue("filters", list))
        .build()
    );

    String joinedlVarsV2 = "item1,item2";
    Map<String, Object> contextData = new HashMap<>();
    contextData.put("myapp.filters", joinedlVarsV2);
    verifyStatic();
    Analytics.trackAction("Adobe Testing Event", contextData);
  }

  @Test
  public void identify() {
    integration.identify(new IdentifyPayload.Builder()
        .userId("123")
        .traits(new Traits())
    .build());

    verifyStatic();
    Config.setUserIdentifier("123");
  }

  @Test
  public void identifyWithNoUserId() {
    integration.identify(new IdentifyPayload.Builder()
        .userId("123")
        .traits(new Traits())
        .build());

    verifyStatic(Mockito.times(0));
    Config.setUserIdentifier(null);
  }

  @Test
  public void screen() {
    integration.screen(new ScreenPayload.Builder()
        .userId("123")
        .name("Viewed a Screen")
        .build()
    );

    verifyStatic();
    Analytics.trackState("Viewed a Screen", null);
  }

  @Test
  public void screenWithContextValues() {
    integration.contextValues = new HashMap<>();
    integration.contextValues.put("testing", "myapp.testing.Testing");

    integration.screen(new ScreenPayload.Builder()
        .userId("123")
        .name("Viewed a Screen")
        .properties(new Properties()
            .putValue("testing", "testing value"))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("myapp.testing.Testing", "testing value");
    verifyStatic();
    Analytics.trackState("Viewed a Screen", contextData);
  }

  @Test
  public void screenWithlVarsV2() {
    integration.eventsV2 = new HashMap<>();
    integration.eventsV2.put("Viewed a Screen", "myapp.screen");
    integration.lVarsV2 = new ArrayList<>();

    ValueMap setting = new ValueMap();
    Map<String, String> values = new HashMap<>();
    values.put("property", "filters");
    values.put("lVar", "myapp.filters");
    values.put("delimiter", ",");
    setting.put("value", values);

    integration.lVarsV2.add(setting);

    List<Object> list = new ArrayList<>();
    list.add("item1");
    list.add("item2");

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Viewed a Screen")
        .properties(new Properties()
            .putValue("filters", list))
      .build()
    );

    String joinedlVarsV2 = "item1,item2";
    Map<String, Object> contextData = new HashMap<>();
    contextData.put("myapp.filters", joinedlVarsV2);
    verifyStatic();
    Analytics.trackAction("myapp.screen", contextData);
  }

  @Test
  public void group() {
  }

  @Test
  public void flush() {
    integration.flush();
    verifyStatic();
    Analytics.sendQueuedHits();
  }

  @Test
  public void reset() {
    integration.reset();
    verifyStatic();
    Config.setUserIdentifier(null);
  }

  // json matcher
  public static JSONObject jsonEq(JSONObject expected) {
    return argThat(new JSONObjectMatcher(expected));
  }

  private static class JSONObjectMatcher extends TypeSafeMatcher<JSONObject> {

    private final JSONObject expected;

    private JSONObjectMatcher(JSONObject expected) {
      this.expected = expected;
    }

    @Override
    public boolean matchesSafely(JSONObject jsonObject) {
      // todo: this relies on having the same order
      return expected.toString().equals(jsonObject.toString());
    }

    @Override
    public void describeTo(Description description) {
      description.appendText(expected.toString());
    }
  }
}
