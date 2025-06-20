/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 4.0.2
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package org.pjsip.pjsua2;

/**
 *  Raise Error exception if the expression fails  Raise Error exception if the status fails  Raise Error exception if the expression fails  Run the statement and catch and ignore Error exception <br>
 * Version information.
 */
public class Version {
  private transient long swigCPtr;
  protected transient boolean swigCMemOwn;

  protected Version(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(Version obj) {
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
        pjsua2JNI.delete_Version(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  /**
   *  Major number 
   */
  public void setMajor(int value) {
    pjsua2JNI.Version_major_set(swigCPtr, this, value);
  }

  /**
   *  Major number 
   */
  public int getMajor() {
    return pjsua2JNI.Version_major_get(swigCPtr, this);
  }

  /**
   *  Minor number 
   */
  public void setMinor(int value) {
    pjsua2JNI.Version_minor_set(swigCPtr, this, value);
  }

  /**
   *  Minor number 
   */
  public int getMinor() {
    return pjsua2JNI.Version_minor_get(swigCPtr, this);
  }

  /**
   *  Additional revision number 
   */
  public void setRev(int value) {
    pjsua2JNI.Version_rev_set(swigCPtr, this, value);
  }

  /**
   *  Additional revision number 
   */
  public int getRev() {
    return pjsua2JNI.Version_rev_get(swigCPtr, this);
  }

  /**
   *  Version suffix (e.g. "-svn") 
   */
  public void setSuffix(String value) {
    pjsua2JNI.Version_suffix_set(swigCPtr, this, value);
  }

  /**
   *  Version suffix (e.g. "-svn") 
   */
  public String getSuffix() {
    return pjsua2JNI.Version_suffix_get(swigCPtr, this);
  }

  /**
   *  The full version info (e.g. "2.1.0-svn") 
   */
  public void setFull(String value) {
    pjsua2JNI.Version_full_set(swigCPtr, this, value);
  }

  /**
   *  The full version info (e.g. "2.1.0-svn") 
   */
  public String getFull() {
    return pjsua2JNI.Version_full_get(swigCPtr, this);
  }

  /**
   * PJLIB version number as three bytes with the following format:<br>
   * 0xMMIIRR00, where MM: major number, II: minor number, RR: revision<br>
   * number, 00: always zero for now.
   */
  public void setNumeric(long value) {
    pjsua2JNI.Version_numeric_set(swigCPtr, this, value);
  }

  /**
   * PJLIB version number as three bytes with the following format:<br>
   * 0xMMIIRR00, where MM: major number, II: minor number, RR: revision<br>
   * number, 00: always zero for now.
   */
  public long getNumeric() {
    return pjsua2JNI.Version_numeric_get(swigCPtr, this);
  }

  public Version() {
    this(pjsua2JNI.new_Version(), true);
  }

}
