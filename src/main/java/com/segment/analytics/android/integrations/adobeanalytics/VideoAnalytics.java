package com.segment.analytics.android.integrations.adobeanalytics;

import android.content.Context;

import com.adobe.primetime.va.simple.MediaHeartbeat;
import com.adobe.primetime.va.simple.MediaHeartbeatConfig;
import com.adobe.primetime.va.simple.MediaObject;
import com.segment.analytics.Properties;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.BasePayload;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.TrackPayload;

import java.util.HashMap;
import java.util.Map;

/**
 * Generate events for all video actions.
 *
 * @since 1.2.0
 */
class VideoAnalytics {

  enum Event {
    PlaybackStarted("Video Playback Started"),
    ContentStarted("Video Content Started"),
    PlaybackPaused("Video Playback Paused"),
    PlaybackResumed("Video Playback Resumed"),
    ContentCompleted("Video Content Completed"),
    PlaybackCompleted("Video Playback Completed"),
    PlaybackBufferStarted("Video Playback Buffer Started"),
    PlaybackBufferCompleted("Video Playback Buffer Completed"),
    PlaybackSeekStarted("Video Playback Seek Started"),
    PlaybackSeekCompleted("Video Playback Seek Completed"),
    AdBreakStarted("Video Ad Break Started"),
    AdBreakCompleted("Video Ad Break Completed"),
    AdStarted("Video Ad Started"),
    AdSkipped("Video Ad Skipped"),
    AdCompleted("Video Ad Completed"),
    PlaybackInterrupted("Video Playback Interrupted"),
    QualityUpdated("Video Quality Updated");

    private String name;

    Event(String name) {
      this.name = name;
    }

    /**
     * Retrieves the Adobe Analytics video event name. This is different from <code>enum.name()
     * </code>.
     *
     * @return Event name.
     */
    String getName() {
      return name;
    }

    private static Map<String, Event> names;

    static {
      names = new HashMap<>();
      for (Event e : Event.values()) {
        names.put(e.name, e);
      }
    }

    static Event get(String name) {
      if (names.containsKey(name)) {
        return names.get(name);
      }
      throw new IllegalArgumentException(name + " is not a valid video event");
    }

    /**
     * Identifies if the event is a video event.
     *
     * @param eventName Event name
     * @return <code>true</code> if it's a video event, <code>false</code> otherwise.
     */
    static boolean isVideoEvent(String eventName) {
      return names.containsKey(eventName);
    }
  }

  private static final Map<String, String> VIDEO_METADATA_KEYS = new HashMap<>();
  private static final Map<String, String> AD_METADATA_KEYS = new HashMap<>();

  /**
   * Creates MediaHeartbeats with the provided delegate.
   *
   * @since 1.1.1
   */
  static class HeartbeatFactory {

    static {
      VIDEO_METADATA_KEYS.put("assetId", MediaHeartbeat.VideoMetadataKeys.ASSET_ID);
      VIDEO_METADATA_KEYS.put("asset_id", MediaHeartbeat.VideoMetadataKeys.ASSET_ID);
      VIDEO_METADATA_KEYS.put("contentAssetId", MediaHeartbeat.VideoMetadataKeys.ASSET_ID);
      VIDEO_METADATA_KEYS.put("content_asset_id", MediaHeartbeat.VideoMetadataKeys.ASSET_ID);
      VIDEO_METADATA_KEYS.put("program", MediaHeartbeat.VideoMetadataKeys.SHOW);
      VIDEO_METADATA_KEYS.put("season", MediaHeartbeat.VideoMetadataKeys.SEASON);
      VIDEO_METADATA_KEYS.put("episode", MediaHeartbeat.VideoMetadataKeys.EPISODE);
      VIDEO_METADATA_KEYS.put("genre", MediaHeartbeat.VideoMetadataKeys.GENRE);
      VIDEO_METADATA_KEYS.put("channel", MediaHeartbeat.VideoMetadataKeys.NETWORK);
      VIDEO_METADATA_KEYS.put("airdate", MediaHeartbeat.VideoMetadataKeys.FIRST_AIR_DATE);
      VIDEO_METADATA_KEYS.put("publisher", MediaHeartbeat.VideoMetadataKeys.ORIGINATOR);
      VIDEO_METADATA_KEYS.put("rating", MediaHeartbeat.VideoMetadataKeys.RATING);

      AD_METADATA_KEYS.put("publisher", MediaHeartbeat.AdMetadataKeys.ADVERTISER);
    }

