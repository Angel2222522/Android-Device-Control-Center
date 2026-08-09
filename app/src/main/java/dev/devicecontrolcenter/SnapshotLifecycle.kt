package dev.devicecontrolcenter

data class SnapshotUiState<T>(
    val snapshot: T? = null,
    val capturedAtMillis: Long? = null,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
) {
    fun beginRefresh(): SnapshotUiState<T> = copy(
        isRefreshing = true,
        errorMessage = null,
    )

    fun success(snapshot: T, capturedAtMillis: Long): SnapshotUiState<T> = SnapshotUiState(
        snapshot = snapshot,
        capturedAtMillis = capturedAtMillis,
        isRefreshing = false,
        errorMessage = null,
    )

    fun failure(message: String): SnapshotUiState<T> = copy(
        isRefreshing = false,
        errorMessage = message,
    )
}

class SnapshotRefreshGate(
    private val minimumIntervalMillis: Long = 1_000L,
) {
    init {
        require(minimumIntervalMillis >= 0L) { "minimumIntervalMillis must not be negative" }
    }

    private var inFlight = false
    private var lastCompletedAtMillis: Long? = null

    @Synchronized
    fun tryStart(nowMillis: Long): Boolean {
        if (inFlight) return false

        val lastCompleted = lastCompletedAtMillis
        if (lastCompleted != null && nowMillis - lastCompleted < minimumIntervalMillis) {
            return false
        }

        inFlight = true
        return true
    }

    @Synchronized
    fun complete(nowMillis: Long) {
        inFlight = false
        lastCompletedAtMillis = nowMillis
    }

    @Synchronized
    fun cancel() {
        inFlight = false
    }
}
