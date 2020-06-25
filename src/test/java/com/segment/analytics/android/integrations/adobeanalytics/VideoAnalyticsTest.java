package com.segment.analytics.android.integrations.adobeanalytics;

import android.content.Context;

import com.adobe.primetime.va.simple.MediaHeartbeat;
import com.adobe.primetime.va.simple.MediaHeartbeat.MediaHeartbeatDelegate;
import com.adobe.primetime.va.simple.MediaHeartbeatConfig;
import com.adobe.primetime.va.simple.MediaObject;
import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.TrackPayload;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;

public class VideoAnalyticsTest {

  private final static String SERVER_URL = "https://www.heartbeatTrackingServerURL.com/";

  @Mock private MediaHeartbeat heartbeat;
  @Mock private VideoAnalytics.HeartbeatFactory heartbeatFactory;
  @Mock private Context context;
  private VideoAnalytics videoAnalytics;
  
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    Mockito.when(context.getPackageName()).thenReturn("test");
    Mockito.when(heartbeatFactory.get(Mockito.any(MediaHeartbeatDelegate.class), Mockito.any(MediaHeartbeatConfig.class))).thenReturn(heartbeat);

    ContextDataConfiguration contextDataConfiguration = new ContextDataConfiguration("", new HashMap<String, String>());
    videoAnalytics = new VideoAnalytics(context, SERVER_URL, contextDataConfiguration, true, heartbeatFactory, Logger.with(Analytics.LogLevel.NONE));
  }

  @Test
  public void trackVideoPlaybackStarted() {
    Map<String, String> variables = new HashMap<>();
    variables.put("random metadata", "adobe.random");
    videoAnalytics.setContextDataConfiguration(new ContextDataConfiguration("", variables));

    TrackPayload payload = new TrackPayload.Builder()
            .userId("test-user")
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
            .build();

    videoAnalytics.track(payload);

    Map<String, String> standardVideoMetadata = new HashMap<>();
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.ASSET_ID, "123");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.SHOW, "Game of Thrones");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.SEASON, "1");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.EPISODE, "7");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.GENRE, "fantasy");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.NETWORK, "HBO");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.FIRST_AIR_DATE, "2011");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.STREAM_FORMAT, MediaHeartbeat.StreamType.VOD);

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

    Mockito.verify(heartbeat).trackSessionStart(mediaObjectEq(mediaInfo), eq(videoMetadata));
    Assert.assertNotNull(videoAnalytics.getPlayback());
  }

  @Test
  public void trackVideoPlaybackPaused() {
    startVideoSession();
    sendHeartbeat("Video Playback Paused");
    Assert.assertTrue(videoAnalytics.getPlayback().isPaused());
    Mockito.verify(heartbeat).trackPause();
  }

  @Test
  public void trackVideoPlaybackResumed() {
    startVideoSession();
    sendHeartbeat("Video Playback Resumed");
    Assert.assertFalse(videoAnalytics.getPlayback().isPaused());
    Mockito.verify(heartbeat).trackPlay();
  }

  @Test
  public void trackVideoContentStarted() {
    Map<String, String> variables = new HashMap<>();
    variables.put("title", "adobe.title");
    videoAnalytics.setContextDataConfiguration(new ContextDataConfiguration("", variables));
    startVideoSession();

    TrackPayload payload = new TrackPayload.Builder()
            .userId("test-user")
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
            .build();
    videoAnalytics.track(payload);

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

    Assert.assertEquals(videoAnalytics.getPlayback().getCurrentPlaybackTime(), 35.0, 0.01);
    Mockito.verify(heartbeat).trackPlay();
    Mockito.verify(heartbeat).trackEvent(eq(MediaHeartbeat.Event.ChapterStart),
        mediaObjectEq(mediaChapter),
        eq(videoMetadata));
  }

  @Test
  public void trackVideoContentStarted_snakeCase() {
    Map<String, String> variables = new HashMap<>();
    variables.put("title", "adobe.title");
    videoAnalytics.setContextDataConfiguration(new ContextDataConfiguration("", variables));
    startVideoSession();

    TrackPayload payload = new TrackPayload.Builder()
            .userId("test-user")
            .event("Video Content Started")
            .properties(new Properties()
                    .putValue("title", "You Win or You Die")
                    .putValue("content_asset_id", "123")
                    .putValue("total_length", 100D)
                    .putValue("start_time", 10D)
                    .putValue("index_position", 1L)
                    .putValue("position", 35)
                    .putValue("season", "1")
                    .putValue("program", "Game of Thrones")
                    .putValue("episode", "7")
                    .putValue("genre", "fantasy")
                    .putValue("channel", "HBO")
                    .putValue("airdate", "2011")
                    .putValue("publisher", "HBO")
                    .putValue("rating", "MA"))
            .build();
    videoAnalytics.track(payload);

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

    Assert.assertEquals(videoAnalytics.getPlayback().getCurrentPlaybackTime(), 35.0, 0.01);
    Mockito.verify(heartbeat).trackPlay();
    Mockito.verify(heartbeat).trackEvent(eq(MediaHeartbeat.Event.ChapterStart),
            mediaObjectEq(mediaChapter),
            eq(videoMetadata));
  }

  @Test
  public void trackVideoContentStartedWithExtraProperties() {
    Map<String, String> variables = new HashMap<>();
    variables.put("title", "adobe.title");
    variables.put(".context.library", "adobe.library");
    videoAnalytics.setContextDataConfiguration(new ContextDataConfiguration("", variables));
    startVideoSession();

    TrackPayload payload = new TrackPayload.Builder()
            .userId("test-user")
            .event("Video Content Started")
            .context(new ValueMap().putValue("library", "Android"))
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
                    .putValue("rating", "MA")
                    .putValue("extra", "extra value"))
            .build();
    videoAnalytics.track(payload);

    Map<String, String> videoMetadata = new HashMap<>();
    videoMetadata.put("adobe.title", "You Win or You Die");
    videoMetadata.put("extra", "extra value");
    videoMetadata.put("adobe.library", "Android");

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

    Assert.assertEquals(videoAnalytics.getPlayback().getCurrentPlaybackTime(), 35.0, 0.01);
    Mockito.verify(heartbeat).trackPlay();
    Mockito.verify(heartbeat).trackEvent(eq(MediaHeartbeat.Event.ChapterStart),
            mediaObjectEq(mediaChapter),
            eq(videoMetadata));
  }

  @Test
  public void trackVideoContentComplete() {
    startVideoSession();
    sendHeartbeat("Video Content Completed");
    Mockito.verify(heartbeat).trackEvent(MediaHeartbeat.Event.ChapterComplete, null, null);
  }

  @Test
  public void trackVideoPlaybackComplete() {
    startVideoSession();
    sendHeartbeat("Video Playback Completed");
    Assert.assertTrue(videoAnalytics.getPlayback().isPaused());
    Mockito.verify(heartbeat).trackComplete();
    Mockito.verify(heartbeat).trackSessionEnd();
  }

  @Test
  public void trackVideoBufferStarted() {
    startVideoSession();
    sendHeartbeat("Video Playback Buffer Started");
    Assert.assertTrue(videoAnalytics.getPlayback().isPaused());
    Mockito.verify(heartbeat).trackEvent(MediaHeartbeat.Event.BufferStart, null, null);
  }

  @Test
  public void trackVideoBufferComplete() {
    startVideoSession();
    sendHeartbeat("Video Playback Buffer Completed");
    Assert.assertFalse(videoAnalytics.getPlayback().isPaused());
    Mockito.verify(heartbeat).trackEvent(MediaHeartbeat.Event.BufferComplete, null, null);
  }

  @Test
  public void trackVideoSeekStarted() {
    startVideoSession();
    sendSeekHeartbeat("Video Playback Seek Started", null);
    Assert.assertTrue(videoAnalytics.getPlayback().isPaused());
    Assert.assertEquals(videoAnalytics.getPlayback().getCurrentPlaybackTime(), 0.0, 0.001);
    Mockito.verify(heartbeat).trackEvent(MediaHeartbeat.Event.SeekStart, null, null);
  }

  @Test
  public void trackVideoSeekComplete() {
    startVideoSession();
    double first = videoAnalytics.getPlayback().getCurrentPlaybackTime();
    sendSeekHeartbeat("Video Playback Seek Completed", 50L);
    Assert.assertFalse(videoAnalytics.getPlayback().isPaused());
    Assert.assertEquals(videoAnalytics.getPlayback().getCurrentPlaybackTime(), first + 50, 0.01);
    Mockito.verify(heartbeat).trackEvent(MediaHeartbeat.Event.SeekComplete, null, null);
  }

  @Test
  public void trackVideoAdBreakStarted() {
    Map<String, String> variables = new HashMap<>();
    variables.put("contextValue", "adobe.context.value");
    videoAnalytics.setContextDataConfiguration(new ContextDataConfiguration("", variables));
    startVideoSession();

    TrackPayload payload = new TrackPayload.Builder()
            .userId("test-user")
            .event(VideoAnalytics.Event.AdBreakStarted.getName())
            .properties(new Properties()
                    .putValue("title",
                            "Car Commercial") // Should this be pre-roll, mid-roll or post-roll instead?
                    .putValue("startTime", 10D)
                    .putValue("indexPosition", 1L)
                    .putValue("contextValue", "value"))
            .build();

    videoAnalytics.track(payload);

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
  public void trackVideoAdBreakStarted_snakeCase() {
    Map<String, String> variables = new HashMap<>();
    variables.put("context_value", "adobe.context.value");
    videoAnalytics.setContextDataConfiguration(new ContextDataConfiguration("", variables));
    startVideoSession();

    TrackPayload payload = new TrackPayload.Builder()
            .userId("test-user")
            .event(VideoAnalytics.Event.AdBreakStarted.getName())
            .properties(new Properties()
                    .putValue("title",
                            "Car Commercial") // Should this be pre-roll, mid-roll or post-roll instead?
                    .putValue("start_time", 10D)
                    .putValue("index_position", 1L)
                    .putValue("context_value", "value"))
            .build();

    videoAnalytics.track(payload);

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
    startVideoSession();
    sendHeartbeat("Video Ad Break Completed");
    Mockito.verify(heartbeat).trackEvent(MediaHeartbeat.Event.AdBreakComplete, null, null);
  }

  @Test
  public void trackVideoAdStarted() {
    Map<String, String> variables = new HashMap<>();
    variables.put("title", "adobe.title");
    videoAnalytics.setContextDataConfiguration(new ContextDataConfiguration("myapp.", variables));
    startVideoSession();

    TrackPayload payload = new TrackPayload.Builder()
            .userId("test-user")
            .event(VideoAnalytics.Event.AdStarted.getName())
            .properties(new Properties()
                    .putValue("title", "Car Commercial")
                    .putValue("assetId", "123")
                    .putValue("totalLength", 10D)
                    .putValue("indexPosition", 1L)
                    .putValue("publisher", "Lexus")
                    .putValue("extra", "extra value"))
            .build();

    videoAnalytics.track(payload);

    MediaObject mediaAdInfo = MediaHeartbeat.createAdObject(
        "Car Commercial",
        "123",
        1L,
        10D
    );

    Map<String, String> adMetadata = new HashMap<>();
    adMetadata.put("adobe.title", "Car Commercial");
    adMetadata.put("myapp.extra", "extra value");

    Map<String, String> standardAdMetadata = new HashMap<>();
    standardAdMetadata.put(MediaHeartbeat.AdMetadataKeys.ADVERTISER, "Lexus");
    mediaAdInfo.setValue(MediaHeartbeat.MediaObjectKey.StandardAdMetadata, standardAdMetadata);

    Mockito.verify(heartbeat).trackEvent(eq(MediaHeartbeat.Event.AdStart),
        mediaObjectEq(mediaAdInfo), eq(adMetadata));
  }

  @Test
  public void trackVideoAdStarted_snakeCase() {
    Map<String, String> variables = new HashMap<>();
    variables.put("title", "adobe.title");
    videoAnalytics.setContextDataConfiguration(new ContextDataConfiguration("myapp.", variables));
    startVideoSession();

    TrackPayload payload = new TrackPayload.Builder()
            .userId("test-user")
            .event(VideoAnalytics.Event.AdStarted.getName())
            .properties(new Properties()
                    .putValue("title", "Car Commercial")
                    .putValue("asset_id", "123")
                    .putValue("total_length", 10D)
                    .putValue("index_position", 1L)
                    .putValue("publisher", "Lexus")
                    .putValue("extra", "extra value"))
            .build();

    videoAnalytics.track(payload);

    MediaObject mediaAdInfo = MediaHeartbeat.createAdObject(
            "Car Commercial",
            "123",
            1L,
            10D
    );

    Map<String, String> adMetadata = new HashMap<>();
    adMetadata.put("adobe.title", "Car Commercial");
    adMetadata.put("myapp.extra", "extra value");

    Map<String, String> standardAdMetadata = new HashMap<>();
    standardAdMetadata.put(MediaHeartbeat.AdMetadataKeys.ADVERTISER, "Lexus");
    mediaAdInfo.setValue(MediaHeartbeat.MediaObjectKey.StandardAdMetadata, standardAdMetadata);

    Mockito.verify(heartbeat).trackEvent(eq(MediaHeartbeat.Event.AdStart),
            mediaObjectEq(mediaAdInfo), eq(adMetadata));
  }

  @Test
  public void trackVideoAdSkipped() {
    startVideoSession();
    sendHeartbeat("Video Ad Skipped");
    Mockito.verify(heartbeat).trackEvent(MediaHeartbeat.Event.AdSkip, null, null);
  }

  @Test
  public void trackVideoAdCompleted() {
    startVideoSession();
    sendHeartbeat("Video Ad Completed");
    Mockito.verify(heartbeat).trackEvent(MediaHeartbeat.Event.AdComplete, null, null);
  }

  @Test
  public void trackVideoPlaybackInterrupted() throws Exception {
    startVideoSession();
    sendHeartbeat("Video Playback Interrupted");
    Double first = videoAnalytics.getPlayback().getCurrentPlaybackTime();
    Thread.sleep(2000L);
    Assert.assertEquals(videoAnalytics.getPlayback().getCurrentPlaybackTime(), first, 0.001);
  }

  @Test
  public void trackVideoQualityUpdated() {
    startVideoSession();

    TrackPayload payload = new TrackPayload.Builder()
            .userId("test-user")
            .event(VideoAnalytics.Event.QualityUpdated.getName())
            .properties(new Properties()
                    .putValue("bitrate", 12000)
                    .putValue("startupTime", 1)
                    .putValue("fps", 50)
                    .putValue("droppedFrames", 1))
            .build();
    videoAnalytics.track(payload);

    MediaObject expectedMediaObject = MediaHeartbeat.createQoSObject(
        12000L,
        1D,
        50D,
        1L
    );

    ArgumentMatcher<MediaObject> matcher = new MediaObjectEqArgumentMatcher(expectedMediaObject);

    Assert.assertTrue(matcher.matches(videoAnalytics.getPlayback().getQosData()));
  }

  @Test
  public void trackVideoQualityUpdated_snakeCase() {
    startVideoSession();

    TrackPayload payload = new TrackPayload.Builder()
            .userId("test-user")
            .event(VideoAnalytics.Event.QualityUpdated.getName())
            .properties(new Properties()
                    .putValue("bitrate", 12000)
                    .putValue("startup_time", 1)
                    .putValue("fps", 50)
                    .putValue("dropped_frames", 1))
            .build();
    videoAnalytics.track(payload);

    MediaObject expectedMediaObject = MediaHeartbeat.createQoSObject(
            12000L,
            1D,
            50D,
            1L
    );

    ArgumentMatcher<MediaObject> matcher = new MediaObjectEqArgumentMatcher(expectedMediaObject);

    Assert.assertTrue(matcher.matches(videoAnalytics.getPlayback().getQosData()));
  }

  private void startVideoSession() {
    TrackPayload payload = new TrackPayload.Builder()
            .userId("test-user")
            .event(VideoAnalytics.Event.PlaybackStarted.getName())
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
            .build();

    videoAnalytics.track(payload);
  }

  private void sendHeartbeat(String eventName) {
    TrackPayload payload = new TrackPayload.Builder()
            .userId("test-user")
            .event(eventName)
            .build();
    videoAnalytics.track(payload);
  }

  private void sendSeekHeartbeat(String eventName, Long seekPosition) {
    TrackPayload payload = new TrackPayload.Builder()
            .userId("test-user")
            .event(eventName)
            .properties(new Properties().putValue("seekPosition", (seekPosition != null ? seekPosition : 0)))
            .build();
    videoAnalytics.track(payload);
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
