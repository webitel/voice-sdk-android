/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 4.0.2
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package org.pjsip.pjsua2;

/**
 * This structure contains parameters for sending instance message methods,<br>
 * e.g: Buddy::sendInstantMessage(), Call:sendInstantMessage().
 */
public class SendInstantMessageParam {
  private transient long swigCPtr;
  protected transient boolean swigCMemOwn;

  protected SendInstantMessageParam(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(SendInstantMessageParam obj) {
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
        pjsua2JNI.delete_SendInstantMessageParam(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  /**
   * MIME type. Default is "text/plain".
   */
  public void setContentType(String value) {
    pjsua2JNI.SendInstantMessageParam_contentType_set(swigCPtr, this, value);
  }

  /**
   * MIME type. Default is "text/plain".
   */
  public String getContentType() {
    return pjsua2JNI.SendInstantMessageParam_contentType_get(swigCPtr, this);
  }

  /**
   * The message content.
   */
  public void setContent(String value) {
    pjsua2JNI.SendInstantMessageParam_content_set(swigCPtr, this, value);
  }

  /**
   * The message content.
   */
  public String getContent() {
    return pjsua2JNI.SendInstantMessageParam_content_get(swigCPtr, this);
  }

  /**
   * List of headers etc to be included in outgoing request.
   */
  public void setTxOption(SipTxOption value) {
    pjsua2JNI.SendInstantMessageParam_txOption_set(swigCPtr, this, SipTxOption.getCPtr(value), value);
  }

  /**
   * List of headers etc to be included in outgoing request.
   */
  public SipTxOption getTxOption() {
    long cPtr = pjsua2JNI.SendInstantMessageParam_txOption_get(swigCPtr, this);
    return (cPtr == 0) ? null : new SipTxOption(cPtr, false);
  }

  /**
   * User data, which will be given back when the IM callback is called.
   */
  public void setUserData(SWIGTYPE_p_void value) {
    pjsua2JNI.SendInstantMessageParam_userData_set(swigCPtr, this, SWIGTYPE_p_void.getCPtr(value));
  }

  /**
   * User data, which will be given back when the IM callback is called.
   */
  public SWIGTYPE_p_void getUserData() {
    long cPtr = pjsua2JNI.SendInstantMessageParam_userData_get(swigCPtr, this);
    return (cPtr == 0) ? null : new SWIGTYPE_p_void(cPtr, false);
  }

  /**
   * Default constructor initializes with zero/empty values.
   */
  public SendInstantMessageParam() {
    this(pjsua2JNI.new_SendInstantMessageParam(), true);
  }

}
