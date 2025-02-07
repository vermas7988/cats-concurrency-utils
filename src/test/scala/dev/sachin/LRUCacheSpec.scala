package dev.sachin

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class LRUCacheSpec extends AnyWordSpec with Matchers {

  "An LRUCache" should {

    "add and get elements" in {
      val cache = LRUCache[IO, String, Int](3)
      val assertion = for {
        _      <- cache.add("a", 1)
        _      <- cache.add("b", 2)
        _      <- cache.add("c", 3)
        valueA <- cache.get("a")
        valueB <- cache.get("b")
        valueC <- cache.get("c")
      } yield {
        valueA shouldBe Some(1): Unit
        valueB shouldBe Some(2): Unit
        valueC shouldBe Some(3)
      }
      assertion.unsafeRunSync()
    }

    "evict elements when capacity is exceeded" in {
      val cache = LRUCache[IO, String, Int](3)
      val assertion = for {
        _      <- cache.add("a", 1)
        _      <- cache.add("b", 2)
        _      <- cache.add("c", 3)
        _      <- cache.add("d", 4) // "a" should be evicted
        valueA <- cache.get("a")
        valueD <- cache.get("d")
      } yield {
        valueA shouldBe None: Unit
        valueD shouldBe Some(4)
      }
      assertion.unsafeRunSync()

    }

    "update existing elements" in {
      val cache = LRUCache[IO, String, Int](3)
      val assertion = for {
        _      <- cache.add("a", 1)
        _      <- cache.add("b", 2)
        _      <- cache.add("a", 5) // Update "a"
        valueA <- cache.get("a")
      } yield {
        valueA shouldBe Some(5)
      }
      assertion.unsafeRunSync()

    }

    "check if it contains elements" in {
      val cache = LRUCache[IO, String, Int](3)
      val assertion = for {
        _         <- cache.add("a", 1)
        containsA <- cache.contains("a")
        containsB <- cache.contains("b")
      } yield {
        containsA shouldBe true: Unit
        containsB shouldBe false
      }
      assertion.unsafeRunSync()

    }

    "peek at elements without updating recentness" in {
      val cache = LRUCache[IO, String, Int](3)
      val assertion = for {
        _          <- cache.add("a", 1)
        valueA     <- cache.peek("a")
        _          <- cache.add("b", 2) // Modify the cache, peek should not update recentness
        valueAPeek <- cache.peek("a")
      } yield {
        valueA shouldBe Some(1): Unit
        valueAPeek shouldBe Some(1)
      }
      assertion.unsafeRunSync()

    }

    "remove elements" in {
      val cache = LRUCache[IO, String, Int](3)
      for {
        _        <- cache.add("a", 1)
        removedA <- cache.remove("a")
        valueA   <- cache.get("a")
      } yield {
        removedA shouldBe true: Unit
        valueA shouldBe None
      }
    }

    "remove the oldest element" in {
      val cache = LRUCache[IO, String, Int](3)
      val assertion = for {
        _      <- cache.add("a", 1)
        _      <- cache.add("b", 2)
        _      <- cache.add("c", 3)
        oldest <- cache.removeOldest()
        valueA <- cache.get("a")
      } yield {
        oldest shouldBe Some(("a", 1)): Unit
        valueA shouldBe None
      }
      assertion.unsafeRunSync()

    }

    "get the oldest element" in {
      val cache = LRUCache[IO, String, Int](3)
      val assertion = for {
        _      <- cache.add("a", 1)
        _      <- cache.add("b", 2)
        _      <- cache.add("c", 3)
        oldest <- cache.getOldest()
      } yield {
        oldest shouldBe Some(("a", 1))
      }
      assertion.unsafeRunSync()

    }

    "return keys and values in correct order" in {
      val cache = LRUCache[IO, String, Int](3)
      val assertion = for {
        _      <- cache.add("a", 1)
        _      <- cache.add("b", 2)
        _      <- cache.add("c", 3)
        keys   <- cache.keys()
        values <- cache.values()
      } yield {
        keys shouldBe Seq("a", "b", "c"): Unit
        values shouldBe Seq(1, 2, 3)
      }
      assertion.unsafeRunSync()

    }

    "return correct length and capacity" in {
      val cache = LRUCache[IO, String, Int](3)
      val assertion = for {
        _   <- cache.add("a", 1)
        len <- cache.len()
        cap <- cache.cap()
      } yield {
        len shouldBe 1: Unit
        cap shouldBe 3
      }
      assertion.unsafeRunSync()

    }

    "purge all elements" in {
      val cache = LRUCache[IO, String, Int](3)
      val assertion = for {
        _   <- cache.add("a", 1)
        _   <- cache.add("b", 2)
        _   <- cache.purge()
        len <- cache.len()
      } yield {
        len shouldBe 0
      }
      assertion.unsafeRunSync()

    }

    "resize the cache" in {
      val cache = LRUCache[IO, String, Int](3)
      val assertion = for {
        _    <- cache.add("a", 1)
        _    <- cache.add("b", 2)
        _    <- cache.resize(2)
        len  <- cache.len()
        cap  <- cache.cap()
        _    <- cache.resize(4)
        _    <- cache.add("c", 3)
        len2 <- cache.len()
        cap2 <- cache.cap()
      } yield {
        len shouldBe 2: Unit
        cap shouldBe 2: Unit
        len2 shouldBe 3: Unit
        cap2 shouldBe 4
      }
      assertion.unsafeRunSync()

    }

    "call the eviction callback" in {
      var evictedKey: Option[String] = None
      var evictedValue: Option[Int]  = None

      val callback = new EvictCallback[String, Int] {
        override def apply(k: String, v: Int): Unit = {
          evictedKey = Some(k)
          evictedValue = Some(v)
        }
      }

      val cache = LRUCache[IO, String, Int](3, Some(callback))
      val assertion = for {
        _ <- cache.add("a", 1)
        _ <- cache.add("b", 2)
        _ <- cache.add("c", 3)
        _ <- cache.add("d", 4) // "a" should be evicted
      } yield {
        evictedKey shouldBe Some("a"): Unit
        evictedValue shouldBe Some(1)
      }
      assertion.unsafeRunSync()

    }
  }
}
