package com.unilever.mixprocessscanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.unilever.mixprocessscanner.core.AppRoutes
import com.unilever.mixprocessscanner.ui.screens.ContainerEditScreen
import com.unilever.mixprocessscanner.ui.screens.ContainerInfoScreen
import com.unilever.mixprocessscanner.ui.screens.ContainerScreen
import com.unilever.mixprocessscanner.ui.screens.DeviceInfoScreen
import com.unilever.mixprocessscanner.ui.screens.LoginLogoutScreen
import com.unilever.mixprocessscanner.ui.screens.LoginScreen
import com.unilever.mixprocessscanner.ui.screens.MainMenuScreen
import com.unilever.mixprocessscanner.ui.screens.PickOrderScreen
import com.unilever.mixprocessscanner.ui.screens.PickingScreen
import com.unilever.mixprocessscanner.ui.screens.RefillingScreen
import com.unilever.mixprocessscanner.ui.screens.ScanningScreen
import com.unilever.mixprocessscanner.ui.screens.ViewLogScreen
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val pickOrderRoutePattern = "${AppRoutes.PICK_ORDER}?order={order}"

    NavHost(
        navController = navController,
        startDestination = AppRoutes.MAIN_MENU,
        modifier = modifier
    ) {
        composable(AppRoutes.MAIN_MENU) { MainMenuScreen(navController) }
        composable(AppRoutes.LOGIN_LOGOUT) { LoginLogoutScreen(navController) }
        composable(AppRoutes.LOGIN) { LoginScreen(navController) }
        composable(AppRoutes.DEVICE_INFO) { DeviceInfoScreen(navController) }
        composable(AppRoutes.CONTAINER) { ContainerScreen(navController) }
        composable(AppRoutes.CONTAINER_INFO) { ContainerInfoScreen(navController) }
        composable(AppRoutes.CONTAINER_EDIT) { ContainerEditScreen(navController) }
        composable(AppRoutes.PICKING) { PickingScreen(navController) }

        composable(
            route = pickOrderRoutePattern,
            arguments = listOf(
                navArgument("order") {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = false
                }
            )
        ) { backStackEntry ->
            val encodedOrder = backStackEntry.arguments?.getString("order").orEmpty()
            val decodedOrder = runCatching {
                URLDecoder.decode(encodedOrder, StandardCharsets.UTF_8.toString())
            }.getOrElse { encodedOrder }

            PickOrderScreen(navController, selectedOrder = decodedOrder)
        }

        composable(AppRoutes.REFILLING) { RefillingScreen(navController) }
        composable(AppRoutes.SCANNING) { ScanningScreen(navController) }
        composable(AppRoutes.VIEW_LOG) { ViewLogScreen(navController) }
    }
}