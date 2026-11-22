package com.example.ui.screens.leaves

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.LeaveBalance
import com.example.domain.model.LeaveRequest
import com.example.domain.model.SyncStatus
import com.example.domain.repository.LeaveRepository
import com.example.domain.repository.RepoResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LeavesViewModel @Inject constructor(
  private val leaveRepository: LeaveRepository,
) : ViewModel() {

  val balances: StateFlow<List<LeaveBalance>> =
    leaveRepository
      .observeLeaveBalances()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val myRequests: StateFlow<List<LeaveRequest>> =
    leaveRepository
      .observeMyLeaveRequests()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val teamRequests: StateFlow<List<LeaveRequest>> =
    leaveRepository
      .observeTeamPendingRequests()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private val _feedbackMessage = MutableStateFlow<String?>(null)
  val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

  fun submitLeave(
    leaveTypeId: String,
    startDate: String,
    endDate: String,
    totalDays: Double,
    reason: String,
    onSuccess: () -> Unit,
  ) {
    viewModelScope.launch {
      when (
        val res =
          leaveRepository.submitLeaveRequest(leaveTypeId, startDate, endDate, totalDays, reason)
      ) {
        is RepoResult.Success -> {
          _feedbackMessage.value =
            if (res.data.syncStatus == SyncStatus.PENDING_SYNC) {
              "Leave request (${res.data.leaveTypeName}) saved locally & queued for WorkManager sync."
            } else {
              "Leave request (${res.data.leaveTypeName}) submitted & synced."
            }
          onSuccess()
        }
        is RepoResult.Error -> {
          _feedbackMessage.value = res.message
        }
      }
    }
  }

  fun cancelRequest(requestId: String) {
    viewModelScope.launch {
      when (val res = leaveRepository.cancelLeaveRequest(requestId)) {
        is RepoResult.Success -> _feedbackMessage.value = "Leave request cancelled."
        is RepoResult.Error -> _feedbackMessage.value = res.message
      }
    }
  }

  fun reviewTeamRequest(requestId: String, approve: Boolean, note: String) {
    viewModelScope.launch {
      when (val res = leaveRepository.reviewLeaveRequest(requestId, approve, note)) {
        is RepoResult.Success ->
          _feedbackMessage.value =
            if (approve) "Leave request approved." else "Leave request declined."
        is RepoResult.Error -> _feedbackMessage.value = res.message
      }
    }
  }

  fun clearFeedback() {
    _feedbackMessage.value = null
  }
}
