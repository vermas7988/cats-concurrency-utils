package dev.sachin

import cats.effect.kernel.Sync
import cats.implicits._

import scala.collection.mutable

object LRUCache {

  /** Creates a new `LRUCache` instance.
    *  @param size The maximum size of the cache.
    *  @param onEvict An optional callback function to be called when an element is evicted.
    *  @return A new `LRUCache` instance.
    */
  def apply[F[_]: Sync, K, V](size: Int, onEvict: Option[EvictCallback[K, V]] = None): LRUCache[F, K, V] =
    new LRUCacheImpl[F, K, V](size, onEvict)

}

trait LRUCache[F[_], K, V] {

  /** Adds a value to the cache, returns true if an eviction occurred and
    * updates the "recently used"-ness of the key.
    */
  def add(key: K, value: V): F[Boolean]

  /** Returns key's value from the cache and
    * updates the "recently used"-ness of the key.
    */
  def get(key: K): F[Option[V]]

  /** Checks if a key exists in cache without updating the recent-ness.
    */
  def contains(key: K): F[Boolean]

  /** Returns key's value without updating the "recently used"-ness of the key.
    */
  def peek(key: K): F[Option[V]]

  /** Removes a key from the cache.
    */
  def remove(key: K): F[Boolean]

  /** Removes the oldest entry from cache.
    */
  def removeOldest(): F[Option[(K, V)]]

  /** Returns the oldest entry from the cache.
    */
  def getOldest(): F[Option[(K, V)]]

  /** Returns a sequence of the keys in the cache, from oldest to newest.
    */
  def keys(): F[Seq[K]]

  /** Returns a sequence of the values in the cache, from oldest to newest.
    */
  def values(): F[Seq[V]]

  /** Returns the number of items in the cache.
    */
  def len(): F[Int]

  /** Returns the capacity of the cache.
    */
  def cap(): F[Int]

  /** Clears all cache entries.
    */
  def purge(): F[Unit]

  /** Resizes cache, returning number evicted.
    */
  def resize(newSize: Int): F[Int]

}
trait EvictCallback[K, V] {
  def apply(k: K, v: V): Unit
}

// LRU implements a thread-safe fixed size LRU cache using Cats Effect
class LRUCacheImpl[F[_]: Sync, K, V](initialSize: Int, val onEvict: Option[EvictCallback[K, V]])
    extends LRUCache[F, K, V] {
  require(initialSize > 0, "must provide a positive size")

  // Internal size field, can change on resize
  private var size: Int = initialSize

  /**
    * `evictList` (LinkedHashMap):
    *
    * This `LinkedHashMap` is the heart of the LRU (Least Recently Used) algorithm.
    * It stores the cache entries (key-value pairs) in the order they were accessed, from least recently used to most recently used.
    *
    * - **Key:** The key of the cache entry (type `K`).
    * - **Value:** The value associated with the key (type `V`).
    *
    * **Why LinkedHashMap?**
    *
    * The `LinkedHashMap` is crucial for implementing the LRU policy efficiently.  It maintains insertion order, which allows us to easily identify the least recently used element (the one at the head of the map).
    * Additionally, it provides constant-time performance for moving elements to the end of the map when they are accessed (which updates their "recentness").
    * `LinkedHashMap` combines the fast lookup of a `HashMap` with the ordered iteration of a `LinkedList`.
    *
    * **How it's used in LRU:**
    *
    * 1. **Insertion:** When a new entry is added to the cache, it's added to the end of the `evictList`.
    * 2. **Access (get):** When an existing entry is accessed (using `get`), it's removed from its current position and re-inserted at the end of the `evictList`. This effectively moves it to the "most recently used" position.
    * 3. **Eviction:** When the cache is full and a new entry needs to be added, the element at the head of the `evictList` (the least recently used) is removed.
    * 4. **Iteration:** The `keys` and `values` methods of `evictList` provide the cache entries in LRU order (from oldest to newest).
    */
  private val evictList = new mutable.LinkedHashMap[K, V]()

  /**
    * `items` (Map):
    *
    * This `Map` provides fast access to the cache entries by key.  It's a standard `Map` that stores the same key-value pairs as `evictList`, but it's optimized for lookups.
    *
    * - **Key:** The key of the cache entry (type `K`).
    * - **Value:** The value associated with the key (type `V`).
    *
    * **Why Map?**
    *
    * A standard `Map` (like `HashMap`) is used for `items` because we need to quickly retrieve the value associated with a given key.  `Map` provides constant-time (on average) lookup by key.
    *
    * **How it's used in LRU:**
    *
    * 1. **Lookup (get, contains, peek):** The `items` map is used to quickly check if a key exists in the cache and to retrieve its value.
    * 2. **Insertion/Update:** When a new entry is added or an existing entry is updated, the `items` map is also updated to reflect the change.
    * 3. **Removal:** When an entry is removed (either explicitly or due to eviction), it's removed from the `items` map as well.
    */
  private val items = mutable.Map.empty[K, V]

  def purge(): F[Unit] = Sync[F].delay {
    items.foreach {
      case (k, v) =>
        onEvict.foreach(callback => callback(k, v))
    }
    items.clear()
    evictList.clear()
  }

  def add(key: K, value: V): F[Boolean] = {
    def updateCache(): F[Boolean] = Sync[F].delay {
      if (items.contains(key)) {
        evictList -= key
        evictList.put(key, value): Unit
        items.update(key, value)
        false
      } else {
        evictList.put(key, value): Unit
        items.put(key, value): Unit
        evictList.size > size // Return true if eviction *will* be needed, not *has* happened.
      }
    }

    for {
      willEvict <- updateCache()
      _         <- if (willEvict) removeOldest().void else Sync[F].unit
    } yield willEvict
  }

  def get(key: K): F[Option[V]] = Sync[F].delay {
    items.get(key).map { value =>
      evictList -= key
      evictList.put(key, value): Unit
      value
    }
  }

  def contains(key: K): F[Boolean] = Sync[F].delay(items.contains(key))

  def peek(key: K): F[Option[V]] = Sync[F].delay(items.get(key))

  def remove(key: K): F[Boolean] = Sync[F].delay {
    if (items.contains(key)) {
      removeElement(key)
      true
    } else {
      false
    }
  }

  def removeOldest(): F[Option[(K, V)]] = Sync[F].delay {
    evictList.headOption.map {
      case (key, value) =>
        removeElement(key)
        (key, value)
    }
  }

  def getOldest(): F[Option[(K, V)]] = Sync[F].delay(evictList.headOption)

  def keys(): F[Seq[K]] = Sync[F].delay(evictList.keys.toSeq)

  def values(): F[Seq[V]] = Sync[F].delay(evictList.values.toSeq)

  def len(): F[Int] = Sync[F].delay(evictList.size)

  def cap(): F[Int] = Sync[F].pure(size)

  def resize(newSize: Int): F[Int] = {
    if (newSize <= 0) {
      Sync[F].raiseError(new IllegalArgumentException("New size must be positive"))
    } else {
      for {
        currentSize <- len()
        diff = currentSize - newSize
        _ <- if (diff > 0) {
          (0 until diff).toList.traverse_(_ => removeOldest())
        } else {
          Sync[F].unit
        }
        _ <- Sync[F].delay(this.size = newSize) // Now correctly updates the internal size
      } yield diff
    }
  }

  private def removeElement(key: K): Unit = {
    evictList -= key
    items.remove(key).foreach { value =>
      onEvict.foreach(callback => callback(key, value))
    }
  }
}
