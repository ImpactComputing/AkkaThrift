package com.impact.akkathrift

import collection.immutable.Queue
import concurrent.Future
import concurrent.Await
import concurrent.duration._
import util.{Try, Success,Failure}

import java.net.InetSocketAddress

import akka.io._
import akka.actor.{IO=>AIO,_}
import akka.pattern.ask
import akka.routing.FromConfig

import org.apache.thrift.transport.{TServerTransport,TTransport,TTransportException}
import org.apache.thrift.protocol.{TProtocolFactory,TProtocol}
import org.apache.thrift.{TProcessorFactory, TProcessor}

class AkkaThriftServerSocket(addr:InetSocketAddress)(implicit system:ActorSystem) extends TServerTransport with AkkaThriftConfig {
  private[this] var router: Option[ActorRef] = None
  
  override def listen() = {
    throw new UnsupportedOperationException("Listen in its raw from is unsupported")
  }

  def listenWith(protoBuilder:TProtocolFactory, procBuilder:TProcessorFactory) = {
    close()

    // Create the router
    val router = Some(system.actorOf(Props(classOf[AkkaThriftServerActor], 
                                           protoBuilder, 
                                           procBuilder).withRouter(FromConfig()),
                                     "akka-thrift-handler"))

    router.map(IO(Tcp) ! Tcp.Bind(_, addr))
  }

  // Not sure how to close a channel, so I'm guessing close
  override def close():Unit = router.foreach({
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
      val child = context.watch(system.actorOf(Props(classOf[AkkaThriftConnection], conn)))
      conn ! Tcp.Register(child)
      startProcessor(child)
    }

    case Terminated(child) => {
      log.debug(s"Child $child terminated.")
    }
  }

  def startProcessor(child:ActorRef) = {
    val transport = new AkkaTransport(child)
    val processor = proc.getProcessor(transport)
    val protocol  = proto.getProtocol(transport)
    system.actorOf(Props(classOf[AkkaThriftProcessorActor], protocol, processor, transport))
  }
}

class AkkaThriftProcessorActor(protocol: TProtocol, processor: TProcessor, transport:AkkaTransport) extends Actor with ActorLogging {
  override def preStart:Unit = transport.informOnRead(self)
  def receive = {
    case CanRead => 
      Try(processor.process(protocol, protocol)) match {
        case Success(true) =>
          transport.informOnRead(self)
        case Success(false) =>
          self ! 'Stop
        case Failure(ex:TTransportException) =>
          self ! 'Stop
        case Failure(ex) =>
          log.warning(s"Exception during processing: $ex")
          self ! 'Stop
      }

    case 'Stop =>
      transport.close()
      context.stop(self)
  }
}
