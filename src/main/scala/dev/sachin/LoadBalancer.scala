package dev.sachin

import cats.effect.IO
import cats.effect.std.Queue
import cats.implicits.toTraverseOps

import scala.concurrent.duration.DurationInt

class LoadBalancer[K, Req, Res] private (
    requestRegistry: RequestRegistry[K, Res],
    distributorQueue: Queue[IO, Req],
    servers: Seq[Req => IO[(K, Res)]]
) {

  def submitRequest(key: K, request: Req): IO[Res] =
    for {
      promise  <- requestRegistry.register(key)
      _        <- distributorQueue.offer(request)
      response <- promise.get.timeout(10.seconds)
    } yield response

  // todo can add strategies e.g round robin
  private def balance: IO[Unit] =
    servers.traverse(s => serverLogic(s).foreverM.start).void

  private def serverLogic(server: Req => IO[(K, Res)]): IO[Unit] =
    for {
      r <- distributorQueue.take.flatMap(req => server(req))
      (requestID, response) = r
      _ <- requestRegistry.complete(requestID, response)
    } yield ()

  // can we shutdown queues in cats safely? if so, provide LoadBalancer.resource
//  private def shutdown: IO[Unit] = ???
}

object LoadBalancer {
  def apply[K, Req, Res](servers: (Req => IO[(K, Res)])*): IO[LoadBalancer[K, Req, Res]] =
    for {
      requestRegistry <- RequestRegistry[K, Res]()
      // use queue bounded for rate limiting
      distributorQueue <- Queue.unbounded[IO, Req]
      lb = new LoadBalancer(requestRegistry, distributorQueue, servers)
      _ <- lb.balance
    } yield lb
}
