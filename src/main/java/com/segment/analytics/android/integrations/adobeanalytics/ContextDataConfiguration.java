package com.segment.analytics.android.integrations.adobeanalytics;

import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.BasePayload;

import java.util.Arrays;
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
   * Inspects the event payload and retrieves the value described in the field. Field respects dot
   * notation (myObject.name) for event properties. If there is a dot present at the beginning of
   * the field, it will retrieve the value from the root of the payload.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li><code>myObject.name</code> = <code>track.properties.myObject.name</code>
   *   <li><code>.userId</code> = <code>identify.userId</code>
   *   <li><code>.context.library</code> = <code>track.context.library</code>
   * </ul>
   *
   * @param field Field name.
   * @param eventPayload Event payload.
   * @return The value if found, <code>null</code> otherwise.
   */
  Object searchValue(String field, BasePayload eventPayload) {
    if (field == null || field.trim().length() == 0) {
      throw new IllegalArgumentException("The field name must be defined");
    }

    String[] searchPaths = field.split("\\.");

    // Using the properties object as starting point by default.
    ValueMap values = eventPayload.getValueMap("properties");

    // Dot is present at the beginning of the field name
    if (searchPaths[0].equals("")) {
      // Using the root of the payload as starting point
      values = eventPayload;
      searchPaths = Arrays.copyOfRange(searchPaths, 1, searchPaths.length);
    }

    return searchValue(searchPaths, values);
  }

  private Object searchValue(String[] searchPath, ValueMap values) {

    ValueMap currentValues = values;

    for (int i = 0; i < searchPath.length; i++) {
      String path = searchPath[i];

      if (path.trim().length() == 0) {
        throw new IllegalArgumentException("Invalid field name");
      }

      if (!currentValues.containsKey(path)) {
        return null;
      }

      Object value = currentValues.get(path);
      if (value == null) {
        return null;
      }

      if (i == searchPath.length - 1) {
        return value;
      }

      if (value instanceof ValueMap) {
        currentValues = (ValueMap) value;
      } else if (value instanceof Map) {
        try {
          currentValues = new ValueMap((Map<String, Object>) value);
        } catch (ClassCastException e) {
          return null;
        }
      }
    }

    return null;
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
