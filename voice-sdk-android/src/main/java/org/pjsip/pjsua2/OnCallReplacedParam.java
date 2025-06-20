/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 4.0.2
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package org.pjsip.pjsua2;

/**
 * This structure contains parameters for Call::onCallReplaced() callback.
 */
public class OnCallReplacedParam {
  private transient long swigCPtr;
  protected transient boolean swigCMemOwn;

  protected OnCallReplacedParam(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(OnCallReplacedParam obj) {
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
        pjsua2JNI.delete_OnCallReplacedParam(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  /**
   * The new call id.
   */
  public void setNewCallId(int value) {
    pjsua2JNI.OnCallReplacedParam_newCallId_set(swigCPtr, this, value);
  }

  /**
   * The new call id.
   */
  public int getNewCallId() {
    return pjsua2JNI.OnCallReplacedParam_newCallId_get(swigCPtr, this);
  }

  /**
   * New Call derived object instantiated by application.
   */
  public void setNewCall(Call value) {
    pjsua2JNI.OnCallReplacedParam_newCall_set(swigCPtr, this, Call.getCPtr(value), value);
  }

  /**
   * New Call derived object instantiated by application.
   */
  public Call getNewCall() {
    long cPtr = pjsua2JNI.OnCallReplacedParam_newCall_get(swigCPtr, this);
    return (cPtr == 0) ? null : new Call(cPtr, false);
  }

  public OnCallReplacedParam() {
    this(pjsua2JNI.new_OnCallReplacedParam(), true);
  }

}
