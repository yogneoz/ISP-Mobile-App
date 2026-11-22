package com.example.ui.screens.payroll

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.PayrollRun
import com.example.domain.repository.PayrollRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class PayrollViewModel @Inject constructor(
  payrollRepository: PayrollRepository,
) : ViewModel() {

  val payrollRuns: StateFlow<List<PayrollRun>> =
    payrollRepository
      .observePayrollRuns()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
