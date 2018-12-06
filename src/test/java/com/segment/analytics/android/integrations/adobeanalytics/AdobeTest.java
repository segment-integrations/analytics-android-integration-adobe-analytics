package com.segment.analytics.android.integrations.adobeanalytics;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.support.annotation.Nullable;
import com.adobe.mobile.Analytics;
import com.adobe.mobile.Config;
import com.adobe.primetime.va.simple.MediaHeartbeat;
import com.adobe.primetime.va.simple.MediaHeartbeat.MediaHeartbeatDelegate;
import com.adobe.primetime.va.simple.MediaHeartbeatConfig;
import com.adobe.primetime.va.simple.MediaObject;
import com.segment.analytics.Properties;
import com.segment.analytics.Properties.Product;
import com.segment.analytics.Traits;
import com.segment.analytics.ValueMap;
import com.segment.analytics.android.integrations.adobeanalytics.AdobeIntegration.PlaybackDelegate;
import com.segment.analytics.integrations.IdentifyPayload;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.ScreenPayload;
import com.segment.analytics.integrations.TrackPayload;
import com.segment.analytics.Analytics.LogLevel;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;

public class AdobeTest {

  @Mock private MediaHeartbeat heartbeat;
  @Mock private com.segment.analytics.Analytics analytics;
  @Mock private Application application;
  @Mock private AdobeAnalyticsClient client;
  private AdobeIntegration integration;
  private AdobeIntegration.HeartbeatFactory mockHeartbeatFactory = new AdobeIntegration.HeartbeatFactory() {
    @Override
    public MediaHeartbeat get(MediaHeartbeatDelegate delegate, MediaHeartbeatConfig config) {
      return heartbeat;
    }
  };

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    Mockito.when(analytics.getApplication()).thenReturn(application);
    Mockito.when(analytics.logger("Adobe Analytics")).thenReturn(Logger.with(LogLevel.VERBOSE));

