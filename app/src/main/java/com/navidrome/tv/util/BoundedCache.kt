package com.navidrome.tv.util

/** Small access-ordered cache. Values expire even when frequently read. */
class BoundedCache<K, V>(private val capacity: Int, private val ttlMs: Long = Long.MAX_VALUE,
    private val clock: () -> Long = System::currentTimeMillis) {
    private data class Entry<V>(val value: V, val created: Long)
    private val entries = LinkedHashMap<K, Entry<V>>(16, .75f, true)
    init { require(capacity > 0 && ttlMs > 0) }
    @Synchronized operator fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (clock() - entry.created >= ttlMs) { entries.remove(key); return null }
        return entry.value
    }
    @Synchronized fun put(key: K, value: V) {
        entries[key] = Entry(value, clock())
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }
    @Synchronized fun clear() = entries.clear()
}

/** Evict an entire screen together so a loaded flag cannot outlive its data. */
class ScreenCache(capacity: Int = 12) {
    private val groups = BoundedCache<String, MutableMap<String, Any>>(capacity)
    @Synchronized @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrPut(key: String, create: () -> T): T {
        val groupKey = key.substringBeforeLast(':', key)
        val group = groups[groupKey] ?: mutableMapOf<String, Any>().also { groups.put(groupKey, it) }
        return group.getOrPut(key, create) as T
    }
    @Synchronized fun clear() = groups.clear()
}

/** Preserve order, including duplicates already explicitly placed in a queue. */
fun <T, K> mergeDistinct(base: List<T>, incoming: List<T>, key: (T) -> K): List<T> {
    val seen = base.mapTo(HashSet()) { key(it) }
    return base + incoming.filter { seen.add(key(it)) }
}
