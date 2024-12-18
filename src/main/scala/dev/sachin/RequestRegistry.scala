package dev.sachin

import cats.effect.{ Deferred, IO, Ref }

/**
  * A registry to manage requests and their corresponding responses using Deferred.
  * @tparam RequestID The type of the request identifier.
  * @tparam Response The type of the response.
  */
class RequestRegistry[RequestID, Response] private (map: Ref[IO, Map[RequestID, Deferred[IO, Response]]]) {

  /**
    * Registers a new computation/IO by its `requestID` and returns a `Deferred` that can
    * be satisfied with the response in some other part of code and submitted to this RequestRegistry.
    *
    *  Example usage:
    *  {{{
    *    for {
    *    request  <- buildRequest()
    *    _        <- fireRequest
    *    deferred <- registry.register(request.key)
    *    response <- deferred.get.timeoutTo(5.seconds, IO(alternateResponse))
    *    } yield ()
    *  }}}
    */
  def register(key: RequestID): IO[Deferred[IO, Response]] =
    Deferred[IO, Response].flatMap { deferred =>
      map.modify { m =>
        (m + (key -> deferred), IO.pure(deferred))
      }.flatten
    }

  /**
    * Submits the response with the given requestID to this registry or does nothing if no promise
    * was submitted to this RequestRegistry while making request using `register(key)`.
    *
    * Example usage:
    * {{{
    *    for {
    *    response  <- gotResponseOutOfThinAir() // e.g. some kafka topic
    *    _ <- registry.complete(response.key, response)
    *    } yield ()
    * }}}
    *
    */
  def complete(key: RequestID, response: Response): IO[Unit] =
    map.modify { m =>
      m.get(key)
        .map(d => (m - key, d.complete(response).void))
        .getOrElse((m, IO.unit))
    }.flatten

}

object RequestRegistry {

  /**
    * A registry to manage requests and their corresponding responses using Deferred.
    *
    * This registry can be created once in program and passed to different parts of program. You can
    * e.g. make some request, register a key using `registry.registry(key)` and receive a Cats `Deferred`
    * in return. Some other part of program can satisfy the registry with a response for the same key e.g.
    * `registry.complete(key, response)`.
    *
    * Example usage:
    * {{{
    *   val registry = RequestRegistry[String, String]()
    * }}}
    *
    */
  def apply[RequestID, Response](): IO[RequestRegistry[RequestID, Response]] =
    Ref.of[IO, Map[RequestID, Deferred[IO, Response]]](Map.empty).map(new RequestRegistry(_))

}
