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

  private var size: Int = initialSize // Internal size field, can change on resize
  private val evictList = new mutable.LinkedHashMap[K, V]()
  private val items     = mutable.Map.empty[K, V]

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
