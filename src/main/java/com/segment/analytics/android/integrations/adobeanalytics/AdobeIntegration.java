package com.segment.analytics.android.integrations.adobeanalytics;

import android.app.Activity;
import android.os.Bundle;
import com.adobe.mobile.Analytics;
import com.adobe.mobile.Config;
import com.segment.analytics.Properties;
import com.segment.analytics.Properties.Product;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.GroupPayload;
import com.segment.analytics.integrations.IdentifyPayload;
import com.segment.analytics.integrations.Integration;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.ScreenPayload;
import com.segment.analytics.integrations.TrackPayload;
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
  String productIdentifier;
  private static final Map<String, String> ECOMMERCE_EVENT_LIST = getEcommerceEventList();
  private static Map<String, String> getEcommerceEventList() {
    Map<String, String> ecommerceEventList = new HashMap<>();
    ecommerceEventList.put("Order Completed", "purchase");
    ecommerceEventList.put("Product Added", "scAdd");
    ecommerceEventList.put("Product Removed", "scRemove");
    ecommerceEventList.put("Checkout Started", "scCheckout");
    ecommerceEventList.put("Cart Viewed", "scView");
    return ecommerceEventList;
  }
  private final Logger logger;

  AdobeIntegration(ValueMap settings, Logger logger) {
    this.eventsV2 = settings.getValueMap("eventsV2");
    this.contextValues = settings.getValueMap("contextValues");
    this.lVars = settings.getValueMap("lVars");
    this.productIdentifier = settings.getString("productIdentifier");
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

    Map<String, Object> mappedProperties = null;

    String eventName = track.event();
    Properties properties = track.properties();

    if (ECOMMERCE_EVENT_LIST.containsKey(eventName)) {
      eventName = ECOMMERCE_EVENT_LIST.get(eventName);
      mappedProperties = (isNullOrEmpty(properties)) ? null : mapEcommerce(eventName, properties);
    }

    if (eventsV2.containsKey(eventName)) {
      eventName = String.valueOf(eventsV2.get(eventName));
      mappedProperties = (isNullOrEmpty(properties)) ? null : mapEcommerce(eventName, properties);
    }

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
            mappedProperties.put(
                String.valueOf(lVars.get(property)), String.valueOf(String.valueOf(value)));
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

  private Map<String, Object> mapEcommerce(String eventName, Properties properties) {
    StringBuilder productStringBuilder;
    Map<String, Object> contextData = null;
    Map<String, Object> productProperties;
    String productsString = null;

    if (!isNullOrEmpty(properties)) {
      productStringBuilder = new StringBuilder();
      contextData = new HashMap<>();
      productProperties = new HashMap<>();

      List<Product> products = properties.products();

      if (!isNullOrEmpty(products)) {
        for (int i = 0; i < products.size(); i++) {
          Product product = products.get(i);
          productProperties.putAll(product);

          String productString = ecommerceStringBuilder(eventName, productProperties);
          if (i < product.size() - 1) {
            productStringBuilder.append(productString).append(",");
          } else {
            productStringBuilder.append(productString);
          }
        }
        productsString = productStringBuilder.toString();
      } else {
        productProperties.putAll(properties);
        productsString = ecommerceStringBuilder(eventName, productProperties);
      }
      //finally, add a purchaseid to context data if it's been mapped by customer
      if (properties.containsKey("orderId") && contextValues.containsKey("orderId")) {
        contextData.put(String.valueOf(contextValues.get("orderId")), properties.getString("orderId"));
      }
      contextData.put("&&products", productsString);
      // add all customer-mapped properties to ecommerce context data map
      contextData.putAll(mapProperties(properties));
    }
    return contextData;
  }

  private String ecommerceStringBuilder(String eventName, Map <String, Object> productProperties) {
    String category = "";
    String name = "";
    int quantity = 1;
    double price = 0;

    if (productProperties.containsKey("category")) {
      category = String.valueOf(productProperties.get("category"));
    }

    // product "name" is determined by a user setting
    if (productProperties.containsKey("name") && productIdentifier.equals("name")) {
      name = String.valueOf(productProperties.get("name"));
    }
    if (productProperties.containsKey("sku") && productIdentifier.equals("sku")) {
      name = String.valueOf(productProperties.get("sku"));
    }
    if (productProperties.containsKey("id") && productIdentifier.equals("name")) {
      name = String.valueOf(productProperties.get("id"));
    }

    if (productProperties.containsKey("quantity") && (productProperties.get("quantity") instanceof Integer)) {
      quantity = (int) productProperties.get("quantity");
    }

    // only pass along total product price for order completed events
    if (eventName.equals("Order Completed")) {
      if (productProperties.containsKey("price") && (productProperties.get("price") instanceof Number)) {
        price = ((double) productProperties.get("price")) * (double) quantity;
      }
    }
    return category + ";" + name + ";" + quantity + ";" + price;
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
}
