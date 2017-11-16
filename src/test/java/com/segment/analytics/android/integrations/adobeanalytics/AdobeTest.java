package com.segment.analytics.android.integrations.adobeanalytics;

import android.app.Activity;
import android.os.Bundle;
import com.adobe.mobile.Analytics;
import com.adobe.mobile.Config;
import com.segment.analytics.Properties;
import com.segment.analytics.ValueMap;
import com.segment.analytics.core.tests.BuildConfig;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.test.TrackPayloadBuilder;
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
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.robolectric.RobolectricTestRunner;

import static com.segment.analytics.Analytics.LogLevel.NONE;
import static com.segment.analytics.Analytics.LogLevel.VERBOSE;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

@RunWith(RobolectricTestRunner.class)
@PrepareForTest({Analytics.class, Config.class})
@org.robolectric.annotation.Config(constants = BuildConfig.class, sdk = 18, manifest = org.robolectric.annotation.Config.NONE)
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
    assertThat(AdobeIntegration.FACTORY.key()).isEqualTo("Adobe Analytics");
  }

  @Test
  public void initialize() {
    integration = new AdobeIntegration(new ValueMap()
        .putValue("events", new HashMap<String, Object>())
        .putValue("contextValues", new HashMap<String, Object>())
        .putValue("lVars", new HashMap<String, Object>()),
      Logger.with(VERBOSE));

    assertThat(integration.eventsV2).isEqualTo(new HashMap<String, Object>());
    assertThat(integration.contextValues).isEqualTo(new HashMap<String, Object>());
    assertThat(integration.lVars).isEqualTo(new HashMap<String, Object>());
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

    integration.track(new TrackPayloadBuilder()
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

    integration.track(new TrackPayloadBuilder()
        .event("Testing Event")
        .properties(new Properties()
            .putValue("testing", "testing value"))
        .build()
    );

    verifyStatic();
    Map<String, Object> contextData = new HashMap<>();
    contextData.put("myapp.testing.Testing", "testing value");
    Analytics.trackAction("Adobe Testing Event", contextData);
  }

  @Test
  public void trackWithlVars() {
    integration.eventsV2 = new HashMap<>();
    integration.eventsV2.put("Testing Event", "Adobe Testing Event");
    integration.lVars = new HashMap<>();
    integration.lVars.put("testing lVars", "joinedString");

    List<Object> list = new ArrayList<>();
    list.add("item1");
    list.add("item2");

    integration.track(new TrackPayloadBuilder()
        .event("Testing Event")
        .properties(new Properties()
            .putValue("testing lVars", list))
        .build()
    );

    verifyStatic();
    String joinedlVars = "item1, item2";
    Map<String, Object> contextData = new HashMap<>();
    contextData.put("joinedString", joinedlVars);
    Analytics.trackAction("Adobe Testing Event", contextData);
  }

  @Test
  public void identify() {
  }

  @Test
  public void screen() {
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
