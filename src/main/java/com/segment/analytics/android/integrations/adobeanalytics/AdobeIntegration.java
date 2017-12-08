package com.segment.analytics.android.integrations.adobeanalytics;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import com.adobe.mobile.Analytics;
import com.adobe.mobile.Config;
import com.adobe.primetime.va.simple.MediaHeartbeat;
import com.adobe.primetime.va.simple.MediaHeartbeat.MediaHeartbeatDelegate;
import com.adobe.primetime.va.simple.MediaHeartbeatConfig;
import com.adobe.primetime.va.simple.MediaObject;
import com.segment.analytics.Properties;
import com.segment.analytics.Properties.Product;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.GroupPayload;
import com.segment.analytics.integrations.IdentifyPayload;
import com.segment.analytics.integrations.Integration;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.ScreenPayload;
import com.segment.analytics.integrations.TrackPayload;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.segment.analytics.internal.Utils.isNullOrEmpty;

/**
 * Adobe Analytics is an analytics tracking tool and dashboard that helps you understand your
 * customer segments via activity and video tracking.
 *
 * @see <a href="http://www.adobe.com/data-analytics-cloud/analytics.html">Adobe Analytics</a>
 * @see <a href="https://segment.com/docs/integrations/adobe-analytics/">Adobe Integration</a>
 * @see <a
 *     href="https://github.com/Adobe-Marketing-Cloud/mobile-services/releases/tag/v4.14.0-Android">Adobe
 *     Android SDK</a>
 */
public class AdobeIntegration extends Integration<Void> {

  public static final Factory FACTORY =
      new Factory() {
        @Override
        public Integration<?> create(ValueMap settings, com.segment.analytics.Analytics analytics) {
          Logger logger = analytics.logger(ADOBE_KEY);
          return new AdobeIntegration(settings, analytics, logger, HeartbeatFactory.REAL);
        }

        @Override
        public String key() {
          return ADOBE_KEY;
        }
      };

  private static final String ADOBE_KEY = "Adobe Analytics";

  private static final Map<String, String> ECOMMERCE_EVENT_LIST = getEcommerceEventList();

  private static Map<String, String> getEcommerceEventList() {
    Map<String, String> ecommerceEventList = new HashMap<>();
    ecommerceEventList.put("Order Completed", "purchase");
    ecommerceEventList.put("Product Added", "scAdd");
    ecommerceEventList.put("Product Removed", "scRemove");
    ecommerceEventList.put("Checkout Started", "scCheckout");
    ecommerceEventList.put("Cart Viewed", "scView");
    ecommerceEventList.put("Product Viewed", "prodView");
    return ecommerceEventList;
  }

  private static final Set<String> VIDEO_EVENT_LIST =
      new HashSet<>(
          Arrays.asList(
              "Video Playback Started",
              "Video Content Started",
              "Video Playback Paused",
              "Video Playback Resumed",
              "Video Content Completed",
              "Video Playback Completed",
              "Video Playback Buffer Started",
              "Video Playback Buffer Completed",
              "Video Playback Seek Started",
              "Video Playback Seek Completed",
              "Video Ad Break Started",
              "Video Ad Break Completed",
              "Video Ad Started",
              "Video Ad Completed",
              "Video Playback Interrupted",
              "Video Quality Updated"));

  private static final Map<String, String> VIDEO_METADATA_MAP = getStandardVideoMetadataMap();

  private static Map<String, String> getStandardVideoMetadataMap() {
    Map<String, String> videoPropertyList = new HashMap<>();
    videoPropertyList.put("assetId", MediaHeartbeat.VideoMetadataKeys.ASSET_ID);
    videoPropertyList.put("program", MediaHeartbeat.VideoMetadataKeys.SHOW);
    videoPropertyList.put("season", MediaHeartbeat.VideoMetadataKeys.SEASON);
    videoPropertyList.put("episode", MediaHeartbeat.VideoMetadataKeys.EPISODE);
    videoPropertyList.put("genre", MediaHeartbeat.VideoMetadataKeys.GENRE);
    videoPropertyList.put("channel", MediaHeartbeat.VideoMetadataKeys.NETWORK);
    videoPropertyList.put("airdate", MediaHeartbeat.VideoMetadataKeys.FIRST_AIR_DATE);
    videoPropertyList.put("publisher", MediaHeartbeat.AdMetadataKeys.ADVERTISER);
    return videoPropertyList;
  }

