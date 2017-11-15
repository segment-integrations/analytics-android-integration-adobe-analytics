package com.segment.analytics.android.integrations.adobeanalytics;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import android.text.TextUtils;
import com.adobe.mobile.Config;
import com.segment.analytics.Analytics;
import com.segment.analytics.Properties.Product;
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
  private final Map<String, Object> contextDataMapper;
  private final Map<String, Object> lVars;
  private static final Map<String, String> ECOMMERCE_MAPPER = createEcommerceMap();
  private static Map<String, String> createEcommerceMap() {
    Map<String, String> map = new HashMap<>();
    map.put("Product Added", "scAdd");
    map.put("Product Removed", "scRemove");
    map.put("Cart Viewed", "scView");
    map.put("Checkout Started", "scCheckout");
    map.put("Order Completed", "purchase");
    return map;
  }

  AdobeIntegration(ValueMap settings, Logger logger) {
    this.logger = logger;
    this.events = settings.getValueMap("events");
    this.contextDataMapper = settings.getValueMap("cDataMapper");
    this.lVars = settings.getValueMap("lVars");
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
      com.adobe.mobile.Analytics.trackState(screen.name(), null);
      logger.verbose("Analytics.trackState(%s, %s);", screen.name(), null);
      return;
    }

    Properties mappedProperties = mapProperties(properties);
    com.adobe.mobile.Analytics.trackState(screen.name(), mappedProperties);
    logger.verbose("Analytics.trackState(%s, %s);", screen.name(), mappedProperties);
  }

  @Override
  public void track(TrackPayload track) {
    super.track(track);

    String eventName = track.event();

    if (ECOMMERCE_MAPPER.containsKey(eventName)) {
      trackEcommerce(track);
    }

    if (isNullOrEmpty(events) || !events.containsKey(eventName)) {
      logger.verbose("Please map your event names to corresponding "
          + "Adobe event names in your Segment UI.");
      return;
    }

    Properties properties = track.properties();
    eventName = String.valueOf(events.get(eventName));

    if (isNullOrEmpty(properties)) {
      com.adobe.mobile.Analytics.trackAction(eventName, null);
      logger.verbose("Analytics.trackAction(%s, %s);", eventName, null);
      return;
    }

    Properties mappedProperties = mapProperties(properties);

    com.adobe.mobile.Analytics.trackAction(eventName, mappedProperties);
    logger.verbose("Analytics.trackAction(%s, %s);", eventName, mappedProperties);
  }

  private void trackEcommerce(TrackPayload track) {
    String eventName = ECOMMERCE_MAPPER.get(track.event());
    Properties properties = track.properties();
    Properties propertiesCopy = null;
    Map<String, Object> ecommerceProperties = null;

    if (!isNullOrEmpty(properties)) {
      propertiesCopy = new Properties();
      propertiesCopy.putAll(properties);
      propertiesCopy.remove("products");
      ecommerceProperties = mapEcommerce(eventName, properties);
    }
    // pass in the copy of properties without a nested products array
    Properties mappedProperties = mapProperties(propertiesCopy);
    mappedProperties.putAll(ecommerceProperties);

    com.adobe.mobile.Analytics.trackAction(eventName, mappedProperties);
    logger.verbose("Analytics.trackAction(%s, %s);", eventName, mappedProperties);
  }

  private Properties mapProperties(Properties properties) {
    Properties propertiesCopy = new Properties();
    propertiesCopy.putAll(properties);
    Properties mappedProperties = new Properties();

    if (!isNullOrEmpty(contextDataMapper)) {
      for (Map.Entry<String, Object> entry : properties.entrySet()) {
        String property = entry.getKey();
        Object value = entry.getValue();

        if (contextDataMapper.containsKey(property)) {
          mappedProperties.put(String.valueOf(contextDataMapper.get(property)), value);
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
    return mappedProperties;
  }

  private Map<String, Object> mapEcommerce (String eventName, Properties properties) {
    StringBuilder productStringBuilder = new StringBuilder();
    Map<String, Object> contextData = new HashMap<>();
    String concatenatedValues;

    List<Product> products = properties.products();
    if (!isNullOrEmpty(products)) {
      for (int i = 0; i < products.size(); i++) {
        Product product = products.get(i);
        // adobe requires we pass a semicolon as a placeholder even if a value is empty
        String category = ";";
        String name = ";";
        String quantity = "1;";
        String price = ";";
        if (product.containsKey("category")) {
          category = product.getString("category") + ";";
        }
        if (product.containsKey("name")) {
          name = product.getString("name") + ";";
        }
        if (product.containsKey("quantity")) {
          quantity = product.getString("quantity") + ";";
        }
        if (product.containsKey("price")) {
          price = product.getString("price") + ";";
        }
        if (i < products.size() - 1) {
          concatenatedValues = category + name + quantity + price + ",";
        } else {
          concatenatedValues = category + name + quantity + price;
        }
        productStringBuilder.append(concatenatedValues);
      }
      String productString = productStringBuilder.toString();
      if (eventName.equals("purchase")) {
        contextData.put("purchaseid", properties.getString("orderId"));
      }
      contextData.put("&&products", productString);
    }
    return contextData;
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
