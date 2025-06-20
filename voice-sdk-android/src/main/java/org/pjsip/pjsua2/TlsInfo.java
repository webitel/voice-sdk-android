/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 4.0.2
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package org.pjsip.pjsua2;

/**
 * TLS transport information.
 */
public class TlsInfo {
  private transient long swigCPtr;
  protected transient boolean swigCMemOwn;

  protected TlsInfo(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(TlsInfo obj) {
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
        pjsua2JNI.delete_TlsInfo(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  /**
   * Describes whether secure socket connection is established, i.e: TLS/SSL <br>
   * handshaking has been done successfully.
   */
  public void setEstablished(boolean value) {
    pjsua2JNI.TlsInfo_established_set(swigCPtr, this, value);
  }

  /**
   * Describes whether secure socket connection is established, i.e: TLS/SSL <br>
   * handshaking has been done successfully.
   */
  public boolean getEstablished() {
    return pjsua2JNI.TlsInfo_established_get(swigCPtr, this);
  }

  /**
   * Describes secure socket protocol being used, see #pj_ssl_sock_proto. <br>
   * Use bitwise OR operation to combine the protocol type.
   */
  public void setProtocol(long value) {
    pjsua2JNI.TlsInfo_protocol_set(swigCPtr, this, value);
  }

  /**
   * Describes secure socket protocol being used, see #pj_ssl_sock_proto. <br>
   * Use bitwise OR operation to combine the protocol type.
   */
  public long getProtocol() {
    return pjsua2JNI.TlsInfo_protocol_get(swigCPtr, this);
  }

  /**
   * Describes cipher suite being used, this will only be set when connection<br>
   * is established.
   */
  public void setCipher(int value) {
    pjsua2JNI.TlsInfo_cipher_set(swigCPtr, this, value);
  }

  /**
   * Describes cipher suite being used, this will only be set when connection<br>
   * is established.
   */
  public int getCipher() {
    return pjsua2JNI.TlsInfo_cipher_get(swigCPtr, this);
  }

  /**
   * Describes cipher name being used, this will only be set when connection<br>
   * is established.
   */
  public void setCipherName(String value) {
    pjsua2JNI.TlsInfo_cipherName_set(swigCPtr, this, value);
  }

  /**
   * Describes cipher name being used, this will only be set when connection<br>
   * is established.
   */
  public String getCipherName() {
    return pjsua2JNI.TlsInfo_cipherName_get(swigCPtr, this);
  }

  /**
   * Describes local address.
   */
  public void setLocalAddr(String value) {
    pjsua2JNI.TlsInfo_localAddr_set(swigCPtr, this, value);
  }

  /**
   * Describes local address.
   */
  public String getLocalAddr() {
    return pjsua2JNI.TlsInfo_localAddr_get(swigCPtr, this);
  }

  /**
   * Describes remote address.
   */
  public void setRemoteAddr(String value) {
    pjsua2JNI.TlsInfo_remoteAddr_set(swigCPtr, this, value);
  }

  /**
   * Describes remote address.
   */
  public String getRemoteAddr() {
    return pjsua2JNI.TlsInfo_remoteAddr_get(swigCPtr, this);
  }

  /**
   * Describes active local certificate info. Use SslCertInfo.isEmpty()<br>
   * to check if the local cert info is available.
   */
  public void setLocalCertInfo(SslCertInfo value) {
    pjsua2JNI.TlsInfo_localCertInfo_set(swigCPtr, this, SslCertInfo.getCPtr(value), value);
  }

  /**
   * Describes active local certificate info. Use SslCertInfo.isEmpty()<br>
   * to check if the local cert info is available.
   */
  public SslCertInfo getLocalCertInfo() {
    long cPtr = pjsua2JNI.TlsInfo_localCertInfo_get(swigCPtr, this);
    return (cPtr == 0) ? null : new SslCertInfo(cPtr, false);
  }

  /**
   * Describes active remote certificate info. Use SslCertInfo.isEmpty()<br>
   * to check if the remote cert info is available.
   */
  public void setRemoteCertInfo(SslCertInfo value) {
    pjsua2JNI.TlsInfo_remoteCertInfo_set(swigCPtr, this, SslCertInfo.getCPtr(value), value);
  }

  /**
   * Describes active remote certificate info. Use SslCertInfo.isEmpty()<br>
   * to check if the remote cert info is available.
   */
  public SslCertInfo getRemoteCertInfo() {
    long cPtr = pjsua2JNI.TlsInfo_remoteCertInfo_get(swigCPtr, this);
    return (cPtr == 0) ? null : new SslCertInfo(cPtr, false);
  }

  /**
   * Status of peer certificate verification.
   */
  public void setVerifyStatus(long value) {
    pjsua2JNI.TlsInfo_verifyStatus_set(swigCPtr, this, value);
  }

  /**
   * Status of peer certificate verification.
   */
  public long getVerifyStatus() {
    return pjsua2JNI.TlsInfo_verifyStatus_get(swigCPtr, this);
  }

  /**
   * Error messages (if any) of peer certificate verification, based on<br>
   * the field verifyStatus above.
   */
  public void setVerifyMsgs(StringVector value) {
    pjsua2JNI.TlsInfo_verifyMsgs_set(swigCPtr, this, StringVector.getCPtr(value), value);
  }

  /**
   * Error messages (if any) of peer certificate verification, based on<br>
   * the field verifyStatus above.
   */
  public StringVector getVerifyMsgs() {
    long cPtr = pjsua2JNI.TlsInfo_verifyMsgs_get(swigCPtr, this);
    return (cPtr == 0) ? null : new StringVector(cPtr, false);
  }

  /**
   * Constructor.
   */
  public TlsInfo() {
    this(pjsua2JNI.new_TlsInfo(), true);
  }

  /**
   * Check if the info is set with empty values.<br>
   * <br>
   * @return True if the info is empty.
   */
  public boolean isEmpty() {
    return pjsua2JNI.TlsInfo_isEmpty(swigCPtr, this);
  }

}
