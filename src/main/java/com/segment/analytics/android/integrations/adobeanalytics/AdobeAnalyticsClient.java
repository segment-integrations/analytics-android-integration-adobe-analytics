package com.segment.analytics.android.integrations.adobeanalytics;

import android.app.Activity;
import android.content.Context;

import com.adobe.mobile.Analytics;
import com.adobe.mobile.Config;

import java.util.Map;

/**
 * Class to wrap the Adobe Analytics SDK and make testing easier without hacks or
 * altering bytecode.
 *
 * @since 1.0.3
 */
interface AdobeAnalyticsClient {

    void trackAction(String action, Map<String, Object> contextData);
    void trackState(String state, Map<String, Object> contextData);
    void setContext(Context context);
    void pauseCollectingLifecycleData();
    void collectLifecycleData(Activity activity);
    void setUserIdentifier(String identifier);
    void setDebugLogging(Boolean debugLogging);

    /**
     * Flushes the client's internal queue.
     */
    void flushQueue();

    /**
     * Default implementation of Adobe Analytics client. It wraps all AA methods used by the integration.
     *
     * Do not add logic here.
     *
     * @since 1.0.3
     */
    class DefaultClient implements AdobeAnalyticsClient {

        public DefaultClient() {
        }

        @Override
        public void trackAction(String action, Map<String, Object> contextData) {
            Analytics.trackAction(action, contextData);
        }

        @Override
        public void trackState(String state, Map<String, Object> contextData) {
            Analytics.trackState(state, contextData);
        }

        @Override
        public void setContext(Context context) {
            Config.setContext(context);
        }

        @Override
        public void pauseCollectingLifecycleData() {
            Config.pauseCollectingLifecycleData();
        }

        @Override
        public void collectLifecycleData(Activity activity) {
            Config.collectLifecycleData();
        }

        @Override
        public void setUserIdentifier(String identifier) {
            Config.setUserIdentifier(identifier);
        }

        @Override
        public void setDebugLogging(Boolean debugLogging) {
            Config.setDebugLogging(debugLogging);
        }

        @Override
        public void flushQueue() {
            Analytics.sendQueuedHits();
        }
    }

}
