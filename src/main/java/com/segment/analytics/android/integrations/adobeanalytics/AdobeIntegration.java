package com.segment.analytics.android.integrations.adobeanalytics;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import android.text.TextUtils;
import com.adobe.mobile.Config;
import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;
import com.segment.analytics.Traits;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.BasePayload;
import com.segment.analytics.integrations.GroupPayload;
import com.segment.analytics.integrations.IdentifyPayload;
import com.segment.analytics.integrations.Integration;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.ScreenPayload;
import com.segment.analytics.integrations.TrackPayload;

import java.io.FileWriter;
import java.security.Provider;
import java.util.ArrayList;
import java.util.HashMap;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Date;

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
public class AdobeIntegration extends Integration<com.adobe.mobile.Analytics> {

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
  private final Logger logger;
  private final Map<String, Object> events;
  private final Map<String, Object> eVars;
  private final Map<String, Object> lVars;
  private final Map<String, Object> props;

  AdobeIntegration(ValueMap settings, Logger logger) {
    this.logger = logger;
    this.events = settings.getValueMap("events");
    this.eVars = settings.getValueMap("eVars");
    this.lVars = settings.getValueMap("lVars");
    this.props = settings.getValueMap("props");
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

    com.adobe.mobile.Analytics.trackState(screen.name(), screen.properties());
  }

  @Override
  public void track(TrackPayload track) {
    super.track(track);

    String eventName = track.event();

    if (isNullOrEmpty(events) || !events.containsKey(eventName)) {
      logger.verbose("Please map your events to corresponding "
          + "Adobe events in your Segment UI.");
      return;
    }

    eventName = String.valueOf(events.get(eventName));
    Properties properties = track.properties();

    if (isNullOrEmpty(properties)) {
      com.adobe.mobile.Analytics.trackAction(eventName, properties);
      logger.verbose("Analytics.trackAction(%s, %s);", eventName, properties);
      return;
    }

    Properties propertiesCopy = new Properties();
    propertiesCopy.putAll(properties);
    Properties mappedProperties = new Properties();

    if (!isNullOrEmpty(eVars)) {
      for (Map.Entry<String, Object> entry : properties.entrySet()) {
        String property = entry.getKey();
        Object value = entry.getValue();

        if (eVars.containsKey(property)) {
          mappedProperties.put(String.valueOf(eVars.get(property)), value);
          propertiesCopy.remove(property);
        }
      }
    }

    if (!isNullOrEmpty(props)) {
      for (Map.Entry<String, Object> entry : properties.entrySet()) {
        String property = entry.getKey();
        Object value = entry.getValue();

        if (props.containsKey(property)) {
          mappedProperties.put(String.valueOf(props.get(property)), value);
          propertiesCopy.remove(property);
        }
      }
    }

    if (!isNullOrEmpty(lVars)) {
      for (Map.Entry<String, Object> entry : properties.entrySet()) {
        String property = entry.getKey();
        Object value = entry.getValue();

        if (lVars.containsKey(property)) {
          if (value instanceof String) {
            mappedProperties.put(String.valueOf(lVars.get(property)), value);
            propertiesCopy.remove(property);
          }
          if (value instanceof List) {
            List<Object> listValue = (List) value;
            String list = TextUtils.join(",", listValue);
            mappedProperties.put(String.valueOf(lVars.get(property)), list);
            propertiesCopy.remove(property);
          }
        }
      }
    }
    // pass along remaining unmapped Segment properties as contextData values
    mappedProperties.putAll(propertiesCopy);
    com.adobe.mobile.Analytics.trackAction(eventName, mappedProperties);
    logger.verbose("Analytics.trackAction(%s, %s);", eventName, mappedProperties);
  }

  @Override
  public void group(GroupPayload group) {
    super.group(group);
  }

  @Override
  public void flush() {
    super.flush();
  }

  @Override
  public void reset() {
    super.reset();
  }
}
