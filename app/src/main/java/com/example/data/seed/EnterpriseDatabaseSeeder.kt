package com.example.data.seed

import com.example.data.local.AnnouncementEntity
import com.example.data.local.ApprovalItemEntity
import com.example.data.local.AttendanceEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.BranchEntity
import com.example.data.local.DelegationEntity
import com.example.data.local.EmployeeDocumentEntity
import com.example.data.local.EmployeeEntity
import com.example.data.local.EmployeeShiftEntity
import com.example.data.local.ExpenseClaimEntity
import com.example.data.local.LeaveBalanceEntity
import com.example.data.local.LeaveRequestEntity
import com.example.data.local.LocationBreadcrumbEntity
import com.example.data.local.NeedsAttentionEntity
import com.example.data.local.PayrollRunEntity
import com.example.data.local.PayrollRunLineEntity
import com.example.data.local.PulseHrDao
import com.example.data.local.RosterEntryEntity
import com.example.data.local.VanStockEntity
import com.example.data.local.WorkOrderEntity
import com.example.data.remote.EmployeeShiftDto
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnterpriseDatabaseSeeder @Inject constructor(
  private val dao: PulseHrDao,
) {
  suspend fun seedForSession(
    session: UserSession,
    bootstrapShifts: List<EmployeeShiftDto> = emptyList(),
  ) {
    val branches =
      listOf(
        BranchEntity(
          id = "br_hq",
          name = "HQ Campus — Mountain View",
          code = "MV-HQ",
          address = "1600 Amphitheatre Pkwy, Bldg 4",
          latitude = 37.4219983,
          longitude = -122.0840000,
          geofenceRadiusMeters = 250.0,
        ),
        BranchEntity(
          id = "br_sf",
          name = "Financial District Hub — SF",
          code = "SF-FIN",
          address = "345 California St, Suite 1800",
          latitude = 37.7929,
          longitude = -122.4005,
          geofenceRadiusMeters = 200.0,
        ),
        BranchEntity(
          id = "br_ktm",
          name = "Lalitpur NOC & Fiber Operations",
          code = "KTM-NOC",
          address = "Jawalakhel Chowk, Lalitpur 44700",
          latitude = 27.6710,
          longitude = 85.3168,
          geofenceRadiusMeters = 180.0,
        ),
      )
    dao.insertBranches(branches)

    val existingEmp = dao.getEmployeeByUserId(session.userId)
    val defaultName =
      when (session.role) {
        UserRole.HR_MANAGER -> "Aarav Adhikari"
        UserRole.HR_OFFICER -> "Sita Gurung"
        UserRole.HR_SELF_SERVICE -> "Maya Shrestha"
      }
    if (existingEmp == null) {
      val (name, code, dept, title) =
        when (session.role) {
          UserRole.HR_MANAGER ->
            listOf("Aarav Adhikari", "EMP-101", "People & Operations", "Director of HR & Field Ops")
          UserRole.HR_OFFICER ->
            listOf("Sita Gurung", "EMP-102", "Fiber Dispatch & HR", "Field Lead & Coordinator")
          UserRole.HR_SELF_SERVICE ->
            listOf("Maya Shrestha", "EMP-103", "FTTH Field Engineering", "Senior Fiber Technician")
        }

      dao.upsertEmployee(
        EmployeeEntity(
          id = session.employeeId,
          userId = session.userId,
          employeeCode = code,
          fullName = name,
          email = session.email,
          phone = "+1 (650) 555-0148",
          emergencyContactName = "Rohan Shrestha",
          emergencyContactPhone = "+1 (650) 555-0192",
          department = dept,
          designation = title,
          branchId = "br_hq",
          bankName = "Enterprise Commercial Bank",
          bankAccountMasked = "•••• •••• 4829",
          joinDate = "2022-03-15",
          avatarUri = null,
        )
      )
    }

    // Seed attendance history for November 2026
    val attendanceSeed =
      listOf(
        AttendanceEntity("att_1101", session.employeeId, "br_hq", "2026-11-01", null, null, "WEEKEND", "MOBILE", syncStatus = "SYNCED"),
        AttendanceEntity("att_1102", session.employeeId, "br_hq", "2026-11-02", "09:02 AM", "06:18 PM", "PRESENT", "MOBILE", 37.4220, -122.0840, 37.4221, -122.0841, 14.0, 0.3, "SYNCED", 0, "VERIFIED", "Location & BSSID verified", 12f, "84:1B:5E:22:10:A1"),
        AttendanceEntity("att_1103", session.employeeId, "br_hq", "2026-11-03", "08:57 AM", "06:45 PM", "PRESENT", "MOBILE", 37.4219, -122.0839, 37.4220, -122.0840, 21.0, 0.8, "SYNCED", 5, "VERIFIED", "Verified punch proof", 15f, "84:1B:5E:22:10:A1"),
        AttendanceEntity("att_1104", session.employeeId, "br_hq", "2026-11-04", "09:11 AM", "02:05 PM", "HALF_DAY", "MOBILE", 37.4221, -122.0842, 37.4220, -122.0840, 33.0, 0.0, "SYNCED", 35, "FLAGGED", "GPS error 68m exceeds 50m threshold", 68f, null),
        AttendanceEntity("att_1105", session.employeeId, "br_hq", "2026-11-05", null, null, "LEAVE", "MOBILE", syncStatus = "SYNCED"),
        AttendanceEntity("att_1106", session.employeeId, "br_hq", "2026-11-06", "09:04 AM", "06:10 PM", "PRESENT", "MOBILE", 37.4220, -122.0840, 37.4220, -122.0840, 11.0, 0.2, "SYNCED", 0, "VERIFIED", "Verified punch proof", 10f, "84:1B:5E:22:10:A1"),
        AttendanceEntity("att_1107", session.employeeId, "br_hq", "2026-11-07", null, null, "WEEKEND", "MOBILE", syncStatus = "SYNCED"),
        AttendanceEntity("att_1108", session.employeeId, "br_hq", "2026-11-08", null, null, "WEEKEND", "MOBILE", syncStatus = "SYNCED"),
        AttendanceEntity("att_1109", session.employeeId, "br_hq", "2026-11-09", "08:55 AM", null, "PRESENT", "MOBILE", 37.4220, -122.0841, null, null, 18.0, 1.1, "SYNCED", 45, "INCOMPLETE", "Missed punch-out closed at shift end; queued for supervisor review", 18f, "84:1B:5E:22:10:A1"),
        AttendanceEntity("att_1110", session.employeeId, "br_hq", "2026-11-10", null, null, "HOLIDAY", "MOBILE", syncStatus = "SYNCED"),
        AttendanceEntity("att_1111", session.employeeId, "br_hq", "2026-11-11", "09:00 AM", "06:12 PM", "PRESENT", "MOBILE", 37.4220, -122.0840, 37.4220, -122.0841, 9.0, 0.2, "SYNCED", 0, "VERIFIED", "Verified punch proof", 11f, "84:1B:5E:22:10:A1"),
        AttendanceEntity("att_1112", session.employeeId, "br_hq", "2026-11-12", null, null, "ABSENT", "MOBILE", syncStatus = "SYNCED"),
        AttendanceEntity("att_1113", session.employeeId, "br_hq", "2026-11-13", "09:03 AM", "06:34 PM", "PRESENT", "MOBILE", 37.4221, -122.0840, 37.4220, -122.0840, 15.0, 0.6, "SYNCED", 0, "VERIFIED", "Verified punch proof", 14f, "84:1B:5E:22:10:A1"),
      )
    dao.insertAttendance(attendanceSeed)

    // Leave Balances
    dao.insertLeaveBalances(
      listOf(
        LeaveBalanceEntity("lb_al_${session.employeeId}", session.employeeId, "LT_ANNUAL", "Annual Earned Leave", "AL", 18.0, 5.0, 2.0, 11.0),
        LeaveBalanceEntity("lb_sl_${session.employeeId}", session.employeeId, "LT_SICK", "Medical & Sick Leave", "SL", 12.0, 2.0, 0.0, 10.0),
        LeaveBalanceEntity("lb_cl_${session.employeeId}", session.employeeId, "LT_CASUAL", "Casual Personal Leave", "CL", 6.0, 1.0, 0.0, 5.0),
        LeaveBalanceEntity("lb_fl_${session.employeeId}", session.employeeId, "LT_FESTIVAL", "Floating Holiday", "FH", 4.0, 1.0, 0.0, 3.0),
      )
    )

    // Leave Requests
    dao.insertLeaveRequests(
      listOf(
        LeaveRequestEntity(
          id = "lv_own_1",
          employeeId = session.employeeId,
          employeeName = "Self",
          leaveTypeId = "LT_ANNUAL",
          leaveTypeName = "Annual Earned Leave",
          startDate = "2026-11-05",
          endDate = "2026-11-05",
          totalDays = 1.0,
          reason = "Family medical appointment in Palo Alto",
          status = "APPROVED",
          submittedAt = "2026-10-29",
          reviewerNote = "Approved by Regional Lead",
          isTeamRequest = false,
        ),
        LeaveRequestEntity(
          id = "lv_own_2",
          employeeId = session.employeeId,
          employeeName = "Self",
          leaveTypeId = "LT_ANNUAL",
          leaveTypeName = "Annual Earned Leave",
          startDate = "2026-11-26",
          endDate = "2026-11-27",
          totalDays = 2.0,
          reason = "Thanksgiving travel with family",
          status = "PENDING",
          submittedAt = "2026-11-10",
          reviewerNote = null,
          isTeamRequest = false,
        ),
        LeaveRequestEntity(
          id = "lv_team_1",
          employeeId = "emp_204",
          employeeName = "Bikash Tamang (NOC Engineer)",
          leaveTypeId = "LT_SICK",
          leaveTypeName = "Medical & Sick Leave",
          startDate = "2026-11-16",
          endDate = "2026-11-17",
          totalDays = 2.0,
          reason = "Seasonal viral fever recovery",
          status = "PENDING",
          submittedAt = "2026-11-13",
          reviewerNote = null,
          isTeamRequest = true,
        ),
        LeaveRequestEntity(
          id = "lv_team_2",
          employeeId = "emp_209",
          employeeName = "Priya Karki (Billing Ops)",
          leaveTypeId = "LT_CASUAL",
          leaveTypeName = "Casual Personal Leave",
          startDate = "2026-11-18",
          endDate = "2026-11-18",
          totalDays = 1.0,
          reason = "Passport renewal biometrics slot",
          status = "PENDING",
          submittedAt = "2026-11-13",
          reviewerNote = null,
          isTeamRequest = true,
        ),
      )
    )

    // Payroll Runs & Run Lines
    val runOct =
      PayrollRunEntity(
        id = "pr_2026_10",
        employeeId = session.employeeId,
        monthLabel = "October 2026",
        periodStart = "2026-10-01",
        periodEnd = "2026-10-31",
        payDate = "2026-11-01",
        status = "FINALIZED",
        currency = "USD",
        grossPay = 6850.0,
        totalDeductions = 1624.50,
        ssfTotal = 753.50,
        tdsTax = 871.00,
        netPay = 5225.50,
      )
    val runSep =
      PayrollRunEntity(
        id = "pr_2026_09",
        employeeId = session.employeeId,
        monthLabel = "September 2026",
        periodStart = "2026-09-01",
        periodEnd = "2026-09-30",
        payDate = "2026-10-01",
        status = "FINALIZED",
        currency = "USD",
        grossPay = 6650.0,
        totalDeductions = 1561.50,
        ssfTotal = 731.50,
        tdsTax = 830.00,
        netPay = 5088.50,
      )
    dao.insertPayrollRuns(listOf(runOct, runSep))

    dao.insertPayrollRunLines(
      listOf(
        PayrollRunLineEntity("prl_1", "pr_2026_10", session.employeeId, "Basic Salary", "EARNING", 4800.0, "60% core grade base salary"),
        PayrollRunLineEntity("prl_2", "pr_2026_10", session.employeeId, "House Rent & Transit Allowance", "EARNING", 1250.0, "Branch urban housing & commute"),
        PayrollRunLineEntity("prl_3", "pr_2026_10", session.employeeId, "On-Call Fiber NOC Allowance", "EARNING", 600.0, "24/7 enterprise SLA standby"),
        PayrollRunLineEntity("prl_4", "pr_2026_10", session.employeeId, "Overtime Payout (3.2 hrs)", "EARNING", 200.0, "1.5x verified biometric punches"),
        PayrollRunLineEntity("prl_5", "pr_2026_10", session.employeeId, "SSF Employee Contribution (11%)", "SSF", 528.0, "Statutory Social Security Fund (employee)"),
        PayrollRunLineEntity("prl_6", "pr_2026_10", session.employeeId, "SSF Retirement Reserve", "SSF", 225.50, "Gratuity & medical protection tier"),
        PayrollRunLineEntity("prl_7", "pr_2026_10", session.employeeId, "TDS Slab 1 (1% Social Security)", "TDS", 68.50, "First progressive bracket"),
        PayrollRunLineEntity("prl_8", "pr_2026_10", session.employeeId, "TDS Slab 2-3 (10% / 20% Marginal)", "TDS", 802.50, "Withholding tax after exemptions"),
        PayrollRunLineEntity("prl_9", "pr_2026_09", session.employeeId, "Basic Salary", "EARNING", 4800.0, "Core grade base salary"),
        PayrollRunLineEntity("prl_10", "pr_2026_09", session.employeeId, "House Rent & Transit Allowance", "EARNING", 1250.0, "Branch urban housing & commute"),
        PayrollRunLineEntity("prl_11", "pr_2026_09", session.employeeId, "On-Call Fiber NOC Allowance", "EARNING", 600.0, "24/7 enterprise SLA standby"),
        PayrollRunLineEntity("prl_12", "pr_2026_09", session.employeeId, "SSF Employee Contribution (11%)", "SSF", 528.0, "Statutory Social Security Fund"),
        PayrollRunLineEntity("prl_13", "pr_2026_09", session.employeeId, "SSF Retirement Reserve", "SSF", 203.50, "Gratuity & medical tier"),
        PayrollRunLineEntity("prl_14", "pr_2026_09", session.employeeId, "TDS Income Tax Withholding", "TDS", 830.0, "Monthly progressive TDS slab"),
      )
    )

    // Documents
    dao.insertDocuments(
      listOf(
        EmployeeDocumentEntity(
          id = "doc_1",
          employeeId = session.employeeId,
          title = "Signed Employment Agreement 2026",
          documentType = "CONTRACT",
          fileName = "employment_contract_2026.pdf",
          mimeType = "application/pdf",
          fileSizeBytes = 1_420_800L,
          uploadedAt = "2026-01-12",
          localUri = "",
          verificationStatus = "VERIFIED",
          extractedText =
            "PULSEHR ENTERPRISE EMPLOYMENT AGREEMENT\nEmployee ID: ${session.employeeId}\nEffective Date: 2026-01-12\nBranch Scope: ${session.branchScope}\nConfidentiality & Compliance Terms Accepted.",
          detectedVendorOrIssuer = "PulseHR People Operations",
          detectedDate = "2026-01-12",
          detectedReferenceId = "CTR-2026-103",
          ocrConfidenceLabel = "Verified • Auto-Indexed",
        ),
        EmployeeDocumentEntity(
          id = "doc_2",
          employeeId = session.employeeId,
          title = "Tax & PAN Registration Card",
          documentType = "TAX_PAN",
          fileName = "tax_pan_certificate.jpg",
          mimeType = "image/jpeg",
          fileSizeBytes = 842_110L,
          uploadedAt = "2026-03-04",
          localUri = "",
          verificationStatus = "VERIFIED",
          extractedText =
            "INLAND REVENUE DEPARTMENT\nPERMANENT ACCOUNT NUMBER (PAN)\nPAN No: 604-882-194\nIssued Date: 2026-03-04\nStatus: Active Taxpayer",
          detectedVendorOrIssuer = "Inland Revenue Department",
          detectedDate = "2026-03-04",
          detectedReferenceId = "PAN-604882194",
          ocrConfidenceLabel = "OCR Verified • 96% match",
        ),
      )
    )

    // Company Announcements
    dao.insertAnnouncements(
      listOf(
        AnnouncementEntity(
          id = "ann_1",
          title = "MyWorkplace v2 Offline-First Sync & Integrity Policy",
          summary = "All field job closures and attendance punches queue locally with idempotency keys. Suspicious GPS/clock punches are accepted and flagged for review — never silently dropped.",
          category = "POLICY",
          publishedAt = "Nov 20, 2026",
          isUrgent = true,
        ),
        AnnouncementEntity(
          id = "ann_2",
          title = "ODB-14 Fiber Cut Restoration • SMS Fallback Active",
          summary = "Backbone splice team on Castro St sector; dispatch coordinators can override half-day leave with logged reason.",
          category = "OUTAGE",
          publishedAt = "Nov 20, 2026",
          isUrgent = true,
        ),
        AnnouncementEntity(
          id = "ann_3",
          title = "FY 2026-27 SSF & Tax Declaration Window Open",
          summary = "Upload updated tax exemption proofs (max 5 MB PDF/JPEG/PNG). Payslips stream transiently with biometric gate.",
          category = "PAYROLL",
          publishedAt = "Nov 09, 2026",
          isUrgent = false,
        ),
      )
    )

    // V2 Work Orders
    dao.insertWorkOrders(
      listOf(
        WorkOrderEntity(
          id = "wo_901",
          ticketCode = "FTTH-4821",
          customerName = "Everest Espresso Roasters",
          address = "412 Castro St, Mountain View • FDB-09",
          taskType = "FTTH New Install (1 Gbps ONT)",
          slaDeadlineLabel = "Today 16:30",
          slaMinutesRemaining = 95,
          status = "ASSIGNED",
          assignedEmployeeId = session.employeeId,
          assignedEmployeeName = existingEmp?.fullName ?: defaultName,
          branchCode = "MV-HQ",
          opticalPowerDbm = null,
          otdrLossDb = null,
          customerSignatureName = null,
          photoCount = 0,
          consumedCableMeters = 0,
          serialUsed = null,
          poleRouteNote = "Pole P-118 → FDB-09 drop span 45m",
          baseVersion = 3,
          syncStatus = "SYNCED",
        ),
        WorkOrderEntity(
          id = "wo_902",
          ticketCode = "LOS-4809",
          customerName = "Bayview Clinic Suite 3B",
          address = "890 El Camino Real • ODB-14",
          taskType = "Fiber LOS Repair & Splice",
          slaDeadlineLabel = "Today 14:45",
          slaMinutesRemaining = 38,
          status = "IN_PROGRESS",
          assignedEmployeeId = session.employeeId,
          assignedEmployeeName = existingEmp?.fullName ?: defaultName,
          branchCode = "MV-HQ",
          opticalPowerDbm = -27.4,
          otdrLossDb = 2.9,
          customerSignatureName = null,
          photoCount = 1,
          consumedCableMeters = 15,
          serialUsed = null,
          poleRouteNote = "Re-terminated SC/APC pigtail at ODB-14",
          baseVersion = 2,
          syncStatus = "SYNCED",
        ),
        WorkOrderEntity(
          id = "wo_903",
          ticketCode = "UPG-4794",
          customerName = "Neha Kapoor Residence",
          address = "218 Shoreline Blvd • FDB-04",
          taskType = "ONT Upgrade (Wi-Fi 6 Dual-Band)",
          slaDeadlineLabel = "Tomorrow 11:00",
          slaMinutesRemaining = 920,
          status = "ASSIGNED",
          assignedEmployeeId = session.employeeId,
          assignedEmployeeName = existingEmp?.fullName ?: defaultName,
          branchCode = "MV-HQ",
          poleRouteNote = "Check optical margin <= -22 dBm",
          baseVersion = 1,
          syncStatus = "SYNCED",
        ),
      )
    )

    // V2 Van Stock
    dao.insertVanStock(
      listOf(
        VanStockEntity("vs_1", "ONT-HX6-01", "Dual-Band Wi-Fi 6 ONT", "CPE Serial", "ONT-2026-8841", 1, "unit", 1, "VAN-MV-07"),
        VanStockEntity("vs_2", "ONT-HX6-02", "Dual-Band Wi-Fi 6 ONT", "CPE Serial", "ONT-2026-8842", 1, "unit", 1, "VAN-MV-07"),
        VanStockEntity("vs_3", "CBL-2F-DROP", "2-Core FTTH Drop Cable", "Consumable", null, 340, "meters", 100, "VAN-MV-07"),
        VanStockEntity("vs_4", "APC-FAST-01", "SC/APC Fast Connector", "Consumable", null, 18, "pcs", 10, "VAN-MV-07"),
        VanStockEntity("vs_5", "SPL-1X8-PLC", "1x8 PLC Optical Splitter", "Passive", "SPL-7740-19", 2, "unit", 1, "VAN-MV-07"),
      )
    )

    // V2 Expense & Overtime Claims
    dao.insertClaims(
      listOf(
        ExpenseClaimEntity(
          id = "clm_301",
          employeeId = session.employeeId,
          employeeName = existingEmp?.fullName ?: defaultName,
          category = "FUEL",
          amount = 48.50,
          currency = "USD",
          ticketCode = "LOS-4809",
          description = "Service van diesel refill during Sector B outage response",
          receiptAttached = true,
          status = "APPROVED",
          submittedAt = "2026-11-12",
          accountingOutboxStatus = "POSTED_TO_GL",
          syncStatus = "SYNCED",
        ),
        ExpenseClaimEntity(
          id = "clm_302",
          employeeId = session.employeeId,
          employeeName = existingEmp?.fullName ?: defaultName,
          category = "OVERTIME",
          amount = 92.00,
          currency = "USD",
          ticketCode = "FTTH-4821",
          description = "2.5h emergency night standby splice call-out",
          receiptAttached = false,
          status = "PENDING",
          submittedAt = "2026-11-13",
          accountingOutboxStatus = "AWAITING_APPROVAL",
          syncStatus = "SYNCED",
        ),
      )
    )

    // V2 Approvals (explicitly includes a self-submitted item to demonstrate No-Self-Approval rule)
    dao.insertApprovalItems(
      listOf(
        ApprovalItemEntity(
          id = "apr_self_01",
          itemType = "OVERTIME",
          requesterId = session.employeeId,
          requesterName = "${existingEmp?.fullName ?: defaultName} (Self)",
          summary = "Own Overtime Claim • 2.5 hrs",
          detail = "Emergency splice call-out. Routed to senior manager per no-self-approval rule.",
          metricLabel = "$92.00 / 2.5h",
          submittedAt = "2026-11-13",
          status = "PENDING",
        ),
        ApprovalItemEntity(
          id = "apr_team_01",
          itemType = "LATE_PUNCH",
          requesterId = "emp_204",
          requesterName = "Bikash Tamang (NOC Engineer)",
          summary = "Held Punch Review • GPS ±74m",
          detail = "Indoor NOC basement entrance degraded GPS fix; Wi-Fi BSSID matched KTM-NOC.",
          metricLabel = "Risk Score 35",
          submittedAt = "2026-11-14",
          status = "PENDING",
          delegatedFrom = "Aarav Adhikari",
        ),
        ApprovalItemEntity(
          id = "apr_team_02",
          itemType = "EXPENSE_CLAIM",
          requesterId = "emp_209",
          requesterName = "Priya Karki (Field Billing)",
          summary = "Local Purchase • Conduit Clamps",
          detail = "Scanned hardware receipt attached; tagged to ticket FTTH-4790.",
          metricLabel = "$34.80",
          submittedAt = "2026-11-14",
          status = "PENDING",
        ),
      )
    )

    // V2 Delegations
    dao.insertDelegations(
      listOf(
        DelegationEntity(
          id = "del_1",
          delegatorId = "emp_101",
          delegatorName = "Aarav Adhikari",
          delegateEmployeeId = "emp_102",
          delegateName = "Sita Gurung",
          capabilitiesLabel = "approve.attendance_ot, approve.expense",
          startDate = "2026-11-12",
          endDate = "2026-11-28",
          active = true,
        )
      )
    )

    // V2 Roster & Standby Entries
    dao.insertRosterEntries(
      listOf(
        RosterEntryEntity("ros_1", session.employeeId, existingEmp?.fullName ?: defaultName, "Primary FTTH Crew", "08:30 – 18:30 (Mon–Fri)", true, null, "MV-HQ"),
        RosterEntryEntity("ros_2", "emp_204", "Bikash Tamang", "NOC Backbone L2", "14:00 – 22:00 (Standby)", true, "Sick Leave Req Pending (Nov 16–17)", "KTM-NOC"),
        RosterEntryEntity("ros_3", "emp_207", "Carlos Mendez", "Outside Plant Splice", "08:30 – 17:30", false, "Half-Day Approved Leave Today", "MV-HQ"),
        RosterEntryEntity("ros_4", "emp_209", "Priya Karki", "Enterprise Activation", "09:00 – 18:00", false, null, "SF-FIN"),
      )
    )

    // V2 Needs Attention Inbox (seed 1 illustrative resolved/active conflict item)
    dao.insertNeedsAttention(
      listOf(
        NeedsAttentionEntity(
          id = "na_seed_1",
          errorCode = "PUNCH_HELD",
          title = "Nov 09 Missed Punch-Out Marked Incomplete",
          message = "Shift closed automatically at 18:30 without clock-out; routed to Approvals inbox for supervisor time confirmation.",
          sourceType = "ATTENDANCE",
          sourceId = "att_1109",
          capturedSummary = "Clock-In 08:55 AM • BSSID 84:1B:5E:22:10:A1 • Risk 45",
          retryable = false,
          createdAtLabel = "Nov 09, 18:31",
          resolved = false,
        )
      )
    )

    // V2 + V6 Security & Biometric Audit Log baseline
    val nowMs = System.currentTimeMillis()
    val seedFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val hwLabel =
      "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercaseChar() }} ${android.os.Build.MODEL} • Android ${android.os.Build.VERSION.RELEASE}"
    dao.insertAuditLogs(
      listOf(
        AuditLogEntity(
          id = "aud_init_1",
          actorId = session.employeeId,
          actorEmail = session.email,
          deviceId = session.deviceId,
          screenName = "DEVICE_BINDING",
          actionLabel = "Device ${session.deviceId} bound to ${session.assignedBundles.joinToString(", ")}",
          timestampLabel = seedFmt.format(java.util.Date(nowMs - 7_200_000L)),
          createdAtEpochMs = nowMs - 7_200_000L,
          eventCategory = "DEVICE_BINDING",
          outcome = "INFO",
          authMethod = "SYSTEM",
          deviceModel = hwLabel,
        ),
        AuditLogEntity(
          id = "aud_bio_1",
          actorId = session.employeeId,
          actorEmail = session.email,
          deviceId = session.deviceId,
          screenName = "APP_LOCK",
          actionLabel = "Biometric verified • Unlock PulseHR",
          timestampLabel = seedFmt.format(java.util.Date(nowMs - 3_600_000L)),
          createdAtEpochMs = nowMs - 3_600_000L,
          eventCategory = "BIOMETRIC_AUTH",
          outcome = "SUCCESS",
          authMethod = "BIOMETRIC_STRONG",
          deviceModel = hwLabel,
        ),
        AuditLogEntity(
          id = "aud_bio_2",
          actorId = session.employeeId,
          actorEmail = session.email,
          deviceId = session.deviceId,
          screenName = "PAYSLIP_VIEW",
          actionLabel = "Biometric authentication failed • Unlock Payroll & Payslips",
          timestampLabel = seedFmt.format(java.util.Date(nowMs - 1_800_000L)),
          createdAtEpochMs = nowMs - 1_800_000L,
          eventCategory = "BIOMETRIC_AUTH",
          outcome = "FAILED",
          authMethod = "BIOMETRIC_STRONG",
          deviceModel = hwLabel,
          failureReason = "Biometric sample did not match enrolled template",
        ),
        AuditLogEntity(
          id = "aud_bio_3",
          actorId = session.employeeId,
          actorEmail = session.email,
          deviceId = session.deviceId,
          screenName = "PAYSLIP_VIEW",
          actionLabel = "Biometric verified • Unlock Payroll & Payslips",
          timestampLabel = seedFmt.format(java.util.Date(nowMs - 1_740_000L)),
          createdAtEpochMs = nowMs - 1_740_000L,
          eventCategory = "BIOMETRIC_AUTH",
          outcome = "SUCCESS",
          authMethod = "BIOMETRIC_STRONG",
          deviceModel = hwLabel,
        ),
      )
    )

    // V4 Seed prior workdays employee travel history & client visit routes
    val existingRoutePoints = dao.getAllUnsyncedLocationBreadcrumbs()
    val oneDayMs = 86_400_000L
    val day1Base = System.currentTimeMillis() - oneDayMs
    val day2Base = System.currentTimeMillis() - (2L * oneDayMs)
    val nov19Session = "route_20261119_${session.employeeId}"
    val nov18Session = "route_20261118_${session.employeeId}"
    dao.insertLocationBreadcrumbs(
      listOf(
        // Nov 19 Workday Route: HQ -> Shoreline -> Castro St -> El Camino -> HQ
        LocationBreadcrumbEntity(
          id = "loc_seed_1_${session.employeeId}",
          employeeId = session.employeeId,
          sessionId = nov19Session,
          dateIso = "2026-11-19",
          eventType = "CHECK_IN",
          latitude = 37.4220,
          longitude = -122.0840,
          accuracyMeters = 9.0f,
          siteLabel = "HQ Campus — Mountain View",
          distanceFromBranchMeters = 12.0,
          isInsideGeofence = true,
          capturedAtEpochMs = day1Base,
          capturedTimeLabel = "08:45:10 AM",
          syncStatus = "SYNCED",
          syncedAtEpochMs = day1Base + 2_000L,
        ),
        LocationBreadcrumbEntity(
          id = "loc_seed_2_${session.employeeId}",
          employeeId = session.employeeId,
          sessionId = nov19Session,
          dateIso = "2026-11-19",
          eventType = "WAYPOINT",
          latitude = 37.4046,
          longitude = -122.0789,
          accuracyMeters = 11.0f,
          siteLabel = "Neha Kapoor Residence • 218 Shoreline Blvd",
          distanceFromBranchMeters = 1_980.0,
          isInsideGeofence = false,
          capturedAtEpochMs = day1Base + 2_400_000L,
          capturedTimeLabel = "09:25:10 AM",
          syncStatus = "SYNCED",
          syncedAtEpochMs = day1Base + 2_402_000L,
        ),
        LocationBreadcrumbEntity(
          id = "loc_seed_3_${session.employeeId}",
          employeeId = session.employeeId,
          sessionId = nov19Session,
          dateIso = "2026-11-19",
          eventType = "MANUAL_PUSH",
          latitude = 37.3948,
          longitude = -122.0785,
          accuracyMeters = 10.0f,
          siteLabel = "Everest Espresso Roasters • 412 Castro St",
          distanceFromBranchMeters = 3_060.0,
          isInsideGeofence = false,
          capturedAtEpochMs = day1Base + 6_300_000L,
          capturedTimeLabel = "10:30:10 AM",
          syncStatus = "SYNCED",
          syncedAtEpochMs = day1Base + 6_302_000L,
        ),
        LocationBreadcrumbEntity(
          id = "loc_seed_4_${session.employeeId}",
          employeeId = session.employeeId,
          sessionId = nov19Session,
          dateIso = "2026-11-19",
          eventType = "WAYPOINT",
          latitude = 37.3861,
          longitude = -122.0839,
          accuracyMeters = 13.0f,
          siteLabel = "Bayview Clinic Suite 3B • 890 El Camino",
          distanceFromBranchMeters = 3_990.0,
          isInsideGeofence = false,
          capturedAtEpochMs = day1Base + 13_200_000L,
          capturedTimeLabel = "12:25:10 PM",
          syncStatus = "SYNCED",
          syncedAtEpochMs = day1Base + 13_202_000L,
        ),
        LocationBreadcrumbEntity(
          id = "loc_seed_5_${session.employeeId}",
          employeeId = session.employeeId,
          sessionId = nov19Session,
          dateIso = "2026-11-19",
          eventType = "CHECK_OUT",
          latitude = 37.4219,
          longitude = -122.0841,
          accuracyMeters = 8.0f,
          siteLabel = "HQ Campus — Mountain View",
          distanceFromBranchMeters = 15.0,
          isInsideGeofence = true,
          capturedAtEpochMs = day1Base + 28_800_000L,
          capturedTimeLabel = "04:45:10 PM",
          syncStatus = "SYNCED",
          syncedAtEpochMs = day1Base + 28_802_000L,
        ),
        // Nov 18 Workday Route: HQ -> Castro St -> El Camino -> HQ
        LocationBreadcrumbEntity(
          id = "loc_seed_18_1_${session.employeeId}",
          employeeId = session.employeeId,
          sessionId = nov18Session,
          dateIso = "2026-11-18",
          eventType = "CHECK_IN",
          latitude = 37.4220,
          longitude = -122.0840,
          accuracyMeters = 9.0f,
          siteLabel = "HQ Campus — Mountain View",
          distanceFromBranchMeters = 10.0,
          isInsideGeofence = true,
          capturedAtEpochMs = day2Base,
          capturedTimeLabel = "08:52:00 AM",
          syncStatus = "SYNCED",
          syncedAtEpochMs = day2Base + 1_500L,
        ),
        LocationBreadcrumbEntity(
          id = "loc_seed_18_2_${session.employeeId}",
          employeeId = session.employeeId,
          sessionId = nov18Session,
          dateIso = "2026-11-18",
          eventType = "WAYPOINT",
          latitude = 37.3951,
          longitude = -122.0787,
          accuracyMeters = 14.0f,
          siteLabel = "Castro St Fiber Cabinet • FDB-09",
          distanceFromBranchMeters = 3_020.0,
          isInsideGeofence = false,
          capturedAtEpochMs = day2Base + 5_400_000L,
          capturedTimeLabel = "10:22:00 AM",
          syncStatus = "SYNCED",
          syncedAtEpochMs = day2Base + 5_401_500L,
        ),
        LocationBreadcrumbEntity(
          id = "loc_seed_18_3_${session.employeeId}",
          employeeId = session.employeeId,
          sessionId = nov18Session,
          dateIso = "2026-11-18",
          eventType = "CHECK_OUT",
          latitude = 37.4218,
          longitude = -122.0838,
          accuracyMeters = 10.0f,
          siteLabel = "HQ Campus — Mountain View",
          distanceFromBranchMeters = 24.0,
          isInsideGeofence = true,
          capturedAtEpochMs = day2Base + 27_000_000L,
          capturedTimeLabel = "04:22:00 PM",
          syncStatus = "SYNCED",
          syncedAtEpochMs = day2Base + 27_001_500L,
        ),
      )
    )
    existingRoutePoints.size

    seedShiftsForSession(session, bootstrapShifts)
  }

  suspend fun seedShiftsForSession(
    session: UserSession,
    bootstrapShifts: List<EmployeeShiftDto> = emptyList(),
  ) {
    // V5 Multi-Shift Schedule synced from bootstrap payload or derived from roster fallback
    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val cal = Calendar.getInstance()
    val todayDate = dateFmt.format(cal.time)
    cal.add(Calendar.DAY_OF_YEAR, 1)
    val tomorrowDate = dateFmt.format(cal.time)
    cal.add(Calendar.DAY_OF_YEAR, 1)
    val dayAfterDate = dateFmt.format(cal.time)
    val branchScope = session.branchScope.substringBefore(" ").ifBlank { "MV-HQ" }

    val shiftEntities =
      if (bootstrapShifts.isNotEmpty()) {
        bootstrapShifts.map { dto ->
          EmployeeShiftEntity(
            id = dto.id,
            employeeId = session.employeeId,
            shiftCode = dto.shiftCode,
            shiftName = dto.shiftName,
            shiftDateAd = dto.shiftDateAd,
            startHour = dto.startHour,
            startMinute = dto.startMinute,
            endHour = dto.endHour,
            endMinute = dto.endMinute,
            crossesMidnight = dto.crossesMidnight,
            workMode = dto.workMode,
            requireGeofenceCheckIn = dto.requireGeofenceCheckIn,
            requireGeofenceCheckOut = dto.requireGeofenceCheckOut,
            branchCode = dto.branchCode,
            sequenceNo = dto.sequenceNo,
            isStandby24x7 = dto.isStandby24x7,
            source = "BOOTSTRAP_ROSTER",
          )
        }
      } else {
        listOf(
          EmployeeShiftEntity(
            id = "shf_${session.employeeId}_day_1",
            employeeId = session.employeeId,
            shiftCode = "FTTH-DAY",
            shiftName = "Primary Field Dispatch Shift",
            shiftDateAd = todayDate,
            startHour = 8,
            startMinute = 30,
            endHour = 17,
            endMinute = 30,
            crossesMidnight = false,
            workMode = "FIELD",
            requireGeofenceCheckIn = true,
            requireGeofenceCheckOut = false,
            branchCode = branchScope,
            sequenceNo = 1,
            isStandby24x7 = false,
            source = "BOOTSTRAP_ROSTER",
          ),
          EmployeeShiftEntity(
            id = "shf_${session.employeeId}_split_2",
            employeeId = session.employeeId,
            shiftCode = "EVE-SURGE",
            shiftName = "Split Evening Cutover Window",
            shiftDateAd = todayDate,
            startHour = 18,
            startMinute = 30,
            endHour = 21,
            endMinute = 0,
            crossesMidnight = false,
            workMode = "FIELD",
            requireGeofenceCheckIn = false,
            requireGeofenceCheckOut = false,
            branchCode = branchScope,
            sequenceNo = 2,
            isStandby24x7 = false,
            source = "BOOTSTRAP_ROSTER",
          ),
          EmployeeShiftEntity(
            id = "shf_${session.employeeId}_night_1",
            employeeId = session.employeeId,
            shiftCode = "NOC-NIGHT",
            shiftName = "Overnight Backbone Maintenance",
            shiftDateAd = tomorrowDate,
            startHour = 22,
            startMinute = 0,
            endHour = 6,
            endMinute = 0,
            crossesMidnight = true,
            workMode = "OFFICE",
            requireGeofenceCheckIn = true,
            requireGeofenceCheckOut = true,
            branchCode = branchScope,
            sequenceNo = 1,
            isStandby24x7 = true,
            source = "BOOTSTRAP_ROSTER",
          ),
          EmployeeShiftEntity(
            id = "shf_${session.employeeId}_day_3",
            employeeId = session.employeeId,
            shiftCode = "FTTH-DAY",
            shiftName = "Enterprise Client Fiber Rollout",
            shiftDateAd = dayAfterDate,
            startHour = 9,
            startMinute = 0,
            endHour = 18,
            endMinute = 0,
            crossesMidnight = false,
            workMode = "HYBRID",
            requireGeofenceCheckIn = true,
            requireGeofenceCheckOut = false,
            branchCode = branchScope,
            sequenceNo = 1,
            isStandby24x7 = false,
            source = "BOOTSTRAP_ROSTER",
          ),
        )
      }
    dao.deleteShiftsForEmployeeBySource(session.employeeId, "BOOTSTRAP_ROSTER")
    dao.insertEmployeeShifts(shiftEntities)
  }
}
