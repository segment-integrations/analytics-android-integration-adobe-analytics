package com.segment.analytics.android.integrations.adobeanalytics;

import org.junit.Assert;
import org.junit.Test;

public class PlaybackDelegateTest {

  @Test
  public void videoPlaybackDelegatePlay() throws Exception {
    PlaybackDelegate playbackDelegate = new PlaybackDelegate();
    Thread.sleep(2000);
    Assert.assertEquals(playbackDelegate.getCurrentPlaybackTime(), 2.0, 0.01);
  }

  @Test
  public void videoPlaybackDelegatePaused() throws Exception {
    PlaybackDelegate playbackDelegate = new PlaybackDelegate();
    playbackDelegate.pausePlayhead();
    Double firstPlayheadPosition = playbackDelegate.getCurrentPlaybackTime();
    Thread.sleep(2000);
    Assert.assertEquals(playbackDelegate.getCurrentPlaybackTime(), firstPlayheadPosition, 0.01);
  }

  @Test
  public void videoPlaybackDelegatePlayAndPause() throws Exception {
    PlaybackDelegate playbackDelegate = new PlaybackDelegate();
    playbackDelegate.pausePlayhead();
    Thread.sleep(1000);
    playbackDelegate.unPausePlayhead();
    Thread.sleep(3000);
    Assert.assertEquals(playbackDelegate.getCurrentPlaybackTime(), 3.0, 0.01);
  }

}
