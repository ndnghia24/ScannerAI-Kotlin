package com.example.scannerai.domain.utils.tree

data class WrongEntryException(
    val availableEntries: Set<String>
): Exception() {
    override val message = "Wrong entry number. Available: $availableEntries"
}