/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 4.0.2
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package org.pjsip.pjsua2;

/**
 * RTCP Feedback capability.
 */
public class RtcpFbCap {
  private transient long swigCPtr;
  protected transient boolean swigCMemOwn;

  protected RtcpFbCap(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(RtcpFbCap obj) {
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
        pjsua2JNI.delete_RtcpFbCap(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  /**
   * Specify the codecs to which the capability is applicable. Codec ID is<br>
   * using the same format as in pjmedia_codec_mgr_find_codecs_by_id() and<br>
   * pjmedia_vid_codec_mgr_find_codecs_by_id(), e.g: "L16/8000/1", "PCMU",<br>
   * "H264". This can also be an asterisk ("*") to represent all codecs.
   */
  public void setCodecId(String value) {
    pjsua2JNI.RtcpFbCap_codecId_set(swigCPtr, this, value);
  }

  /**
   * Specify the codecs to which the capability is applicable. Codec ID is<br>
   * using the same format as in pjmedia_codec_mgr_find_codecs_by_id() and<br>
   * pjmedia_vid_codec_mgr_find_codecs_by_id(), e.g: "L16/8000/1", "PCMU",<br>
   * "H264". This can also be an asterisk ("*") to represent all codecs.
   */
  public String getCodecId() {
    return pjsua2JNI.RtcpFbCap_codecId_get(swigCPtr, this);
  }

  /**
   * Specify the RTCP Feedback type.
   */
  public void setType(int value) {
    pjsua2JNI.RtcpFbCap_type_set(swigCPtr, this, value);
  }

  /**
   * Specify the RTCP Feedback type.
   */
  public int getType() {
    return pjsua2JNI.RtcpFbCap_type_get(swigCPtr, this);
  }

  /**
   * Specify the type name if RTCP Feedback type is PJMEDIA_RTCP_FB_OTHER.
   */
  public void setTypeName(String value) {
    pjsua2JNI.RtcpFbCap_typeName_set(swigCPtr, this, value);
  }

  /**
   * Specify the type name if RTCP Feedback type is PJMEDIA_RTCP_FB_OTHER.
   */
  public String getTypeName() {
    return pjsua2JNI.RtcpFbCap_typeName_get(swigCPtr, this);
  }

  /**
   * Specify the RTCP Feedback parameters.
   */
  public void setParam(String value) {
    pjsua2JNI.RtcpFbCap_param_set(swigCPtr, this, value);
  }

  /**
   * Specify the RTCP Feedback parameters.
   */
  public String getParam() {
    return pjsua2JNI.RtcpFbCap_param_get(swigCPtr, this);
  }

  /**
   * Constructor.
   */
  public RtcpFbCap() {
    this(pjsua2JNI.new_RtcpFbCap(), true);
  }

}
