/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 4.0.2
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package org.pjsip.pjsua2;

/**
 * Parameter of Endpoint::onSelectAccount() callback.
 */
public class OnSelectAccountParam {
  private transient long swigCPtr;
  protected transient boolean swigCMemOwn;

  protected OnSelectAccountParam(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(OnSelectAccountParam obj) {
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
        pjsua2JNI.delete_OnSelectAccountParam(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  /**
   * The incoming request.
   */
  public void setRdata(SipRxData value) {
    pjsua2JNI.OnSelectAccountParam_rdata_set(swigCPtr, this, SipRxData.getCPtr(value), value);
  }

  /**
   * The incoming request.
   */
  public SipRxData getRdata() {
    long cPtr = pjsua2JNI.OnSelectAccountParam_rdata_get(swigCPtr, this);
    return (cPtr == 0) ? null : new SipRxData(cPtr, false);
  }

  /**
   * The account index to be used to handle the request.<br>
   * Upon entry, this will be filled by the account index<br>
   * chosen by the library. Application may change it to<br>
   * another value to use another account.
   */
  public void setAccountIndex(int value) {
    pjsua2JNI.OnSelectAccountParam_accountIndex_set(swigCPtr, this, value);
  }

  /**
   * The account index to be used to handle the request.<br>
   * Upon entry, this will be filled by the account index<br>
   * chosen by the library. Application may change it to<br>
   * another value to use another account.
   */
  public int getAccountIndex() {
    return pjsua2JNI.OnSelectAccountParam_accountIndex_get(swigCPtr, this);
  }

  public OnSelectAccountParam() {
    this(pjsua2JNI.new_OnSelectAccountParam(), true);
  }

}