    HeartbeatFactory() {}

    MediaHeartbeat get(
        MediaHeartbeat.MediaHeartbeatDelegate delegate, MediaHeartbeatConfig config) {
      return new MediaHeartbeat(delegate, config);
    }
  }

  private String heartbeatTrackingServerUrl;
  private ContextDataConfiguration contextDataConfiguration;
  private boolean ssl;
  private boolean debug;
  private boolean sessionStarted;
  private String packageName;
  private PlaybackDelegate playback;
  private MediaHeartbeat heartbeat;
  private HeartbeatFactory heartbeatFactory;
  private Logger logger;

  VideoAnalytics(
      Context context,
      String serverUrl,
      ContextDataConfiguration contextDataConfiguration,
      boolean ssl,
      Logger logger) {
    this(context, serverUrl, contextDataConfiguration, ssl, new HeartbeatFactory(), logger);
  }

  VideoAnalytics(
      Context context,
      String serverUrl,
      ContextDataConfiguration contextDataConfiguration,
      boolean ssl,
      HeartbeatFactory heartbeatFactory,
      Logger logger) {
    this.heartbeatFactory = heartbeatFactory;
    this.logger = logger;
    this.ssl = ssl;
    this.contextDataConfiguration = contextDataConfiguration;

    sessionStarted = false;
    debug = false;
    heartbeatTrackingServerUrl = serverUrl;

    packageName = context.getPackageName();
    if (packageName == null) {
      // default app version to "unknown" if not otherwise present b/c Adobe requires this value
      packageName = "unknown";
    }
  }

  void track(TrackPayload payload) {
    Event event = Event.get(payload.event());

    if (heartbeatTrackingServerUrl == null) {
      logger.verbose(
          "Please enter a Heartbeat Tracking Server URL in your Segment UI "
              + "Settings in order to send video events to Adobe Analytics");
      return;
    }

    if (event != Event.PlaybackStarted && !sessionStarted) {
      throw new IllegalStateException("Video session has not started yet.");
    }

    switch (event) {
      case PlaybackStarted:
        trackVideoPlaybackStarted(payload);
        break;

      case PlaybackPaused:
        trackVideoPlaybackPaused();
        break;

      case PlaybackResumed:
        trackVideoPlaybackResumed();
        break;

      case PlaybackCompleted:
        trackVideoPlaybackCompleted();
        break;

      case ContentStarted:
        trackVideoContentStarted(payload);
        break;

      case ContentCompleted:
        trackVideoContentCompleted();
        break;

      case PlaybackBufferStarted:
        trackVideoPlaybackBufferStarted();
        break;

      case PlaybackBufferCompleted:
        trackVideoPlaybackBufferCompleted();
        break;

      case PlaybackSeekStarted:
        trackVideoPlaybackSeekStarted();
        break;

      case PlaybackSeekCompleted:
        trackVideoPlaybackSeekCompleted(payload);
        break;

      case AdBreakStarted:
        trackVideoAdBreakStarted(payload);
        break;

      case AdBreakCompleted:
        trackVideoAdBreakCompleted();
        break;

      case AdStarted:
        trackVideoAdStarted(payload);
        break;

      case AdSkipped:
        trackVideoAdSkipped();
        break;

      case AdCompleted:
        trackVideoAdCompleted();
        break;

      case PlaybackInterrupted:
        trackVideoPlaybackInterrupted();
        break;

      case QualityUpdated:
        trackVideoQualityUpdated(payload);
        break;
    }
  }

