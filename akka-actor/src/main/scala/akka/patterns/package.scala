/**
 * Copyright (C) 2009-2011 Typesafe Inc. <http://www.typesafe.com>
 */
package akka

package object patterns {

  import akka.actor.{ ActorRef, InternalActorRef, ActorRefWithProvider, AskTimeoutException }
  import akka.dispatch.{ Future, Promise }
  import akka.util.Timeout

  /**
   * Import this implicit conversion to gain `?` and `ask` methods on
   * [[akka.actor.ActorRef]], which will defer to the
   * `ask(actorRef, message)(timeout)` method defined here.
   *
   * {{{
   * import akka.patterns.ask
   *
   * val future = actor ? message             // => ask(actor, message)
   * val future = actor ask message           // => ask(actor, message)
   * val future = actor.ask(message)(timeout) // => ask(actor, message)(timeout)
   * }}}
   *
   * All of the above use an implicit [[akka.actor.Timeout]].
   */
  implicit def ask(actorRef: ActorRef): AskableActorRef = new AskableActorRef(actorRef)

  /**
   * Sends a message asynchronously and returns a [[akka.dispatch.Future]]
   * holding the eventual reply message; this means that the target actor
   * needs to send the result to the `sender` reference provided. The Future
   * will be completed with an [[akka.actor.AskTimeoutException]] after the
   * given timeout has expired; this is independent from any timeout applied
   * while awaiting a result for this future (i.e. in
   * `Await.result(..., timeout)`).
   *
   * <b>Warning:</b>
   * When using future callbacks, inside actors you need to carefully avoid closing over
   * the containing actor’s object, i.e. do not call methods or access mutable state
   * on the enclosing actor from within the callback. This would break the actor
   * encapsulation and may introduce synchronization bugs and race conditions because
   * the callback will be scheduled concurrently to the enclosing actor. Unfortunately
   * there is not yet a way to detect these illegal accesses at compile time.
   *
   * <b>Recommended usage:</b>
   *
   * {{{
   *   val f = ask(worker, request)(timeout)
   *   flow {
   *     EnrichedRequest(request, f())
   *   } pipeTo nextActor
   * }}}
   *
   * [see [[akka.dispatch.Future]] for a description of `flow`]
   */
  def ask(actorRef: ActorRef, message: Any)(implicit timeout: Timeout): Future[Any] = actorRef match {
    case ref: ActorRefWithProvider ⇒
      ref.provider.ask(timeout) match {
        case Some(ref) ⇒
          actorRef.tell(message, ref)
          ref.result
        case None ⇒
          actorRef.tell(message)
          Promise.failed(new AskTimeoutException("failed to create PromiseActorRef"))(ref.provider.dispatcher)
      }
    case _ ⇒ throw new IllegalArgumentException("incompatible ActorRef " + actorRef)
  }

}
