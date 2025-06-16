package com.webitel.voice.sdk


/**
 * Represents a user with details such as name, email, phone number, and localization information.
 * This class is built using the Builder pattern to allow flexible construction of the User object.
 *
 * @property iss The Issuer Identifier. A URL that identifies the provider of the response.
 * @property sub The Subject Identifier. A unique identifier for the End-User within the Issuer.
 * @property name The full name of the End-User, displayed according to their preferences.
 * @property email The email address of the End-User.
 * @property emailVerified A boolean indicating whether the End-User's email has been verified.
 * @property phoneNumber The phone number of the End-User.
 * @property phoneNumberVerified A boolean indicating whether the End-User's phone number has been verified.
 * @property locale The locale of the End-User, typically represented as a BCP47 language tag.
 */
class User private constructor(builder: Builder) {

    val iss: String
    val sub: String
    val name: String
    val email: String
    val emailVerified: Boolean
    val phoneNumber: String
    val phoneNumberVerified: Boolean
    var locale: String

    init {
        iss = builder.iss
        sub = builder.sub
        name = builder.name
        email = builder.email
        emailVerified = builder.emailVerified
        phoneNumber = builder.phoneNumber
        phoneNumberVerified = builder.phoneNumberVerified
        locale = builder.locale
    }


    /**
     * Builder class to construct a User instance with the necessary attributes.
     * This class follows the builder pattern for more readable and flexible construction of User objects.
     */
    class Builder(
        /**
         * Issuer Identifier for the Issuer of the response.
         * The iss value is a case sensitive URL using the https scheme that contains scheme, host,
         * and optionally, port number and path components and no query or fragment components.
         * */
        val iss: String,

        /**
         * Subject Identifier.
         * A locally unique and never reassigned identifier within the Issuer for the End-User,
         * which is intended to be consumed by the Client, e.g., 24400320 or AItOawmwtWwcT0k51BayewNvutrJUqsvl6qs7A4.
         * It MUST NOT exceed 255 ASCII characters in length.
         * The sub value is a case sensitive string.
         * */
        val sub: String,

        /**
         * End-User's full name in displayable form including all name parts,
         * possibly including titles and suffixes, ordered according to the End-User's locale and preferences.
         * */
        val name: String
    ) {
        var email: String = ""
            private set
        var emailVerified: Boolean = false
            private set

        var phoneNumber: String = ""
            private set
        var phoneNumberVerified: Boolean = false
            private set

        var locale: String = ""
            private set


        /**
         * Sets the email of the End-User.
         * @param value The email address to assign to the End-User.
         * @return The Builder instance to allow for chaining.
         */
        fun email(value: String) = apply { this.email = value }


        /**
         * Sets whether the email of the End-User is verified.
         * @param value Boolean indicating if the email is verified.
         * @return The Builder instance to allow for chaining.
         */
        fun emailVerified(value: Boolean) = apply { this.emailVerified = value }


        /**
         * Sets the phone number of the End-User.
         * @param value The phone number to assign to the End-User.
         * @return The Builder instance to allow for chaining.
         */
        fun phoneNumber(value: String) = apply { this.phoneNumber = value }


        /**
         * Sets whether the phone number of the End-User is verified.
         * @param value Boolean indicating if the phone number is verified.
         * @return The Builder instance to allow for chaining.
         */
        fun phoneNumberVerified(value: Boolean) = apply { this.phoneNumberVerified = value }


        /**
         * Sets the locale of the End-User using a BCP47 language tag (e.g., "en-US").
         * @param value The locale to assign to the End-User.
         * @return The Builder instance to allow for chaining.
         */
        fun locale(value: String) = apply { this.locale = value }


        /**
         * Builds and returns the User instance using the current state of the Builder.
         * @return A fully constructed User instance.
         */
        fun build() = User(this)
    }
}