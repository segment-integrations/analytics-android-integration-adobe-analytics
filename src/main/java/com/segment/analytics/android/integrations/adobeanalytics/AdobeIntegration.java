package com.segment.analytics.android.integrations.adobeanalytics;

import android.app.Activity;
import android.os.Bundle;
import com.adobe.mobile.Analytics;
import com.adobe.mobile.Config;
import com.segment.analytics.Properties;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.GroupPayload;
import com.segment.analytics.integrations.IdentifyPayload;
import com.segment.analytics.integrations.Integration;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.ScreenPayload;
import com.segment.analytics.integrations.TrackPayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
          return new AdobeIntegration(settings, logger);
        }

        @Override
        public String key() {
          return ADOBE_KEY;
        }
      };

  private static final String ADOBE_KEY = "Adobe Analytics";
  Map<String, Object> eventsV2;
  Map<String, Object> contextValues;
  Map<String, Object> lVars;
  private final Logger logger;

  AdobeIntegration(ValueMap settings, Logger logger) {
    this.eventsV2 = settings.getValueMap("events");
    this.contextValues = settings.getValueMap("contextValues");
    this.lVars = settings.getValueMap("lVars");
    this.logger = logger;
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

    if (eventsV2.containsKey(eventName)) {
      eventName = String.valueOf(eventsV2.get(eventName));
    }

    Properties properties = track.properties();

    if (isNullOrEmpty(properties)) {
      Analytics.trackAction(eventName, null);
      logger.verbose("Analytics.trackAction(%s, %s);", eventName, null);
      return;
    }

    Map<String, Object> mappedProperties = mapProperties(properties);

    Analytics.trackAction(eventName, mappedProperties);
    logger.verbose("Analytics.trackAction(%s, %s);", eventName, mappedProperties);
  }

  private Map<String, Object> mapProperties(Properties properties) {
    Properties propertiesCopy = new Properties();
    propertiesCopy.putAll(properties);
    Map<String, Object> mappedProperties = new HashMap<>();

    if (!isNullOrEmpty(contextValues)) {
      for (Map.Entry<String, Object> entry : properties.entrySet()) {
        String property = entry.getKey();
        Object value = entry.getValue();

        if (contextValues.containsKey(property)) {
          mappedProperties.put(String.valueOf(contextValues.get(property)), value);
          propertiesCopy.remove(property);
        }
      }
    }

    if (!isNullOrEmpty(lVars)) {
      for (Map.Entry<String, Object> entry : properties.entrySet()) {
        String property = entry.getKey();
        Object value = entry.getValue();

        if (lVars.containsKey(property)) {
          if (value instanceof String
              || value instanceof Integer
              || value instanceof Double
              || value instanceof Long) {
            mappedProperties.put(String.valueOf(lVars.get(property)), value);
            propertiesCopy.remove(property);
          }
          if (value instanceof List) {
            StringBuilder builder = new StringBuilder();
            List<Object> list = (List) value;

            for (int i = 0; i < list.size(); i++) {
              String item = String.valueOf(list.get(i));
              if (i < list.size() - 1) {
                builder.append(item).append(",");
              } else {
                builder.append(item);
              }
            }

            String joinedList = builder.toString();

            mappedProperties.put(String.valueOf(lVars.get(property)), joinedList);
            propertiesCopy.remove(property);
          }
        }
      }
    }
    // pass along remaining unmapped Segment properties as contextData just in case
    mappedProperties.putAll(propertiesCopy);
    return mappedProperties;
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
  }
}
