/**
 * Copyright (C) 2016 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evervolv.platform.internal

import android.content.Context
import android.os.SystemProperties
import android.util.Slog
import com.android.server.LocalServices
import com.android.server.SystemServiceManager
import com.evervolv.platform.internal.common.VendorServiceHelper

/**
 * Base Vendor System Server which handles the starting and states of various Lineage
 * specific system services. Since its part of the main looper provided by the system
 * server, it will be available indefinitely (until all the things die).
 */
class VendorServer(private val context: Context) {
    private val helper = VendorServiceHelper(context)

    /**
     * Invoked via reflection by the SystemServer
     */
    private fun run() {
        // Start services.
        try {
            startServices()
        } catch (ex: Throwable) {
            Slog.e("System", "******************************************")
            Slog.e("System", "************ Failure starting vendor system services", ex)
            throw ex
        }
    }

    private fun startServices() {
        val ssm = LocalServices.getService(SystemServiceManager::class.java)
        val externalServices = context.resources.getStringArray(
                com.evervolv.platform.internal.R.array.config_externalVendorServices)
        for (service in externalServices) {
            try {
                Slog.i(TAG, "Attempting to start service $service")
                val vendorService = helper.getServiceFor(service)
                if (context.packageManager.hasSystemFeature(
                                vendorService.getFeatureDeclaration())) {
                    if (coreAppsOnly() && !vendorService.isCoreService) {
                        Slog.d(TAG, "Not starting " + service +
                                " - only parsing core apps")
                    } else {
                        Slog.i(TAG, "Starting service $service")
                        ssm.startService(vendorService.javaClass)
                    }
                } else {
                    Slog.i(TAG, "Not starting service " + service +
                            " due to feature not declared on device")
                }
            } catch (e: Throwable) {
                reportWtf("starting $service", e)
            }
        }
    }

    private fun reportWtf(msg: String, e: Throwable) {
        Slog.w(TAG, "***********************************************")
        Slog.wtf(TAG, "BOOT FAILURE $msg", e)
    }

    companion object {
        private const val TAG = "VendorServer"
        private const val ENCRYPTING_STATE = "trigger_restart_min_framework"
        private const val ENCRYPTED_STATE = "1"
        fun coreAppsOnly(): Boolean {
            // Only run "core" apps+services if we're encrypting the device.
            val cryptState = SystemProperties.get("vold.decrypt")
            val isAlarmBoot = SystemProperties.getBoolean("ro.alarm_boot", false) ||
                    SystemProperties.getBoolean("ro.vendor.alarm_boot", false)
            return ENCRYPTING_STATE == cryptState || ENCRYPTED_STATE == cryptState ||
                    isAlarmBoot
        }
    }
}
