package com.segment.analytics.android.integrations.adobeanalytics;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import com.adobe.mobile.Analytics;
import com.adobe.mobile.Config;
import com.segment.analytics.Properties;
import com.segment.analytics.Properties.Product;
import com.segment.analytics.Traits;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.IdentifyPayload;
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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.robolectric.RobolectricTestRunner;

import static com.segment.analytics.Analytics.LogLevel.NONE;
import static com.segment.analytics.Analytics.LogLevel.VERBOSE;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(RobolectricTestRunner.class)
@PrepareForTest({Analytics.class, Config.class})
@org.robolectric.annotation.Config(constants = BuildConfig.class)
@PowerMockIgnore({ "org.mockito.*", "org.robolectric.*", "android.*", "org.json.*" })
public class AdobeTest {

  @Rule public PowerMockRule rule = new PowerMockRule();
  private AdobeIntegration integration;
  @Mock com.segment.analytics.Analytics analytics;
  @Mock Application context;

  @Before
  public void setUp() {
    initMocks(this);
    PowerMockito.mockStatic(Config.class);
    PowerMockito.mockStatic(Analytics.class);
    when(analytics.getApplication()).thenReturn(context);
    integration = new AdobeIntegration(new ValueMap(), analytics, Logger.with(NONE));
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
        .putValue("lVarsV2", new ArrayList<ValueMap>())
        .putValue("productIdentifier", "id"),
      analytics,
      Logger.with(VERBOSE));

    verifyStatic();
    Config.setDebugLogging(true);

