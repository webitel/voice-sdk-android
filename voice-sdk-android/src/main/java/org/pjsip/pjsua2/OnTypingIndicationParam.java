/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 4.0.2
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package org.pjsip.pjsua2;

/**
 * Parameters for onTypingIndication() account callback.
 */
public class OnTypingIndicationParam {
  private transient long swigCPtr;
  protected transient boolean swigCMemOwn;

  protected OnTypingIndicationParam(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(OnTypingIndicationParam obj) {
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
        pjsua2JNI.delete_OnTypingIndicationParam(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  /**
   * Sender/From URI.
   */
  public void setFromUri(String value) {
    pjsua2JNI.OnTypingIndicationParam_fromUri_set(swigCPtr, this, value);
  }

  /**
   * Sender/From URI.
   */
  public String getFromUri() {
    return pjsua2JNI.OnTypingIndicationParam_fromUri_get(swigCPtr, this);
  }

  /**
   * To URI.
   */
  public void setToUri(String value) {
    pjsua2JNI.OnTypingIndicationParam_toUri_set(swigCPtr, this, value);
  }

  /**
   * To URI.
   */
  public String getToUri() {
    return pjsua2JNI.OnTypingIndicationParam_toUri_get(swigCPtr, this);
  }

  /**
   * The Contact URI.
   */
  public void setContactUri(String value) {
    pjsua2JNI.OnTypingIndicationParam_contactUri_set(swigCPtr, this, value);
  }

  /**
   * The Contact URI.
   */
  public String getContactUri() {
    return pjsua2JNI.OnTypingIndicationParam_contactUri_get(swigCPtr, this);
  }

  /**
   * Boolean to indicate if sender is typing.
   */
  public void setIsTyping(boolean value) {
    pjsua2JNI.OnTypingIndicationParam_isTyping_set(swigCPtr, this, value);
  }

  /**
   * Boolean to indicate if sender is typing.
   */
  public boolean getIsTyping() {
    return pjsua2JNI.OnTypingIndicationParam_isTyping_get(swigCPtr, this);
  }

  /**
   * The whole message buffer.
   */
  public void setRdata(SipRxData value) {
    pjsua2JNI.OnTypingIndicationParam_rdata_set(swigCPtr, this, SipRxData.getCPtr(value), value);
  }

  /**
   * The whole message buffer.
   */
  public SipRxData getRdata() {
    long cPtr = pjsua2JNI.OnTypingIndicationParam_rdata_get(swigCPtr, this);
    return (cPtr == 0) ? null : new SipRxData(cPtr, false);
  }

  public OnTypingIndicationParam() {
    this(pjsua2JNI.new_OnTypingIndicationParam(), true);
  }

}
