/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 4.0.2
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package org.pjsip.pjsua2;

public class SockOptVector extends java.util.AbstractList<SockOpt> implements java.util.RandomAccess {
  private transient long swigCPtr;
  protected transient boolean swigCMemOwn;

  protected SockOptVector(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(SockOptVector obj) {
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
        pjsua2JNI.delete_SockOptVector(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  public SockOptVector(SockOpt[] initialElements) {
    this();
    reserve(initialElements.length);

    for (SockOpt element : initialElements) {
      add(element);
    }
  }

  public SockOptVector(Iterable<SockOpt> initialElements) {
    this();
    for (SockOpt element : initialElements) {
      add(element);
    }
  }

  public SockOpt get(int index) {
    return doGet(index);
  }

  public SockOpt set(int index, SockOpt e) {
    return doSet(index, e);
  }

  public boolean add(SockOpt e) {
    modCount++;
    doAdd(e);
    return true;
  }

  public void add(int index, SockOpt e) {
    modCount++;
    doAdd(index, e);
  }

  public SockOpt remove(int index) {
    modCount++;
    return doRemove(index);
  }

  protected void removeRange(int fromIndex, int toIndex) {
    modCount++;
    doRemoveRange(fromIndex, toIndex);
  }

  public int size() {
    return doSize();
  }

  public SockOptVector() {
    this(pjsua2JNI.new_SockOptVector__SWIG_0(), true);
  }

  public SockOptVector(SockOptVector other) {
    this(pjsua2JNI.new_SockOptVector__SWIG_1(SockOptVector.getCPtr(other), other), true);
  }

  public long capacity() {
    return pjsua2JNI.SockOptVector_capacity(swigCPtr, this);
  }

  public void reserve(long n) {
    pjsua2JNI.SockOptVector_reserve(swigCPtr, this, n);
  }

  public boolean isEmpty() {
    return pjsua2JNI.SockOptVector_isEmpty(swigCPtr, this);
  }

  public void clear() {
    pjsua2JNI.SockOptVector_clear(swigCPtr, this);
  }

  public SockOptVector(int count, SockOpt value) {
    this(pjsua2JNI.new_SockOptVector__SWIG_2(count, SockOpt.getCPtr(value), value), true);
  }

  private int doSize() {
    return pjsua2JNI.SockOptVector_doSize(swigCPtr, this);
  }

  private void doAdd(SockOpt x) {
    pjsua2JNI.SockOptVector_doAdd__SWIG_0(swigCPtr, this, SockOpt.getCPtr(x), x);
  }

  private void doAdd(int index, SockOpt x) {
    pjsua2JNI.SockOptVector_doAdd__SWIG_1(swigCPtr, this, index, SockOpt.getCPtr(x), x);
  }

  private SockOpt doRemove(int index) {
    return new SockOpt(pjsua2JNI.SockOptVector_doRemove(swigCPtr, this, index), true);
  }

  private SockOpt doGet(int index) {
    return new SockOpt(pjsua2JNI.SockOptVector_doGet(swigCPtr, this, index), false);
  }

  private SockOpt doSet(int index, SockOpt val) {
    return new SockOpt(pjsua2JNI.SockOptVector_doSet(swigCPtr, this, index, SockOpt.getCPtr(val), val), true);
  }

  private void doRemoveRange(int fromIndex, int toIndex) {
    pjsua2JNI.SockOptVector_doRemoveRange(swigCPtr, this, fromIndex, toIndex);
  }

}
