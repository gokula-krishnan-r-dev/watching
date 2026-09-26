package com.meritscreen.feature.authentication.ui

import com.meritscreen.core.testing.FakeNetworkMonitor
import com.meritscreen.feature.authentication.domain.CompleteParentAuthUseCase
import com.meritscreen.feature.authentication.fakes.FakeAnalyticsTracker
import com.meritscreen.feature.authentication.fakes.FakeAuthClient
import com.meritscreen.feature.authentication.fakes.FakeEmailOtpClient
import com.meritscreen.feature.authentication.fakes.FakeFamilyStore
import com.meritscreen.feature.authentication.fakes.FakeOnboardingDraftRepository
import com.meritscreen.feature.authentication.fakes.FakeParentSessionRepository
import com.meritscreen.feature.authentication.google.GoogleIdTokenClient
import io.mockk.every
import io.mockk.mockk
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
class SignInViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val googleClient = mockk<GoogleIdTokenClient>(relaxed = true) {
        every { isConfigured() } returns true
    }
    private lateinit var otpClient: FakeEmailOtpClient
    private lateinit var useCase: CompleteParentAuthUseCase
    private lateinit var viewModel: SignInViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        otpClient = FakeEmailOtpClient()
        useCase = CompleteParentAuthUseCase(
            authClient = FakeAuthClient(),
            emailOtpClient = otpClient,
            familyStore = FakeFamilyStore(),
            draftRepository = FakeOnboardingDraftRepository(),
            parentSessionRepository = FakeParentSessionRepository(),
            networkMonitor = FakeNetworkMonitor(online = true),
            analyticsTracker = FakeAnalyticsTracker(),
        )
        viewModel = SignInViewModel(useCase, googleClient)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsDefault() {
        val state = viewModel.uiState.value
        assertEquals("", state.email)
        assertFalse(state.isValidEmail)
        assertTrue(state.rememberDevice)
        assertFalse(state.isSubmitting)
        assertTrue(state.googleAvailable)
        assertNull(state.error)
    }

    @Test
    fun emailChangeUpdatesValidationFlag() {
        viewModel.onEmailChanged("not-an-email")
        assertEquals("not-an-email", viewModel.uiState.value.email)
        assertFalse(viewModel.uiState.value.isValidEmail)

        viewModel.onEmailChanged("parent@example.com")
        assertEquals("parent@example.com", viewModel.uiState.value.email)
        assertTrue(viewModel.uiState.value.isValidEmail)
    }

    @Test
    fun rememberDeviceTogglesState() {
        viewModel.onRememberDeviceChanged(false)
        assertFalse(viewModel.uiState.value.rememberDevice)
        viewModel.onRememberDeviceChanged(true)
        assertTrue(viewModel.uiState.value.rememberDevice)
    }

    @Test
    fun sendOtpWithInvalidEmailSetsValidationError() = runTest {
        viewModel.onEmailChanged("invalid")
        var sentEmail: String? = null
        viewModel.sendOtp { sentEmail = it }
        advanceUntilIdle()

        assertNull(sentEmail)
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun sendOtpWithValidEmailInvokesCallback() = runTest {
        viewModel.onEmailChanged("parent@example.com")
        var sentEmail: String? = null
        viewModel.sendOtp { sentEmail = it }
        advanceUntilIdle()

        assertEquals("parent@example.com", sentEmail)
        assertEquals("parent@example.com", otpClient.lastSentEmail)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertNull(viewModel.uiState.value.error)
    }
}
