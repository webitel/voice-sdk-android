/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 4.0.2
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package org.pjsip.pjsua2;

/**
 * This structure contains parameters for Call::onCallSdpCreated() callback.
 */
public class OnCallSdpCreatedParam {
  private transient long swigCPtr;
  protected transient boolean swigCMemOwn;

  protected OnCallSdpCreatedParam(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(OnCallSdpCreatedParam obj) {
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
        pjsua2JNI.delete_OnCallSdpCreatedParam(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  /**
   * The SDP has just been created.
   */
  public void setSdp(SdpSession value) {
    pjsua2JNI.OnCallSdpCreatedParam_sdp_set(swigCPtr, this, SdpSession.getCPtr(value), value);
  }

  /**
   * The SDP has just been created.
   */
  public SdpSession getSdp() {
    long cPtr = pjsua2JNI.OnCallSdpCreatedParam_sdp_get(swigCPtr, this);
    return (cPtr == 0) ? null : new SdpSession(cPtr, false);
  }

  /**
   * The remote SDP, will be empty if local is SDP offerer.
   */
  public void setRemSdp(SdpSession value) {
    pjsua2JNI.OnCallSdpCreatedParam_remSdp_set(swigCPtr, this, SdpSession.getCPtr(value), value);
  }

  /**
   * The remote SDP, will be empty if local is SDP offerer.
   */
  public SdpSession getRemSdp() {
    long cPtr = pjsua2JNI.OnCallSdpCreatedParam_remSdp_get(swigCPtr, this);
    return (cPtr == 0) ? null : new SdpSession(cPtr, false);
  }

  public OnCallSdpCreatedParam() {
    this(pjsua2JNI.new_OnCallSdpCreatedParam(), true);
  }

}
