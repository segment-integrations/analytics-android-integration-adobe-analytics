package com.segment.analytics.android.integrations.adobeanalytics;

import com.adobe.primetime.va.simple.MediaHeartbeat;
import com.adobe.primetime.va.simple.MediaObject;
import com.segment.analytics.Properties;

/**
 * PlaybackDelegate implements Adobe's MediaHeartbeatDelegate interface. This implementation allows
 * us to return the position of a video playhead during a video session.
 *
 * @since 1.0.0
 */
class PlaybackDelegate implements MediaHeartbeat.MediaHeartbeatDelegate {

  /**
   * The system time in millis at which the playhead is first set or updated. The playhead is first
   * set upon instantiation of the PlaybackDelegate. The value is updated whenever {@link
   * #calculateCurrentPlayheadPosition()} is invoked.
   */
  private long playheadPositionTime;
  /** The current playhead position in seconds. */
  private long playheadPosition;

  /** Whether the video playhead is in a paused state. */
  private boolean paused;

  /**
   * Quality of service object. This is created and updated upon receipt of a "VideoEvent Quality
   * Updated" event, which triggers {@link #createAndUpdateQosObject(Properties)}.
   */
  private MediaObject qosData;

  PlaybackDelegate() {
    this.playheadPositionTime = System.currentTimeMillis();
    this.paused = false;
  }

  /**
   * Creates a quality of service object.
   *
   * @param properties Properties object from a "VideoEvent Quality Updated" event, which triggers
   *     invocation of this method.
   */
  void createAndUpdateQosObject(Properties properties) {
    qosData =
        MediaHeartbeat.createQoSObject(
            properties.getLong("bitrate", 0),
            properties.getDouble("startupTime", 0),
            properties.getDouble("fps", 0),
            properties.getLong("droppedFrames", 0));
  }

  /** Adobe invokes this method once every ten seconds to report quality of service data. */
  @Override
  public MediaObject getQoSObject() {
    return qosData;
  }

  /**
   * Adobe invokes this method once per second to resolve the current position of the video
   * playhead. Unless paused, this method increments the value of {@link #playheadPosition} by one
   * every second by calling {@link #calculateCurrentPlayheadPosition()}
   */
  @Override
  public Double getCurrentPlaybackTime() {
    if (paused) {
      return (double) playheadPosition;
    }
    return (double) calculateCurrentPlayheadPosition();
  }

  /**
   * Stores the current playhead position in {@link #playheadPosition}. Also stores the system time
   * at which the video was paused in {@link #playheadPositionTime}. Sets {@link #paused} to true so
   * {@link #getCurrentPlaybackTime()} knows the video is in a paused state.
   */
  void pausePlayhead() {
    this.playheadPosition = calculateCurrentPlayheadPosition();
    this.playheadPositionTime = System.currentTimeMillis();
    this.paused = true;
  }

  /**
   * This method sets the {@link #paused} flag to false, as well as sets the {@link
   * #playheadPositionTime} to the time at which the video is unpaused.
   */
  void unPausePlayhead() {
    this.paused = false;
    this.playheadPositionTime = System.currentTimeMillis();
  }

  /**
   * Updates member variables {@link #playheadPositionTime} and {@link #playheadPosition} whenever
   * either a "VideoEvent Playback Seek Completed" or "VideoEvent Content Started" event is received
   * AND contains properties.seekPosition or properties.position, respectively. After invocation,
   * {@link #playheadPositionTime} is assigned to the system time at which the video event was
   * received.
   *
   * @param playheadPosition properties.position passed by the customer into a "VideoEvent Playback
   *     Seek Completed" or "VideoEvent Content Started" event. This value is required for accurate
   *     reporting in the Adobe dashboard. It defaults to 0.
   */
  void updatePlayheadPosition(long playheadPosition) {
    this.playheadPositionTime = System.currentTimeMillis();
    this.playheadPosition = playheadPosition;
  }

  /**
   * Internal helper function used to calculate the {@link #playheadPosition}.
   *
   * <p>System.currentTimeMillis retrieves the current time in milliseconds, then we calculate the
   * delta between the current time and the {@link #playheadPositionTime}, which is the system time
   * at the time a Segment Spec'd VideoEvent event is triggered.
   *
   * @return long playheadPosition
   */
  private long calculateCurrentPlayheadPosition() {
    long currentTime = System.currentTimeMillis();
    long delta = (currentTime - this.playheadPositionTime) / 1000;
    return this.playheadPosition + delta;
  }

  boolean isPaused() {
    return paused;
  }

  MediaObject getQosData() {
    return qosData;
  }
}