  private final com.segment.analytics.Analytics analytics;
  private final Logger logger;
  private MediaHeartbeat heartbeat;
  private HeartbeatFactory heartbeatFactory;
  PlaybackDelegate playbackDelegate;
  final boolean adobeLogLevel;
  final String heartbeatTrackingServer;
  final String packageName;
  final boolean ssl;
  Map<String, Object> eventsV2;
  Map<String, Object> contextValues;
  String productIdentifier;

  AdobeIntegration(
      ValueMap settings,
      com.segment.analytics.Analytics analytics,
      Logger logger,
      HeartbeatFactory heartbeatFactory) {
    this.eventsV2 = settings.getValueMap("eventsV2");
    this.contextValues = settings.getValueMap("contextValues");
    this.productIdentifier = settings.getString("productIdentifier");
    this.analytics = analytics;
    this.heartbeatFactory = heartbeatFactory;
    this.heartbeatTrackingServer = settings.getString("heartbeatTrackingServer");
    this.ssl = settings.getBoolean("ssl", false);
    this.logger = logger;

    Context context = analytics.getApplication();

    if (context.getPackageName() != null) {
      this.packageName = context.getPackageName();
    } else {
      // default app version to "unknown" if not otherwise present b/c Adobe requires this value
      packageName = "unknown";
    }

    this.adobeLogLevel = logger.logLevel.equals(com.segment.analytics.Analytics.LogLevel.VERBOSE);
    Config.setDebugLogging(adobeLogLevel);
  }

  /*
   * PlaybackDelegate implements Adobe's MediaHeartbeatDelegate interface. This implementation allows us
   * to return the position of a video playhead during a video session.
   */
  static class PlaybackDelegate implements MediaHeartbeatDelegate {
    /**
     * The system time in millis at which the playhead is first set or updated. The playhead is
     * first set upon instantiation of the PlaybackDelegate. The value is updated whenever {@link
     * #updatePlayheadPosition(Long)} is invoked.
     */
    long initialTime;
    /** The current playhead position in seconds */
    long playheadPosition;
    /** The position of the playhead in seconds when the video was paused */
    long pausedPlayheadPosition;
    /** The system time in millis at which {@link #pausePlayhead} was invoked */
    long pauseStartedTime;
    /**
     * The updated playhead position - this variable is assigned to the value a customer passes as
     * properties.seekPosition in a "Video Playback Seek Completed" event or properties.position in
     * a "Video Content Started" event
     */
    long updatedPlayheadPosition;
    /** The total time in seconds a video has been in a paused state during a video session */
    long offset = 0;
    /** Whether the video playhead is in a paused state. */
    boolean isPaused = false;
    /**
     * Quality of service object. This is created and updated upon receipt of a "Video Quality
     * Updated" event, which triggers {@link #createAndUpdateQosObject(Properties)}.
     */
    MediaObject qosData;

    public PlaybackDelegate() {
      this.initialTime = System.currentTimeMillis();
    }

    /**
     * Creates a quality of service object.
     *
     * @param properties Properties object from a "Video Quality Updated" event, which triggers
     *     invocation of this method.
     */
    public void createAndUpdateQosObject(Properties properties) {
      qosData =
          MediaHeartbeat.createQoSObject(
              properties.getLong("bitrate", 0),
              properties.getDouble("startupTime", 0),
              properties.getDouble("fps", 0),
              properties.getLong("droppedFrames", 0));
    }

