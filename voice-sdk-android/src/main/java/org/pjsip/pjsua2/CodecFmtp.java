/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 4.0.2
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package org.pjsip.pjsua2;

/**
 * Structure of codec specific parameters which contains name=value pairs.<br>
 * The codec specific parameters are to be used with SDP according to<br>
 * the standards (e.g: RFC 3555) in SDP 'a=fmtp' attribute.
 */
public class CodecFmtp {
  private transient long swigCPtr;
  protected transient boolean swigCMemOwn;

  protected CodecFmtp(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(CodecFmtp obj) {
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
        pjsua2JNI.delete_CodecFmtp(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  /**
   *  name  
   */
  public void setName(String value) {
    pjsua2JNI.CodecFmtp_name_set(swigCPtr, this, value);
  }

  /**
   *  name  
   */
  public String getName() {
    return pjsua2JNI.CodecFmtp_name_get(swigCPtr, this);
  }

  /**
   *  value 
   */
  public void setVal(String value) {
    pjsua2JNI.CodecFmtp_val_set(swigCPtr, this, value);
  }

  /**
   *  value 
   */
  public String getVal() {
    return pjsua2JNI.CodecFmtp_val_get(swigCPtr, this);
  }

  public CodecFmtp() {
    this(pjsua2JNI.new_CodecFmtp(), true);
  }

}
