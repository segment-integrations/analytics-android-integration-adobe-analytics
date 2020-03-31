package com.segment.analytics.android.integrations.adobeanalytics;

import com.segment.analytics.Properties;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.BasePayload;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.TrackPayload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Generate events for all ecommerce actions.
 *
 * @since 1.2.0
 */
public class EcommerceAnalytics {

  enum Event {
    OrderCompleted("Order Completed", "purchase"),
    ProductAdded("Product Added", "scAdd"),
    ProductRemoved("Product Removed", "scRemove"),
    CheckoutStarted("Checkout Started", "scCheckout"),
    CartViewed("Cart Viewed", "scView"),
    ProductView("Product Viewed", "prodView");

    private String segmentEvent;
    private String adobeAnalyticsEvent;

    Event(String segmentEvent, String adobeAnalyticsEvent) {
      this.segmentEvent = segmentEvent;
      this.adobeAnalyticsEvent = adobeAnalyticsEvent;
    }

    /**
     * Retrieves Segment's ecommerce event name. This is different from <code>enum.name()
     * </code>.
     *
     * @return Ecommerce event name.
     */
    String getSegmentEvent() {
      return segmentEvent;
    }

    /**
     * Retrieves Adobe Analytics' ecommerce event name. This is different from <code>enum.name()
     * </code>.
     *
     * @return Ecommerce event name.
     */
    String getAdobeAnalyticsEvent() {
      return adobeAnalyticsEvent;
    }

    private static Map<String, Event> names;

    static {
      names = new HashMap<>();
      for (Event e : Event.values()) {
        names.put(e.segmentEvent, e);
      }
    }

    /**
     * Retrieves the event using Segment's ecommerce event name.
     *
     * @param name Segment's ecommerce event name.
     * @return The event.
     */
    static Event get(String name) {
      if (names.containsKey(name)) {
        return names.get(name);
      }
      throw new IllegalArgumentException(name + " is not a valid ecommerce event");
    }

    /**
     * Identifies if the event is part of Segment's ecommerce spec.
     *
     * @param eventName Event name
     * @return <code>true</code> if it's a ecommerce event, <code>false</code> otherwise.
     */
    static boolean isEcommerceEvent(String eventName) {
      return names.containsKey(eventName);
    }
  }

  private AdobeAnalyticsClient adobeAnalytics;
  private Logger logger;
  private ContextDataConfiguration contextDataConfiguration;
  private String productIdentifier;

  EcommerceAnalytics(
      AdobeAnalyticsClient adobeAnalytics,
      String productIdentifier,
      ContextDataConfiguration contextDataConfiguration,
      Logger logger) {
    this.adobeAnalytics = adobeAnalytics;
    this.logger = logger;
    this.contextDataConfiguration = contextDataConfiguration;
    this.productIdentifier = productIdentifier;
  }

  void track(TrackPayload payload) {
    EcommerceAnalytics.Event event = EcommerceAnalytics.Event.get(payload.event());
    String eventName = event.getAdobeAnalyticsEvent();

    Map<String, Object> cdata = getContextData(eventName, payload);

    adobeAnalytics.trackAction(eventName, cdata);
    logger.verbose("Analytics.trackAction(%s, %s);", eventName, cdata);
  }

  private Map<String, Object> getContextData(String eventName, BasePayload payload) {

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("&&events", eventName);

    Properties extraProperties = new Properties();
    ValueMap properties;
    if (payload.containsKey("properties")) {
      properties = payload.getValueMap("properties");
      extraProperties.putAll(properties);
    } else {
      properties = new Properties();
    }

    Products products;
    if (properties.containsKey("products")
        && properties.getList("products", Properties.Product.class).size() > 0) {
      products = new Products(properties.getList("products", Properties.Product.class));
      extraProperties.remove("products");
    } else {
      List<String> propertiesToRemove = new LinkedList<>();
      propertiesToRemove.add("category");
      propertiesToRemove.add("quantity");
      propertiesToRemove.add("price");
      products = new Products(properties);

      String idKey = productIdentifier;
      if (idKey == null || idKey.equals("id")) {
        propertiesToRemove.add("productId");
        propertiesToRemove.add("product_id");
      } else {
        propertiesToRemove.add(idKey);
      }

      for (String key : propertiesToRemove) {
        extraProperties.remove(key);
      }
    }

    if (!products.isEmpty()) {
      contextData.put("&&products", products.toString());
    }

    if (properties.containsKey("orderId")) {
      contextData.put("purchaseid", properties.getString("orderId"));
      extraProperties.remove("orderId");
    }

    if (properties.containsKey("order_id")) {
      contextData.put("purchaseid", properties.getString("order_id"));
      extraProperties.remove("order_id");
    }

    // add all customer-mapped properties to ecommerce context data map
    for (String field : contextDataConfiguration.getEventFieldNames()) {

      Object value = null;
      try {
        value = contextDataConfiguration.searchValue(field, payload);
      } catch (IllegalArgumentException e) {
        // Ignore.
      }

      if (value != null) {
        String variable = contextDataConfiguration.getVariableName(field);
        contextData.put(variable, String.valueOf(value));
        extraProperties.remove(field);
      }
    }

    // Add extra properties.
    for (String extraProperty : extraProperties.keySet()) {
      String variable = contextDataConfiguration.getPrefix() + extraProperty;
      contextData.put(variable, extraProperties.get(extraProperty));
    }

    // If we only have events, we return null;
    if (contextData.size() == 1) {
      return null;
    }

    return contextData;
  }

