package com.example

import com.example.domain.model.LeaveRequest
import com.example.domain.model.LeaveStatus
import com.example.ui.screens.leaves.LeaveCalendarBuilder
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaveCalendarBuilderTest {

  private fun request(
    id: String,
    start: String,
    end: String,
    status: LeaveStatus,
  ) =
    LeaveRequest(
      id = id,
      employeeId = "emp_103",
      employeeName = "Maya Shrestha",
      leaveTypeId = "LT_ANNUAL",
      leaveTypeName = "Annual Earned Leave",
      startDate = start,
      endDate = end,
      totalDays = 1.0,
      reason = "Scheduled leave",
      status = status,
      submittedAt = "2026-11-01",
    )

  @Test
  fun `month grid includes approved and pending ranges and excludes rejected or cancelled`() {
    val requests =
      listOf(
        request("lv_app", "2026-11-05", "2026-11-05", LeaveStatus.APPROVED),
        request("lv_pend", "2026-11-26", "2026-11-27", LeaveStatus.PENDING),
        request("lv_rej", "2026-11-12", "2026-11-13", LeaveStatus.REJECTED),
        request("lv_can", "2026-11-18", "2026-11-18", LeaveStatus.CANCELLED),
      )

    val grid =
      LeaveCalendarBuilder.buildMonthGrid(
        year = 2026,
        monthZeroBased = Calendar.NOVEMBER,
        requests = requests,
        todayIso = "2026-11-14",
      )

    assertEquals("November 2026", grid.monthTitle)
    assertEquals(1, grid.approvedDayCount)
    assertEquals(2, grid.pendingDayCount)

    val days = grid.cells.filterNotNull().associateBy { it.dateIso }
    assertTrue(days.getValue("2026-11-05").hasApproved)
    assertFalse(days.getValue("2026-11-05").hasPending)

    assertTrue(days.getValue("2026-11-26").hasPending)
    assertTrue(days.getValue("2026-11-27").hasPending)

    assertFalse(days.getValue("2026-11-12").hasApproved)
    assertFalse(days.getValue("2026-11-12").hasPending)
    assertFalse(days.getValue("2026-11-18").hasPending)

    assertTrue(days.getValue("2026-11-14").isToday)
    assertEquals(0, grid.cells.size % 7)
  }
}
