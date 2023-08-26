package de.selfmade4u.statenotifier

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Composable
fun AdvertisedServiceDetails(navController: NavController, serviceId: String) {
    Text("Service details for $serviceId", fontSize = 20.sp)

}