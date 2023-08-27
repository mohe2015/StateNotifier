package de.selfmade4u.statenotifier

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class RegistrationEvent {
    data class ServiceRegistered(val nsdServiceInfo: NsdServiceInfo) : RegistrationEvent()
    data class ServiceUnregistered(val nsdServiceInfo: NsdServiceInfo) : RegistrationEvent()
}

sealed class DiscoverEvent {
    data class ServiceFound(val nsdServiceInfo: NsdServiceInfo) : DiscoverEvent()
    data class ServiceLost(val nsdServiceInfo: NsdServiceInfo) : DiscoverEvent()
}


sealed class NsdManagerException(override val message: String) : Exception()

data class RegistrationFailedException(val nsdServiceInfo: NsdServiceInfo, val errorCode: Int) :
    NsdManagerException("onRegistrationFailed $nsdServiceInfo $errorCode")

data class UnregistrationFailedException(val nsdServiceInfo: NsdServiceInfo, val errorCode: Int) :
    NsdManagerException("onUnregistrationFailed $nsdServiceInfo $errorCode")

data class StartDiscoveryFailedException(val serviceType: String, val errorCode: Int) :
    NsdManagerException("onStartDiscoveryFailed $serviceType $errorCode")

data class StopDiscoveryFailedException(val serviceType: String, val errorCode: Int) :
    NsdManagerException("onStopDiscoveryFailed $serviceType $errorCode")

data class ResolveFailedException(val serviceInfo: NsdServiceInfo, val errorCode: Int) :
    NsdManagerException("onResolveFailed $serviceInfo $errorCode")

class NsdManagerFlow(val nsdManager: NsdManager) {

    fun registerService(
        serviceInfo: NsdServiceInfo,
        protocolType: Int,
    ): Flow<RegistrationEvent> = callbackFlow {
        Log.w(TAG, "registerService")
        val registrationListener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(nsdServiceInfo: NsdServiceInfo, errorCode: Int) {
                close(RegistrationFailedException(nsdServiceInfo, errorCode))
            }

            override fun onUnregistrationFailed(nsdServiceInfo: NsdServiceInfo, errorCode: Int) {
                close(UnregistrationFailedException(nsdServiceInfo, errorCode))
            }

            override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
                trySendBlocking(RegistrationEvent.ServiceRegistered(nsdServiceInfo)).onFailure { throwable ->
                    Log.e(TAG, "trySendBlocking", throwable)
                    close(throwable)
                }
            }

            override fun onServiceUnregistered(nsdServiceInfo: NsdServiceInfo) {
                trySendBlocking(RegistrationEvent.ServiceUnregistered(nsdServiceInfo)).onFailure { throwable ->
                    Log.e(TAG, "trySendBlocking", throwable)
                    close(throwable)
                }
            }

        }
        nsdManager.registerService(serviceInfo, protocolType, registrationListener)
        awaitClose { nsdManager.unregisterService(registrationListener) }
    }

    fun discoverServices(
        serviceType: String,
        protocolType: Int,
    ): Flow<DiscoverEvent> = callbackFlow {
        Log.w(TAG, "discoverServices")
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(StartDiscoveryFailedException(serviceType, errorCode))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(StopDiscoveryFailedException(serviceType, errorCode))
            }

            override fun onDiscoveryStarted(serviceType: String) {

            }

            override fun onDiscoveryStopped(serviceType: String) {

            }

            override fun onServiceFound(nsdServiceInfo: NsdServiceInfo) {
                trySendBlocking(DiscoverEvent.ServiceFound(nsdServiceInfo)).onFailure { throwable ->
                    Log.e(TAG, "trySendBlocking", throwable)
                    close(throwable)
                }
            }

            override fun onServiceLost(nsdServiceInfo: NsdServiceInfo) {
                trySendBlocking(DiscoverEvent.ServiceLost(nsdServiceInfo)).onFailure { throwable ->
                    Log.e(TAG, "trySendBlocking", throwable)
                    close(throwable)
                }
            }

        }
        nsdManager.discoverServices(serviceType, protocolType, discoveryListener)
        awaitClose { nsdManager.stopServiceDiscovery(discoveryListener) }
    }

    suspend fun resolveService(serviceInfo: NsdServiceInfo): NsdServiceInfo =
        suspendCancellableCoroutine {
            Log.w(TAG, "resolveService")
            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    it.resumeWithException(ResolveFailedException(serviceInfo, errorCode))
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    it.resume(serviceInfo)
                }

            }
            @Suppress("DEPRECATION") // we have minSdk 33
            nsdManager.resolveService(serviceInfo, resolveListener)
        }
}