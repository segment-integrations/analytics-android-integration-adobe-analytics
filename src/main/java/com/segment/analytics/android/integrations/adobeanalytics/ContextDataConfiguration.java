package com.segment.analytics.android.integrations.adobeanalytics;

import com.segment.analytics.ValueMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Encapsulates Context Data settings:
 *
 * <ul>
 *   <li><code>contextValues</code>: Mappings for context data variables
 *   <li><code>customDataPrefix</code>: Prefix for extra properties
 * </ul>
 */
class ContextDataConfiguration {

  private Map<String, String> contextDataVariables;
  private String prefix;

  ContextDataConfiguration(ValueMap settings) {
    this(
        settings.getString("customDataPrefix"),
        settings.getValueMap("contextValues") != null
            ? settings.getValueMap("contextValues").toStringMap()
            : null);
  }

  ContextDataConfiguration(String prefix, Map<String, String> contextDataVariables) {
    this.contextDataVariables = contextDataVariables;
    if (this.contextDataVariables == null) {
      this.contextDataVariables = new HashMap<>();
    }

    this.prefix = prefix;
    // "a." is reserved by Adobe Analytics
    if (this.prefix == null || this.prefix.equals("a.")) {
      this.prefix = "";
    }
  }

  /**
   * Retrieves a set of Segment fields that has associated a Adobe Analytics variable.
   *
   * @return Set of keys.
   */
  Set<String> getEventFieldNames() {
    return contextDataVariables.keySet();
  }

  String getVariableName(String fieldName) {
    return contextDataVariables.get(fieldName);
  }

  /**
   * Retrieves context data variables map, using keys as Segment's event fields and values the
   * corresponding Adobe Analytics variable name.
   *
   * @return The translation map between Segment fields and Adobe Analytics variables.
   */
  Map<String, String> getContextDataVariables() {
    return contextDataVariables;
  }

  /**
   * Gets the prefix added to all extra properties not defined in the translation map.
   *
   * @return Prefix.
   */
  String getPrefix() {
    return prefix;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ContextDataConfiguration that = (ContextDataConfiguration) o;
    return contextDataVariables.equals(that.contextDataVariables) && prefix.equals(that.prefix);
  }

  @Override
  public int hashCode() {
    int hash = 31 + prefix.hashCode();
    hash = (31 * hash) + contextDataVariables.hashCode();

    return hash;
  }
}
