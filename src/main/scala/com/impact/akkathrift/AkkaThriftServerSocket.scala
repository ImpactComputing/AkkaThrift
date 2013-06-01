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

import org.apache.thrift.transport.{TServerTransport,TTransport}
import org.apache.thrift.protocol.{TProtocolFactory}
import org.apache.thrift.{TProcessorFactory}

class AkkaThriftServerSocket(addr:InetSocketAddress)(implicit system:ActorSystem) extends TServerTransport {
  private[this] var sActor:Option[ActorRef] = None
  
  override def listen() = {
    throw new UnsupportedOperationException("Listen in its raw from is unsupported")
  }

  def listenWith(protoBuilder:TProtocolFactory, procBuilder:TProcessorFactory) = {
    close()
    sActor = Some(system.actorOf(Props(new AkkaThriftServerActor(protoBuilder, procBuilder))))
    sActor.map(IO(Tcp) ! Tcp.Bind(_, addr))
  }

  // Not sure how to close a channel, so I'm guessing close
  override def close():Unit = sActor.foreach({
    IO(Tcp) ! Tcp.Unbind
    _ ! 'Stop
  })

  /** 
    This is stupid: you are required to return a non-null transport which means we have
    a few choices.  What's worse, the accept method is declared as final so it can't
    be overriden.  

    1. Block the thread until we have a connection come in.  This is dumb.
    2. Return a 'None' Transport which we know how to handle later.  Also dumb
    3. Return null and just not use `accept`.  Ding ding ding!
  */
  override def acceptImpl(): TTransport = null

}

object AkkaThriftServerSocket {
  def apply(addr:InetSocketAddress)(implicit system:ActorSystem) = {
    new AkkaThriftServerSocket(addr)
  }
}

class AkkaThriftServerActor(proto: TProtocolFactory, proc: TProcessorFactory) extends Actor with ActorLogging {
  implicit val system = context.system
  import system.dispatcher
  def receive = {
    case Tcp.Connected(_,_) => {
      log.debug("New connection, spawning new child...")
      val conn = sender
      val child = context.watch(system.actorOf(Props(new AkkaThriftConnection(conn))))
      conn ! Tcp.Register(child)
      process(child)
    }

    case Terminated(child) => {
      log.debug(s"Child $child terminated.")
    }
  }

  def process(child:ActorRef) = {

    val transport = new AkkaTransport(child)
    val processor = proc.getProcessor(transport)
    val protocol  = proto.getProtocol(transport)
    val logger = log
    Future {
      while(processor.process(protocol, protocol)) {}
    } andThen {
      case _ => transport.close()
    } andThen {
      case Success(()) => logger.debug("Request Finished successfully!")
      case Failure(ex) => logger.info(s"Request failed with $ex")
    } 
  }
}
