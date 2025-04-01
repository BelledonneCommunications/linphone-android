package org.linphone.models.callhistory

enum class ReportStates(val value: Int) {
    Pending(0),
    Complete(1),
    Failed(2),
    Counting(3),
    Timeout(4);

    companion object {
        fun isDone(reportStatus: Int): Boolean {
            return reportStatus == ReportStates.Complete.value ||
                reportStatus == ReportStates.Failed.value ||
                reportStatus == ReportStates.Timeout.value
        }
    }
}
