package com.impact.akkathrift

import collection.immutable.Queue
import concurrent.Future
import concurrent.Await
import concurrent.duration._
import util.{Success,Failure}

import java.net.InetSocketAddress

import akka.io._
import akka.actor.{IO=>AIO,_}
import akka.pattern.ask

import org.apache.thrift.transport.{TTransport, TServerTransport}
import org.apache.thrift.protocol.{TProtocol}
import org.apache.thrift.TProcessor

class AkkaThriftServerTransport(addr:InetSocketAddress)(implicit system:ActorSystem) extends TServerTransport {
  var sActor:Option[ActorRef] = None
  override def listen() = {
    sActor = Some(system.actorOf(Props[AkkaThriftServerActor], "thrift-server"))
    sActor.map(IO(Tcp) ! Tcp.Bind(_, addr))
  }

  override def close() = {
    // Not sure how to close a channel, so I'm guessing close
    IO(Tcp) ! Tcp.Unbind
    sActor.foreach(_ ! 'Stop)
  }

  /** 
    This is stupid: you are required to return a non-null transport which means we have
    a few choices:

    1. Block the thread until we have a connection come in.  This is dumb.
    2. Return a 'None' Transport which we know how to handle later.  Also dumb
    3. Return null and just not use `accept`.  Ding ding ding!
  */
  override def acceptImpl():TTransport = null

}

class AkkaThriftServerActor(toProto: TTransport => TProtocol, proc: => TProcessor) extends Actor with ActorLogging {
  implicit val system = context.system
  import system.dispatcher
  def receive = {
    case Tcp.Connected(_,_) => {
      log.info("New connection, spawning new child...")
      val conn = sender
      val child = context.watch(system.actorOf(Props(new AkkaThriftConnection(conn))))
      conn ! Tcp.Register(child)
      process(child)
    }

    case Terminated(child) => {
      log.info(s"Child $child terminated.")
    }
  }

  def process(child:ActorRef) = {

    val p = proc
    val processor = toProto(new AkkaTransport(child))
    val logger = log
    Future {
      p.process(processor, processor)
    } andThen {
      case Success(true) => logger.info("Finished request successfully")
      case Success(false) => logger.info("Request Failed!")
      case Failure(ex) => logger.info(s"Request failed with $ex")
    }
  }
}
