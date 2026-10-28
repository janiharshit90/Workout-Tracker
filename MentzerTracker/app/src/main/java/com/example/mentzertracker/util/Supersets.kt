package com.example.mentzertracker.util

/**
 * Supersets are stored as "this exercise goes straight into the next one in the list". These helpers
 * keep that set consistent as the list changes.
 */

/** True when [id] is followed, with no rest, by the next exercise in [ids]. */
fun linksToNext(ids: List<Long>, supersets: Set<Long>, id: Long): Boolean {
    val i = ids.indexOf(id)
    return i >= 0 && i < ids.lastIndex && id in supersets
}

/** The exercise done straight before [id] (no rest), if any. */
fun linkedFrom(ids: List<Long>, supersets: Set<Long>, id: Long): Long? {
    val i = ids.indexOf(id)
    return if (i > 0 && ids[i - 1] in supersets) ids[i - 1] else null
}

/**
 * Removing an exercise from a list breaks any link into or out of it: otherwise the exercise before
 * it would silently become supersetted with whatever comes after.
 */
fun supersetsWithout(ids: List<Long>, supersets: Set<Long>, removed: Long): Set<Long> {
    val prev = ids.getOrNull(ids.indexOf(removed) - 1)
    return supersets - removed - setOfNotNull(prev)
}

/** Only keep links that point at a real next exercise (drops a link on the last one). */
fun cleanSupersets(ids: List<Long>, supersets: Set<Long>): Set<Long> =
    ids.dropLast(1).filter { it in supersets }.toSet()
