/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 4.0.2
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package org.pjsip.pjsua2;

/**
 * Account configuration.
 */
public class AccountConfig extends PersistentObject {
  private transient long swigCPtr;

  protected AccountConfig(long cPtr, boolean cMemoryOwn) {
    super(pjsua2JNI.AccountConfig_SWIGUpcast(cPtr), cMemoryOwn);
    swigCPtr = cPtr;
  }

  protected static long getCPtr(AccountConfig obj) {
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
        pjsua2JNI.delete_AccountConfig(swigCPtr);
      }
      swigCPtr = 0;
    }
    super.delete();
  }

  /**
   * Account priority, which is used to control the order of matching<br>
   * incoming/outgoing requests. The higher the number means the higher<br>
   * the priority is, and the account will be matched first.
   */
  public void setPriority(int value) {
    pjsua2JNI.AccountConfig_priority_set(swigCPtr, this, value);
  }

  /**
   * Account priority, which is used to control the order of matching<br>
   * incoming/outgoing requests. The higher the number means the higher<br>
   * the priority is, and the account will be matched first.
   */
  public int getPriority() {
    return pjsua2JNI.AccountConfig_priority_get(swigCPtr, this);
  }

  /**
   * The Address of Record or AOR, that is full SIP URL that identifies the<br>
   * account. The value can take name address or URL format, and will look<br>
   * something like "sip:account@serviceprovider".<br>
   * <br>
   * This field is mandatory.
   */
  public void setIdUri(String value) {
    pjsua2JNI.AccountConfig_idUri_set(swigCPtr, this, value);
  }

  /**
   * The Address of Record or AOR, that is full SIP URL that identifies the<br>
   * account. The value can take name address or URL format, and will look<br>
   * something like "sip:account@serviceprovider".<br>
   * <br>
   * This field is mandatory.
   */
  public String getIdUri() {
    return pjsua2JNI.AccountConfig_idUri_get(swigCPtr, this);
  }

  /**
   * Registration settings.
   */
  public void setRegConfig(AccountRegConfig value) {
    pjsua2JNI.AccountConfig_regConfig_set(swigCPtr, this, AccountRegConfig.getCPtr(value), value);
  }

  /**
   * Registration settings.
   */
  public AccountRegConfig getRegConfig() {
    long cPtr = pjsua2JNI.AccountConfig_regConfig_get(swigCPtr, this);
    return (cPtr == 0) ? null : new AccountRegConfig(cPtr, false);
  }

  /**
   * SIP settings.
   */
  public void setSipConfig(AccountSipConfig value) {
    pjsua2JNI.AccountConfig_sipConfig_set(swigCPtr, this, AccountSipConfig.getCPtr(value), value);
  }

  /**
   * SIP settings.
   */
  public AccountSipConfig getSipConfig() {
    long cPtr = pjsua2JNI.AccountConfig_sipConfig_get(swigCPtr, this);
    return (cPtr == 0) ? null : new AccountSipConfig(cPtr, false);
  }

  /**
   * Call settings.
   */
  public void setCallConfig(AccountCallConfig value) {
    pjsua2JNI.AccountConfig_callConfig_set(swigCPtr, this, AccountCallConfig.getCPtr(value), value);
  }

  /**
   * Call settings.
   */
  public AccountCallConfig getCallConfig() {
    long cPtr = pjsua2JNI.AccountConfig_callConfig_get(swigCPtr, this);
    return (cPtr == 0) ? null : new AccountCallConfig(cPtr, false);
  }

  /**
   * Presence settings.
   */
  public void setPresConfig(AccountPresConfig value) {
    pjsua2JNI.AccountConfig_presConfig_set(swigCPtr, this, AccountPresConfig.getCPtr(value), value);
  }

  /**
   * Presence settings.
   */
  public AccountPresConfig getPresConfig() {
    long cPtr = pjsua2JNI.AccountConfig_presConfig_get(swigCPtr, this);
    return (cPtr == 0) ? null : new AccountPresConfig(cPtr, false);
  }

  /**
   * MWI (Message Waiting Indication) settings.
   */
  public void setMwiConfig(AccountMwiConfig value) {
    pjsua2JNI.AccountConfig_mwiConfig_set(swigCPtr, this, AccountMwiConfig.getCPtr(value), value);
  }

  /**
   * MWI (Message Waiting Indication) settings.
   */
  public AccountMwiConfig getMwiConfig() {
    long cPtr = pjsua2JNI.AccountConfig_mwiConfig_get(swigCPtr, this);
    return (cPtr == 0) ? null : new AccountMwiConfig(cPtr, false);
  }

  /**
   * NAT settings.
   */
  public void setNatConfig(AccountNatConfig value) {
    pjsua2JNI.AccountConfig_natConfig_set(swigCPtr, this, AccountNatConfig.getCPtr(value), value);
  }

  /**
   * NAT settings.
   */
  public AccountNatConfig getNatConfig() {
    long cPtr = pjsua2JNI.AccountConfig_natConfig_get(swigCPtr, this);
    return (cPtr == 0) ? null : new AccountNatConfig(cPtr, false);
  }

  /**
   * Media settings (applicable for both audio and video).
   */
  public void setMediaConfig(AccountMediaConfig value) {
    pjsua2JNI.AccountConfig_mediaConfig_set(swigCPtr, this, AccountMediaConfig.getCPtr(value), value);
  }

  /**
   * Media settings (applicable for both audio and video).
   */
  public AccountMediaConfig getMediaConfig() {
    long cPtr = pjsua2JNI.AccountConfig_mediaConfig_get(swigCPtr, this);
    return (cPtr == 0) ? null : new AccountMediaConfig(cPtr, false);
  }

  /**
   * Video settings.
   */
  public void setVideoConfig(AccountVideoConfig value) {
    pjsua2JNI.AccountConfig_videoConfig_set(swigCPtr, this, AccountVideoConfig.getCPtr(value), value);
  }

  /**
   * Video settings.
   */
  public AccountVideoConfig getVideoConfig() {
    long cPtr = pjsua2JNI.AccountConfig_videoConfig_get(swigCPtr, this);
    return (cPtr == 0) ? null : new AccountVideoConfig(cPtr, false);
  }

  /**
   * IP Change settings.
   */
  public void setIpChangeConfig(AccountIpChangeConfig value) {
    pjsua2JNI.AccountConfig_ipChangeConfig_set(swigCPtr, this, AccountIpChangeConfig.getCPtr(value), value);
  }

  /**
   * IP Change settings.
   */
  public AccountIpChangeConfig getIpChangeConfig() {
    long cPtr = pjsua2JNI.AccountConfig_ipChangeConfig_get(swigCPtr, this);
    return (cPtr == 0) ? null : new AccountIpChangeConfig(cPtr, false);
  }

  /**
   * Default constructor will initialize with default values.
   */
  public AccountConfig() {
    this(pjsua2JNI.new_AccountConfig(), true);
  }

  /**
   * Read this object from a container node.<br>
   * <br>
   * @param node              Container to read values from.
   */
  public void readObject(ContainerNode node) throws java.lang.Exception {
    pjsua2JNI.AccountConfig_readObject(swigCPtr, this, ContainerNode.getCPtr(node), node);
  }

  /**
   * Write this object to a container node.<br>
   * <br>
   * @param node              Container to write values to.
   */
  public void writeObject(ContainerNode node) throws java.lang.Exception {
    pjsua2JNI.AccountConfig_writeObject(swigCPtr, this, ContainerNode.getCPtr(node), node);
  }

}
