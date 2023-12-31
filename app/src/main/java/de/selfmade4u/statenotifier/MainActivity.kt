package de.selfmade4u.statenotifier

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import de.selfmade4u.statenotifier.ui.theme.StateNotiferTheme
import kotlinx.coroutines.launch

public val TAG: String = "StateNotifer"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val networkDiscovery: OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<NetworkDiscoveryServiceWorker>()
                .build()
        WorkManager
            .getInstance(this)
            .enqueueUniqueWork("nsd", ExistingWorkPolicy.KEEP, networkDiscovery)

        setContent {
            MainActivityContent()
        }
    }
}

sealed class Screen(val route: String, @StringRes val resourceId: Int) {
    object Discover : Screen("discover", R.string.discover)
    object Advertise : Screen("advertise", R.string.advertise)
}

@Preview
@Composable
fun RenderScreenPreview() {
    RenderScreen(rememberNavController(), rememberDrawerState(DrawerValue.Open), Screen.Discover)
}

@Composable
fun RenderScreen(navController: NavController, drawerState: DrawerState, screen: Screen) {
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    NavigationDrawerItem(
        label = { Text(stringResource(screen.resourceId)) },
        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
        onClick = {
            navController.navigate(screen.route) {
                // Pop up to the start destination of the graph to
                // avoid building up a large stack of destinations
                // on the back stack as users select items
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                // Avoid multiple copies of the same destination when
                // reselecting the same item
                launchSingleTop = true
                // Restore state when reselecting a previously selected item
                restoreState = true
            }
            scope.launch { drawerState.close() }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Preview
@Composable
fun MainActivityContent() {
    val navController = rememberNavController()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }
    val context = LocalContext.current
    StateNotiferTheme {
        Scaffold(
            content = { innerPadding ->
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Spacer(Modifier.height(12.dp))
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination
                            val state by AppDatabase.getDatabase(LocalContext.current)
                                .advertisedServiceDao().getAll().collectAsStateWithLifecycle(
                                    listOf()
                                )
                            RenderScreen(navController, drawerState, Screen.Discover)
                            Spacer(Modifier.size(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.size(16.dp))
                            RenderScreen(navController, drawerState, Screen.Advertise)
                            state.forEach { advertisedService ->
                                NavigationDrawerItem(
                                    label = { Text(advertisedService.name) },
                                    selected = currentDestination?.hierarchy?.any { it.route == "advertise/${advertisedService.privateKey}" } == true,
                                    onClick = {
                                        navController.navigate("advertise/${advertisedService.privateKey}") {
                                            // Pop up to the start destination of the graph to
                                            // avoid building up a large stack of destinations
                                            // on the back stack as users select items
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            // Avoid multiple copies of the same destination when
                                            // reselecting the same item
                                            launchSingleTop = true
                                            // Restore state when reselecting a previously selected item
                                            restoreState = true
                                        }
                                        scope.launch { drawerState.close() }
                                    }
                                )
                            }
                        }
                    },
                    content = {
                        Column {
                            TopAppBar(
                                title = {
                                    Text(
                                        "StateNotifier",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(
                                            imageVector = Icons.Filled.Menu,
                                            contentDescription = "Localized description"
                                        )
                                    }
                                },
                            )
                            // TODO FIXME if you grant the permission the notification doesn't appear if it is already there
                            val notificationPermissionState = if (LocalInspectionMode.current) {
                                object : PermissionState {
                                    override val permission: String
                                        get() = Manifest.permission.POST_NOTIFICATIONS
                                    override val status: PermissionStatus
                                        get() = PermissionStatus.Granted

                                    override fun launchPermissionRequest() {

                                    }
                                }
                            } else {
                                rememberPermissionState(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            }
                            if (!notificationPermissionState.status.isGranted) {
                                Column {
                                    val textToShow = if (notificationPermissionState.status.shouldShowRationale) {
                                        "Sending notifications is useful for this app to show when it advertises your state in the background."
                                    } else {
                                        // First time the user sees this feature or the user doesn't want to be asked again
                                        "Showing the advertised state requires notification permission. Enable permission in app settings if you want it."
                                    }

                                    val buttonText = "Allow sending notifications"

                                    Text(text = textToShow)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = { notificationPermissionState.launchPermissionRequest() }) {
                                        Text(buttonText)
                                    }
                                }
                            }
                            NavHost(
                                navController = navController,
                                startDestination = Screen.Discover.route,
                                Modifier.padding(innerPadding)
                            ) {
                                composable(Screen.Discover.route) {
                                    Discover()
                                }
                                composable(Screen.Advertise.route) {
                                    Advertise(navController)
                                }
                                composable("advertise/{serviceId}") { backStackEntry ->
                                    AdvertisedServiceDetails(
                                        navController,
                                        backStackEntry.arguments!!.getString("serviceId")!!
                                    )
                                }
                            }
                        }
                    }
                )
            }
        )
    }
}