  private void trackVideoPlaybackStarted(TrackPayload track) {
    Properties eventProperties = track.properties();
    MediaHeartbeatConfig config = new MediaHeartbeatConfig();

    config.trackingServer = heartbeatTrackingServerUrl;
    config.channel = eventProperties.getString("channel");
    if (config.channel == null) {
      config.channel = "";
    }

    config.playerName = eventProperties.getString("videoPlayer");
    if (config.playerName == null) {
      config.playerName = eventProperties.getString("video_player");
      if (config.playerName == null) {
        config.playerName = "unknown";
      }
    }

    config.appVersion = packageName;
    config.ssl = ssl;
    config.debugLogging = debug;
    ValueMap eventOptions = track.integrations().getValueMap("Adobe Analytics");
    if (eventOptions != null && eventOptions.getString("ovpName") != null) {
      config.ovp = eventOptions.getString("ovpName");
    } else if (eventOptions != null && eventOptions.getString("ovp_name") != null) {
      config.ovp = eventOptions.getString("ovp_name");
    } else if (eventOptions != null && eventOptions.getString("ovp") != null) {
      config.ovp = eventOptions.getString("ovp");
    } else {
      config.ovp = "unknown";
    }

    playback = new PlaybackDelegate();
    heartbeat = heartbeatFactory.get(playback, config);
    sessionStarted = true;

    VideoEvent event = new VideoEvent(track);

    heartbeat.trackSessionStart(event.getMediaObject(), event.getContextData());
    logger.verbose("heartbeat.trackSessionStart(MediaObject);");
  }

  private void trackVideoPlaybackPaused() {
    playback.pausePlayhead();
    heartbeat.trackPause();
    logger.verbose("heartbeat.trackPause();");
  }

  private void trackVideoPlaybackResumed() {
    playback.unPausePlayhead();
    heartbeat.trackPlay();
    logger.verbose("heartbeat.trackPlay();");
  }

  private void trackVideoContentStarted(TrackPayload track) {
    VideoEvent event = new VideoEvent(track);

    if (event.properties.getDouble("position", 0) > 0) {
      playback.updatePlayheadPosition(event.properties.getLong("position", 0));
    }

    heartbeat.trackPlay();
    logger.verbose("heartbeat.trackPlay();");
    trackAdobeEvent(
        MediaHeartbeat.Event.ChapterStart, event.getChapterObject(), event.getContextData());
  }

  private void trackVideoContentCompleted() {
    trackAdobeEvent(MediaHeartbeat.Event.ChapterComplete, null, null);
    heartbeat.trackComplete();
    logger.verbose("heartbeat.trackComplete();");
  }

  private void trackVideoPlaybackCompleted() {
    heartbeat.trackSessionEnd();
    logger.verbose("heartbeat.trackSessionEnd();");
  }

  private void trackVideoPlaybackBufferStarted() {
    playback.pausePlayhead();
    trackAdobeEvent(MediaHeartbeat.Event.BufferStart, null, null);
  }

  private void trackVideoPlaybackBufferCompleted() {
    playback.unPausePlayhead();
    trackAdobeEvent(MediaHeartbeat.Event.BufferComplete, null, null);
  }

  private void trackAdobeEvent(
      MediaHeartbeat.Event eventName, MediaObject mediaObject, Map<String, String> cdata) {
    heartbeat.trackEvent(eventName, mediaObject, cdata);
    logger.verbose("heartbeat.trackEvent(%s, %s, %s);", eventName, mediaObject, cdata);
  }

  private void trackVideoPlaybackSeekStarted() {
    playback.pausePlayhead();
    trackAdobeEvent(MediaHeartbeat.Event.SeekStart, null, null);
  }

  private void trackVideoPlaybackSeekCompleted(TrackPayload track) {
    Properties seekProperties = track.properties();
    long seekPosition = seekProperties.getLong("seekPosition", 0);
    if (seekPosition == 0) {
      seekPosition = seekProperties.getLong("seek_position", 0);
    }
    playback.updatePlayheadPosition(seekPosition);
    playback.unPausePlayhead();
    trackAdobeEvent(MediaHeartbeat.Event.SeekComplete, null, null);
  }

  private void trackVideoAdBreakStarted(TrackPayload track) {
    VideoEvent event = new VideoEvent(track, true);
    trackAdobeEvent(
        MediaHeartbeat.Event.AdBreakStart, event.getAdBreakObject(), event.getContextData());
  }

  private void trackVideoAdBreakCompleted() {
    trackAdobeEvent(MediaHeartbeat.Event.AdBreakComplete, null, null);
  }

  private void trackVideoAdStarted(TrackPayload track) {
    VideoEvent event = new VideoEvent(track, true);
    trackAdobeEvent(MediaHeartbeat.Event.AdStart, event.getAdObject(), event.getContextData());
  }

