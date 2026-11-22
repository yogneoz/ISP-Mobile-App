package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
  entities =
    [
      BranchEntity::class,
      EmployeeEntity::class,
      AttendanceEntity::class,
      LeaveBalanceEntity::class,
      LeaveRequestEntity::class,
      PayrollRunEntity::class,
      PayrollRunLineEntity::class,
      EmployeeDocumentEntity::class,
      AnnouncementEntity::class,
      PendingSyncActionEntity::class,
      WorkOrderEntity::class,
      VanStockEntity::class,
      ExpenseClaimEntity::class,
      ApprovalItemEntity::class,
      DelegationEntity::class,
      RosterEntryEntity::class,
      NeedsAttentionEntity::class,
      AuditLogEntity::class,
      LocationBreadcrumbEntity::class,
      EmployeeShiftEntity::class,
    ],
  version = 7,
  exportSchema = false,
)
abstract class PulseHrDatabase : RoomDatabase() {
  abstract fun pulseHrDao(): PulseHrDao
}
