package org.linphone.utils

class Optional<T> private constructor(private val value: T?) {

    fun isPresent(): Boolean = value != null

    fun get(): T {
        if (value == null) {
            throw NoSuchElementException("No value present")
        }
        return value
    }

    fun getOrNull(): T? {
        return value
    }

    companion object {
        fun <T> empty(): Optional<T> = Optional(null)

        fun <T> of(value: T): Optional<T> {
            return Optional(value)
        }

        fun <T> ofNullable(value: T?): Optional<T> {
            return Optional(value)
        }
    }
}
