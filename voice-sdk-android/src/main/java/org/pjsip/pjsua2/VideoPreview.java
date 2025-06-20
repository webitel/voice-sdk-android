/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 4.0.2
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package org.pjsip.pjsua2;

/**
 * Video Preview
 */
public class VideoPreview {
  private transient long swigCPtr;
  protected transient boolean swigCMemOwn;

  protected VideoPreview(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(VideoPreview obj) {
    return (obj == null) ? 0 : obj.swigCPtr;
  }

  @SuppressWarnings("deprecation")
  protected void finalize() {
    delete();
  }

  public synchronized void delete() {
    if (swigCPtr != 0) {
      if (swigCMemOwn) {
        swigCMemOwn = false;
        pjsua2JNI.delete_VideoPreview(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  /**
   * Constructor
   */
  public VideoPreview(int dev_id) {
    this(pjsua2JNI.new_VideoPreview(dev_id), true);
  }

  /**
   * Determine if the specified video input device has built-in native<br>
   * preview capability. This is a convenience function that is equal to<br>
   * querying device's capability for PJMEDIA_VID_DEV_CAP_INPUT_PREVIEW<br>
   * capability.<br>
   * <br>
   * @return true if it has.
   */
  public boolean hasNative() {
    return pjsua2JNI.VideoPreview_hasNative(swigCPtr, this);
  }

  /**
   * Start video preview window for the specified capture device.<br>
   * <br>
   * @param param     Video preview parameters.
   */
  public void start(VideoPreviewOpParam param) throws java.lang.Exception {
    pjsua2JNI.VideoPreview_start(swigCPtr, this, VideoPreviewOpParam.getCPtr(param), param);
  }

  /**
   * Stop video preview.
   */
  public void stop() throws java.lang.Exception {
    pjsua2JNI.VideoPreview_stop(swigCPtr, this);
  }

  /**
   * Get the preview window handle associated with the capture device,if any.
   */
  public VideoWindow getVideoWindow() {
    return new VideoWindow(pjsua2JNI.VideoPreview_getVideoWindow(swigCPtr, this), true);
  }

  /**
   * Get video media or conference bridge port of the video capture device.<br>
   * <br>
   * @return Video media of the video capture device.
   */
  public VideoMedia getVideoMedia() throws java.lang.Exception {
    return new VideoMedia(pjsua2JNI.VideoPreview_getVideoMedia(swigCPtr, this), true);
  }

}
