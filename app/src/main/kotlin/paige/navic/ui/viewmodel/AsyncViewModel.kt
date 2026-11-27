/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import paige.navic.domain.manager.ui.SnackBarManager
import paige.navic.ui.core.UiState

abstract class AsyncViewModel<T>(
	initialState: UiState<T> = UiState.Loading(),
	initialValue: T? = null,
	private val snackBarManager: SnackBarManager
) : ViewModel() {
	private val _uiState = MutableStateFlow<UiState<T>>(initialState)
	val uiState: StateFlow<UiState<T>> = _uiState.asStateFlow()
	val data
		get() = uiState.value.data

	init {
	    if (initialValue != null) {
			_uiState.value = UiState.Loading(initialValue)
		}
	}

	protected fun execute(block: suspend CoroutineScope.(T?) -> T) {
		viewModelScope.launch {
			setLoading()

			runCatching {
				block.invoke(this, _uiState.value.data)
			}.onSuccess { result ->
				_uiState.value = UiState.Success(result)
			}.onFailure { error ->
				handleError(error)
			}
		}
	}

	protected fun launch(block: suspend CoroutineScope.() -> Unit) {
		viewModelScope.launch {
			runCatching {
				block()
			}.onFailure { error ->
				handleError(error)
			}
		}
	}

	private fun handleError(error: Throwable) {
		val exception = error as? Exception ?: Exception(error)
		snackBarManager.notifyError(exception)
		updateState { UiState.Error(exception, it.data) }
	}

	protected fun updateState(transform: (UiState<T>) -> UiState<T>) {
		_uiState.update(transform)
	}

	protected fun setLoading() {
		updateState { UiState.Loading(it.data) }
	}

	protected fun setSuccess(data: T) {
		_uiState.value = UiState.Success(data)
	}

	protected fun updateData(transform: (T) -> T) {
		updateState { state ->
			state.data?.let { UiState.Success(transform(it)) } ?: state
		}
	}
}
