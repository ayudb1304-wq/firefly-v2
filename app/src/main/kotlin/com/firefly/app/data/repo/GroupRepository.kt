package com.firefly.app.data.repo

import com.firefly.app.core.group.GroupCode
import com.firefly.app.core.group.SenderId
import com.firefly.app.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** The one group this install is in (PRD A4: one group at a time). */
data class GroupSession(val code: String, val senderId: Int) {
    val groupId: Long = GroupCode.groupId(code)
}

class GroupRepository(
    private val settings: SettingsStore,
    private val members: MemberRepository,
) {
    val session: Flow<GroupSession?> = combine(settings.groupCode, settings.senderId) { code, id ->
        if (code != null && id != null && GroupCode.isValid(code) && SenderId.isValid(id)) GroupSession(code, id) else null
    }.distinctUntilChanged()

    /** Generate a fresh code locally (PRD A1) and join it. */
    suspend fun create(): String {
        val code = GroupCode.generate()
        join(code)
        return code
    }

    /** @return the normalised code, or null if the input is not a valid code. */
    suspend fun join(input: String): String? {
        val code = GroupCode.normalise(input) ?: return null
        members.clear()
        settings.setGroup(code, SenderId.generate())
        return code
    }

    /** PRD A3: leave and wipe local data for the group. */
    suspend fun leave() {
        members.clear()
        settings.clearGroup()
    }
}
