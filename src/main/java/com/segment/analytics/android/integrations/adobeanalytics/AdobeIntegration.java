package com.segment.analytics.android.integrations.adobeanalytics;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
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
import java.util.Collections;
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

  private static final Map<String, String> ECOMMERCE_EVENT_MAP = getEcommerceEventMap();

  private static Map<String, String> getEcommerceEventMap() {
    Map<String, String> ecommerceEventMap = new HashMap<>();
    ecommerceEventMap.put("Order Completed", "purchase");
    ecommerceEventMap.put("Product Added", "scAdd");
    ecommerceEventMap.put("Product Removed", "scRemove");
    ecommerceEventMap.put("Checkout Started", "scCheckout");
    ecommerceEventMap.put("Cart Viewed", "scView");
    ecommerceEventMap.put("Product Viewed", "prodView");
    return ecommerceEventMap;
  }

  private static final Set<String> VIDEO_EVENT_LIST =
      newSet(
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
          "Video Ad Skipped",
          "Video Ad Completed",
          "Video Playback Interrupted",
          "Video Quality Updated");

  private static final Map<String, String> VIDEO_METADATA_MAP = getStandardVideoMetadataMap();

  private static Map<String, String> getStandardVideoMetadataMap() {
    Map<String, String> videoPropertyList = new HashMap<>();
    videoPropertyList.put("assetId", MediaHeartbeat.VideoMetadataKeys.ASSET_ID);
    videoPropertyList.put("contentAssetId", MediaHeartbeat.VideoMetadataKeys.ASSET_ID);
    videoPropertyList.put("program", MediaHeartbeat.VideoMetadataKeys.SHOW);
    videoPropertyList.put("season", MediaHeartbeat.VideoMetadataKeys.SEASON);
    videoPropertyList.put("episode", MediaHeartbeat.VideoMetadataKeys.EPISODE);
    videoPropertyList.put("genre", MediaHeartbeat.VideoMetadataKeys.GENRE);
    videoPropertyList.put("channel", MediaHeartbeat.VideoMetadataKeys.NETWORK);
    videoPropertyList.put("airdate", MediaHeartbeat.VideoMetadataKeys.FIRST_AIR_DATE);
    videoPropertyList.put("publisher", MediaHeartbeat.VideoMetadataKeys.ORIGINATOR);
    videoPropertyList.put("rating", MediaHeartbeat.VideoMetadataKeys.RATING);
    return videoPropertyList;
  }

  // breaking this out into a separate map since this will likely grow over time as we spec more ad
  // properties Adobe expects; also prevents overlap with standard video metadata
  private static final Map<String, String> AD_METADATA_MAP = getStandardAdMetadataMap();

  private static Map<String, String> getStandardAdMetadataMap() {
    Map<String, String> adPropertyList = new HashMap<>();
    adPropertyList.put("publisher", MediaHeartbeat.AdMetadataKeys.ADVERTISER);
    return adPropertyList;
  }

  private final Logger logger;
  private MediaHeartbeat heartbeat;
  private HeartbeatFactory heartbeatFactory;
  PlaybackDelegate playbackDelegate;
  private final boolean adobeLogLevel;
  private final String heartbeatTrackingServerUrl;
  private final String packageName;
  private final boolean ssl;
  private AdobeAnalyticsClient adobeAnalytics;
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
    this.heartbeatFactory = heartbeatFactory;
    this.heartbeatTrackingServerUrl = settings.getString("heartbeatTrackingServerUrl");
    this.ssl = settings.getBoolean("ssl", false);
    this.adobeAnalytics = new AdobeAnalyticsClient.DefaultClient();
    this.logger = logger;

    Context context = analytics.getApplication();

    if (context.getPackageName() != null) {
      this.packageName = context.getPackageName();
    } else {
      // default app version to "unknown" if not otherwise present b/c Adobe requires this value
      packageName = "unknown";
    }

    this.adobeLogLevel = logger.logLevel.equals(com.segment.analytics.Analytics.LogLevel.VERBOSE);
    adobeAnalytics.setDebugLogging(adobeLogLevel);
    logger.verbose("Config.setDebugLogging(%b)", adobeLogLevel);
  }

  /**
   * Allows to set a different implementation. Used for testing.
   *
   * @param adobeAnalytics Adobe Analytics client.
   */
  void setClient(AdobeAnalyticsClient adobeAnalytics) {
    this.adobeAnalytics = adobeAnalytics;
  }

  /*
   * PlaybackDelegate implements Adobe's MediaHeartbeatDelegate interface. This implementation allows us
   * to return the position of a video playhead during a video session.
   */
  static class PlaybackDelegate implements MediaHeartbeatDelegate {

    /**
     * The system time in millis at which the playhead is first set or updated. The playhead is
     * first set upon instantiation of the PlaybackDelegate. The value is updated whenever {@link
     * #calculateCurrentPlayheadPosition()} is invoked.
     */
    long playheadPositionTime;
    /** The current playhead position in seconds. */
    long playheadPosition;
    /** Whether the video playhead is in a paused state. */
    boolean isPaused = false;
    /**
     * Quality of service object. This is created and updated upon receipt of a "Video Quality
     * Updated" event, which triggers {@link #createAndUpdateQosObject(Properties)}.
     */
    MediaObject qosData;

    PlaybackDelegate() {
      this.playheadPositionTime = System.currentTimeMillis();
    }

    /**
     * Creates a quality of service object.
     *
     * @param properties Properties object from a "Video Quality Updated" event, which triggers
     *     invocation of this method.
     */
    void createAndUpdateQosObject(Properties properties) {
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
     * every second by calling {@link #calculateCurrentPlayheadPosition()}
     */
    @Override
    public Double getCurrentPlaybackTime() {
      if (isPaused) {
        return (double) playheadPosition;
      }
      return (double) calculateCurrentPlayheadPosition();
    }

    /**
     * Stores the current playhead position in {@link #playheadPosition}. Also stores the system
     * time at which the video was paused in {@link #playheadPositionTime}. Sets {@link #isPaused}
     * to true so {@link #getCurrentPlaybackTime()} knows the video is in a paused state.
     */
    void pausePlayhead() {
      this.playheadPosition = calculateCurrentPlayheadPosition();
      this.playheadPositionTime = System.currentTimeMillis();
      this.isPaused = true;
    }

    /**
     * This method sets the {@link #isPaused} flag to false, as well as sets the {@link
     * #playheadPositionTime} to the time at which the video is unpaused.
     */
    void unPausePlayhead() {
      this.isPaused = false;
      this.playheadPositionTime = System.currentTimeMillis();
    }

    /**
     * Updates member variables {@link #playheadPositionTime} and {@link #playheadPosition} whenever
     * either a "Video Playback Seek Completed" or "Video Content Started" event is received AND
     * contains properties.seekPosition or properties.position, respectively. After invocation,
     * {@link #playheadPositionTime} is assigned to the system time at which the video event was
     * received.
     *
     * @param playheadPosition properties.position passed by the customer into a "Video Playback
     *     Seek Completed" or "Video Content Started" event. This value is required for accurate
     *     reporting in the Adobe dashboard. It defaults to 0.
     */
    void updatePlayheadPosition(long playheadPosition) {
      this.playheadPositionTime = System.currentTimeMillis();
      this.playheadPosition = playheadPosition;
    }

    /**
     * Internal helper function used to calculate the {@link #playheadPosition}.
     *
     * <p>System.currentTimeMillis retrieves the current time in milliseconds, then we calculate the
     * delta between the current time and the {@link #playheadPositionTime}, which is the system
     * time at the time a Segment Spec'd Video event is triggered.
     *
     * @return long playheadPosition
     */
    long calculateCurrentPlayheadPosition() {
      long currentTime = System.currentTimeMillis();
      long delta = (currentTime - this.playheadPositionTime) / 1000;
      return this.playheadPosition + delta;
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

    adobeAnalytics.setContext(activity.getApplicationContext());
    logger.verbose("Config.setContext(%s);", activity.getApplicationContext());
  }

  @Override
  public void onActivityPaused(Activity activity) {
    super.onActivityPaused(activity);

    adobeAnalytics.pauseCollectingLifecycleData();
    logger.verbose("Config.pauseCollectingLifecycleData();");
  }

  @Override
  public void onActivityResumed(Activity activity) {
    super.onActivityResumed(activity);

    adobeAnalytics.collectLifecycleData(activity);
    logger.verbose("Config.collectLifecycleData(%s);", activity);
  }

  @Override
  public void identify(IdentifyPayload identify) {
    super.identify(identify);

    String userId = identify.userId();
    if (isNullOrEmpty(userId)) {
      return;
    }
    adobeAnalytics.setUserIdentifier(userId);
    logger.verbose("Config.setUserIdentifier(%s);", userId);
  }

  @Override
  public void screen(ScreenPayload screen) {
    super.screen(screen);

    Properties properties = screen.properties();

    if (isNullOrEmpty(properties)) {
      adobeAnalytics.trackState(screen.name(), null);
      logger.verbose("Analytics.trackState(%s, %s);", screen.name(), null);
      return;
    }

    Map<String, Object> mappedProperties = mapProperties(properties);
    adobeAnalytics.trackState(screen.name(), mappedProperties);
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

    if (!(ECOMMERCE_EVENT_MAP.containsKey(eventName)) && isNullOrEmpty(eventsV2)) {
      logger.verbose(
          "Event must be either configured in Adobe and in the Segment EventsV2 setting or "
              + "a reserved Adobe Ecommerce event.");
      return;
    }
    if ((!isNullOrEmpty(eventsV2))
        && eventsV2.containsKey(eventName)
        && ECOMMERCE_EVENT_MAP.containsKey(eventName)) {
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
    if (ECOMMERCE_EVENT_MAP.containsKey(eventName)) {
      eventName = ECOMMERCE_EVENT_MAP.get(eventName);
      mappedProperties = (isNullOrEmpty(properties)) ? null : mapEcommerce(eventName, properties);
    }

    adobeAnalytics.trackAction(eventName, mappedProperties);
    logger.verbose("Analytics.trackAction(%s, %s);", eventName, mappedProperties);
  }

  private Properties mapProperties(Properties properties) {
    Properties propertiesCopy = new Properties();
    propertiesCopy.putAll(properties);

    // if a products array exists, remove it now because we'll have already mapped it in ecommerce properties
    // if not, it shouldn't exist because a products array is only specced for ecommerce events
    if (propertiesCopy.containsKey("products")) {
      propertiesCopy.remove("products");
    }

    Properties mappedProperties = new Properties();

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

    adobeAnalytics.flushQueue();
    logger.verbose("Analytics.sendQueuedHits();");
  }

  @Override
  public void reset() {
    super.reset();

    adobeAnalytics.setUserIdentifier(null);
    logger.verbose("Config.setUserIdentifier(null);");
  }

  private void trackVideo(String eventName, TrackPayload track) {

    if (heartbeatTrackingServerUrl == null) {
      logger.verbose(
          "Please enter a Heartbeat Tracking Server URL in your Segment UI "
              + "Settings in order to send video events to Adobe Analytics");
      return;
    }

    switch (eventName) {
      case "Video Playback Started":
        Properties properties = track.properties();
        MediaHeartbeatConfig config = new MediaHeartbeatConfig();

        config.trackingServer = heartbeatTrackingServerUrl;
        config.channel = getConfigProperty("channel", "", properties);
        config.playerName = getConfigProperty("videoPlayer", "unknown", properties);
        config.appVersion = packageName;
        config.ssl = ssl;
        config.debugLogging = adobeLogLevel;
        ValueMap eventOptions = track.integrations().getValueMap("Adobe Analytics");
        if (eventOptions != null && eventOptions.getString("ovpName") != null) {
          config.ovp = eventOptions.getString("ovpName");
        } else {
          config.ovp = "unknown";
        }

        this.playbackDelegate = new PlaybackDelegate();
        heartbeat = heartbeatFactory.get(playbackDelegate, config);

        Map<String, String> standardVideoMetadata = new HashMap<>();
        Properties videoProperties =
            mapStandardVideoMetadata(
                properties,
                standardVideoMetadata,
                // eventType
                "coreVideo");
        Properties videoMetadata = mapProperties(videoProperties);

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

        heartbeat.trackSessionStart(mediaInfo, videoMetadata.toStringMap());
        logger.verbose("heartbeat.trackSessionStart(MediaObject, %s);", videoMetadata);
        break;

      case "Video Playback Paused":
        playbackDelegate.pausePlayhead();
        heartbeat.trackPause();
        logger.verbose("heartbeat.trackPause();");
        break;

      case "Video Playback Resumed":
        playbackDelegate.unPausePlayhead();
        heartbeat.trackPlay();
        logger.verbose("heartbeat.trackPlay();");
        break;

      case "Video Content Started":
        Properties videoContentProperties = track.properties();
        Map<String, String> standardChapterMetadata = new HashMap<>();
        Properties chapterProperties =
            mapStandardVideoMetadata(
                videoContentProperties,
                standardChapterMetadata,
                // eventType
                "coreVideo");
        Properties chapterMetadata = mapProperties(chapterProperties);

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
        logger.verbose("heartbeat.trackPlay();");
        trackAdobeEvent(
            MediaHeartbeat.Event.ChapterStart, mediaChapter, chapterMetadata.toStringMap());
        break;

      case "Video Content Completed":
        trackAdobeEvent(MediaHeartbeat.Event.ChapterComplete, null, null);
        heartbeat.trackComplete();
        logger.verbose("heartbeat.trackComplete();");
        break;

      case "Video Playback Completed":
        heartbeat.trackSessionEnd();
        logger.verbose("heartbeat.trackSessionEnd();");
        break;

      case "Video Playback Buffer Started":
        playbackDelegate.pausePlayhead();
        trackAdobeEvent(MediaHeartbeat.Event.BufferStart, null, null);
        break;

      case "Video Playback Buffer Completed":
        playbackDelegate.unPausePlayhead();
        trackAdobeEvent(MediaHeartbeat.Event.BufferComplete, null, null);
        break;

      case "Video Playback Seek Started":
        playbackDelegate.pausePlayhead();
        trackAdobeEvent(MediaHeartbeat.Event.SeekStart, null, null);
        break;

      case "Video Playback Seek Completed":
        Properties seekProperties = track.properties();
        playbackDelegate.updatePlayheadPosition(seekProperties.getLong("seekPosition", 0));
        playbackDelegate.unPausePlayhead();
        trackAdobeEvent(MediaHeartbeat.Event.SeekComplete, null, null);
        break;

      case "Video Ad Break Started":
        Properties videoAdBreakProperties = track.properties();
        MediaObject mediaAdBreakInfo =
            MediaHeartbeat.createAdBreakObject(
                videoAdBreakProperties.getString("title"),
                videoAdBreakProperties.getLong("indexPosition", 1), // Segment does not spec this
                videoAdBreakProperties.getDouble("startTime", 0));
        Properties adBreakMetadata = mapProperties(videoAdBreakProperties);

        trackAdobeEvent(
            MediaHeartbeat.Event.AdBreakStart, mediaAdBreakInfo, adBreakMetadata.toStringMap());
        break;

      case "Video Ad Break Completed":
        trackAdobeEvent(MediaHeartbeat.Event.AdBreakComplete, null, null);
        break;

      case "Video Ad Started":
        Properties videoAdProperties = track.properties();
        Map<String, String> standardAdMetadata = new HashMap<>();
        Properties adProperties =
            mapStandardVideoMetadata(
                videoAdProperties,
                standardAdMetadata,
                // eventType
                "ad");
        Properties adMetadata = mapProperties(adProperties);

        MediaObject mediaAdInfo =
            MediaHeartbeat.createAdObject(
                videoAdProperties.getString("title"),
                videoAdProperties.getString("assetId"),
                videoAdProperties.getLong("indexPosition", 0),
                videoAdProperties.getDouble("totalLength", 0));

        mediaAdInfo.setValue(MediaHeartbeat.MediaObjectKey.StandardAdMetadata, standardAdMetadata);

        trackAdobeEvent(MediaHeartbeat.Event.AdStart, mediaAdInfo, adMetadata.toStringMap());
        break;

      case "Video Ad Skipped":
        trackAdobeEvent(MediaHeartbeat.Event.AdSkip, null, null);
        break;

      case "Video Ad Completed":
        trackAdobeEvent(MediaHeartbeat.Event.AdComplete, null, null);
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

  private String getConfigProperty(String key, String defaultValue, Properties properties) {
    if (properties.get(key) != null) {
      return properties.getString(key);
    }
    return defaultValue;
  }

  private void trackAdobeEvent(
      MediaHeartbeat.Event eventName, MediaObject mediaObject, Map<String, String> customMetadata) {
    heartbeat.trackEvent(eventName, mediaObject, customMetadata);
    logger.verbose("heartbeat.trackEvent(%s, %s, %s);", eventName, mediaObject, customMetadata);
  }

  private Properties mapStandardVideoMetadata(
      Properties properties, Map<String, String> standardVideoMetadata, String eventType) {
    Properties propertiesCopy = new Properties();
    propertiesCopy.putAll(properties);
    Map<String, String> propertyMap =
        (eventType.equals("coreVideo")) ? VIDEO_METADATA_MAP : AD_METADATA_MAP;
    for (Map.Entry<String, Object> entry : properties.entrySet()) {
      String propertyKey = entry.getKey();
      String value = String.valueOf(entry.getValue());

      if (propertyMap.containsKey(propertyKey)) {
        standardVideoMetadata.put(propertyMap.get(propertyKey), value);
        propertiesCopy.remove(propertyKey);
      }
    }
    if (eventType.equals("coreVideo") && properties.containsKey("livestream")) {
      standardVideoMetadata.put(
          MediaHeartbeat.VideoMetadataKeys.STREAM_FORMAT,
          properties.getBoolean("livestream", false)
              ? MediaHeartbeat.StreamType.LIVE
              : MediaHeartbeat.StreamType.VOD);
      propertiesCopy.remove("livestream");
    }
    return propertiesCopy;
  }

  /** Creates a mutable HashSet instance containing the given elements in unspecified order */
  static <T> Set<T> newSet(T... values) {
    Set<T> set = new HashSet<>(values.length);
    Collections.addAll(set, values);
    return set;
  }
}
