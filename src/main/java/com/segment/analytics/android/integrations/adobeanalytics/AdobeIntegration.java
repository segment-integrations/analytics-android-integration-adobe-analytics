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
 * customers segments via activity and video tracking.
 *
 * @see <a href="http://www.adobe.com/data-analytics-cloud/analytics.html">Adobe Analytics</a>
 * @see <a href="https://segment.com/docs/integrations/amplitude/">Adobe Integration</a>
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
          return new AdobeIntegration(settings, analytics, logger, Provider.REAL);
        }

        @Override
        public String key() {
          return ADOBE_KEY;
        }
      };

  private static final String ADOBE_KEY = "Adobe Analytics";
  Map<String, Object> eventsV2;
  Map<String, Object> contextValues;
  String productIdentifier;
  boolean videoHeartbeatEnabled;
  private final Logger logger;

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

  MediaHeartbeatConfig config;
  MediaHeartbeat heartbeat;

  private static Set<String> videoEventList =
      new HashSet<>(
          Arrays.asList(
              "Video Content Started",
              "Video Playback Paused",
              "Video Playback Resumed",
              "Video Content Completed",
              "Video Playback Completed"));

  AdobeIntegration(
      ValueMap settings,
      com.segment.analytics.Analytics analytics,
      Logger logger,
      Provider provider) {
    this.eventsV2 = settings.getValueMap("eventsV2");
    this.contextValues = settings.getValueMap("contextValues");
    this.productIdentifier = settings.getString("productIdentifier");
    this.videoHeartbeatEnabled = settings.getBoolean("videoHeartbeatEnabled", true);
    this.logger = logger;

    boolean adobeLogLevel = logger.logLevel.equals(com.segment.analytics.Analytics.LogLevel.VERBOSE);
    Config.setDebugLogging(adobeLogLevel);

    if (videoHeartbeatEnabled) {
      Context context = analytics.getApplication();

      config = new MediaHeartbeatConfig();

      config.trackingServer = settings.getString("heartbeatTrackingServer");
      config.channel = settings.getString("heartbeatChannel");
      // default app version to 0.0 if not otherwise present b/c Adobe requires this value
      config.appVersion =
          (!isNullOrEmpty(context.getPackageName())) ? context.getPackageName() : "0.0";
      config.ovp = settings.getString("heartbeatOnlineVideoPlatform");
      config.playerName = settings.getString("heartbeatPlayerName");
      config.ssl = settings.getBoolean("heartbeatEnableSsl", false);
      config.debugLogging = adobeLogLevel;

      heartbeat = (provider != null) ? provider.get() : new MediaHeartbeat(new NoOpDelegate(), config);
    }
  }

  static class NoOpDelegate implements MediaHeartbeatDelegate {

    private NoOpDelegate() {}

    @Override
    public MediaObject getQoSObject() {
      return null;
    }

    @Override
    public Double getCurrentPlaybackTime() {
      return null;
    }
  }

  interface Provider {

    MediaHeartbeat get();

    Provider REAL =
        new Provider() {
          @Override
          public MediaHeartbeat get() {
            return null;
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

    if (videoHeartbeatEnabled && videoEventList.contains(eventName)) {
      trackVideo(eventName, properties);
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

  private Properties videoProperties;

  private void trackVideo(String eventName, Properties properties) {
    switch (eventName) {
      case "Video Content Started":
        videoProperties = new Properties();
        videoProperties.putAll(properties);

        Map<String, String> standardVideoMetadata = mapStandardVideoMetadata(properties);
        HashMap<String, String> videoMetadata = new HashMap<>();
        videoMetadata.putAll(videoProperties.toStringMap());

        // create a media object; values can be null
        MediaObject mediaInfo =
            MediaHeartbeat.createMediaObject(
                properties.getString("title"),
                properties.getString("sessionId"),
                properties.getDouble("totalLength", 0),
                properties.getBoolean("livestream", false)
                    ? MediaHeartbeat.StreamType.LIVE
                    : MediaHeartbeat.StreamType.VOD);

        mediaInfo.setValue(
            MediaHeartbeat.MediaObjectKey.StandardVideoMetadata, standardVideoMetadata);

        heartbeat.trackSessionStart(mediaInfo, videoMetadata);
        heartbeat.trackPlay();
        videoProperties = null;
        break;

      case "Video Playback Paused":
        heartbeat.trackPause();
        break;

      case "Video Playback Resumed":
        heartbeat.trackPlay();
        break;

      case "Video Content Completed":
        heartbeat.trackComplete();
        break;

      case "Video Playback Completed":
        heartbeat.trackSessionEnd();
    }
  }

  private Map<String, String> mapStandardVideoMetadata(Properties properties) {
    Map<String, String> standardVideoMetadata = new HashMap<>();
    if (properties.getString("assetId") != null) {
      standardVideoMetadata.put(
          MediaHeartbeat.VideoMetadataKeys.ASSET_ID, properties.getString("assetId"));
      videoProperties.remove("assetId");
    }
    if (properties.getString("program") != null) {
      standardVideoMetadata.put(
          MediaHeartbeat.VideoMetadataKeys.SHOW, properties.getString("program"));
      videoProperties.remove("program");
    }
    if (properties.getString("season") != null) {
      standardVideoMetadata.put(
          MediaHeartbeat.VideoMetadataKeys.SEASON, properties.getString("season"));
      videoProperties.remove("season");
    }
    if (properties.getString("episode") != null) {
      standardVideoMetadata.put(
          MediaHeartbeat.VideoMetadataKeys.EPISODE, properties.getString("episode"));
      videoProperties.remove("episode");
    }
    if (properties.getString("genre") != null) {
      standardVideoMetadata.put(
          MediaHeartbeat.VideoMetadataKeys.GENRE, properties.getString("genre"));
      videoProperties.remove("genre");
    }
    if (properties.getString("channel") != null) {
      standardVideoMetadata.put(
          MediaHeartbeat.VideoMetadataKeys.NETWORK, properties.getString("channel"));
      videoProperties.remove("channel");
    }
    if (properties.getString("airdate") != null) {
      standardVideoMetadata.put(
          MediaHeartbeat.VideoMetadataKeys.FIRST_AIR_DATE, properties.getString("airdate"));
      videoProperties.remove("airdate");
    }
    standardVideoMetadata.put(
        MediaHeartbeat.VideoMetadataKeys.STREAM_FORMAT,
        videoProperties.getBoolean("livestream", false)
            ? MediaHeartbeat.StreamType.LIVE
            : MediaHeartbeat.StreamType.VOD);
    return standardVideoMetadata;
  }
}
