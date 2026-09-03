package io.github.sulfuro25.salati

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Dashboard : NavKey
@Serializable data object Calendar : NavKey
@Serializable data object Zakat : NavKey
@Serializable data object Settings : NavKey
@Serializable data object Qibla : NavKey
