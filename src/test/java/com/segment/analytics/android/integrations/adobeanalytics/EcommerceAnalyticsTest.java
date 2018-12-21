package com.segment.analytics.android.integrations.adobeanalytics;

import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.TrackPayload;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

public class EcommerceAnalyticsTest {

    @Mock private AdobeAnalyticsClient client;
    @Mock private EcommerceAnalytics ecommerceAnalytics;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        Map<String, String> contextVariables = new HashMap<>();
        ecommerceAnalytics = new EcommerceAnalytics(client, "", contextVariables, Logger.with(Analytics.LogLevel.NONE));
    }

    @Test
    public void initialize() {
        Map<String, String> contextVariables = new HashMap<>();
        contextVariables.put("testField", "myapp.var");
        ecommerceAnalytics = new EcommerceAnalytics(client, "id", contextVariables, Logger.with(Analytics.LogLevel.NONE));

        Assert.assertEquals("id", ecommerceAnalytics.getProductIdentifier());
        Assert.assertEquals(contextVariables, ecommerceAnalytics.getContextDataVariables());
    }

    @Test
    public void track() {

        EcommerceAnalytics.Event event = EcommerceAnalytics.Event.CheckoutStarted;
        TrackPayload payload = new TrackPayload.Builder()
                .userId("test-user")
                .event(event.getSegmentEvent())
                .build();

        ecommerceAnalytics.track(payload);

        Mockito.verify(client).trackAction(event.getAdobeAnalyticsEvent(), null);
    }

    @Test
    public void trackOrderCompleted() {
        ecommerceAnalytics.setProductIdentifier("name");

        Map<String, String> contextDataVariables = new HashMap<>();
        contextDataVariables.put("testing", "myapp.testing");
        ecommerceAnalytics.setContextDataVariables(contextDataVariables);

        EcommerceAnalytics.Event event = EcommerceAnalytics.Event.OrderCompleted;
        Properties.Product product = new Properties.Product("123", "ABC", 10.0);
        product.putName("shoes");
        product.putValue("category", "athletic");
        product.putValue("quantity", 2);

        TrackPayload payload = new TrackPayload.Builder()
                .userId("test-user")
                .event(event.getSegmentEvent())
                .properties(new Properties()
                        .putOrderId("A5744855555")
                        .putValue("testing", "test!")
                        .putProducts(product))
                .build();

        ecommerceAnalytics.track(payload);

        Map<String, Object> contextData = new HashMap<>();
        contextData.put("myapp.testing", "test!");
        contextData.put("purchaseid", "A5744855555");
        contextData.put("&&products", "athletic;shoes;2;20.0");
        contextData.put("&&events", event.getAdobeAnalyticsEvent());
        Mockito.verify(client).trackAction(event.getAdobeAnalyticsEvent(), contextData);
    }

    @Test
    public void trackOrderCompletedWithExtraProperties() {
        ecommerceAnalytics.setProductIdentifier("name");

        Map<String, String> contextDataVariables = new HashMap<>();
        contextDataVariables.put("testing", "myapp.testing");
        ecommerceAnalytics.setContextDataVariables(contextDataVariables);

        EcommerceAnalytics.Event event = EcommerceAnalytics.Event.OrderCompleted;
        Properties.Product product = new Properties.Product("123", "ABC", 10.0);
        product.putName("shoes");
        product.putValue("category", "athletic");
        product.putValue("quantity", 2);

        TrackPayload payload = new TrackPayload.Builder()
                .userId("test-user")
                .event(event.getSegmentEvent())
                .properties(new Properties()
                        .putOrderId("A5744855555")
                        .putValue("testing", "test!")
                        .putValue("extra", "extra value")
                        .putProducts(product))
                .build();

        ecommerceAnalytics.track(payload);

        Map<String, Object> contextData = new HashMap<>();
        contextData.put("myapp.testing", "test!");
        contextData.put("purchaseid", "A5744855555");
        contextData.put("&&products", "athletic;shoes;2;20.0");
        contextData.put("&&events", event.getAdobeAnalyticsEvent());
        contextData.put("extra", "extra value");
        Mockito.verify(client).trackAction(event.getAdobeAnalyticsEvent(), contextData);
    }

    @Test
    public void trackProductAdded() {
        ecommerceAnalytics.setProductIdentifier("name");

        EcommerceAnalytics.Event event = EcommerceAnalytics.Event.ProductAdded;
        TrackPayload payload = new TrackPayload.Builder()
                .userId("test-user")
                .event(event.getSegmentEvent())
                .properties(new Properties()
                        .putSku("ABC")
                        .putPrice(10.0)
                        .putName("shoes")
                        .putCategory("athletic")
                        .putValue("quantity", 2))
                .build();

        ecommerceAnalytics.track(payload);

        Map<String, Object> contextData = new HashMap<>();
        contextData.put("&&products", "athletic;shoes;2;20.0");
        contextData.put("&&events", event.getAdobeAnalyticsEvent());
        contextData.put("sku", "ABC");
        Mockito.verify(client).trackAction(event.getAdobeAnalyticsEvent(), contextData);
    }

    @Test
    public void trackProductAddedWithExtraProperties() {
        ecommerceAnalytics.setProductIdentifier("name");

        EcommerceAnalytics.Event event = EcommerceAnalytics.Event.ProductAdded;
        TrackPayload payload = new TrackPayload.Builder()
                .userId("test-user")
                .event(event.getSegmentEvent())
                .properties(new Properties()
                        .putSku("ABC")
                        .putPrice(10.0)
                        .putName("shoes")
                        .putCategory("athletic")
                        .putValue("quantity", 2)
                        .putValue("extra", "extra value"))
                .build();

        ecommerceAnalytics.track(payload);

        Map<String, Object> contextData = new HashMap<>();
        contextData.put("&&products", "athletic;shoes;2;20.0");
        contextData.put("&&events", event.getAdobeAnalyticsEvent());
        contextData.put("extra", "extra value");
        contextData.put("sku", "ABC");
        Mockito.verify(client).trackAction(event.getAdobeAnalyticsEvent(), contextData);
    }


    @Test
    public void trackProductRemoved() {
        ecommerceAnalytics.setProductIdentifier("name");

        EcommerceAnalytics.Event event = EcommerceAnalytics.Event.ProductRemoved;
        TrackPayload payload = new TrackPayload.Builder()
                .userId("test-user")
                .event(event.getSegmentEvent())
                .properties(new Properties()
                        .putSku("ABC")
                        .putPrice(10.0)
                        .putName("shoes")
                        .putCategory("athletic")
                        .putValue("quantity", 2))
                .build();

        ecommerceAnalytics.track(payload);

        Map<String, Object> contextData = new HashMap<>();
        contextData.put("&&products", "athletic;shoes;2;20.0");
        contextData.put("&&events", event.getAdobeAnalyticsEvent());
        contextData.put("sku", "ABC");
        Mockito.verify(client).trackAction(event.getAdobeAnalyticsEvent(), contextData);
    }

    @Test
    public void trackProductViewed() {
        ecommerceAnalytics.setProductIdentifier("name");

        EcommerceAnalytics.Event event = EcommerceAnalytics.Event.ProductView;
        TrackPayload payload = new TrackPayload.Builder()
                .userId("test-user")
                .event(event.getSegmentEvent())
                .properties(new Properties()
                        .putSku("ABC")
                        .putPrice(10.0)
                        .putName("shoes")
                        .putCategory("athletic")
                        .putValue("quantity", 2))
                .build();

        ecommerceAnalytics.track(payload);

        Map<String, Object> contextData = new HashMap<>();
        contextData.put("&&products", "athletic;shoes;2;20.0");
        contextData.put("&&events", event.getAdobeAnalyticsEvent());
        contextData.put("sku", "ABC");
        Mockito.verify(client).trackAction(event.getAdobeAnalyticsEvent(), contextData);
    }

    @Test
    public void trackEcommerceEventWithProductId() {
        ecommerceAnalytics.setProductIdentifier("id");

        EcommerceAnalytics.Event event = EcommerceAnalytics.Event.ProductView;
        TrackPayload payload = new TrackPayload.Builder()
                .userId("test-user")
                .event(event.getSegmentEvent())
                .properties(new Properties()
                        .putValue("productId", "XYZ")
                        .putSku("ABC")
                        .putPrice(10.0)
                        .putName("shoes")
                        .putCategory("athletic")
                        .putValue("quantity", 2))
                .build();
        ecommerceAnalytics.track(payload);

        Map<String, Object> contextData = new HashMap<>();
        contextData.put("&&products", "athletic;XYZ;2;20.0");
        contextData.put("&&events", event.getAdobeAnalyticsEvent());
        contextData.put("sku", "ABC");
        contextData.put("name", "shoes");
        Mockito.verify(client).trackAction(event.getAdobeAnalyticsEvent(), contextData);
    }

    @Test
    public void trackCheckoutStarted() {
        ecommerceAnalytics.setProductIdentifier("name");

        EcommerceAnalytics.Event event = EcommerceAnalytics.Event.CheckoutStarted;
        Properties.Product product1 = new Properties.Product("123", "ABC", 10.0);
        product1.putName("shoes");
        product1.putValue("category", "athletic");
        product1.putValue("quantity", 2);

        Properties.Product product2 = new Properties.Product("456", "DEF", 20.0);
        product2.putName("jeans");
        product2.putValue("category", "casual");
        product2.putValue("quantity", 1);

        // Will use id instead of name.
        Properties.Product product3 = new Properties.Product("789", "GHI", 30.0);
        product3.putValue("category", "formal");
        product3.putValue("quantity", 1);

        TrackPayload payload = new TrackPayload.Builder()
                .userId("test-user")
                .event(event.getSegmentEvent())
                .properties(new Properties().putProducts(product1, product2, product3))
                .build();

        ecommerceAnalytics.track(payload);

        Map<String, Object> contextData = new HashMap<>();
        contextData.put("&&products", "athletic;shoes;2;20.0,casual;jeans;1;20.0,formal;789;1;30.0");
        contextData.put("&&events", event.getAdobeAnalyticsEvent());
        Mockito.verify(client).trackAction(event.getAdobeAnalyticsEvent(), contextData);
    }

    @Test
    public void trackCartViewed() {
        ecommerceAnalytics.setProductIdentifier("name");

        EcommerceAnalytics.Event event = EcommerceAnalytics.Event.CartViewed;
        Properties.Product product1 = new Properties.Product("123", "ABC", 10.0);
        product1.putName("shoes");
        product1.putValue("category", "athletic");
        product1.putValue("quantity", 2);

        Properties.Product product2 = new Properties.Product("456", "DEF", 20.0);
        product2.putName("jeans");
        product2.putValue("category", "casual");
        product2.putValue("quantity", 1);

        TrackPayload payload = new TrackPayload.Builder()
                .userId("test-user")
                .event(event.getSegmentEvent())
                .properties(new Properties().putProducts(product1, product2))
                .build();

        ecommerceAnalytics.track(payload);

        Map<String, Object> contextData = new HashMap<>();
        contextData.put("&&products", "athletic;shoes;2;20.0,casual;jeans;1;20.0");
        contextData.put("&&events", event.getAdobeAnalyticsEvent());
        Mockito.verify(client).trackAction(event.getAdobeAnalyticsEvent(), contextData);
    }

    @Test
    public void trackWhenProductNameIsNotSet() {
        ecommerceAnalytics.setProductIdentifier("name");

        EcommerceAnalytics.Event event = EcommerceAnalytics.Event.ProductRemoved;
        TrackPayload payload = new TrackPayload.Builder()
                .userId("test-user")
                .event(event.getSegmentEvent())
                .properties(new Properties()
                        .putSku("ABC")
                        .putPrice(10.0)
                        .putCategory("athletic")
                        .putValue("quantity", 2))
                .build();

        ecommerceAnalytics.track(payload);

        Map<String, Object> contextData = new HashMap<>();
        contextData.put("&&events", event.getAdobeAnalyticsEvent());
        contextData.put("sku", "ABC");

        Mockito.verify(client).trackAction(event.getAdobeAnalyticsEvent(), contextData);
    }

    @Test
    public void trackEventWithNoProperties() {
        ecommerceAnalytics.setProductIdentifier("name");

        EcommerceAnalytics.Event event = EcommerceAnalytics.Event.ProductAdded;
        TrackPayload payload = new TrackPayload.Builder()
                .userId("test-user")
                .event(event.getSegmentEvent())
                .properties(new Properties())
                .build();

        ecommerceAnalytics.track(payload);

        Mockito.verify(client).trackAction(event.getAdobeAnalyticsEvent(), null);
    }

    @Test
    public void trackPurchaseEventToTestDefaults() {
        ecommerceAnalytics.setProductIdentifier("sku");

        Properties.Product product = new Properties.Product("123", "ABC", 0);

        EcommerceAnalytics.Event event = EcommerceAnalytics.Event.OrderCompleted;
        TrackPayload payload = new TrackPayload.Builder()
                .userId("test-user")
                .event(event.getSegmentEvent())
                .properties(new Properties().putProducts(product))
                .build();

        ecommerceAnalytics.track(payload);

        Map<String, Object> contextData = new HashMap<>();
        contextData.put("&&products", ";ABC;1;0.0");
        contextData.put("&&events", event.getAdobeAnalyticsEvent());
        Mockito.verify(client).trackAction(event.getAdobeAnalyticsEvent(), contextData);
    }

    @Test
    public void trackPurchaseWithoutProducts() {
        ecommerceAnalytics.setProductIdentifier("name");

        EcommerceAnalytics.Event event = EcommerceAnalytics.Event.OrderCompleted;
        TrackPayload payload = new TrackPayload.Builder()
                .userId("test-user")
                .event(event.getSegmentEvent())
                .properties(new Properties()
                        .putOrderId("123456"))
                .build();

        ecommerceAnalytics.track(payload);

        Map<String, Object> contextData = new HashMap<>();
        contextData.put("&&events", event.getAdobeAnalyticsEvent());
        contextData.put("purchaseid", "123456");
        Mockito.verify(client).trackAction(event.getAdobeAnalyticsEvent(), contextData);
    }
}