    /** Adobe invokes this method once every ten seconds to report quality of service data. */
    @Override
    public MediaObject getQoSObject() {
      return qosData;
    }

    /**
     * Adobe invokes this method once per second to resolve the current position of the video
     * playhead. Unless paused, this method increments the value of {@link #playheadPosition} by one
     * every second by calling {@link #incrementPlayheadPosition()}
     */
    @Override
    public Double getCurrentPlaybackTime() {
      if (!isPaused) {
        incrementPlayheadPosition();
        return (double) playheadPosition;
      }
      return (double) pausedPlayheadPosition;
    }

    /**
     * {@link #getCurrentPlaybackTime()} invokes this method once per second to resolve the current
     * location of the video playhead:
     *
     * <p>updatedPlayheadPosition + (currentSystemTime - videoSessionStartTime) - offset
     *
     * <p>{@link #updatedPlayheadPosition} represents a user-specified position of the playhead.
     * This is useful for updating the playhead position after a "Video Content Started" or "Video
     * Playback Seek Completed" event. The user must provide the updated playhead position in order
     * for this method to update the playhead position here properly.
     */
    private void incrementPlayheadPosition() {
      this.playheadPosition =
          updatedPlayheadPosition + ((System.currentTimeMillis() - initialTime) / 1000) - offset;
    }

    /**
     * Stores the current playhead position in {@link #pausedPlayheadPosition}. Also stores the
     * system time at which the video was paused in {@link #pauseStartedTime}. Sets {@link
     * #isPaused} to true so {@link #getCurrentPlaybackTime()} knows the video is in a paused state.
     */
    public void pausePlayhead() {
      this.pausedPlayheadPosition = playheadPosition;
      this.pauseStartedTime = System.currentTimeMillis();
      this.isPaused = true;
    }

    /**
     * This method sets the {@link #isPaused} flag to false, as well as calculates the {@link
     * #offset} value. {@link #offset} represents the total cumulative time in seconds a video was
     * in a paused state during a session. If no offset exists:
     *
     * <p>offset = currentSystemTime - timePlayerWasPaused
     *
     * <p>Otherwise, the offset is added to the existing offset value. This may be the case if a
     * user pauses and unpauses the video many times during a single session:
     *
     * <p>offset = originalOffset + (currentSystemTime - timePlayerWasPaused)
     */
    public void unPausePlayhead() {
      if (offset == 0) {
        offset = (System.currentTimeMillis() - pauseStartedTime) / 1000;
      } else {
        offset = offset + (System.currentTimeMillis() - pauseStartedTime) / 1000;
      }
      isPaused = false;
    }

    /**
     * Resumes invocation of {@link #getCurrentPlaybackTime()} by setting {@link #isPaused} to
     * false. This is only called when a customer sends a "Video Playback Seek Completed" event.
     */
    public void resumePlayheadAfterSeeking() {
      this.isPaused = false;
    }

    /**
     * Updates member variables {@link #initialTime} and {@link #updatedPlayheadPosition} whenever
     * either a "Video Playback Seek Completed" or "Video Content Started" event is received AND
     * contains properties.seekPosition or properties.position, respectively. After invocation,
     * {@link #initialTime} is assigned to the system time at which the video event was received.
     *
     * @param playheadPosition properties.position passed by the customer into a "Video Playback
     *     Seek Completed" or "Video Content Started" event. This value is required for accurate
     *     reporting in the Adobe dashboard. It defaults to 0.
     */
    public void updatePlayheadPosition(Long playheadPosition) {
      this.initialTime = System.currentTimeMillis();
      this.updatedPlayheadPosition = playheadPosition;
    }
  }

  interface HeartbeatFactory {

    MediaHeartbeat get(MediaHeartbeatDelegate delegate, MediaHeartbeatConfig config);

