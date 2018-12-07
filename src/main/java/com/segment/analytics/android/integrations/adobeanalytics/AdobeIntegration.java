package com.segment.analytics.android.integrations.adobeanalytics;

import android.app.Activity;
import android.os.Bundle;
import com.segment.analytics.Properties;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.GroupPayload;
import com.segment.analytics.integrations.IdentifyPayload;
import com.segment.analytics.integrations.Integration;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.ScreenPayload;
import com.segment.analytics.integrations.TrackPayload;

import java.util.HashMap;
import java.util.Map;

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
          return new AdobeIntegration(settings, analytics, logger);
        }

        @Override
        public String key() {
          return ADOBE_KEY;
        }
      };

  private static final String ADOBE_KEY = "Adobe Analytics";

  private Logger logger;
  private AdobeAnalyticsClient adobeAnalytics;
  private VideoAnalytics video;
  private EcommerceAnalytics ecommerce;
  private Map<String, String> eventsMapping;
  private Map<String, String> contextDataVariables;

  AdobeIntegration(ValueMap settings, com.segment.analytics.Analytics analytics, Logger logger) {
    initialize(settings, logger);

    String serverUrl = settings.getString("heartbeatTrackingServerUrl");
    String productIdentifier = settings.getString("productIdentifier");
    Map<String, String> contextDataVariables = getSetting("contextValues", settings);
    boolean ssl = settings.getBoolean("ssl", false);

    video =
        new VideoAnalytics(
            analytics.getApplication(), serverUrl, contextDataVariables, ssl, logger);
    adobeAnalytics = new AdobeAnalyticsClient.DefaultClient();
    ecommerce =
        new EcommerceAnalytics(adobeAnalytics, productIdentifier, contextDataVariables, logger);

    if (logger.logLevel.equals(com.segment.analytics.Analytics.LogLevel.VERBOSE)) {
      logger.verbose("Enabled debugging");
      video.setDebugLogging(true);
      adobeAnalytics.setDebugLogging(true);
    }
  }

  AdobeIntegration(
      ValueMap settings,
      VideoAnalytics video,
      EcommerceAnalytics ecommerce,
      AdobeAnalyticsClient adobeAnalytics,
      Logger logger) {
    initialize(settings, logger);

    this.video = video;
    this.ecommerce = ecommerce;
    this.adobeAnalytics = adobeAnalytics;
  }

  private void initialize(ValueMap settings, Logger logger) {
    this.eventsMapping = getSetting("eventsV2", settings);
    this.contextDataVariables = getSetting("contextValues", settings);

    this.logger = logger;
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

    Map<String, Object> cdata = getContextData(properties);
    adobeAnalytics.trackState(screen.name(), cdata);
    logger.verbose("Analytics.trackState(%s, %s);", screen.name(), cdata);
  }

  @Override
  public void track(TrackPayload payload) {
    super.track(payload);

    String eventName = payload.event();

    if (VideoAnalytics.Event.isVideoEvent(eventName)) {
      video.track(payload);
      return;
    }

    if (EcommerceAnalytics.Event.isEcommerceEvent(eventName)) {
      if (eventsMapping != null && eventsMapping.containsKey(eventName)) {
        logger.verbose(
            "Segment currently does not support mapping specced ecommerce events to "
                + "custom Adobe events.");
        return;
      }

      ecommerce.track(payload);
      return;
    }

    if (eventsMapping == null
        || eventsMapping.size() == 0
        || !eventsMapping.containsKey(eventName)) {
      logger.verbose(
          "Event must be either configured in Adobe and in the Segment EventsV2 setting, "
              + "a reserved Adobe Ecommerce or Video event.");
      return;
    }

    String event = String.valueOf(eventsMapping.get(eventName));
    Map<String, Object> cdata = getContextData(payload.properties());

    adobeAnalytics.trackAction(event, cdata);
    logger.verbose("Analytics.trackAction(%s, %s);", event, cdata);
  }

  private Map<String, Object> getContextData(Properties properties) {
    if (properties == null
        || properties.size() == 0
        || contextDataVariables == null
        || contextDataVariables.size() == 0) {
      return null;
    }

    Map<String, Object> contextData = new HashMap<>();

    for (String key : contextDataVariables.keySet()) {

      if (properties.containsKey(key)) {
        String variable = contextDataVariables.get(key);
        Object value = properties.get(key);
        contextData.put(variable, value);
      }
    }

    return contextData;
  }

  /**
   * Retrieves the setting as a map of strings, or an empty map if it is not defined.
   *
   * @param name Setting name.
   * @return Strings map.
   */
  private static Map<String, String> getSetting(String name, ValueMap settings) {
    ValueMap setting = settings.getValueMap(name);
    if (setting == null) {
      setting = new ValueMap();
    }

    return setting.toStringMap();
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

  Map<String, String> getEventsMapping() {
    return eventsMapping;
  }

  /**
   * Allows to redefine the events mapping. Only used for testing.
   *
   * @param eventsMapping Events mapping as <code>{segment event, adobe analytics event}</code>.
   */
  void setEventsMapping(Map<String, String> eventsMapping) {
    this.eventsMapping = eventsMapping;
  }

  Map<String, String> getContextDataVariables() {
    return contextDataVariables;
  }

  /**
   * Allows to redefine the context data variables. Only used for testing.
   *
   * @param contextDataVariables Context data variables as <code>
   *     {segment field, adobe analytics field}</code>.
   */
  void setContextDataVariables(Map<String, String> contextDataVariables) {
    this.contextDataVariables = contextDataVariables;
  }
}
