package de.selfmade4u.statenotifier

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.runningReduce
import kotlinx.coroutines.flow.scan
import java.net.InetAddress

@Composable
fun Discover() {
    val nsdManagerFlow =
        NsdManagerFlow(LocalContext.current.getSystemService(Context.NSD_SERVICE) as NsdManager)

    // TODO FIXME don't call distinctUntilChanged in a Composable context
    val result by remember {
        nsdManagerFlow.discoverServices("_statenotifier._tcp", NsdManager.PROTOCOL_DNS_SD)
            .scan(emptySet<NsdServiceInfo>()) { acc, value ->
                Log.w(TAG, "test $value $acc")
                when (value) {
                    is DiscoverEvent.ServiceFound -> acc + value.nsdServiceInfo
                    is DiscoverEvent.ServiceLost -> acc - value.nsdServiceInfo
                }
            }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(setOf())

    Column {
        result.forEach { nsdServiceInfo ->
            ListItem(
                headlineContent = { Text("${nsdServiceInfo.serviceName} ${nsdServiceInfo.serviceType}") },
                leadingContent = {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = "Localized description",
                    )
                }
            )
            HorizontalDivider()
        }
    }
}