/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 4.0.2
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package org.pjsip.pjsua2;

/**
 * Credential information. Credential contains information to authenticate<br>
 * against a service.
 */
public class AuthCredInfo extends PersistentObject {
  private transient long swigCPtr;

  protected AuthCredInfo(long cPtr, boolean cMemoryOwn) {
    super(pjsua2JNI.AuthCredInfo_SWIGUpcast(cPtr), cMemoryOwn);
    swigCPtr = cPtr;
  }

  protected static long getCPtr(AuthCredInfo obj) {
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
        pjsua2JNI.delete_AuthCredInfo(swigCPtr);
      }
      swigCPtr = 0;
    }
    super.delete();
  }

  /**
   * The authentication scheme (e.g. "digest").
   */
  public void setScheme(String value) {
    pjsua2JNI.AuthCredInfo_scheme_set(swigCPtr, this, value);
  }

  /**
   * The authentication scheme (e.g. "digest").
   */
  public String getScheme() {
    return pjsua2JNI.AuthCredInfo_scheme_get(swigCPtr, this);
  }

  /**
   * Realm on which this credential is to be used. Use "*" to make<br>
   * a credential that can be used to authenticate against any challenges.
   */
  public void setRealm(String value) {
    pjsua2JNI.AuthCredInfo_realm_set(swigCPtr, this, value);
  }

  /**
   * Realm on which this credential is to be used. Use "*" to make<br>
   * a credential that can be used to authenticate against any challenges.
   */
  public String getRealm() {
    return pjsua2JNI.AuthCredInfo_realm_get(swigCPtr, this);
  }

  /**
   * Authentication user name.
   */
  public void setUsername(String value) {
    pjsua2JNI.AuthCredInfo_username_set(swigCPtr, this, value);
  }

  /**
   * Authentication user name.
   */
  public String getUsername() {
    return pjsua2JNI.AuthCredInfo_username_get(swigCPtr, this);
  }

  /**
   * Type of data that is contained in the "data" field. Use 0 if the data<br>
   * contains plain text password.
   */
  public void setDataType(int value) {
    pjsua2JNI.AuthCredInfo_dataType_set(swigCPtr, this, value);
  }

  /**
   * Type of data that is contained in the "data" field. Use 0 if the data<br>
   * contains plain text password.
   */
  public int getDataType() {
    return pjsua2JNI.AuthCredInfo_dataType_get(swigCPtr, this);
  }

  /**
   * The data, which can be a plain text password or a hashed digest.
   */
  public void setData(String value) {
    pjsua2JNI.AuthCredInfo_data_set(swigCPtr, this, value);
  }

  /**
   * The data, which can be a plain text password or a hashed digest.
   */
  public String getData() {
    return pjsua2JNI.AuthCredInfo_data_get(swigCPtr, this);
  }

  /**
   * Digest algorithm type.
   */
  public void setAlgoType(int value) {
    pjsua2JNI.AuthCredInfo_algoType_set(swigCPtr, this, value);
  }

  /**
   * Digest algorithm type.
   */
  public int getAlgoType() {
    return pjsua2JNI.AuthCredInfo_algoType_get(swigCPtr, this);
  }

  /**
   *  Permanent subscriber key. 
   */
  public void setAkaK(String value) {
    pjsua2JNI.AuthCredInfo_akaK_set(swigCPtr, this, value);
  }

  /**
   *  Permanent subscriber key. 
   */
  public String getAkaK() {
    return pjsua2JNI.AuthCredInfo_akaK_get(swigCPtr, this);
  }

  /**
   *  Operator variant key. 
   */
  public void setAkaOp(String value) {
    pjsua2JNI.AuthCredInfo_akaOp_set(swigCPtr, this, value);
  }

  /**
   *  Operator variant key. 
   */
  public String getAkaOp() {
    return pjsua2JNI.AuthCredInfo_akaOp_get(swigCPtr, this);
  }

  /**
   *  Authentication Management Field 
   */
  public void setAkaAmf(String value) {
    pjsua2JNI.AuthCredInfo_akaAmf_set(swigCPtr, this, value);
  }

  /**
   *  Authentication Management Field 
   */
  public String getAkaAmf() {
    return pjsua2JNI.AuthCredInfo_akaAmf_get(swigCPtr, this);
  }

  /**
   *  Default constructor 
   */
  public AuthCredInfo() {
    this(pjsua2JNI.new_AuthCredInfo__SWIG_0(), true);
  }

  /**
   *  Construct a credential with the specified parameters 
   */
  public AuthCredInfo(String scheme, String realm, String user_name, int data_type, String data) {
    this(pjsua2JNI.new_AuthCredInfo__SWIG_1(scheme, realm, user_name, data_type, data), true);
  }

  /**
   * Read this object from a container node.<br>
   * <br>
   * @param node              Container to read values from.
   */
  public void readObject(ContainerNode node) throws java.lang.Exception {
    pjsua2JNI.AuthCredInfo_readObject(swigCPtr, this, ContainerNode.getCPtr(node), node);
  }

  /**
   * Write this object to a container node.<br>
   * <br>
   * @param node              Container to write values to.
   */
  public void writeObject(ContainerNode node) throws java.lang.Exception {
    pjsua2JNI.AuthCredInfo_writeObject(swigCPtr, this, ContainerNode.getCPtr(node), node);
  }

}
