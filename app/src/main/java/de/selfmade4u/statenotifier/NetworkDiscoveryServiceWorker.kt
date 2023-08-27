package de.selfmade4u.statenotifier

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import java.net.ServerSocket
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

class NetworkDiscoveryServiceWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        try {
            setForeground(getForegroundInfo())
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Log.e("StateNotifier", "ForegroundServiceStartNotAllowedException", e)
        }
/*
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager;
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "statenotifier:nds");
        wakeLock.acquire(10*60*1000L /*10 minutes*/);
*/
        val nsdManager = (applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager)

        // TODO FIXME unregister on cancellation

        AppDatabase.getDatabase(applicationContext).advertisedServiceDao().getAll()
            .collect { services ->
                Log.w("StateNotifier", services.toString())

                for (service in services) {
                    val serverSocket = ServerSocket(0)

                    val serviceInfo = NsdServiceInfo().apply {
                        serviceName = "StateNotifier"
                        serviceType = "_statenotifier._tcp"
                        port = serverSocket.localPort
                    }

                    val registrationListener = object : NsdManager.RegistrationListener {

                        override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
                            // Save the service name. Android may have changed it in order to
                            // resolve a conflict, so update the name you initially requested
                            // with the name Android actually used.
                            val mServiceName = nsdServiceInfo.serviceName
                            Log.w("StateNotifier", "onServiceRegistered")
                        }

                        override fun onRegistrationFailed(
                            serviceInfo: NsdServiceInfo,
                            errorCode: Int
                        ) {
                            // Registration failed! Put debugging code here to determine why.
                            Log.w("StateNotifier", "onRegistrationFailed")
                        }

                        override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                            // Service has been unregistered. This only happens when you call
                            // NsdManager.unregisterService() and pass in this listener.
                            Log.w("StateNotifier", "onServiceUnregistered")
                        }

                        override fun onUnregistrationFailed(
                            serviceInfo: NsdServiceInfo,
                            errorCode: Int
                        ) {
                            // Unregistration failed. Put debugging code here to determine why.
                            Log.w("StateNotifier", "onUnregistrationFailed")
                        }
                    }

                    nsdManager.apply {
                        registerService(
                            serviceInfo,
                            NsdManager.PROTOCOL_DNS_SD,
                            registrationListener
                        )
                    }
                }
            }

        return Result.success()
    }

    private fun createNotificationChannel(channelId: String, channelName: String): String {
        val chan = NotificationChannel(
            channelId,
            channelName, NotificationManager.IMPORTANCE_DEFAULT
        )
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val intent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)

        val channelId = createNotificationChannel("statenotifier", "StateNotifier")

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("StateNotifier")
            .setContentText("Your services are currently advertised")
            .setSmallIcon(R.drawable.baseline_hub_24)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_delete, "Cancel", intent)
            .build()

        return ForegroundInfo(101, notification)
    }

}