  String getProductIdentifier() {
    return productIdentifier;
  }

  /**
   * Allows to redefine the product identifier. Only used for testing.
   *
   * @param productIdentifier Field that represents the product id.
   */
  void setProductIdentifier(String productIdentifier) {
    this.productIdentifier = productIdentifier;
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

  /** Defines a Adobe Analytics ecommerce product. */
  class Product {

    private String category;
    private String id;
    private Integer quantity;
    private Double price;

    /**
     * Creates a product.
     *
     * @param eventProduct Product as defined in the event.
     */
    Product(ValueMap eventProduct) {

      this.setProductId(eventProduct);
      this.category = eventProduct.getString("category");

      // Default to 1.
      this.quantity = 1;
      String q = eventProduct.getString("quantity");
      if (q != null) {
        try {
          this.quantity = Integer.parseInt(q);
        } catch (NumberFormatException e) {
          // Default.
        }
      }

      // Default to 0.
      this.price = 0.0;
      String p = eventProduct.getString("price");
      if (p != null) {
        try {
          this.price = Double.parseDouble(p);
        } catch (NumberFormatException e) {
          // Default.
        }
      }

      this.price = price * quantity;
    }

    /**
     * Sets the product ID using productIdentifier setting if present (supported values are <code>
     * name</code>, <code>sku</code> and <code>id</code>. If the field is not present, it fallbacks
     * to "productId" and "id".
     *
     * <p>Currently we do not allow to have products without IDs. Adobe Analytics allows to send an
     * extra product for merchandising evars and event serialization, as seen in the last example of
     * the <a
     * href="https://marketing.adobe.com/resources/help/en_US/sc/implement/products.html">docs</a>,
     * but it is not well documented and does not conform Segment's spec.
     *
     * <p><b>NOTE: V2 Ecommerce spec defines "product_id" instead of "id". We fallback to "id" to
     * keep backwards compatibility.</b>
     *
     * @param eventProduct Event's product.
     * @throws IllegalArgumentException if the product does not have an ID.
     */
    private void setProductId(ValueMap eventProduct) {
      if (productIdentifier != null) {
        // When productIdentifier is "id" use the default behavior.
        if (!productIdentifier.equals("id")) {
          id = eventProduct.getString(productIdentifier);
        }
      }

      // Fallback to "productId" as V2 ecommerce spec
      if (id == null || id.trim().length() == 0) {
        id = eventProduct.getString("productId");
      }

      // Fallback to "product_id" as V2 ecommerce spec
      if (id == null || id.trim().length() == 0) {
        id = eventProduct.getString("product_id");
      }

      // Fallback to "id" as V1 ecommerce spec
      if (id == null || id.trim().length() == 0) {
        id = eventProduct.getString("id");
      }

      if (id == null || id.trim().length() == 0) {
        throw new IllegalArgumentException("Product id is not defined.");
      }
    }

    /**
     * Builds a string out of product properties category, name, quantity and price to send to
     * Adobe.
     *
     * @return A single string of product properties, in the format `category;name;quantity;price;
     *     examples: `athletic;shoes;1;10.0`, `;shoes;1;0.0`, `;123;;`
     */
    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();

      // Category
      if (category != null && category.trim().length() > 0) {
        builder.append(category);
      }
      builder.append(";");

      // Id
      if (id != null && id.trim().length() > 0) {
        builder.append(id);
      }
      builder.append(";");

      // Quantity
      if (quantity != null) {
        builder.append(quantity.intValue());
      }
      builder.append(";");

      // Price
      if (price != null) {
        builder.append(price.doubleValue());
      }

      return builder.toString();
    }
  }

  /** Defines an array of products. */
  class Products {

    private List<Product> products;

    Products(List<Properties.Product> eventProducts) {
      products = new ArrayList<>(eventProducts.size());

      for (Properties.Product eventProduct : eventProducts) {
        try {
          products.add(new Product(eventProduct));
        } catch (IllegalArgumentException e) {
          // We ignore the product
          logger.verbose(
              "You must provide a name for each product to pass an ecommerce event"
                  + "to Adobe Analytics.");
        }
      }
    }

    Products(ValueMap eventProperties) {
      products = new ArrayList<>(1);

      try {
        products.add(new Product(eventProperties));
      } catch (IllegalArgumentException e) {
        // We ignore the product
        logger.verbose(
            "You must provide a name for each product to pass an ecommerce event"
                + "to Adobe Analytics.");
      }
    }

    boolean isEmpty() {
      return products.isEmpty();
    }

    /**
     * Builds a string out of product properties category, name, quantity and price to send to
     * Adobe.
     *
     * @return A single string of product properties, in the format `category;name;quantity;price;
     *     examples: `athletic;shoes;1;10.0`, `;shoes;1;0.0`
     */
    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();

      for (int i = 0; i < products.size(); i++) {
        builder.append(products.get(i).toString());
        if (i < (products.size() - 1)) {
          builder.append(',');
        }
      }

      return builder.toString();
    }
  }
}
