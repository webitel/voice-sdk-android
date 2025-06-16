package com.webitel.voice.sdk.internal.sip

import android.os.Parcel
import android.os.Parcelable
import com.google.gson.annotations.SerializedName


 internal class SipConfig: Parcelable {

    @SerializedName("auth")      var auth: String? = null
    @SerializedName("domain")    var domain: String? = null
    @SerializedName("extension") var extension: String? = null
    @SerializedName("password")  var password: String? = null
    @SerializedName("proxy")     var proxy: String? = null


    constructor(auth: String?,
                domain: String?,
                extension: String?,
                password: String?,
                proxy: String?) {

        this.auth = auth
        this.domain = domain
        this.extension = extension
        this.password = password
        this.proxy = proxy
    }


    constructor(source: Parcel) {
        auth = source.readString()
        domain = source.readString()
        extension = source.readString()
        password = source.readString()
        proxy = source.readString()
    }


    override fun describeContents(): Int {
        return 0
    }


    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(auth)
        dest.writeString(domain)
        dest.writeString(extension)
        dest.writeString(password)
        dest.writeString(proxy)
    }


    @JvmName("getProxy1")
    fun getProxy(): String {
        return if(proxy?.startsWith("sip:") == true) {
            proxy!!
        }else {
            "sip:$proxy"
        }
    }


    override fun toString(): String {
        return "auth: $auth; password: $password; domain: $domain; " +
                "extension: $extension; proxy: $proxy"
    }


    fun getSipUri(number:String, name: String, suffix: String = ""):String{
        val x = proxy?.replace("sip:", "")
        return "\"$name\"<sip:$number@$x$suffix>"
    }


    companion object {
        @JvmField val CREATOR: Parcelable.Creator<SipConfig?> = object :
            Parcelable.Creator<SipConfig?> {
            override fun newArray(size: Int): Array<SipConfig?> {
                return arrayOfNulls(size)
            }

            override fun createFromParcel(source: Parcel): SipConfig? {
                return SipConfig(source)
            }
        }
    }
}