  private void trackVideoAdSkipped() {
    trackAdobeEvent(MediaHeartbeat.Event.AdSkip, null, null);
  }

  private void trackVideoAdCompleted() {
    trackAdobeEvent(MediaHeartbeat.Event.AdComplete, null, null);
  }

  private void trackVideoPlaybackInterrupted() {
    playback.pausePlayhead();
  }

  private void trackVideoQualityUpdated(TrackPayload track) {
    playback.createAndUpdateQosObject(track.properties());
  }

  PlaybackDelegate getPlayback() {
    return playback;
  }

  boolean isSessionStarted() {
    return sessionStarted;
  }

  void setDebugLogging(boolean debug) {
    this.debug = debug;
  }

  ContextDataConfiguration getContextDataConfiguration() {
    return contextDataConfiguration;
  }

  /**
   * Allows to redefine the context data configuration. Only used for testing.
   *
   * @param contextDataConfiguration New context data configuration.
   */
  void setContextDataConfiguration(ContextDataConfiguration contextDataConfiguration) {
    this.contextDataConfiguration = contextDataConfiguration;
  }

  /** A wrapper for video metadata and properties. */
  class VideoEvent {
    private Map<String, String> metadata;
    private Properties properties;
    private BasePayload payload;

    /**
     * Creates video properties from the ones provided in the event.
     *
     * @param payload Event Payload.
     */
    VideoEvent(BasePayload payload) {
      this(payload, false);
    }

    /**
     * Creates video properties from the ones provided in the event.
     *
     * @param payload Event Payload.
     * @param isAd Determines if the video is an ad.
     */
    VideoEvent(BasePayload payload, boolean isAd) {
      this.payload = payload;
      metadata = new HashMap<>();
      properties = new Properties();
      if (payload.containsKey("properties")) {
        ValueMap eventProperties = payload.getValueMap("properties");
        properties.putAll(eventProperties);

        if (isAd) {
          mapAdProperties(eventProperties);
        } else {
          mapVideoProperties(eventProperties);
        }
      }
    }

    private void mapVideoProperties(ValueMap eventProperties) {
      for (String key : eventProperties.keySet()) {

        if (VIDEO_METADATA_KEYS.containsKey(key)) {
          String propertyKey = VIDEO_METADATA_KEYS.get(key);
          metadata.put(propertyKey, String.valueOf(eventProperties.get(key)));
          properties.remove(key);
        }
      }

      if (properties.containsKey("livestream")) {
        String format = MediaHeartbeat.StreamType.LIVE;
        if (!properties.getBoolean("livestream", false)) {
          format = MediaHeartbeat.StreamType.VOD;
        }

        metadata.put(MediaHeartbeat.VideoMetadataKeys.STREAM_FORMAT, format);
        properties.remove("livestream");
      }
    }

    private void mapAdProperties(ValueMap eventProperties) {
      for (String key : eventProperties.keySet()) {

        if (AD_METADATA_KEYS.containsKey(key)) {
          String propertyKey = AD_METADATA_KEYS.get(key);
          metadata.put(propertyKey, String.valueOf(eventProperties.get(key)));
          properties.remove(key);
        }
      }
    }

    Map<String, String> getContextData() {

      Properties extraProperties = new Properties();
      extraProperties.putAll(properties);

      // Remove products from extra properties
      extraProperties.remove("products");

      // Remove video metadata keys
      for (String key : VIDEO_METADATA_KEYS.keySet()) {
        extraProperties.remove(key);
      }

      // Remove ad metadata keys
      for (String key : AD_METADATA_KEYS.keySet()) {
        extraProperties.remove(key);
      }

      // Remove media object keys
      for (String key :
          new String[] {
            "title",
            "indexPosition",
            "index_position",
            "position",
            "totalLength",
            "total_length",
            "startTime",
            "start_time"
          }) {
        extraProperties.remove(key);
      }

      Map<String, String> cdata = new HashMap<>();

      for (String field : contextDataConfiguration.getEventFieldNames()) {
        Object value = null;
        try {
          value = contextDataConfiguration.searchValue(field, payload);
        } catch (IllegalArgumentException e) {
          // Ignore.
        }

        if (value != null) {
          String variable = contextDataConfiguration.getVariableName(field);
          cdata.put(variable, String.valueOf(value));
          extraProperties.remove(field);
        }
      }

      // Add extra properties.
      for (String extraProperty : extraProperties.keySet()) {
        String variable = contextDataConfiguration.getPrefix() + extraProperty;
        cdata.put(variable, extraProperties.getString(extraProperty));
      }

      return cdata;
    }

