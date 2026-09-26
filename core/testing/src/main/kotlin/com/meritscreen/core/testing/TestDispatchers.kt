package com.meritscreen.core.testing

import com.meritscreen.core.common.dispatchers.AppDispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

fun testAppDispatchers(dispatcher: TestDispatcher = StandardTestDispatcher()): AppDispatchers =
    AppDispatchers(io = dispatcher, default = dispatcher, main = dispatcher)
