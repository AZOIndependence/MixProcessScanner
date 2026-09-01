package com.unilever.mixprocessscanner.core

object AppRoutes {
    const val MAIN_MENU = "main_menu"
    const val LOGIN_LOGOUT = "login_logout"
    const val LOGIN = "login"
    const val DEVICE_INFO = "device_info"
    const val CONTAINER = "container"
    const val CONTAINER_INFO = "container_info"
    const val CONTAINER_EDIT = "container_edit"
    const val PICKING = "picking"
    const val PICK_ORDER = "pick_order"
    const val REFILLING = "refilling"
    const val SCANNING = "scanning"
    const val VIEW_LOG = "view_log"

    // Pick Order argument helpers
    const val ARG_ORDER = "order"
    const val PICK_ORDER_ROUTE_PATTERN = "$PICK_ORDER?$ARG_ORDER={$ARG_ORDER}"

    fun pickOrderRoute(orderEncoded: String): String = "$PICK_ORDER?$ARG_ORDER=$orderEncoded"
}