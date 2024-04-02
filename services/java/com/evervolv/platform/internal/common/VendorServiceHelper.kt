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
package com.evervolv.platform.internal.common

import android.content.Context
import com.evervolv.platform.internal.VendorService
import java.lang.reflect.InvocationTargetException

/**
 * Helper methods for fetching a VendorService from a class declaration
 */
class VendorServiceHelper(private val context: Context) {
    fun getServiceFor(className: String): VendorService {
        @Suppress("UNCHECKED_CAST") val serviceClass: Class<VendorService> = try {
            Class.forName(className) as Class<VendorService>
        } catch (ex: ClassNotFoundException) {
            throw RuntimeException("Failed to create service " + className
                    + ": service class not found", ex)
        }
        return getServiceFromClass(serviceClass)
    }

    private fun <T : VendorService?> getServiceFromClass(serviceClass: Class<T>): T {
        val service: T = try {
            val constructor = serviceClass.getConstructor(Context::class.java)
            constructor.newInstance(context)
        } catch (ex: InstantiationException) {
            throw RuntimeException("Failed to create service " + serviceClass
                    + ": service could not be instantiated", ex)
        } catch (ex: IllegalAccessException) {
            throw RuntimeException("Failed to create service " + serviceClass
                    + ": service must have a public constructor with a Context argument", ex)
        } catch (ex: NoSuchMethodException) {
            throw RuntimeException("Failed to create service " + serviceClass
                    + ": service must have a public constructor with a Context argument", ex)
        } catch (ex: InvocationTargetException) {
            throw RuntimeException("Failed to create service " + serviceClass
                    + ": service constructor threw an exception", ex)
        }
        return service
    }
}
