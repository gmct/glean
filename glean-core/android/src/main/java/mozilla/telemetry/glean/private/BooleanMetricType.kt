/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.telemetry.glean.private

import androidx.annotation.VisibleForTesting
import com.sun.jna.StringArray
import mozilla.telemetry.glean.Glean
import mozilla.telemetry.glean.rust.LibGleanFFI
import mozilla.telemetry.glean.rust.toByte
import mozilla.telemetry.glean.rust.RustError

import mozilla.telemetry.glean.Dispatchers
import mozilla.telemetry.glean.rust.toBoolean

class BooleanMetricType(
    disabled: Boolean,
    category: String,
    lifetime: Lifetime,
    name: String,
    val sendInPings: List<String>
) {
    private var handle: Long

    init {
        val ffiPingsList = StringArray(sendInPings.toTypedArray(), "utf-8")
        this.handle = LibGleanFFI.INSTANCE.glean_new_boolean_metric(
                category = category,
                name = name,
                send_in_pings = ffiPingsList,
                send_in_pings_len = sendInPings.size,
                lifetime = lifetime.ordinal,
                disabled = disabled.toByte())
    }

    protected fun finalize() {
        if (this.handle != 0L) {
            val error = RustError.ByReference()
            LibGleanFFI.INSTANCE.glean_destroy_boolean_metric(this.handle, error)
        }
    }

    fun shouldRecord(): Boolean {
        // Don't record metrics if we aren't initialized
        if (!Glean.isInitialized()) {
            return false
        }

        return LibGleanFFI.INSTANCE.glean_boolean_should_record(Glean.handle, this.handle).toBoolean()
    }

    /**
     * Set a boolean value.
     *
     * @param value This is a user defined boolean value.
     */
    fun set(value: Boolean) {
        if (!shouldRecord()) {
            return
        }

        @Suppress("EXPERIMENTAL_API_USAGE")
        Dispatchers.API.launch {
            LibGleanFFI.INSTANCE.glean_boolean_set(Glean.handle, this@BooleanMetricType.handle, value.toByte())
        }
    }

    /**
     * Tests whether a value is stored for the metric for testing purposes only. This function will
     * attempt to await the last task (if any) writing to the the metric's storage engine before
     * returning a value.
     *
     * @param pingName represents the name of the ping to retrieve the metric for.  Defaults
     *                 to the either the first value in [defaultStorageDestinations] or the first
     *                 value in [sendInPings]
     * @return true if metric value exists, otherwise false
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun testHasValue(pingName: String = sendInPings.first()): Boolean {
        @Suppress("EXPERIMENTAL_API_USAGE")
        Dispatchers.API.assertInTestingMode()

        val res = LibGleanFFI.INSTANCE.glean_boolean_test_has_value(Glean.handle, this.handle, pingName)
        return res.toBoolean()
    }

    /**
     * Returns the stored value for testing purposes only. This function will attempt to await the
     * last task (if any) writing to the the metric's storage engine before returning a value.
     *
     * @param pingName represents the name of the ping to retrieve the metric for.  Defaults
     *                 to the either the first value in [defaultStorageDestinations] or the first
     *                 value in [sendInPings]
     * @return value of the stored metric
     * @throws [NullPointerException] if no value is stored
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun testGetValue(pingName: String = sendInPings.first()): Boolean {
        @Suppress("EXPERIMENTAL_API_USAGE")
        Dispatchers.API.assertInTestingMode()

        if (!testHasValue(pingName)) {
            throw NullPointerException()
        }
        return LibGleanFFI.INSTANCE.glean_boolean_test_get_value(Glean.handle, this.handle, pingName).toBoolean()
    }
}