    integration = new AdobeIntegration(new ValueMap()
        .putValue("heartbeatTrackingServerUrl", "https://www.heartbeatTrackingServerURL.com/"),
        analytics, Logger.with(LogLevel.NONE), mockHeartbeatFactory);
    integration.setClient(client);
  }

  @Test
  public void factory() {
    Assert.assertEquals(AdobeIntegration.FACTORY.key(), "Adobe Analytics");
  }

  @Test
  public void initialize() {
    integration = new AdobeIntegration(new ValueMap()
        .putValue("eventsV2", new HashMap<String, Object>())
        .putValue("contextValues", new HashMap<String, Object>())
        .putValue("productIdentifier", "id")
        .putValue("adobeVerboseLogging", true),
        analytics,
        Logger.with(LogLevel.VERBOSE),
        mockHeartbeatFactory);

    Assert.assertEquals(integration.eventsV2, new HashMap<String, Object>());
    Assert.assertEquals(integration.contextValues, new HashMap<String, Object>());
    Assert.assertEquals(integration.productIdentifier, "id");
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
    integration.eventsV2 = new HashMap<>();
    integration.eventsV2.put("Testing Event", "Adobe Testing Event");

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Testing Event")
        .build()
    );

    Mockito.verify(client).trackAction("Adobe Testing Event", null);
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
    Mockito.verify(client).trackAction("Adobe Testing Event", contextData);
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
    Mockito.verify(client).trackAction("purchase", contextData);
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
    Mockito.verify(client).trackAction("scAdd", contextData);
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
    Mockito.verify(client).trackAction("scRemove", contextData);
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
    Mockito.verify(client).trackAction("prodView", contextData);
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
    Mockito.verify(client).trackAction("prodView", contextData);
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
    Mockito.verify(client).trackAction("scCheckout", contextData);
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
    Mockito.verify(client).trackAction("scView", contextData);
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

    Mockito.verify(client).trackAction("scRemove", contextData);
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

    Mockito.verify(client).trackAction("scAdd", null);
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
    Mockito.verify(client).trackAction("purchase", contextData);
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
    Mockito.verify(client).trackAction("purchase", contextData);
  }

  @Test
  public void videoPlaybackDelegatePlay() throws Exception {
    PlaybackDelegate playbackDelegate = new AdobeIntegration.PlaybackDelegate();
    Thread.sleep(2000);
    Assert.assertEquals(playbackDelegate.getCurrentPlaybackTime().doubleValue(), 2.0, 0.01);
  }

  @Test
  public void videoPlaybackDelegatePaused() throws Exception {
    PlaybackDelegate playbackDelegate = new AdobeIntegration.PlaybackDelegate();
    playbackDelegate.pausePlayhead();
    Double firstPlayheadPosition = playbackDelegate.getCurrentPlaybackTime();
    Thread.sleep(2000);
    Assert.assertEquals(playbackDelegate.getCurrentPlaybackTime().doubleValue(), firstPlayheadPosition.doubleValue(), 0.01);
  }

  @Test
  public void videoPlaybackDelegatePlayAndPause() throws Exception {
    PlaybackDelegate playbackDelegate = new AdobeIntegration.PlaybackDelegate();
    playbackDelegate.pausePlayhead();
    Thread.sleep(1000);
    playbackDelegate.unPausePlayhead();
    Thread.sleep(3000);
    Assert.assertEquals(playbackDelegate.getCurrentPlaybackTime().doubleValue(), 3.0, 0.01);
  }

  @Test
  public void trackVideoPlaybackStarted() {
    integration.contextValues = new HashMap<>();
    integration.contextValues.put("random metadata", "adobe.random");

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Video Playback Started")
        .properties(new Properties()
            .putValue("title", "You Win or You Die")
            .putValue("contentAssetId", "123")
            .putValue("totalLength", 100D)
            .putValue("assetId", "123")
            .putValue("program", "Game of Thrones")
            .putValue("season", "1")
            .putValue("episode", "7")
            .putValue("genre", "fantasy")
            .putValue("channel", "HBO")
            .putValue("airdate", "2011")
            .putValue("livestream", false)
            .putValue("random metadata", "something super random"))
        .build()
    );

    Map<String, String> standardVideoMetadata = new HashMap<>();
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.ASSET_ID, "123");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.SHOW, "Game of Thrones");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.SEASON, "1");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.EPISODE, "7");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.GENRE, "fantasy");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.NETWORK, "HBO");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.FIRST_AIR_DATE, "2011");
    standardVideoMetadata
        .put(MediaHeartbeat.VideoMetadataKeys.STREAM_FORMAT, MediaHeartbeat.StreamType.VOD);

    Map<String, String> videoMetadata = new HashMap<>();
    videoMetadata.put("adobe.random", "something super random");

    // create a media object; values can be null
    MediaObject mediaInfo = MediaHeartbeat.createMediaObject(
        "You Win or You Die",
        "123",
        100D,
        MediaHeartbeat.StreamType.VOD
    );

    mediaInfo.setValue(MediaHeartbeat.MediaObjectKey.StandardVideoMetadata, standardVideoMetadata);

    Mockito.verify(heartbeat).trackSessionStart(mediaObjectEq(mediaInfo),
        eq(videoMetadata));
    Assert.assertNotNull(integration.playbackDelegate);
  }

  @Test
  public void trackVideoPlaybackPaused() {
    newVideoSession();
    heartbeatTestFixture("Video Playback Paused");
    Assert.assertTrue(integration.playbackDelegate.isPaused);
    Mockito.verify(heartbeat).trackPause();
  }

  @Test
  public void trackVideoPlaybackResumed() {
    newVideoSession();
    heartbeatTestFixture("Video Playback Resumed");
    Assert.assertFalse(integration.playbackDelegate.isPaused);
    Mockito.verify(heartbeat).trackPlay();
  }

  @Test
  public void trackVideoContentStarted() {
    integration.contextValues = new HashMap<>();
    integration.contextValues.put("title", "adobe.title");

    newVideoSession();

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Video Content Started")
        .properties(new Properties()
            .putValue("title", "You Win or You Die")
            .putValue("contentAssetId", "123")
            .putValue("totalLength", 100D)
            .putValue("startTime", 10D)
            .putValue("indexPosition", 1L)
            .putValue("position", 35)
            .putValue("season", "1")
            .putValue("program", "Game of Thrones")
            .putValue("episode", "7")
            .putValue("genre", "fantasy")
            .putValue("channel", "HBO")
            .putValue("airdate", "2011")
            .putValue("publisher", "HBO")
            .putValue("rating", "MA"))
        .build()
    );

    Map<String, String> videoMetadata = new HashMap<>();
    videoMetadata.put("adobe.title", "You Win or You Die");

    MediaObject mediaChapter = MediaHeartbeat.createChapterObject(
        "You Win or You Die",
        1L,
        100D,
        10D
    );

    Map <String, String> standardVideoMetadata = new HashMap<>();
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.ASSET_ID, "123");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.SHOW, "Game of Thrones");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.SEASON, "1");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.EPISODE, "7");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.GENRE, "fantasy");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.NETWORK, "HBO");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.FIRST_AIR_DATE, "2011");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.ORIGINATOR, "HBO");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.RATING, "MA");

    mediaChapter.setValue(MediaHeartbeat.MediaObjectKey.StandardVideoMetadata,
        standardVideoMetadata);

    Assert.assertEquals(integration.playbackDelegate.getCurrentPlaybackTime().doubleValue(), 35.0, 0.01);
    Mockito.verify(heartbeat).trackPlay();
    Mockito.verify(heartbeat).trackEvent(eq(MediaHeartbeat.Event.ChapterStart),
        mediaObjectEq(mediaChapter),
        eq(videoMetadata));
  }

  @Test
  public void trackVideoContentComplete() {
    newVideoSession();
    heartbeatTestFixture("Video Content Completed");
    Mockito.verify(heartbeat).trackEvent(MediaHeartbeat.Event.ChapterComplete, null, null);
    Mockito.verify(heartbeat).trackComplete();
  }

  @Test
  public void trackVideoPlaybackComplete() {
    newVideoSession();
    heartbeatTestFixture("Video Playback Completed");
    Mockito.verify(heartbeat).trackSessionEnd();
  }

  @Test
  public void trackVideoBufferStarted() {
    newVideoSession();
    heartbeatTestFixture("Video Playback Buffer Started");
    Assert.assertTrue(integration.playbackDelegate.isPaused);
    Mockito.verify(heartbeat).trackEvent(MediaHeartbeat.Event.BufferStart, null, null);
  }

  @Test
  public void trackVideoBufferComplete() {
    newVideoSession();
    heartbeatTestFixture("Video Playback Buffer Completed");
    Assert.assertFalse(integration.playbackDelegate.isPaused);
    Mockito.verify(heartbeat).trackEvent(MediaHeartbeat.Event.BufferComplete, null, null);
  }

  @Test
  public void trackVideoSeekStarted() {
    newVideoSession();
    heartbeatSeekFixture("Video Playback Seek Started", null);
    Assert.assertTrue(integration.playbackDelegate.isPaused);
    Assert.assertEquals(integration.playbackDelegate.getCurrentPlaybackTime().doubleValue(), 0.0, 0.001);
    Mockito.verify(heartbeat).trackEvent(MediaHeartbeat.Event.SeekStart, null, null);
  }

  @Test
  public void trackVideoSeekComplete() {
    newVideoSession();
    double first = integration.playbackDelegate.getCurrentPlaybackTime();
    heartbeatSeekFixture("Video Playback Seek Completed", 50L);
    Assert.assertFalse(integration.playbackDelegate.isPaused);
    Assert.assertEquals(integration.playbackDelegate.getCurrentPlaybackTime().doubleValue(), first + 50, 0.01);
    Mockito.verify(heartbeat).trackEvent(MediaHeartbeat.Event.SeekComplete, null, null);
  }

  @Test
  public void trackVideoAdBreakStarted() {
    integration.contextValues = new HashMap<>();
    integration.contextValues.put("contextValue", "adobe.context.value");

    newVideoSession();

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Video Ad Break Started")
        .properties(new Properties()
            .putValue("title",
                "Car Commercial") // Should this be pre-roll, mid-roll or post-roll instead?
            .putValue("startTime", 10D)
            .putValue("indexPosition", 1L)
            .putValue("contextValue", "value"))
        .build()
    );

    MediaObject mediaAdBreakInfo = MediaHeartbeat.createAdBreakObject(
        "Car Commercial",
        1L,
        10D
    );

    Map<String, String> adBreakMetadata = new HashMap<>();
    adBreakMetadata.put("adobe.context.value", "value");
    
    Mockito.verify(heartbeat).trackEvent(eq(MediaHeartbeat.Event.AdBreakStart),
        mediaObjectEq(mediaAdBreakInfo), eq(adBreakMetadata));
  }

  @Test
  public void trackVideoAdBreakCompleted() {
    newVideoSession();
    heartbeatTestFixture("Video Ad Break Completed");
    Mockito.verify(heartbeat).trackEvent(MediaHeartbeat.Event.AdBreakComplete, null, null);
  }

  @Test
  public void trackVideoAdStarted() {
    integration.contextValues = new HashMap<>();
    integration.contextValues.put("title", "adobe.title");

    newVideoSession();
    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Video Ad Started")
        .properties(new Properties()
            .putValue("title", "Car Commercial")
            .putValue("assetId", "123")
            .putValue("totalLength", 10D)
            .putValue("indexPosition", 1L)
            .putValue("publisher", "Lexus"))
        .build()
    );

    MediaObject mediaAdInfo = MediaHeartbeat.createAdObject(
        "Car Commercial",
        "123",
        1L,
        10D
    );

    Map<String, String> adMetadata = new HashMap<>();
    adMetadata.put("adobe.title", "Car Commercial");

    Map<String, String> standardAdMetadata = new HashMap<>();
    standardAdMetadata.put(MediaHeartbeat.AdMetadataKeys.ADVERTISER, "Lexus");
    mediaAdInfo.setValue(MediaHeartbeat.MediaObjectKey.StandardAdMetadata, standardAdMetadata);

    Mockito.verify(heartbeat).trackEvent(eq(MediaHeartbeat.Event.AdStart),
        mediaObjectEq(mediaAdInfo), eq(adMetadata));
  }

  @Test
  public void trackVideoAdSkipped() {
    newVideoSession();
    heartbeatTestFixture("Video Ad Skipped");
    Mockito.verify(heartbeat).trackEvent(MediaHeartbeat.Event.AdSkip, null, null);
  }

  @Test
  public void trackVideoAdCompleted() {
    newVideoSession();
    heartbeatTestFixture("Video Ad Completed");
    Mockito.verify(heartbeat).trackEvent(MediaHeartbeat.Event.AdComplete, null, null);
  }

  @Test
  public void trackVideoPlaybackInterrupted() throws Exception {
    integration.playbackDelegate = new AdobeIntegration.PlaybackDelegate();
    heartbeatTestFixture("Video Playback Interrupted");
    Double first = integration.playbackDelegate.getCurrentPlaybackTime();
    Thread.sleep(2000L);
    Assert.assertEquals(integration.playbackDelegate.getCurrentPlaybackTime().doubleValue(), first.doubleValue(), 0.001);
  }

  @Test
  public void trackVideoQualityUpdated() {
    integration.playbackDelegate = new AdobeIntegration.PlaybackDelegate();
    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Video Quality Updated")
        .properties(new Properties()
            .putValue("bitrate", 12000)
            .putValue("startupTime", 1)
            .putValue("fps", 50)
            .putValue("droppedFrames", 1))
        .build()
    );

    MediaObject expectedMediaObject = MediaHeartbeat.createQoSObject(
        12000L,
        1D,
        50D,
        1L
    );

    ArgumentMatcher<MediaObject> matcher = new MediaObjectEqArgumentMatcher(expectedMediaObject);

    Assert.assertTrue(matcher.matches(integration.playbackDelegate.qosData));
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
    integration.screen(new ScreenPayload.Builder()
        .userId("123")
        .name("Viewed a Screen")
        .build()
    );

    Mockito.verify(client).trackState("Viewed a Screen", null);
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

  private void newVideoSession() {
    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Video Playback Started")
        .properties(new Properties()
            .putValue("title", "You Win or You Die")
            .putValue("sessionId", "123")
            .putValue("totalLength", 100D)
            .putValue("assetId", "123")
            .putValue("program", "Game of Thrones")
            .putValue("season", "1")
            .putValue("episode", "7")
            .putValue("genre", "fantasy")
            .putValue("channel", "HBO")
            .putValue("airdate", "2011")
            .putValue("livestream", false))
        .build()
    );
  }

  private void heartbeatTestFixture(String eventName) {
    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event(eventName)
        .build()
    );
  }

  private void heartbeatSeekFixture(String eventName, @Nullable Long seekPosition) {
    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event(eventName)
        .properties(new Properties()
            .putValue("seekPosition", (seekPosition != null ? seekPosition : 0))
        )
        .build()
    );
  }

  /**
   * Compares the value maps of a media object.
   * @param expected MediaObject object expected.
   * @return Argument matcher.
   */
  private static MediaObject mediaObjectEq(MediaObject expected) {
    return argThat(new MediaObjectEqArgumentMatcher(expected));
  }

  private static class MediaObjectEqArgumentMatcher implements ArgumentMatcher<MediaObject> {

    private MediaObject expected;

    MediaObjectEqArgumentMatcher(MediaObject expected) {
      this.expected = expected;
    }

    @Override
    public boolean matches(MediaObject other) {
      if (expected == null) {
        return (other == null);
      } else if (other == null) {
        return false;
      }

      Set<Object> keys = new HashSet<>(Arrays.asList(expected.allKeys()));
      Set<Object> otherKeys = new HashSet<>(Arrays.asList(other.allKeys()));
      if (!keys.equals(otherKeys)) {
        return false;
      }

      for (Object key : keys) {
        String k = (String) key;
        Object value = expected.getValue(k);
        Object otherValue = expected.getValue(k);
        if (!value.equals(otherValue)) {
          return false;
        }
      }

      return true;
    }

    @Override
    public String toString() {
      Set<Object> keys = new HashSet<>(Arrays.asList(expected.allKeys()));
      Map<String, Object> map = new HashMap<>();
      for (Object k : keys) {
        String key = (String) k;
        map.put(key, expected.getValue(key));
      }

      JSONObject obj = new JSONObject(map);
      return obj.toString();
    }
  }

}
