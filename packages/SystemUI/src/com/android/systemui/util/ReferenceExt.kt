package com.android.systemui.util

import java.lang.ref.SoftReference
import java.lang.ref.WeakReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Creates a Kotlin idiomatic weak reference.
 *
 * Usage:
 * ```
 * var weakReferenceObj: Object? by weakReference(null)
 * weakReferenceObj = Object()
 * ```
 */
fun <T> weakReference(obj: T? = null): ReadWriteProperty<Any?, T?> {
    return object : ReadWriteProperty<Any?, T?> {
        var weakRef = WeakReference<T?>(obj)
        override fun getValue(thisRef: Any?, property: KProperty<*>): T? {
            return weakRef.get()
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
            weakRef = WeakReference(value)
        }
    }
}

/**
 * Creates a Kotlin idiomatic soft reference.
 *
 * Usage:
 * ```
 * var softReferenceObj: Object? by softReference(null)
 * softReferenceObj = Object()
 * ```
 */
fun <T> softReference(obj: T? = null): ReadWriteProperty<Any?, T?> {
    return object : ReadWriteProperty<Any?, T?> {
        var softRef = SoftReference<T?>(obj)
        override fun getValue(thisRef: Any?, property: KProperty<*>): T? {
            return softRef.get()
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
            softRef = SoftReference(value)
        }
    }
}
