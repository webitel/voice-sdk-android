/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 4.0.2
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package org.pjsip.pjsua2;

/**
 * This structure describes a media event. It corresponds to the<br>
 * pjmedia_event structure.
 */
public class MediaEvent {
  private transient long swigCPtr;
  protected transient boolean swigCMemOwn;

  protected MediaEvent(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(MediaEvent obj) {
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
        pjsua2JNI.delete_MediaEvent(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  /**
   * The event type.
   */
  public void setType(int value) {
    pjsua2JNI.MediaEvent_type_set(swigCPtr, this, value);
  }

  /**
   * The event type.
   */
  public int getType() {
    return pjsua2JNI.MediaEvent_type_get(swigCPtr, this);
  }

  /**
   * Additional data/parameters about the event. The type of data<br>
   * will be specific to the event type being reported.
   */
  public void setData(MediaEventData value) {
    pjsua2JNI.MediaEvent_data_set(swigCPtr, this, MediaEventData.getCPtr(value), value);
  }

  /**
   * Additional data/parameters about the event. The type of data<br>
   * will be specific to the event type being reported.
   */
  public MediaEventData getData() {
    long cPtr = pjsua2JNI.MediaEvent_data_get(swigCPtr, this);
    return (cPtr == 0) ? null : new MediaEventData(cPtr, false);
  }

  /**
   * Pointer to original pjmedia_event. Only valid when the struct<br>
   * is converted from PJSIP's pjmedia_event.
   */
  public void setPjMediaEvent(SWIGTYPE_p_void value) {
    pjsua2JNI.MediaEvent_pjMediaEvent_set(swigCPtr, this, SWIGTYPE_p_void.getCPtr(value));
  }

  /**
   * Pointer to original pjmedia_event. Only valid when the struct<br>
   * is converted from PJSIP's pjmedia_event.
   */
  public SWIGTYPE_p_void getPjMediaEvent() {
    long cPtr = pjsua2JNI.MediaEvent_pjMediaEvent_get(swigCPtr, this);
    return (cPtr == 0) ? null : new SWIGTYPE_p_void(cPtr, false);
  }

  /**
   * Default constructor
   */
  public MediaEvent() {
    this(pjsua2JNI.new_MediaEvent(), true);
  }

}
