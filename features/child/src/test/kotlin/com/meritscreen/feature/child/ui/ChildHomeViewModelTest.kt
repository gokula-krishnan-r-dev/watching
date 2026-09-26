package com.meritscreen.feature.child.ui

import android.content.Context
import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.common.domain.SessionPhase
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.security.pairing.ChildPairingCredential
import com.meritscreen.core.security.pairing.ChildPairingStore
import com.meritscreen.feature.applications.AppIconLoader
import com.meritscreen.feature.applications.InstalledAppsRepository
import com.meritscreen.feature.child.data.ChildDeviceLifecycleCoordinator
import com.meritscreen.feature.child.data.ChildLocalProfile
import com.meritscreen.feature.child.data.ChildPolicyRepository
import com.meritscreen.feature.child.data.ChildPolicySyncCoordinator
import com.meritscreen.feature.child.domain.ChildSessionController
import com.meritscreen.feature.child.domain.SessionSnapshot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChildHomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private val pairingStore: ChildPairingStore = mockk(relaxed = true)
    private val policyRepository: ChildPolicyRepository = mockk(relaxed = true)
    private val sessionController: ChildSessionController = mockk(relaxed = true)
    private val syncCoordinator: ChildPolicySyncCoordinator = mockk(relaxed = true)
    private val installedAppsRepository: InstalledAppsRepository = mockk(relaxed = true)
    private val deviceLifecycle: ChildDeviceLifecycleCoordinator = mockk(relaxed = true)
    private val stickerRepository: com.meritscreen.feature.child.data.StickerRepository = mockk(relaxed = true)
    private val iconLoader: AppIconLoader = mockk(relaxed = true)

    private val sessionFlow = MutableStateFlow(
        SessionSnapshot(
            phase = SessionPhase.Idle,
            minutesUsedToday = 30,
        ),
    )

    private val installedPackagesFlow = MutableStateFlow(
        setOf(
            "com.google.android.apps.youtube.kids",
            "com.duolingo.kids",
            "com.mojang.minecraftpe",
            "org.scratchjr.android",
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { sessionController.snapshot } returns sessionFlow
        every { deviceLifecycle.forceSignedOut } returns MutableStateFlow(false)
        every { deviceLifecycle.isDefaultHome() } returns true
        every { installedAppsRepository.observeInstalledPackageNames() } returns installedPackagesFlow
        every { installedAppsRepository.isInstalledCached(any(), any()) } answers {
            val pkg = firstArg<String>()
            val set = secondArg<Set<String>>()
            set.contains(pkg)
        }
        every { stickerRepository.observeProgress(any()) } returns flowOf(
            com.meritscreen.core.common.domain.ExplorerProgress(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(startTicker: Boolean = false): ChildHomeViewModel {
        val vm = ChildHomeViewModel(
            context = context,
            pairingStore = pairingStore,
            policyRepository = policyRepository,
            sessionController = sessionController,
            syncCoordinator = syncCoordinator,
            installedAppsRepository = installedAppsRepository,
            deviceLifecycle = deviceLifecycle,
            stickerRepository = stickerRepository,
            iconLoader = iconLoader,
        )
        if (!startTicker) {
            vm.stopPeriodicTicker()
        }
        return vm
    }

    @Test
    fun `searchQuery updates and emergency calling toggles properly`() = runTest {
        coEvery { pairingStore.get() } returns ChildPairingCredential("fam1", "child_leo", "dev1", "hash")
        val vm = createViewModel()

        assertEquals("", vm.searchQuery.value)
        vm.setSearchQuery("Minecraft")
        assertEquals("Minecraft", vm.searchQuery.value)

        assertFalse(vm.isCallingEmergency.value)
        vm.startEmergencyCall()
        assertTrue(vm.isCallingEmergency.value)
        vm.endEmergencyCall()
        assertFalse(vm.isCallingEmergency.value)
    }

    @Test
    fun `onAppTapped for uninstalled app reports error and blocks`() = runTest {
        coEvery { pairingStore.get() } returns ChildPairingCredential("fam1", "child_leo", "dev1", "hash")
        val vm = createViewModel()

        val tile = HomeAppTile(
            rule = AppRule("uninstalled_app", "com.unknown.app", "Unknown App", allowed = true),
            label = "Unknown App",
            installed = false,
            launchable = false,
        )

        val result = vm.onAppTapped(tile)
        assertEquals(AppTapResult.Blocked, result)
        assertNotNull(vm.launchError.value)
        assertTrue(vm.launchError.value!!.contains("isn’t installed"))
    }

    @Test
    fun `active block tile shows live remaining minutes`() = runTest {
        val credential = ChildPairingCredential("fam1", "child_leo", "dev1", "hash")
        coEvery { pairingStore.get() } returns credential

        val files = AppRule(
            "files",
            "com.google.android.documentsui",
            "Files",
            allowed = true,
            blockMinutes = 15,
        )
        every { policyRepository.observePolicy("child_leo") } returns flowOf(ChildPolicy(dailyCeilingMinutes = 120))
        every { policyRepository.observeAppRules("child_leo") } returns flowOf(listOf(files))
        every { policyRepository.observeProfile("child_leo") } returns flowOf(
            ChildLocalProfile("child_leo", "fam1", "Leo", "Elementary Quest", "rabbit", "en"),
        )
        installedPackagesFlow.value = setOf("com.google.android.documentsui")
        // Align session clocks with SystemClock so live remaining matches production.
        val now = android.os.SystemClock.elapsedRealtime()
        sessionFlow.value = SessionSnapshot(
            phase = SessionPhase.InBlock,
            activePackage = "com.google.android.documentsui",
            activeAppId = "files",
            blockStartedElapsedMs = now - 120_000L,
            blockDurationMinutes = 15,
            minutesAccruedInBlock = 2f,
            minutesUsedToday = 2,
            lastTickElapsedMs = now,
        )

        val vm = createViewModel()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        testScheduler.runCurrent()

        val data = (vm.uiState.value as UiState.Success).data
        assertEquals(2, data.usedTodayMinutes)
        assertEquals(118, data.dailyRemainingMinutes)
        val tile = data.apps.first()
        assertEquals("13m left", tile.badgeLabel)
        assertEquals(13, tile.remainingMinutes)
        collectJob.cancel()
    }

    @Test
    fun `app tile metadata maps known categories correctly`() = runTest {
        val credential = ChildPairingCredential("fam1", "child_leo", "dev1", "hash")
        coEvery { pairingStore.get() } returns credential

        val rules = listOf(
            AppRule("r1", "com.google.android.apps.youtube.kids", "YouTube Kids", allowed = true, blockMinutes = 25),
            AppRule("r2", "com.duolingo.kids", "Duo ABC", allowed = true),
            AppRule("r3", "com.mojang.minecraftpe", "Minecraft", allowed = true),
        )

        every { policyRepository.observePolicy("child_leo") } returns flowOf(ChildPolicy(dailyCeilingMinutes = 120))
        every { policyRepository.observeAppRules("child_leo") } returns flowOf(rules)
        every { policyRepository.observeProfile("child_leo") } returns flowOf(
            ChildLocalProfile("child_leo", "fam1", "Leo", "Elementary Quest", "rabbit", "en"),
        )

        val vm = createViewModel()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        testScheduler.runCurrent()

        val state = vm.uiState.value
        assertTrue("Expected Success but got $state", state is UiState.Success)
        val data = (state as UiState.Success).data

        assertEquals("Leo", data.greetingName)
        assertEquals(3, data.apps.size)

        val ytTile = data.apps.first { it.rule.appId == "r1" }
        assertEquals("Videos", ytTile.subtitle)
        assertEquals("25m left", ytTile.badgeLabel)
        assertEquals(TileBadgeType.TIME_ALERT, ytTile.badgeType)

        val duoTile = data.apps.first { it.rule.appId == "r2" }
        assertEquals("Reading", duoTile.subtitle)
        assertEquals("∞ Free", duoTile.badgeLabel)
        assertEquals(TileBadgeType.FREE, duoTile.badgeType)

        val mcTile = data.apps.first { it.rule.appId == "r3" }
        assertEquals("Build", mcTile.subtitle)
        assertEquals("✓ Open", mcTile.badgeLabel)
        assertEquals(TileBadgeType.OPEN, mcTile.badgeType)
        collectJob.cancel()
    }

    @Test
    fun `default block minutes do not show per-app timer badge`() = runTest {
        val credential = ChildPairingCredential("fam1", "child_leo", "dev1", "hash")
        coEvery { pairingStore.get() } returns credential

        // Same as child-wide default — global daily card covers this, not every icon.
        val clock = AppRule(
            "clock",
            "com.google.android.deskclock",
            "Clock",
            allowed = true,
            blockMinutes = 5,
        )
        val maps = AppRule(
            "maps",
            "com.google.android.apps.maps",
            "Maps",
            allowed = true,
            blockMinutes = 5,
        )
        // Parent customized this app away from the default.
        val yt = AppRule(
            "yt",
            "com.google.android.apps.youtube.kids",
            "YouTube Kids",
            allowed = true,
            blockMinutes = 25,
        )

        every { policyRepository.observePolicy("child_leo") } returns flowOf(
            ChildPolicy(dailyCeilingMinutes = 120, defaultBlockMinutes = 5),
        )
        every { policyRepository.observeAppRules("child_leo") } returns flowOf(listOf(clock, maps, yt))
        every { policyRepository.observeProfile("child_leo") } returns flowOf(
            ChildLocalProfile("child_leo", "fam1", "Leo", "Elementary Quest", "rabbit", "en"),
        )
        installedPackagesFlow.value = setOf(
            "com.google.android.deskclock",
            "com.google.android.apps.maps",
            "com.google.android.apps.youtube.kids",
        )

        val vm = createViewModel()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        testScheduler.runCurrent()

        val data = (vm.uiState.value as UiState.Success).data
        val clockTile = data.apps.first { it.rule.appId == "clock" }
        assertEquals(null, clockTile.badgeLabel)
        assertEquals(TileBadgeType.NEUTRAL, clockTile.badgeType)
        assertEquals(null, clockTile.remainingMinutes)

        val mapsTile = data.apps.first { it.rule.appId == "maps" }
        assertEquals(null, mapsTile.badgeLabel)
        assertEquals(null, mapsTile.remainingMinutes)

        val ytTile = data.apps.first { it.rule.appId == "yt" }
        assertEquals("25m left", ytTile.badgeLabel)
        assertEquals(TileBadgeType.TIME_ALERT, ytTile.badgeType)
        assertEquals(25, ytTile.remainingMinutes)
        collectJob.cancel()
    }
}
