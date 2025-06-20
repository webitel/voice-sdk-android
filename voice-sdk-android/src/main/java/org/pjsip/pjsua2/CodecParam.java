/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 4.0.2
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package org.pjsip.pjsua2;

/**
 * Detailed codec attributes used in configuring an audio codec and in querying<br>
 * the capability of audio codec factories.<br>
 * <br>
 * Please note that codec parameter also contains SDP specific setting,<br>
 * setting::decFmtp and setting::encFmtp, which may need to be set<br>
 * appropriately based on the effective setting. <br>
 * See each codec documentation for more detail.
 */
public class CodecParam {
  private transient long swigCPtr;
  protected transient boolean swigCMemOwn;

  protected CodecParam(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(CodecParam obj) {
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
        pjsua2JNI.delete_CodecParam(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  /**
   *  info 
   */
  public void setInfo(CodecParamInfo value) {
    pjsua2JNI.CodecParam_info_set(swigCPtr, this, CodecParamInfo.getCPtr(value), value);
  }

  /**
   *  info 
   */
  public CodecParamInfo getInfo() {
    long cPtr = pjsua2JNI.CodecParam_info_get(swigCPtr, this);
    return (cPtr == 0) ? null : new CodecParamInfo(cPtr, false);
  }

  /**
   *  setting 
   */
  public void setSetting(CodecParamSetting value) {
    pjsua2JNI.CodecParam_setting_set(swigCPtr, this, CodecParamSetting.getCPtr(value), value);
  }

  /**
   *  setting 
   */
  public CodecParamSetting getSetting() {
    long cPtr = pjsua2JNI.CodecParam_setting_get(swigCPtr, this);
    return (cPtr == 0) ? null : new CodecParamSetting(cPtr, false);
  }

  public CodecParam() {
    this(pjsua2JNI.new_CodecParam(), true);
  }

}
