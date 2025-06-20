/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 4.0.2
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package org.pjsip.pjsua2;

/**
 * Media.
 */
public class Media {
  private transient long swigCPtr;
  protected transient boolean swigCMemOwn;

  protected Media(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(Media obj) {
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
        pjsua2JNI.delete_Media(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  /**
   * Get type of the media.<br>
   * <br>
   * @return The media type.
   */
  public int getType() {
    return pjsua2JNI.Media_getType(swigCPtr, this);
  }

}
