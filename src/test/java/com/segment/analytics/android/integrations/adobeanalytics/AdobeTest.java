package com.segment.analytics.android.integrations.adobeanalytics;

import android.app.Application;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.segment.analytics.Analytics;
import com.segment.analytics.Options;
import com.segment.analytics.Properties;
import com.segment.analytics.Traits;
import com.segment.analytics.ValueMap;
import com.segment.analytics.core.tests.BuildConfig;
import com.segment.analytics.integrations.GroupPayload;
import com.segment.analytics.integrations.IdentifyPayload;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.TrackPayload;
import com.segment.analytics.test.GroupPayloadBuilder;
import com.segment.analytics.test.IdentifyPayloadBuilder;
import com.segment.analytics.test.ScreenPayloadBuilder;
import com.segment.analytics.test.TrackPayloadBuilder;

import java.util.HashMap;
import java.util.Map;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;

import static com.segment.analytics.Analytics.LogLevel.VERBOSE;
import static com.segment.analytics.Utils.createTraits;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

@RunWith(RobolectricTestRunner.class)
@PrepareForTest(com.adobe.mobile.Analytics.class)
@Config(constants = BuildConfig.class, sdk = 18, manifest = Config.NONE)
public class AdobeTest {

  @Rule
  public PowerMockRule rule = new PowerMockRule();

  private com.adobe.mobile.Analytics adobeAnalytics;
  private AdobeIntegration integration;

  @Before
  public void setUp() {
    adobeAnalytics = PowerMockito.mock(com.adobe.mobile.Analytics.class);
    PowerMockito.mockStatic(com.adobe.mobile.Analytics.class);
    integration = new AdobeIntegration(new ValueMap(), Logger.with(VERBOSE));
  }

  @Test
  public void factory() {
  }

  @Test
  public void initialize() {
  }

  @Test
  public void initializeWithDefaultArguments() {
  }

  @Test
  public void track() {
    integration.events = new HashMap<>();
    integration.events.put("Test Event", "myapp.testing.Testing");

    integration.track(new TrackPayloadBuilder()
      .event("Test Event")
      .properties(new Properties())
      .build());

    verifyStatic();
    adobeAnalytics.trackAction("myapp.testing.Testing", null);
  }

  @Test
  public void trackWithoutMappedName() {
    integration.track(new TrackPayloadBuilder()
        .event("Test Event")
        .properties(new Properties())
        .build());

    verifyZeroInteractions(adobeAnalytics);
  }


  @Test
  public void trackEcommerce() {
    integration.track(new TrackPayloadBuilder()
      .event("Order Completed")
      .properties(new Properties()
        .putOrderId("123")
      )
      .build());

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("purchaseid", "123");
    contextData.put("orderId", "123");

    verifyStatic();
    adobeAnalytics.trackAction("purchase", contextData);
  }

  @Test
  public void identify() {
  }

  @Test
  public void screen() {
    integration.screen(new ScreenPayloadBuilder()
        .name("Viewed Home Screen")
        .properties(new Properties()
            .putValue("userLoggedIn", true)
        )
        .build());

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("userLoggedIn", true);

    verifyStatic();
    adobeAnalytics.trackState("Viewed Home Screen", contextData);
  }

  @Test
  public void group() {
  }

  @Test
  public void flush() {
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