    assertTrue(integration.eventsV2.equals(new HashMap<String, Object>()));
    assertTrue(integration.contextValues.equals(new HashMap<String, Object>()));
    assertTrue(integration.lVarsV2.equals(new ArrayList<ValueMap>()));
    assertTrue(integration.productIdentifier.equals("id"));
  }

  @Test
  public void initializeWithAdobeHeartbeat() {
    integration = new AdobeIntegration(new ValueMap()
        .putValue("videoHeartbeatEnabled", true)
        .putValue("heartbeatTrackingServer", "exchangepartnersegment.hb.omtrdc.net")
        .putValue("heartbeatChannel", "Video Channel")
        .putValue("heartbeatOnlineVideoPlatform", "HTML 5")
        .putValue("heartbeatPlayerName", "HTML 5 Basic")
        .putValue("heartbeatEnableSsl", true),
        analytics,
        Logger.with(VERBOSE));

    verifyStatic();
    Config.setDebugLogging(true);

    assertTrue(integration.videoHeartbeatEnabled);
    assertTrue(integration.config.debugLogging);
    assertTrue(integration.config.trackingServer.equals("exchangepartnersegment.hb.omtrdc.net"));
    assertTrue(integration.config.channel.equals("Video Channel"));
    assertTrue(integration.config.appVersion.equals("0.0"));
    assertTrue(integration.config.ovp.equals("HTML 5"));
    assertTrue(integration.config.playerName.equals("HTML 5 Basic"));
    assertTrue(integration.config.ssl);
    assertTrue(integration.config.debugLogging);
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
  public void trackOrderCompleted() {
    integration.productIdentifier = "name";
    integration.contextValues = new HashMap<>();
    integration.contextValues.put("testing", "myapp.testing");

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Order Completed")
        .properties(new Properties()
            .putOrderId("A5744855555")
            .putValue("testing", "test!")
            .putProducts(new Product("123", "ABC", 10.0)
                .putName("shoes")
                .putValue("category", "athletic")
                .putValue("quantity", 2)))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("myapp.testing", "test!");
    contextData.put("purchaseid", "A5744855555");
    contextData.put("&&products", "athletic;shoes;2;20.0");
    contextData.put("&&events", "purchase");
    verifyStatic();
    Analytics.trackAction("purchase", contextData);
  }

  @Test
  public void trackProductAdded() {
    integration.productIdentifier = "name";

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Product Added")
        .properties(new Properties()
            .putSku("ABC")
            .putPrice(10.0)
            .putName("shoes")
            .putCategory("athletic")
            .putValue("quantity", 2))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("&&products", "athletic;shoes;2;20.0");
    contextData.put("&&events", "scAdd");
    verifyStatic();
    Analytics.trackAction("scAdd", contextData);
  }

  @Test
  public void trackProductRemoved() {
    integration.productIdentifier = "name";

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Product Removed")
        .properties(new Properties()
            .putSku("ABC")
            .putPrice(10.0)
            .putName("shoes")
            .putCategory("athletic")
            .putValue("quantity", 2))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("&&products", "athletic;shoes;2;20.0");
    contextData.put("&&events", "scRemove");
    verifyStatic();
    Analytics.trackAction("scRemove", contextData);
  }

  @Test
  public void trackProductViewed() {
    integration.productIdentifier = "name";

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Product Viewed")
        .properties(new Properties()
            .putSku("ABC")
            .putPrice(10.0)
            .putName("shoes")
            .putCategory("athletic")
            .putValue("quantity", 2))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("&&products", "athletic;shoes;2;20.0");
    contextData.put("&&events", "prodView");
    verifyStatic();
    Analytics.trackAction("prodView", contextData);
  }

  @Test
  public void trackEcommerceEventWithProductId() {
    integration.productIdentifier = "id";

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Product Viewed")
        .properties(new Properties()
            .putValue("productId", "XYZ")
            .putSku("ABC")
            .putPrice(10.0)
            .putName("shoes")
            .putCategory("athletic")
            .putValue("quantity", 2))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("&&products", "athletic;XYZ;2;20.0");
    contextData.put("&&events", "prodView");
    verifyStatic();
    Analytics.trackAction("prodView", contextData);
  }

  @Test
  public void trackCheckoutStarted() {
    integration.productIdentifier = "name";

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Checkout Started")
        .properties(new Properties()
            .putProducts(new Product("123", "ABC", 10.0)
                .putName("shoes")
                .putValue("category", "athletic")
                .putValue("quantity", 2),
              new Product("456", "DEF", 20.0)
                .putName("jeans")
                .putValue("category", "casual")
                .putValue("quantity", 1)))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("&&products", "athletic;shoes;2;20.0,casual;jeans;1;20.0");
    contextData.put("&&events", "scCheckout");
    verifyStatic();
    Analytics.trackAction("scCheckout", contextData);
  }

  @Test
  public void trackCartViewed() {
    integration.productIdentifier = "name";

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Cart Viewed")
        .properties(new Properties()
            .putProducts(new Product("123", "ABC", 10.0)
                    .putName("shoes")
                    .putValue("category", "athletic")
                    .putValue("quantity", 2),
                new Product("456", "DEF", 20.0)
                    .putName("jeans")
                    .putValue("category", "casual")
                    .putValue("quantity", 1)))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("&&products", "athletic;shoes;2;20.0,casual;jeans;1;20.0");
    contextData.put("&&events", "scView");
    verifyStatic();
    Analytics.trackAction("scView", contextData);
  }

  @Test
  public void trackEcommerceWhenProductNameIsNotSet() {
    integration.productIdentifier = "name";

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Product Removed")
        .properties(new Properties()
            .putSku("ABC")
            .putPrice(10.0)
            .putCategory("athletic")
            .putValue("quantity", 2))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("&&events", "scRemove");

    verifyStatic();
    Analytics.trackAction("scRemove", contextData);
  }

  @Test
  public void trackEcommerceEventWithNoProperties() {
    integration.productIdentifier = "name";

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Product Added")
        .properties(new Properties())
        .build()
    );

    verifyStatic();
    Analytics.trackAction("scAdd", null);
  }

  @Test
  public void trackPurchaseEventToTestDefaults() {
    integration.productIdentifier = "sku";

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Order Completed")
        .properties(new Properties()
            .putProducts(new Product("123", "ABC", 0)))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("&&products", ";ABC;1;0.0");
    contextData.put("&&events", "purchase");
    verifyStatic();
    Analytics.trackAction("purchase", contextData);
  }

  @Test
  public void trackPurchaseWithoutProducts() {
    integration.productIdentifier = "name";

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Order Completed")
        .properties(new Properties()
            .putOrderId("123456"))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("&&events", "purchase");
    contextData.put("purchaseid", "123456");
    verifyStatic();
    Analytics.trackAction("purchase", contextData);
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

    integration.screen(new ScreenPayload.Builder()
        .userId("123")
        .name("Viewed a Screen")
        .properties(new Properties()
            .putValue("filters", list))
      .build()
    );

    String joinedlVarsV2 = "item1,item2";
    Map<String, Object> contextData = new HashMap<>();
    contextData.put("myapp.filters", joinedlVarsV2);
    verifyStatic();
    Analytics.trackState("Viewed a Screen", contextData);
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
