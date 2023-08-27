package de.selfmade4u.statenotifier

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.net.InetAddress

@Composable
fun Discover() {
    val nsdManagerFlow = NsdManagerFlow(LocalContext.current.getSystemService(Context.NSD_SERVICE) as NsdManager)

    val result = nsdManagerFlow.discoverServices("_statenotifier._tcp", NsdManager.PROTOCOL_DNS_SD)
}