    MediaObject getChapterObject() {
      if (!payload.containsKey("properties")) {
        return null;
      }

      ValueMap eventProperties = payload.getValueMap("properties");

      String title = eventProperties.getString("title");
      long indexPosition =
          eventProperties.getLong("indexPosition", 1); // Segment does not spec this
      if (indexPosition == 1) {
        indexPosition = eventProperties.getLong("index_position", 1);
      }
      double totalLength = eventProperties.getDouble("totalLength", 0);
      if (totalLength == 0) {
        totalLength = eventProperties.getDouble("total_length", 0);
      }
      double startTime = eventProperties.getDouble("startTime", 0);
      if (startTime == 0) {
        startTime = eventProperties.getDouble("start_time", 0);
      }

      MediaObject media =
          MediaHeartbeat.createChapterObject(title, indexPosition, totalLength, startTime);
      media.setValue(MediaHeartbeat.MediaObjectKey.StandardVideoMetadata, metadata);
      return media;
    }

    MediaObject getMediaObject() {
      if (!payload.containsKey("properties")) {
        return null;
      }

      ValueMap eventProperties = payload.getValueMap("properties");

      String title = eventProperties.getString("title");
      String contentAssetId = eventProperties.getString("contentAssetId");
      if (contentAssetId == null || contentAssetId.trim().isEmpty()) {
        contentAssetId = eventProperties.getString("content_asset_id");
      }
      double totalLength = eventProperties.getDouble("totalLength", 0);
      if (totalLength == 0) {
        totalLength = eventProperties.getDouble("total_length", 0);
      }
      String format = MediaHeartbeat.StreamType.LIVE;
      if (!eventProperties.getBoolean("livestream", false)) {
        format = MediaHeartbeat.StreamType.VOD;
      }

      MediaObject media =
          MediaHeartbeat.createMediaObject(title, contentAssetId, totalLength, format);
      media.setValue(MediaHeartbeat.MediaObjectKey.StandardVideoMetadata, metadata);
      return media;
    }

    MediaObject getAdObject() {
      if (!payload.containsKey("properties")) {
        return null;
      }

      ValueMap eventProperties = payload.getValueMap("properties");

      String title = eventProperties.getString("title");
      String assetId = eventProperties.getString("assetId");
      if (assetId == null || assetId.trim().isEmpty()) {
        assetId = eventProperties.getString("asset_id");
      }
      long indexPosition = eventProperties.getLong("indexPosition", 1);
      if (indexPosition == 1) {
        indexPosition = eventProperties.getLong("index_position", 1);
      }
      double totalLength = eventProperties.getDouble("totalLength", 0);
      if (totalLength == 0) {
        totalLength = eventProperties.getDouble("total_length", 0);
      }

      MediaObject media = MediaHeartbeat.createAdObject(title, assetId, indexPosition, totalLength);

      media.setValue(MediaHeartbeat.MediaObjectKey.StandardAdMetadata, metadata);
      return media;
    }

    MediaObject getAdBreakObject() {
      if (!payload.containsKey("properties")) {
        return null;
      }

      ValueMap eventProperties = payload.getValueMap("properties");

      String title = eventProperties.getString("title");
      long indexPosition =
          eventProperties.getLong("indexPosition", 1); // Segment does not spec this
      if (indexPosition == 1) {
        indexPosition = eventProperties.getLong("index_position", 1);
      }
      double startTime = eventProperties.getDouble("startTime", 0);
      if (startTime == 0) {
        startTime = eventProperties.getDouble("start_time", 0);
      }
      MediaObject media = MediaHeartbeat.createAdBreakObject(title, indexPosition, startTime);

      return media;
    }

    Map<String, String> getMetadata() {
      return metadata;
    }

    Properties getProperties() {
      return properties;
    }

    BasePayload getEventPayload() {
      return payload;
    }
  }
}
