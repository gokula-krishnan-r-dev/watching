package com.meritscreen.feature.authentication.ui

import com.meritscreen.core.testing.FakeNetworkMonitor
import com.meritscreen.feature.authentication.domain.CompleteParentAuthUseCase
import com.meritscreen.feature.authentication.domain.ParentAuthResult
import com.meritscreen.feature.authentication.fakes.FakeAnalyticsTracker
import com.meritscreen.feature.authentication.fakes.FakeAuthClient
import com.meritscreen.feature.authentication.fakes.FakeEmailOtpClient
import com.meritscreen.feature.authentication.fakes.FakeFamilyStore
import com.meritscreen.feature.authentication.fakes.FakeOnboardingDraftRepository
import com.meritscreen.feature.authentication.fakes.FakeParentSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OtpVerificationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var otpClient: FakeEmailOtpClient
    private lateinit var useCase: CompleteParentAuthUseCase
    private lateinit var viewModel: OtpVerificationViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        otpClient = FakeEmailOtpClient()
        useCase = CompleteParentAuthUseCase(
            authClient = FakeAuthClient(),
            emailOtpClient = otpClient,
            familyStore = FakeFamilyStore(familyIdForUser = "family-1"),
            draftRepository = FakeOnboardingDraftRepository(),
            parentSessionRepository = FakeParentSessionRepository(),
            networkMonitor = FakeNetworkMonitor(online = true),
            analyticsTracker = FakeAnalyticsTracker(),
        )
        viewModel = OtpVerificationViewModel(useCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initEmailConfiguresState() {
        viewModel.initEmail("parent@example.com")
        val state = viewModel.uiState.value
        assertEquals("parent@example.com", state.email)
        assertEquals("", state.otp)
        assertFalse(state.canResend)
    }

    @Test
    fun digitEntryAndBackspaceWorkCorrectly() {
        viewModel.initEmail("parent@example.com")
        viewModel.onDigitEntered('1')
        viewModel.onDigitEntered('2')
        viewModel.onDigitEntered('3')
        assertEquals("123", viewModel.uiState.value.otp)

        viewModel.onBackspace()
        assertEquals("12", viewModel.uiState.value.otp)
    }

    @Test
    fun reaching6DigitsTriggersVerification() = runTest {
        viewModel.initEmail("parent@example.com")
        var result: ParentAuthResult? = null

        "12345".forEach { viewModel.onDigitEntered(it) }
        assertEquals("12345", viewModel.uiState.value.otp)
        assertNull(result)

        viewModel.onDigitEntered('6') { result = it }
        advanceUntilIdle()

        assertNotNull(result)
        assertEquals("family-1", result?.familyId)
        assertTrue(viewModel.uiState.value.isSuccess)
    }

    @Test
    fun verificationFailureSetsError() = runTest {
        otpClient.shouldFailVerify = true
        viewModel.initEmail("parent@example.com")
        var result: ParentAuthResult? = null

        "123456".forEach { digit ->
            viewModel.onDigitEntered(digit) { result = it }
        }
        advanceUntilIdle()

        assertNull(result)
        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }
}
