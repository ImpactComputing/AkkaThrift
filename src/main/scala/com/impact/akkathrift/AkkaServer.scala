package com.impact.akkathrift

import akka.io.{IO=>AIO,_}
import akka.actor._

import org.apache.thrift.server.TServer
import org.apache.thrift.protocol.TProtocol
import org.apache.thrift.transport.TTransport

class AkkaThriftServer(args:AkkaThriftServer.Args) extends TServer(args) {

  override def serve():Unit = {
    args.atss.listenWith(inputProtocolFactory_, processorFactory_)
  }
}

object AkkaThriftServer extends AkkaThriftConfig {
  class Args(val atss:AkkaThriftServerSocket) extends TServer.AbstractServerArgs[Args](atss) 
  object Args {
    def apply(atss:AkkaThriftServerSocket) = new Args(atss)
  }
  private[this] lazy val actorSystem_ = ActorSystem("AkkaThrift", conf)

  def actorSystem = actorSystem_

  def apply(args:AkkaThriftServer.Args) = {
    new AkkaThriftServer(args)
  }
}
