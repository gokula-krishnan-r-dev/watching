package com.meritscreen.feature.authentication.ui

import kotlinx.serialization.Serializable

@Serializable
data object AuthenticationRoute

@Serializable
data class OtpVerificationRoute(val email: String)

@Serializable
data class ParentPairingRoute(val childId: String = "")

@Serializable
data object ChildPairingRoute