    HeartbeatFactory REAL =
        new HeartbeatFactory() {
          @Override
          public MediaHeartbeat get(MediaHeartbeatDelegate delegate, MediaHeartbeatConfig config) {
            return new MediaHeartbeat(delegate, config);
          }
        };
  }

  @Override
  public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    super.onActivityCreated(activity, savedInstanceState);

    Config.setContext(activity.getApplicationContext());
    logger.verbose("Config.setContext(%s);", activity.getApplicationContext());
  }

  @Override
  public void onActivityPaused(Activity activity) {
    super.onActivityPaused(activity);

    Config.pauseCollectingLifecycleData();
    logger.verbose("Config.pauseCollectingLifecycleData();");
  }

  @Override
  public void onActivityResumed(Activity activity) {
    super.onActivityResumed(activity);

    Config.collectLifecycleData(activity);
    logger.verbose("Config.collectLifecycleData(%s);", activity);
  }

  @Override
  public void identify(IdentifyPayload identify) {
    super.identify(identify);

    String userId = identify.userId();
    if (isNullOrEmpty(userId)) return;
    Config.setUserIdentifier(userId);
    logger.verbose("Config.setUserIdentifier(%s);", userId);
  }

  @Override
  public void screen(ScreenPayload screen) {
    super.screen(screen);

    Properties properties = screen.properties();

    if (isNullOrEmpty(properties)) {
      Analytics.trackState(screen.name(), null);
      logger.verbose("Analytics.trackState(%s, %s);", screen.name(), null);
      return;
    }

    Map<String, Object> mappedProperties = mapProperties(properties);
    Analytics.trackState(screen.name(), mappedProperties);
    logger.verbose("Analytics.trackState(%s, %s);", screen.name(), mappedProperties);
  }

  @Override
  public void track(TrackPayload track) {
    super.track(track);

    String eventName = track.event();
    Properties properties = track.properties();

    if (VIDEO_EVENT_LIST.contains(eventName)) {
      trackVideo(eventName, track);
      return;
    }

    if (!(ECOMMERCE_EVENT_LIST.containsKey(eventName)) && isNullOrEmpty(eventsV2)) {
      logger.verbose(
          "Event must be either configured in Adobe and in the Segment EventsV2 setting or "
              + "a reserved Adobe Ecommerce event.");
      return;
    }
    if ((!isNullOrEmpty(eventsV2))
        && eventsV2.containsKey(eventName)
        && ECOMMERCE_EVENT_LIST.containsKey(eventName)) {
      logger.verbose(
          "Segment currently does not support mapping specced ecommerce events to "
              + "custom Adobe events.");
      return;
    }
    Map<String, Object> mappedProperties = null;
    if (!isNullOrEmpty(eventsV2) && eventsV2.containsKey(eventName)) {
      eventName = String.valueOf(eventsV2.get(eventName));
      mappedProperties = (isNullOrEmpty(properties)) ? null : mapProperties(properties);
    }
    if (ECOMMERCE_EVENT_LIST.containsKey(eventName)) {
      eventName = ECOMMERCE_EVENT_LIST.get(eventName);
      mappedProperties = (isNullOrEmpty(properties)) ? null : mapEcommerce(eventName, properties);
    }

    Analytics.trackAction(eventName, mappedProperties);
    logger.verbose("Analytics.trackAction(%s, %s);", eventName, mappedProperties);
  }

  private Map<String, Object> mapProperties(Properties properties) {
    Properties propertiesCopy = new Properties();
    propertiesCopy.putAll(properties);

    // if a products array exists, remove it now because we'll have already mapped it in ecommerce properties
    // if not, it shouldn't exist because a products array is only specced for ecommerce events
    if (propertiesCopy.containsKey("products")) {
      propertiesCopy.remove("products");
    }

    Map<String, Object> mappedProperties = new HashMap<>();

    if (!isNullOrEmpty(contextValues)) {
      for (Map.Entry<String, Object> entry : properties.entrySet()) {
        String property = entry.getKey();
        Object value = entry.getValue();

        if (contextValues.containsKey(property)) {
          mappedProperties.put(String.valueOf(contextValues.get(property)), value);
        }
      }
    }
    return mappedProperties;
  }

  private Map<String, Object> mapEcommerce(String eventName, Properties properties) {
    Map<String, Object> contextData = new HashMap<>();
    if (!isNullOrEmpty(properties)) {
      StringBuilder productStringBuilder = new StringBuilder();
      Map<String, Object> productProperties = new HashMap<>();
      String productsString;

      List<Product> products = properties.products();

      if (!isNullOrEmpty(products)) {
        for (int i = 0; i < products.size(); i++) {
          Product product = products.get(i);
          productProperties.putAll(product);

          String productString = ecommerceStringBuilder(productProperties);

          // early return where product name is passed incorrectly
          if (productString.equals("")) {
            return null;
          }

          if (i < products.size() - 1) {
            productStringBuilder.append(productString).append(",");
          } else {
            productStringBuilder.append(productString);
          }
        }
        productsString = productStringBuilder.toString();
      } else {
        productProperties.putAll(properties);
        productsString = ecommerceStringBuilder(productProperties);
      }
      //finally, add a purchaseid to context data if it's been mapped by customer
      if (properties.containsKey("orderId")) {
        contextData.put("purchaseid", properties.getString("orderId"));
      }
      contextData.put("&&events", eventName);
      // only add the &&products variable if a product exists
      if (productsString.length() > 0) {
        contextData.put("&&products", productsString);
      }
      // add all customer-mapped properties to ecommerce context data map
      contextData.putAll(mapProperties(properties));
    }
    return contextData;
  }

  /**
   * Builds a string out of product properties category, name, quantity and price to send to Adobe.
   *
   * @param productProperties A map of product properties.
   * @return A single string of product properties, in the format `category;name;quantity;price;
   *     examples: `athletic;shoes;1;10.0`, `;shoes;1;0.0`
   */
  private String ecommerceStringBuilder(Map<String, Object> productProperties) {
    if (productProperties.get(productIdentifier) == null
        && productProperties.get("productId") == null) {
      logger.verbose(
          "You must provide a name for each product to pass an ecommerce event"
              + "to Adobe Analytics.");
      return "";
    }
    String name;
    if (productIdentifier.equals("id")) {
      name =
          getString(productProperties, "productId") != null
              ? getString(productProperties, "productId")
              : getString(productProperties, "id");
    } else {
      name = getString(productProperties, productIdentifier);
    }
    String category = getString(productProperties, "category");
    String quantity =
        (productProperties.get("quantity") == null)
            ? "1"
            : getString(productProperties, "quantity");
    String price =
        (productProperties.get("price") == null)
            ? "0"
            : String.valueOf(
                Double.parseDouble((getString(productProperties, "price")))
                    * Double.parseDouble(quantity));
    return category + ";" + name + ";" + quantity + ";" + price;
  }

  private String getString(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value == null) {
      return "";
    }
    return String.valueOf(value);
  }

  @Override
  public void group(GroupPayload group) {
    super.group(group);
  }

  @Override
  public void flush() {
    super.flush();

    Analytics.sendQueuedHits();
    logger.verbose("Analytics.sendQueuedHits();");
  }

  @Override
  public void reset() {
    super.reset();

    Config.setUserIdentifier(null);
    logger.verbose("Config.setUserIdentifier(null);");
  }

  private void trackVideo(String eventName, TrackPayload track) {

    if (heartbeatTrackingServer == null) {
      logger.verbose(
          "Please enter a Heartbeat Tracking Server URL in your Segment UI "
              + "Settings in order to send video events to Adobe Analytics");
      return;
    }

    switch (eventName) {
      case "Video Playback Started":
        Context context = analytics.getApplication();
        Properties properties = track.properties();
        MediaHeartbeatConfig config = new MediaHeartbeatConfig();

        config.trackingServer = heartbeatTrackingServer;
        if (properties.get("channel") != null) {
          config.channel = properties.getString("channel");
        } else {
          config.channel = "";
        }
        config.appVersion = packageName;
        ValueMap eventOptions = track.integrations().getValueMap("Adobe Analytics");
        if (eventOptions != null && eventOptions.getString("ovpName") != null) {
          config.ovp = eventOptions.getString("ovpName");
        } else {
          config.ovp = "unknown";
        }
        if (properties.get("videoPlayer") != null) {
          config.playerName = properties.getString("videoPlayer");
        } else {
          config.playerName = "unknown";
        }
        config.ssl = ssl;
        config.debugLogging = adobeLogLevel;

        this.playbackDelegate = new PlaybackDelegate();
        heartbeat = heartbeatFactory.get(playbackDelegate, config);

        Map<String, String> standardVideoMetadata = new HashMap<>();
        Properties videoProperties = mapStandardVideoMetadata(properties, standardVideoMetadata);
        HashMap<String, String> videoMetadata = new HashMap<>();
        videoMetadata.putAll(videoProperties.toStringMap());

        MediaObject mediaInfo =
            MediaHeartbeat.createMediaObject(
                properties.getString("title"),
                properties.getString("contentAssetId"),
                properties.getDouble("totalLength", 0),
                properties.getBoolean("livestream", false)
                    ? MediaHeartbeat.StreamType.LIVE
                    : MediaHeartbeat.StreamType.VOD);

        mediaInfo.setValue(
            MediaHeartbeat.MediaObjectKey.StandardVideoMetadata, standardVideoMetadata);

        heartbeat.trackSessionStart(mediaInfo, videoMetadata);
        break;

      case "Video Playback Paused":
        playbackDelegate.pausePlayhead();
        heartbeat.trackPause();
        break;

      case "Video Playback Resumed":
        playbackDelegate.unPausePlayhead();
        heartbeat.trackPlay();
        break;

      case "Video Content Started":
        Properties videoContentProperties = track.properties();
        Map<String, String> standardChapterMetadata = new HashMap<>();
        Properties chapterProperties =
            mapStandardVideoMetadata(videoContentProperties, standardChapterMetadata);
        HashMap<String, String> chapterMetadata = new HashMap<>();
        chapterMetadata.putAll(chapterProperties.toStringMap());

        MediaObject mediaChapter =
            MediaHeartbeat.createChapterObject(
                videoContentProperties.getString("title"),
                videoContentProperties.getLong("indexPosition", 1), // Segment does not spec this
                videoContentProperties.getDouble("totalLength", 0),
                videoContentProperties.getDouble("startTime", 0));

        mediaChapter.setValue(
            MediaHeartbeat.MediaObjectKey.StandardVideoMetadata, standardChapterMetadata);

        if (videoContentProperties.getDouble("position", 0) > 0) {
          playbackDelegate.updatePlayheadPosition(videoContentProperties.getLong("position", 0));
        }
        heartbeat.trackPlay();
        heartbeat.trackEvent(MediaHeartbeat.Event.ChapterStart, mediaChapter, chapterMetadata);
        break;

      case "Video Content Completed":
        heartbeat.trackEvent(MediaHeartbeat.Event.ChapterComplete, null, null);
        heartbeat.trackComplete();
        break;

      case "Video Playback Completed":
        heartbeat.trackSessionEnd();
        break;

      case "Video Playback Buffer Started":
        playbackDelegate.pausePlayhead();
        heartbeat.trackEvent(MediaHeartbeat.Event.BufferStart, null, null);
        break;

      case "Video Playback Buffer Completed":
        playbackDelegate.unPausePlayhead();
        heartbeat.trackEvent(MediaHeartbeat.Event.BufferComplete, null, null);
        break;

      case "Video Playback Seek Started":
        playbackDelegate.pausePlayhead();
        heartbeat.trackEvent(MediaHeartbeat.Event.SeekStart, null, null);
        break;

      case "Video Playback Seek Completed":
        Properties seekProperties = track.properties();
        playbackDelegate.updatePlayheadPosition(seekProperties.getLong("seekPosition", 0));
        playbackDelegate.resumePlayheadAfterSeeking();
        heartbeat.trackEvent(MediaHeartbeat.Event.SeekComplete, null, null);
        break;

      case "Video Ad Break Started":
        Properties videoAdBreakProperties = track.properties();
        MediaObject mediaAdBreakInfo =
            MediaHeartbeat.createAdBreakObject(
                videoAdBreakProperties.getString("title"),
                videoAdBreakProperties.getLong("indexPosition", 1), // Segment does not spec this
                videoAdBreakProperties.getDouble("startTime", 0));

        heartbeat.trackEvent(MediaHeartbeat.Event.AdBreakStart, mediaAdBreakInfo, null);
        break;

      case "Video Ad Break Completed":
        heartbeat.trackEvent(MediaHeartbeat.Event.AdBreakComplete, null, null);
        break;

      case "Video Ad Started":
        Properties videoAdProperties = track.properties();
        Map<String, String> standardAdMetadata = new HashMap<>();
        Properties adProperties = mapStandardVideoMetadata(videoAdProperties, standardAdMetadata);
        HashMap<String, String> adMetadata = new HashMap<>();
        adMetadata.putAll(adProperties.toStringMap());

        MediaObject mediaAdInfo =
            MediaHeartbeat.createAdObject(
                videoAdProperties.getString("title"),
                videoAdProperties.getString("assetId"),
                videoAdProperties.getLong("indexPosition", 0),
                videoAdProperties.getDouble("totalLength", 0));

        // Delete assetId; otherwise helper function would map this incorrectly as standard video metadata
        // Adobe handles "assetId" differently for content events and ad events
        // This is the only property that is shared b/w ad and content events; all others are unique
        standardAdMetadata.remove(MediaHeartbeat.VideoMetadataKeys.ASSET_ID);
        mediaAdInfo.setValue(MediaHeartbeat.MediaObjectKey.StandardAdMetadata, standardAdMetadata);

        heartbeat.trackEvent(MediaHeartbeat.Event.AdStart, mediaAdInfo, adMetadata);
        break;

      case "Video Ad Skipped":
        heartbeat.trackEvent(MediaHeartbeat.Event.AdSkip, null, null);

      case "Video Ad Completed":
        heartbeat.trackEvent(MediaHeartbeat.Event.AdComplete, null, null);
        break;

      case "Video Playback Interrupted":
        playbackDelegate.pausePlayhead();
        break;

      case "Video Quality Updated":
        Properties videoQualityProperties = track.properties();
        playbackDelegate.createAndUpdateQosObject(videoQualityProperties);
        break;
    }
  }

  private Properties mapStandardVideoMetadata(
      Properties properties, Map<String, String> standardVideoMetadata) {
    Properties propertiesCopy = new Properties();
    propertiesCopy.putAll(properties);
    for (Map.Entry<String, Object> entry : properties.entrySet()) {
      String propertyKey = entry.getKey();
      String value = String.valueOf(entry.getValue());

      if (VIDEO_METADATA_MAP.containsKey(propertyKey)) {
        standardVideoMetadata.put(VIDEO_METADATA_MAP.get(propertyKey), value);
        propertiesCopy.remove(propertyKey);
      }
    }
    if (properties.containsKey("livestream")) {
      standardVideoMetadata.put(
          MediaHeartbeat.VideoMetadataKeys.STREAM_FORMAT,
          properties.getBoolean("livestream", false)
              ? MediaHeartbeat.StreamType.LIVE
              : MediaHeartbeat.StreamType.VOD);
      propertiesCopy.remove("livestream");
    }
    return propertiesCopy;
  }
}
